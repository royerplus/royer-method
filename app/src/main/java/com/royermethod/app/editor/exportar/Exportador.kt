package com.royermethod.app.editor.exportar

import android.content.Context
import android.media.MediaCodec
import android.net.Uri
import android.os.Bundle
import android.view.Surface
import com.royermethod.app.editor.audio.DecodificadorAudio
import com.royermethod.app.editor.audio.Mezclador
import com.royermethod.app.editor.audio.Pcm
import com.royermethod.app.editor.audio.PlanAudio
import com.royermethod.app.editor.modelo.Escena
import com.royermethod.app.editor.modelo.Linea
import com.royermethod.app.editor.modelo.PlanExportacion
import com.royermethod.app.editor.modelo.Proyecto
import com.royermethod.app.editor.motor.Compositor
import com.royermethod.app.editor.motor.Egl
import com.royermethod.app.editor.motor.Fluidez
import java.io.File

/** Se pidio parar. */
class Cancelado : Exception("cancelado")

/** Lo que salio. */
data class Informe(
    val archivo: File,
    val bytes: Long,
    val ancho: Int,
    val alto: Int,
    /** "H.265" o "H.264". */
    val codec: String,
    val verificacion: Verificacion,
    /** Fuentes que no se pudieron leer bien en algun fotograma. */
    val avisos: Int
)

/**
 * Exporta un proyecto a MP4.
 *
 * Cada fotograma se dibuja con el mismo motor que la vista previa y se le
 * pone su marca de tiempo exacta (n / 30 s): el codificador no decide nada
 * de tiempos, asi que no puede salir a 29.97 ni repetir uno. Por cada
 * fotograma:
 *
 *  1. Si es un corte seco, se pide un fotograma completo justo ahi. Un corte
 *     en mitad de una cadena de fotogramas parciales es lo mas caro de
 *     codificar y lo que antes se ve en bloques.
 *  2. Se ajusta el bitrate al tramo (ver [PlanExportacion]).
 *  3. Se dibuja, se marca el tiempo y se entrega.
 *
 * El audio se codifica a la vez, por delante del video lo justo para que el
 * MP4 quede intercalado y se pueda reproducir mientras se descarga.
 */
class Exportador(
    private val context: Context,
    private val proyecto: Proyecto,
    private val destino: File,
    private val cancelado: () -> Boolean = { false },
    private val alAvanzar: (hechos: Long, total: Long) -> Unit = { _, _ -> }
) {
    private val info = MediaCodec.BufferInfo()

    fun exportar(): Informe {
        if (destino.exists()) destino.delete()
        val segmentos = PlanAudio.de(proyecto)
        val conAudio = segmentos.isNotEmpty()
        val plan0 = PlanExportacion.de(proyecto, conAudio = conAudio)
        // H.265 solo si de verdad hay codificador para ese tamano
        val hevc = plan0.hevc && Codificadores.hay(Codificadores.HEVC, plan0.ancho, plan0.alto)
        val plan = if (hevc == plan0.hevc) plan0 else PlanExportacion.de(proyecto, hevc = false, conAudio = conAudio)
        require(plan.fotogramas > 0) { "El proyecto esta vacio" }

        var video: MediaCodec? = null
        var audio: MediaCodec? = null
        var superficie: Surface? = null
        var egl: Egl? = null
        var compositor: Compositor? = null
        var mezclador: Mezclador? = null
        val contenedor = Contenedor(destino, if (conAudio) 2 else 1)
        var bien = false
        try {
            val (codec, mime) = Codificadores.video(plan.ancho, plan.alto, plan.bitrateEn(0), hevc)
            video = codec
            superficie = codec.createInputSurface()
            codec.start()
            if (conAudio) audio = Codificadores.audio(plan.audioKbps).also { it.start() }

            egl = Egl(superficie, plan.ancho, plan.alto, paraCodificador = true)
            egl.activar()
            val fluido = (proyecto.principal + proyecto.capas.map { it.clip }).any { it.fluido }
            compositor = Compositor(context, plan.ancho, plan.alto, if (fluido) Fluidez(context) else null)
            val escena = Escena(proyecto, plan.ancho, plan.alto, compositor.medidor)

            val totalAudio = PlanAudio.total(proyecto)
            if (conAudio) {
                mezclador = Mezclador(segmentos, totalAudio) { s, dentro ->
                    Mezclador.lector(s, dentro) { uri, desde ->
                        DecodificadorAudio.abrir(context, Uri.parse(uri), desde)?.let { it to it.hz }
                    }
                }
            }
            val pistaVideo = Pista()
            val pistaAudio = Pista()
            val audioEnvio = EnvioAudio(totalAudio)

            val cortes = Linea.cortes(proyecto)
            var bitrate = plan.bitrateEn(0)
            for (n in 0 until plan.fotogramas) {
                if (cancelado()) throw Cancelado()

                val b = plan.bitrateEn(n)
                if (b != bitrate) {
                    bitrate = b
                    codec.setParameters(Bundle().apply { putInt(MediaCodec.PARAMETER_KEY_VIDEO_BITRATE, b) })
                }
                if (n > 0 && n in cortes) {
                    codec.setParameters(Bundle().apply { putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0) })
                }

                compositor.dibujar(escena.cuadro(n))
                egl.marcarTiempo(n * 1_000_000_000L / Linea.FPS)
                egl.publicar()
                drenar(codec, pistaVideo, contenedor, fin = false)

                // Audio hasta medio segundo por delante del video
                if (audio != null && mezclador != null) {
                    val hasta = Pcm.frames(Linea.tiempoDe(n + 1) + 500_000).coerceAtMost(totalAudio)
                    while (!audioEnvio.acabado && audioEnvio.frames < hasta) {
                        if (!alimentar(audio, mezclador, audioEnvio, hasta)) {
                            drenar(audio, pistaAudio, contenedor, fin = false)
                        }
                    }
                    drenar(audio, pistaAudio, contenedor, fin = false)
                }
                alAvanzar(n + 1, plan.fotogramas)
            }

            // Cerrar: lo que quede en los codificadores tambien va al archivo
            codec.signalEndOfInputStream()
            if (audio != null && mezclador != null) {
                while (!audioEnvio.acabado) {
                    if (!alimentar(audio, mezclador, audioEnvio, totalAudio)) {
                        drenar(audio, pistaAudio, contenedor, fin = false)
                    }
                }
            }
            drenar(codec, pistaVideo, contenedor, fin = true)
            if (audio != null) drenar(audio, pistaAudio, contenedor, fin = true)
            contenedor.cerrar()

            val verificacion = Verificacion.de(destino, plan.fotogramas)
            bien = true
            return Informe(
                archivo = destino,
                bytes = destino.length(),
                ancho = plan.ancho,
                alto = plan.alto,
                codec = if (mime == Codificadores.HEVC) "H.265" else "H.264",
                verificacion = verificacion,
                avisos = compositor.avisos
            )
        } finally {
            mezclador?.cerrar()
            runCatching { compositor?.soltar() }
            runCatching { egl?.soltar() }
            superficie?.release()
            runCatching { video?.stop() }
            runCatching { video?.release() }
            runCatching { audio?.stop() }
            runCatching { audio?.release() }
            contenedor.cerrar()
            if (!bien) destino.delete()
        }
    }

    private class Pista {
        var indice = -1
        var acabada = false
    }

    private class EnvioAudio(val total: Long) {
        var frames = 0L
        var acabado = false
        val muestras = FloatArray(1024 * Pcm.CANALES)
    }

    /**
     * Mete un trozo de audio en el codificador, hasta [hasta] como mucho.
     * @return false si el codificador no tenia sitio
     */
    private fun alimentar(codec: MediaCodec, mezclador: Mezclador, e: EnvioAudio, hasta: Long): Boolean {
        val i = codec.dequeueInputBuffer(5_000)
        if (i < 0) return false
        val buf = codec.getInputBuffer(i)!!
        buf.clear()
        val cabe = buf.remaining() / (2 * Pcm.CANALES)
        val restante = hasta - e.frames
        if (restante <= 0 || cabe <= 0) {
            if (e.frames >= e.total) {
                codec.queueInputBuffer(i, 0, 0, Pcm.us(e.frames), MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                e.acabado = true
            } else {
                codec.queueInputBuffer(i, 0, 0, Pcm.us(e.frames), 0)
            }
            return true
        }
        val n = mezclador.leer(e.muestras, minOf(cabe.toLong(), 1024L, restante).toInt())
        if (n <= 0) {
            codec.queueInputBuffer(i, 0, 0, Pcm.us(e.frames), MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            e.acabado = true
            return true
        }
        val corto = buf.asShortBuffer()
        for (k in 0 until n * Pcm.CANALES) {
            val v = (e.muestras[k] * 32767f).coerceIn(-32768f, 32767f)
            corto.put(Math.round(v).toShort())
        }
        codec.queueInputBuffer(i, 0, n * 2 * Pcm.CANALES, Pcm.us(e.frames), 0)
        e.frames += n
        return true
    }

    /**
     * Saca lo que haya codificado y lo escribe.
     * @param fin esperar hasta el final del flujo
     */
    private fun drenar(codec: MediaCodec, pista: Pista, contenedor: Contenedor, fin: Boolean) {
        if (pista.acabada) return
        var sinNada = 0
        while (true) {
            val i = codec.dequeueOutputBuffer(info, if (fin) 10_000 else 0)
            when {
                i == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!fin) return
                    // Un codificador colgado no puede dejar la exportacion
                    // esperando para siempre.
                    if (++sinNada > 500) throw IllegalStateException("El codificador dejo de responder")
                }
                i == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> pista.indice = contenedor.pista(codec.outputFormat)
                i >= 0 -> {
                    sinNada = 0
                    val buf = codec.getOutputBuffer(i)!!
                    // La configuracion ya va en el formato de la pista
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                    if (info.size > 0 && pista.indice >= 0) contenedor.escribir(pista.indice, buf, info)
                    codec.releaseOutputBuffer(i, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        pista.acabada = true
                        return
                    }
                }
            }
        }
    }
}
