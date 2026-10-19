package com.royermethod.app.editor.ui

import com.royermethod.app.editor.modelo.EfectoVideo
import com.royermethod.app.editor.modelo.ProgresoEfecto
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToLong

/** Un efecto acelerado puede terminar con menos de un fotograma disponible. */
internal fun minimoFinEfecto(inicioUs: Long, pasoUs: Long, duracionUs: Long): Long =
    (inicioUs + pasoUs).coerceAtMost(duracionUs)

/** Mantiene validos los limites de deslizador, botones y cabezal. */
internal fun acotarBordeEfecto(valorUs: Long, minimoUs: Long, maximoUs: Long): Long =
    valorUs.coerceIn(minimoUs, maxOf(minimoUs, maximoUs))

private fun EfectoVideo.ritmoControl(): Double =
    (rapidez.takeIf { it.isFinite() }?.coerceIn(.1f, 10f) ?: 1f).toDouble()

internal fun EfectoVideo.faseControl(localUs: Long): Long {
    val base = duracionBaseUs.takeIf { it > 0 } ?: duracionUs
    return ((localUs - inicioUs + desfaseUs).toDouble() * ritmoControl())
        .roundToLong().coerceIn(0L, base)
}

/** Redondea hacia el primer microsegundo que alcanza el punto medido. */
internal fun EfectoVideo.localDelPunto(punto: ProgresoEfecto): Long =
    ceil(punto.tiempoUs.toDouble() / ritmoControl() - desfaseUs.toDouble()).toLong() + inicioUs

/** La vuelta por el reloj entero puede diferir hasta ceil(rapidez) microsegundos. */
internal fun EfectoVideo.puntoEnFase(faseUs: Long): ProgresoEfecto? =
    progresos.minByOrNull { abs(it.tiempoUs - faseUs) }
        ?.takeIf { abs(it.tiempoUs - faseUs) <= ceil(ritmoControl()).toLong() }
