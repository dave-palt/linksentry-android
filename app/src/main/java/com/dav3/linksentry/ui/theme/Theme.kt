package com.dav3.linksentry.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF86EFAC),
    secondary = Color(0xFFB8CCC4),
    tertiary = Color(0xFFFBBF24),
    background = Color(0xFF0E1113),
    surface = Color(0xFF161B1E),
    onSurface = Color(0xFFE4E9E6),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF15803D),
    secondary = Color(0xFF42554B),
    tertiary = Color(0xFFB45309),
)

@Composable
fun LinkSentryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        content = content,
    )
}
