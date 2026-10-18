package org.aura.listen.audio;

import android.content.Context;
import android.os.SystemClock;
import org.aura.listen.AppStore;
import org.json.JSONObject;
import org.tensorflow.lite.Interpreter;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Bounded background inference; never blocks playback waiting for a prediction. */
public final class SoundClassifier implements AutoCloseable {
    public static final int WINDOW = 15600;
    private static final String MODEL_SHA256 = "10c95ea3eb9a7bb4cb8bddf6feb023250381008177ac162ce169694d05c317de";
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> new Thread(r, "AURA-sound-categories"));
    private final AtomicBoolean pending = new AtomicBoolean();
    private final int sampleRate;
    private short[] collecting, spare;
    private int filled;
    private volatile boolean ready, closed;
    private volatile JSONObject snapshot = status("loading", "Loading offline sound model…");
    private Interpreter model;
    private ByteBuffer modelBuffer;

    public SoundClassifier(Context context, int sampleRate) {
        this.sampleRate = sampleRate;
        collecting = new short[WINDOW * (sampleRate / 16000)]; spare = new short[collecting.length];
        Context app = context.getApplicationContext();
        worker.execute(() -> {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            try (InputStream in = app.getAssets().open("models/yamnet.tflite"); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192]; int read;
                while ((read = in.read(buffer)) != -1) {
                    if (bytes.size() + read > 8_000_000) throw new IllegalStateException("Model exceeds expected size.");
                    bytes.write(buffer, 0, read);
                }
                byte[] data = bytes.toByteArray();
                StringBuilder digest = new StringBuilder();
                for (byte b : MessageDigest.getInstance("SHA-256").digest(data)) digest.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
                if (!MODEL_SHA256.equals(digest.toString())) throw new IllegalStateException("Model integrity check failed.");
                modelBuffer = ByteBuffer.allocateDirect(data.length).order(ByteOrder.nativeOrder()); modelBuffer.put(data).rewind();
                model = new Interpreter(modelBuffer, new Interpreter.Options().setNumThreads(1));
                if (!Arrays.equals(model.getInputTensor(0).shape(), new int[]{WINDOW}) || !Arrays.equals(model.getOutputTensor(0).shape(), new int[]{1,521}))
                    throw new IllegalStateException("Unexpected model shape.");
                if (!closed) { ready = true; snapshot = status("warming", "Listening for a one-second sound sample…"); }
            } catch (Exception | LinkageError error) {
                snapshot = status("unavailable", "Sound detection is unavailable on this device. Listening still works.");
            }
        });
    }

    /** Called only by the capture thread. Busy inference drops windows instead of queueing audio. */
    public void offer(short[] input, int count) {
        if (!ready || closed) return;
        int offset = 0;
        while (offset < count) {
            int amount = Math.min(count - offset, collecting.length - filled);
            System.arraycopy(input, offset, collecting, filled, amount); offset += amount; filled += amount;
            if (filled == collecting.length) {
                filled = 0;
                if (!pending.compareAndSet(false, true)) continue;
                short[] complete = collecting; collecting = spare; spare = complete;
                worker.execute(() -> classify(complete));
            }
        }
    }
    private void classify(short[] samples) {
        try {
            if (closed) return;
            long start = SystemClock.elapsedRealtime();
            float[] input = new float[WINDOW]; float[][] output = new float[1][521];
            AudioResampler.to16k(samples, sampleRate, input);
            model.run(input, output);
            float[] scores = SoundCategories.scores(output[0]);
            JSONObject result = status("ready", "Estimated sound categories · not separated audio");
            JSONObject categories = new JSONObject();
            for (int i = 0; i < scores.length; i++) AppStore.put(categories, SoundCategories.KEYS[i], scores[i]);
            AppStore.put(result, "scores", categories);
            AppStore.put(result, "inferenceMs", SystemClock.elapsedRealtime() - start);
            AppStore.put(result, "updatedAtElapsed", SystemClock.elapsedRealtime());
            if (!closed) snapshot = result;
        } catch (Exception | LinkageError error) {
            ready = false; snapshot = status("unavailable", "Sound detection stopped. Audio playback is unaffected.");
        } finally { Arrays.fill(samples, (short) 0); pending.set(false); }
    }
    public JSONObject snapshot() {
        try {
            JSONObject copy = new JSONObject(snapshot.toString());
            if (copy.has("updatedAtElapsed")) AppStore.put(copy, "ageMs", Math.max(0, SystemClock.elapsedRealtime() - copy.optLong("updatedAtElapsed")));
            copy.remove("updatedAtElapsed"); return copy;
        } catch (Exception ignored) { return status("unavailable", "Sound detection unavailable."); }
    }
    public static JSONObject status(String state, String message) {
        JSONObject result = new JSONObject(); AppStore.put(result, "status", state); AppStore.put(result, "message", message); return result;
    }
    @Override public void close() {
        closed = true; ready = false; Arrays.fill(collecting, (short) 0);
        worker.execute(() -> { if (model != null) model.close(); model = null; modelBuffer = null; Arrays.fill(spare, (short) 0); });
        worker.shutdown();
    }
}
