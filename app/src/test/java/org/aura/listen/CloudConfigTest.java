package org.aura.listen;

import org.aura.listen.cloud.CloudConfig;
import org.junit.Test;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public class CloudConfigTest {
    @Test public void onlyHostedHttpsProjectOriginsAccepted() {
        assertEquals("https://cwnfvwezyqmwviixaqxr.supabase.co", CloudConfig.validateUrl(" https://cwnfvwezyqmwviixaqxr.supabase.co/ "));
        for (String input : new String[]{"http://cwnfvwezyqmwviixaqxr.supabase.co", "https://cwnfvwezyqmwviixaqxr.supabase.co.evil.test", "https://user@cwnfvwezyqmwviixaqxr.supabase.co", "https://127.0.0.1", "https://cwnfvwezyqmwviixaqxr.supabase.co/path", "https://cwnfvwezyqmwviixaqxr.supabase.co:443", "https://cwnfvwezyqmwviixaqxr.supabase.co?x=1", "file:///data/data"}) {
            try { CloudConfig.validateUrl(input); fail("Accepted untrusted URL: " + input); } catch (IllegalArgumentException expected) { }
        }
    }
    @Test public void privilegedAndMalformedKeysRejected() {
        String anon = jwt("anon"), service = jwt("service_role");
        assertEquals(anon, CloudConfig.validateKey(anon));
        assertEquals("sb_publishable_abcdefghijklmnop", CloudConfig.validateKey("sb_publishable_abcdefghijklmnop"));
        for (String input : new String[]{service, "sb_secret_abcdefghijklmnop", "unknown", "x.y.z", "sb_publishable_bad\nHeader: inject"}) {
            try { CloudConfig.validateKey(input); fail("Accepted invalid key"); } catch (IllegalArgumentException expected) { }
        }
    }
    private String jwt(String role) {
        return "header." + Base64.getUrlEncoder().withoutPadding().encodeToString(("{\"role\":\"" + role + "\"}").getBytes(StandardCharsets.UTF_8)) + ".signature";
    }
}
