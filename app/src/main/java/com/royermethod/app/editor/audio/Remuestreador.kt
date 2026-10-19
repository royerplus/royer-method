package com.royermethod.app.editor.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

/**
 * Cambia la frecuencia de muestreo (44.1 kHz del movil a 48 kHz, por ejemplo).
 *
 * Con interpolacion de sinc enventanada: cada muestra nueva sale de las 32
 * originales que la rodean. Interpolar en linea recta entre dos muestras, que
 * es lo facil, apaga los agudos y mete un siseo metalico que se oye en
 * cualquier voz o platillo.
 *
 * Al bajar de frecuencia el filtro tambien baja su corte, para que lo que ya
 * no cabe no se cuele doblado como ruido.
 *
 * @param entradaHz frecuencia de [fuente]. Puede no ser entera: asi se cambia
 *   la velocidad sin conservar el tono (se lee como si fuera mas rapido).
 */
class Remuestreador(
    private val fuente: FuentePcm,
    entradaHz: Double,
    salidaHz: Int = Pcm.HZ
) : FuentePcm {

    /** Muestras de entrada que avanza cada muestra de salida. */
    private val paso = entradaHz / salidaHz
    private val directo = abs(paso - 1.0) < 1e-9

    /** Corte del filtro, en fraccion de la frecuencia de entrada (1 = su Nyquist). */
    private val corte = min(1.0, 1.0 / paso) * 0.95
    /** Alcance del filtro a cada lado, en muestras de entrada. */
    private val alcance = ceil(CRUCES / corte).toInt()
    private val tabla = FloatArray(alcance * FASES + 2).also { t ->
        for (i in t.indices) t[i] = nucleo(i.toDouble() / FASES).toFloat()
    }

    // Lo leido de la fuente que aun hace falta: frames [base, base + cuantos)
    private var buf = FloatArray(8192 * Pcm.CANALES)
    private var base = 0L
    private var cuantos = 0
    private var acabada = false
    /** Frames de entrada totales, cuando se sabe (al acabarse la fuente). */
    private var total = Long.MAX_VALUE
    private val tmp = FloatArray(1024 * Pcm.CANALES)

    /** Siguiente muestra de salida. */
    private var n = 0L

    private fun nucleo(t: Double): Double {
        val x = abs(t)
        val r = CRUCES / corte
        if (x >= r) return 0.0
        val s = if (x < 1e-9) 1.0 else sin(PI * corte * x) / (PI * corte * x)
        // Ventana de Blackman: corta el sinc sin rizos audibles
        val w = 0.42 + 0.5 * cos(PI * x / r) + 0.08 * cos(2 * PI * x / r)
        return corte * s * w
    }

    private fun h(t: Double): Float {
        val p = abs(t) * FASES
        val i = p.toInt()
        if (i + 1 >= tabla.size) return 0f
        val f = (p - i).toFloat()
        return tabla[i] + (tabla[i + 1] - tabla[i]) * f
    }

    /** Que haya en el buffer hasta el frame [hasta] (exclusivo), si la fuente da. */
    private fun asegurar(hasta: Long) {
        while (!acabada && base + cuantos < hasta) {
            val leidos = fuente.leer(tmp, tmp.size / Pcm.CANALES)
            if (leidos <= 0) {
                acabada = true
                total = base + cuantos
                break
            }
            if ((cuantos + leidos) * Pcm.CANALES > buf.size) {
                buf = buf.copyOf(maxOf(buf.size * 2, (cuantos + leidos) * Pcm.CANALES))
            }
            System.arraycopy(tmp, 0, buf, cuantos * Pcm.CANALES, leidos * Pcm.CANALES)
            cuantos += leidos
        }
    }

    /** Suelta lo que ya no va a hacer falta (todo lo anterior a [desde]). */
    private fun descartar(desde: Long) {
        val sobra = (desde - base).coerceIn(0, cuantos.toLong()).toInt()
        if (sobra < 2048) return
        System.arraycopy(buf, sobra * Pcm.CANALES, buf, 0, (cuantos - sobra) * Pcm.CANALES)
        cuantos -= sobra
        base += sobra
    }

    override fun leer(destino: FloatArray, frames: Int): Int {
        if (directo) return fuente.leer(destino, frames)
        var hechos = 0
        while (hechos < frames) {
            val x = n * paso
            val centro = floor(x).toLong()
            asegurar(centro + alcance + 1)
            if (x >= total) break
            var l = 0f
            var r = 0f
            val desde = centro - alcance + 1
            val hasta = centro + alcance
            for (k in desde..hasta) {
                if (k < base || k >= base + cuantos) continue
                val w = h(x - k)
                if (w == 0f) continue
                val j = ((k - base) * Pcm.CANALES).toInt()
                l += buf[j] * w
                r += buf[j + 1] * w
            }
            destino[hechos * 2] = l
            destino[hechos * 2 + 1] = r
            hechos++
            n++
            descartar(centro - alcance)
        }
        return hechos
    }

    override fun cerrar() = fuente.cerrar()

    private companion object {
        /** Cruces por cero del sinc a cada lado: 16 da un filtro limpio de sobra. */
        const val CRUCES = 16.0
        const val FASES = 512
    }
}
