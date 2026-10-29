package com.pangguai.breeze.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** 设计令牌：深色底 + 薄荷绿。全部色值都从这里取，不要在界面里写死颜色。 */
object Ink {
    val bg = Color(0xFF0B0F0E)
    val bgGlow = Color(0xFF0E1A15)
    val surface = Color(0xFF121816)
    val surface2 = Color(0xFF161D1A)
    val line = Color(0xFF223029)
    val line2 = Color(0xFF1B2621)
    val mint = Color(0xFF3FD69B)
    val mint2 = Color(0xFF6FE9B8)
    val mintDeep = Color(0xFF0F3B2C)
    val text = Color(0xFFE8F2ED)
    val text2 = Color(0xFFB9CCC3)
    val dim = Color(0xFF8FA79B)
    val dim2 = Color(0xFF5E7268)
    val warn = Color(0xFFE8B45A)
    val onMint = Color(0xFF06231A)
}

private val scheme = darkColorScheme(
    primary = Ink.mint,
    onPrimary = Ink.onMint,
    background = Ink.bg,
    onBackground = Ink.text,
    surface = Ink.surface,
    onSurface = Ink.text,
    surfaceVariant = Ink.surface2,
    onSurfaceVariant = Ink.dim,
    outline = Ink.line,
)

@Composable
fun BreezeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
}
