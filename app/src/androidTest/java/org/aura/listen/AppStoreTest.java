package org.aura.listen;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/** Runs against real Android SharedPreferences in a separate test-only preference namespace. */
public class AppStoreTest {
    private Context context;

    @Before public void prepare() {
        context = new ContextWrapper(InstrumentationRegistry.getInstrumentation().getTargetContext()) {
            @Override public Context getApplicationContext() { return this; }
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return super.getSharedPreferences("instrumentation-test-" + name, mode);
            }
        };
        AppStore.reset(context);
    }
    @After public void clean() { AppStore.reset(context); }

    @Test public void defaultsAndClamping() throws Exception {
        assertEquals(25, AppStore.settings(context).getInt("volume"));
        JSONObject settings = AppStore.defaults().put("volume", 500).put("clarity", -20).put("mode", "unknown");
        AppStore.setSettings(context, settings);
        assertEquals(100, AppStore.settings(context).getInt("volume"));
        assertEquals(0, AppStore.settings(context).getInt("clarity"));
        assertEquals("care", AppStore.settings(context).getString("mode"));
    }

    @Test public void profileSaveLoadDeletePersists() throws Exception {
        AppStore.setSettings(context, AppStore.defaults().put("clarity", 73));
        AppStore.saveProfile(context, "Clinic");
        String id = AppStore.profiles(context).getJSONObject(0).getString("id");
        AppStore.setSettings(context, AppStore.defaults());
        AppStore.loadProfile(context, id);
        assertEquals(73, AppStore.settings(context).getInt("clarity"));
        AppStore.deleteProfile(context, id);
        assertEquals(0, AppStore.profiles(context).length());
    }

    @Test public void sessionRetentionAndFeedback() throws Exception {
        for (int i = 0; i < 205; i++) AppStore.recordSession(context, "care", 10000L + i, i + 1);
        JSONArray sessions = AppStore.sessions(context);
        assertEquals(200, sessions.length());
        assertEquals(10204, sessions.getJSONObject(0).getLong("startedAt"));
        String id = sessions.getJSONObject(0).getString("id");
        AppStore.feedback(context, id, "difficult", "Quiet room next time.");
        assertEquals("difficult", AppStore.sessions(context).getJSONObject(0).getString("rating"));
        AppStore.applySuggestion(context, id);
        assertEquals(65, AppStore.settings(context).getInt("clarity"));
        assertEquals(30, AppStore.settings(context).getInt("reduction"));
        assertEquals(25, AppStore.settings(context).getInt("volume"));
        try { AppStore.applySuggestion(context, id); fail("A suggestion must not apply twice."); }
        catch (IllegalArgumentException expected) { }
    }

    @Test public void validBackupRoundTrips() throws Exception {
        AppStore.saveProfile(context, "Home");
        AppStore.recordSession(context, "focus", 10000, 8);
        String id = AppStore.sessions(context).getJSONObject(0).getString("id");
        AppStore.feedback(context, id, "clear", "Helpful");
        String backup = AppStore.exportData(context).toString();
        AppStore.reset(context);
        AppStore.importData(context, backup);
        assertEquals(backup, AppStore.exportData(context).toString());
    }

    @Test public void invalidImportNeverPartiallyReplacesData() throws Exception {
        AppStore.saveProfile(context, "Keep this");
        String before = AppStore.exportData(context).toString();
        JSONObject bad = new JSONObject(before);
        bad.getJSONObject("settings").put("volume", 101);
        try { AppStore.importData(context, bad.toString()); fail("Invalid volume accepted."); }
        catch (IllegalArgumentException expected) { }
        assertEquals(before, AppStore.exportData(context).toString());
        bad = new JSONObject(before);
        bad.getJSONArray("profiles").put(bad.getJSONArray("profiles").getJSONObject(0));
        try { AppStore.importData(context, bad.toString()); fail("Duplicate ID accepted."); }
        catch (IllegalArgumentException expected) { }
        assertEquals(before, AppStore.exportData(context).toString());
        try { AppStore.importData(context, "{not json"); fail("Malformed JSON accepted."); }
        catch (org.json.JSONException expected) { }
        assertEquals(before, AppStore.exportData(context).toString());
    }

    @Test public void limitsAndClearHistory() throws Exception {
        for (int i = 0; i < 20; i++) AppStore.saveProfile(context, "Profile " + i);
        try { AppStore.saveProfile(context, "Too many"); fail("Profile limit ignored."); }
        catch (IllegalArgumentException expected) { }
        AppStore.recordSession(context, "commute", 10000, 1);
        AppStore.clearHistory(context);
        assertEquals(0, AppStore.sessions(context).length());
        assertEquals(20, AppStore.profiles(context).length());
        AppStore.completeOnboarding(context);
        AppStore.reset(context);
        assertFalse(AppStore.onboardingDone(context));
        assertEquals(0, AppStore.profiles(context).length());
    }
}
