package com.notificationreader;

import android.app.Notification;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.os.Bundle;
import android.util.Base64;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.HttpsURLConnection;

public class BankNotificationListener extends NotificationListenerService {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private ConfigStore store;
    @Override public void onCreate() { super.onCreate(); store = new ConfigStore(this); }
    @Override public void onListenerConnected() { super.onListenerConnected(); store.status("Escuchando notificaciones seleccionadas."); }
    @Override public void onListenerDisconnected() { super.onListenerDisconnected(); store.status("Android desconectó el lector. Revisa el acceso a notificaciones."); requestRebind(new android.content.ComponentName(this, BankNotificationListener.class)); }

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || store == null || getPackageName().equals(sbn.getPackageName())) return;
        String bank = store.bank();
        String appLabel = appLabel(sbn.getPackageName());
        if (!matchesBank(bank, appLabel, sbn.getPackageName())) return;
        Notification n = sbn.getNotification();
        Bundle extras = n == null ? null : n.extras;
        if (extras == null) return;
        String title = text(extras.getCharSequence(Notification.EXTRA_TITLE));
        String body = text(extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
        if (body.isEmpty()) body = text(extras.getCharSequence(Notification.EXTRA_TEXT));
        NotificationParser.Payment payment = NotificationParser.parse(bank, title, body);
        if (payment == null) return;
        String key = sbn.getPackageName() + ":" + sbn.getId() + ":" + String.valueOf(sbn.getTag()) + ":" + sbn.getPostTime();
        if (!rememberIfNew(key)) return;
        String endpoint = store.url(), apiUser = store.user(), apiPassword = store.password();
        if (endpoint.isEmpty() || apiUser.isEmpty() || apiPassword.isEmpty()) { store.status("Pago detectado, pero falta completar la configuración de la API."); return; }
        store.status("Pago detectado; enviando a la API…");
        final String sender = payment.user, stableKey = key;
        final java.math.BigDecimal amount = payment.amount;
        executor.execute(() -> sendWithRetry(endpoint, apiUser, apiPassword, sender, amount, stableKey));
    }

    private String appLabel(String pkg) {
        try { return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0)).toString(); }
        catch (Exception e) { return pkg; }
    }
    private boolean matchesBank(String selected, String label, String pkg) {
        String haystack = (label + " " + pkg).toLowerCase(java.util.Locale.ROOT);
        return "BCP".equals(selected) ? haystack.contains("bcp") : haystack.contains("yape");
    }
    private String text(CharSequence cs) { return cs == null ? "" : cs.toString().trim(); }

    private synchronized boolean rememberIfNew(String key) {
        android.content.SharedPreferences p = getSharedPreferences("bank_reader_events", MODE_PRIVATE);
        String old = p.getString("keys", "");
        String[] items = old.isEmpty() ? new String[0] : old.split("\\n");
        for (String item : items) if (item.equals(key)) return false;
        List<String> next = new ArrayList<>(); next.add(key);
        for (int i = 0; i < items.length && next.size() < 80; i++) next.add(items[i]);
        p.edit().putString("keys", android.text.TextUtils.join("\n", next)).apply();
        return true;
    }

    private void sendWithRetry(String endpoint, String username, String password, String sender, java.math.BigDecimal amount, String key) {
        for (int attempt = 0; attempt < 3; attempt++) {
            HttpsURLConnection connection = null;
            try {
                connection = (HttpsURLConnection) new URL(endpoint).openConnection();
                connection.setRequestMethod("POST"); connection.setConnectTimeout(12000); connection.setReadTimeout(12000); connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                String token = Base64.encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
                connection.setRequestProperty("Authorization", "Basic " + token);
                connection.setRequestProperty("Idempotency-Key", sha256(key));
                JSONObject json = new JSONObject(); json.put("user", sender); json.put("amount", amount);
                byte[] data = json.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream out = connection.getOutputStream()) { out.write(data); }
                int code = connection.getResponseCode();
                if (code >= 200 && code < 300) { store.status("Pago enviado correctamente (HTTP " + code + ")."); return; }
                store.status("La API respondió HTTP " + code + ". Se reintentará.");
            } catch (Exception e) {
                store.status("No se pudo contactar la API. Se reintentará.");
            } finally { if (connection != null) connection.disconnect(); }
            if (attempt < 2) try { Thread.sleep(1000L << attempt); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
        }
        store.status("No se pudo enviar después de 3 intentos. Una nueva notificación podrá reintentarlo.");
    }
    private String sha256(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder(); for (byte b : digest) out.append(String.format(java.util.Locale.ROOT, "%02x", b)); return out.toString();
    }
}
