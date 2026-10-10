package com.midinero.control.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.midinero.control.TestApp
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream

/**
 * Capturas de las pantallas principales con datos de ejemplo, en claro y en oscuro, para revisar el diseño.
 * Solo se ejecuta con SCREENSHOTS=1 (el CI lo activa fuera de main y sube las imágenes a la rama «screenshots»).
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = TestApp::class, qualifiers = "w393dp-h851dp-xhdpi")
class ScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val dir = File("build/screenshots").apply { mkdirs() }

    private fun shot(name: String) {
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        runCatching {
            // Se pinta la ventana a mano: captureToImage espera un fotograma que Robolectric no llega a dar.
            // Se pintan todas las ventanas (la de la app y, encima, hojas y diálogos abiertos).
            val main = compose.activity.window.decorView
            val bitmap = android.graphics.Bitmap.createBitmap(main.width, main.height, android.graphics.Bitmap.Config.ARGB_8888)
            compose.runOnUiThread {
                val canvas = android.graphics.Canvas(bitmap)
                val roots = runCatching {
                    val global = Class.forName("android.view.WindowManagerGlobal")
                    val instance = global.getMethod("getInstance").invoke(null)
                    val field = global.getDeclaredField("mViews").apply { isAccessible = true }
                    @Suppress("UNCHECKED_CAST")
                    (field.get(instance) as List<android.view.View>).toList()
                }.getOrDefault(listOf(main))
                roots.filter { it.isShown && it.width > 0 }.ifEmpty { listOf(main) }.forEach { v ->
                    val location = IntArray(2)
                    v.getLocationOnScreen(location)
                    canvas.save()
                    canvas.translate(location[0].toFloat(), location[1].toFloat())
                    v.draw(canvas)
                    canvas.restore()
                }
            }
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            File(dir, "$name.png").writeBytes(png(bitmap.width, bitmap.height, pixels))
        }.onFailure { File(dir, "errores.txt").appendText("$name: ${it.stackTraceToString().take(3000)}\n\n") }
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

    /** Pulsa lo primero que tenga ese texto (o esa descripción), si existe. */
    private fun tap(label: String): Boolean {
        val matcher = (hasText(label) or hasContentDescription(label)) and hasClickAction()
        if (runCatching { compose.waitUntilAtLeastOneExists(matcher, 3_000) }.isFailure) return false
        compose.onAllNodes(matcher).onFirst().performClick()
        compose.waitForIdle()
        return true
    }

    private fun tour(theme: String) {
        assumeTrue(System.getenv("SCREENSHOTS") == "1")
        File(dir, "inicio-$theme.txt").writeText("Capturas en ${dir.absolutePath}\n")
        compose.waitUntilAtLeastOneExists(hasText("Bienvenido a MiDinero"), 10_000)
        shot("${theme}_01_bienvenida")
        tap("Empezar la configuración")
        shot("${theme}_02_paso1")
        tap("Atrás")
        tap("Probar con datos de ejemplo")
        compose.waitUntilAtLeastOneExists(hasText("Estás viendo datos de ejemplo"), 15_000)
        Thread.sleep(1_500)
        shot("${theme}_10_inicio")
        runCatching {
            compose.onAllNodes(androidx.compose.ui.test.hasScrollAction()).onFirst()
                .performScrollToNode(hasText("Tu plan"))
            shot("${theme}_11_inicio_abajo")
            compose.onAllNodes(androidx.compose.ui.test.hasScrollAction()).onFirst()
                .performScrollToNode(hasText("Buenos días") or hasText("Buenas tardes") or hasText("Buenas noches"))
        }
        if (tap("Actividad")) shot("${theme}_20_movimientos")
        if (tap("Análisis")) {
            shot("${theme}_30_analisis")
            if (tap("Año")) shot("${theme}_31_ano")
            if (tap("Calendario")) shot("${theme}_32_calendario")
        }
        if (tap("Más")) shot("${theme}_40_mas")
        runCatching {
            compose.onAllNodes(androidx.compose.ui.test.hasScrollAction()).onFirst().performScrollToNode(hasText("Tus comercios"))
            if (tap("Tus comercios")) shot("${theme}_45_comercios")
            compose.activity.onBackPressedDispatcher.onBackPressed()
        }
        if (tap("Fijos") || tap("Cargos fijos")) shot("${theme}_50_fijos")
        compose.activity.onBackPressedDispatcher.onBackPressed()
        if (tap("Límites")) shot("${theme}_60_limites")
        compose.activity.onBackPressedDispatcher.onBackPressed()
        if (tap("Inicio") && tap("Ajustes")) shot("${theme}_70_ajustes")
        compose.activity.onBackPressedDispatcher.onBackPressed()
        if (tap("Añadir gasto")) shot("${theme}_80_editor")
    }

    @Test
    fun claro() = tour("claro")

    /** Tablet en horizontal: Inicio y Actividad lado a lado. */
    @Test
    @Config(qualifiers = "w1100dp-h720dp-land-mdpi")
    fun tablet() {
        assumeTrue(System.getenv("SCREENSHOTS") == "1")
        compose.waitUntilAtLeastOneExists(hasText("Bienvenido a MiDinero"), 10_000)
        tap("Probar con datos de ejemplo")
        compose.waitUntilAtLeastOneExists(hasContentDescription("Ajustes"), 15_000)
        Thread.sleep(2_500)
        shot("tablet_10_inicio")
    }

    /** Con la letra del sistema al 130 %, para ver que nada se corta ni se solapa. */
    @Test
    fun letraGrande() {
        assumeTrue(System.getenv("SCREENSHOTS") == "1")
        org.robolectric.RuntimeEnvironment.setFontScale(1.3f)
        compose.activityRule.scenario.recreate()
        compose.waitUntilAtLeastOneExists(hasText("Bienvenido a MiDinero"), 10_000)
        shot("grande_01_bienvenida")
        tap("Probar con datos de ejemplo")
        // Con la letra grande el aviso de datos de ejemplo queda fuera de la pantalla: se espera a la cabecera
        compose.waitUntilAtLeastOneExists(hasContentDescription("Ajustes"), 15_000)
        Thread.sleep(2_500)
        shot("grande_10_inicio")
        if (tap("Actividad")) shot("grande_20_movimientos")
        if (tap("Análisis")) shot("grande_30_analisis")
        if (tap("Más")) shot("grande_40_mas")
    }

    @Test
    @Config(qualifiers = "w393dp-h851dp-night-xhdpi")
    fun oscuro() = tour("oscuro")
}
