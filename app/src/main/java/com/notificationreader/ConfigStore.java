package com.notificationreader;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.ByteBuffer;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

/** Local settings; API password is encrypted with an Android Keystore AES key. */
final class ConfigStore {
    static final String PREF = "bank_reader";
    static final String BANK = "bank";
    static final String URL = "api_url";
    static final String USER = "api_user";
    static final String STATUS = "status";
    private static final String PASSWORD = "api_password_encrypted";
    private static final String ALIAS = "notification_reader_key_v1";
    private final SharedPreferences prefs;

    ConfigStore(Context c) { prefs = c.getSharedPreferences(PREF, Context.MODE_PRIVATE); }
    String bank() { return prefs.getString(BANK, "YAPE"); }
    String url() { return prefs.getString(URL, ""); }
    String user() { return prefs.getString(USER, ""); }
    String password() {
        try {
            String encoded = prefs.getString(PASSWORD, "");
            if (encoded.isEmpty()) return "";
            byte[] all = Base64.decode(encoded, Base64.NO_WRAP);
            ByteBuffer b = ByteBuffer.wrap(all);
            byte[] iv = new byte[b.getInt()]; b.get(iv);
            byte[] ciphertext = new byte[b.remaining()]; b.get(ciphertext);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getKey(), new javax.crypto.spec.GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(ciphertext), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) { return ""; }
    }
    void save(String bank, String url, String user, String password) throws Exception {
        String encrypted = "";
        if (!password.isEmpty()) {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, getKey());
            byte[] iv = cipher.getIV();
            byte[] ciphertext = cipher.doFinal(password.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            ByteBuffer b = ByteBuffer.allocate(4 + iv.length + ciphertext.length).putInt(iv.length).put(iv).put(ciphertext);
            encrypted = Base64.encodeToString(b.array(), Base64.NO_WRAP);
        }
        prefs.edit().putString(BANK, bank).putString(URL, url).putString(USER, user).putString(PASSWORD, encrypted).apply();
    }
    void status(String s) { prefs.edit().putString(STATUS, s).apply(); }
    String status() { return prefs.getString(STATUS, "Sin actividad todavía."); }

    private SecretKey getKey() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
        java.security.Key existing = ks.getKey(ALIAS, null);
        if (existing instanceof SecretKey) return (SecretKey) existing;
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }
}
