package com.enmanuelgil.androidsecurity.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Color palette ──────────────────────────────────────
val BgDark       = Color(0xFF07070F)
val BgSurface    = Color(0xFF0D0D1B)
val BgRaised     = Color(0xFF13131F)
val PrimaryBlue  = Color(0xFF5B8DEF)
val AccentPurple = Color(0xFF7C5CEF)
val AccentRed    = Color(0xFFEF5B5B)
val AccentGreen  = Color(0xFF3DDC84)
val TextPrimary  = Color(0xFFEEEEF8)
val TextSecond   = Color(0xFF9898B8)
val TextThird    = Color(0xFF5A5A7A)
val BorderColor  = Color(0x12FFFFFF)

private val DarkColors = darkColorScheme(
    primary          = PrimaryBlue,
    secondary        = AccentPurple,
    tertiary         = AccentGreen,
    background       = BgDark,
    surface          = BgSurface,
    surfaceVariant   = BgRaised,
    onBackground     = TextPrimary,
    onSurface        = TextPrimary,
    onPrimary        = Color.White,
    error            = AccentRed,
    outline          = BorderColor,
)

@Composable
fun AndroidSecurityTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography  = Typography,
        content     = content
    )
}
