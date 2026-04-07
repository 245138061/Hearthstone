package com.bgtactician.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF2C66D),
    onPrimary = Color(0xFF08111A),
    secondary = Color(0xFF4AB0C2),
    tertiary = Color(0xFF1C8B72),
    background = Color(0xFF071017),
    surface = Color(0xFF0F1B28),
    onSurface = Color(0xFFF3F7FB),
    onSurfaceVariant = Color(0xFF9FB1C4)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF8C5D0F),
    onPrimary = Color.White,
    secondary = Color(0xFF005E73),
    tertiary = Color(0xFF006B57),
    background = Color(0xFFF4F7FA),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF16202A),
    onSurfaceVariant = Color(0xFF5A6B79)
)

@Composable
fun BGTacticianTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        content = content
    )
}
