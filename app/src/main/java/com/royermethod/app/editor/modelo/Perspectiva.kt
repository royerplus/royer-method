package com.royermethod.app.editor.modelo

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Perspectiva de un bloque: lo inclina en 3D como una tarjeta vista por una
 * camara. La camara esta a [FOCO] veces el lado mayor del bloque, asi la
 * forma que sale depende solo de los angulos (igual de grande o de chico,
 * y lo mismo en el dibujo del panel que en el video).
 *
 * Todo son homografias 3x3 en filas: [a b c; d e f; g h i].
 */
object Perspectiva {
    /** Lo mas que se inclina, en grados (mas alla la tarjeta casi se ve de canto). */
    const val MAXIMA = 70f
    const val FOCO = 2f

    /**
     * Del punto del bloque centrado (u, v en pixeles, el centro en 0) al
     * plano de la imagen, todavia centrado y sin el giro normal del bloque.
     */
    fun proyeccion(inclinacionX: Float, inclinacionY: Float, w: Float, h: Float): FloatArray {
        val ax = Math.toRadians(inclinacionX.coerceIn(-MAXIMA, MAXIMA).toDouble())
        val ay = Math.toRadians(inclinacionY.coerceIn(-MAXIMA, MAXIMA).toDouble())
        val cx = cos(ax).toFloat(); val sx = sin(ax).toFloat()
        val cy = cos(ay).toFloat(); val sy = sin(ay).toFloat()
        val f = FOCO * max(max(w, h), 1e-3f)
        // Columnas de Ry * Rx para los ejes u y v del plano (z = 0)
        val r00 = cy; val r10 = 0f; val r20 = -sy
        val r01 = sy * sx; val r11 = cx; val r21 = cy * sx
        // x' = f X / (f + Z): dividido por f para que la esquina de abajo sea 1
        return floatArrayOf(r00, r01, 0f, r10, r11, 0f, r20 / f, r21 / f, 1f)
    }

    /** Donde caen las 4 esquinas (arriba izq, arriba der, abajo der, abajo izq), centradas. */
    fun esquinas(inclinacionX: Float, inclinacionY: Float, w: Float, h: Float): List<Pair<Float, Float>> {
        val m = proyeccion(inclinacionX, inclinacionY, w, h)
        return listOf(-1f to -1f, 1f to -1f, 1f to 1f, -1f to 1f).map { (sx, sy) ->
            aplicar(m, sx * w / 2, sy * h / 2)
        }
    }

    fun aplicar(m: FloatArray, x: Float, y: Float): Pair<Float, Float> {
        val z = m[6] * x + m[7] * y + m[8]
        return (m[0] * x + m[1] * y + m[2]) / z to (m[3] * x + m[4] * y + m[5]) / z
    }

    fun por(a: FloatArray, b: FloatArray): FloatArray = FloatArray(9) { k ->
        val i = k / 3; val j = k % 3
        a[i * 3] * b[j] + a[i * 3 + 1] * b[3 + j] + a[i * 3 + 2] * b[6 + j]
    }

    /** Inversa de una 3x3, o null si no la tiene. */
    fun inversa(m: FloatArray): FloatArray? {
        val (a, b, c) = Triple(m[0].toDouble(), m[1].toDouble(), m[2].toDouble())
        val (d, e, f) = Triple(m[3].toDouble(), m[4].toDouble(), m[5].toDouble())
        val (g, h, i) = Triple(m[6].toDouble(), m[7].toDouble(), m[8].toDouble())
        val A = e * i - f * h; val B = -(d * i - f * g); val C = d * h - e * g
        val det = a * A + b * B + c * C
        if (kotlin.math.abs(det) < 1e-18 || !det.isFinite()) return null
        val r = doubleArrayOf(
            A, -(b * i - c * h), b * f - c * e,
            B, a * i - c * g, -(a * f - c * d),
            C, -(a * h - b * g), a * e - b * d
        )
        return FloatArray(9) { (r[it] / det).toFloat() }
    }
}
