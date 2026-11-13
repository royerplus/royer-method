package com.royermethod.app.editor.motor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.opengl.GLES20
import android.opengl.GLUtils

/**
 * Una imagen subida a la GPU una sola vez.
 *
 * Se decodifica ya reducida al tamano maximo al que se puede llegar a ver. Una
 * foto de movil entera son cincuenta megas en memoria para ensenarse en un
 * lienzo de 576 de ancho.
 */
class FuenteImagen private constructor(
    val textura: Int,
    val ancho: Int,
    val alto: Int
) {
    var usadaEn = -1L

    fun soltar() = GLES20.glDeleteTextures(1, intArrayOf(textura), 0)

    companion object {
        fun abrir(context: Context, uri: Uri, ladoMaximo: Int): FuenteImagen? {
            val bmp = decodificar(context, uri, ladoMaximo) ?: return null
            return subir(bmp).also { bmp.recycle() }
        }

        fun subir(bmp: Bitmap): FuenteImagen {
            val ids = IntArray(1)
            GLES20.glGenTextures(1, ids, 0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
            Lamina.filtros(GLES20.GL_TEXTURE_2D)
            // GLUtils sube la fila de arriba primero, que es la convencion de
            // toda la app, y premultiplicada, que es lo que espera el compositor.
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
            return FuenteImagen(ids[0], bmp.width, bmp.height)
        }

        /**
         * Decodifica derecha y sin pasarse de [ladoMaximo].
         *
         * La orientacion de las fotos va en los datos EXIF, no en los pixeles:
         * la camara guarda tumbado y apunta que hay que girar. Sin leerla, las
         * fotos verticales entrarian de lado.
         */
        fun decodificar(context: Context, uri: Uri, ladoMaximo: Int): Bitmap? {
            val medidas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, medidas) }
            }
            if (medidas.outWidth <= 0 || medidas.outHeight <= 0) return null
            var muestra = 1
            while (maxOf(medidas.outWidth, medidas.outHeight) / (muestra * 2) >= ladoMaximo) muestra *= 2
            val bmp = runCatching {
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
                        inSampleSize = muestra
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    })
                }
            }.getOrNull() ?: return null

            val giro = orientacion(context, uri)
            val lado = maxOf(bmp.width, bmp.height)
            val escala = if (lado > ladoMaximo) ladoMaximo.toFloat() / lado else 1f
            if (giro == 0 && escala == 1f) return bmp
            val m = Matrix().apply {
                postScale(escala, escala)
                postRotate(giro.toFloat())
            }
            val r = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            if (r !== bmp) bmp.recycle()
            return r
        }

        fun orientacion(context: Context, uri: Uri): Int = runCatching {
            context.contentResolver.openInputStream(uri)?.use { s ->
                when (ExifInterface(s).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        }.getOrDefault(0)
    }
}
