package com.royermethod.app.editor.exportar

import java.nio.ByteBuffer

/**
 * Un punto de acceso tiene que poder decodificarse sin los paquetes anteriores.
 * Algunos codificadores marcan tambien el B anterior a un IDR como clave;
 * trasladar esa bandera al indice MP4 hace que buscar salte el IDR necesario.
 */
internal object SincronizacionVideo {
    private const val AVC = "video/avc"
    private const val HEVC = "video/hevc"

    fun admite(mime: String?): Boolean = mime == AVC || mime == HEVC

    /** Lee solo el tramo indicado y conserva posicion y limite del buffer. */
    fun esClave(mime: String, datos: ByteBuffer, offset: Int, tamano: Int): Boolean =
        tipos(mime, datos, offset, tamano)?.any { independiente(mime, it) } ?: false

    /**
     * Los tipos de NAL del tramo, en orden; null si no se entiende. Como
     * [esClave], no mueve el buffer.
     */
    fun tipos(mime: String, datos: ByteBuffer, offset: Int, tamano: Int): List<Int>? {
        if (!admite(mime) || offset < 0 || tamano <= 0 || offset > datos.limit() || tamano > datos.limit() - offset) return null
        val fin = offset + tamano
        // Primero los largos de cuatro bytes. Un largo de 256 empieza como un
        // start code de tres bytes: comprobar la estructura completa evita la
        // ambiguedad con Annex B.
        return largos(mime, datos, offset, fin) ?: annexB(mime, datos, offset, fin)
    }

    /** El tipo de la imagen: el de su primer NAL de video, o null si no lleva. */
    fun tipoImagen(mime: String, tipos: List<Int>): Int? =
        tipos.firstOrNull { if (mime == AVC) it in 1..5 else it in 0..31 }

    /**
     * Despues de [irap] (un punto de acceso), que tipos pueden llegar y aun asi
     * ensenarse antes que el. Fuera de esos, la norma no deja ninguno.
     */
    fun anterioresPermitidos(mime: String, irap: Int): IntRange? = when {
        mime != HEVC -> null
        // CRA y BLA_W_LP: RADL y RASL
        irap == 21 || irap == 16 -> 6..9
        // IDR_W_RADL y BLA_W_RADL: solo RADL, que se apoyan en el propio IRAP
        irap == 19 || irap == 17 -> 6..7
        else -> null
    }

    /** null si no es una secuencia completa de NAL con largos de cuatro bytes. */
    private fun largos(mime: String, b: ByteBuffer, inicio: Int, fin: Int): List<Int>? {
        var p = inicio
        val tipos = ArrayList<Int>(4)
        while (p < fin) {
            if (fin - p < 4) return null
            val n = ((b.get(p).toLong() and 255) shl 24) or
                ((b.get(p + 1).toLong() and 255) shl 16) or
                ((b.get(p + 2).toLong() and 255) shl 8) or (b.get(p + 3).toLong() and 255)
            p += 4
            if (n <= 0 || n > fin - p) return null
            tipos.add(tipo(mime, b, p, n.toInt()) ?: return null)
            p += n.toInt()
        }
        return tipos
    }

    private fun annexB(mime: String, b: ByteBuffer, inicio: Int, fin: Int): List<Int>? {
        var p = inicio
        val tipos = ArrayList<Int>(4)
        while (p < fin) {
            val largo = prefijo(b, p, fin)
            if (largo == 0) return null
            val nal = p + largo
            var siguiente = nal
            while (siguiente < fin && prefijo(b, siguiente, fin) == 0) siguiente++
            tipos.add(tipo(mime, b, nal, siguiente - nal) ?: return null)
            p = siguiente
        }
        return tipos
    }

    private fun prefijo(b: ByteBuffer, p: Int, fin: Int): Int {
        if (fin - p < 3 || b.get(p).toInt() != 0 || b.get(p + 1).toInt() != 0) return 0
        if (b.get(p + 2).toInt() == 1) return 3
        return if (fin - p >= 4 && b.get(p + 2).toInt() == 0 && b.get(p + 3).toInt() == 1) 4 else 0
    }

    private fun tipo(mime: String, b: ByteBuffer, p: Int, n: Int): Int? {
        if (n < (if (mime == HEVC) 2 else 1)) return null
        val cabecera = b.get(p).toInt() and 255
        if (cabecera and 128 != 0) return null
        return if (mime == HEVC) {
            if (b.get(p + 1).toInt() and 7 == 0) null else (cabecera ushr 1) and 63
        } else (cabecera and 31).takeIf { it > 0 }
    }

    fun independiente(mime: String, tipo: Int): Boolean =
        if (mime == AVC) tipo == 5 else tipo in 16..21
}

/**
 * Cuenta los fotogramas que llegan detras de un punto de acceso (IDR) pero se
 * ensenan antes que el y no estan entre los que la norma permite ahi.
 *
 * Pasa con algunos codificadores que usan fotogramas B cuando se les pide un
 * fotograma completo en un corte: convierten en IDR el P que tocaba y luego
 * mandan el B que quedaba pendiente, apoyado en fotogramas de antes del IDR,
 * que el decodificador ya tiro. Ese B sale gris o mal, y si el reproductor
 * se lia con el orden, lo que viene detras se pinta sobre un fotograma
 * equivocado hasta el siguiente IDR: oscuro y con manchas.
 *
 * Los paquetes se le pasan en el orden del archivo (el de decodificar).
 */
internal class VigiaColgados(private val mime: String) {
    private var claveUs = Long.MIN_VALUE
    private var permitidos: IntRange? = null

    var cuantos = 0
        private set

    fun ver(datos: ByteBuffer, offset: Int, tamano: Int, tiempoUs: Long) {
        val tipos = SincronizacionVideo.tipos(mime, datos, offset, tamano) ?: return
        val tipo = SincronizacionVideo.tipoImagen(mime, tipos) ?: return
        if (SincronizacionVideo.independiente(mime, tipo)) {
            claveUs = tiempoUs
            permitidos = SincronizacionVideo.anterioresPermitidos(mime, tipo)
        } else if (tiempoUs < claveUs && permitidos?.contains(tipo) != true) {
            cuantos++
        }
    }
}
