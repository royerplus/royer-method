package com.royermethod.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * La app es siempre negra. No hay modo claro: el diseno depende del
 * contraste absoluto blanco-sobre-negro, un tema claro lo destruiria.
 */
private val MonochromeScheme = darkColorScheme(
    primary = Ink.White,
    onPrimary = Ink.Black,
    secondary = Ink.White70,
    onSecondary = Ink.Black,
    background = Ink.Black,
    onBackground = Ink.White,
    surface = Ink.Black,
    onSurface = Ink.White,
    surfaceVariant = Ink.White08,
    onSurfaceVariant = Ink.White70,
    outline = Ink.White30,
    error = Ink.White,
    onError = Ink.Black
)

@Composable
fun RoyerMethodTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MonochromeScheme,
        typography = AppTypography,
        content = content
    )
}
