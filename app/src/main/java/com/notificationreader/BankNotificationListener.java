package com.notificationreader;

import android.app.Notification;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.os.Bundle;
import android.util.Base64;
import com.microsoft.signalr.HubConnection;
import com.microsoft.signalr.HubConnectionBuilder;
import com.microsoft.signalr.TransportEnum;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BankNotificationListener extends NotificationListenerService {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private ConfigStore store;
    @Override public void onCreate() { super.onCreate(); store = new ConfigStore(this); }
    @Override public void onListenerConnected() { super.onListenerConnected(); store.status("Escuchando notificaciones seleccionadas."); store.debugLog("Servicio lector conectado a Android."); }
    @Override public void onListenerDisconnected() { super.onListenerDisconnected(); store.status("Android desconectó el lector. Revisa el acceso a notificaciones."); store.debugLog("Android desconectó el servicio lector; se solicita reconexión."); requestRebind(new android.content.ComponentName(this, BankNotificationListener.class)); }

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || store == null || getPackageName().equals(sbn.getPackageName())) return;
        String bank = store.bank();
        String appLabel = appLabel(sbn.getPackageName());
        store.debugLog("Notificación recibida; app=" + appLabel + "; paquete=" + sbn.getPackageName() + "; banca configurada=" + bank);
        if (!matchesBank(bank, appLabel, sbn.getPackageName())) { store.debugLog("Ignorada: la notificación del paquete " + sbn.getPackageName() + " no corresponde a la app bancaria seleccionada."); return; }
        Notification n = sbn.getNotification();
        Bundle extras = n == null ? null : n.extras;
        if (extras == null) { store.debugLog("Coincide con la banca, pero no contiene texto legible."); return; }
        String title = text(extras.getCharSequence(Notification.EXTRA_TITLE));
        String body = text(extras.getCharSequence(Notification.EXTRA_BIG_TEXT));
        if (body.isEmpty()) body = text(extras.getCharSequence(Notification.EXTRA_TEXT));
        NotificationParser.Payment payment = NotificationParser.parse(bank, title, body);
        if (payment == null) { store.debugLog("Notificación de la banca recibida, pero el formato no coincide con un pago reconocido."); return; }
        store.debugLog("Pago reconocido; usuario y monto extraídos (valores omitidos del registro por privacidad).");
        String key = sbn.getPackageName() + ":" + sbn.getId() + ":" + String.valueOf(sbn.getTag()) + ":" + sbn.getPostTime();
        String deliveryMode = store.mode();
        final String sender = payment.user, stableKey = key;
        final java.math.BigDecimal amount = payment.amount;
        if ("SIGNALR".equals(deliveryMode)) {
            String hubUrl = store.hubUrl(), method = store.hubMethod(), auth = store.hubAuth();
            String hubUser = store.hubUser(), hubSecret = store.hubSecret();
            if (hubUrl.isEmpty() || method.isEmpty() || ("BASIC".equals(auth) && (hubUser.isEmpty() || hubSecret.isEmpty())) || ("BEARER".equals(auth) && hubSecret.isEmpty())) {
                store.status("Pago detectado, pero falta completar la configuración SignalR."); store.debugLog("Envío detenido: falta configuración SignalR."); return;
            }
            if (!rememberIfNew(key)) { store.debugLog("Evento duplicado; no se vuelve a enviar."); return; }
            store.status("Pago detectado; conectando con SignalR…");
            store.debugLog("Envío iniciado por SignalR; método=" + method + ".");
            executor.execute(() -> sendSignalRWithRetry(hubUrl, method, auth, hubUser, hubSecret, sender, amount, key));
        } else {
            String endpoint = store.apiUrl(), apiUser = store.apiUser(), apiPassword = store.apiPassword();
            if (endpoint.isEmpty() || apiUser.isEmpty() || apiPassword.isEmpty()) { store.status("Pago detectado, pero falta completar la configuración de la API."); store.debugLog("Envío detenido: falta URL, usuario o contraseña de la API."); return; }
            if (!rememberIfNew(key)) { store.debugLog("Evento duplicado; no se vuelve a enviar."); return; }
            store.status("Pago detectado; enviando a la API…");
            store.debugLog("Envío iniciado a la API; destino=" + endpoint + ".");
            executor.execute(() -> sendOnce(endpoint, apiUser, apiPassword, sender, bank, amount, stableKey));
        }
    }

    private String appLabel(String pkg) {
        try { return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg, 0)).toString(); }
        catch (Exception e) { return pkg; }
    }
    private boolean matchesBank(String selected, String label, String pkg) {
        String haystack = (label + " " + pkg).toLowerCase(java.util.Locale.ROOT);
        if ("BCP".equals(selected)) return haystack.contains("bcp");
        if ("ALTOKE".equals(selected)) return haystack.contains("altoke") || haystack.contains("al toque");
        return haystack.contains("yape");
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

    private void sendOnce(String endpoint, String username, String password, String sender, String application, java.math.BigDecimal amount, String key) {
        java.net.HttpURLConnection connection = null;
        try {
            connection = (java.net.HttpURLConnection) new URL(endpoint).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(60000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            String token = Base64.encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
            connection.setRequestProperty("Authorization", "Basic " + token);
            String idempotencyKey = sha256(key);
            connection.setRequestProperty("Idempotency-Key", idempotencyKey);

            JSONObject json = new JSONObject();
            json.put("user", sender);
            json.put("application", application);
            json.put("amount", amount);
            byte[] data = json.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = connection.getOutputStream()) { out.write(data); }

            int code = connection.getResponseCode();
            String responseBody = readResponseBody(connection, code);
            if (code == 200) {
                String transactionId = "";
                try { transactionId = new JSONObject(responseBody).optString("transactionId", ""); }
                catch (Exception ignored) { }
                store.status("Pago enviado correctamente (HTTP 200).");
                store.debugLog("API confirmó HTTP 200" + (transactionId.isEmpty() ? "" : "; transacción=" + transactionId) + ".");
                return;
            }

            store.status("La API no confirmó el pago (HTTP " + code + "). No se reintentará automáticamente; revisa el log de la API.");
            store.debugLog("API respondió HTTP " + code + (responseBody.isEmpty() ? "" : "; detalle=" + responseBody) + "; clave de idempotencia=" + idempotencyKey + ". No se reintentará.");
        } catch (Exception e) {
            store.status("No se pudo confirmar el envío a la API. No se reintentará automáticamente; revisa la conexión y el log de la API.");
            store.debugLog("Error de conexión con API en intento único; " + e.getClass().getSimpleName() + ". No se reintentará.");
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private String readResponseBody(java.net.HttpURLConnection connection, int code) {
        java.io.InputStream stream = null;
        try {
            stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
            if (stream == null) return "";
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(stream, StandardCharsets.UTF_8))) {
                StringBuilder body = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) body.append(line).append(' ');
                return body.toString().trim();
            }
        } catch (Exception ignored) {
            return "";
        }
    }

    private void sendSignalRWithRetry(String endpoint, String method, String auth, String username, String secret, String sender, java.math.BigDecimal amount, String key) {
        for (int attempt = 0; attempt < 3; attempt++) {
            HubConnection hub = null;
            try {
                com.microsoft.signalr.HttpHubConnectionBuilder builder = HubConnectionBuilder.create(endpoint)
                        .withTransport(TransportEnum.WEBSOCKETS)
                        .withHeader("Idempotency-Key", sha256(key));
                if ("BASIC".equals(auth)) {
                    String credentials = Base64.encodeToString((username + ":" + secret).getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
                    builder.withHeader("Authorization", "Basic " + credentials);
                } else if ("BEARER".equals(auth)) {
                    builder.withAccessTokenProvider(io.reactivex.rxjava3.core.Single.defer(() -> io.reactivex.rxjava3.core.Single.just(secret)));
                }
                hub = builder.build();
                hub.start().blockingAwait();
                // Contract: the Hub method accepts (string user, decimal amount), in that order.
                hub.invoke(method, new Object[]{sender, amount}).blockingAwait();
                store.status("Pago enviado correctamente por SignalR.");
                store.debugLog("SignalR confirmó la invocación del método.");
                return;
            } catch (Exception e) {
                store.status("SignalR no confirmó el pago. Se reintentará.");
                store.debugLog("Error SignalR; intento " + (attempt + 1) + ": " + e.getClass().getSimpleName() + ".");
            } finally {
                if (hub != null) try { hub.close(); } catch (Exception ignored) {}
            }
            if (attempt < 2) try { Thread.sleep(1000L << attempt); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
        }
        store.status("SignalR no pudo enviar después de 3 intentos. Revisa la URL, método y autenticación del Hub.");
        store.debugLog("SignalR sin confirmación después de 3 intentos.");
    }
    private String sha256(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder(); for (byte b : digest) out.append(String.format(java.util.Locale.ROOT, "%02x", b)); return out.toString();
    }
}
