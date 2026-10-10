# Política de privacidad de MiDinero

_Última actualización: 9 de octubre de 2026_

MiDinero es una app para controlar tus gastos. Está diseñada para que **tus datos no salgan de tu móvil** (salvo un resumen a tu propio reloj, si instalas su app).

## Qué datos trata

- **Movimientos que apuntas tú**: importe, comercio o concepto, fecha, categoría y nota.
- **Pagos detectados en notificaciones**: si le das permiso de «Acceso a notificaciones», MiDinero lee
  **solo** las notificaciones de las apps de pago que elijas en *Ajustes → Apps de pago* (por defecto,
  Google Wallet / Google Pay). De cada pago guarda el importe, el nombre del comercio, la hora y el texto
  de la notificación (para que puedas revisarlo). Las notificaciones de cualquier otra app se ignoran
  sin leerse.
- **Fotos de tickets** (opcional): si adjuntas una foto a un gasto, se guarda una copia reducida en la
  carpeta privada de la app. MiDinero solo ve la foto que eliges o la que haces en ese momento; no tiene
  acceso al resto de tu galería ni permiso de cámara (la foto la hace la app de cámara del móvil).
- **Ficheros del banco que importas** (opcional): se leen para apuntar los movimientos y no se guardan.
- **Personas con las que compartes gastos**: solo el nombre que escribes tú, para llevar las cuentas.
- **Ajustes de la app**: límites, presupuesto, preferencias de avisos, tema, cambios de moneda, etc.

## Dónde se guardan

Todo se guarda en una base de datos local en tu móvil. MiDinero:

- no tiene servidores ni cuentas de usuario;
- **no tiene permiso de acceso a Internet**, así que no puede enviar datos a ningún servidor;
- no incluye anuncios, analítica ni seguimiento de terceros.

Si tienes activada la copia de seguridad de Android (Google), el sistema puede incluir los datos de
MiDinero en esa copia cifrada de tu cuenta, igual que con el resto de apps. Puedes desactivarlo en los
ajustes de Android.

## Reloj Wear OS (opcional)

Si instalas MiDinero en tu reloj, el móvil le envía un resumen: lo gastado este mes y hoy, el presupuesto,
los últimos movimientos, el gasto por categoría y la lista de categorías para apuntar. El reloj manda al móvil
los gastos que apuntes en él y, solo si le das acceso a notificaciones, los avisos de pago de Google Wallet o
Samsung Wallet del reloj. Los gastos se guardan únicamente en el móvil.

La comunicación usa la capa de datos de Wear OS (Google Play Services): normalmente va directa por Bluetooth;
si el reloj no está cerca del móvil, Wear OS puede hacerla pasar cifrada por los servicios de Google. MiDinero
no tiene servidor propio.

Si activas «Ocultar importes» en el móvil, el reloj muestra puntos en lugar de cifras desde la siguiente
sincronización. Para borrar lo que guarda el reloj, desinstala su app o borra sus datos.

## Copias de seguridad y exportaciones

Desde *Ajustes → Tus datos* puedes guardar una copia (JSON), programar una copia automática en la carpeta
que elijas o exportar tus movimientos (CSV). Esos ficheros se guardan donde tú elijas (por ejemplo, en tu
Google Drive) y a partir de ahí quedan bajo tu control. Las fotos de tickets no van dentro de esos
ficheros; sí pasan a un móvil nuevo con la transferencia de datos de Android.

Si pones una contraseña en *Ajustes → Tus datos → Copias con contraseña*, las copias (manuales y automáticas)
se guardan cifradas con AES-256 y solo pueden abrirse con esa contraseña. La contraseña se guarda únicamente
en tu móvil; si la olvidas, nadie puede recuperar esas copias. La exportación CSV no se cifra.

## Permisos

| Permiso | Para qué |
|---|---|
| Acceso a notificaciones (opcional) | Detectar automáticamente los pagos de las apps que elijas |
| Mostrar notificaciones | Avisarte de gastos detectados, cargos fijos, límites y el resumen semanal |
| Biometría (opcional) | Bloquear la app con tu huella, tu cara o el PIN del móvil |

## Borrar tus datos

Puedes borrarlo todo en *Ajustes → Tus datos → Borrar todos los datos*, o desinstalando la app.

## Contacto

Para cualquier duda sobre privacidad, escribe a: josemingorance13@gmail.com
