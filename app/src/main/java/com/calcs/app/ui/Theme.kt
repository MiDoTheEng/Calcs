package com.calcs.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Immutable
data class CalcColors(
    val bg: Color, val surface: Color, val surface2: Color, val ink: Color, val muted: Color, val line: Color,
    val inBg: Color, val inLine: Color, val resBg: Color, val keyBg: Color, val keyInk: Color,
    val ok: Color, val bad: Color, val head: Color, val vGood: Color, val vOk: Color, val vBad: Color
)

private val Light = CalcColors(
    bg = Color(0xFFEEF1F5), surface = Color.White, surface2 = Color(0xFFF6F8FB), ink = Color(0xFF17202B), muted = Color(0xFF5B6878), line = Color(0xFFDCE2EA),
    inBg = Color(0xFFFFF8E1), inLine = Color(0xFFE6D28C), resBg = Color(0xFFF1F8F3), keyBg = Color(0xFFDEF1E3), keyInk = Color(0xFF145A2C),
    ok = Color(0xFF1D7A3A), bad = Color(0xFFB3261E), head = Color(0xFF27364A), vGood = Color(0xFF2E9E5B), vOk = Color(0xFFD99A00), vBad = Color(0xFFD23C3C)
)
private val Dark = CalcColors(
    bg = Color(0xFF0E1318), surface = Color(0xFF161D25), surface2 = Color(0xFF1B242E), ink = Color(0xFFE6ECF2), muted = Color(0xFF93A1B1), line = Color(0xFF2A3542),
    inBg = Color(0xFF2A2513), inLine = Color(0xFF5C4F22), resBg = Color(0xFF15221B), keyBg = Color(0xFF173A25), keyInk = Color(0xFF9BE0B0),
    ok = Color(0xFF5CCF83), bad = Color(0xFFFF8A80), head = Color(0xFF2C3B50), vGood = Color(0xFF3CC47A), vOk = Color(0xFFF2C14E), vBad = Color(0xFFFF6B5E)
)

val LocalCalc = staticCompositionLocalOf { Light }

object T {
    val num = TextStyle(fontFeatureSettings = "tnum")
    val label = TextStyle(fontSize = 15.sp)
    val small = TextStyle(fontSize = 12.5.sp)
    val section = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold)
}

/** Lightens an accent for dark mode so it stays readable. */
fun accentFor(argb: Long, dark: Boolean): Color {
    val c = Color(argb)
    if (!dark) return c
    return Color(red = c.red + (1 - c.red) * 0.35f, green = c.green + (1 - c.green) * 0.35f, blue = c.blue + (1 - c.blue) * 0.35f)
}

@Composable
fun CalcsTheme(accent: Long = 0xFF27364A, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val cc = if (dark) Dark else Light
    val a = accentFor(accent, dark)
    val scheme = if (dark) darkColorScheme(primary = a, onPrimary = Color.Black, background = cc.bg, surface = cc.surface, onSurface = cc.ink, onBackground = cc.ink,
        surfaceVariant = cc.surface2, onSurfaceVariant = cc.muted, outline = cc.line, secondaryContainer = a.copy(alpha = 0.25f))
    else lightColorScheme(primary = a, onPrimary = Color.White, background = cc.bg, surface = cc.surface, onSurface = cc.ink, onBackground = cc.ink,
        surfaceVariant = cc.surface2, onSurfaceVariant = cc.muted, outline = cc.line, secondaryContainer = a.copy(alpha = 0.14f))
    CompositionLocalProvider(LocalCalc provides cc) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
