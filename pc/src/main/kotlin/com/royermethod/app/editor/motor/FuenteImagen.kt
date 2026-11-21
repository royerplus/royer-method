package com.royermethod.app.editor.motor

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.opengl.GLES20
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL12
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Una imagen subida a la GPU una sola vez (PC).
 *
 * La lee Skia (JPEG, PNG, WebP, GIF, BMP), que ya la deja derecha segun su
 * EXIF, y se reduce al tamano maximo al que se puede llegar a ver.
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
            return subir(bmp)
        }

        /** Sube un mapa de bits premultiplicado, fila de arriba primero. */
        fun subir(bmp: Bitmap): FuenteImagen {
            val img = aIntPre(bmp.imagen)
            val datos = (img.raster.dataBuffer as DataBufferInt).data
            val buf = ByteBuffer.allocateDirect(datos.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            buf.asIntBuffer().put(datos)
            buf.position(0)
            val ids = IntArray(1)
            GLES20.glGenTextures(1, ids, 0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
            Lamina.filtros(GLES20.GL_TEXTURE_2D)
            // Un int ARGB en little endian son los bytes B, G, R, A
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4)
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, img.width, img.height, 0, GL12.GL_BGRA, GL11.GL_UNSIGNED_BYTE, buf)
            return FuenteImagen(ids[0], img.width, img.height)
        }

        private fun aIntPre(i: BufferedImage): BufferedImage {
            if (i.type == BufferedImage.TYPE_INT_ARGB_PRE) return i
            val r = BufferedImage(i.width, i.height, BufferedImage.TYPE_INT_ARGB_PRE)
            r.createGraphics().apply { drawImage(i, 0, 0, null); dispose() }
            return r
        }

        /** Decodifica derecha y sin pasarse de [ladoMaximo]. */
        fun decodificar(context: Context, uri: Uri, ladoMaximo: Int): Bitmap? = runCatching {
            val bytes = uri.archivo().readBytes()
            val img = Image.makeFromEncoded(bytes)
            img.use { reducida(it, ladoMaximo) }
        }.getOrNull()

        /** La imagen de Skia a un mapa de bits, a [ladoMaximo] como mucho. */
        internal fun reducida(img: Image, ladoMaximo: Int): Bitmap {
            val lado = maxOf(img.width, img.height)
            val escala = if (lado > ladoMaximo) ladoMaximo.toFloat() / lado else 1f
            val w = (img.width * escala).toInt().coerceAtLeast(1)
            val h = (img.height * escala).toInt().coerceAtLeast(1)
            val info = ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.PREMUL)
            val sup = Surface.makeRaster(info)
            sup.use { s ->
                s.canvas.drawImageRect(
                    img, Rect.makeWH(img.width.toFloat(), img.height.toFloat()), Rect.makeWH(w.toFloat(), h.toFloat()),
                    FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR), Paint(), true
                )
                val salida = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB_PRE)
                val datos = (salida.raster.dataBuffer as DataBufferInt).data
                val px = s.makeImageSnapshot().use { snap ->
                    org.jetbrains.skia.Bitmap().use { b ->
                        b.allocPixels(info)
                        snap.readPixels(b, 0, 0)
                        b.readPixels(info, w * 4, 0, 0) ?: ByteArray(0)
                    }
                }
                ByteBuffer.wrap(px).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(datos, 0, minOf(datos.size, px.size / 4))
                return Bitmap(salida)
            }
        }

        fun orientacion(context: Context, uri: Uri): Int = 0
    }
}
