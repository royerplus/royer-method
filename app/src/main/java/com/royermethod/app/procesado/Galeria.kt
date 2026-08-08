package com.royermethod.app.procesado

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val CARPETA = "Royer Method"

/**
 * Mete el archivo en la galeria.
 *
 * Hasta Android 9 la galeria es una carpeta del almacenamiento y hay que
 * copiar el archivo ahi y avisar. Desde Android 10 es una base de datos con
 * permisos por archivo: se pide un hueco, se escribe dentro y se marca como
 * terminado. Mientras esta a medias queda con IS_PENDING para que ninguna app
 * lo ensene a medio escribir.
 */
fun guardarEnGaleria(context: Context, origen: File, esVideo: Boolean): Uri? {
    val nombre = nombreNuevo(esVideo)
    val tipo = if (esVideo) "video/mp4" else "image/jpeg"
    val coleccion = if (esVideo) {
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }
    val subcarpeta = if (esVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES

    val datos = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, nombre)
        put(MediaStore.MediaColumns.MIME_TYPE, tipo)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$subcarpeta/$CARPETA")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
    }

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        return guardarAntiguo(context, origen, nombre, subcarpeta, coleccion, datos)
    }

    val destino = context.contentResolver.insert(coleccion, datos) ?: return null
    return try {
        context.contentResolver.openOutputStream(destino)?.use { salida ->
            origen.inputStream().use { it.copyTo(salida) }
        } ?: return null
        context.contentResolver.update(
            destino,
            ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
            null, null
        )
        destino
    } catch (e: Exception) {
        runCatching { context.contentResolver.delete(destino, null, null) }
        null
    }
}

@Suppress("DEPRECATION")
private fun guardarAntiguo(
    context: Context,
    origen: File,
    nombre: String,
    subcarpeta: String,
    coleccion: Uri,
    datos: ContentValues
): Uri? = try {
    val carpeta = File(Environment.getExternalStoragePublicDirectory(subcarpeta), CARPETA)
    carpeta.mkdirs()
    val destino = File(carpeta, nombre)
    origen.inputStream().use { entrada ->
        destino.outputStream().use { entrada.copyTo(it) }
    }
    datos.put(MediaStore.MediaColumns.DATA, destino.absolutePath)
    context.contentResolver.insert(coleccion, datos)
} catch (e: Exception) {
    null
}

private fun nombreNuevo(esVideo: Boolean): String {
    val sello = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    return if (esVideo) "royer_$sello.mp4" else "royer_$sello.jpg"
}
