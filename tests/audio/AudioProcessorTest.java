import org.aura.listen.audio.AudioProcessor;
import java.util.Random;

/** Deterministic synthetic-signal checks. These do not certify hearing safety or Android routing. */
public final class AudioProcessorTest {
    private static int assertions;
    public static void main(String[] args) {
        silenceIsFinite();
        peaksAreBounded();
        speechBandReducesRumble();
        lowLevelNoiseIsAttenuated();
        awarenessMixPreservesCapturedAudio();
        changingSettingsAndMute();
        invalidInputs();
        System.out.println("PASS: " + assertions + " DSP assertions (silence, peaks, frequency response, noise, awareness, settings, input bounds)");
    }
    private static AudioProcessor.Settings settings(int clarity, int reduction, int awareness, int volume) {
        return new AudioProcessor.Settings("focus", clarity, reduction, awareness, volume);
    }
    private static void silenceIsFinite() {
        for (int rate : new int[] {16000, 48000}) {
            AudioProcessor processor = new AudioProcessor(rate, AudioProcessor.Settings.defaults());
            short[] samples = new short[rate];
            processor.process(samples, samples, samples.length);
            for (short sample : samples) check(sample == 0, "Silence generated audio");
            check(Double.isFinite(processor.getInputDbfs()) && processor.getInputDbfs() == -96, "Input silence level");
            check(Double.isFinite(processor.getOutputDbfs()) && processor.getOutputDbfs() == -96, "Output silence level");
        }
    }
    private static void peaksAreBounded() {
        for (String mode : new String[] {"care", "focus", "commute"}) {
            AudioProcessor processor = new AudioProcessor(48000, new AudioProcessor.Settings(mode, 100, 0, 0, 100));
            short[] samples = new short[48000 * 3];
            Random random = new Random(891);
            for (int i = 0; i < samples.length; i++) samples[i] = i < 48000
                    ? (i % 100 < 50 ? Short.MAX_VALUE : Short.MIN_VALUE) : (short) random.nextInt(65536);
            processor.process(samples, samples, samples.length);
            int limit = (int) Math.ceil(AudioProcessor.PEAK_LIMIT * 32767);
            for (short sample : samples) check(Math.abs((int) sample) <= limit, "Peak limiter exceeded");
            check(Double.isFinite(processor.getOutputDbfs()), "Non-finite meter");
        }
    }
    private static void speechBandReducesRumble() {
        for (int rate : new int[] {16000, 48000}) {
            double low = sineOutputRms(rate, 35, settings(100, 0, 0, 100));
            double speech = sineOutputRms(rate, 1000, settings(100, 0, 0, 100));
            double hiss = sineOutputRms(rate, rate == 16000 ? 7000 : 11000, settings(100, 0, 0, 100));
            check(speech > low * 12, "Speech band does not reject rumble at " + rate);
            check(speech > hiss * 4, "Speech band does not reduce high frequency hiss at " + rate);
            check(speech > 0.035 && speech < 0.06, "Unexpected speech level");
        }
    }
    private static void lowLevelNoiseIsAttenuated() {
        int rate = 16000;
        short[] noise = new short[rate * 5];
        Random random = new Random(42);
        for (int i = 0; i < noise.length; i++) noise[i] = (short) ((random.nextDouble() * 2 - 1) * 160);
        short[] reference = new short[noise.length], reduced = new short[noise.length];
        new AudioProcessor(rate, settings(0, 0, 0, 100)).process(noise, reference, noise.length);
        new AudioProcessor(rate, settings(0, 100, 0, 100)).process(noise, reduced, noise.length);
        check(rms(reduced, rate * 3) < rms(reference, rate * 3) * 0.45, "Low-level noise was not attenuated");
        double voice = sineOutputRms(rate, 1000, settings(50, 100, 0, 100));
        check(voice > 0.025, "Expander excessively suppressed speech-like signal");
    }
    private static void awarenessMixPreservesCapturedAudio() {
        int rate = 16000;
        short[] input = sine(rate, 35, 0.05, 3);
        short[] output = new short[input.length];
        new AudioProcessor(rate, settings(100, 100, 100, 100)).process(input, output, input.length);
        for (int i = rate * 2; i < input.length; i++) {
            int expected = (int) Math.round(input[i] * AudioProcessor.MAX_LINEAR_GAIN * 32767.0 / 32768.0);
            check(Math.abs(output[i] - expected) <= 2, "Awareness dry mix changed captured signal");
        }
    }
    private static void changingSettingsAndMute() {
        int rate = 48000;
        AudioProcessor processor = new AudioProcessor(rate, settings(50, 50, 50, 30));
        short[] input = sine(rate, 1000, 0.1, 1), output = new short[input.length];
        processor.process(input, output, input.length);
        double initial = rms(output, rate / 2);
        check(initial > 0, "Starting gain is silent");
        processor.configure(settings(50, 50, 50, 0));
        processor.process(input, output, input.length);
        check(rms(output, 0) == 0, "Mute must take effect immediately");
        processor.configure(new AudioProcessor.Settings("commute", 35, 20, 80, 60));
        processor.process(input, output, input.length);
        check(rms(output, rate / 2) > initial, "Live settings were not applied");
        check(Double.isFinite(processor.getOutputDbfs()), "Live settings generated nonfinite level");
        processor.configure(null);
        processor.process(input, output, 0);
        check(processor.getSampleRate() == rate, "Sample rate changed unexpectedly");
    }
    private static void invalidInputs() {
        AudioProcessor.Settings clamped = new AudioProcessor.Settings("unknown", -5, 500, -1, 200);
        check(clamped.mode.equals("care") && clamped.clarity == 0 && clamped.reduction == 100
                && clamped.awareness == 0 && clamped.volume == 100, "Settings bounds failed");
        boolean invalidRate = false, invalidCount = false;
        try { new AudioProcessor(0, clamped); } catch (IllegalArgumentException expected) { invalidRate = true; }
        try { new AudioProcessor(16000, clamped).process(new short[1], new short[1], 2); }
        catch (IllegalArgumentException expected) { invalidCount = true; }
        check(invalidRate && invalidCount, "Invalid input was accepted");
    }
    private static double sineOutputRms(int rate, double frequency, AudioProcessor.Settings settings) {
        short[] input = sine(rate, frequency, 0.1, 4), output = new short[input.length];
        new AudioProcessor(rate, settings).process(input, output, input.length);
        return rms(output, rate * 3);
    }
    private static short[] sine(int rate, double frequency, double amplitude, int seconds) {
        short[] result = new short[rate * seconds];
        for (int i = 0; i < result.length; i++) result[i] = (short) Math.round(Math.sin(2 * Math.PI * frequency * i / rate) * amplitude * 32767);
        return result;
    }
    private static double rms(short[] samples, int from) {
        double total = 0;
        for (int i = from; i < samples.length; i++) { double value = samples[i] / 32768.0; total += value * value; }
        return Math.sqrt(total / (samples.length - from));
    }
    private static void check(boolean result, String message) {
        assertions++;
        if (!result) throw new AssertionError(message);
    }
}
