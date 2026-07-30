package com.maquis.caisse.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = MaquisOrange,
    secondary = MaquisGreen,
    error = MaquisRed,
    background = MaquisBackground,
    surface = MaquisSurface,
    onSurface = MaquisOnSurface,
)

private val LightColors = lightColorScheme(
    primary = MaquisOrange,
    secondary = MaquisGreen,
    error = MaquisRed,
)

/** Thème central. Sombre par défaut (meilleure lisibilité de nuit/extérieur). */
@Composable
fun MaquisCaisseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, typography = MaquisTypography, content = content)
}
