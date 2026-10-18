package org.aura.listen.audio;

/**
 * Small, allocation-free PCM processor. This is conventional signal processing, not AI:
 * a speech-band filter, conservative adaptive downward expander, compressor and peak cap.
 * Optional microphone boost is user-controlled. dBFS readings are digital levels, never SPL.
 */
public final class AudioProcessor {
    public static final float MAX_LINEAR_GAIN = 0.70f;
    public static final float PEAK_LIMIT = 0.65f;

    public static final class Settings {
        public final String mode;
        public final int clarity, reduction, awareness, volume;
        public final int micGainDb, humHz;
        public final boolean monitor;

        public Settings(String mode, int clarity, int reduction, int awareness, int volume) {
            this(mode, clarity, reduction, awareness, volume, 0, false, 0);
        }
        public Settings(String mode, int clarity, int reduction, int awareness, int volume, int micGainDb, boolean monitor, int humHz) {
            this.mode = "focus".equals(mode) || "commute".equals(mode) ? mode : "care";
            this.clarity = clampPercent(clarity);
            this.reduction = clampPercent(reduction);
            this.awareness = clampPercent(awareness);
            this.volume = clampPercent(volume);
            this.micGainDb = Math.max(0, Math.min(24, micGainDb));
            this.monitor = monitor;
            this.humHz = humHz == 50 || humHz == 60 ? humHz : 0;
        }
        private static int clampPercent(int value) { return Math.max(0, Math.min(100, value)); }
        public static Settings defaults() { return new Settings("care", 55, 35, 35, 25); }
    }

    private final int sampleRate;
    private final Biquad highPass = new Biquad();
    private final Biquad lowPass = new Biquad();
    private final Biquad humNotch = new Biquad();
    private final double envelopeAttack, envelopeRelease, gainAttack, gainRelease, noiseFall, noiseRise;
    private Settings settings;
    private double envelope, noiseFloor = 0.004, gateGain = 1.0, compressorEnvelope;
    private double inputDbfs = -96, outputDbfs = -96;
    private double volumeGain = 0, targetVolumeGain, clarityMix, ambientMix, reduction;
    private double microphoneGain = 1, targetMicrophoneGain = 1;

    public AudioProcessor(int sampleRate, Settings settings) {
        if (sampleRate < 8000 || sampleRate > 192000) throw new IllegalArgumentException("Unsupported sample rate");
        this.sampleRate = sampleRate;
        envelopeAttack = coefficient(0.008);
        envelopeRelease = coefficient(0.120);
        gainAttack = coefficient(0.006);
        gainRelease = coefficient(0.160);
        noiseFall = coefficient(0.08);
        noiseRise = coefficient(18.0);
        configure(settings);
    }

    /** Call on the processing thread between buffers. Existing filter/envelope state is retained. */
    public void configure(Settings value) {
        if (value == null) value = Settings.defaults();
        boolean modeChanged = settings == null || !settings.mode.equals(value.mode);
        boolean humChanged = settings == null || settings.humHz != value.humHz || settings.monitor != value.monitor;
        settings = value;
        targetVolumeGain = MAX_LINEAR_GAIN * value.volume / 100.0;
        targetMicrophoneGain = Math.pow(10, value.micGainDb / 20.0);
        clarityMix = value.clarity / 100.0;
        ambientMix = value.awareness / 100.0;
        reduction = value.reduction / 100.0;
        if (humChanged) humNotch.notch(value.humHz, sampleRate);
        if (modeChanged) {
            double low = "focus".equals(value.mode) ? 190 : "commute".equals(value.mode) ? 100 : 140;
            double high = "focus".equals(value.mode) ? 3800 : "commute".equals(value.mode) ? 5500 : 4700;
            highPass.configure(true, low, sampleRate);
            lowPass.configure(false, Math.min(high, sampleRate * 0.42), sampleRate);
        }
    }

    /** Input and output may be the same array. Samples beyond count are untouched. */
    public void process(short[] input, short[] output, int count) {
        if (count < 0 || count > input.length || count > output.length) throw new IllegalArgumentException("Invalid sample count");
        if (count == 0) return;
        double inputEnergy = 0, outputEnergy = 0;
        for (int i = 0; i < count; i++) {
            double dry = input[i] / 32768.0;
            inputEnergy += dry * dry;
            double band = lowPass.filter(highPass.filter(dry));
            double amplitude = Math.abs(band);
            double envCoef = amplitude > envelope ? envelopeAttack : envelopeRelease;
            envelope = envCoef * envelope + (1 - envCoef) * amplitude;
            // A slow-rising floor avoids treating a new spoken phrase as steady noise.
            double noiseCoef = envelope < noiseFloor ? noiseFall : noiseRise;
            noiseFloor = noiseCoef * noiseFloor + (1 - noiseCoef) * envelope;
            noiseFloor = Math.max(0.00025, Math.min(0.025, noiseFloor));
            double threshold = Math.max(0.002 + 0.014 * reduction * reduction, noiseFloor * 1.7);
            double openness = Math.max(0, Math.min(1, (envelope / threshold - 0.55) / 1.10));
            double minimumGain = 1.0 - reduction * 0.78;
            double desiredGate = minimumGain + (1 - minimumGain) * openness;
            double gateCoef = desiredGate > gateGain ? gainAttack : gainRelease;
            gateGain = gateCoef * gateGain + (1 - gateCoef) * desiredGate;
            double enhanced = (dry * (1 - clarityMix) + band * clarityMix) * gateGain;
            // "Awareness" is a dry mix of captured microphone audio; it cannot identify alarms.
            double mixed = enhanced * (1 - ambientMix) + dry * ambientMix;
            if (settings.monitor) mixed = dry;
            else if (settings.humHz != 0) mixed = humNotch.filter(mixed);
            microphoneGain = targetMicrophoneGain < microphoneGain ? targetMicrophoneGain
                    : gainRelease * microphoneGain + (1 - gainRelease) * targetMicrophoneGain;
            mixed *= microphoneGain;
            double absolute = Math.abs(mixed);
            double compCoef = absolute > compressorEnvelope ? gainAttack : gainRelease;
            compressorEnvelope = compCoef * compressorEnvelope + (1 - compCoef) * absolute;
            double compression = compressorEnvelope > 0.28
                    ? (0.28 + (compressorEnvelope - 0.28) / 3.0) / compressorEnvelope : 1.0;
            // Smooth gain increases, but make mute/decreases immediate.
            volumeGain = targetVolumeGain < volumeGain ? targetVolumeGain
                    : gainRelease * volumeGain + (1 - gainRelease) * targetVolumeGain;
            double sample = mixed * compression * volumeGain;
            if (!Double.isFinite(sample)) sample = 0;
            sample = Math.max(-PEAK_LIMIT, Math.min(PEAK_LIMIT, sample));
            output[i] = (short) Math.round(sample * 32767.0);
            outputEnergy += sample * sample;
        }
        inputDbfs = level(inputEnergy, count);
        outputDbfs = level(outputEnergy, count);
    }

    public double getInputDbfs() { return inputDbfs; }
    public double getOutputDbfs() { return outputDbfs; }
    public int getSampleRate() { return sampleRate; }
    private double coefficient(double seconds) { return Math.exp(-1.0 / (seconds * sampleRate)); }
    private static double level(double energy, int count) {
        return Math.max(-96, 10 * Math.log10(Math.max(1e-12, energy / count)));
    }

    /** RBJ Butterworth high/low-pass biquad, transposed direct form II. */
    private static final class Biquad {
        private double b0, b1, b2, a1, a2, z1, z2;
        void notch(double frequency, double rate) {
            z1 = z2 = 0;
            if (frequency == 0) { b0 = 1; b1 = b2 = a1 = a2 = 0; return; }
            double omega = 2 * Math.PI * frequency / rate;
            double alpha = Math.sin(omega) / (2 * 8.0); // Narrow Q=8 mains-hum notch.
            b0 = b2 = 1 / (1 + alpha);
            b1 = a1 = -2 * Math.cos(omega) / (1 + alpha);
            a2 = (1 - alpha) / (1 + alpha);
        }
        void configure(boolean highPass, double frequency, double rate) {
            double omega = 2 * Math.PI * frequency / rate;
            double cosine = Math.cos(omega);
            double alpha = Math.sin(omega) / Math.sqrt(2);
            double a0 = 1 + alpha;
            b0 = (1 + (highPass ? cosine : -cosine)) / (2 * a0);
            b1 = (highPass ? -(1 + cosine) : 1 - cosine) / a0;
            b2 = b0;
            a1 = -2 * cosine / a0;
            a2 = (1 - alpha) / a0;
        }
        double filter(double sample) {
            double result = b0 * sample + z1;
            z1 = b1 * sample - a1 * result + z2;
            z2 = b2 * sample - a2 * result;
            return result;
        }
    }
}
