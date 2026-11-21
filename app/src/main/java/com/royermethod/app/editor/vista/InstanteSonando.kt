package com.royermethod.app.editor.vista

/**
 * El instante que ensena la interfaz mientras suena, en cada refresco de
 * pantalla. No depende de Android: los tiempos usan la base de System.nanoTime.
 *
 * La vista previa solo publica un fotograma nuevo 30 veces por segundo, y
 * despues de dibujarlo. Si la linea de tiempo se moviera solo con eso, en una
 * pantalla de 60-120 Hz avanzaria a escalones de 33 ms, y a 90 o 120 Hz ademas
 * desiguales (cada 3, 4 o 5 refrescos, segun lo que tarde cada dibujo). Aqui
 * se parte de la ultima lectura del reloj de la vista previa y se sigue con el
 * tiempo del sistema:
 *
 *  - Solo si el reloj venia avanzando: al arrancar el sonido, o si se para,
 *    no se adelanta a lo que suena.
 *  - Como mucho [adelantoMaxUs] desde esa lectura, lo que cubre un dibujo que
 *    tarda (abrir un video al entrar en un bloque) sin irse lejos si el sonido
 *    se para justo entonces.
 *  - Nunca hacia atras dentro de la misma reproduccion: si una lectura llega
 *    por detras de lo ya ensenado, se espera a que el reloj lo alcance.
 *
 * Al buscar, pausar o empezar se llama a [reiniciar]: lo que venga despues no
 * sigue a lo de antes.
 */
internal class InstanteSonando(private val adelantoMaxUs: Long = ADELANTO_MAX_US) {
    private var baseUs = 0L
    /** Cuando se leyo [baseUs]; 0 si esa lectura no avanzaba. */
    private var baseNs = 0L
    private var finUs = Long.MAX_VALUE
    private var entregado = Long.MIN_VALUE

    /**
     * Una lectura del reloj: [t] en [ns]. [avanza] si va por delante de la
     * anterior; [fin] es lo ultimo que se puede ensenar.
     */
    fun leer(t: Long, ns: Long, avanza: Boolean, fin: Long) {
        baseUs = t
        baseNs = if (avanza) ns else 0L
        finUs = fin
    }

    fun reiniciar(t: Long) {
        baseUs = t
        baseNs = 0L
        finUs = Long.MAX_VALUE
        entregado = Long.MIN_VALUE
    }

    /** Lo que se ensena en el refresco de [ns]. */
    fun en(ns: Long): Long {
        val t = if (baseNs == 0L) baseUs else baseUs + ((ns - baseNs) / 1_000).coerceIn(0L, adelantoMaxUs)
        val r = maxOf(minOf(t, finUs), entregado)
        entregado = r
        return r
    }

    companion object {
        const val ADELANTO_MAX_US = 200_000L
    }
}
