# MiDinero · Control de gastos

App Android para controlar tus gastos casi sin esfuerzo, en español e inglés. **Sin cuentas, sin Internet, sin anuncios**: tus datos solo están en tu móvil. Con app para relojes Wear OS (Galaxy Watch).

## Qué hace

**Apuntar sin esfuerzo**
- Detecta los pagos con el móvil leyendo la notificación de **Google Wallet / Google Pay** (y, si quieres, de Samsung Wallet, Revolut, N26, BBVA, CaixaBank, Santander, ING u Openbank).
- Clasifica cada gasto por el comercio (Mercadona → Supermercado, Glovo → Comida a domicilio…) y **aprende** cuando lo corriges.
- **Categorías personalizadas** con tu nombre, icono y color, creadas desde el propio editor del gasto.
- Desde el aviso del pago puedes asignar la categoría con un toque, sin abrir la app.
- Gastos e **ingresos** manuales con autocompletado de comercios, fecha y hora.
- Frase rápida («12,50 café ayer»), widget, mosaico de ajustes rápidos y accesos directos.
- **Importar del banco** (CSV) sin duplicados y **reglas** propias («si contiene AMZN → Amazon, Compras»).
- **Foto del ticket** en cada gasto.

**Cargos fijos**
- Alquileres, suscripciones, cuotas y nóminas: semanales, mensuales, trimestrales o anuales.
- Se apuntan solos el día que toca; cuotas con fin automático, aviso el día antes, saltar o adelantar un cargo.

**Presupuestos**
- Presupuesto total del mes y límites por categoría, con avisos al llegar al umbral que elijas y al pasarte.
- Marca de ritmo («vas rápido»), lo que puedes gastar al día y límites sugeridos según lo que sueles gastar.
- El mes puede empezar el día que cobras (de nómina a nómina).

**Análisis**
- Total del mes frente al anterior, media diaria y previsión a fin de mes.
- Reparto por categoría, evolución de 6 meses, gasto por día de la semana y comercios donde más gastas.
- Ingresos, ahorro y tasa de ahorro. Resumen semanal los lunes.

**Viajes, amigos y metas**
- **Viajes y eventos** con presupuesto propio: lo de esas fechas se asigna solo; gasto por día y proyección.
- **Otras monedas** con el cambio que elijas (los pagos detectados en dólares o libras se convierten solos).
- **Cuentas con amigos**: compartir un gasto a partes iguales y saldar deudas.
- **Metas de ahorro** con aportaciones y cuánto apartar al mes.
- Suscripciones detectadas, ideas para ahorrar, calendario de gasto y resumen anual.

**Tus datos**
- Búsqueda en todo el historial (también por importe: «>50», «20-40»).
- Copia de seguridad (JSON) manual o **automática** en la carpeta que elijas; exportación a Excel (CSV).
- Bloqueo con huella/PIN, ocultar importes, pantalla privada, tema claro/oscuro.

## Instalar

Cada cambio en `main` compila la app con GitHub Actions y publica el APK en **Releases**. Descárgalo desde el móvil, ábrelo y permite instalar apps de origen desconocido si Android lo pide. Al abrir MiDinero por primera vez, la bienvenida te guía para activar la detección de pagos.

## Galaxy Watch / Wear OS

App para el reloj (módulo `wear`): esfera con lo que puedes gastar hoy y el anillo del presupuesto del mes, últimos movimientos, gasto por categoría, apuntar gastos con un teclado pensado para pantalla redonda, tarjeta (tile) y complicación para la esfera. Los apuntes se guardan en el móvil, una sola vez aunque el reloj los reenvíe. Instalación en [RELOJ.md](RELOJ.md).

## Desarrollo

Kotlin 2.2 · Jetpack Compose (Material 3) · Room · WorkManager · Glance · NotificationListenerService. minSdk 26 (Android 8), targetSdk 36 (Android 16).

```bash
./gradlew testDebugUnitTest   # tests (JVM + Robolectric)
./gradlew assembleRelease     # APK firmado
```

| Carpeta | Contenido |
|---|---|
| `data/` | Base de datos Room, migraciones, copias de seguridad, preferencias y repositorio |
| `domain/` | Lógica pura y testeada: lectura de notificaciones, categorizador, reglas, recurrencias, presupuestos, estadísticas, periodos, monedas, viajes, búsqueda, CSV |
| `service/` | Detección de pagos, avisos, tareas en segundo plano y acciones de las notificaciones |
| `widget/` | Widget de la pantalla de inicio |
| `ui/` | Pantallas: Inicio, Movimientos, Análisis, Fijos, Límites, Ajustes, búsqueda, viajes, metas, cuentas, reglas, importar, ayuda, bienvenida y bloqueo |
| `schemas/` | Esquemas de la base de datos por versión (para comprobar migraciones) |

### Firma de la app

Para publicar, guarda tu propia clave en los secretos del repositorio (`MIDINERO_KEYSTORE_BASE64`, `MIDINERO_KEYSTORE_PASSWORD`, `MIDINERO_KEY_ALIAS`, `MIDINERO_KEY_PASSWORD`); CI la usará en lugar de la clave de desarrollo incluida en el repositorio. **No publiques en Google Play con la clave de desarrollo**: su contraseña es pública. Ojo: un APK firmado con otra clave no se instala encima del anterior (hay que desinstalar; guarda antes una copia de seguridad).

## Privacidad

Ver [PRIVACY.md](PRIVACY.md). Pasos para Google Play en [PLAY_STORE.md](PLAY_STORE.md). Historial de cambios en [CHANGELOG.md](CHANGELOG.md).
