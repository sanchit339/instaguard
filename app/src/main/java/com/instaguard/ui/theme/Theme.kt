package com.instaguard.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF0B6E4F),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF355070),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFFF77F00),
    background = Color(0xFFF2F7F4),
    onBackground = Color(0xFF0E1B16),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF14211D)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF66D19E),
    onPrimary = Color(0xFF053021),
    secondary = Color(0xFFAFC6E9),
    onSecondary = Color(0xFF102236),
    tertiary = Color(0xFFFFC971),
    background = Color(0xFF0E1412),
    onBackground = Color(0xFFE3EFEA),
    surface = Color(0xFF17201C),
    onSurface = Color(0xFFE0ECE7)
)

@Composable
fun InstaGuardTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
