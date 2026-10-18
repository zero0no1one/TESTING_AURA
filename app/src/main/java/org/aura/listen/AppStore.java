package org.aura.listen;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Local settings and session metadata. No microphone samples are stored here. */
public final class AppStore {
    private static final String PREFS = "aura.local.v1";
    public static final int MAX_PROFILES = 20;
    public static final int MAX_SESSIONS = 200;
    private AppStore() {}

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static JSONObject defaults() {
        JSONObject result = new JSONObject();
        put(result, "mode", "care");
        put(result, "clarity", 55);
        put(result, "reduction", 35);
        put(result, "awareness", 35);
        put(result, "volume", 25);
        put(result, "context", "general");
        put(result, "profileName", "");
        return result;
    }

    public static JSONObject sanitizeSettings(JSONObject source) {
        JSONObject result = defaults();
        if (source == null) return result;
        String mode = source.optString("mode", "care");
        if (!isMode(mode)) mode = "care";
        put(result, "mode", mode);
        String listeningContext = source.optString("context", "general");
        put(result, "context", validContext(listeningContext) ? listeningContext : "general");
        String profileName = source.optString("profileName", "").trim();
        put(result, "profileName", profileName.length() <= 40 ? profileName : "");
        for (String key : new String[]{"clarity", "reduction", "awareness", "volume"}) {
            double value = source.optDouble(key, result.optInt(key));
            if (!Double.isFinite(value)) value = result.optInt(key);
            put(result, key, (int) Math.round(Math.max(0, Math.min(100, value))));
        }
        return result;
    }

    private static boolean isMode(String mode) {
        return "care".equals(mode) || "focus".equals(mode) || "commute".equals(mode);
    }

    private static boolean validContext(String context) {
        return "general".equals(context) || "consultation".equals(context) || "pharmacy".equals(context)
            || "caregiving".equals(context) || "family".equals(context) || "study".equals(context) || "commute".equals(context);
    }

    public static synchronized JSONObject settings(Context context) {
        try { return sanitizeSettings(new JSONObject(prefs(context).getString("settings", "{}"))); }
        catch (JSONException e) { return defaults(); }
    }

    public static synchronized JSONObject setSettings(Context context, JSONObject source) {
        JSONObject safe = sanitizeSettings(source);
        prefs(context).edit().putString("settings", safe.toString()).apply();
        return safe;
    }

    public static synchronized JSONArray profiles(Context context) {
        return array(context, "profiles");
    }

    public static synchronized JSONArray sessions(Context context) {
        return array(context, "sessions");
    }

    private static JSONArray array(Context context, String key) {
        try { return new JSONArray(prefs(context).getString(key, "[]")); }
        catch (JSONException e) { return new JSONArray(); }
    }

    public static synchronized void saveProfile(Context context, String name) {
        name = name == null ? "" : name.trim();
        if (name.isEmpty() || name.length() > 40) throw new IllegalArgumentException("Use a profile name of 1–40 characters.");
        JSONArray profiles = profiles(context);
        if (profiles.length() >= MAX_PROFILES) throw new IllegalArgumentException("You can keep up to 20 profiles. Delete one before saving another.");
        JSONObject profile = new JSONObject();
        put(profile, "id", UUID.randomUUID().toString());
        put(profile, "name", name);
        JSONObject savedSettings = settings(context);
        put(savedSettings, "profileName", name);
        put(profile, "settings", savedSettings);
        profiles.put(profile);
        prefs(context).edit().putString("profiles", profiles.toString()).apply();
    }

    public static synchronized JSONObject loadProfile(Context context, String id) {
        JSONArray profiles = profiles(context);
        for (int i = 0; i < profiles.length(); i++) {
            JSONObject profile = profiles.optJSONObject(i);
            if (profile != null && id.equals(profile.optString("id"))) {
                JSONObject savedSettings = sanitizeSettings(profile.optJSONObject("settings"));
                put(savedSettings, "profileName", profile.optString("name"));
                return setSettings(context, savedSettings);
            }
        }
        throw new IllegalArgumentException("That profile is no longer available.");
    }

    public static synchronized void deleteProfile(Context context, String id) {
        JSONArray result = new JSONArray();
        JSONArray profiles = profiles(context);
        for (int i = 0; i < profiles.length(); i++) {
            JSONObject profile = profiles.optJSONObject(i);
            if (profile != null && !id.equals(profile.optString("id"))) result.put(profile);
        }
        prefs(context).edit().putString("profiles", result.toString()).apply();
    }

    /** Called by AudioService once, after a completed listening session. */
    public static synchronized void recordSession(Context context, String mode, long start, long duration) {
        JSONObject snapshot = settings(context); put(snapshot, "mode", isMode(mode) ? mode : "care");
        recordSession(context, snapshot, start, duration);
    }

    public static synchronized void recordSession(Context context, JSONObject initialSettings, long start, long duration) {
        if (duration < 1) return; // Duration is seconds, never an audio sample count.
        JSONObject snapshot = sanitizeSettings(initialSettings);
        JSONObject session = new JSONObject();
        put(session, "id", UUID.randomUUID().toString());
        put(session, "mode", snapshot.optString("mode"));
        put(session, "settings", snapshot);
        put(session, "startedAt", start);
        put(session, "durationSeconds", duration);
        put(session, "rating", JSONObject.NULL);
        put(session, "note", "");
        put(session, "suggestionApplied", false);
        JSONArray result = new JSONArray();
        result.put(session);
        JSONArray previous = sessions(context);
        for (int i = 0; i < Math.min(previous.length(), MAX_SESSIONS - 1); i++) result.put(previous.opt(i));
        prefs(context).edit().putString("sessions", result.toString()).apply();
    }

    public static synchronized void feedback(Context context, String id, String rating, String note) {
        if (!validRating(rating)) throw new IllegalArgumentException("Choose Clear, Okay, or Difficult.");
        if (note == null) note = "";
        if (note.length() > 300) throw new IllegalArgumentException("Keep notes to 300 characters.");
        JSONArray sessions = sessions(context);
        JSONObject session = findSession(sessions, id);
        put(session, "rating", rating);
        put(session, "note", note.trim());
        prefs(context).edit().putString("sessions", sessions.toString()).apply();
    }

    public static synchronized void applySuggestion(Context context, String id) {
        JSONArray sessions = sessions(context);
        JSONObject session = findSession(sessions, id);
        String rating = session.optString("rating");
        if (!validRating(rating)) throw new IllegalArgumentException("Rate this session first.");
        if (session.optBoolean("suggestionApplied")) throw new IllegalArgumentException("This suggestion has already been applied.");
        JSONObject current = settings(context);
        JSONObject settings = session.has("settings") ? sanitizeSettings(session.optJSONObject("settings")) : current;
        // A new recommendation must never raise the user's current output volume.
        put(settings, "volume", Math.min(current.optInt("volume"), settings.optInt("volume")));
        put(settings, "profileName", "");
        if ("difficult".equals(rating)) {
            put(settings, "clarity", settings.optInt("clarity") + 10);
            put(settings, "reduction", settings.optInt("reduction") - 5);
        } else if ("okay".equals(rating)) {
            put(settings, "clarity", settings.optInt("clarity") + 5);
        }
        setSettings(context, settings);
        put(session, "suggestionApplied", true);
        prefs(context).edit().putString("sessions", sessions.toString()).apply();
    }

    private static boolean validRating(String rating) {
        return "clear".equals(rating) || "okay".equals(rating) || "difficult".equals(rating);
    }

    private static JSONObject findSession(JSONArray sessions, String id) {
        for (int i = 0; i < sessions.length(); i++) {
            JSONObject session = sessions.optJSONObject(i);
            if (session != null && id.equals(session.optString("id"))) return session;
        }
        throw new IllegalArgumentException("That session is no longer available.");
    }

    public static synchronized boolean onboardingDone(Context context) { return prefs(context).getBoolean("onboarding", false); }
    public static synchronized void completeOnboarding(Context context) { prefs(context).edit().putBoolean("onboarding", true).apply(); }
    public static synchronized void clearHistory(Context context) { prefs(context).edit().remove("sessions").apply(); }
    public static synchronized void reset(Context context) { prefs(context).edit().clear().apply(); }

    public static synchronized JSONObject exportData(Context context) {
        JSONObject data = new JSONObject();
        put(data, "format", "aura-local-backup");
        put(data, "version", 1);
        put(data, "settings", settings(context));
        put(data, "profiles", profiles(context));
        put(data, "sessions", sessions(context));
        return data;
    }

    /** Validate the entire backup before committing anything. Only AURA data is accepted. */
    public static synchronized void importData(Context context, String text) throws JSONException {
        if (text.length() > 1_048_576) throw new IllegalArgumentException("Backup is too large (maximum 1 MB).");
        JSONObject data = new JSONObject(text);
        if (!"aura-local-backup".equals(data.optString("format")) || data.optInt("version") != 1) throw new IllegalArgumentException("Choose an AURA version 1 JSON backup.");
        JSONObject newSettings = strictSettings(data.getJSONObject("settings"));
        JSONArray incomingProfiles = data.getJSONArray("profiles");
        JSONArray incomingSessions = data.getJSONArray("sessions");
        if (incomingProfiles.length() > MAX_PROFILES || incomingSessions.length() > MAX_SESSIONS) throw new IllegalArgumentException("Backup exceeds the profile or session limit.");
        JSONArray newProfiles = new JSONArray();
        JSONArray newSessions = new JSONArray();
        Set<String> profileIds = new HashSet<>();
        Set<String> sessionIds = new HashSet<>();
        for (int i = 0; i < incomingProfiles.length(); i++) {
            JSONObject incoming = incomingProfiles.getJSONObject(i);
            String id = validId(incoming.getString("id"), profileIds);
            String name = incoming.getString("name").trim();
            if (name.isEmpty() || name.length() > 40) throw new IllegalArgumentException("Backup contains an invalid profile name.");
            JSONObject profile = new JSONObject();
            put(profile, "id", id); put(profile, "name", name);
            put(profile, "settings", strictSettings(incoming.getJSONObject("settings")));
            newProfiles.put(profile);
        }
        for (int i = 0; i < incomingSessions.length(); i++) {
            JSONObject incoming = incomingSessions.getJSONObject(i);
            JSONObject session = new JSONObject();
            String mode = incoming.getString("mode");
            long started = incoming.getLong("startedAt"), duration = incoming.getLong("durationSeconds");
            String rating = incoming.isNull("rating") ? null : incoming.getString("rating");
            String note = incoming.optString("note", "");
            if (!isMode(mode) || started < 0 || started > 8_640_000_000_000_000L || duration < 1 || duration > 604800 || note.length() > 300 || (rating != null && !validRating(rating))) throw new IllegalArgumentException("Backup contains invalid session data.");
            put(session, "id", validId(incoming.getString("id"), sessionIds));
            put(session, "mode", mode);
            if (incoming.has("settings")) put(session, "settings", strictSettings(incoming.getJSONObject("settings")));
            put(session, "startedAt", started); put(session, "durationSeconds", duration);
            put(session, "rating", rating == null ? JSONObject.NULL : rating); put(session, "note", note);
            put(session, "suggestionApplied", incoming.optBoolean("suggestionApplied", false));
            newSessions.put(session);
        }
        prefs(context).edit().putString("settings", newSettings.toString()).putString("profiles", newProfiles.toString()).putString("sessions", newSessions.toString()).apply();
    }

    private static JSONObject strictSettings(JSONObject source) throws JSONException {
        if (!isMode(source.getString("mode"))) throw new IllegalArgumentException("Invalid listening mode in backup.");
        if (source.has("context") && !validContext(source.getString("context"))) throw new IllegalArgumentException("Invalid listening context in backup.");
        if (source.has("profileName") && source.getString("profileName").length() > 40) throw new IllegalArgumentException("Invalid profile label in backup.");
        for (String key : new String[]{"clarity", "reduction", "awareness", "volume"}) {
            Object raw = source.get(key);
            if (!(raw instanceof Number)) throw new IllegalArgumentException("Invalid control value in backup.");
            double n = ((Number) raw).doubleValue();
            if (!Double.isFinite(n) || n < 0 || n > 100) throw new IllegalArgumentException("Backup control values must be from 0 to 100.");
        }
        return sanitizeSettings(source);
    }

    private static String validId(String id, Set<String> seen) {
        if (id.isEmpty() || id.length() > 80 || !seen.add(id)) throw new IllegalArgumentException("Backup contains invalid or duplicate IDs.");
        return id;
    }

    public static void put(JSONObject object, String key, Object value) {
        try { object.put(key, value); } catch (JSONException e) { throw new IllegalArgumentException("Invalid data value.", e); }
    }
}
