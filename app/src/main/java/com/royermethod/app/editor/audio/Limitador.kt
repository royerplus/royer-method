package com.royermethod.app.editor.audio

import kotlin.math.abs
import kotlin.math.exp

/**
 * Evita que la mezcla se pase de 0 dB.
 *
 * Dos clips a todo volumen sumados se salen del rango, y recortar a lo bruto
 * suena a chasquido. Este baja el volumen justo antes de cada pico (mira unos
 * milisegundos por delante) y lo devuelve poco a poco, asi que no se oye que
 * actua. Por debajo del umbral no toca nada: la mezcla sale identica.
 *
 * Va con retraso: lo que entra sale [LATENCIA] frames despues.
 */
class Limitador(private val umbral: Float = 0.966f) {

    private val anticipo = ANTICIPO
    // Retraso de la senal y de la ganancia necesaria
    private val linea = FloatArray(LATENCIA * Pcm.CANALES)
    private val necesaria = FloatArray(anticipo * 2) { 1f }
    private var pos = 0L
    private var ganancia = 1f
    /** Cuanto se recupera por frame: unos 80 ms en volver. */
    private val recuperar = (1.0 - exp(-1.0 / (0.08 * Pcm.HZ))).toFloat()
    // Media movil de la ganancia minima, para que bajar tampoco sea un escalon
    private val minimos = FloatArray(anticipo) { 1f }
    private var sumaMinimos = anticipo.toFloat()

    /** Procesa en el sitio [frames] pares de muestras de [datos]. */
    fun procesar(datos: FloatArray, frames: Int) {
        for (f in 0 until frames) {
            val l = datos[f * 2]
            val r = datos[f * 2 + 1]
            val pico = maxOf(abs(l), abs(r))
            val req = if (pico > umbral) umbral / pico else 1f
            necesaria[(pos % necesaria.size).toInt()] = req

            // Lo minimo que hace falta en el ultimo anticipo, este incluido
            var minimo = 1f
            for (j in 0..anticipo) {
                val v = necesaria[((pos - j + necesaria.size) % necesaria.size).toInt()]
                if (v < minimo) minimo = v
            }
            val im = (pos % anticipo).toInt()
            sumaMinimos += minimo - minimos[im]
            minimos[im] = minimo
            val objetivo = (sumaMinimos / anticipo).coerceAtMost(1f)

            ganancia = if (objetivo < ganancia) objetivo else ganancia + (objetivo - ganancia) * recuperar

            // Sale lo que entro hace LATENCIA frames, con la ganancia de ahora
            val il = ((pos % LATENCIA) * 2).toInt()
            val sl = linea[il]
            val sr = linea[il + 1]
            linea[il] = l
            linea[il + 1] = r
            datos[f * 2] = (sl * ganancia).coerceIn(-1f, 1f)
            datos[f * 2 + 1] = (sr * ganancia).coerceIn(-1f, 1f)
            pos++
        }
    }

    companion object {
        /** 2.5 ms de anticipo. */
        const val ANTICIPO = 120
        /**
         * Retraso total. Un pico entra en el minimo en cuanto llega y la media
         * movil tarda [ANTICIPO] en bajar del todo; la senal se retrasa lo
         * mismo, asi que el pico sale justo cuando la ganancia ya esta abajo.
         */
        const val LATENCIA = ANTICIPO
    }
}
