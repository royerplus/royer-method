package com.royermethod.app.editor.exportar

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/**
 * El archivo MP4 que se va llenando.
 *
 * MediaMuxer no deja escribir nada hasta tener todas las pistas, y cada
 * codificador dice su formato solo cuando saca el primer paquete. Lo que
 * llegue antes se guarda aqui y se escribe al arrancar, en orden.
 */
internal class Contenedor(archivo: File, private val pistasEsperadas: Int) {

    private val muxer = MediaMuxer(archivo.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    private var pistas = 0
    private var arrancado = false
    private val tipos = HashMap<Int, String>()
    private val vigias = HashMap<Int, VigiaColgados>()

    /** Fotogramas colgados de un IDR escritos hasta ahora (ver [VigiaColgados]). */
    val colgados: Int get() = vigias.values.sumOf { it.cuantos }

    private class Guardado(val pista: Int, val datos: ByteBuffer, val tiempo: Long, val banderas: Int)

    private val espera = ArrayList<Guardado>()

    fun pista(formato: MediaFormat): Int {
        check(!arrancado) { "pista despues de arrancar" }
        val i = muxer.addTrack(formato)
        formato.getString(MediaFormat.KEY_MIME)?.let {
            tipos[i] = it
            if (SincronizacionVideo.admite(it)) vigias[i] = VigiaColgados(it)
        }
        pistas++
        if (pistas == pistasEsperadas) {
            muxer.start()
            arrancado = true
            val info = MediaCodec.BufferInfo()
            for (g in espera) {
                info.set(0, g.datos.remaining(), g.tiempo, g.banderas)
                muxer.writeSampleData(g.pista, g.datos, info)
            }
            espera.clear()
        }
        return i
    }

    fun escribir(pista: Int, datos: ByteBuffer, info: MediaCodec.BufferInfo) {
        val mime = tipos[pista]
        if (SincronizacionVideo.admite(mime)) {
            // El indice sync se basa en el NAL real, no en una bandera que
            // algunos codecs repiten en el B inmediatamente posterior al IDR.
            val clave = SincronizacionVideo.esClave(mime!!, datos, info.offset, info.size)
            info.flags = if (clave) info.flags or MediaCodec.BUFFER_FLAG_KEY_FRAME
                else info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME.inv()
            vigias[pista]?.ver(datos, info.offset, info.size, info.presentationTimeUs)
        }
        datos.position(info.offset)
        datos.limit(info.offset + info.size)
        if (arrancado) {
            muxer.writeSampleData(pista, datos, info)
        } else {
            val copia = ByteBuffer.allocateDirect(info.size)
            copia.put(datos)
            copia.flip()
            espera.add(Guardado(pista, copia, info.presentationTimeUs, info.flags))
        }
    }

    /** Cierra el archivo. Sin esto el MP4 queda sin indice y no se abre. */
    fun cerrar() {
        if (arrancado) runCatching { muxer.stop() }
        runCatching { muxer.release() }
    }
}
