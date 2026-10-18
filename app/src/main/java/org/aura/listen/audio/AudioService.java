package org.aura.listen.audio;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioFocusRequest;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioRecordingConfiguration;
import android.media.AudioRouting;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.media.audiofx.NoiseSuppressor;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;

import org.aura.listen.AppStore;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/** Foreground microphone-to-headphones loop. No samples are saved or sent anywhere. */
public final class AudioService extends Service {
    public static final String ACTION_START = "org.aura.listen.START";
    public static final String ACTION_STOP = "org.aura.listen.STOP";
    public static final String ACTION_UPDATE = "org.aura.listen.UPDATE";
    private static final String CHANNEL = "aura_listening";
    private static final int NOTIFICATION_ID = 31;
    private static final Object STATE_LOCK = new Object();
    private static String state = "idle", message = "Connect headphones, then start listening.", route = "Not connected";
    private static long startedAt, startedElapsed, durationMs;
    private static double inputDbfs = -96, outputDbfs = -96, processingMs;
    private static int sampleRate;
    private static boolean hardwareSuppression;
    private static String inputRoute = "Not active", captureSource = "Not active";
    private static int underruns, mediaVolume, mediaMaximum;
    private static volatile SoundClassifier activeClassifier;
    private static AudioProcessor.Settings snapshotSettings = AudioProcessor.Settings.defaults();

    private final Handler main = new Handler(Looper.getMainLooper());
    private AudioManager audioManager;
    private AudioFocusRequest focusRequest;
    private volatile boolean requested, routeVerified;
    private volatile AudioProcessor.Settings settings = AudioProcessor.Settings.defaults();
    private volatile JSONObject preferenceSnapshot = AppStore.defaults();
    private volatile AudioRecord activeRecord;
    private volatile AudioTrack activeTrack;
    private volatile int expectedOutputId = -1;
    private volatile Thread worker;
    private volatile String finishMessage = "Listening stopped.", finishState = "idle";
    private boolean receiverRegistered;

    private final BroadcastReceiver noisyReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction()) && requested)
                requestStop("error", "Audio output changed. Reconnect headphones and start again.");
        }
    };
    private final AudioDeviceCallback devices = new AudioDeviceCallback() {
        @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] removed) {
            for (AudioDeviceInfo device : removed) {
                if (requested && device.getId() == expectedOutputId) {
                    requestStop("error", "Headphones disconnected. Listening stopped.");
                    break;
                }
            }
        }
    };
    private final AudioManager.AudioRecordingCallback microphoneState = new AudioManager.AudioRecordingCallback() {
        @Override public void onRecordingConfigChanged(List<AudioRecordingConfiguration> configurations) {
            AudioRecord recorder = activeRecord;
            if (requested && recorder != null && Build.VERSION.SDK_INT >= 29) {
                try {
                    AudioRecordingConfiguration configuration = recorder.getActiveRecordingConfiguration();
                    if (configuration != null && configuration.isClientSilenced())
                        requestStop("error", "Android silenced the microphone. Check microphone privacy settings and close other recording apps.");
                } catch (RuntimeException ignored) { /* A concurrent stop may already have released the recorder. */ }
            }
        }
    };
    private final AudioRouting.OnRoutingChangedListener routeListener = router -> {
        if (requested && routeVerified && !isExpectedRoute(router.getRoutedDevice()))
            requestStop("error", "Audio routing changed. Listening stopped to avoid speaker playback.");
    };

    public static JSONObject snapshot() {
        synchronized (STATE_LOCK) {
            JSONObject object = new JSONObject();
            try {
                long elapsed = "listening".equals(state) && startedElapsed > 0
                        ? Math.max(0, SystemClock.elapsedRealtime() - startedElapsed) : durationMs;
                object.put("status", state).put("message", message).put("startedAt", startedAt)
                        .put("elapsedSeconds", elapsed / 1000).put("inputDbfs", inputDbfs)
                        .put("outputDbfs", outputDbfs).put("processingMs", processingMs)
                        .put("sampleRate", sampleRate).put("route", route)
                        .put("noiseSuppressor", hardwareSuppression).put("settings", asJson(snapshotSettings));
                object.put("inputRoute", inputRoute).put("captureSource", captureSource).put("underruns", underruns)
                        .put("mediaVolume", mediaVolume).put("mediaMaximum", mediaMaximum);
                SoundClassifier classifier = activeClassifier;
                object.put("categories", classifier == null ? SoundClassifier.status("off", "Sound detection is off or listening has stopped.") : classifier.snapshot());
            } catch (JSONException ignored) { /* All values are finite primitives. */ }
            return object;
        }
    }

    /** Prefer cable/USB headsets. A2DP can also represent a Bluetooth speaker: connect headphones only. */
    public static AudioDeviceInfo findOutput(Context context) {
        AudioManager manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (manager == null) return null;
        AudioDeviceInfo best = null;
        int bestPriority = Integer.MAX_VALUE;
        for (AudioDeviceInfo device : manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            int priority = outputPriority(device);
            if (priority < bestPriority) { bestPriority = priority; best = device; }
        }
        return best;
    }

    private static int outputPriority(AudioDeviceInfo device) {
        if (!device.isSink()) return Integer.MAX_VALUE;
        switch (device.getType()) {
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
            case AudioDeviceInfo.TYPE_WIRED_HEADSET: return 0;
            case AudioDeviceInfo.TYPE_USB_HEADSET: return 1;
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP: return 3;
            default:
                if (Build.VERSION.SDK_INT >= 31 && device.getType() == AudioDeviceInfo.TYPE_BLE_HEADSET) return 2;
                if (Build.VERSION.SDK_INT >= 28 && device.getType() == AudioDeviceInfo.TYPE_HEARING_AID) return 4;
                return Integer.MAX_VALUE;
        }
    }

    @Override public void onCreate() {
        super.onCreate();
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        NotificationChannel channel = new NotificationChannel(CHANNEL, "AURA listening", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Shows when the microphone is active and lets you stop listening.");
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
        AudioAttributes attributes = playbackAttributes();
        focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attributes).setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener(change -> {
                    if (change != AudioManager.AUDIOFOCUS_GAIN && requested)
                        requestStop("idle", "Listening stopped because another app needs audio. Tap Start to resume.");
                }, main).build();
        if (audioManager != null) audioManager.registerAudioDeviceCallback(devices, main);
        IntentFilter filter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(noisyReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(noisyReceiver, filter);
        receiverRegistered = true;
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_STOP : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            requestStop("idle", "Listening stopped.");
            if (worker == null) { setState("idle", "Listening stopped."); stopSelfResult(startId); }
            return START_NOT_STICKY;
        }
        if (ACTION_UPDATE.equals(action) || ACTION_START.equals(action)) readSettings(intent);
        if (ACTION_UPDATE.equals(action)) {
            if (worker == null) stopSelfResult(startId);
            return START_NOT_STICKY;
        }
        if (!ACTION_START.equals(action)) { if (worker == null) stopSelfResult(startId); return START_NOT_STICKY; }
        if (worker != null) return START_NOT_STICKY;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            setState("error", "Microphone permission is required. Allow it in Android app settings, then try again.");
            stopSelfResult(startId);
            return START_NOT_STICKY;
        }
        AudioDeviceInfo output = findOutput(this);
        if (output == null) {
            setState("error", "Connect wired, USB-C, or supported Bluetooth headphones before listening.");
            stopSelfResult(startId);
            return START_NOT_STICKY;
        }
        try {
            Notification notification = notification("Preparing microphone and headphones…");
            if (Build.VERSION.SDK_INT >= 30)
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            else startForeground(NOTIFICATION_ID, notification);
        } catch (RuntimeException error) {
            setState("error", "Android could not start microphone access. Open AURA, check microphone permission, and try again.");
            stopSelfResult(startId);
            return START_NOT_STICKY;
        }
        expectedOutputId = output.getId();
        requested = true;
        routeVerified = false;
        finishMessage = "Listening stopped.";
        finishState = "idle";
        synchronized (STATE_LOCK) {
            startedAt = 0; startedElapsed = 0; durationMs = 0;
            inputDbfs = -96; outputDbfs = -96; processingMs = 0;
            sampleRate = 0; hardwareSuppression = false; route = routeName(output);
            inputRoute = "Checking microphone…"; captureSource = "Preparing"; underruns = 0;
            mediaVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            mediaMaximum = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        }
        setState("starting", "Checking headphone routing…");
        worker = new Thread(() -> runAudio(output, startId), "AURA-audio");
        worker.start();
        return START_NOT_STICKY;
    }

    private void readSettings(Intent intent) {
        try {
            String raw = intent.getStringExtra("settings");
            if (raw == null) return;
            JSONObject value = new JSONObject(raw);
            preferenceSnapshot = AppStore.sanitizeSettings(value);
            AudioProcessor.Settings previous = settings;
            settings = new AudioProcessor.Settings(value.optString("mode", previous.mode),
                    value.optInt("clarity", previous.clarity), value.optInt("reduction", previous.reduction),
                    value.optInt("awareness", previous.awareness), value.optInt("volume", previous.volume),
                    value.optInt("micGainDb", 0), value.optBoolean("monitor", false), value.optInt("humHz", 0));
            synchronized (STATE_LOCK) { snapshotSettings = settings; }
        } catch (JSONException ignored) { /* Keep the last usable settings. */ }
    }

    private void runAudio(AudioDeviceInfo output, int startId) {
        AudioRecord recorder = null;
        AudioTrack track = null;
        NoiseSuppressor suppressor = null;
        long sessionWall = 0, sessionElapsed = 0;
        JSONObject initialPreferences = preferenceSnapshot;
        SoundClassifier classifier = null;
        boolean focusHeld = false, recordingCallbackRegistered = false, suppressorEnabled = false;
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
            if (audioManager == null) throw new AudioFailure("Android audio is unavailable.");
            if (audioManager.requestAudioFocus(focusRequest) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
                throw new AudioFailure("Another app is using audio. Finish that session, then try again.");
            focusHeld = true;
            int rate = 48000;
            String selectedMicrophone = preferenceSnapshot.optString("microphone", "phone");
            int source = "true".equals(audioManager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED))
                    ? MediaRecorder.AudioSource.UNPROCESSED : MediaRecorder.AudioSource.VOICE_RECOGNITION;
            synchronized (STATE_LOCK) { captureSource = source == MediaRecorder.AudioSource.UNPROCESSED ? "Unprocessed microphone" : "Voice-recognition input (device-dependent processing)"; }
            for (int attempt = 0; attempt < 2; attempt++) {
                rate = attempt == 0 ? 48000 : 16000;
                int minimum = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
                if (minimum <= 0) continue;
                try {
                    recorder = new AudioRecord.Builder().setAudioSource(source)
                            .setAudioFormat(new AudioFormat.Builder().setSampleRate(rate)
                                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                            .setBufferSizeInBytes(Math.max(minimum, rate / 100 * 2 * 4)).build();
                    if (recorder.getState() == AudioRecord.STATE_INITIALIZED) break;
                } catch (IllegalArgumentException | UnsupportedOperationException ignored) { }
                if (recorder != null) { recorder.release(); recorder = null; }
            }
            if (recorder == null || recorder.getState() != AudioRecord.STATE_INITIALIZED)
                throw new AudioFailure("This phone could not open a supported microphone format.");
            activeRecord = recorder;
            if (Build.VERSION.SDK_INT >= 29) {
                recorder.registerAudioRecordingCallback(main::post, microphoneState);
                recordingCallbackRegistered = true;
            }
            AudioDeviceInfo selectedInput = null;
            for (AudioDeviceInfo device : audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)) {
                boolean headset = device.getType() == AudioDeviceInfo.TYPE_WIRED_HEADSET || device.getType() == AudioDeviceInfo.TYPE_USB_HEADSET || device.getType() == AudioDeviceInfo.TYPE_USB_DEVICE;
                if (("headset".equals(selectedMicrophone) && headset) || ("phone".equals(selectedMicrophone) && device.getType() == AudioDeviceInfo.TYPE_BUILTIN_MIC)) { selectedInput = device; break; }
            }
            if (selectedInput == null || !recorder.setPreferredDevice(selectedInput))
                throw new AudioFailure("The selected microphone is unavailable. Choose Phone microphone, or connect a headset with a microphone.");
            int selectedInputId = selectedInput.getId();
            int frameCount = rate / 100; // 10 ms DSP blocks; hardware/transport adds latency.
            int minOutput = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (minOutput <= 0) throw new AudioFailure("This phone could not open headphone audio playback.");
            track = new AudioTrack.Builder().setAudioAttributes(playbackAttributes())
                    .setAudioFormat(new AudioFormat.Builder().setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                    .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(Math.max(minOutput, frameCount * 2 * 6))
                    .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY).build();
            activeTrack = track;
            if (track.getState() != AudioTrack.STATE_INITIALIZED || !track.setPreferredDevice(output))
                throw new AudioFailure("Android could not select these headphones. Try wired or USB-C headphones.");
            track.addOnRoutingChangedListener(routeListener, main);
            short[] input = new short[frameCount];
            short[] processed = new short[frameCount];
            // Activate the route with silence before reading or writing any microphone samples.
            if (!requested) return;
            short[] prime = new short[track.getBufferSizeInFrames()];
            int primed = track.write(prime, 0, prime.length, AudioTrack.WRITE_NON_BLOCKING);
            if (primed <= 0) throw new AudioFailure("Headphone playback could not be prepared. Reconnect your headphones.");
            track.play();
            long routeDeadline = SystemClock.elapsedRealtime() + 1500;
            while (requested && !isExpectedRoute(track.getRoutedDevice()) && SystemClock.elapsedRealtime() < routeDeadline) {
                track.write(processed, 0, processed.length, AudioTrack.WRITE_NON_BLOCKING);
                Thread.sleep(20);
            }
            if (!requested) return;
            if (!isExpectedRoute(track.getRoutedDevice()))
                throw new AudioFailure("Headphone routing could not be verified. Try wired or USB-C headphones.");
            routeVerified = true;
            AudioProcessor.Settings applied = settings;
            try {
                if (NoiseSuppressor.isAvailable()) {
                    suppressor = NoiseSuppressor.create(recorder.getAudioSessionId());
                    if (suppressor != null) suppressor.setEnabled(!applied.monitor && preferenceSnapshot.optBoolean("deviceSuppression", false) && applied.reduction > 0);
                }
            } catch (RuntimeException ignored) {
                if (suppressor != null) { try { suppressor.release(); } catch (RuntimeException unused) { } }
                suppressor = null;
            }
            suppressorEnabled = suppressionEnabled(suppressor);
            AudioProcessor processor = new AudioProcessor(rate, applied);
            if (!requested) return;
            recorder.startRecording();
            if (recorder.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING)
                throw new AudioFailure("Microphone recording did not start. Check Android microphone privacy settings.");
            if (Build.VERSION.SDK_INT >= 29) {
                AudioRecordingConfiguration configuration = recorder.getActiveRecordingConfiguration();
                if (configuration != null && configuration.isClientSilenced())
                    throw new AudioFailure("Android silenced the microphone. Check microphone privacy settings and close other recording apps.");
            }
            initialPreferences = AppStore.sanitizeSettings(preferenceSnapshot);
            AppStore.put(initialPreferences, "mode", applied.mode);
            AppStore.put(initialPreferences, "clarity", applied.clarity);
            AppStore.put(initialPreferences, "reduction", applied.reduction);
            AppStore.put(initialPreferences, "awareness", applied.awareness);
            AppStore.put(initialPreferences, "volume", applied.volume);
            AppStore.put(initialPreferences, "micGainDb", applied.micGainDb);
            AppStore.put(initialPreferences, "monitor", applied.monitor);
            AppStore.put(initialPreferences, "humHz", applied.humHz);
            sessionWall = System.currentTimeMillis();
            sessionElapsed = SystemClock.elapsedRealtime();
            synchronized (STATE_LOCK) {
                startedAt = sessionWall; startedElapsed = sessionElapsed; sampleRate = rate;
                hardwareSuppression = suppressorEnabled;
            }
            setState("listening", "Listening through " + routeName(output) + ".");
            main.post(() -> {
                if (requested) getSystemService(NotificationManager.class).notify(NOTIFICATION_ID,
                        notification("Microphone active · " + routeName(output)));
            });
            int emptyReads = 0;
            int startupUnderruns = track.getUnderrunCount();
            long nextMediaCheck = 0;
            while (requested) {
                if (!isExpectedRoute(track.getRoutedDevice()))
                    throw new AudioFailure("Audio routing changed. Listening stopped to avoid speaker playback.");
                int count = recorder.read(input, 0, input.length, AudioRecord.READ_BLOCKING);
                if (!requested) break;
                if (count < 0) throw new AudioFailure("The microphone was interrupted. Close other audio apps and try again.");
                if (count == 0) { if (++emptyReads > 30) throw new AudioFailure("The microphone returned no audio."); continue; }
                emptyReads = 0;
                AudioDeviceInfo actualInput = recorder.getRoutedDevice();
                if (actualInput != null && actualInput.getId() != selectedInputId)
                    throw new AudioFailure("Android changed the microphone route. Choose an available microphone and start again.");
                if (!selectedMicrophone.equals(preferenceSnapshot.optString("microphone", "phone")))
                    throw new AudioFailure("Microphone selection changed. Tap Start to use the new microphone.");
                synchronized (STATE_LOCK) { inputRoute = actualInput == null ? "Input route not reported by Android" : ("headset".equals(selectedMicrophone) ? "Headset: " : "Phone: ") + actualInput.getProductName().toString(); }
                boolean classify = preferenceSnapshot.optBoolean("soundCategories", true);
                if (classify && classifier == null) { classifier = new SoundClassifier(getApplicationContext(), rate); activeClassifier = classifier; }
                if (!classify && classifier != null) { activeClassifier = null; classifier.close(); classifier = null; }
                if (classifier != null) classifier.offer(input, count);
                AudioProcessor.Settings latest = settings;
                if (latest != applied) {
                    processor.configure(latest);
                    if (suppressor != null) {
                        try { suppressor.setEnabled(!latest.monitor && preferenceSnapshot.optBoolean("deviceSuppression", false) && latest.reduction > 0); }
                        catch (RuntimeException ignored) { suppressor.release(); suppressor = null; }
                    }
                    suppressorEnabled = suppressionEnabled(suppressor);
                    applied = latest;
                }
                long tick = System.nanoTime();
                processor.process(input, processed, count);
                double processingTime = (System.nanoTime() - tick) / 1_000_000.0;
                int written = 0;
                while (requested && written < count) {
                    if (!isExpectedRoute(track.getRoutedDevice()))
                        throw new AudioFailure("Audio output changed. Listening stopped.");
                    int amount = track.write(processed, written, count - written, AudioTrack.WRITE_BLOCKING);
                    if (!requested) break;
                    if (amount <= 0) throw new AudioFailure("Headphone playback was interrupted. Reconnect and try again.");
                    written += amount;
                }
                synchronized (STATE_LOCK) {
                    inputDbfs = processor.getInputDbfs(); outputDbfs = processor.getOutputDbfs(); processingMs = processingTime;
                    hardwareSuppression = suppressorEnabled;
                    underruns = Math.max(0, track.getUnderrunCount() - startupUnderruns);
                    if (SystemClock.elapsedRealtime() >= nextMediaCheck) {
                        mediaVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                        mediaMaximum = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                        nextMediaCheck = SystemClock.elapsedRealtime() + 500;
                    }
                }
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (AudioFailure error) {
            if (requested) { finishState = "error"; finishMessage = error.getMessage(); }
        } catch (SecurityException error) {
            if (requested) { finishState = "error"; finishMessage = "Microphone access was denied. Check permissions and Android microphone privacy settings."; }
        } catch (RuntimeException error) {
            if (requested) { finishState = "error"; finishMessage = "Audio could not continue on this device. Try wired headphones, close other audio apps, and restart listening."; }
        } finally {
            requested = false;
            routeVerified = false;
            activeRecord = null; activeTrack = null;
            activeClassifier = null;
            if (classifier != null) classifier.close();
            if (track != null) {
                try { track.pause(); track.flush(); track.removeOnRoutingChangedListener(routeListener); } catch (RuntimeException ignored) { }
                try { track.release(); } catch (RuntimeException ignored) { }
            }
            if (recorder != null) {
                if (recordingCallbackRegistered && Build.VERSION.SDK_INT >= 29) {
                    try { recorder.unregisterAudioRecordingCallback(microphoneState); } catch (RuntimeException ignored) { }
                }
                try { recorder.stop(); } catch (RuntimeException ignored) { }
                try { recorder.release(); } catch (RuntimeException ignored) { }
            }
            if (suppressor != null) { try { suppressor.release(); } catch (RuntimeException ignored) { } }
            if (focusHeld) { try { audioManager.abandonAudioFocusRequest(focusRequest); } catch (RuntimeException ignored) { } }
            long length = sessionElapsed == 0 ? 0 : Math.max(0, SystemClock.elapsedRealtime() - sessionElapsed);
            synchronized (STATE_LOCK) { durationMs = length; hardwareSuppression = false; }
            if (length >= 1000) {
                try { AppStore.recordSession(getApplicationContext(), initialPreferences, sessionWall, length / 1000); }
                catch (RuntimeException ignored) { /* Audio shutdown must succeed even if local storage fails. */ }
            }
            main.post(() -> {
                worker = null;
                expectedOutputId = -1;
                setState(finishState, finishMessage);
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf();
            });
        }
    }

    private static boolean suppressionEnabled(NoiseSuppressor suppressor) {
        try { return suppressor != null && suppressor.getEnabled(); }
        catch (RuntimeException ignored) { return false; }
    }

    private boolean isExpectedRoute(AudioDeviceInfo device) {
        return device != null && device.getId() == expectedOutputId && outputPriority(device) < Integer.MAX_VALUE;
    }

    private void requestStop(String endState, String reason) {
        finishState = endState;
        finishMessage = reason;
        requested = false;
        // Silence and discard queued output immediately; stop recording to unblock its read.
        AudioTrack track = activeTrack;
        if (track != null) {
            try { track.setVolume(0); track.pause(); track.flush(); } catch (RuntimeException ignored) { }
        }
        AudioRecord record = activeRecord;
        if (record != null) { try { record.stop(); } catch (RuntimeException ignored) { } }
        Thread thread = worker;
        if (thread != null) thread.interrupt();
    }

    private static void setState(String status, String text) {
        synchronized (STATE_LOCK) { state = status; message = text; }
    }

    private static AudioAttributes playbackAttributes() {
        return new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
    }

    public static String routeName(AudioDeviceInfo device) {
        if (device == null) return "Not connected";
        switch (device.getType()) {
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:
            case AudioDeviceInfo.TYPE_WIRED_HEADSET: return "Wired headphones";
            case AudioDeviceInfo.TYPE_USB_HEADSET: return "USB headphones";
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP: return "Bluetooth audio";
            default: return "Bluetooth hearing device";
        }
    }

    private Notification notification(String text) {
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, AudioService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(org.aura.listen.R.drawable.ic_aura).setContentTitle("AURA listening")
                .setContentText(text).setOngoing(true).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_SERVICE)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .addAction(new Notification.Action.Builder(android.R.drawable.ic_media_pause, "Stop", stop).build());
        Intent launch = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (launch != null) builder.setContentIntent(PendingIntent.getActivity(this, 0, launch,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        return builder.build();
    }

    private static JSONObject asJson(AudioProcessor.Settings value) throws JSONException {
        return new JSONObject().put("mode", value.mode).put("clarity", value.clarity).put("reduction", value.reduction)
                .put("awareness", value.awareness).put("volume", value.volume).put("micGainDb", value.micGainDb).put("monitor", value.monitor).put("humHz", value.humHz);
    }

    @Override public void onTaskRemoved(Intent rootIntent) {
        requestStop("idle", "Listening stopped when AURA was closed.");
        if (worker == null) stopSelf();
        super.onTaskRemoved(rootIntent);
    }
    @Override public void onDestroy() {
        requestStop("idle", "Listening stopped.");
        if (audioManager != null) audioManager.unregisterAudioDeviceCallback(devices);
        if (receiverRegistered) { unregisterReceiver(noisyReceiver); receiverRegistered = false; }
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
    private static final class AudioFailure extends Exception {
        AudioFailure(String message) { super(message); }
    }
}
