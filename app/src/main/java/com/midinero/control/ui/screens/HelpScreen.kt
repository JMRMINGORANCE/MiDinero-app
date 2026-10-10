package com.midinero.control.ui.screens

import com.midinero.control.domain.Lang
import com.midinero.control.domain.tr
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val faq get() = if (Lang.english) faqEn else faqEs

private val faqEs = listOf(
    "¿Puedo usar la app en inglés?" to
        "Sí. En Ajustes → Privacidad y aspecto → Idioma elige English. En «Automático» la app sigue el idioma del móvil.",
    "¿Puedo guardar la foto del ticket?" to
        "Sí. Al apuntar o editar un gasto, toca «Hacer foto» o «Galería». La foto se guarda reducida y solo en tu móvil; " +
            "en Movimientos verás un 📎 y puedes filtrar «Con ticket». Tócala para verla en grande (con zoom) o compartirla, " +
            "útil para garantías y devoluciones. Las fotos no van dentro de la copia en fichero.",
    "¿Para qué sirven los viajes y eventos?" to
        "Para saber cuánto te cuesta algo que no encaja en un mes: unas vacaciones, una boda, la reforma de casa. " +
            "Crea uno en Inicio › Viajes y eventos con sus fechas y, si quieres, un presupuesto. Lo que apuntes esos días " +
            "(también lo detectado de Google Wallet) se asigna solo, y te avisamos al llegar al 80 % y al 100 %. " +
            "Los gastos siguen contando en su mes y su categoría como siempre.",
    "Estoy de viaje y pago en otra moneda" to
        "Al apuntar un gasto, toca «EUR» junto al importe y elige la moneda. Escribe lo que pagaste y el cambio " +
        "(se propone uno aproximado y se recuerda el último que uses). Se guarda en euros para tus límites y estadísticas, " +
        "y en la lista verás también el importe original. Si Google Wallet avisa de un pago en dólares o libras, " +
        "MiDinero lo convierte con ese mismo cambio.",
    "No se detectan mis pagos con el móvil" to
        "1) En Ajustes, comprueba que la detección está «Activada». 2) En «Apps de pago» marca la app con la que pagas " +
        "(Google Wallet viene activada; bancos y Samsung Wallet hay que marcarlos). 3) Mira «Avisos no reconocidos»: si tu " +
        "banco usa otro formato, aparecerá ahí y puedes apuntarlo a mano. 4) Algunos móviles (Xiaomi, Huawei, Oppo...) " +
        "cierran las apps en segundo plano: quita a MiDinero de la optimización de batería y, si existe, activa el «Inicio automático».",
    "Se ha apuntado un pago dos veces" to
        "MiDinero descarta los avisos repetidos de un mismo pago, pero si pasa, desliza el movimiento a la izquierda para borrarlo. " +
        "Si te ocurre a menudo con una app, desactívala en «Apps de pago» y deja solo Google Wallet.",
    "La categoría no es la correcta" to
        "Cámbiala al editar el gasto y deja marcado «Usar siempre esta categoría»: MiDinero lo recordará. Para casos más " +
        "complejos crea una regla en Más → Tus reglas (por ejemplo, «contiene amzn → Compras»).",
    "¿Puedo crear mis propias categorías?" to
        "Sí. Al apuntar o editar un movimiento (también al tocar uno sin categoría), un cargo fijo o una regla, toca «+ Nueva» junto a las categorías: eliges nombre, icono y color y queda " +
        "elegida. En Más → Categorías puedes editarlas, ordenarlas, ocultarlas o borrarlas (pasando sus movimientos a otra).",
    "¿Puedo proteger mis copias de seguridad?" to
        "Sí. En Ajustes → Tus datos → Copias con contraseña, pon una contraseña: las copias (también las automáticas) se " +
        "guardan cifradas y al restaurar se pide la contraseña. Si la olvidas, esas copias no se pueden abrir.",
    "¿Qué diferencia hay entre límite y presupuesto?" to
        "El presupuesto es lo máximo para todo el mes. Los límites son por categoría (restaurantes, ocio...). Puedes usar uno, " +
        "otro o los dos; te avisamos al acercarte y al pasarte.",
    "Cobro a final de mes, ¿cómo cuadro los meses?" to
        "En Ajustes → Privacidad y aspecto → «Mi mes empieza el día», elige el día que cobras. Los meses irán de nómina a nómina.",
    "¿Cómo paso mis datos a otro móvil?" to
        "Guarda una copia en Ajustes → Tus datos (mejor aún, activa la copia automática en una carpeta de Google Drive) y, en el " +
        "móvil nuevo, usa «Restaurar». La copia de Android también suele llevarse los datos al cambiar de móvil.",
    "¿Puedo importar lo que ya tengo en el banco?" to
        "Sí: descarga los movimientos en CSV desde la web o app del banco y usa Más → «Importar del banco». " +
        "No se duplicará lo que ya tengas.",
    "¿Cómo apunto un gasto compartido?" to
        "Abre el gasto y pulsa «Compartir»: indica con quién (por ejemplo «Ana y Luis»). En tus gastos quedará solo tu parte " +
        "y en Cuentas con amigos verás lo que te debe cada uno hasta que lo saldes.",
    "¿Mis datos salen del móvil?" to
        "No. MiDinero no tiene servidores ni permiso de Internet. Si usas la app del reloj, el móvil le pasa un resumen directamente por la conexión del reloj (Bluetooth). Las copias las guardas tú donde quieras.",
)

private val faqEn = listOf(
    "Can I use the app in Spanish?" to
        "Yes. In Settings → Privacy and appearance → Language choose Español. On “Automatic” the app follows your phone's language.",
    "Can I save a photo of the receipt?" to
        "Yes. When adding or editing an expense, tap “Take photo” or “Gallery”. The photo is stored reduced and only on your phone; " +
            "in Transactions you'll see a 📎 and can filter “With receipt”. Tap it to view it full size (with zoom) or share it, " +
            "handy for warranties and returns. Photos aren't included in the backup file.",
    "What are trips and events for?" to
        "To know how much something that doesn't fit in a month costs you: a holiday, a wedding, a home renovation. " +
            "Create one in Home › Trips and events with its dates and, if you like, a budget. What you log on those days " +
            "(including what's detected from Google Wallet) is assigned automatically, and we warn you at 80 % and 100 %. " +
            "Expenses still count in their month and category as usual.",
    "I'm travelling and paying in another currency" to
        "When adding an expense, tap “EUR” next to the amount and choose the currency. Enter what you paid and the rate " +
            "(an approximate one is suggested and the last one you use is remembered). It's saved in euros for your limits and insights, " +
            "and the list also shows the original amount. If Google Wallet reports a payment in dollars or pounds, " +
            "MiDinero converts it with that same rate.",
    "My phone payments aren't being detected" to
        "1) In Settings, check that detection is “On”. 2) In “Payment apps”, tick the app you pay with " +
            "(Google Wallet is on by default; banks and Samsung Wallet need to be ticked). 3) Look at “Unrecognised notifications”: if your " +
            "bank uses a different format, it will show up there and you can log it by hand. 4) Some phones (Xiaomi, Huawei, Oppo...) " +
            "close apps in the background: remove MiDinero from battery optimisation and, if available, turn on “Autostart”.",
    "A payment was logged twice" to
        "MiDinero ignores repeated notifications for the same payment, but if it happens, swipe the transaction left to delete it. " +
            "If it happens often with one app, turn it off in “Payment apps” and keep only Google Wallet.",
    "The category is wrong" to
        "Change it when editing the expense and leave “Always use this category” ticked: MiDinero will remember it. For more " +
            "complex cases, create a rule in More → Your rules (for example, “contains amzn → Shopping”).",
    "Can I create my own categories?" to
        "Yes. When adding or editing a transaction (including tapping an uncategorised one), a recurring charge or a rule, tap “+ New” next to the categories: pick a name, icon and colour and it's " +
            "selected straight away. In More → Categories you can edit, reorder, hide or delete them (moving their " +
            "transactions to another category).",
    "Can I protect my backups?" to
        "Yes. In Settings → Your data → Password-protected backups, set a password: backups (automatic ones too) are " +
            "saved encrypted and the password is asked for when restoring. If you forget it, those backups can't be opened.",
    "What's the difference between a limit and a budget?" to
        "The budget is the most for the whole month. Limits are per category (restaurants, leisure...). You can use one, " +
            "the other or both; we warn you when you get close and when you go over.",
    "I'm paid at the end of the month. How do I line up my months?" to
        "In Settings → Privacy and appearance → “My month starts on day”, choose the day you're paid. Your months will run from payday to payday.",
    "How do I move my data to another phone?" to
        "Save a backup in Settings → Your data (better still, turn on automatic backup to a Google Drive folder) and, on the " +
            "new phone, use “Restore”. Android's own backup usually brings the data across when you change phones too.",
    "Can I import what I already have at my bank?" to
        "Yes: download your transactions as CSV from your bank's website or app and use More → “Import from your bank”. " +
            "Nothing you already have will be duplicated.",
    "How do I log a shared expense?" to
        "Open the expense and tap “Share”: say who with (for example “Ana and Luis”). Only your part stays in your expenses " +
            "and Split with friends shows what each person owes you until you settle up.",
    "Does my data leave my phone?" to
        "No. MiDinero has no servers and no Internet permission. If you use the watch app, your phone sends it a summary directly over the watch connection (Bluetooth). You keep backups wherever you like.",
)

@Composable
fun HelpScreen(onBack: () -> Unit) {
    var open by rememberSaveable { mutableStateOf<Int?>(null) }
    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        item {
            ScreenHeader(tr("Ayuda", "Help"), subtitle = tr("Respuestas a las dudas más comunes", "Answers to the most common questions"), onBack = onBack)
        }
        items(faq.size) { i ->
            val (q, a) = faq[i]
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { open = if (open == i) null else i }
                    .padding(vertical = 14.dp)
                    .animateContentSize(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(q, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Icon(
                        if (open == i) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (open == i) tr("Cerrar", "Close") else tr("Abrir", "Open"),
                    )
                }
                if (open == i) {
                    Text(
                        a,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            HorizontalDivider()
        }
    }
}

