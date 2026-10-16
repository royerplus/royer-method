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
    fun esClave(mime: String, datos: ByteBuffer, offset: Int, tamano: Int): Boolean {
        if (!admite(mime) || offset < 0 || tamano <= 0 || offset > datos.limit() || tamano > datos.limit() - offset) return false
        val fin = offset + tamano
        // Primero los largos de cuatro bytes. Un largo de 256 empieza como un
        // start code de tres bytes: comprobar la estructura completa evita la
        // ambiguedad con Annex B.
        largos(mime, datos, offset, fin)?.let { return it }
        return annexB(mime, datos, offset, fin)
    }

    /** null si no es una secuencia completa de NAL con largos de cuatro bytes. */
    private fun largos(mime: String, b: ByteBuffer, inicio: Int, fin: Int): Boolean? {
        var p = inicio
        var clave = false
        while (p < fin) {
            if (fin - p < 4) return null
            val n = ((b.get(p).toLong() and 255) shl 24) or
                ((b.get(p + 1).toLong() and 255) shl 16) or
                ((b.get(p + 2).toLong() and 255) shl 8) or (b.get(p + 3).toLong() and 255)
            p += 4
            if (n <= 0 || n > fin - p) return null
            val tipo = tipo(mime, b, p, n.toInt()) ?: return null
            if (independiente(mime, tipo)) clave = true
            p += n.toInt()
        }
        return clave
    }

    private fun annexB(mime: String, b: ByteBuffer, inicio: Int, fin: Int): Boolean {
        var p = inicio
        var clave = false
        while (p < fin) {
            val largo = prefijo(b, p, fin)
            if (largo == 0) return false
            val nal = p + largo
            var siguiente = nal
            while (siguiente < fin && prefijo(b, siguiente, fin) == 0) siguiente++
            val tipo = tipo(mime, b, nal, siguiente - nal) ?: return false
            if (independiente(mime, tipo)) clave = true
            p = siguiente
        }
        return clave
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

    private fun independiente(mime: String, tipo: Int): Boolean =
        if (mime == AVC) tipo == 5 else tipo in 16..21
}
