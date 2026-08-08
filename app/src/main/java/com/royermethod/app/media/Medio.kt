package com.royermethod.app.media

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * Lo que sabemos del archivo que ha elegido el usuario.
 *
 * Se lee una sola vez al elegirlo. Hace falta antes de procesar nada porque
 * media app depende de ello: sin saber a cuantos fps viene el video no se
 * puede decidir cuantos frames generar, y sin saber su tamano no se puede
 * decir si bajar la resolucion aporta algo.
 */
data class Medio(
    val uri: Uri,
    val nombre: String,
    val bytes: Long,
    val ancho: Int,
    val alto: Int,
    /**
     * Lo dice el selector, no los metadatos.
     *
     * Antes esto se deducia de si la duracion era mayor que cero, y era
     * fragil: un video al que no se le puede leer la duracion habria acabado
     * en el camino de las imagenes y habria reventado. El usuario ya nos dijo
     * que buscaba al abrir el selector; esa es la fuente fiable.
     */
    val esVideo: Boolean,
    /** JPG, PNG, WEBP... Para poder ofrecer "dejarlo como esta". */
    val formato: String = "",
    /** Milisegundos. 0 en imagenes. */
    val duracionMs: Int = 0,
    /** 0 si no se ha podido averiguar. */
    val fps: Int = 0,
    val miniatura: ImageBitmap? = null
) {

    /**
     * La linea de datos de debajo de la vista previa.
     *
     * Sin el nombre del archivo a proposito: cabe justo, y un nombre de
     * camara se come el ancho entero y deja fuera los fps, que es el dato
     * del que dependen los ajustes. El nombre va arriba, en la etiqueta.
     */
    fun ficha(): String {
        val partes = mutableListOf<String>()
        // Ni duracion ni fps: la duracion ya sale en la barra de tiempo, y los
        // fps se ven en los ajustes, donde las opciones que el archivo no
        // permite salen bloqueadas. La linea comparte fila con dos botones y
        // no da para mas.
        if (!esVideo && formato.isNotEmpty()) partes.add(formato)
        if (ancho > 0 && alto > 0) partes.add("${ancho}x$alto")
        partes.add(peso(bytes))
        return partes.joinToString(" · ")
    }
}

fun tiempo(ms: Int): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

fun peso(bytes: Long): String = when {
    bytes >= 1048576L -> "%.1f MB".format(bytes / 1048576.0)
    bytes > 0L -> "%.0f KB".format(bytes / 1024.0)
    else -> "?"
}

/**
 * Lee los datos del archivo. Va a disco y decodifica, asi que tiene que
 * llamarse fuera del hilo principal.
 */
fun leerMedio(context: Context, uri: Uri, video: Boolean): Medio? {
    var nombre = "archivo"
    var bytes = 0L
    runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val iN = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val iT = c.getColumnIndex(OpenableColumns.SIZE)
            if (c.moveToFirst()) {
                if (iN >= 0) nombre = c.getString(iN) ?: nombre
                if (iT >= 0) bytes = c.getLong(iT)
            }
        }
    }

    return if (video) leerVideo(context, uri, nombre, bytes)
    else leerImagen(context, uri, nombre, bytes)
}

private fun leerVideo(context: Context, uri: Uri, nombre: String, bytes: Long): Medio? {
    val r = MediaMetadataRetriever()
    return try {
        r.setDataSource(context, uri)
        fun clave(k: Int) = r.extractMetadata(k)?.toIntOrNull() ?: 0

        var ancho = clave(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
        var alto = clave(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
        // Un video grabado en vertical suele venir apaisado con una marca de
        // giro. Sin cruzarla, la app ensenaria 1920x1080 en un video que el
        // usuario ve de pie, y elegiria mal la resolucion de salida.
        val giro = clave(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
        if (giro == 90 || giro == 270) {
            val t = ancho; ancho = alto; alto = t
        }

        Medio(
            uri = uri,
            nombre = nombre,
            bytes = bytes,
            esVideo = true,
            ancho = ancho,
            alto = alto,
            duracionMs = clave(MediaMetadataRetriever.METADATA_KEY_DURATION),
            fps = fpsDe(context, uri),
            miniatura = runCatching {
                r.getFrameAtTime(0L)?.asImageBitmap()
            }.getOrNull()
        )
    } catch (e: Exception) {
        null
    } finally {
        runCatching { r.release() }
    }
}

/**
 * Los fps no salen de los metadatos generales: ahi el dato es opcional y
 * casi nunca viene. Estan en el formato de la pista de video, que es donde
 * el codificador los deja siempre.
 */
private fun fpsDe(context: Context, uri: Uri): Int {
    val ex = MediaExtractor()
    return try {
        ex.setDataSource(context, uri, null)
        var fps = 0
        for (i in 0 until ex.trackCount) {
            val f = ex.getTrackFormat(i)
            val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
            if (!mime.startsWith("video/")) continue
            if (!f.containsKey(MediaFormat.KEY_FRAME_RATE)) continue
            // Segun quien lo escribiera viene como entero o como float
            fps = runCatching { f.getInteger(MediaFormat.KEY_FRAME_RATE) }
                .getOrElse { runCatching { f.getFloat(MediaFormat.KEY_FRAME_RATE).toInt() }.getOrDefault(0) }
            break
        }
        fps
    } catch (e: Exception) {
        0
    } finally {
        runCatching { ex.release() }
    }
}

private fun leerImagen(context: Context, uri: Uri, nombre: String, bytes: Long): Medio? {
    // Primera pasada solo para las medidas, sin traer los pixeles a memoria
    val medidas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    runCatching {
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, medidas)
        }
    }
    if (medidas.outWidth <= 0) return null

    // Segunda pasada reducida: la vista previa no necesita mas de unos 1600px
    // y una foto de movil entera son 50 MB de bitmap en memoria.
    val opciones = BitmapFactory.Options().apply {
        inSampleSize = generateSequence(1) { it * 2 }
            .first { medidas.outWidth / it <= 1600 && medidas.outHeight / it <= 1600 }
    }
    val bmp = runCatching {
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opciones)
        }
    }.getOrNull()

    return Medio(
        uri = uri,
        nombre = nombre,
        bytes = bytes,
        esVideo = false,
        // outMimeType lo dice el propio decodificador, no la extension del
        // nombre: hay archivos .jpg que por dentro son PNG.
        formato = when (medidas.outMimeType) {
            "image/jpeg" -> "JPG"
            "image/png" -> "PNG"
            "image/webp" -> "WEBP"
            else -> medidas.outMimeType?.substringAfter('/')?.uppercase() ?: ""
        },
        ancho = medidas.outWidth,
        alto = medidas.outHeight,
        miniatura = bmp?.asImageBitmap()
    )
}
