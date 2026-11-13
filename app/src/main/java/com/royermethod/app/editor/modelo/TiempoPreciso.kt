package com.royermethod.app.editor.modelo

import kotlin.math.roundToLong

/**
 * Tiempos escritos y leidos con precision de microsegundos.
 *
 * Todo el editor guarda microsegundos enteros, asi que 0,005 s y 0,001 s ya
 * caben sin perder nada. Lo que faltaba era poder escribirlos, verlos con
 * sus decimales y elegir si se pegan a los fotogramas de 30 fps o no.
 */
object TiempoPreciso {

    /**
     * Para ensenar: "0.005 s", "1.250 s". Un fotograma de 30 fps que no cae en
     * un milisegundo exacto (1,0666... s) sale con tres decimales y "≈"
     * delante ("≈1.067 s"): es el fotograma, no 1,067 s escrito a mano. Lo
     * demas con microsegundos sueltos, con seis decimales ("0.001500 s").
     * Para escribirlo y leerlo igual, [textoExacto].
     */
    fun texto(us: Long): String {
        val v = us.coerceAtLeast(0)
        if (v % 1_000L != 0L && esFotograma(v)) return "≈%.3f s".format(java.util.Locale.ROOT, v / 1_000_000.0)
        return textoExacto(v)
    }

    /** "0.005 s", "1.250 s"; si quedan microsegundos sueltos, con seis decimales. [leer] lo lee igual. */
    fun textoExacto(us: Long): String {
        val v = us.coerceAtLeast(0)
        return if (v % 1_000L == 0L) "%.3f s".format(java.util.Locale.ROOT, v / 1_000_000.0)
        else "%.6f s".format(java.util.Locale.ROOT, v / 1_000_000.0)
    }

    /**
     * ¿Es [us] el instante de un fotograma de [Linea.FPS] que no cae en un
     * milisegundo (dos de cada tres)? Con un microsegundo de margen: un tiempo
     * dentro del bloque es la resta de dos fotogramas de la linea y puede
     * salir uno por encima (0,100000 - 0,033333 = 0,066667). Los que caen en
     * un milisegundo no cuentan: 0,000001 s es un tiempo escrito, no el 0.
     */
    private fun esFotograma(us: Long): Boolean {
        val n = (us * Linea.FPS / 1_000_000.0).roundToLong()
        return n % 3L != 0L && kotlin.math.abs(us - Linea.tiempoDe(n)) <= 1L
    }

    /**
     * Lee lo que escribe una persona: "0,005", "0.005", "0.005 s", "5 ms",
     * "1,2s". Null si no es un tiempo o es negativo. Redondea al microsegundo.
     */
    fun leer(texto: String): Long? {
        val t = texto.trim().lowercase().replace(',', '.').replace(" ", "")
        if (t.isEmpty()) return null
        val (numero, factor) = when {
            t.endsWith("ms") -> t.removeSuffix("ms") to 1_000.0
            t.endsWith("us") || t.endsWith("µs") -> t.removeSuffix("us").removeSuffix("µs") to 1.0
            t.endsWith("s") -> t.removeSuffix("s") to 1_000_000.0
            else -> t to 1_000_000.0
        }
        val v = numero.toDoubleOrNull() ?: return null
        if (!v.isFinite() || v < 0.0) return null
        val us = v * factor
        if (us > Long.MAX_VALUE / 2) return null
        return us.roundToLong()
    }

    /** Al fotograma de [Linea.FPS] mas cercano. */
    fun aFotograma(us: Long): Long =
        Linea.tiempoDe((us.coerceAtLeast(0) * Linea.FPS / 1_000_000.0).roundToLong())

    /** Pegado a los fotogramas solo si se pide; nunca negativo. */
    fun ajustar(us: Long, aFotogramas: Boolean): Long =
        if (aFotogramas) aFotograma(us) else us.coerceAtLeast(0)
}
