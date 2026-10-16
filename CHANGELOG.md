# Cambios

## 4.20

- **Importar con datos extra**: el CSV exportado trae ahora las columnas Método de pago, Litros, Km del repostaje, Km del coche, Coche, kWh punta, kWh llano, kWh valle, kWh excedentes, kWh gas, m³ y Mantenimiento, y sirve de plantilla. Al importar un CSV con esas columnas se leen también el tipo (gasto, ingreso o devolución), la hora, la categoría y la nota. Las categorías, coches y métodos de pago que falten se crean, y las categorías con litros, kWh o m³ pasan solas a su panel de Casa y coche.
- **Consumo al momento**: al poner los km de un repostaje, el editor enseña los l/100 km, igual que el precio del litro.
- **Muchas categorías**: si tienes 12 o más, al apuntar salen las 8 que más usas y un botón «Todas» que despliega el resto, con buscador.

## 4.19

- **Activar más funciones** (Ajustes): funciones extra que vienen apagadas y se encienden cuando las quieras. Al apagarlas no se borra nada.
- **Métodos de pago**: crea los tuyos («💳 Crédito BBVA», «🏦 Débito Santander», «💶 Efectivo», «🔁 Transferencia HSBC»…) y elige en cada gasto con cuál pagaste. Uno puede ir por defecto y cada app de pago puede tener el suyo para los pagos detectados. En Más → Métodos de pago ves lo gastado con cada uno, mes a mes.
- **Perfiles**: lleva aparte tu negocio (o lo que quieras) sin clonar la app. Cada perfil tiene sus gastos, categorías, presupuesto, ajustes, tickets y copias de seguridad. Se cambia desde Más y la app se reinicia con los datos de ese perfil. Tu perfil de siempre no cambia.

## 4.18

- **Casa y coche** (nueva sección en Más), con una pantalla para cada cosa:
  - **Combustible**: al apuntar un repostaje pones los litros servidos y el precio del litro sale solo con el importe. También los km que duró el repostaje anterior, que se anotan en esa entrada (o los km del coche y se calculan solos). Ves lo gastado, el precio del litro, el consumo (l/100 km), el coste cada 100 km y un aviso si el consumo se dispara.
  - **Luz**: kWh punta, llano, valle y excedentes de cada recibo, el precio real del kWh, el reparto por tramos y un botón para copiar el resumen y comparar tarifas en luzfija.es.
  - **Gas** (kWh) y **Agua** (m³): consumo y precio por unidad de cada recibo y de cada mes.
  - **Coches**: lo que te cuesta cada coche (combustible, mantenimientos, ITV, impuesto y seguro), los km y el coste por km. Avisa de la ITV y del cambio de aceite (11 meses después del último, por defecto).
  - **Seguros**: precio de cada año y cuánto ha subido, y un aviso 45 días antes del vencimiento para pedir precio y negociar antes del plazo de baja (30 días).
- Los datos extra se apuntan en el propio editor de gastos al elegir una categoría de esos paneles (también en los pagos detectados). La categoría Gasolina de serie ya viene enlazada; para luz, gas y agua eliges la tuya o la crea con un toque.
- **Comparar periodos** (Más): el gasto entre las fechas que quieras, por categoría y mes a mes, frente a un año antes, el tramo anterior u otras fechas.
- Todo viaja en las copias de seguridad. Nada de lo que ya tenías cambia.

## 4.17

- **Elige tu moneda** en Ajustes → Privacidad y aspecto → Moneda: euros (por defecto), dólares o pesos mexicanos. Todos los importes, el widget y el reloj usan esa moneda; los avisos de pago en ella se apuntan tal cual y los de otras monedas se convierten (con pesos, «$» son pesos y «US$» son dólares). Lo ya apuntado no se convierte: solo cambia cómo se muestra. La moneda viaja en las copias de seguridad.

## 4.16

- **«¿Es un cargo fijo?» en Inicio**: cuando un pago se repite cada mes por un importe parecido (gimnasio, una suscripción, un seguro…) y aún no está en Fijos, aparece en «Tu plan» para añadirlo con un toque o descartarlo. Al añadirlo cuenta en la previsión del mes y avisa antes de cada cobro.
- Se quitan las cuentas y saldos de la 4.15 (pantalla, selector «Pagado con» y cuenta por defecto). La base de datos no cambia y no se pierde nada.

## 4.15

- **Previsión de fin de mes** en Inicio («Tu plan»): cuánto acabarás gastando al ritmo actual, si te pasarás del presupuesto y lo que te sobrará o faltará contando tus ingresos y los ingresos fijos que aún no han llegado.
- **Cuentas y saldos** (Más → Cuentas y saldos): efectivo, banco, Revolut, Bybit… con su saldo. Eliges en qué cuenta va cada gasto («Pagado con»), hay una cuenta por defecto para los nuevos y los detectados, y puedes pasar dinero entre cuentas sin que cuente como gasto.
- **Leer el ticket con la cámara**: al adjuntar la foto a un gasto nuevo se rellenan solos el total, el comercio y la fecha; también con el botón «Leer ticket». Se hace en el propio móvil, sin Internet.
- **Etiquetas**: escribe #etiquetas en la nota (#cumpleaños, #coche…). Se sugieren las que más usas, se buscan con «#etiqueta» y en Más → Etiquetas ves lo gastado en cada una.
- **Retos de ahorro** (Más → Retos de ahorro): días sin gastos al mes, gastar menos de X € en una categoría o rachas sin gastar en algo. Se ven en Inicio y avisan al cumplirse.
- **Asistente de Google**: «Ok Google, apunta 4 euros de café en MiDinero» lo apunta directamente.
- **Reloj**: nueva tarjeta «Semana y límites» y tres complicaciones nuevas: lo que te queda esta semana, la categoría más cerca de su límite y el último gasto.

## 4.14

- **Pagos con Google Wallet desde el reloj (Galaxy Watch y otros Wear OS)**: en el reloj el aviso del pago contactless lo publica Servicios de Google Play, no la app de Wallet, y la app del reloj lo descartaba. Ahora lo lee (solo los avisos de Play Services que llevan un importe) y el móvil lo apunta si tienes activada cualquiera de las dos opciones de Google en la detección.
- Los avisos de pago del reloj que no se entienden ya no se pierden: aparecen en «Avisos no reconocidos» (marcados con ⌚) para apuntarlos con un toque.
- **Guía para Galaxy Watch** (solo en móviles Samsung): en Ajustes → Reloj → «Pagos del reloj», los pasos para dar el acceso a notificaciones una sola vez con Bugjaeger, con el comando listo para copiar y un botón para abrir o instalar Bugjaeger. Deja claro que en el resto de relojes Wear OS no hace falta. MiDinero sigue sin permiso de Internet.
- **Tarjetas cripto**: Bleap, Bybit y Bybit EU en la lista de apps de pago de la detección (desactivadas al principio: actívalas en Ajustes → Detección de pagos). Los avisos tipo «… at Lidl was successful» o «… en Zara ha sido aprobado» ya guardan solo el comercio.
- El lector de avisos del reloj pide volver a conectarse si el sistema lo para, y lee también los avisos con varias líneas.

## 4.13

- **Foto del comercio desde el propio pago**: al tocar un pago, el icono junto al comercio se puede tocar para ponerle una foto de la galería; si no tiene logo aparece además «Poner foto a…». Se guarda como comercio tuyo (con un nombre limpio, «BAR PACO 1234 MADRID» → «Bar Paco»), así que sus próximos pagos ya salen con esa foto.
- **146 marcas más** con su logo y su categoría: más supermercados (bonÀrea, Alimerka, E.Leclerc…), restaurantes (TGI Fridays, La Mafia, Pret A Manger…), gasolineras y recarga (Esso, Ionity, Wenea…), apps y suscripciones (Perplexity, GitHub, Slack, Zoom, NYT, antivirus, VPN…), aerolíneas y transporte (Lufthansa, KLM, Wizz Air, Flixbus, FGC…), tiendas (Etsy, Miravia, LEGO, Puma, The North Face…), luz y seguros, ocio y apuestas. Los gastos que tenías sin categoría de esas marcas se clasifican solos.

## 4.12

- **Cualquier icono para tus categorías**: al crear o editar una, «Ver todos los iconos…» abre más de 200 emojis por temas con buscador en español e inglés («perro», «gym», «luz»…). También se puede escribir cualquier emoji con el teclado en el campo «Icono».
- **Tus comercios con foto**: en Más → Comercios que ya conoce → «Tus comercios» → Añadir, pon el nombre, una foto de tu galería, cómo aparece en el banco (opcional) y su categoría (opcional). Sus pagos se muestran con tu foto en todas las listas, se guardan con ese nombre y, si le pusiste categoría, se clasifican solos (también los que estaban sin categoría).
- **Tu foto en cualquier comercio**: toca uno de la lista de comercios conocidos, o abre un comercio en «Tus comercios» → «Foto del comercio», para cambiar su logo por una foto tuya.
- Las fotos van dentro de la base de datos y de las copias de seguridad, así que no se pierden al restaurar ni al cambiar de móvil.

## 4.11

- **Categorías personalizadas al momento**: el botón «+ Nueva» junto a las categorías crea una con su nombre, icono, color y (si quieres) límite, y la deja elegida. Está al apuntar o editar un gasto o ingreso, al tocar un pago sin categoría (en la lista o en su aviso), al cambiar la categoría de varios movimientos y en los editores de cargos fijos y de reglas. Siguen pudiéndose editar y borrar desde Límites.
- **Pantalla Categorías** (Más → Categorías): ordénalas con las flechas (ese orden se usa en todos los selectores), **ocúltalas** si no las usas (no se ofrecen al apuntar ni se usan para clasificar solas, pero su historial se conserva) y edítalas.
- **Borrar una categoría sin perder nada**: eliges a qué categoría pasan sus movimientos, cargos fijos, reglas y lo aprendido de sus comercios (o los dejas sin categoría, como antes).
- **Devoluciones**: los avisos de devolución o reembolso ya no se ignoran. Se apuntan restando, en la categoría y el viaje de la compra original del mismo comercio, así que el total del mes, los límites y el reloj cuadran. También se puede apuntar a mano desde el gasto (botón «Devolución», total o parcial) y, al importar del banco, un abono de devolución resta del gasto en lugar de contar como ingreso.
- **Deshacer al borrar una categoría**: vuelve con sus movimientos, cargos fijos, reglas y lo aprendido.
- **Ajustes más cortos**: fuera «Probar la detección» y los accesos repetidos que ya están en Más (reglas, comercios conocidos, importar del banco, cuentas con amigos) y en Inicio (datos de ejemplo).
- Más rápida con años de datos: importar del banco, clasificar, aplicar reglas y cambiar la categoría de un comercio ya no leen todo el historial.
- **Copias con contraseña**: en Ajustes → Tus datos puedes poner una contraseña y las copias, también las automáticas, se guardan cifradas (AES-256). Al restaurar una copia cifrada se pide la contraseña. Las copias sin cifrar se siguen pudiendo restaurar.
- **Cuentas con amigos**: toca un apunte pendiente para corregirlo o borrarlo (con «Deshacer»); se ven todos, no solo los cinco primeros.
- **Avisos no reconocidos → «Apuntar»**: abre el editor con la fecha, la hora y el importe del aviso, y lo quita de la lista al guardar.
- Una devolución detectada de la que no se encuentra la compra pregunta en el aviso de qué categoría era.
- Librerías de Android y del reloj al día (dentro de las que admite la versión actual de las herramientas) y la versión de la app se fija en un solo sitio.

## 4.10

**Nuevo icono y logo.** Anillo del presupuesto en lima con el € en la tipografía de la app (Plus Jakarta Sans), sobre tinta. Es el mismo en el móvil, el reloj, las notificaciones, la bienvenida y la ficha de Google Play (icono y gráfico de funciones regenerados con `tools/brand/brand.swift`). La bienvenida usa iconos en lugar de emojis.

Arreglos (revisión completa de la app):
- **Restaurar, Importar del banco y la foto del ticket vuelven a abrir el selector de archivos**: en móviles recientes fallaban con «Este móvil no deja abrir archivos desde aquí» o cerraban la app, por una librería antigua (fragment 1.2.5) que llegaba con la de la huella.
- Copias de seguridad: si falla la copia automática, ya no se pierde la del mismo día (se escribe la nueva antes de borrar la anterior); los fallos pasajeros se reintentan y solo pide elegir carpeta si de verdad no hay permiso. Restaurar una copia dañada avisa en vez de cerrar la app.
- Un fallo en segundo plano (base de datos llena, aviso de pago raro, botón de categoría del aviso) ya no cierra la app.
- Pagos detectados: se leen importes en formato inglés («€1,234.56»), «12,50 euros», y avisos de banco que añaden el saldo al final («… en AMAZON. Tu saldo es…»). Comercios como «Café Saldoni» o «Gasolinera Abonos» ya no se descartan.
- Importar del banco: dos compras iguales el mismo día en el fichero son dos gastos; un pago ya detectado en el móvil («Mercadona») no se duplica con la fila del banco («COMPRA TARJ. … MERCADONA VALENCIA»); se leen fechas como «1/9/2025 10:22» y «3/9/25»; si falla, lo dice en vez de cerrar la app.
- Reloj: los importes dictados con miles («15.000 €», «€1,200») y en palabras («ciento veinte», «dos mil quinientos», «doce coma cinco») salen bien; un apunte con la hora del reloj adelantada ya no se queda «Enviando…» para siempre.
- «Ocultar importes» se respeta también en los widgets y avisos aunque no se haya abierto la app.
- Con el bloqueo activado, volver de la cámara o del selector de archivos ya no pierde el gasto que estabas apuntando ni la foto del ticket (también al plegar o girar el móvil).
- «Permitir avisos» lleva a los ajustes de Android si se bloquearon antes, y el estado es correcto en Android 8-12. «Dar acceso a notificaciones» no cierra la app en móviles sin esa pantalla.
- La curva del mes ya no se descuadra con el cambio de hora; los widgets se actualizan al cambiar de mes; «este mes» en la búsqueda respeta el día de inicio del mes; los límites sugeridos no se quedan en un tercio con un solo mes de datos.
- Cambiar de idioma ya no recarga la pantalla dos veces.

## 4.9

- **Preparada para Google Play**: la app del reloj ya no va dentro de la del móvil ni se instala con Wear Installer 2 o ADB; se publica en la misma ficha de Google Play como app de Wear OS. *Ajustes → Reloj → Instalar en el reloj* abre esa ficha.
- *Pagos del reloj* ya no da pasos con ADB: explica cómo dar el permiso en el reloj y, si el reloj no tiene esa pantalla (Galaxy Watch), que los pagos se apuntan desde los avisos del banco en el móvil.
- Android 8 y 9: la copia automática ya no ofrece «Guardar en Descargas», que en esas versiones no funciona sin permisos.
- El enlace a la política de privacidad abre la página pública.
- El .aab para Google Play solo se genera firmado con la clave de subida, y el del reloj tiene su propio número de versión.

## 4.8

- **Copias en móviles sin selector de carpetas**: en algunos móviles «Elegir carpeta» cerraba la app porque el sistema no trae esa pantalla. Ahora la copia automática puede guardarse en **Descargas/MiDinero** (botón «Guardar en Descargas», sin pedir permisos), y si el selector no abre se usa esa carpeta sola. «Guardar copia» también guarda en Descargas/MiDinero si no se puede elegir dónde.
- Abrir una copia, importar el CSV del banco y añadir la foto del ticket ya no pueden cerrar la app si el móvil no abre el selector: muestran un aviso.

## 4.7

- **Informe de errores**: si MiDinero se cierra por un fallo, guarda el detalle técnico en tu móvil (no se envía a ningún sitio) y al abrirla de nuevo ofrece copiarlo o compartirlo para poder arreglarlo.
- Guardar copia, exportar CSV y elegir carpeta de copias ya no pueden cerrar la app si el móvil no abre el selector de archivos: muestran un aviso.

## 4.6

- **Pagos del reloj en Galaxy Watch**: los Galaxy Watch no muestran el permiso de acceso a notificaciones, así que la app del reloj no podía leer los pagos de Wallet. Ahora el reloj prueba también la pantalla de permiso de la app y, si no existe, lo explica; y en el móvil, *Ajustes → Reloj → Pagos del reloj* dice si el reloj tiene el permiso y da los pasos para concederlo una sola vez con Bugjaeger (sin ordenador), con un botón para copiar el comando.

## 4.5

- **Reloj: apuntar por voz**. Botón de micrófono junto al +: dices «doce con cincuenta café», ves el importe y el concepto y eliges la categoría (o dejas que la elija el móvil). Si no entiende el importe, abre el teclado.
- **Reloj: avisos de límite**. Al llegar al umbral o pasarte de un límite, el reloj vibra y lo muestra con su estilo (y el móvil no lo duplica en la muñeca).
- **Resumen del año** (Análisis → Año): comparación con el año anterior (a estas alturas si es el año en curso), mapa de calor del año día a día con el día más caro, imagen para compartir el año y acceso a todos los comercios.
- **Tus comercios** (Más → Tus comercios, o «Ver todos» en Análisis): ranking de dónde gastas más en 30 días, 3 meses, un año o todo, con cuántas veces, ticket medio y última visita.
- **Renovaciones anuales**: en Cargos fijos, las renovaciones anuales y trimestrales de los próximos 60 días; y un aviso una semana antes de cada renovación anual para poder darte de baja a tiempo.
- **Buscar por fecha**: «hoy», «ayer», «septiembre», «sept 2025», «12/09», «esta semana», «mes pasado»; se combina con texto e importes («amazon >30 septiembre»).
- **Importes ocultos con porcentaje**: con «Ocultar importes», Inicio, el widget y el reloj enseñan qué parte del presupuesto llevas («64 %») en lugar de puntos.
- **Más logos**: se buscan los que faltaban con nombres más concretos en Google Play.

## 4.4

- **Más logos**: 464 comercios con su logo real (antes 391). Los que faltaban se han buscado en el icono de su app de Google Play, en Wikidata y en Simple Icons, y se han revisado a mano: entran Carrefour, Dia, Vueling, Endesa, Sanitas, Iberdrola, Subway, Telepizza, Sephora, eBay, Calzedonia, Primark y muchos más.

## 4.3

- **Logos reales de los comercios**: 391 comercios del catálogo (Mercadona, Lidl, Netflix, Repsol, Zara…) muestran su logo en lugar de las siglas de color. Los logos van dentro de la app (se descargan al compilar, en GitHub), así que MiDinero sigue sin usar Internet. Los que no tienen un logo fiable siguen con su distintivo de siglas.

## 4.2

- **Instalar en el reloj, corregido**: Wear Installer 2 no aparece en el menú de compartir porque solo instala APK de la carpeta Descargas. Ahora el botón guarda la app del reloj en Descargas y abre Wear Installer 2: allí se pulsa «Custom APK» y se elige. El botón «Compartir» sigue disponible para apps que sí lo aceptan (WearLoad, Bugjaeger).

## 4.1

- **Instalar la app del reloj desde el móvil**: la app del móvil lleva dentro la del reloj. En *Ajustes → Reloj → Instalar en el reloj* se abre el menú de compartir para pasársela a Wear Installer 2 (u otra app que instale en relojes), sin ordenador ni descargas. Así el reloj siempre tiene la misma versión que el móvil. MiDinero sigue sin permiso de Internet.

## 4.0

- **App nueva para el reloj** (Wear OS, probada en diseño para Galaxy Watch Ultra 2), hecha desde cero con el estilo tinta y lima:
  - Esfera con **lo que puedes gastar hoy** en grande y un anillo con el presupuesto del mes (lima, ámbar si vas por delante del ritmo, rojo si te pasas) y una marca de dónde «tocaría» ir.
  - Al girar el bisel o deslizar: comparación con el mes anterior, curva del gasto del mes, últimos movimientos y gasto por categoría con sus límites.
  - **Apuntar desde la muñeca**: botón +, teclado numérico para pantalla redonda y elegir categoría (las más usadas primero). Sin conexión se guarda y se envía al volver; nunca se apunta dos veces.
  - **Tarjeta (tile)** «Hoy puedes gastar» con botón para apuntar, y **complicación** para cualquier esfera (texto o anillo).
  - Opcional: con acceso a notificaciones en el reloj, los pagos con Google Wallet o Samsung Wallet del reloj se apuntan solos en el móvil, sin duplicarse con el aviso del banco.
  - En Ajustes del móvil, un apartado «Reloj» indica si está conectado.
- **Las recargas de Revolut ya no cuentan como gasto**: los pagos con Google Wallet (o el banco) cuyo concepto incluye «Revolut» son mover dinero a tu cuenta de Revolut y se ignoran. Las que se habían apuntado solas se borran al actualizar (las apuntadas a mano no se tocan).
- Sustituye la app de reloj anterior. Instalación en [RELOJ.md](RELOJ.md).

## 3.9

- **Posibles duplicados**: si dos pagos del mismo importe llegan con pocos minutos de diferencia (Google Wallet y el banco avisando del mismo), Actividad lo avisa, los marca con «¿Repetido?» y permite revisarlos o descartar el aviso.
- **Arrastrar lo que sobra**: en Límites, un interruptor suma a cada límite (y al presupuesto total) lo que no se gastó el mes anterior. Se ve en Inicio, en Límites y en los avisos.
- **Subidas de precio**: si una suscripción o cuota que pagas cada mes sube (Netflix de 12,99 € a 13,99 €), aparece un aviso en Inicio.
- **Frente a tu media**: en Análisis, cada categoría comparada con su media de los 6 meses anteriores (en el mes en curso, lo previsto a fin de mes).
- **Resumen del mes en imagen**: el botón de compartir de Inicio permite enviar una tarjeta con el total, la curva del mes y en qué se ha ido, o el resumen en texto.
- **Tocar la pestaña en la que estás sube hasta arriba** (Inicio, Actividad, Análisis y Más).
- **Apuntar por voz**: el micrófono de la barra de Inicio entiende «doce con cincuenta café» o «veinte y cinco euros de gasolina».
- **Selección múltiple en Actividad**: mantén pulsado un movimiento para seleccionar varios y cambiarles la categoría o borrarlos (con deshacer).
- **Vibración suave** al guardar, borrar, cambiar de pestaña o de opción, y al pasarte de un límite.
- **Widgets nuevos**: la curva del gasto del mes y «Hoy puedes gastar».
- **Tablets y plegables**: con la pantalla ancha, Inicio muestra Actividad a su lado.
- **498 comercios reconocidos** (antes 285): más supermercados, restaurantes, moda, seguros, energía, telefonía, alquiler de coches, hoteles, viajes, gimnasios y suscripciones.

## 3.8

- **Corregido**: tras abrir una categoría o un movimiento desde Inicio, pulsar «Inicio» en la barra podía volver a abrir Actividad. Ahora siempre vuelve a Inicio.
- **Previsión más realista**: la previsión a fin de mes suma los cargos fijos que aún faltan por cobrar. En Análisis, «Gastos fijos» indica cuánto queda por llegar.
- **Mejor lectura de los avisos del banco**: los comercios con punto ya no se cortan («AMAZON.ES», «REPSOL E.S. 1234») y se entienden los recibos domiciliados («recibo de 45,20 € de IBERDROLA»). Probado con formatos de BBVA, Santander, CaixaBank, ING, Openbank, Revolut, Sabadell, Bankinter y PayPal.
- Nuevas apps de pago que se pueden activar: Banco Sabadell, Bankinter, PayPal, Wise y Curve.

## 3.7

- **Widgets y notificaciones con los colores nuevos**: los widgets de la pantalla de inicio usan la paleta tinta y lima (fondo blanco en claro, tinta en oscuro) y los avisos llevan el color de la app.
- **Editor de gastos pulido**: importe grande sin marco, moneda en píldora centrada y atajos de fecha, hora y viaje en píldoras con la opción elegida en tinta.
- **Accesibilidad**: una prueba automática comprueba que todo lo que se puede pulsar en las pantallas principales tiene nombre para TalkBack y tamaño suficiente, y se revisan capturas con la letra al 130 %.
- Por dentro: Inicio y Ajustes repartidos en archivos más pequeños y la compilación avisa (con imágenes de antes y después) cuando una pantalla cambia respecto a la anterior.

## 3.6

- **Barra de abajo blanca en modo claro**, con la pestaña activa en lima y el botón de añadir en tinta; en oscuro sigue en tinta con el botón lima.
- Accesos directos de Inicio más ligeros (círculos de color), avisos y consejos en tarjetas blancas con icono lima, y títulos de sección más grandes.
- Últimos movimientos con fechas relativas («Hoy», «Ayer», «lun 22 sept») y próximos cargos fijos con su logotipo e importe a la derecha.
- La cifra de Cargos fijos usa la misma cabecera de tinta con resplandor lima que Inicio.
- Editor de gastos: selector Gasto/Ingreso en píldora y el distintivo del comercio aparece al escribir uno conocido.

## 3.5

- **Diseño nuevo de arriba abajo, «tinta y lima»**: fondo de piedra cálida, tarjetas blancas, tinta casi negra para lo importante y un verde lima eléctrico para destacar. Modo oscuro negro profundo con acentos lima.
- **Tipografía propia**: Plus Jakarta Sans (licencia OFL) en toda la app, con cifras de ancho fijo.
- **Inicio**: cabecera en tinta con resplandor lima, total animado, anillo del presupuesto, la curva de lo gastado día a día con la línea del ritmo del presupuesto, e ingresos, ahorro y previsión.
- Tarjetas de categoría teñidas de su color, barra de abajo en tinta con la pestaña activa en lima y el botón de añadir lima, botones en píldora y selectores con la opción elegida en tinta.
- Icono de la app renovado con los nuevos colores.

## 3.4

- **Interfaz reconstruida**: barra de abajo flotante con cuatro pestañas (Inicio, Movimientos, Análisis y Más) y un botón central para añadir un gasto desde cualquier sitio.
- **Nueva pantalla Más**: todo lo demás en tarjetas con su estado al día (cargos fijos, límites, metas, viajes, cuentas con amigos, reglas, comercios, importar, ajustes y ayuda).
- **Inicio renovado**: saludo y fecha, cabecera con el total animado y un anillo del presupuesto, accesos directos, últimos movimientos y «Tu plan».
- **Movimiento**: las tarjetas se hunden al pulsarlas, las cifras cuentan hasta su valor, las barras se llenan y las pantallas entran con un fundido suave.
- Cabeceras iguales en todas las pantallas, selector segmentado animado en Análisis y en el idioma, ajustes agrupados con iconos de color y resumen de gastos e ingresos en Movimientos.

## 3.3

- **Comercios conocidos desde el primer día**: un catálogo de más de 280 comercios habituales en España (supermercados, gasolineras, restaurantes, suscripciones, transporte, tiendas, facturas, salud y ocio) con su categoría y su distintivo de color.
- Los pagos detectados se guardan con el nombre limpio: «MERCADONA S.A. 4432 VALENCIA» → «Mercadona», «PAYPAL *NETFLIX» → «Netflix».
- Los movimientos, el calendario y el detalle del comercio muestran el distintivo de la marca.
- Al actualizar, los gastos que ya tenías sin categoría se clasifican solos si son de un comercio conocido.
- En Ajustes → Categorización, «Comercios que ya conoce» con buscador. Tus reglas y lo que corriges siguen mandando.

## 3.2

- **Bienvenida nueva**: una presentación de la app (qué hace y cómo cuida tus datos) y una configuración en 5 pasos cortos y opcionales: detección de pagos, apps de las que leer, avisos, cuándo empieza tu mes y tu presupuesto, y privacidad.
- Aviso claro de qué lee MiDinero antes de pedir el acceso a las notificaciones.
- Resumen final con lo que has dejado activado, y datos de ejemplo para probar sin configurar nada.
- Paquete .aab para Google Play en cada versión.

## 3.1

- **Nuevo diseño**: paleta propia verde esmeralda (sin color dinámico) con modo claro y oscuro, esquinas muy redondeadas, tarjetas blancas sobre fondo gris claro y cifras en negrita.
- **Inicio**: cabecera con degradado (total del mes, comparación con el mes anterior, media diaria y barra de reparto) y tarjetas de categorías en dos columnas; al tocar una se abren sus movimientos.
- **Editores en hojas inferiores**: importe grande, atajos Hoy y Ayer y categorías con su color. También para cargos fijos, metas, viajes, reglas, deudas y presupuesto.
- **Estados vacíos amables** en todas las pantallas y movimientos agrupados por día en tarjetas.
- Se instala encima de la 1.0 conservando los datos (misma firma y migración desde la base de datos de la 1.0).
- **En inglés**: toda la app, los avisos, el widget, los resúmenes y el CSV. Se elige en Ajustes → Idioma (o en el idioma por app de Android 13+); en automático sigue al idioma del móvil.
- Fechas e importes con el formato de cada idioma (12,50 € / €12.50); las categorías por defecto se crean en el idioma elegido.
- La frase rápida entiende también inglés («12.50 coffee yesterday») y la importación reconoce columnas de bancos en inglés.
- Dependencias al día: Room 2.8, WorkManager 2.12, Glance 1.2, Core 1.17, Activity 1.11, Lifecycle 2.9.4, Navigation 2.9.4, Robolectric 4.17 (las versiones siguientes de Core, Lifecycle y Navigation ya exigen Android Gradle Plugin 9).
- Editor de gastos más robusto: la ruta de la foto de la cámara se crea al pulsar el botón.
- Botones flotantes con nombre y acción para TalkBack.

## 3.0

Todo lo necesario para llevar tus cuentas del día a día, de viaje y con amigos.

- **Apuntar en un segundo**: frase rápida («12,50 café ayer»), mosaico de ajustes rápidos, widget de añadir con tus categorías y atajo desde el icono.
- **Importar del banco**: movimientos desde el CSV de tu banco, con columnas detectadas solas y sin duplicados.
- **Tus reglas**: «si el comercio contiene… ponle esta categoría o este nombre», aplicables al historial.
- **Suscripciones detectadas** e ideas para ahorrar según tus hábitos.
- **Detalle** de cada categoría y comercio, **calendario de gasto** y **resumen del año**.
- **Metas de ahorro** con aportaciones, fecha objetivo y cuánto apartar al mes.
- **Cuentas con amigos**: comparte un gasto a partes iguales y lleva quién te debe y a quién debes.
- **Otras monedas**: apunta en dólares, libras, etc. con el cambio que elijas; los pagos detectados en otra moneda se convierten solos.
- **Viajes y eventos** con su propio presupuesto, gasto por día, proyección y avisos.
- **Fotos de tickets** adjuntas a cada gasto, con visor y zoom.
- **Búsqueda en todo el historial** por texto e importes (>50, 20-40) y **resumen del mes para compartir**.
- **Copia automática** diaria o semanal en la carpeta que elijas (por ejemplo, de Google Drive).
- **Datos de ejemplo** para probar la app y **ayuda** con preguntas frecuentes.
- **Tablets y horizontal** con barra lateral, mejor **accesibilidad** (TalkBack, tamaños de letra) y **rendimiento**.
- **Plataforma**: Android 16 (targetSdk 36), Kotlin 2.2, Compose y Room al día; tests de interfaz y de migraciones.

## 2.0

Gran actualización: de un registro de gastos básico a una app completa de finanzas personales.

- **Detección de pagos** más fiable: sin duplicados entre Wallet y Play Services, reconexión automática del servicio, apps de bancos opcionales, lista de avisos no reconocidos y botones para asignar la categoría desde la notificación.
- **Categorización** más precisa (gana la palabra más específica) y aprendizaje que se aplica a todo el historial del comercio.
- **Movimientos**: búsqueda, filtros por categoría y tipo, deslizar para borrar con deshacer, autocompletar comercios, duplicar, editar la hora, ordenar por importe.
- **Análisis**: total frente al mes anterior, media diaria, previsión, reparto por categoría, evolución de 6 meses, día de la semana, comercios top, ingresos y ahorro.
- **Cargos fijos**: frecuencia semanal y trimestral, cuotas con fin, aviso el día antes, saltar o adelantar cargos, historial.
- **Presupuestos**: presupuesto total, ritmo del mes, lo que puedes gastar al día, límites sugeridos, meses cumplidos, umbral de aviso configurable y resumen semanal.
- **Ingresos**: nómina y otros ingresos, balance y tasa de ahorro.
- **Tus datos**: copia de seguridad y restauración, exportación a CSV, borrar todo, gestionar comercios aprendidos.
- **Privacidad**: bloqueo con huella/PIN, ocultar importes, pantalla privada, sin permiso de Internet.
- **Personalización**: tema claro/oscuro, Material You, mes que empieza el día que cobras.
- **Extras**: bienvenida guiada, widget, accesos directos, icono temático de Android 13.
- **Calidad**: tests unitarios y de base de datos, migración probada, R8, CI en todas las ramas.

## 1.0

Primera versión: detección de pagos de Google Pay, categorías, límites mensuales, gastos recurrentes y gastos manuales.
