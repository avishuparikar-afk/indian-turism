package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = SaffronLight,
    onPrimary = Color.Black,
    primaryContainer = SaffronDark,
    onPrimaryContainer = Color.White,
    secondary = PeacockTeal,
    onSecondary = Color.White,
    tertiary = GoldenAmber,
    background = GlassDarkBackground,
    onBackground = GlassDarkText,
    surface = Color(0xFF131D33),
    onSurface = GlassDarkText,
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = GlassDarkTextSecondary,
    outline = Color(0xFF334155)
)

private val LightColorScheme = lightColorScheme(
    primary = SaffronPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFEDD5),
    onPrimaryContainer = SaffronDark,
    secondary = PeacockTeal,
    onSecondary = Color.White,
    tertiary = GoldenAmber,
    background = Color(0xFFF8FAFC),
    onBackground = GlassLightText,
    surface = Color(0xFFFFFFFF),
    onSurface = GlassLightText,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = GlassLightTextSecondary,
    outline = Color(0xFFCBD5E1)
)

@Composable
fun IndiaTourismTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// Backwards compatibility alias for tests
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    IndiaTourismTheme(darkTheme = darkTheme, content = content)
}
