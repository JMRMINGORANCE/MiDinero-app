package com.midinero.control.wear

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.midinero.control.link.WatchCategory
import com.midinero.control.link.WatchMovement
import com.midinero.control.link.WatchState
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream

/**
 * Capturas de la app del reloj (pantalla redonda de 225 dp) con datos de ejemplo. Se guardan junto a las del
 * móvil para revisarlas en la rama «screenshots». Solo con SCREENSHOTS=1.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w225dp-h225dp-xhdpi")
class WatchScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val dir = File("../app/build/screenshots").apply { mkdirs() }

    private val sample = WatchState(
        updated = System.currentTimeMillis() - 4 * 60_000,
        english = false,
        monthLabel = "Octubre",
        spent = "642,30 €",
        budget = "1.000 €",
        budgetRatio = 0.64f,
        pace = 0.55f,
        todayLabel = "Hoy puedes gastar",
        todayValue = "24,60 €",
        todayNote = "Llevas 8,90 € hoy",
        versus = "12 % menos que en septiembre",
        curve = listOf(0L, 3200, 3200, 8900, 15600, 21000, 26800, 31000, 38800, 41200, 47600, 52300, 58900, 64230),
        recent = listOf(
            WatchMovement("Mercadona", "23,40 €", "🛒", 0xFF2FB36B.toInt(), "13:05", false),
            WatchMovement("Café Sol", "2,80 €", "☕", 0xFFFF9F43.toInt(), "09:12", false),
            WatchMovement("Nómina", "+1.850 €", "💼", 0xFF4D9DFF.toInt(), "Ayer", true),
            WatchMovement("Netflix", "13,99 €", "🎬", 0xFFE5484D.toInt(), "3 oct", false),
        ),
        categories = listOf(
            WatchCategory(1, "Supermercado", "🛒", 0xFF2FB36B.toInt(), "214,60 €", "300 €", 0.72f),
            WatchCategory(2, "Restaurantes", "🍽️", 0xFFFF9F43.toInt(), "158,00 €", "120 €", 1.32f),
            WatchCategory(3, "Transporte", "🚌", 0xFF4D9DFF.toInt(), "64,20 €"),
        ),
        quick = listOf(
            WatchCategory(1, "Supermercado", "🛒", 0xFF2FB36B.toInt()),
            WatchCategory(2, "Restaurantes", "🍽️", 0xFFFF9F43.toInt()),
            WatchCategory(3, "Transporte", "🚌", 0xFF4D9DFF.toInt()),
            WatchCategory(4, "Ocio", "🎟️", 0xFFB46BFF.toInt()),
        ),
    )

    @Before
    fun setUp() {
        assumeTrue(System.getenv("SCREENSHOTS") == "1")
        WatchStore.init(compose.activity)
        WatchStore.applyState(compose.activity, sample.toJson())
    }

    private fun show(content: @Composable () -> Unit) {
        compose.setContent { WatchTheme { content() } }
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
    }

    private fun shot(name: String) {
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        runCatching {
            val view = compose.activity.window.decorView
            val bitmap = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            compose.runOnUiThread { view.draw(android.graphics.Canvas(bitmap)) }
            val w = bitmap.width
            val h = bitmap.height
            val pixels = IntArray(w * h)
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
            // Lo que queda fuera de la pantalla redonda, en gris (como el bisel)
            val r = w / 2f
            for (y in 0 until h) for (x in 0 until w) {
                val dx = x + 0.5f - r
                val dy = y + 0.5f - h / 2f
                if (dx * dx + dy * dy > r * r) pixels[y * w + x] = 0xFF3A3A40.toInt()
            }
            File(dir, "reloj_$name.png").writeBytes(png(w, h, pixels))
        }.onFailure { File(dir, "errores.txt").appendText("reloj_$name: ${it.stackTraceToString().take(3000)}\n\n") }
    }

    @Test
    fun inicio() {
        show { HomeScreen(sample, emptyList(), System.currentTimeMillis(), {}, {}, {}, onVoice = {}) }
        shot("1_esfera")
    }

    @Test
    fun movimientos() {
        val pending = listOf(PendingEntry("x", 450, 2, "🍽️", "Restaurantes", 0xFFFF9F43.toInt(), 0))
        show { HomeScreen(sample, pending, System.currentTimeMillis(), {}, {}, {}) }
        compose.onRoot().performTouchInput { swipeUp(durationMillis = 400) }
        shot("2_lista")
    }

    @Test
    fun teclado() {
        show { AmountScreen {} }
        listOf("1", "2", "Coma", "5").forEach { compose.onNodeWithContentDescription(it).performClick() }
        shot("3_teclado")
    }

    @Test
    fun categoria() {
        show { CategoryScreen(1250, sample.quick, "Café") {} }
        shot("4_categoria")
    }

    @Test
    fun hecho() {
        show { DoneScreen(1250, "🍽️ Restaurantes") {} }
        shot("5_apuntado")
    }

    @Test
    fun ajustes() {
        show { SettingsScreen(sample, false, System.currentTimeMillis()) {} }
        shot("6_ajustes")
    }

    @Test
    fun sinDatos() {
        show { HomeScreen(WatchState(), emptyList(), System.currentTimeMillis(), {}, {}, {}) }
        shot("7_sin_datos")
    }

    /** PNG mínimo (RGB de 8 bits) a partir de píxeles ARGB. */
    private fun png(width: Int, height: Int, argb: IntArray): ByteArray {
        val raw = ByteArrayOutputStream()
        DeflaterOutputStream(raw).use { z ->
            val row = ByteArray(1 + width * 3)
            for (y in 0 until height) {
                row[0] = 0
                for (x in 0 until width) {
                    val c = argb[y * width + x]
                    row[1 + x * 3] = (c shr 16).toByte()
                    row[2 + x * 3] = (c shr 8).toByte()
                    row[3 + x * 3] = c.toByte()
                }
                z.write(row)
            }
        }
        val out = ByteArrayOutputStream()
        val data = DataOutputStream(out)
        data.write(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10))
        fun chunk(type: String, body: ByteArray) {
            data.writeInt(body.size)
            val typeBytes = type.toByteArray(Charsets.US_ASCII)
            data.write(typeBytes)
            data.write(body)
            val crc = CRC32().apply { update(typeBytes); update(body) }
            data.writeInt(crc.value.toInt())
        }
        val header = ByteArrayOutputStream().also { h ->
            DataOutputStream(h).apply { writeInt(width); writeInt(height); write(byteArrayOf(8, 2, 0, 0, 0)) }
        }.toByteArray()
        chunk("IHDR", header)
        chunk("IDAT", raw.toByteArray())
        chunk("IEND", ByteArray(0))
        return out.toByteArray()
    }
}
