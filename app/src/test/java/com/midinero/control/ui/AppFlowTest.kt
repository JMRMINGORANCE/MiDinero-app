package com.midinero.control.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.midinero.control.TestApp
import androidx.compose.ui.semantics.getOrNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Recorre la app de verdad: bienvenida, pestañas, ajustes, apuntar, buscar y viajes.
 * No escribe en los diálogos: en Robolectric un campo de texto dentro de un AlertDialog no llega nunca
 * a reposo (comprobado aislando cada parte del editor), así que el editor se prueba en un móvil real.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = TestApp::class, qualifiers = "w411dp-h1600dp")
class AppFlowTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun skipOnboarding() {
        compose.onNodeWithText("Bienvenido a MiDinero").assertExists()
        compose.onNodeWithText("Saltar").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Gasto por categoría"), 5_000)
    }

    /** Pulsa una pestaña y espera a ver [marker]; reintenta por si la app aún estaba arrancando. */
    private fun openTab(label: String, marker: SemanticsMatcher) {
        repeat(3) {
            compose.waitUntilAtLeastOneExists(hasText(label) and hasClickAction(), 5_000)
            compose.onAllNodes(hasText(label) and hasClickAction()).onFirst().performClick()
            compose.waitForIdle()
            if (runCatching { compose.waitUntilAtLeastOneExists(marker, 3_000) }.isSuccess) return
        }
        try {
            compose.waitUntilAtLeastOneExists(marker, 3_000)
        } catch (e: Throwable) {
            // Para ver en el CI qué había en pantalla
            throw AssertionError("No aparece ${marker.description} tras pulsar $label. Pantalla:\n" + compose.onAllNodes(isRoot()).printToString(maxDepth = 30), e)
        }
    }

    /** Espera a ver [matcher]; si no aparece, el error incluye lo que había en pantalla. */
    private fun see(matcher: SemanticsMatcher, timeoutMs: Long = 5_000) {
        try {
            compose.waitUntilAtLeastOneExists(matcher, timeoutMs)
        } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
            throw AssertionError("No aparece ${matcher.description}. Pantalla:\n" + compose.onAllNodes(isRoot()).printToString(maxDepth = 30), e)
        }
    }

    private val movementsScreen = hasContentDescription("Ordenar por importe") and hasClickAction()

    @Test
    fun recorreTodasLasPantallas() {
        skipOnboarding()
        see(hasContentDescription("Añadir gasto") and hasClickAction())
        openTab("Actividad", movementsScreen)
        openTab("Análisis", hasText("Calendario"))
        compose.onNodeWithText("Calendario").performClick()
        see(hasText("Calendario de gasto"), 5_000)
        compose.onNodeWithText("Año").performClick()
        compose.waitForIdle()
        openTab("Más", hasText("Cargos fijos"))
        compose.onNodeWithText("Cargos fijos").performClick()
        see(hasText("Gastos fijos al mes"), 5_000)
        compose.onNodeWithContentDescription("Volver").performClick()
        see(hasText("Límites") and hasClickAction(), 5_000)
        compose.onAllNodes(hasText("Límites") and hasClickAction()).onFirst().performClick()
        see(hasText("Presupuesto total del mes"), 5_000)
        compose.onNodeWithContentDescription("Volver").performClick()
        // Con Casa y coche, Categorías queda más abajo en Más: se baja hasta ella
        see(hasText("Límites") and hasClickAction(), 5_000)
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Categorías") and hasClickAction())
        see(hasText("Categorías") and hasClickAction(), 5_000)
        compose.onAllNodes(hasText("Categorías") and hasClickAction()).onFirst().performClick()
        see(hasContentDescription("Subir Ocio") and hasClickAction(), 5_000)
        see(hasText("+ Nueva categoría de ingresos") and hasClickAction(), 5_000)
        compose.onNodeWithContentDescription("Volver").performClick()
        openTab("Inicio", hasContentDescription("Ajustes"))
        compose.onNodeWithContentDescription("Ajustes").performClick()
        see(hasText("Detección automática de pagos"), 5_000)
        compose.onNodeWithText("Ayuda").performClick()
        see(hasText("No se detectan mis pagos con el móvil"), 5_000)
    }

    /** El editor de gastos ofrece crear una categoría sin salir de él. */
    @Test
    fun elEditorOfreceCrearCategoria() {
        skipOnboarding()
        see(hasContentDescription("Añadir gasto") and hasClickAction())
        compose.onAllNodes(hasContentDescription("Añadir gasto") and hasClickAction()).onFirst().performClick()
        see(hasContentDescription("Nueva categoría") and hasClickAction(), 5_000)
        compose.onAllNodes(hasContentDescription("Nueva categoría") and hasClickAction()).onFirst().performClick()
        see(hasText("Color"), 5_000)
    }

    /**
     * Lo que se puede pulsar en las pantallas principales tiene un nombre que TalkBack puede leer
     * (texto o descripción) y un tamaño suficiente para el dedo.
     */
    @Test
    fun todoLoPulsableTieneNombreYTamano() {
        compose.onNodeWithText("Probar con datos de ejemplo").performClick()
        see(hasText("Estás viendo datos de ejemplo"), 10_000)
        val problems = mutableListOf<String>()
        fun check(screen: String) {
            compose.waitForIdle()
            val density = compose.activity.resources.displayMetrics.density
            compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().forEach { node ->
                val c = node.config
                val text = c.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)?.joinToString { it.text }.orEmpty()
                val desc = c.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription)?.joinToString().orEmpty()
                val editable = c.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.EditableText) != null
                val w = node.size.width / density
                val h = node.size.height / density
                if (text.isBlank() && desc.isBlank() && !editable) problems += "$screen: botón sin nombre (${w.toInt()}×${h.toInt()} dp)"
                if (w > 0 && h > 0 && (w < 32 || h < 32)) problems += "$screen: «${text.ifBlank { desc }}» es pequeño (${w.toInt()}×${h.toInt()} dp)"
            }
        }
        check("Inicio")
        openTab("Actividad", movementsScreen); check("Actividad")
        openTab("Análisis", hasText("Calendario")); check("Análisis")
        openTab("Más", hasText("Cargos fijos")); check("Más")
        org.junit.Assert.assertTrue(problems.distinct().joinToString("\n"), problems.isEmpty())
    }

    /** Error corregido: tras abrir una categoría desde Inicio, pulsar «Inicio» volvía a abrir Actividad. */
    @Test
    fun inicioVuelveAInicioTrasAbrirUnaCategoria() {
        compose.onNodeWithText("Probar con datos de ejemplo").performClick()
        see(hasText("Estás viendo datos de ejemplo"), 10_000)
        val homeList = compose.onAllNodes(hasScrollAction()).onFirst()
        // «Ver todos» de los últimos movimientos abre Actividad desde Inicio (como tocar una categoría),
        // y existe con cualquier fecha (las categorías del mes dependen del día en que se ejecute)
        // El aviso de ejemplo sale antes de que terminen de guardarse todos los gastos: se espera a que estén
        compose.waitUntil(10_000) { runCatching { homeList.performScrollToNode(hasText("Ver todos") and hasClickAction()) }.isSuccess }
        compose.onAllNodes(hasText("Ver todos") and hasClickAction()).onFirst().performClick()
        see(movementsScreen, 5_000)
        fun tapTabOnce(label: String) {
            compose.onAllNodes(hasText(label) and hasClickAction()).onFirst().performClick()
            compose.mainClock.advanceTimeBy(1_000)
            compose.waitForIdle()
        }
        // Inicio vuelve donde estaba (puede estar desplazada): cualquier parte de Inicio vale
        val home = hasContentDescription("Ajustes") or hasText("Gasto por categoría") or hasText("Últimos movimientos") or
            hasText("Tu plan") or (hasText("Ver todos") and hasClickAction())
        tapTabOnce("Inicio")
        see(home, 5_000)
        org.junit.Assert.assertTrue(
            "Al pulsar Inicio se ha abierto Actividad",
            compose.onAllNodes(movementsScreen).fetchSemanticsNodes().isEmpty(),
        )
        // Y lo mismo pasando por otras pestañas
        tapTabOnce("Análisis")
        tapTabOnce("Inicio")
        see(home, 5_000)
        org.junit.Assert.assertTrue(compose.onAllNodes(movementsScreen).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun apuntarRapidoApareceEnMovimientos() {
        skipOnboarding()
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("12,50 cafetería luna")
        compose.onAllNodes(hasSetTextAction()).onFirst().performImeAction()
        openTab("Actividad", movementsScreen)
        see(hasText("Cafetería luna"), 5_000)
    }

    @Test
    fun buscarEnTodosLosMesesYAbrirViajes() {
        skipOnboarding()
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("8,40 panadería rosa")
        compose.onAllNodes(hasSetTextAction()).onFirst().performImeAction()
        compose.onNodeWithContentDescription("Buscar en todos los meses").performClick()
        see(hasText("Busca en todos tus meses", substring = true), 5_000)
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("rosa <10")
        see(hasText("Panadería rosa", substring = true), 5_000)
        compose.onNodeWithContentDescription("Volver").performClick()
        see(hasText("Gasto por categoría"), 5_000)
        // La tarjeta está más abajo en Inicio: la lista solo compone lo visible
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("Viajes y eventos"))
        compose.onNodeWithText("Viajes y eventos").performClick()
        see(hasText("Unas vacaciones, una boda", substring = true), 5_000)
        see(hasContentDescription("Nuevo") and hasClickAction(), 5_000)
    }

    @Test
    fun datosDeEjemploDesdeLaBienvenida() {
        compose.onNodeWithText("Probar con datos de ejemplo").performClick()
        see(hasText("Estás viendo datos de ejemplo"), 10_000)
    }

    @Test
    fun configuracionPorPasosHastaEntrar() {
        compose.onNodeWithText("Empezar la configuración").performClick()
        for (step in 1..5) {
            see(hasText("Paso $step de 5"), 5_000)
            compose.onNodeWithText("Siguiente").performClick()
        }
        see(hasText("Entrar en MiDinero"), 5_000)
        compose.onNodeWithText("Entrar en MiDinero").performClick()
        see(hasText("Gasto por categoría"), 10_000)
    }
}
