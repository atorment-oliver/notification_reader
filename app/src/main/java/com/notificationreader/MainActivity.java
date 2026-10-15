package com.notificationreader;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.net.URI;

public class MainActivity extends Activity {
    private ConfigStore store;
    private Spinner bank, mode, hubAuth;
    private EditText apiUrl, apiUser, apiPassword, hubUrl, hubMethod, hubUser, hubSecret;
    private LinearLayout apiGroup, signalRGroup, hubUserRow, hubSecretRow;
    private TextView status;
    private String loadedHubAuth = "BASIC";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state); store = new ConfigStore(this);
        buildUi(); load();
        mode.setOnItemSelectedListener(new SimpleSelection(() -> updateModeFields()));
        hubAuth.setOnItemSelectedListener(new SimpleSelection(() -> updateAuthFields()));
        updateModeFields(); updateAuthFields(); refreshStatus();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(24), dp(28), dp(24), dp(28));
        scroll.addView(root); setContentView(scroll);
        TextView title = new TextView(this); title.setText("Lector bancario"); title.setTextSize(26); title.setGravity(Gravity.CENTER_VERTICAL); root.addView(title);
        TextView intro = new TextView(this); intro.setText("Selecciona la banca y el medio por el que se enviarán los pagos detectados."); intro.setTextSize(15); intro.setPadding(0,dp(8),0,dp(18)); root.addView(intro);

        label(root, "Aplicación bancaria");
        bank = spinner(new String[]{"Yape", "BCP Bolivia"}); root.addView(bank);
        label(root, "Método de envío");
        mode = spinner(new String[]{"API HTTPS (POST)", "SignalR"}); root.addView(mode);

        apiGroup = group(root);
        label(apiGroup, "URL de la API (HTTPS)"); apiUrl = field("https://api.ejemplo.com/pagos", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI); apiGroup.addView(apiUrl);
        label(apiGroup, "Usuario de la API"); apiUser = field("", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD); apiGroup.addView(apiUser);
        label(apiGroup, "Contraseña de la API"); apiPassword = field("Contraseña", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD); apiGroup.addView(apiPassword);

        signalRGroup = group(root);
        label(signalRGroup, "URL HTTPS del Hub SignalR"); hubUrl = field("https://servidor.com/hubs/pagos", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI); signalRGroup.addView(hubUrl);
        label(signalRGroup, "Método del Hub que recibirá el pago"); hubMethod = field("RegistrarAvisoPago", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD); signalRGroup.addView(hubMethod);
        label(signalRGroup, "Autenticación del Hub");
        hubAuth = spinner(new String[]{"Usuario y contraseña (Basic)", "Token Bearer", "Sin autenticación"}); signalRGroup.addView(hubAuth);
        hubUserRow = new LinearLayout(this); hubUserRow.setOrientation(LinearLayout.VERTICAL); signalRGroup.addView(hubUserRow);
        label(hubUserRow, "Usuario SignalR"); hubUser = field("Usuario", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD); hubUserRow.addView(hubUser);
        hubSecretRow = new LinearLayout(this); hubSecretRow.setOrientation(LinearLayout.VERTICAL); signalRGroup.addView(hubSecretRow);
        label(hubSecretRow, "Contraseña o token"); hubSecret = field("Guardado localmente", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD); hubSecretRow.addView(hubSecret);
        TextView contract = new TextView(this); contract.setText("La llamada enviará dos argumentos al método del Hub, en este orden: nombre recibido y monto numérico. El servidor debe exponer un método compatible, por ejemplo RegistrarAvisoPago(string user, decimal amount)."); contract.setTextSize(13); contract.setPadding(0,dp(10),0,dp(4)); signalRGroup.addView(contract);

        Button save = new Button(this); save.setText("Guardar configuración"); root.addView(save); save.setOnClickListener(v -> save());
        Button access = new Button(this); access.setText("Activar acceso a notificaciones"); root.addView(access);
        access.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        status = new TextView(this); status.setTextSize(14); status.setPadding(0, dp(18), 0, dp(8)); root.addView(status);
        TextView note = new TextView(this); note.setText("Mantén habilitado el acceso a notificaciones. Android y algunos fabricantes pueden pausar la app por ahorro de batería; exclúyela de esas restricciones para mejorar la continuidad."); note.setTextSize(13); root.addView(note);
    }

    private LinearLayout group(LinearLayout parent) { LinearLayout g = new LinearLayout(this); g.setOrientation(LinearLayout.VERTICAL); parent.addView(g); return g; }
    private Spinner spinner(String[] values) { Spinner s = new Spinner(this); s.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, values)); return s; }
    private EditText field(String hint, int type) { EditText e = new EditText(this); e.setSingleLine(true); e.setHint(hint); e.setInputType(type); return e; }
    private void label(LinearLayout root, String s) { TextView t = new TextView(this); t.setText(s); t.setTextSize(15); t.setPadding(0,dp(14),0,dp(2)); root.addView(t); }
    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }

    private void load() {
        bank.setSelection("BCP".equals(store.bank()) ? 1 : 0);
        mode.setSelection("SIGNALR".equals(store.mode()) ? 1 : 0);
        apiUrl.setText(store.apiUrl()); apiUser.setText(store.apiUser());
        apiPassword.setHint(store.apiPassword().isEmpty() ? "Contraseña" : "Guardada; escribe solo para cambiarla");
        hubUrl.setText(store.hubUrl()); hubMethod.setText(store.hubMethod()); hubUser.setText(store.hubUser());
        loadedHubAuth = store.hubAuth();
        hubAuth.setSelection("BEARER".equals(loadedHubAuth) ? 1 : "NONE".equals(loadedHubAuth) ? 2 : 0);
        hubSecret.setHint(store.hubSecret().isEmpty() ? "Contraseña o token" : "Guardado; escribe solo para cambiarlo");
    }
    private void updateModeFields() {
        boolean useSignalR = mode != null && mode.getSelectedItemPosition() == 1;
        if (apiGroup != null) apiGroup.setVisibility(useSignalR ? View.GONE : View.VISIBLE);
        if (signalRGroup != null) signalRGroup.setVisibility(useSignalR ? View.VISIBLE : View.GONE);
    }
    private void updateAuthFields() {
        int auth = hubAuth == null ? 0 : hubAuth.getSelectedItemPosition();
        if (hubUserRow != null) hubUserRow.setVisibility(auth == 0 ? View.VISIBLE : View.GONE);
        if (hubSecretRow != null) hubSecretRow.setVisibility(auth == 2 ? View.GONE : View.VISIBLE);
    }
    private String bankValue() { return bank.getSelectedItemPosition() == 1 ? "BCP" : "YAPE"; }
    private boolean httpsUrl(String value) {
        try { URI u = URI.create(value); return "https".equalsIgnoreCase(u.getScheme()) && u.getHost() != null; }
        catch (Exception e) { return false; }
    }
    private void save() {
        try {
            if (mode.getSelectedItemPosition() == 0) {
                String endpoint = apiUrl.getText().toString().trim();
                String username = apiUser.getText().toString().trim();
                String password = apiPassword.getText().toString(); if (password.isEmpty()) password = store.apiPassword();
                if (!httpsUrl(endpoint) || username.isEmpty() || password.isEmpty()) throw new IllegalArgumentException("Completa la URL HTTPS, usuario y contraseña de la API.");
                store.saveApi(bankValue(), endpoint, username, password);
                apiPassword.setText(""); apiPassword.setHint("Guardada; escribe solo para cambiarla");
            } else {
                String endpoint = hubUrl.getText().toString().trim();
                String method = hubMethod.getText().toString().trim();
                if (!httpsUrl(endpoint)) throw new IllegalArgumentException("La URL del Hub debe comenzar con https:// y contener la ruta del Hub.");
                if (!method.matches("[A-Za-z_][A-Za-z0-9_]*")) throw new IllegalArgumentException("El método del Hub no es válido.");
                int authIndex = hubAuth.getSelectedItemPosition();
                String auth = authIndex == 1 ? "BEARER" : authIndex == 2 ? "NONE" : "BASIC";
                String username = hubUser.getText().toString().trim();
                String secret = hubSecret.getText().toString();
                if (secret.isEmpty() && auth.equals(loadedHubAuth)) secret = store.hubSecret();
                if ("BASIC".equals(auth) && (username.isEmpty() || secret.isEmpty())) throw new IllegalArgumentException("Completa el usuario y contraseña Basic del Hub.");
                if ("BEARER".equals(auth) && secret.isEmpty()) throw new IllegalArgumentException("Ingresa el token Bearer del Hub.");
                store.saveSignalR(bankValue(), endpoint, method, auth, username, secret);
                loadedHubAuth = auth; hubSecret.setText(""); hubSecret.setHint(secret.isEmpty() ? "No se requiere para esta opción" : "Guardado; escribe solo para cambiarlo");
            }
            Toast.makeText(this, "Configuración guardada", Toast.LENGTH_SHORT).show(); refreshStatus();
        } catch (IllegalArgumentException e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo guardar la configuración", Toast.LENGTH_LONG).show();
        }
    }
    private void refreshStatus() {
        boolean enabled = false;
        try {
            String listeners = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
            enabled = listeners != null && listeners.contains(getPackageName());
        } catch (Exception ignored) {}
        status.setText((enabled ? "Acceso a notificaciones: ACTIVADO\n" : "Acceso a notificaciones: DESACTIVADO\n") + store.status());
    }
    @Override protected void onResume() { super.onResume(); if (status != null) refreshStatus(); }

    private static class SimpleSelection implements AdapterView.OnItemSelectedListener {
        private final Runnable onSelect;
        SimpleSelection(Runnable onSelect) { this.onSelect = onSelect; }
        @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { onSelect.run(); }
        @Override public void onNothingSelected(AdapterView<?> parent) {}
    }
}
