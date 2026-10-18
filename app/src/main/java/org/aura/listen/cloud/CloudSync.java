package org.aura.listen.cloud;

import android.content.Context;
import android.app.Application;
import android.content.SharedPreferences;
import org.aura.listen.AppStore;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Optional, explicit metadata backup. No audio samples or passwords are persisted. */
public final class CloudSync {
    public interface Result { void complete(String message, String backup); }
    private static CloudSync instance;
    private final Application context;
    private final SharedPreferences config;
    private final SessionVault vault;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private volatile JSONObject session = new JSONObject();
    private volatile String status = "Cloud backup is optional. Listening works offline.";
    private static final int LIMIT = 1_048_576;

    public static synchronized CloudSync get(Context context) {
        if (instance == null) instance = new CloudSync((Application) context.getApplicationContext());
        return instance;
    }
    private CloudSync(Application context) {
        this.context = context;
        this.config = context.getSharedPreferences("aura.cloud.config", Context.MODE_PRIVATE);
        if (!config.contains("initialized")) {
            // Supabase publishable keys are intentionally public; RLS provides authorization.
            config.edit().putBoolean("initialized", true)
                .putString("url", "https://cwnfvwezyqmwviixaqxr.supabase.co")
                .putString("key", "sb_publishable_HCaucTD2Lyefo373DCFr8g_2yjfOt8T").apply();
        }
        this.vault = new SessionVault(context);
        try { session = new JSONObject(vault.read()); }
        catch (Exception ignored) {
            try { vault.clear(); } catch (IllegalStateException e) { status = e.getMessage(); return; }
            status = "Please sign in again. The stored session could not be unlocked.";
        }
    }
    public JSONObject state() {
        JSONObject state = new JSONObject();
        AppStore.put(state, "configured", !config.getString("url", "").isEmpty());
        AppStore.put(state, "signedIn", !session.optString("access_token").isEmpty());
        AppStore.put(state, "email", session.optString("email"));
        AppStore.put(state, "project", config.getString("url", ""));
        AppStore.put(state, "busy", busy.get());
        AppStore.put(state, "status", status);
        AppStore.put(state, "lastBackup", config.getLong("lastBackup", 0));
        return state;
    }
    public String projectUrl() { return config.getString("url", ""); }
    public void configure(String url, String publicKey) {
        if (!busy.compareAndSet(false, true)) throw new IllegalStateException("Wait for the current cloud action to finish.");
        try {
            String safeUrl = CloudConfig.validateUrl(url), safeKey = CloudConfig.validateKey(publicKey);
            vault.clear(); session = new JSONObject();
            if (!config.edit().clear().putBoolean("initialized", true).putString("url", safeUrl).putString("key", safeKey).commit())
                throw new IllegalStateException("Could not save project settings.");
            status = "Project saved. Sign in to use your private cloud backup.";
        } finally { busy.set(false); }
    }
    public void disconnect() {
        if (!busy.compareAndSet(false, true)) throw new IllegalStateException("Wait for the current cloud action to finish.");
        try { vault.clear(); session = new JSONObject(); config.edit().clear().putBoolean("initialized", true).apply(); status = "Disconnected. Your local data is unchanged."; }
        finally { busy.set(false); }
    }
    public void authenticate(String email, String password, boolean create, Result callback) {
        if (email == null || email.length() > 254 || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
            || password == null || password.length() < (create ? 12 : 1) || password.length() > 128) {
            callback.complete(create ? "Use a valid email and a password of 12–128 characters." : "Enter your email and password.", null); return;
        }
        submit(() -> {
            JSONObject body = new JSONObject().put("email", email).put("password", password);
            JSONObject result = new JSONObject(request("POST", create ? "/auth/v1/signup" : "/auth/v1/token?grant_type=password", body.toString(), null));
            if (result.optString("access_token").isEmpty()) {
                status = "If signup is available, check your email to confirm your account, then sign in. Email delivery depends on the project setup.";
            } else {
                saveSession(result);
                status = "Signed in. Nothing is uploaded until you choose Back up now.";
            }
            return null;
        }, callback);
    }
    public void recover(String email, String code, String newPassword, Result callback) {
        if (email == null || email.length() > 254 || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            callback.complete("Enter a valid email.", null); return;
        }
        if (code != null && (!code.matches("[0-9]{6,10}") || newPassword == null || newPassword.length() < 12 || newPassword.length() > 128)) {
            callback.complete("Enter your recovery code and a new password of 12–128 characters.", null); return;
        }
        submit(() -> {
            if (code == null) {
                request("POST", "/auth/v1/recover", new JSONObject().put("email", email).toString(), null);
                status = "If the account is eligible, a recovery email will arrive. Enter its code here. Email delivery depends on the project setup.";
            } else {
                JSONObject verified = new JSONObject(request("POST", "/auth/v1/verify", new JSONObject().put("type", "recovery").put("email", email).put("token", code).toString(), null));
                request("PUT", "/auth/v1/user", new JSONObject().put("password", newPassword).toString(), verified.getString("access_token"));
                saveSession(verified);
                status = "Password updated. You are signed in; no listening data has been uploaded.";
            }
            return null;
        }, callback);
    }
    public void action(String action, Result callback) {
        submit(() -> {
            if ("signOut".equals(action)) {
                try { if (!session.optString("access_token").isEmpty()) request("POST", "/auth/v1/logout?scope=local", "{}", session.optString("access_token")); }
                catch (Exception ignored) { /* Local sign-out must work offline. */ }
                vault.clear(); session = new JSONObject(); config.edit().remove("lastBackup").apply();
                status = "Signed out on this phone. Your local data and cloud backup are unchanged."; return null;
            }
            String token = accessToken();
            String uid = session.getString("user_id");
            String row = "/rest/v1/aura_backups?user_id=eq." + uid;
            switch (action) {
                case "backup": {
                    JSONObject body = new JSONObject().put("user_id", uid).put("data", AppStore.exportData(context));
                    request("POST", "/rest/v1/aura_backups?on_conflict=user_id", body.toString(), token);
                    config.edit().putLong("lastBackup", System.currentTimeMillis()).apply();
                    status = "Cloud backup saved. Previous cloud backup replaced; no audio was sent."; return null;
                }
                case "restore": {
                    JSONArray rows = new JSONArray(request("GET", row + "&select=data&limit=1", null, token));
                    if (rows.length() == 0) throw new IllegalStateException("No cloud backup exists for this account yet.");
                    String backup = rows.getJSONObject(0).getJSONObject("data").toString();
                    status = "Backup downloaded. Confirm Restore to replace local data."; return backup;
                }
                case "deleteBackup":
                    request("DELETE", row, null, token); config.edit().remove("lastBackup").apply();
                    status = "Cloud backup deleted. Local data is unchanged."; return null;
                case "deleteAccount":
                    request("POST", "/rest/v1/rpc/aura_delete_account", "{}", token);
                    vault.clear(); session = new JSONObject(); config.edit().remove("lastBackup").apply();
                    status = "Account and its cloud backup deleted. Local data is unchanged."; return null;
                default: throw new IllegalArgumentException("Unknown cloud action.");
            }
        }, callback);
    }
    private interface Operation { String run() throws Exception; }
    private void submit(Operation operation, Result callback) {
        if (!busy.compareAndSet(false, true)) { callback.complete("Wait for the current cloud action to finish.", null); return; }
        worker.execute(() -> {
            String backup = null;
            try { backup = operation.run(); }
            catch (IllegalStateException e) { status = e.getMessage(); }
            catch (Exception ignored) { status = "Cloud action failed. Check your connection and project setup, then try again. Local data was not replaced."; }
            finally { busy.set(false); }
            callback.complete(status, backup);
        });
    }
    private String accessToken() throws Exception {
        if (session.optString("access_token").isEmpty()) throw new IllegalStateException("Sign in first.");
        if (session.optLong("expires_at") <= System.currentTimeMillis() / 1000 + 60) {
            JSONObject body = new JSONObject().put("refresh_token", session.getString("refresh_token"));
            try { saveSession(new JSONObject(request("POST", "/auth/v1/token?grant_type=refresh_token", body.toString(), null))); }
            catch (CloudHttpException e) {
                if (e.code == 400 || e.code == 401 || e.code == 403 || e.code == 422) {
                    vault.clear(); session = new JSONObject(); config.edit().remove("lastBackup").apply();
                    throw new IllegalStateException("Your session expired or was revoked. Please sign in again.");
                }
                throw e;
            }
        }
        return session.getString("access_token");
    }
    private void saveSession(JSONObject response) throws Exception {
        JSONObject user = response.getJSONObject("user");
        String uid = UUID.fromString(user.getString("id")).toString();
        JSONObject safe = new JSONObject().put("user_id", uid).put("email", user.optString("email"))
            .put("access_token", response.getString("access_token")).put("refresh_token", response.getString("refresh_token"))
            .put("expires_at", response.optLong("expires_at", System.currentTimeMillis()/1000 + response.optLong("expires_in", 3600)));
        boolean accountChanged = !uid.equals(session.optString("user_id"));
        vault.write(safe.toString()); session = safe;
        if (accountChanged) config.edit().remove("lastBackup").apply();
    }
    private static final class CloudHttpException extends IllegalStateException {
        final int code;
        CloudHttpException(int code, String message) { super(message); this.code = code; }
    }
    private String request(String method, String path, String body, String token) throws Exception {
        String url = CloudConfig.validateUrl(config.getString("url", ""));
        String key = CloudConfig.validateKey(config.getString("key", ""));
        HttpsURLConnection connection = (HttpsURLConnection) new URL(url + path).openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(15000); connection.setReadTimeout(20000);
        connection.setRequestMethod(method);
        connection.setRequestProperty("apikey", key);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        if (token != null) connection.setRequestProperty("Authorization", "Bearer " + token);
        if (path.startsWith("/rest/v1/aura_backups") && "POST".equals(method))
            connection.setRequestProperty("Prefer", "resolution=merge-duplicates,return=minimal");
        try {
            if (body != null) {
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                if (bytes.length > LIMIT) throw new IllegalStateException("Backup exceeds the 1 MB limit.");
                connection.setDoOutput(true); connection.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream output = connection.getOutputStream()) { output.write(bytes); }
            }
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                if (code == 401 || code == 403) throw new CloudHttpException(code, "Access denied. Check your public project key, account confirmation, sign-in, and database policies.");
                if (code == 429) throw new CloudHttpException(code, "Too many attempts. Wait before trying again; free-plan limits may apply.");
                if (code == 400 || code == 422) throw new CloudHttpException(code, "Request not accepted. Check your sign-in details, email confirmation, and project setup.");
                throw new CloudHttpException(code, "Cloud request failed (HTTP " + code + "). Check that the free project is active and the AURA database migration is installed.");
            }
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192]; int read;
                while ((read = input.read(buffer)) != -1) {
                    if (output.size() + read > LIMIT) throw new IllegalStateException("Cloud response exceeds the 1 MB limit.");
                    output.write(buffer, 0, read);
                }
                return output.toString(StandardCharsets.UTF_8.name());
            }
        } finally { connection.disconnect(); }
    }
}
