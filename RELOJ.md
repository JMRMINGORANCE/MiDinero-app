# MiDinero en el reloj (Wear OS · Galaxy Watch)

App para relojes con Wear OS 3 o posterior (Galaxy Watch 4 en adelante, incluido el **Galaxy Watch Ultra 2**).
Es la compañera de la app del móvil: los gastos se guardan en el móvil y el reloj muestra un resumen y permite apuntar.

## Qué hace

- **Esfera de MiDinero**: lo que puedes gastar hoy en grande, el anillo del presupuesto del mes (lima si vas bien,
  ámbar si vas por delante del ritmo, rojo si te pasas) y lo gastado frente al presupuesto.
- Gira el bisel o desliza hacia arriba: comparación con el mes anterior, curva del mes, últimos movimientos y
  gasto por categoría con su límite.
- **Botón +**: teclado numérico (mantén pulsado ⌫ para borrar todo), eliges la categoría y listo. Si el móvil no
  está cerca, se queda «Enviando al móvil…» y se guarda en cuanto conecten. Cada apunte se guarda una sola vez.
- **Tarjeta (tile)**: mantén pulsada la esfera del reloj o desliza a la derecha → añadir tarjeta → «MiDinero · hoy».
- **Complicación**: al editar una esfera, elige MiDinero → «Hoy puedes gastar» (texto corto o anillo).
- **Pagos del reloj (opcional)**: con acceso a notificaciones, los pagos con Google Wallet o Samsung Wallet hechos
  con el reloj se apuntan solos en el móvil. Si el banco también avisa en el móvil, no se duplica.
  Las recargas de Revolut se ignoran.

## Instalación desde Google Play

La app del reloj se publica en la misma ficha de Google Play que la del móvil (bundle de Wear OS).

- En el móvil: ficha de MiDinero en Google Play → «Disponible en más dispositivos» → elige el reloj. En MiDinero,
  *Ajustes → Reloj → Instalar en el reloj* abre esa ficha.
- O en el reloj: abre Google Play y busca MiDinero.

Google Play la actualiza sola, igual que la del móvil.

## Instalación a mano con ordenador (ADB)

Solo para la versión de [Releases](https://github.com/JMRMINGORANCE/MiDinero/releases), que no se mezcla con la de Google Play
(tienen firmas distintas). Descarga `MiDinero-Reloj-<versión>.apk` de la misma versión que la app del móvil (las dos deben ser de la misma
publicación de [Releases](https://github.com/JMRMINGORANCE/MiDinero/releases) porque comparten firma).

1. En el reloj: **Ajustes → Información del reloj → Información de software** y pulsa 7 veces en
   «Versión de software» para activar las opciones de desarrollador.
2. **Ajustes → Opciones de desarrollador**: activa «Depuración ADB» y «Depuración inalámbrica». Reloj y ordenador en la misma wifi.
3. En «Depuración inalámbrica» pulsa «Emparejar nuevo dispositivo» y, en el ordenador (con
   [Android Platform Tools](https://developer.android.com/tools/releases/platform-tools)):

   ```sh
   adb pair IP:PUERTO_DE_EMPAREJAMIENTO      # escribe el código que muestra el reloj
   adb connect IP:PUERTO                     # el que aparece en «Depuración inalámbrica»
   adb install -r MiDinero-Reloj-<versión>.apk
   ```

   Si tenías la app de reloj anterior, `install -r` la sustituye.
4. Abre MiDinero en el reloj. Si sale «Abre MiDinero en el móvil», abre la app del móvil (versión 4.0 o posterior)
   y pulsa «Sincronizar» en el reloj.
5. Opcional, para los pagos del reloj: en el reloj, MiDinero → ⚙ → «Pagos del reloj». Si el reloj no muestra la
   pantalla del permiso (los Galaxy Watch no la tienen), puedes darlo desde el ordenador:

   ```sh
   adb shell cmd notification allow_listener com.midinero.control/com.midinero.control.wear.WatchPaymentListener
   ```

   En el móvil, la detección de pagos debe estar activada y la app de pago (Google Wallet / Samsung Wallet) marcada.

Al terminar puedes desactivar la depuración en el reloj. Para actualizar, repite `adb install -r` con el APK nuevo.

## Cómo funciona

- El móvil publica un resumen por la capa de datos de Wear OS cada vez que cambian tus gastos, tus ajustes o el día.
  El reloj lo guarda para verlo también sin conexión e indica cuándo se actualizó.
- Los apuntes del reloj llevan un identificador único: el móvil los guarda una vez y le confirma al reloj, que
  entonces los borra de su cola.
- Los avisos de pago del reloj los interpreta el móvil con el mismo lector que los suyos, con la misma detección
  de duplicados (mismo importe y comercio con menos de 90 segundos de diferencia).
- Sin Internet ni cuentas: la app del reloj no tiene permiso de Internet; la comunicación la hace Wear OS.
