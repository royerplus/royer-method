package com.royermethod.app.editor.modelo

/** Medidas relativas al lado corto del elemento, antes de proyectarlo. */
data class MarcoVisual(
    val ancho: Float = 0f,
    val radio: Float = 0f,
    val color: Int = -1,
    val neon: Float = 0f,
    val sombra: Float = 0f,
    val colorSombra: Int = 0xFF000000.toInt(),
    val suavidad: Float = 0.04f,
    val distancia: Float = 0.02f,
    val angulo: Float = 90f
) {
    val activo: Boolean get() = ancho > 0f || radio > 0f || neon > 0f || sombra > 0f
}
