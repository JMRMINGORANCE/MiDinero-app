package com.midinero.control.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.midinero.control.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt

// Identidad de MiDinero: «tinta y lima». Fondo de piedra cálida, tarjetas blancas, tinta casi negra
// para lo importante y un verde lima eléctrico para destacar. No se usa el color dinámico del sistema.
val Ink = Color(0xFF141318)
val Lime = Color(0xFFD4F54A)
private val LimeSoft = Color(0xFFEAFBA6)

internal val LightColors = lightColorScheme(
    primary = Ink,
    onPrimary = Color.White,
    primaryContainer = LimeSoft,
    onPrimaryContainer = Ink,
    secondary = Color(0xFF6B6873),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFECE9E3),
    onSecondaryContainer = Ink,
    tertiary = Color(0xFF6C4DFF),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFECE7FF),
    onTertiaryContainer = Color(0xFF231566),
    background = Color(0xFFF4F2EE),
    onBackground = Ink,
    surface = Color(0xFFF4F2EE),
    onSurface = Ink,
    surfaceVariant = Color(0xFFE8E4DE),
    onSurfaceVariant = Color(0xFF6B6873),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFBFAF8),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFEFECE7),
    surfaceContainerHighest = Color(0xFFE6E2DB),
    outline = Color(0xFFCFCAC2),
    outlineVariant = Color(0xFFE9E5DF),
    error = Color(0xFFE5484D),
    errorContainer = Color(0xFFFFE4E3),
    onErrorContainer = Color(0xFF5C0A10),
)

internal val DarkColors = darkColorScheme(
    primary = Lime,
    onPrimary = Ink,
    primaryContainer = Color(0xFF37400F),
    onPrimaryContainer = Color(0xFFEAFBA6),
    secondary = Color(0xFFA6A3AF),
    onSecondary = Ink,
    secondaryContainer = Color(0xFF26262E),
    onSecondaryContainer = Color(0xFFE8E6EE),
    tertiary = Color(0xFFB7A6FF),
    onTertiary = Color(0xFF1B1244),
    tertiaryContainer = Color(0xFF2A2350),
    onTertiaryContainer = Color(0xFFE6DFFF),
    background = Color(0xFF0C0C0F),
    onBackground = Color(0xFFF2F1F4),
    surface = Color(0xFF0C0C0F),
    onSurface = Color(0xFFF2F1F4),
    surfaceVariant = Color(0xFF1F1F26),
    onSurfaceVariant = Color(0xFF9C99A6),
    surfaceContainerLowest = Color(0xFF17171C),
    surfaceContainerLow = Color(0xFF141418),
    surfaceContainer = Color(0xFF17171C),
    surfaceContainerHigh = Color(0xFF212128),
    surfaceContainerHighest = Color(0xFF2A2A32),
    outline = Color(0xFF4A4852),
    outlineVariant = Color(0xFF26252C),
    error = Color(0xFFFF7A7F),
    errorContainer = Color(0xFF4A1417),
    onErrorContainer = Color(0xFFFFDADB),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

/** Plus Jakarta Sans (licencia OFL), incluida en la app: geométrica, moderna y con buenas cifras. */
val Jakarta = FontFamily(
    Font(R.font.jakarta_regular, FontWeight.Normal),
    Font(R.font.jakarta_medium, FontWeight.Medium),
    Font(R.font.jakarta_semibold, FontWeight.SemiBold),
    Font(R.font.jakarta_bold, FontWeight.Bold),
    Font(R.font.jakarta_extrabold, FontWeight.ExtraBold),
    Font(R.font.jakarta_extrabold, FontWeight.Black),
)

private val base = Typography()
private fun TextStyle.jakarta(weight: FontWeight, spacing: Float = 0f) =
    copy(fontFamily = Jakarta, fontWeight = weight, letterSpacing = spacing.sp)

private val AppTypography = Typography(
    displayLarge = base.displayLarge.jakarta(FontWeight.ExtraBold, -1.5f),
    displayMedium = base.displayMedium.jakarta(FontWeight.ExtraBold, -1.2f),
    displaySmall = base.displaySmall.jakarta(FontWeight.ExtraBold, -1f),
    headlineLarge = base.headlineLarge.jakarta(FontWeight.ExtraBold, -0.8f),
    headlineMedium = base.headlineMedium.jakarta(FontWeight.ExtraBold, -0.6f),
    headlineSmall = base.headlineSmall.jakarta(FontWeight.ExtraBold, -0.4f),
    titleLarge = base.titleLarge.jakarta(FontWeight.Bold, -0.2f),
    titleMedium = base.titleMedium.jakarta(FontWeight.Bold),
    titleSmall = base.titleSmall.jakarta(FontWeight.Bold),
    bodyLarge = base.bodyLarge.jakarta(FontWeight.Medium),
    bodyMedium = base.bodyMedium.jakarta(FontWeight.Normal),
    bodySmall = base.bodySmall.jakarta(FontWeight.Normal),
    labelLarge = base.labelLarge.jakarta(FontWeight.Bold),
    labelMedium = base.labelMedium.jakarta(FontWeight.SemiBold),
    labelSmall = base.labelSmall.jakarta(FontWeight.SemiBold),
)

/** Colores de estado para límites y presupuestos (siempre acompañados de texto). */
object BudgetColors {
    val ok = Color(0xFF1FA971)
    val warning = Color(0xFFF08C00)
    val over = Color(0xFFE5484D)
}

/** true si la app se está pintando en modo oscuro (por el ajuste de la app o el del sistema). */
val LocalDarkTheme = staticCompositionLocalOf { false }

/** Fondo de las cabeceras: tinta casi negra (un poco más clara en modo oscuro para que se distinga del fondo). */
object AppBrushes {
    val hero: Brush
        @Composable @ReadOnlyComposable
        get() = if (LocalDarkTheme.current) {
            Brush.linearGradient(listOf(Color(0xFF26262F), Color(0xFF18181E)))
        } else {
            Brush.linearGradient(listOf(Color(0xFF23222A), Ink))
        }
}

/** Estilo de las cifras: negrita, un poco más juntas y con dígitos del mismo ancho (no bailan al animarse). */
val MoneyStyle = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp, fontFeatureSettings = "tnum")

/** [mode]: "system", "light" o "dark". */
@Composable
fun MiDineroTheme(mode: String = "system", content: @Composable () -> Unit) {
    val dark = when (mode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    CompositionLocalProvider(LocalDarkTheme provides dark) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

fun parseHexColor(hex: String, fallback: Color = Color(0xFF495057)): Color =
    runCatching { Color(hex.toColorInt()) }.getOrDefault(fallback)

/** Colores de los widgets: los de la app, con fondo blanco en claro y tinta en oscuro. */
val WidgetColors = androidx.glance.material3.ColorProviders(
    light = LightColors.copy(surface = Color.White, onSurface = Ink),
    dark = DarkColors.copy(surface = Color(0xFF17171C)),
)
