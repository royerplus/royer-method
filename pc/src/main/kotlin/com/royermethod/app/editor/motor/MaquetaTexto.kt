package com.royermethod.app.editor.motor

import android.graphics.Bitmap
import android.graphics.Typeface
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font as FontArchivo
import com.royermethod.app.editor.modelo.Alineacion
import com.royermethod.app.editor.modelo.Fuente
import com.royermethod.app.editor.modelo.Medidor
import com.royermethod.app.editor.modelo.Texto
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.awt.RenderingHints
import java.awt.Shape
import java.awt.font.FontRenderContext
import java.awt.font.LineBreakMeasurer
import java.awt.font.TextAttribute
import java.awt.font.TextLayout
import java.awt.geom.AffineTransform
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.awt.image.ConvolveOp
import java.awt.image.Kernel
import java.io.File
import java.text.AttributedString
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * Maquetar y pintar textos (PC), con AWT.
 *
 * Igual que en el telefono: se maqueta una vez al tamano base y se pinta esa
 * maqueta escalada, en vectorial. Las lineas se parten a lo ancho de medio
 * alto del lienzo, con sus saltos de linea, contorno, sombra y caja.
 */
object MaquetaTexto {

    val medidor = Medidor { clip, altoLienzo ->
        val t = clip.texto
        val o = clip.origen
        when {
            t != null -> medidas(t, altoLienzo)
            o != null -> o.anchoVisible.toFloat() to o.altoVisible.toFloat()
            else -> 1f to 1f
        }
    }

    private class Linea(val layout: TextLayout, val x: Float, val base: Float)
    private class Maqueta(val lineas: List<Linea>, val ancho: Float, val alto: Float, val margen: Float, val subrayado: Boolean)

    private val contexto = FontRenderContext(null, true, true)

    private val cache = object : LinkedHashMap<Pair<Texto, Int>, Maqueta>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<Texto, Int>, Maqueta>?) = size > 48
    }

    @Synchronized
    fun medidas(texto: Texto, altoLienzo: Int): Pair<Float, Float> {
        val m = maqueta(texto, altoLienzo)
        return m.ancho to m.alto
    }

    @Synchronized
    fun pintar(texto: Texto, altoLienzo: Int, escala: Float): Bitmap {
        val m = maqueta(texto, altoLienzo)
        val w = ceil(m.ancho * escala).toInt().coerceIn(1, 4096)
        val h = ceil(m.alto * escala).toInt().coerceIn(1, 4096)
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB_PRE)
        val g = img.createGraphics()
        calidad(g)
        g.scale(w / m.ancho.toDouble(), h / m.alto.toDouble())
        val tam = texto.tamano * altoLienzo

        if (texto.fondo != 0) {
            g.color = java.awt.Color(texto.fondo, true)
            val r = tam * 0.28f * 2
            g.fill(RoundRectangle2D.Float(0f, 0f, m.ancho, m.alto, r, r))
        }

        val forma = java.awt.geom.Path2D.Float()
        for (l in m.lineas) {
            forma.append(l.layout.getOutline(AffineTransform.getTranslateInstance((m.margen + l.x).toDouble(), (m.margen + l.base).toDouble())), false)
            if (m.subrayado) {
                val grosor = maxOf(1f, tam * 0.06f)
                forma.append(java.awt.geom.Rectangle2D.Float(m.margen + l.x, m.margen + l.base + tam * 0.12f, l.layout.advance, grosor), false)
            }
        }

        if (texto.sombra && texto.fondo == 0 && texto.sombraOpacidad > 0f) {
            val ang = Math.toRadians(texto.sombraAngulo.toDouble())
            val d = texto.sombraDistancia * tam
            val radio = maxOf(texto.sombraDesenfoque * tam, 0.5f) * (w / m.ancho)
            val sc = texto.sombraColor
            val alfa = (texto.sombraOpacidad.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
            val sombra = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB_PRE)
            val gs = sombra.createGraphics()
            calidad(gs)
            gs.transform = g.transform
            gs.translate(cos(ang) * d, sin(ang) * d)
            gs.color = java.awt.Color((sc shr 16) and 255, (sc shr 8) and 255, sc and 255, alfa)
            gs.fill(forma)
            gs.dispose()
            val borrosa = desenfocar(sombra, radio)
            val t = g.transform
            g.transform = AffineTransform()
            g.drawImage(borrosa, 0, 0, null)
            g.transform = t
        }

        if (texto.contorno > 0f) {
            g.color = java.awt.Color(texto.colorContorno, true)
            g.stroke = BasicStroke(texto.contorno * tam, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            g.draw(forma)
        }
        g.composite = AlphaComposite.SrcOver
        g.color = java.awt.Color(texto.color, true)
        g.fill(forma)
        g.dispose()
        return Bitmap(img)
    }

    private fun calidad(g: java.awt.Graphics2D) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
    }

    /** Desenfoque gaussiano separable; [radio] como el de la sombra de Android. */
    private fun desenfocar(img: BufferedImage, radio: Float): BufferedImage {
        val sigma = radio * 0.57735f + 0.5f
        val n = (ceil(sigma * 3).toInt()).coerceIn(1, 60)
        val k = FloatArray(n * 2 + 1) { i -> val x = (i - n).toFloat(); exp(-(x * x) / (2 * sigma * sigma)) }
        val s = k.sum()
        for (i in k.indices) k[i] /= s
        val h = ConvolveOp(Kernel(k.size, 1, k), ConvolveOp.EDGE_ZERO_FILL, null)
        val v = ConvolveOp(Kernel(1, k.size, k), ConvolveOp.EDGE_ZERO_FILL, null)
        return v.filter(h.filter(img, null), null)
    }

    private fun maqueta(texto: Texto, altoLienzo: Int): Maqueta = cache.getOrPut(texto to altoLienzo) {
        val tam = texto.tamano * altoLienzo
        val letra = tipo(texto.fuente, texto.negrita, texto.cursiva).awt.deriveFont(tam)
        val maximo = (altoLienzo * 0.5f).coerceAtLeast(1f)
        val contenido = texto.contenido.ifEmpty { " " }
        val interlineado = texto.interlineado.coerceIn(0.6f, 2.5f)

        // Primero las lineas, partidas al ancho maximo; luego se alinean al ancho de la mas larga
        val partes = ArrayList<TextLayout?>()
        for (parrafo in contenido.split('\n')) {
            if (parrafo.isEmpty()) { partes.add(null); continue }
            val a = AttributedString(parrafo)
            a.addAttribute(TextAttribute.FONT, letra)
            if (texto.espaciado != 0f) a.addAttribute(TextAttribute.TRACKING, texto.espaciado)
            val medidor = LineBreakMeasurer(a.iterator, contexto)
            while (medidor.position < parrafo.length) partes.add(medidor.nextLayout(maximo))
        }
        val metricas = letra.getLineMetrics("Ag", contexto)
        val subida = metricas.ascent
        val bajada = metricas.descent
        val alto1 = (subida + bajada + metricas.leading) * interlineado
        val anchoLineas = partes.maxOfOrNull { it?.advance ?: 0f } ?: 1f
        val anchoLayout = ceil(anchoLineas).coerceIn(1f, maximo)
        val lineas = ArrayList<Linea>()
        var y = subida
        for (p in partes) {
            if (p != null) {
                val x = when (texto.alineacion) {
                    Alineacion.IZQUIERDA -> 0f
                    Alineacion.CENTRO -> (anchoLayout - p.advance) / 2f
                    Alineacion.DERECHA -> anchoLayout - p.advance
                }
                lineas.add(Linea(p, x, y))
            }
            y += alto1
        }
        val altoLayout = y - alto1 + bajada
        val sombra = if (texto.sombra && texto.fondo == 0) (texto.sombraDistancia + texto.sombraDesenfoque) * 1.1f else 0f
        val margen = tam * maxOf(if (texto.fondo != 0) 0.45f else 0.2f, sombra) + texto.contorno * tam
        Maqueta(lineas, anchoLayout + margen * 2, altoLayout + margen * 2, margen, texto.subrayado)
    }

    /** La letra de [f]: la mas parecida que tenga el PC a la del telefono. */
    fun tipo(f: Fuente, negrita: Boolean, cursiva: Boolean = false): Typeface = synchronized(tipos) {
        tipos.getOrPut(Triple(f, negrita, cursiva)) { cargar(f, negrita, cursiva) }
    }

    private val tipos = HashMap<Triple<Fuente, Boolean, Boolean>, Typeface>()

    private val instaladas: Set<String> by lazy {
        runCatching { GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.map { it.lowercase() }.toSet() }
            .getOrDefault(emptySet())
    }

    private fun primera(vararg nombres: String): String? = nombres.firstOrNull { it.lowercase() in instaladas }

    private fun cargar(f: Fuente, negrita: Boolean, cursiva: Boolean): Typeface {
        val nombre = when (f) {
            Fuente.SANS -> primera("Roboto", "Segoe UI", "Arial", "DejaVu Sans") ?: Font.SANS_SERIF
            Fuente.CONDENSADA -> primera("Roboto Condensed", "Arial Narrow", "Bahnschrift SemiCondensed", "DejaVu Sans Condensed") ?: Font.SANS_SERIF
            Fuente.SERIF -> primera("Noto Serif", "Georgia", "Cambria", "Times New Roman", "DejaVu Serif") ?: Font.SERIF
            Fuente.MONO -> primera("Cutive Mono", "Courier New", "Consolas", "DejaVu Sans Mono") ?: Font.MONOSPACED
            Fuente.REDONDA -> primera("Coming Soon", "Comic Sans MS", "Segoe Print") ?: Font.SANS_SERIF
            Fuente.MANUSCRITA -> primera("Dancing Script", "Segoe Script", "Lucida Handwriting", "Gabriola") ?: Font.SERIF
            Fuente.VERSALITAS -> primera("Carrois Gothic SC", "Copperplate Gothic Light", "Bahnschrift") ?: Font.SANS_SERIF
        }
        val estilo = (if (negrita) Font.BOLD else 0) or (if (cursiva) Font.ITALIC else 0)
        val awt = Font(nombre, estilo, 12)
        val familia = when (f) {
            Fuente.SERIF -> FontFamily.Serif
            Fuente.MONO -> FontFamily.Monospace
            Fuente.MANUSCRITA -> FontFamily.Cursive
            else -> FontFamily.SansSerif
        }
        return Typeface(awt, sistema(nombre, negrita, cursiva) ?: familia)
    }

    /** La misma letra para la interfaz, desde su archivo en Windows\Fonts si se encuentra. */
    private fun sistema(nombre: String, negrita: Boolean, cursiva: Boolean): FontFamily? = runCatching {
        val dir = File(System.getenv("WINDIR") ?: return@runCatching null, "Fonts")
        val base = nombre.replace(" ", "").lowercase()
        val archivo = dir.listFiles()?.firstOrNull { it.nameWithoutExtension.lowercase() == base && it.extension.lowercase() in setOf("ttf", "otf") }
            ?: return@runCatching null
        FontFamily(FontArchivo(archivo, if (negrita) FontWeight.Bold else FontWeight.Normal, if (cursiva) FontStyle.Italic else FontStyle.Normal))
    }.getOrNull()
}
