package com.midinero.control.wear

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.Typography

/** Colores de MiDinero en el reloj: fondo negro (ahorra batería en OLED), tinta y lima. */
object W {
    val Black = Color(0xFF000000)
    val Ink = Color(0xFF141318)
    val Card = Color(0xFF1C1B22)
    val CardHigh = Color(0xFF26252E)
    val Lime = Color(0xFFD4F54A)
    val OnLime = Color(0xFF141318)
    val White = Color(0xFFFFFFFF)
    val Soft = Color(0xA3FFFFFF)
    val Faint = Color(0x5CFFFFFF)
    val Track = Color(0x24FFFFFF)
    val Coral = Color(0xFFFF7A7F)
    val Amber = Color(0xFFFFC857)
}

val Jakarta = FontFamily(
    Font(R.font.jakarta_regular, FontWeight.Normal),
    Font(R.font.jakarta_medium, FontWeight.Medium),
    Font(R.font.jakarta_semibold, FontWeight.SemiBold),
    Font(R.font.jakarta_bold, FontWeight.Bold),
    Font(R.font.jakarta_extrabold, FontWeight.ExtraBold),
)

/** Cifras con números tabulares, como en el móvil. */
fun money(size: TextUnit, color: Color = W.White, weight: FontWeight = FontWeight.ExtraBold) =
    TextStyle(fontFamily = Jakarta, fontWeight = weight, fontSize = size, color = color, fontFeatureSettings = "tnum")

fun label(size: TextUnit = 12.sp, color: Color = W.Soft, weight: FontWeight = FontWeight.Medium) =
    TextStyle(fontFamily = Jakarta, fontWeight = weight, fontSize = size, color = color)

@Composable
fun WatchTheme(content: @Composable () -> Unit) {
    val base = Typography(defaultFontFamily = Jakarta)
    MaterialTheme(
        colors = Colors(
            primary = W.Lime, onPrimary = W.OnLime, secondary = W.Lime, onSecondary = W.OnLime,
            background = W.Black, onBackground = W.White, surface = W.Card, onSurface = W.White, error = W.Coral,
        ),
        typography = base,
        content = content,
    )
}

/** Escala para que todo quepa igual en relojes pequeños y grandes (diseñado para 225 dp). */
@Composable
fun scale(): Float = (LocalConfiguration.current.screenWidthDp / 225f).coerceIn(0.85f, 1.25f)

@Composable
fun Dp.s(): Dp = this * scale()

/** Emoji de la categoría en un círculo de su color. */
@Composable
fun EmojiDot(emoji: String, color: Color, size: Dp = 30.dp) {
    Box(Modifier.size(size).background(color.copy(alpha = 0.22f), CircleShape), contentAlignment = Alignment.Center) {
        Text(emoji, fontSize = (size.value * 0.5f).sp)
    }
}
