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

    /** "0.005 s", "1.250 s"; si quedan microsegundos sueltos, con seis decimales. */
    fun texto(us: Long): String {
        val v = us.coerceAtLeast(0)
        return if (v % 1_000L == 0L) "%.3f s".format(java.util.Locale.ROOT, v / 1_000_000.0)
        else "%.6f s".format(java.util.Locale.ROOT, v / 1_000_000.0)
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
