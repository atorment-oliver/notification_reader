package com.notificationreader;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

/** Local settings; API and SignalR secrets are encrypted with Android Keystore. */
final class ConfigStore {
    static final String PREF = "bank_reader";
    static final String BANK = "bank";
    static final String MODE = "delivery_mode";
    static final String API_URL = "api_url";
    static final String API_USER = "api_user";
    static final String HUB_URL = "hub_url";
    static final String HUB_METHOD = "hub_method";
    static final String HUB_AUTH = "hub_auth";
    static final String HUB_USER = "hub_user";
    static final String STATUS = "status";
    private static final String API_PASSWORD = "api_password_encrypted";
    private static final String HUB_SECRET = "hub_secret_encrypted";
    private static final String ALIAS = "notification_reader_key_v1";
    private final SharedPreferences prefs;

    ConfigStore(Context c) { prefs = c.getSharedPreferences(PREF, Context.MODE_PRIVATE); }
    String bank() { return prefs.getString(BANK, "YAPE"); }
    String mode() { return prefs.getString(MODE, "API"); }
    String apiUrl() { return prefs.getString(API_URL, ""); }
    String apiUser() { return prefs.getString(API_USER, ""); }
    String apiPassword() { return decrypt(API_PASSWORD); }
    String hubUrl() { return prefs.getString(HUB_URL, ""); }
    String hubMethod() { return prefs.getString(HUB_METHOD, "RegistrarAvisoPago"); }
    String hubAuth() { return prefs.getString(HUB_AUTH, "BASIC"); }
    String hubUser() { return prefs.getString(HUB_USER, ""); }
    String hubSecret() { return decrypt(HUB_SECRET); }

    void saveApi(String bank, String url, String user, String password) throws Exception {
        prefs.edit().putString(BANK, bank).putString(MODE, "API").putString(API_URL, url).putString(API_USER, user).putString(API_PASSWORD, encrypt(password)).apply();
    }
    void saveSignalR(String bank, String url, String method, String auth, String user, String secret) throws Exception {
        prefs.edit().putString(BANK, bank).putString(MODE, "SIGNALR").putString(HUB_URL, url).putString(HUB_METHOD, method)
                .putString(HUB_AUTH, auth).putString(HUB_USER, user).putString(HUB_SECRET, encrypt(secret)).apply();
    }
    void status(String s) { prefs.edit().putString(STATUS, s).apply(); }
    String status() { return prefs.getString(STATUS, "Sin actividad todavía."); }

    private String encrypt(String plainText) throws Exception {
        if (plainText == null || plainText.isEmpty()) return "";
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getKey());
        byte[] iv = cipher.getIV();
        byte[] ciphertext = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
        ByteBuffer b = ByteBuffer.allocate(4 + iv.length + ciphertext.length).putInt(iv.length).put(iv).put(ciphertext);
        return Base64.encodeToString(b.array(), Base64.NO_WRAP);
    }
    private String decrypt(String preferenceKey) {
        try {
            String encoded = prefs.getString(preferenceKey, "");
            if (encoded.isEmpty()) return "";
            ByteBuffer b = ByteBuffer.wrap(Base64.decode(encoded, Base64.NO_WRAP));
            byte[] iv = new byte[b.getInt()]; b.get(iv);
            byte[] ciphertext = new byte[b.remaining()]; b.get(ciphertext);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getKey(), new javax.crypto.spec.GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) { return ""; }
    }
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
