# Publicar MiDinero en Google Play

Cada compilación de `main` publica en Releases los APK (para instalar a mano) y, si están los secretos de la clave
de subida, los **.aab** del móvil y del reloj (para subir a Play Console). Sin esos secretos `bundleRelease` falla a
propósito y el CI no genera los .aab: así nunca se sube a Play un paquete firmado con la clave de desarrollo.

## 1. Clave de subida propia (obligatorio)

La clave `app/midinero.keystore` es de desarrollo y su contraseña es pública: **no la uses en Play**.

1. Crea una clave: `keytool -genkeypair -v -keystore upload.jks -alias midinero -keyalg RSA -keysize 4096 -validity 10000`
2. Guárdala en los secretos del repositorio (Settings → Secrets and variables → Actions):
   `MIDINERO_KEYSTORE_BASE64` (`base64 -w0 upload.jks`), `MIDINERO_KEYSTORE_PASSWORD`, `MIDINERO_KEY_ALIAS`, `MIDINERO_KEY_PASSWORD`.
3. En Play Console deja activada la **firma de apps de Google Play** y sube el .aab firmado con esa clave.

La versión de Play se firma con la clave de Google, así que no se instala encima del APK de Releases:
antes de pasar a la de Play, haz una copia en *Ajustes → Copia de seguridad* y restáurala después.

## 2. App del reloj (Wear OS)

La app del reloj ya no va dentro de la del móvil (Play no permite instalar APK por fuera de la tienda). Se publica en
la misma ficha:

1. Play Console → *Configuración avanzada → Factores de forma* → añade **Wear OS**.
2. Sube `MiDinero-Reloj-4.9.x.aab` en el canal de Wear OS (o en el mismo canal, según lo que pida Play Console).
   Su `versionCode` empieza en 100000 para no chocar con el del móvil: Play exige números distintos en cada bundle.
3. Añade capturas del reloj (redondas, 384×384 como mínimo) y revisa los requisitos de calidad de Wear OS.
   Las capturas de `WatchScreenshotTest` sirven de base.

## 3. Ficha de la tienda

Textos, novedades, icono (512×512) y gráfico destacado (1024×500) en español e inglés en
`fastlane/metadata/android/`. Faltan las **capturas de pantalla** (mínimo 2 por idioma, del móvil):
Inicio, Movimientos, Estadísticas, un viaje y la bienvenida.

## 4. Política de privacidad

Play exige una URL pública y este repositorio es privado, así que `PRIVACY.md` se publica en el repositorio público
[midinero-legal](https://github.com/JMRMINGORANCE/midinero-legal) con GitHub Pages:

**https://jmrmingorance.github.io/midinero-legal/**

Esa es la URL de Play Console y la de `PRIVACY_URL` (`app/src/main/java/com/midinero/control/ui/screens/SettingsScreen.kt`),
que la app abre desde *Ajustes → Privacidad*. Si cambias `PRIVACY.md`, copia el cambio en `index.md` de midinero-legal.

## 5. Seguridad de los datos (Data safety)

- ¿Recoge o comparte datos? **No.** La app no tiene permiso de Internet; todo se guarda en el móvil.
- Cifrado en tránsito: no aplica (no envía datos). Eliminación: el usuario borra sus datos desde la app o desinstalándola.
- Los datos que solo se procesan en el móvil no cuentan como «recogidos», pero la app sí trata **información financiera**
  (compras, importes) y lee notificaciones: dilo así en la declaración de acceso a notificaciones y en la política.
- La copia de seguridad de Android (Google) incluye la base de datos: está explicado en la política de privacidad.

## 6. Declaraciones de permisos y contenido

- **Acceso a notificaciones** (`NotificationListenerService`): se usa solo para leer los pagos de las apps que el usuario elige.
  La app muestra un aviso destacado antes de pedirlo (paso 1 de la bienvenida). Explícalo igual en el formulario si Play lo pide,
  y graba un vídeo corto: bienvenida → «Aceptar y dar acceso» → pago detectado.
- Anuncios: **no**. Público objetivo: mayores de 18. Categoría: **Finanzas**.
- Cuestionario de apps financieras: es una app de **presupuesto y control de gastos personal**; no ofrece préstamos, inversión, pagos ni banca.
- Clasificación de contenido: rellena el cuestionario IARC (sin contenido sensible).

## 7. Pruebas

Las cuentas personales nuevas deben hacer una **prueba cerrada con 12 testers durante 14 días** antes de pasar a producción.
Empieza por *Pruebas internas* con el .aab, luego *Prueba cerrada*.

## 8. Subida automática a Play

Con una cuenta de servicio, cada compilación de `main` sube sola los dos .aab a Play (paso «Subir a Google Play» de
`build.yml`, script `.github/scripts/play_upload.py`): el del móvil al canal `alpha` y el del reloj al canal de Wear OS
«wear:Reloj - prueba cerrada», ya publicados para los testers y con las notas de `fastlane/metadata/android/*/changelogs/default.txt`.
Sin el secreto el paso no se ejecuta y hay que subirlos a mano como antes.

Se configura una sola vez:

1. [Google Cloud Console](https://console.cloud.google.com/) → crea un proyecto (p. ej. «MiDinero») →
   *APIs y servicios → Biblioteca* → activa **Google Play Android Developer API**.
2. *IAM y administración → Cuentas de servicio → Crear cuenta de servicio* (p. ej. `github-play`), sin roles.
   Ábrela → *Claves → Añadir clave → JSON*: se descarga un archivo .json.
3. Play Console → *Usuarios y permisos → Invitar a usuarios* → el correo de la cuenta de servicio
   (`github-play@….iam.gserviceaccount.com`) → *Permisos de la aplicación* → MiDinero → marca
   **Publicar en canales de prueba** (y «Ver información de la aplicación») → Invitar.
4. GitHub → *Settings → Secrets and variables → Actions → New repository secret* → nombre `PLAY_SERVICE_ACCOUNT_JSON`,
   valor: el contenido completo del .json. Después borra el .json de tu equipo.

Si los canales cambian de nombre, ponlos en *Variables* del repositorio: `PLAY_TRACK` (móvil) y `PLAY_WEAR_TRACK` (reloj).
Si un nombre no existe, el paso falla y muestra la lista de canales que hay.
