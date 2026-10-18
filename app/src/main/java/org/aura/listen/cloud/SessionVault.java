package org.aura.listen.cloud;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Refresh/access tokens are encrypted with a non-exportable Android Keystore key. */
final class SessionVault {
    private static final String ALIAS = "aura.cloud.session.v1";
    private final Context context;
    SessionVault(Context context) { this.context = context; }
    private SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (!store.containsAlias(ALIAS)) {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            generator.generateKey();
        }
        return (SecretKey) store.getKey(ALIAS, null);
    }
    String read() throws Exception {
        String saved = context.getSharedPreferences("aura.cloud.tokens", Context.MODE_PRIVATE).getString("session", "");
        if (saved.isEmpty()) return "{}";
        String[] parts = saved.split(":", -1);
        if (parts.length != 2) throw new IllegalStateException("Invalid encrypted session.");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)));
        cipher.updateAAD(ALIAS.getBytes(StandardCharsets.UTF_8));
        return new String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), StandardCharsets.UTF_8);
    }
    void write(String session) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key());
        cipher.updateAAD(ALIAS.getBytes(StandardCharsets.UTF_8));
        String encrypted = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + ":" +
            Base64.encodeToString(cipher.doFinal(session.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
        if (!context.getSharedPreferences("aura.cloud.tokens", Context.MODE_PRIVATE).edit().putString("session", encrypted).commit())
            throw new IllegalStateException("Could not save the encrypted session.");
    }
    void clear() {
        if (!context.getSharedPreferences("aura.cloud.tokens", Context.MODE_PRIVATE).edit().clear().commit())
            throw new IllegalStateException("Could not clear sign-in tokens. Clear AURA storage in Android settings before sharing this phone.");
    }
}
