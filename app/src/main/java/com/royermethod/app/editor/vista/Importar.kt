package com.royermethod.app.editor.vista

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.royermethod.app.editor.modelo.Origen
import com.royermethod.app.editor.modelo.TipoOrigen

/**
 * De un archivo elegido por el usuario a un [Origen] del proyecto.
 *
 * Tambien pide permiso permanente para leerlo: el proyecto se guarda y se
 * vuelve a abrir otro dia, y sin ese permiso el archivo dejaria de poderse
 * leer en cuanto se cerrara la app.
 */
object Importar {

    fun origen(context: Context, uri: Uri): Origen? {
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        var nombre = uri.lastPathSegment ?: "archivo"
        var bytes = 0L
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val iN = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val iT = c.getColumnIndex(OpenableColumns.SIZE)
                    if (iN >= 0) nombre = c.getString(iN) ?: nombre
                    if (iT >= 0) bytes = c.getLong(iT)
                }
            }
        }
        val mime = context.contentResolver.getType(uri) ?: ""
        return when {
            mime.startsWith("image/") -> imagen(context, uri, nombre, bytes)
            mime.startsWith("audio/") -> sonido(context, uri, nombre, bytes)
            else -> video(context, uri, nombre, bytes) ?: imagen(context, uri, nombre, bytes)
        }
    }

    private fun video(context: Context, uri: Uri, nombre: String, bytes: Long): Origen? {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, uri)
            fun clave(k: Int) = r.extractMetadata(k)?.toLongOrNull() ?: 0L
            if (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) != "yes") return null
            val ancho = clave(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH).toInt()
            val alto = clave(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT).toInt()
            val giro = clave(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION).toInt()
            val dur = clave(MediaMetadataRetriever.METADATA_KEY_DURATION) * 1000
            val audio = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
            if (ancho <= 0 || alto <= 0 || dur <= 0) return null
            return Origen(uri.toString(), nombre, TipoOrigen.VIDEO, ancho, alto, dur, fps(context, uri), audio, giro, bytes)
        } catch (e: Exception) {
            return null
        } finally {
            runCatching { r.release() }
        }
    }

    private fun fps(context: Context, uri: Uri): Float {
        val ex = MediaExtractor()
        return try {
            ex.setDataSource(context, uri, null)
            (0 until ex.trackCount).map { ex.getTrackFormat(it) }
                .firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }
                ?.let { f -> if (f.containsKey(MediaFormat.KEY_FRAME_RATE)) runCatching { f.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat() }.getOrElse { f.getFloat(MediaFormat.KEY_FRAME_RATE) } else 0f }
                ?: 0f
        } catch (e: Exception) {
            0f
        } finally {
            ex.release()
        }
    }

    private fun imagen(context: Context, uri: Uri, nombre: String, bytes: Long): Origen? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) } }
        if (o.outWidth <= 0 || o.outHeight <= 0) return null
        // Las fotos del movil vienen tumbadas y una etiqueta dice como girarlas
        val giro = runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        }.getOrDefault(0)
        return Origen(uri.toString(), nombre, TipoOrigen.IMAGEN, o.outWidth, o.outHeight, 0, 0f, false, giro, bytes)
    }

    private fun sonido(context: Context, uri: Uri, nombre: String, bytes: Long): Origen? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, uri)
            val dur = (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) * 1000
            if (dur <= 0) null else Origen(uri.toString(), nombre, TipoOrigen.AUDIO, duracionUs = dur, conAudio = true, bytes = bytes)
        } catch (e: Exception) {
            null
        } finally {
            runCatching { r.release() }
        }
    }
}
