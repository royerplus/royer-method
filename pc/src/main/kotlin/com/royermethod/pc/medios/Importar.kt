package com.royermethod.pc.medios

import com.royermethod.app.editor.modelo.Origen
import com.royermethod.app.editor.modelo.TipoOrigen
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.jetbrains.skia.Image
import java.io.File

/**
 * De un archivo del PC a un [Origen] del proyecto. La direccion es la del
 * archivo ("file:/C:/..."): el proyecto lo vuelve a abrir de ahi otro dia.
 */
object Importar {
    private val imagenes = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "avif")
    private val sonidos = setOf("mp3", "wav", "m4a", "aac", "ogg", "opus", "flac", "wma")

    fun origen(f: File): Origen? {
        if (!f.isFile) return null
        val ext = f.extension.lowercase()
        return when {
            ext in imagenes -> imagen(f) ?: video(f)
            ext in sonidos -> sonido(f)
            else -> video(f) ?: imagen(f) ?: sonido(f)
        }
    }

    private fun uri(f: File) = f.absoluteFile.toURI().toString()

    private fun imagen(f: File): Origen? = runCatching {
        Image.makeFromEncoded(f.readBytes()).use { img ->
            if (img.width <= 0 || img.height <= 0) return null
            // Skia ya la deja derecha segun su EXIF: sin giro aparte
            Origen(uri(f), f.name, TipoOrigen.IMAGEN, img.width, img.height, 0, 0f, false, 0, f.length())
        }
    }.getOrNull()

    private fun video(f: File): Origen? {
        val g = FFmpegFrameGrabber(f)
        return try {
            g.start()
            if (!g.hasVideo() || g.imageWidth <= 0) return null
            val dur = g.lengthInTime
            if (dur <= 0) return null
            val giro = Math.floorMod(-Math.round(g.displayRotation).toInt(), 360).let { (it + 45) / 90 * 90 % 360 }
            val fps = g.videoFrameRate.toFloat().takeIf { it.isFinite() && it > 0f } ?: 0f
            Origen(uri(f), f.name, TipoOrigen.VIDEO, g.imageWidth, g.imageHeight, dur, fps, g.hasAudio(), giro, f.length())
        } catch (e: Exception) {
            null
        } finally {
            runCatching { g.release() }
        }
    }

    private fun sonido(f: File): Origen? {
        val g = FFmpegFrameGrabber(f)
        return try {
            g.start()
            val dur = g.lengthInTime
            if (!g.hasAudio() || dur <= 0) null
            else Origen(uri(f), f.name, TipoOrigen.AUDIO, duracionUs = dur, conAudio = true, bytes = f.length())
        } catch (e: Exception) {
            null
        } finally {
            runCatching { g.release() }
        }
    }
}
