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
    fun tipos(mime: String, datos: ByteBuffer, offset: Int, tamano: Int): List<Int>? =
        nals(mime, datos, offset, tamano)?.map { it.tipo }

    /** Cada NAL: su tipo y donde empieza (en su cabecera). */
    private class Nal(val tipo: Int, val inicio: Int, val largo: Int)

    private fun nals(mime: String, datos: ByteBuffer, offset: Int, tamano: Int): List<Nal>? {
        if (!admite(mime) || offset < 0 || tamano <= 0 || offset > datos.limit() || tamano > datos.limit() - offset) return null
        val fin = offset + tamano
        // Primero los largos de cuatro bytes. Un largo de 256 empieza como un
        // start code de tres bytes: comprobar la estructura completa evita la
        // ambiguedad con Annex B.
        return largos(mime, datos, offset, fin) ?: annexB(mime, datos, offset, fin)
    }

    private fun esImagen(mime: String, tipo: Int) = if (mime == AVC) tipo in 1..5 else tipo in 0..31

    /** El tipo de la imagen: el de su primer NAL de video, o null si no lleva. */
    fun tipoImagen(mime: String, tipos: List<Int>): Int? = tipos.firstOrNull { esImagen(mime, it) }

    /**
     * ¿Se puede empezar a decodificar en este paquete? Si es un IDR, un IRAP
     * de H.265 o, en H.264, una imagen I (asi marcan sus puntos de acceso los
     * GOP abiertos) o una con SEI de punto de recuperacion (el refresco
     * intra periodico, sin imagenes I). null si no se entiende.
     */
    fun puedeEmpezar(mime: String, datos: ByteBuffer, offset: Int, tamano: Int): Boolean? {
        val nals = nals(mime, datos, offset, tamano) ?: return null
        val imagen = nals.firstOrNull { esImagen(mime, it.tipo) } ?: return null
        if (independiente(mime, imagen.tipo)) return true
        if (mime == HEVC) return imagen.tipo in 22..23
        if (imagen.tipo != 1) return false
        if (nals.any { it.tipo == 6 && conRecuperacion(datos, it) }) return true
        val corte = tipoCorte(datos, imagen) ?: return null
        return corte % 5 == 2 || corte % 5 == 4
    }

    /**
     * ¿Hay fotogramas que se apoyan en este? En H.265 lo dice el tipo (los
     * pares por debajo de 16 no); en H.264, nal_ref_idc. null si no se entiende.
     */
    fun esReferencia(mime: String, datos: ByteBuffer, offset: Int, tamano: Int): Boolean? {
        val imagen = nals(mime, datos, offset, tamano)?.firstOrNull { esImagen(mime, it.tipo) } ?: return null
        return if (mime == HEVC) !(imagen.tipo < 16 && imagen.tipo % 2 == 0)
        else (datos.get(imagen.inicio).toInt() ushr 5) and 3 != 0
    }

    /**
     * ¿Lleva este SEI de H.264 un punto de recuperacion (payloadType 6)? Se
     * recorren sus mensajes por tipo y tamano; si algo no cuadra, se para.
     */
    private fun conRecuperacion(b: ByteBuffer, nal: Nal): Boolean {
        val fin = nal.inicio + nal.largo
        var p = nal.inicio + 1
        fun numero(): Int? {
            var v = 0
            while (true) {
                if (p >= fin) return null
                val x = b.get(p++).toInt() and 255
                v += x
                if (x != 255) return v
            }
        }
        while (p < fin) {
            // 0x80: el final del SEI
            if (b.get(p).toInt() and 255 == 0x80 && p == fin - 1) return false
            val tipo = numero() ?: return false
            if (tipo == 6) return true
            val tam = numero() ?: return false
            p += tam
        }
        return false
    }

    /** slice_type de un corte H.264: el segundo ue(v) tras la cabecera del NAL. */
    private fun tipoCorte(b: ByteBuffer, nal: Nal): Int? {
        val fin = nal.inicio + nal.largo
        var p = nal.inicio + 1
        var ceros = 0
        var byte = 0
        var bit = 8
        fun leer(): Int? {
            if (bit == 8) {
                if (p >= fin) return null
                var v = b.get(p++).toInt() and 255
                // Byte de prevencion (00 00 03): no es del corte
                if (ceros >= 2 && v == 3) {
                    if (p >= fin) return null
                    v = b.get(p++).toInt() and 255
                    ceros = 0
                }
                ceros = if (v == 0) ceros + 1 else 0
                byte = v
                bit = 0
            }
            return (byte ushr (7 - bit++)) and 1
        }
        fun ue(): Int? {
            var n = 0
            while (true) {
                val v = leer() ?: return null
                if (v == 1) break
                if (++n > 16) return null
            }
            var r = 0
            repeat(n) { r = (r shl 1) or (leer() ?: return null) }
            return (1 shl n) - 1 + r
        }
        ue() ?: return null
        return ue()
    }

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
    private fun largos(mime: String, b: ByteBuffer, inicio: Int, fin: Int): List<Nal>? {
        var p = inicio
        val tipos = ArrayList<Nal>(4)
        while (p < fin) {
            if (fin - p < 4) return null
            val n = ((b.get(p).toLong() and 255) shl 24) or
                ((b.get(p + 1).toLong() and 255) shl 16) or
                ((b.get(p + 2).toLong() and 255) shl 8) or (b.get(p + 3).toLong() and 255)
            p += 4
            if (n <= 0 || n > fin - p) return null
            tipos.add(Nal(tipo(mime, b, p, n.toInt()) ?: return null, p, n.toInt()))
            p += n.toInt()
        }
        return tipos
    }

    private fun annexB(mime: String, b: ByteBuffer, inicio: Int, fin: Int): List<Nal>? {
        var p = inicio
        val tipos = ArrayList<Nal>(4)
        while (p < fin) {
            val largo = prefijo(b, p, fin)
            if (largo == 0) return null
            val nal = p + largo
            var siguiente = nal
            while (siguiente < fin && prefijo(b, siguiente, fin) == 0) siguiente++
            tipos.add(Nal(tipo(mime, b, nal, siguiente - nal) ?: return null, nal, siguiente - nal))
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
    /** Instante del ultimo punto de acceso visto. */
    var claveUs = Long.MIN_VALUE
        private set
    private var permitidos: IntRange? = null

    var cuantos = 0
        private set

    /** @return si este paquete esta colgado */
    fun ver(datos: ByteBuffer, offset: Int, tamano: Int, tiempoUs: Long): Boolean {
        val tipos = SincronizacionVideo.tipos(mime, datos, offset, tamano) ?: return false
        val tipo = SincronizacionVideo.tipoImagen(mime, tipos) ?: return false
        if (SincronizacionVideo.independiente(mime, tipo)) {
            claveUs = tiempoUs
            permitidos = SincronizacionVideo.anterioresPermitidos(mime, tipo)
        } else if (tiempoUs < claveUs && permitidos?.contains(tipo) != true) {
            cuantos++
            return true
        }
        return false
    }
}

/**
 * Al leer un video para decodificarlo, los paquetes que no se le dan al
 * decodificador: los colgados de [VigiaColgados] en los que no se apoya nadie.
 * Con uno dentro, algunos decodificadores pintan oscuro o con manchas todo lo
 * que viene hasta el siguiente IDR (medio segundo o varios); sin el, solo falta
 * ese fotograma y se ve el anterior. Pasa con los videos que exporto esta app
 * con fotogramas B antes de corregirlo, si se vuelven a meter en un proyecto.
 *
 * Para no abrir cada paquete, solo se miran los que el archivo marca como sync
 * y los que llegan con un instante anterior al del ultimo punto de acceso; un
 * video normal no tiene de esos.
 *
 * Los paquetes van en el orden del archivo; despues de buscar, uno nuevo.
 */
internal class FiltroColgados(private val mime: String) {
    private val vigia = VigiaColgados(mime)

    /** Tirados hasta ahora. */
    val tirados: Int get() = vigia.cuantos

    fun tirar(datos: ByteBuffer, offset: Int, tamano: Int, tiempoUs: Long, sync: Boolean): Boolean {
        if (!sync && tiempoUs >= vigia.claveUs) return false
        return vigia.ver(datos, offset, tamano, tiempoUs) &&
            SincronizacionVideo.esReferencia(mime, datos, offset, tamano) == false
    }
}
