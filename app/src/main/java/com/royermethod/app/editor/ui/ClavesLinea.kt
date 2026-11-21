package com.royermethod.app.editor.ui

import com.royermethod.app.editor.modelo.Clave
import com.royermethod.app.editor.modelo.Linea
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Las cuentas de las claves que se tocan y se arrastran (en la linea de
 * tiempo y en "Momento de esta clave"), sin Compose: se prueban en el
 * ordenador. Tiempos en microsegundos del bloque; nada sale negativo.
 */
internal object ClavesLinea {

    /**
     * El rombo mas cercano a [x] (en px), si esta a [radio] o menos: su
     * indice en [xs], o -1. A igual distancia, el primero. Con claves a una
     * milesima los rombos se tocan; elegir el primero que encaja (como antes)
     * cogia siempre el de la izquierda.
     */
    fun masCercana(xs: FloatArray, x: Float, radio: Float): Int {
        var mejor = -1
        var distancia = Float.MAX_VALUE
        for (i in xs.indices) {
            val d = abs(xs[i] - x)
            if (d <= radio && d < distancia) {
                mejor = i
                distancia = d
            }
        }
        return mejor
    }

    /**
     * Donde esta el cabezal ([local], tiempo del bloque; null si cae fuera)
     * entre las claves [tiempos] (ordenadas y sin repetir): la que tiene
     * encima (a [tolerancia] o menos, la mas cercana; -1 si ninguna) y las de
     * antes y despues para ir a ellas. Fuera del bloque, "despues" es la primera.
     */
    data class Posicion(val indice: Int, val anterior: Long?, val siguiente: Long?)

    fun posicion(tiempos: List<Long>, local: Long?, tolerancia: Long): Posicion {
        if (tiempos.isEmpty()) return Posicion(-1, null, null)
        if (local == null) return Posicion(-1, null, tiempos.first())
        val tol = tolerancia.coerceAtLeast(0)
        var indice = -1
        var distancia = Long.MAX_VALUE
        for (i in tiempos.indices) {
            val d = abs(tiempos[i] - local)
            if (d <= tol && d < distancia) { indice = i; distancia = d }
        }
        if (indice >= 0) return Posicion(indice, tiempos.getOrNull(indice - 1), tiempos.getOrNull(indice + 1))
        return Posicion(-1, tiempos.lastOrNull { it < local }, tiempos.firstOrNull { it > local })
    }

    /**
     * Hasta donde puede ir la clave que esta en [tUs] sin pisar a sus vecinas
     * (queda a mas de [tolerancia] de ellas, asi sigue siendo una clave
     * aparte) ni salir de 0..[duracionUs]. No pasa por encima de una vecina:
     * el orden de las claves no cambia. Las que comparten su tiempo (un salto)
     * se mueven con ella y no cuentan. Null si no le queda sitio.
     *
     * Tambien null si la clave ya esta mas cerca de una vecina que
     * [tolerancia] (puesta sin el iman, como las de 1,000 y 1,005 s de los
     * ejemplos): su propio tiempo queda fuera del hueco y [destino] la meteria
     * a la fuerza en el, asi que saltaria un fotograma o mas nada mas cogerla,
     * incluso contra el dedo. Asi el rango, si lo hay, siempre contiene [tUs].
     */
    fun limites(claves: List<Clave>, tUs: Long, duracionUs: Long, tolerancia: Long): LongRange? {
        val tol = tolerancia.coerceAtLeast(0)
        val dur = duracionUs.coerceAtLeast(0)
        var antes: Long? = null
        var despues: Long? = null
        for (k in claves) {
            val t = k.tiempoUs
            if (t < tUs && (antes == null || t > antes)) antes = t
            if (t > tUs && (despues == null || t < despues)) despues = t
        }
        val lo = maxOf(0L, antes?.let { it + tol + 1 } ?: 0L)
        val hi = minOf(dur, despues?.let { it - tol - 1 } ?: dur)
        return if (lo <= hi && tUs in lo..hi) lo..hi else null
    }

    /**
     * Si el rombo [i] de [xs] (px; NaN los que no se ven) tiene sitio para
     * cogerlo con el dedo: ningun otro rombo visible a menos de [separacion].
     * Los que comparten su tiempo ([tiempos]) son un salto, se mueven con el y
     * no cuentan. Con una clave por fotograma a zoom normal quedan a 2 dp:
     * entonces el bloque entero seria "clave" y arrastrar ya no moveria el
     * cabezal, asi que no se coge ninguno hasta acercar el zoom.
     */
    fun aislada(xs: FloatArray, tiempos: LongArray, i: Int, separacion: Float): Boolean {
        if (i !in xs.indices || xs[i].isNaN()) return false
        for (j in xs.indices) {
            if (j == i || xs[j].isNaN() || tiempos[j] == tiempos[i]) continue
            if (abs(xs[j] - xs[i]) < separacion) return false
        }
        return true
    }

    /**
     * Resolucion de un arrastre sin iman a fotogramas: el menor paso de 1, 2
     * o 5 x 10^k us que no baja de un pixel. En el zoom fino maximo (unos
     * 6 us por pixel) son 10 us; en el normal maximo, 0,5 ms.
     */
    fun pasoArrastre(usPorPx: Double): Long {
        var p = 1L
        while (true) {
            for (m in longArrayOf(1, 2, 5)) if (p * m >= usPorPx) return p * m
            if (p > Long.MAX_VALUE / 100) return p
            p *= 10
        }
    }

    /**
     * Donde queda una clave del bloque que empieza en [inicioUs] (en la
     * linea) al arrastrarla [dxPx] desde [tUs], calculado siempre desde donde
     * estaba al cogerla.
     *
     * Con [fotogramas] cae en el fotograma de la linea mas cercano (los
     * cuadros que se exportan, aunque el bloque empiece a mitad de uno); sin
     * el, al [pasoArrastre] del zoom. Siempre dentro de [limites]; si con el
     * iman no cabe ningun fotograma, se queda en [tUs]. Sin moverse no
     * cambia nada: cogerla y soltarla no la redondea. Nunca va contra el
     * dedo: si el fotograma (o el paso) mas cercano queda al otro lado de
     * [tUs], p. ej. una clave entre dos fotogramas sin sitio hacia donde se
     * arrastra, se queda donde estaba.
     */
    fun destino(tUs: Long, dxPx: Double, pxPorUs: Double, inicioUs: Long, limites: LongRange, fotogramas: Boolean): Long {
        if (dxPx == 0.0 || pxPorUs <= 0.0 || pxPorUs.isNaN()) return tUs
        val bruto = tUs + dxPx / pxPorUs
        val v = if (!fotogramas) {
            val paso = pasoArrastre(1.0 / pxPorUs)
            ((bruto / paso).roundToLong() * paso).coerceIn(limites.first, limites.last)
        } else {
            val ini = inicioUs.coerceAtLeast(0)
            // Fotogramas de la linea que caen dentro de los limites
            val primero = Linea.fotogramaDe(ini + limites.first)
            val ultimo = Linea.fotogramaEn(ini + limites.last)
            if (ultimo < primero) return tUs
            val cerca = ((ini + bruto) * Linea.FPS / 1_000_000.0).roundToLong()
            Linea.tiempoDe(cerca.coerceIn(primero, ultimo)) - ini
        }
        return if ((dxPx > 0 && v < tUs) || (dxPx < 0 && v > tUs)) tUs else v
    }
}
