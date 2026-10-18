package org.aura.listen;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioDeviceInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.ScrollView;
import android.text.InputType;
import android.text.InputFilter;
import android.view.WindowManager;
import org.aura.listen.cloud.CloudSync;
import org.aura.listen.audio.AudioService;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/** Assets-only UI shell. Cloud HTTPS requests and credentials stay in native code. */
public class MainActivity extends Activity {
    private static final int PERMISSIONS = 10, EXPORT = 11, IMPORT = 12;
    private static final String ASSET_ROOT = "file:///android_asset/";
    private final Handler main = new Handler(Looper.getMainLooper());
    private WebView web;
    private volatile String notice = "";
    private volatile long noticeSeq = 0;
    private boolean pendingStart = false;
    private String pendingExport;
    private CloudSync cloud;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        cloud = CloudSync.get(this);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        web = new WebView(this);
        web.setTag("aura-web");
        web.setBackgroundColor(Color.rgb(18, 23, 34));
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Color.rgb(18, 23, 34));
        frame.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(frame);
        // Pad the native container, not WebView: the HTML viewport must resize when the
        // keyboard opens. On older Android, normal decor fitting + adjustResize does this.
        if (Build.VERSION.SDK_INT >= 30) {
            frame.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets padding = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                view.setPadding(padding.left, padding.top, padding.right, padding.bottom);
                return WindowInsets.CONSUMED;
            });
            frame.requestApplyInsets();
        }
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setBlockNetworkLoads(true);
        settings.setDomStorageEnabled(false);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setTextZoom(Math.round(getResources().getConfiguration().fontScale * 100));
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { return true; }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.startsWith(ASSET_ROOT) && !url.contains("..")) return null;
                return new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream(new byte[0]));
            }
        });
        web.addJavascriptInterface(new Bridge(), "Aura");
        web.loadUrl(ASSET_ROOT + "index.html");
    }

    private final class Bridge {
        @JavascriptInterface public String state() {
            JSONObject result = new JSONObject();
            AppStore.put(result, "engine", AudioService.snapshot());
            AppStore.put(result, "settings", AppStore.settings(MainActivity.this));
            AppStore.put(result, "profiles", AppStore.profiles(MainActivity.this));
            AppStore.put(result, "sessions", AppStore.sessions(MainActivity.this));
            AppStore.put(result, "onboardingDone", AppStore.onboardingDone(MainActivity.this));
            AppStore.put(result, "cloud", cloud.state());
            AppStore.put(result, "notice", notice);
            AppStore.put(result, "noticeSeq", noticeSeq);
            JSONObject device = new JSONObject();
            AudioDeviceInfo output = AudioService.findOutput(MainActivity.this);
            AppStore.put(device, "androidVersion", Build.VERSION.RELEASE);
            AppStore.put(device, "sdk", Build.VERSION.SDK_INT);
            AppStore.put(device, "headphones", output != null);
            AppStore.put(device, "route", output == null ? "No headphones connected" : output.getProductName().toString());
            AppStore.put(device, "micPermission", checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED);
            AppStore.put(device, "notificationPermission", Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED);
            AppStore.put(result, "device", device);
            return result.toString();
        }

        @JavascriptInterface public void command(String json) {
            if (json == null || json.length() > 16384) return;
            main.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                try { handleCommand(new JSONObject(json)); }
                catch (Exception e) { inform(e.getMessage() == null ? "Could not complete that action." : e.getMessage()); }
            });
        }
    }

    private void handleCommand(JSONObject command) throws JSONException {
        JSONObject payload = command.optJSONObject("payload");
        if (payload == null) payload = new JSONObject();
        switch (command.optString("type")) {
            case "start": startListening(); break;
            case "stop": stopListening(); break;
            case "settings": AppStore.setSettings(this, payload); updateEngine(); break;
            case "saveProfile": AppStore.saveProfile(this, payload.optString("name")); inform("Profile saved on this phone."); break;
            case "loadProfile": AppStore.loadProfile(this, payload.optString("id")); updateEngine(); inform("Profile loaded."); break;
            case "deleteProfile": AppStore.deleteProfile(this, payload.optString("id")); inform("Profile deleted."); break;
            case "feedback": AppStore.feedback(this, payload.optString("id"), payload.optString("rating"), payload.optString("note")); inform("Feedback saved. Adjustments are always your choice."); break;
            case "applySuggestion": AppStore.applySuggestion(this, payload.optString("id")); updateEngine(); inform("Suggestion applied. Output volume has not been increased."); break;
            case "cloudConfigure": cloudSetup(); break;
            case "cloudSignIn": cloudCredentials(false); break;
            case "cloudSignUp": cloudCredentials(true); break;
            case "cloudResetPassword": org.aura.listen.cloud.RecoveryDialog.show(this, cloud, cloudResult()); break;
            case "cloudSignOut": cloud.action("signOut", cloudResult()); break;
            case "cloudBackup": confirmCloud("Replace your cloud backup?", "Your settings, profiles, context, session details, and written notes will be sent to your Supabase account. No audio is uploaded. This replaces any previous backup for this account.", "Back up", "backup"); break;
            case "cloudRestore": cloud.action("restore", cloudResult()); break;
            case "cloudDeleteBackup": confirmCloud("Delete cloud backup?", "This permanently removes your account's cloud backup. Data on this phone stays here.", "Delete backup", "deleteBackup"); break;
            case "cloudDeleteAccount": confirmCloud("Delete your AURA cloud account?", "This permanently deletes your account and its cloud backup in the configured AURA project. Other signed-in devices will lose access. Data on this phone stays here.", "Delete account", "deleteAccount"); break;
            case "cloudDisconnect":
                new AlertDialog.Builder(this).setTitle("Disconnect this project?").setMessage("The saved project configuration and sign-in tokens will be removed from this phone. Your cloud account and backup remain; no local listening data is deleted.")
                    .setNegativeButton("Cancel", null).setPositiveButton("Disconnect", (d, w) -> {
                        try { cloud.disconnect(); inform("Project disconnected."); } catch (Exception e) { inform(e.getMessage()); }
                    }).show(); break;
            case "export": exportData(); break;
            case "import": importData(); break;
            case "clearHistory":
                new AlertDialog.Builder(this).setTitle("Delete session history?").setMessage("All saved session details and notes will be removed from this phone.")
                    .setNegativeButton("Cancel", null).setPositiveButton("Delete", (dialog, which) -> afterStopped(() -> { AppStore.clearHistory(this); inform("Session history deleted."); }, 0)).show();
                break;
            case "reset":
                new AlertDialog.Builder(this).setTitle("Reset AURA?").setMessage("Listening will stop. Settings, profiles, session history, and notes will be deleted. Export a backup first if you want to keep them.")
                    .setNegativeButton("Cancel", null).setPositiveButton("Reset", (dialog, which) -> afterStopped(() -> { AppStore.reset(this); inform("AURA has been reset."); }, 0)).show();
                break;
            case "onboardingDone": AppStore.completeOnboarding(this); break;
            case "permissions": requestAppPermissions(false); break;
            case "appSettings": startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))); break;
            default: inform("This action is not available.");
        }
        refresh();
    }

    private boolean engineActive() {
        String status = AudioService.snapshot().optString("status");
        return "starting".equals(status) || "listening".equals(status) || "stopping".equals(status);
    }

    private CloudSync.Result cloudResult() {
        return (message, backup) -> main.post(() -> {
            if (isFinishing() || isDestroyed()) return;
            inform(message);
            if (backup != null) confirmImport(backup);
        });
    }

    private void confirmCloud(String title, String message, String actionLabel, String operation) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(message + "\n\nAccount: " + cloud.state().optString("email") + "\nProject: " + cloud.projectUrl())
            .setNegativeButton("Cancel", null).setPositiveButton(actionLabel, (d, w) -> cloud.action(operation, cloudResult())).show();
    }

    private LinearLayout cloudForm() {
        LinearLayout form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(22 * getResources().getDisplayMetrics().density);
        form.setPadding(padding, padding / 2, padding, padding / 2); return form;
    }

    private EditText field(LinearLayout form, String label, int type, int maxLength) {
        TextView text = new TextView(this); text.setText(label); form.addView(text);
        EditText input = new EditText(this); input.setId(View.generateViewId()); text.setLabelFor(input.getId());
        input.setInputType(type); input.setSingleLine(true); input.setContentDescription(label);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxLength)});
        input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        input.setSaveEnabled(false); form.addView(input); return input;
    }

    private void cloudSetup() {
        LinearLayout form = cloudForm();
        TextView help = new TextView(this); help.setText(R.string.cloud_setup_help); form.addView(help);
        EditText url = field(form, "Supabase project URL", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI, 200);
        url.setText(cloud.projectUrl().isEmpty() ? "https://cwnfvwezyqmwviixaqxr.supabase.co" : cloud.projectUrl());
        EditText key = field(form, "Publishable or anon key", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS, 4096);
        ScrollView scroll = new ScrollView(this); scroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Connect Supabase").setView(scroll).setNegativeButton("Cancel", null).setPositiveButton("Save project", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try { cloud.configure(url.getText().toString(), key.getText().toString()); dialog.dismiss(); inform("Project saved. Sign in when you want to back up your data."); }
            catch (Exception e) { key.setError(e.getMessage()); }
        })); dialog.show();
    }

    private void cloudCredentials(boolean create) {
        LinearLayout form = cloudForm();
        TextView help = new TextView(this); help.setText(getString(R.string.cloud_credentials_help, cloud.projectUrl())); form.addView(help);
        EditText email = field(form, "Email", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, 254);
        EditText password = field(form, create ? "Password (12–128 characters)" : "Password", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD, 128);
        ScrollView scroll = new ScrollView(this); scroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(create ? "Create an AURA account" : "Sign in to AURA").setView(scroll)
            .setNegativeButton("Cancel", null).setPositiveButton(create ? "Create account" : "Sign in", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String mail = email.getText().toString().trim(), secret = password.getText().toString();
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(mail).matches()) { email.setError("Enter a valid email."); return; }
            if (secret.length() < (create ? 12 : 1)) { password.setError(create ? "Use at least 12 characters." : "Enter your password."); return; }
            password.setText(""); dialog.dismiss(); cloud.authenticate(mail, secret, create, cloudResult());
        }));
        dialog.setOnDismissListener(d -> password.setText(""));
        if (dialog.getWindow() != null) dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        dialog.show();
    }

    private void startListening() {
        if (engineActive()) return;
        if (AudioService.findOutput(this) == null) { inform("Connect wired, USB-C, or compatible Bluetooth headphones first."); return; }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { requestAppPermissions(true); return; }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED && !getPreferences(MODE_PRIVATE).getBoolean("notificationsAsked", false)) { requestAppPermissions(true); return; }
        Intent intent = new Intent(this, AudioService.class).setAction(AudioService.ACTION_START).putExtra("settings", AppStore.settings(this).toString());
        try { startForegroundService(intent); }
        catch (RuntimeException e) { inform("Android could not start listening. Open AURA and try again; check microphone permissions in app settings."); }
    }

    private void stopListening() {
        pendingStart = false;
        if (engineActive()) startService(new Intent(this, AudioService.class).setAction(AudioService.ACTION_STOP));
    }

    private void updateEngine() {
        if (engineActive()) startService(new Intent(this, AudioService.class).setAction(AudioService.ACTION_UPDATE).putExtra("settings", AppStore.settings(this).toString()));
    }

    private void requestAppPermissions(boolean startAfter) {
        ArrayList<String> permissions = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) permissions.add(Manifest.permission.RECORD_AUDIO);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS);
            getPreferences(MODE_PRIVATE).edit().putBoolean("notificationsAsked", true).apply();
        }
        if (permissions.isEmpty()) { if (startAfter) startListening(); else inform("Microphone permission is enabled."); return; }
        pendingStart = startAfter;
        requestPermissions(permissions.toArray(new String[0]), PERMISSIONS);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != PERMISSIONS) return;
        boolean shouldStart = pendingStart;
        pendingStart = false;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            if (shouldStart) startListening();
            else inform("Microphone permission enabled.");
        } else inform("Microphone permission is needed to listen. You can enable it in Android app settings.");
        refresh();
    }

    private void afterStopped(Runnable action, int attempt) {
        if (attempt == 0) stopListening();
        if (!engineActive()) { action.run(); refresh(); return; }
        if (attempt >= 50) { inform("Listening is still stopping. Try again in a moment."); return; }
        main.postDelayed(() -> afterStopped(action, attempt + 1), 100);
    }

    private void exportData() {
        pendingExport = AppStore.exportData(this).toString();
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "aura-backup-" + new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()) + ".json");
        try { startActivityForResult(intent, EXPORT); }
        catch (RuntimeException e) { inform("No document picker is available on this phone."); }
    }

    private void importData() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/json", "text/plain", "application/octet-stream"});
        try { startActivityForResult(intent, IMPORT); }
        catch (RuntimeException e) { inform("No document picker is available on this phone."); }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == EXPORT) {
            String content = pendingExport == null ? AppStore.exportData(this).toString() : pendingExport;
            pendingExport = null;
            new Thread(() -> {
                try (OutputStream stream = getContentResolver().openOutputStream(uri, "wt")) {
                    if (stream == null) throw new IllegalArgumentException("Could not open the backup file.");
                    stream.write(content.getBytes(StandardCharsets.UTF_8));
                    main.post(() -> inform("Backup exported. It contains preferences and session notes, never audio."));
                } catch (Exception e) { main.post(() -> inform("Could not write the backup. Choose another location.")); }
            }, "aura-export").start();
        } else if (requestCode == IMPORT) {
            new Thread(() -> {
                try (InputStream input = getContentResolver().openInputStream(uri); ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
                    if (input == null) throw new IllegalArgumentException("Could not open the backup.");
                    byte[] chunk = new byte[8192];
                    int count;
                    while ((count = input.read(chunk)) != -1) {
                        if (buffer.size() + count > 1_048_576) throw new IllegalArgumentException("Backup is too large (maximum 1 MB).");
                        buffer.write(chunk, 0, count);
                    }
                    String content = buffer.toString(StandardCharsets.UTF_8.name());
                    main.post(() -> confirmImport(content));
                } catch (Exception e) { main.post(() -> inform("Could not read the backup. Use an AURA JSON file under 1 MB.")); }
            }, "aura-import").start();
        }
    }

    private void confirmImport(String content) {
        if (isFinishing() || isDestroyed()) return;
        new AlertDialog.Builder(this).setTitle("Restore this backup?").setMessage("Listening will stop. This replaces current settings, profiles, and session history. Your backup is checked before anything is replaced.")
            .setNegativeButton("Cancel", null).setPositiveButton("Restore", (dialog, which) -> afterStopped(() -> {
                try { AppStore.importData(this, content); inform("Backup restored on this phone."); }
                catch (Exception e) { inform("Backup was not restored: " + (e instanceof JSONException ? "Invalid JSON or missing fields." : e.getMessage())); }
            }, 0)).show();
    }

    private void inform(String text) { notice = text; noticeSeq++; refresh(); }
    private void refresh() {
        if (web != null && !isDestroyed()) web.evaluateJavascript("window.dispatchEvent(new Event('auraRefresh'))", null);
    }

    @Override protected void onResume() { super.onResume(); refresh(); }
    @Override public void onBackPressed() {
        if (web != null) web.evaluateJavascript("window.auraBack ? window.auraBack() : false", handled -> {
            if (!"true".equals(handled)) moveTaskToBack(true);
        });
        else super.onBackPressed();
    }
    @Override protected void onDestroy() {
        main.removeCallbacksAndMessages(null);
        if (web != null) { web.removeJavascriptInterface("Aura"); web.destroy(); web = null; }
        super.onDestroy();
    }
}
