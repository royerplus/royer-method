package com.royermethod.app.editor.exportar

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build

/**
 * Elegir y configurar los codificadores del telefono.
 *
 * Cada fabricante trae los suyos y no todos aceptan lo mismo: hay quien no
 * tiene H.265, quien lo tiene pero no a ese tamano, quien rechaza el perfil
 * High de H.264... Por eso se prueba en orden de mejor a peor y se queda el
 * primero que arranca, en vez de fallar la exportacion entera.
 */
internal object Codificadores {

    const val HEVC = MediaFormat.MIMETYPE_VIDEO_HEVC
    const val AVC = MediaFormat.MIMETYPE_VIDEO_AVC
    const val AAC = MediaFormat.MIMETYPE_AUDIO_AAC

    /** ¿Hay codificador de [mime] por hardware o software para este tamano? */
    fun hay(mime: String, ancho: Int, alto: Int): Boolean = candidatos(mime, ancho, alto).isNotEmpty()

    /** Los que sirven, primero los de hardware (mucho mas rapidos y suelen comprimir mejor). */
    fun candidatos(mime: String, ancho: Int, alto: Int): List<MediaCodecInfo> =
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            .filter { it.isEncoder && it.supportedTypes.any { t -> t.equals(mime, ignoreCase = true) } }
            .filter { info ->
                runCatching {
                    info.getCapabilitiesForType(mime).videoCapabilities?.isSizeSupported(ancho, alto) ?: false
                }.getOrDefault(false)
            }
            .sortedBy { if (esSoftware(it)) 1 else 0 }

    fun esSoftware(info: MediaCodecInfo): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return info.isSoftwareOnly
        val n = info.name.lowercase()
        return n.startsWith("omx.google.") || n.startsWith("c2.android.")
    }

    /**
     * Formato de video para [info].
     *
     * @param conPerfil pedir el mejor perfil (High en H.264, que comprime
     *   bastante mejor que el Baseline que algunos ponen por defecto). Si el
     *   codificador lo rechaza se vuelve a intentar sin.
     */
    fun formatoVideo(info: MediaCodecInfo, mime: String, ancho: Int, alto: Int, bitrate: Int, conPerfil: Boolean): MediaFormat {
        val caps = info.getCapabilitiesForType(mime)
        val f = MediaFormat.createVideoFormat(mime, ancho, alto)
        f.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        val maximo = caps.videoCapabilities?.bitrateRange?.upper ?: Int.MAX_VALUE
        f.setInteger(MediaFormat.KEY_BIT_RATE, bitrate.coerceAtMost(maximo))
        f.setInteger(MediaFormat.KEY_FRAME_RATE, 30)
        // Un fotograma completo cada pocos segundos, y ademas en cada corte (se
        // pide aparte). Mas separados = menos peso; los cortes ya traen el suyo.
        f.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 4)
        // Bitrate variable: gasta menos en lo facil. El constante rellena
        // con bits de sobra lo que no los necesita.
        if (caps.encoderCapabilities?.isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR) == true) {
            f.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
        }
        if (conPerfil) {
            val quiero = if (mime == AVC) MediaCodecInfo.CodecProfileLevel.AVCProfileHigh
            else MediaCodecInfo.CodecProfileLevel.HEVCProfileMain
            val nivel = caps.profileLevels.filter { it.profile == quiero }.maxOfOrNull { it.level }
            if (nivel != null) {
                f.setInteger(MediaFormat.KEY_PROFILE, quiero)
                f.setInteger(MediaFormat.KEY_LEVEL, nivel)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // Colores de video normal (BT.709, rango limitado). Sin esto cada
            // reproductor adivina, y algunos lo ensenan lavado.
            f.setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT709)
            f.setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_LIMITED)
            f.setInteger(MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.COLOR_TRANSFER_SDR_VIDEO)
        }
        return f
    }

    /**
     * El primer codificador de video que arranca: H.265 si se pide y existe,
     * si no H.264; hardware antes que software; con perfil y, si no, sin.
     *
     * @return el codec configurado (sin arrancar) y su mime
     */
    fun video(ancho: Int, alto: Int, bitrate: Int, hevc: Boolean): Pair<MediaCodec, String> {
        val orden = if (hevc) listOf(HEVC, AVC) else listOf(AVC)
        var ultimo: Exception? = null
        for (mime in orden) {
            for (info in candidatos(mime, ancho, alto)) {
                for (perfil in listOf(true, false)) {
                    val codec = runCatching { MediaCodec.createByCodecName(info.name) }.getOrNull() ?: continue
                    try {
                        codec.configure(formatoVideo(info, mime, ancho, alto, bitrate, perfil), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                        return codec to mime
                    } catch (e: Exception) {
                        ultimo = e
                        codec.release()
                    }
                }
            }
        }
        throw IllegalStateException("Este telefono no tiene un codificador de video que acepte ${ancho}x$alto", ultimo)
    }

    /** AAC-LC estereo a 48 kHz: lo que reproduce absolutamente todo. */
    fun audio(kbps: Int): MediaCodec {
        val f = MediaFormat.createAudioFormat(AAC, 48_000, 2)
        f.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        f.setInteger(MediaFormat.KEY_BIT_RATE, kbps * 1000)
        f.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384)
        val codec = MediaCodec.createEncoderByType(AAC)
        codec.configure(f, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        return codec
    }
}
