package org.aura.listen.audio;

/** Band-limited 48 kHz to 16 kHz conversion for classification, never playback. */
public final class AudioResampler {
    private static final double[] FIR = coefficients();
    private AudioResampler() {}
    public static void to16k(short[] source, int sourceRate, float[] target) {
        int ratio = sourceRate / 16000;
        if ((sourceRate != 16000 && sourceRate != 48000) || source.length != target.length * ratio)
            throw new IllegalArgumentException("Expected 16 or 48 kHz mono window.");
        for (int i = 0; i < target.length; i++) {
            if (ratio == 1) { target[i] = source[i] / 32768f; continue; }
            double value = 0;
            for (int tap = 0; tap < FIR.length; tap++) {
                int position = i * 3 + tap - FIR.length / 2;
                if (position >= 0 && position < source.length) value += source[position] * FIR[tap];
            }
            target[i] = (float) Math.max(-1, Math.min(1, value / 32768.0));
        }
    }
    private static double[] coefficients() {
        double[] result = new double[63]; double sum = 0, cutoff = 7000.0 / 48000;
        for (int i = 0; i < result.length; i++) {
            int n = i - result.length / 2;
            double sinc = n == 0 ? 2 * cutoff : Math.sin(2 * Math.PI * cutoff * n) / (Math.PI * n);
            result[i] = sinc * (0.54 - 0.46 * Math.cos(2 * Math.PI * i / (result.length - 1))); sum += result[i];
        }
        for (int i = 0; i < result.length; i++) result[i] /= sum;
        return result;
    }
}
