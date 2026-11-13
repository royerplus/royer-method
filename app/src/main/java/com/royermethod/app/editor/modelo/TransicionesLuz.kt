package com.royermethod.app.editor.modelo

import kotlin.math.cos
import kotlin.math.sin

/** Decoración de LINEA. Los valores iniciales conservan los proyectos anteriores. */
data class LuzTransicion(
    val girar: Boolean = false,
    val anguloMedio: Float = 90f,
    val anguloFinal: Float = 0f,
    val halo: Float = 1f,
    val nucleo: Float = 1f,
    val textura: Float = 0f,
    val arcoiris: Boolean = false
) {
    /** El avance ya incluye la curva de la transición; cada tramo recorre el arco corto. */
    fun anguloEn(inicial: Float, progreso: Float): Float {
        if (!girar) return inicial
        val p = progreso.coerceIn(0f, 1f)
        return if (p <= .5f) interpolar(inicial, anguloMedio, p * 2f)
        else interpolar(anguloMedio, anguloFinal, (p - .5f) * 2f)
    }

    fun direccionEn(inicial: Float, progreso: Float): Pair<Float, Float> {
        val a = Math.toRadians(anguloEn(inicial, progreso).toDouble())
        return cos(a).toFloat() to sin(a).toFloat()
    }

    private fun interpolar(a: Float, b: Float, p: Float): Float {
        val arco = ((b - a + 180f) % 360f + 360f) % 360f - 180f
        return a + arco * p
    }
}
