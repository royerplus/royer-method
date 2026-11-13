package com.royermethod.app.editor.vista

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * Que fotograma toca dibujar en cada refresco de pantalla mientras suena, y a
 * que hora debe verse. No depende de Android: los tiempos usan la base de
 * System.nanoTime.
 *
 * Antes cada fotograma se dibujaba cuando llegaba su instante y salia en
 * pantalla cuando se podia: lo que tardaba el dibujo (de 5 a 50 ms) se sumaba
 * al ritmo, y a 60 Hz la mitad de los cuadros se veian uno o tres refrescos
 * en vez de dos. En un movimiento rapido (una imagen que entra subiendo) eso
 * se ve a tirones aunque lo exportado vaya fino. Ahora:
 *
 *  - Se elige el fotograma del refresco que llega dentro de [antesNs] (el
 *    margen del dibujo, unos 32 ms a 60 Hz) y se dibuja ya.
 *  - Se le pone ese refresco como hora de presentacion
 *    (eglPresentationTimeANDROID): la pantalla no lo ensena antes aunque este
 *    listo, y cada fotograma dura lo mismo.
 *  - Manda el sonido: lo que se ve en ese refresco es lo que va a sonar
 *    entonces. Solo se adelanta hasta el refresco si el reloj avanza; parado
 *    (el sonido aun no arranca) se queda en lo que suena.
 *  - Los refrescos se ponen lejos de los cambios de fotograma (la fase): si
 *    un cambio cayera justo en un refresco, cualquier temblor del reloj lo
 *    pasaria al refresco de antes o al de despues y el ritmo seria 1-3-1-3.
 *    Mueve la imagen como mucho tres cuartos de refresco respecto al sonido
 *    (12 ms a 60 Hz), que no se nota.
 */
internal class Ritmo(
    private val fps: Int,
    private val antesNs: Long = ANTES_NS,
    periodoInicialNs: Long = 16_666_667L
) {
    /** El fotograma [fotograma] se ensena en [presentarNs]; tiene hasta [limiteNs] para estar listo. */
    data class Turno(val fotograma: Long, val presentarNs: Long, val limiteNs: Long)

    /** Lo que dura un refresco, medido de los avisos de la pantalla. */
    var periodoNs = periodoInicialNs.coerceIn(PERIODO_MIN_NS, PERIODO_MAX_NS)
        private set
    private var vsyncAnterior = 0L
    /** Desplazamiento en fotogramas que aleja los refrescos de los cambios. */
    var fase = 0.0
        private set
    private var conFase = false
    private var ultimo = Long.MIN_VALUE

    /** Empieza otra reproduccion (play, busqueda): [enPantalla] es el fotograma que ya se ve, o null. */
    fun reiniciar(enPantalla: Long?) {
        ultimo = enPantalla ?: Long.MIN_VALUE
        fase = 0.0
        conFase = false
        vsyncAnterior = 0L
    }

    /**
     * El refresco [vsyncNs] (Choreographer): [tUs] es lo que suena en
     * [ahoraNs] y [avanza] si el reloj se mueve. Devuelve el fotograma nuevo y
     * cuando ensenarlo, o null si sigue valiendo el que ya va.
     */
    fun turno(vsyncNs: Long, ahoraNs: Long, tUs: Long, avanza: Boolean): Turno? {
        medirPeriodo(vsyncNs)
        val p = periodoNs
        // Siempre los mismos refrescos por delante, para que un aviso que llega
        // un poco antes o despues no cambie de refresco. Solo si el hilo llega
        // muy tarde (el dibujo anterior se alargo) se apunta a uno posterior.
        var d = vsyncNs + maxOf(1L, (antesNs + p - 1) / p) * p
        while (d - ahoraNs < MINIMO_NS) d += p
        val tD = if (avanza) tUs + (d - ahoraNs) / 1_000L else tUs
        val x = tD.toDouble() * fps / 1e6
        if (avanza) centrar(x, p)
        val n = floor(x + fase).toLong()
        if (n <= ultimo) return null
        ultimo = n
        // Medio refresco antes: la pantalla lo toma para el refresco d y no para el anterior
        return Turno(n, d - p / 2, d - p)
    }

    private fun medirPeriodo(vsyncNs: Long) {
        val antes = vsyncAnterior
        vsyncAnterior = vsyncNs
        if (antes == 0L) return
        val delta = vsyncNs - antes
        if (delta < PERIODO_MIN_NS || delta > 4 * PERIODO_MAX_NS) return
        // Si el hilo se salto refrescos, el aviso llega a un multiplo
        val veces = (delta.toDouble() / periodoNs).roundToLong().coerceAtLeast(1L)
        val uno = delta / veces
        if (abs(uno - periodoNs) <= periodoNs / 4) periodoNs += (uno - periodoNs) / 8
        else if (veces == 1L && delta <= PERIODO_MAX_NS) periodoNs = delta // la pantalla cambio de frecuencia
    }

    /** Lleva la fase a que los refrescos caigan en mitad de su tramo de fotograma. */
    private fun centrar(x: Double, p: Long) {
        val paso = p.toDouble() * fps / 1e9 // fotogramas por refresco: 0,5 a 60 Hz
        if (paso <= 0.0 || paso >= 1.0) return
        val porFotograma = 1.0 / paso
        val entero = Math.round(porFotograma).toDouble()
        // 30 por segundo en 144 Hz o en 40 Hz: el reparto es desigual de todas formas
        if (abs(porFotograma - entero) > 0.02 * entero) return
        val y = ((x + fase) % 1.0 + 1.0) % 1.0 % paso
        val error = y - paso / 2
        fase -= if (conFase) error * GANANCIA else error
        conFase = true
        // Una deriva lenta entre el reloj del sonido y la pantalla: se da la
        // vuelta de un refresco entero, que solo repite un cuadro una vez. No
        // en el medio refresco justo: ahi el temblor la haria ir y venir.
        if (fase > paso * 3 / 4) fase -= paso
        if (fase < -paso * 3 / 4) fase += paso
    }

    companion object {
        /** Lo que se dibuja por delante: a 60 Hz deja unos 32 ms para el dibujo. */
        const val ANTES_NS = 48_000_000L
        /** Menos que esto para dibujar y la pantalla ya no llega a tomarlo a tiempo. */
        const val MINIMO_NS = 30_000_000L
        const val GANANCIA = 0.1
        const val PERIODO_MIN_NS = 4_000_000L
        const val PERIODO_MAX_NS = 34_000_000L
    }
}
