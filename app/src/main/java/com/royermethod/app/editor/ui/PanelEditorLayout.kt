package com.royermethod.app.editor.ui

import androidx.compose.runtime.staticCompositionLocalOf

/** Tamaño del panel inferior; ausente al dibujar una hoja fuera del editor. */
data class TamanoPanelEditor(val ampliado: Boolean, val cambiar: () -> Unit)

val LocalTamanoPanelEditor = staticCompositionLocalOf<TamanoPanelEditor?> { null }
