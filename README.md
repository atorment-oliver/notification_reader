# Lector de notificaciones bancarias

App Android para detectar notificaciones entrantes de **Yape** o **BCP Bolivia**, extraer nombre y monto, y hacer un `POST` a una API.

## Compilación

Al subir cambios a `main`, GitHub Actions compila el APK debug y ejecuta pruebas del parser. Descarga `app-debug.apk` desde **Actions → Android APK → Artifacts**. También se puede iniciar manualmente desde la pestaña Actions.

## Instalación y configuración

1. Instala el APK en Android.
2. Selecciona Yape o BCP Bolivia; configura la URL HTTPS, usuario y contraseña de API.
3. Pulsa Guardar configuración.
4. Activa el acceso a notificaciones en Ajustes de Android.
5. Para mejorar la continuidad, permite actividad en segundo plano y excluye la app de optimización de batería si el fabricante ofrece esa opción.

Android o el fabricante pueden pausar procesos por ahorro de batería; ninguna app estándar puede garantizar que nunca se detenga. Si se revoca el acceso a notificaciones, el lector deja de recibir eventos.

## Datos enviados

El parser se basa en los ejemplos facilitados:

- Yape: `QR DE JOHN OLIV te envió Bs. 1.00`
- BCP: `Recibiste un pago QR por Bs. 1950.00 de ANEZ MERLIN RANDY`

El cuerpo JSON es `{"user":"...","amount":1.00}`. Las credenciales se envían mediante HTTP Basic Auth, solo por HTTPS. La contraseña queda cifrada en el dispositivo con Android Keystore. No se registra el texto de las notificaciones en logs. Se envía `Idempotency-Key`; la API debe respetarla para evitar duplicados si la respuesta se pierde.

El manifiesto consulta nombres de aplicaciones instaladas para reconocer Yape/BCP. Para publicar en Google Play, el permiso `QUERY_ALL_PACKAGES` requiere justificar el uso según sus políticas. Los mensajes bancarios pueden cambiar; notificaciones no reconocidas se ignoran.
