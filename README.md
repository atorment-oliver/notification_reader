# Lector de notificaciones bancarias

App Android para detectar notificaciones de **Al toque**, **Yape** o **BCP Bolivia**, extraer el nombre y monto, y enviarlos a un servidor.

## Compilación

Cada push a `main` compila el APK debug y ejecuta las pruebas del parser. Descarga el artefacto desde **Actions → Android APK → Artifacts**.

## Instalación

1. Instala el APK en un teléfono o emulador Android.
2. Selecciona Al toque, Yape o BCP Bolivia y el método de envío.
3. Completa los campos de la opción elegida y pulsa **Guardar configuración**.
4. Pulsa **Activar acceso a notificaciones** y habilita el lector en Ajustes de Android.
5. Para mejorar la continuidad, excluye la app de optimización de batería si el fabricante ofrece esa opción.

Android o el fabricante pueden pausar procesos por ahorro de batería; ninguna app estándar puede garantizar que nunca se detenga. Si se revoca el acceso, el lector deja de recibir notificaciones.

## Método: API HTTPS o API local del emulador

Configura URL, usuario y contraseña. Para la API local del emulador usa `http://10.0.2.2:5098/api/credits`; el permiso HTTP queda limitado a esa IP y puerto. En servidores remotos usa HTTPS. El cuerpo JSON es `{"user":"...","application":"ALTOQUE","amount":1.00}` y las credenciales se envían como HTTP Basic Auth. Se envía `Idempotency-Key`; el servidor debe respetarla para evitar duplicados si la respuesta se pierde.

## Método: SignalR

Configura:

- URL HTTPS completa del Hub, incluida su ruta (por ejemplo, `https://servidor.com/hubs/pagos`).
- Nombre del método del Hub. El valor inicial es `RegistrarAvisoPago`, editable.
- Autenticación del Hub: usuario/contraseña Basic, token Bearer o ninguna.

El lector invoca el método con **dos argumentos**, en este orden: `user` (nombre detectado) y `amount` (monto numérico). El servidor debe exponer una firma compatible, por ejemplo `RegistrarAvisoPago(string user, decimal amount)`. La app espera la confirmación del Hub y hace hasta tres intentos si falla. También manda `Idempotency-Key` como encabezado de conexión; si el servidor quiere impedir duplicados tras un corte ambiguo, debe leerlo y guardar las claves ya procesadas.

El cliente Java de SignalR usa WebSockets; el Hub y cualquier proxy deben admitir WebSockets. Con Basic, el servidor debe aceptar `Authorization: Basic` durante la conexión; con Bearer, debe validar el token. Contraseñas y tokens quedan cifrados en Android Keystore. No se registra el texto de las notificaciones en logs.

## Patrones iniciales

- Al toque: `JOHN O te abonó Bs. 1.00` (título: `Recibiste un pago`)\n- Yape: `QR DE JOHN OLIV te envió Bs. 1.00`
- BCP: `Recibiste un pago QR por Bs. 1950.00 de ANEZ MERLIN RANDY`

Los textos o etiquetas de las apps bancarias pueden cambiar; los mensajes que no coincidan se ignoran. El manifiesto consulta etiquetas de apps instaladas para reconocer Al toque/Yape/BCP; publicar en Google Play requeriría justificar `QUERY_ALL_PACKAGES` según sus políticas.
