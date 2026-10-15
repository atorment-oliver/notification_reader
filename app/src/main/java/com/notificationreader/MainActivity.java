package com.notificationreader;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.ComponentName;
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
    private Spinner bank;
    private EditText url, apiUser, apiPassword;
    private TextView status;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state); store = new ConfigStore(this);
        buildUi(); load(); refreshStatus();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(24), dp(28), dp(24), dp(28));
        scroll.addView(root); setContentView(scroll);
        TextView title = new TextView(this); title.setText("Lector bancario"); title.setTextSize(26); title.setGravity(Gravity.CENTER_VERTICAL); root.addView(title);
        TextView intro = new TextView(this); intro.setText("Selecciona qué notificaciones procesar y dónde enviar los pagos detectados."); intro.setTextSize(15); intro.setPadding(0,dp(8),0,dp(18)); root.addView(intro);
        label(root, "Aplicación bancaria");
        bank = new Spinner(this); bank.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"Yape", "BCP Bolivia"})); root.addView(bank);
        label(root, "URL de la API (HTTPS)"); url = field("https://api.ejemplo.com/pagos", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI); root.addView(url);
        label(root, "Usuario de la API"); apiUser = field("", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD); root.addView(apiUser);
        label(root, "Contraseña de la API"); apiPassword = field("", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD); root.addView(apiPassword);
        Button save = new Button(this); save.setText("Guardar configuración"); root.addView(save); save.setOnClickListener(v -> save());
        Button access = new Button(this); access.setText("Activar acceso a notificaciones"); root.addView(access);
        access.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        status = new TextView(this); status.setTextSize(14); status.setPadding(0, dp(18), 0, dp(8)); root.addView(status);
        TextView note = new TextView(this); note.setText("El acceso a notificaciones debe permanecer habilitado. Android y algunos fabricantes pueden pausar apps por ahorro de batería; excluye esta app de esas restricciones para mejorar la continuidad."); note.setTextSize(13); root.addView(note);
    }
    private EditText field(String hint, int type) { EditText e = new EditText(this); e.setSingleLine(true); e.setHint(hint); e.setInputType(type); return e; }
    private void label(LinearLayout root, String s) { TextView t = new TextView(this); t.setText(s); t.setTextSize(15); t.setPadding(0,dp(14),0,dp(2)); root.addView(t); }
    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
    private void load() {
        bank.setSelection("BCP".equals(store.bank()) ? 1 : 0); url.setText(store.url()); apiUser.setText(store.user());
        // Intentionally do not place the stored password in the password field.
        apiPassword.setHint(store.password().isEmpty() ? "Contraseña" : "Guardada; escribe solo para cambiarla");
    }
    private void save() {
        String endpoint = url.getText().toString().trim();
        try {
            URI parsed = URI.create(endpoint);
            if (!"https".equalsIgnoreCase(parsed.getScheme()) || parsed.getHost() == null) throw new IllegalArgumentException();
            String selectedBank = bank.getSelectedItemPosition() == 1 ? "BCP" : "YAPE";
            String pass = apiPassword.getText().toString();
            if (pass.isEmpty()) pass = store.password();
            store.save(selectedBank, endpoint, apiUser.getText().toString().trim(), pass);
            apiPassword.setText(""); apiPassword.setHint("Guardada; escribe solo para cambiarla");
            Toast.makeText(this, "Configuración guardada", Toast.LENGTH_SHORT).show(); refreshStatus();
        } catch (Exception e) {
            Toast.makeText(this, "Ingresa una URL HTTPS válida y revisa la configuración", Toast.LENGTH_LONG).show();
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
}
