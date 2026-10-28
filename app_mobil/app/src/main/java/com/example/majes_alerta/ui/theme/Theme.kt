package com.example.majes_alerta.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = AppGreen,
    secondary = AppLime,
    background = AppPaper,
    surface = AppCardBg,
    onPrimary = AppPaper,
    onSecondary = AppInk,
    onBackground = AppInk,
    onSurface = AppInk
)

@Composable
fun MAJES_ALERTATheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
