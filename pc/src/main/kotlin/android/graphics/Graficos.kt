package android.graphics

import androidx.compose.ui.text.font.FontFamily
import java.awt.image.BufferedImage

/**
 * Un mapa de bits del PC con el nombre del de Android. Por dentro es una
 * imagen de AWT premultiplicada, la que pinta MaquetaTexto y sube FuenteImagen.
 */
class Bitmap(val imagen: BufferedImage) {
    val width: Int get() = imagen.width
    val height: Int get() = imagen.height
    fun recycle() {}

    companion object {
        fun createBitmap(w: Int, h: Int): Bitmap = Bitmap(BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB_PRE))
    }
}

object Color {
    fun alpha(c: Int) = c ushr 24
    fun red(c: Int) = (c shr 16) and 0xFF
    fun green(c: Int) = (c shr 8) and 0xFF
    fun blue(c: Int) = c and 0xFF
    fun argb(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b
    fun rgb(r: Int, g: Int, b: Int) = argb(255, r, g, b)
}

/** La letra de un texto: la de AWT para pintarla y la de Compose para la interfaz. */
class Typeface(val awt: java.awt.Font, val familia: FontFamily)
