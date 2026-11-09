package com.royermethod.app.editor.motor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.royermethod.app.editor.modelo.Alineacion
import com.royermethod.app.editor.modelo.Fuente
import com.royermethod.app.editor.modelo.Medidor
import com.royermethod.app.editor.modelo.Texto
import kotlin.math.ceil

/**
 * Maquetar y pintar textos.
 *
 * El texto se maqueta una vez al tamano base (escala 1 sobre un lienzo de
 * cierto alto) y, si se amplia, se pinta esa misma maqueta escalada: asi las
 * lineas se parten igual a cualquier tamano y el texto no salta de forma al
 * hacer zoom, pero se dibuja nitido porque se pinta en vectorial, no se
 * estira un mapa de bits.
 */
object MaquetaTexto {

    /**
     * Lo que se mide para la escena: videos e imagenes por sus metadatos,
     * textos maquetados igual que se van a dibujar. Lo comparten la vista
     * previa, la exportacion y la pantalla (para el marco de lo elegido).
     */
    val medidor = Medidor { clip, altoLienzo ->
        val t = clip.texto
        val o = clip.origen
        when {
            t != null -> medidas(t, altoLienzo)
            o != null -> o.anchoVisible.toFloat() to o.altoVisible.toFloat()
            else -> 1f to 1f
        }
    }

    private class Maqueta(val layout: StaticLayout, val ancho: Float, val alto: Float, val margen: Float)

    private val cache = object : LinkedHashMap<Pair<Texto, Int>, Maqueta>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<Texto, Int>, Maqueta>?) = size > 48
    }

    /** Ancho y alto del texto pintado (con su caja y contorno), en pixeles del lienzo. */
    @Synchronized
    fun medidas(texto: Texto, altoLienzo: Int): Pair<Float, Float> {
        val m = maqueta(texto, altoLienzo)
        return m.ancho to m.alto
    }

    /** El texto en un mapa de bits, [escala] veces el tamano base. */
    @Synchronized
    fun pintar(texto: Texto, altoLienzo: Int, escala: Float): Bitmap {
        val m = maqueta(texto, altoLienzo)
        val w = ceil(m.ancho * escala).toInt().coerceIn(1, 4096)
        val h = ceil(m.alto * escala).toInt().coerceIn(1, 4096)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val lienzo = Canvas(bmp)
        lienzo.scale(w / m.ancho, h / m.alto)
        val tam = texto.tamano * altoLienzo

        if (texto.fondo != 0) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = texto.fondo }
            val r = tam * 0.28f
            lienzo.drawRoundRect(RectF(0f, 0f, m.ancho, m.alto), r, r, p)
        }

        lienzo.translate(m.margen, m.margen)
        val pintura = m.layout.paint
        if (texto.contorno > 0f) {
            pintura.style = Paint.Style.STROKE
            pintura.strokeJoin = Paint.Join.ROUND
            pintura.strokeWidth = texto.contorno * tam
            pintura.color = texto.colorContorno
            pintura.clearShadowLayer()
            m.layout.draw(lienzo)
        }
        pintura.style = Paint.Style.FILL
        pintura.color = texto.color
        if (texto.sombra && texto.fondo == 0 && texto.sombraOpacidad > 0f) {
            val ang = Math.toRadians(texto.sombraAngulo.toDouble())
            val d = texto.sombraDistancia * tam
            val sc = texto.sombraColor
            val alfa = (texto.sombraOpacidad.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
            // Un radio 0 apaga la sombra en Android: lo minimo es medio pixel
            pintura.setShadowLayer(
                maxOf(texto.sombraDesenfoque * tam, 0.5f), (kotlin.math.cos(ang) * d).toFloat(), (kotlin.math.sin(ang) * d).toFloat(),
                Color.argb(alfa, Color.red(sc), Color.green(sc), Color.blue(sc))
            )
        } else {
            pintura.clearShadowLayer()
        }
        m.layout.draw(lienzo)
        pintura.clearShadowLayer()
        return bmp
    }

    private fun maqueta(texto: Texto, altoLienzo: Int): Maqueta = cache.getOrPut(texto to altoLienzo) {
        val tam = texto.tamano * altoLienzo
        val pintura = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = tam
            typeface = tipo(texto.fuente, texto.negrita, texto.cursiva)
            letterSpacing = texto.espaciado
            color = texto.color
        }
        // Ancho maximo: el lienzo en vertical menos margenes. Se mide con el
        // alto porque el formato lo puede cambiar el usuario y el texto no
        // deberia partirse distinto por eso.
        val maximo = (altoLienzo * 0.5f).toInt().coerceAtLeast(1)
        val contenido = texto.contenido.ifEmpty { " " }
        val alinea = when (texto.alineacion) {
            Alineacion.IZQUIERDA -> Layout.Alignment.ALIGN_NORMAL
            Alineacion.CENTRO -> Layout.Alignment.ALIGN_CENTER
            Alineacion.DERECHA -> Layout.Alignment.ALIGN_OPPOSITE
        }
        var anchoLineas = 0f
        val previo = StaticLayout.Builder.obtain(contenido, 0, contenido.length, pintura, maximo).build()
        for (i in 0 until previo.lineCount) anchoLineas = maxOf(anchoLineas, previo.getLineWidth(i))
        val anchoLayout = ceil(anchoLineas).toInt().coerceIn(1, maximo)
        val layout = StaticLayout.Builder.obtain(contenido, 0, contenido.length, pintura, anchoLayout)
            .setAlignment(alinea)
            .setIncludePad(true)
            .setLineSpacing(0f, texto.interlineado.coerceIn(0.6f, 2.5f))
            .build()
        // Margen para el contorno, la sombra y la caja de fondo (con los
        // valores de siempre la sombra cabe en el 0.2 de antes)
        val sombra = if (texto.sombra && texto.fondo == 0) (texto.sombraDistancia + texto.sombraDesenfoque) * 1.1f else 0f
        val margen = tam * maxOf(if (texto.fondo != 0) 0.45f else 0.2f, sombra) + texto.contorno * tam
        Maqueta(layout, anchoLayout + margen * 2, layout.height + margen * 2, margen)
    }

    fun tipo(f: Fuente, negrita: Boolean, cursiva: Boolean = false): Typeface {
        val estilo = when {
            negrita && cursiva -> Typeface.BOLD_ITALIC
            negrita -> Typeface.BOLD
            cursiva -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        return when (f) {
            Fuente.SANS -> Typeface.create(Typeface.SANS_SERIF, estilo)
            Fuente.CONDENSADA -> Typeface.create("sans-serif-condensed", estilo)
            Fuente.SERIF -> Typeface.create(Typeface.SERIF, estilo)
            Fuente.MONO -> Typeface.create(Typeface.MONOSPACE, estilo)
            Fuente.REDONDA -> Typeface.create("casual", estilo)
        }
    }
}
