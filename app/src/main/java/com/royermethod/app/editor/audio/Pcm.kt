package com.royermethod.app.editor.audio

/**
 * Audio sin comprimir: muestras float de -1 a 1, en estereo intercalado
 * (izquierda, derecha, izquierda...). Todo el editor trabaja asi por dentro y
 * solo se convierte a otra cosa al entrar (decodificar) y al salir (codificar).
 */
object Pcm {
    /** Frecuencia de todo el audio del editor y del video exportado. */
    const val HZ = 48_000
    const val CANALES = 2

    /** Pares de muestras (fotogramas de audio) que caben en [us]. */
    fun frames(us: Long): Long = Math.floorDiv(us * HZ + 500_000, 1_000_000L)

    /** Microsegundos que ocupan [frames] pares de muestras. */
    fun us(frames: Long): Long = Math.floorDiv(frames * 1_000_000L, HZ.toLong())
}

/**
 * Algo de donde sacar audio en el formato de [Pcm], a trozos y en orden.
 */
fun interface FuentePcm {
    /**
     * Rellena [destino] desde el principio con hasta [frames] pares de
     * muestras.
     *
     * @return cuantos ha puesto. 0 solo cuando ya no queda nada: si hay que
     *   esperar al decodificador, se espera dentro.
     */
    fun leer(destino: FloatArray, frames: Int): Int

    /** Libera lo que tenga abierto. */
    fun cerrar() {}
}

/** Silencio sin fin. Para los clips sin audio o que no se pueden leer. */
object Silencio : FuentePcm {
    override fun leer(destino: FloatArray, frames: Int): Int {
        java.util.Arrays.fill(destino, 0, frames * Pcm.CANALES, 0f)
        return frames
    }
}

/**
 * Lee hasta llenar: algunas fuentes devuelven menos de lo pedido aunque no
 * hayan terminado.
 */
internal fun FuentePcm.leerTodo(destino: FloatArray, frames: Int, tmp: FloatArray): Int {
    var hechos = 0
    while (hechos < frames) {
        val pedir = minOf(frames - hechos, tmp.size / Pcm.CANALES)
        val n = leer(tmp, pedir)
        if (n <= 0) break
        System.arraycopy(tmp, 0, destino, hechos * Pcm.CANALES, n * Pcm.CANALES)
        hechos += n
    }
    return hechos
}
