package com.royermethod.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.isSpecified

/**
 * La app es siempre oscura. No hay modo claro: el diseno depende del
 * contraste de un neon sobre fondo casi negro, un tema claro lo destruiria.
 * Lo que si cambia es el color del neon, el fondo y la letra: salen de
 * [Estilo], y al leerse aqui dentro la app entera se repinta al cambiarlos.
 */
@Composable
fun RoyerMethodTheme(content: @Composable () -> Unit) {
    val esquema = darkColorScheme(
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
        error = Ink.Peligro,
        onError = Ink.Black
    )
    MaterialTheme(
        colorScheme = esquema,
        typography = AppTypography.escalada(Estilo.letra),
        content = content
    )
}

private fun TextStyle.por(f: Float): TextStyle = copy(
    fontSize = fontSize * f,
    lineHeight = if (lineHeight.isSpecified) lineHeight * f else lineHeight
)

private fun Typography.escalada(f: Float): Typography = if (f == 1f) this else Typography(
    displayLarge = displayLarge.por(f),
    titleLarge = titleLarge.por(f),
    titleMedium = titleMedium.por(f),
    bodyLarge = bodyLarge.por(f),
    bodyMedium = bodyMedium.por(f),
    labelLarge = labelLarge.por(f),
    labelSmall = labelSmall.por(f)
)
