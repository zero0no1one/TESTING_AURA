package org.aura.listen.cloud;

import java.util.Base64;
import java.nio.charset.StandardCharsets;

/** Only hosted Supabase projects and public client keys can be configured. */
public final class CloudConfig {
    private CloudConfig() {}
    public static String validateUrl(String value) {
        String url = value == null ? "" : value.trim();
        if (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        if (!url.matches("https://[a-z0-9]{20}\\.supabase\\.co"))
            throw new IllegalArgumentException("Use your HTTPS project URL: https://PROJECTREF.supabase.co");
        return url;
    }
    public static String validateKey(String value) {
        String key = value == null ? "" : value.trim();
        if (key.matches("sb_publishable_[A-Za-z0-9_-]{16,256}")) return key;
        // Legacy anon JWTs are public client keys. Never accept a service_role JWT.
        if (key.length() <= 4096 && key.matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+")) {
            try {
                String claims = new String(Base64.getUrlDecoder().decode(key.split("\\.")[1]), StandardCharsets.UTF_8);
                if (claims.matches("(?s).*\"role\"\\s*:\\s*\"anon\".*") && !claims.contains("service_role")) return key;
            } catch (IllegalArgumentException ignored) { }
        }
        throw new IllegalArgumentException("Use a publishable or legacy anon key. Secret and service-role keys are never allowed.");
    }
}
