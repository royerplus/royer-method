package com.royermethod.app.editor.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sqrt

/**
 * Cambia la velocidad del audio sin cambiar el tono (WSOLA).
 *
 * El audio se trocea en ventanas de 32 ms que se vuelven a pegar mas juntas
 * (acelerar) o mas separadas (ralentizar). Pegarlas sin mas suena a robot:
 * cada trozo empieza en una fase distinta de la onda y en las juntas se
 * cancela. Por eso cada trozo se busca, dentro de unos milisegundos de donde
 * tocaria, en el punto en que mejor continua lo que ya ha sonado.
 *
 * @param velocidad cuanto audio de entrada se consume por cada uno de salida
 */
class Estirador(
    private val fuente: FuentePcm,
    private val velocidad: Float
) : FuentePcm {

    private val directo = abs(velocidad - 1f) < 1e-4f
    /** Avance en la entrada entre ventanas. */
    private val pasoEntrada = SALTO * velocidad.toDouble()

    private val ventana = FloatArray(LARGO) { i -> (0.5 - 0.5 * cos(2 * PI * i / LARGO)).toFloat() }

    // Entrada pendiente: frames [base, base + cuantos)
    private var buf = FloatArray(LARGO * 8 * Pcm.CANALES)
    private var base = 0L
    private var cuantos = 0
    private var acabada = false
    private var total = Long.MAX_VALUE
    private val tmp = FloatArray(1024 * Pcm.CANALES)

    /** Solapamiento pendiente de sumar: la segunda mitad de la ultima ventana. */
    private val cola = FloatArray(LARGO * Pcm.CANALES)
    /** Lo listo para entregar. */
    private val listo = FloatArray(SALTO * Pcm.CANALES)
    private var listoDesde = SALTO
    private var k = 0L
    private var anterior = 0L
    private var terminado = false

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

    private fun descartar(desde: Long) {
        val sobra = (desde - base).coerceIn(0, cuantos.toLong()).toInt()
        if (sobra < LARGO) return
        System.arraycopy(buf, sobra * Pcm.CANALES, buf, 0, (cuantos - sobra) * Pcm.CANALES)
        cuantos -= sobra
        base += sobra
    }

    private fun izq(i: Long): Float {
        val j = i - base
        return if (j < 0 || j >= cuantos) 0f else buf[(j * 2).toInt()]
    }

    private fun der(i: Long): Float {
        val j = i - base
        return if (j < 0 || j >= cuantos) 0f else buf[(j * 2 + 1).toInt()]
    }

    private fun mono(i: Long): Float = izq(i) + der(i)

    /**
     * Donde empezar la ventana [k]: cerca de su sitio, donde mejor se parezca
     * a como seguiria la anterior.
     */
    private fun buscar(nominal: Long): Long {
        val natural = anterior + SALTO
        val mitad = LARGO / 2
        fun parecido(c: Long, salto: Int): Double {
            var cruz = 0.0
            var energia = 1e-9
            var i = 0
            while (i < mitad) {
                val a = mono(c + i)
                cruz += a * mono(natural + i)
                energia += a * a
                i += salto
            }
            return cruz / sqrt(energia)
        }
        // Primero a grandes pasos, luego afinando alrededor del mejor
        var mejor = nominal
        var valor = Double.NEGATIVE_INFINITY
        var d = -TOLERANCIA
        while (d <= TOLERANCIA) {
            val c = (nominal + d).coerceAtLeast(0)
            val v = parecido(c, 4)
            if (v > valor) { valor = v; mejor = c }
            d += 4
        }
        val grueso = mejor
        valor = Double.NEGATIVE_INFINITY
        for (f in -4..4) {
            val c = (grueso + f).coerceAtLeast(0)
            val v = parecido(c, 1)
            if (v > valor) { valor = v; mejor = c }
        }
        return mejor
    }

    /** Pega la ventana siguiente y deja [SALTO] frames listos. */
    private fun ventanaSiguiente(): Boolean {
        val nominal = (k * pasoEntrada).roundToLong()
        asegurar(nominal + TOLERANCIA + LARGO + 1)
        if (nominal >= total) return false
        val inicio = if (k == 0L) 0L else buscar(nominal)
        for (i in 0 until LARGO) {
            // La primera ventana entra entera, sin subir desde cero: no hay
            // nada antes con que fundirse.
            val w = if (k == 0L && i < SALTO) 1f else ventana[i]
            cola[i * 2] += izq(inicio + i) * w
            cola[i * 2 + 1] += der(inicio + i) * w
        }
        System.arraycopy(cola, 0, listo, 0, SALTO * Pcm.CANALES)
        System.arraycopy(cola, SALTO * Pcm.CANALES, cola, 0, (LARGO - SALTO) * Pcm.CANALES)
        java.util.Arrays.fill(cola, (LARGO - SALTO) * Pcm.CANALES, cola.size, 0f)
        listoDesde = 0
        anterior = inicio
        k++
        descartar(minOf(anterior, (k * pasoEntrada).toLong()) - TOLERANCIA - 1)
        return true
    }

    override fun leer(destino: FloatArray, frames: Int): Int {
        if (directo) return fuente.leer(destino, frames)
        var hechos = 0
        while (hechos < frames) {
            if (listoDesde >= SALTO) {
                if (terminado || !ventanaSiguiente()) {
                    if (!terminado) {
                        // Lo que quedaba solapado aun tiene que sonar
                        terminado = true
                        System.arraycopy(cola, 0, listo, 0, SALTO * Pcm.CANALES)
                        java.util.Arrays.fill(cola, 0f)
                        listoDesde = 0
                    } else break
                }
            }
            val n = minOf(frames - hechos, SALTO - listoDesde)
            System.arraycopy(listo, listoDesde * 2, destino, hechos * 2, n * 2)
            listoDesde += n
            hechos += n
        }
        return hechos
    }

    override fun cerrar() = fuente.cerrar()

    private companion object {
        /** Ventana de 32 ms a 48 kHz. */
        const val LARGO = 1536
        const val SALTO = LARGO / 2
        /** Hasta 8 ms a cada lado para buscar la mejor junta. */
        const val TOLERANCIA = 384L
    }
}
