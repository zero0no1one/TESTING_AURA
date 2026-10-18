package org.aura.listen;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.view.ViewGroup;
import android.webkit.WebView;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.junit.Test;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Tests the actual Android WebView and bridge, without pretending an emulator has headphones. */
public class NativeShellTest {
    @Test public void installedAppHasNoNetworkPermission() throws Exception {
        android.content.Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), PackageManager.GET_PERMISSIONS);
        assertFalse(Arrays.asList(info.requestedPermissions).contains("android.permission.INTERNET"));
        assertTrue(Arrays.asList(info.requestedPermissions).contains("android.permission.RECORD_AUDIO"));
    }

    @Test public void nativeUiLoadsAndBridgeReturnsRealDeviceState() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Intent intent = new Intent(instrumentation.getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Activity activity = instrumentation.startActivitySync(intent);
        AtomicReference<WebView> reference = new AtomicReference<>();
        instrumentation.runOnMainSync(() -> reference.set(activity.findViewById(android.R.id.content).findViewWithTag("aura-web")));
        WebView web = reference.get();
        try {
            String ready = "";
            for (int i = 0; i < 40; i++) {
                ready = evaluate(web, "Boolean(document.getElementById('listen-toggle') && window.Aura && document.readyState === 'complete')");
                if ("true".equals(ready)) break;
                Thread.sleep(250);
            }
            assertEquals("Bundled app page did not finish loading", "true", ready);
            String wrapped = evaluate(web, "Aura.state()");
            String json = new org.json.JSONArray("[" + wrapped + "]").getString(0);
            JSONObject state = new JSONObject(json);
            assertTrue(state.has("settings"));
            assertTrue(state.has("profiles"));
            assertTrue(state.getJSONObject("device").getInt("sdk") >= 26);
            assertEquals("idle", state.getJSONObject("engine").getString("status"));
            instrumentation.runOnMainSync(() -> {
                assertTrue(web.getSettings().getBlockNetworkLoads());
                assertFalse(web.getSettings().getAllowFileAccess());
                assertFalse(web.getSettings().getAllowUniversalAccessFromFileURLs());
            });
        } finally { instrumentation.runOnMainSync(activity::finish); }
    }

    private String evaluate(WebView web, String script) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> result = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> web.evaluateJavascript(script, value -> { result.set(value); latch.countDown(); }));
        assertTrue("JavaScript callback timed out", latch.await(20, TimeUnit.SECONDS));
        return result.get();
    }
}
