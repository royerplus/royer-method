package com.royermethod.pc.medios

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.royermethod.app.editor.audio.DecodificadorAudio
import com.royermethod.app.editor.audio.Pcm
import com.royermethod.app.editor.modelo.Origen
import com.royermethod.app.editor.motor.FuenteImagen
import com.royermethod.app.editor.ui.Recursos
import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import javax.swing.SwingUtilities
import kotlin.math.abs
import kotlin.math.max

/**
 * Miniaturas, fotogramas y ondas de sonido para la linea de tiempo (PC).
 * Como en el telefono: se piden, llega null, y cuando estan listas la
 * pantalla se repinta sola porque van en mapas de estado de Compose.
 */
class RecursosPc(private val context: Context) : Recursos {
    private val hilo = Executors.newSingleThreadExecutor { Thread(it, "miniaturas").apply { isDaemon = true; priority = Thread.MIN_PRIORITY } }
    private val hiloFotos = Executors.newSingleThreadExecutor { Thread(it, "fotograma").apply { isDaemon = true } }
    private val hiloOndas = Executors.newSingleThreadExecutor { Thread(it, "ondas").apply { isDaemon = true; priority = Thread.MIN_PRIORITY } }
    private val miniaturas = mutableStateMapOf<String, ImageBitmap>()
    private val fotogramas = mutableStateMapOf<String, ImageBitmap>()
    private val ondas = mutableStateMapOf<String, FloatArray>()
    private val pedidas = HashSet<String>()
    private val usoMiniaturas = LinkedHashMap<String, Unit>(64, 0.75f, true)
    @Volatile
    private var fotogramaPendiente: Pair<Origen, Long>? = null
    @Volatile
    private var cerrado = false

    override fun miniatura(o: Origen, tUs: Long): ImageBitmap? {
        val clave = "${o.uri}@$tUs"
        usoMiniaturas[clave] = Unit
        if (usoMiniaturas.size > MAX_MINIATURAS) {
            val vieja = usoMiniaturas.keys.first()
            usoMiniaturas.remove(vieja)
            pedidas.remove(vieja)
            SwingUtilities.invokeLater { miniaturas.remove(vieja) }
        }
        miniaturas[clave]?.let { return it }
        if (pedidas.add(clave)) hilo.execute {
            if (cerrado) return@execute
            val b = runCatching { cuadro(o, tUs, LADO, exacto = false) }.getOrNull()
            if (b != null) SwingUtilities.invokeLater { if (clave in pedidas) miniaturas[clave] = b }
        }
        return null
    }

    override fun fotograma(o: Origen, tUs: Long): ImageBitmap? {
        val clave = "${o.uri}@$tUs"
        fotogramas[clave]?.let { return it }
        val iniciar = fotogramaPendiente == null
        fotogramaPendiente = o to tUs
        if (iniciar) hiloFotos.execute {
            while (!cerrado) {
                val (po, pt) = fotogramaPendiente ?: break
                fotogramaPendiente = null
                val b = runCatching { cuadro(po, pt, LADO_FOTOGRAMA, exacto = true) }.getOrNull() ?: continue
                SwingUtilities.invokeLater {
                    if (fotogramas.size > 6) fotogramas.keys.firstOrNull()?.let { fotogramas.remove(it) }
                    fotogramas["${po.uri}@$pt"] = b
                }
            }
        }
        return null
    }

    /** Un fotograma pequeno (lado menor [lado]) del archivo. */
    private fun cuadro(o: Origen, tUs: Long, lado: Int, exacto: Boolean): ImageBitmap? {
        val archivo = Uri.parse(o.uri)
        if (o.esImagen) {
            val b = FuenteImagen.decodificar(context, archivo, lado * 2) ?: return null
            return b.asImageBitmap()
        }
        val g = FFmpegFrameGrabber(archivo.archivo())
        try {
            g.pixelFormat = avutil.AV_PIX_FMT_RGBA
            g.audioStream = -1
            val escala = lado.toFloat() / maxOf(1, minOf(o.ancho, o.alto))
            if (escala < 1f) {
                g.imageWidth = max(2, (o.ancho * escala).toInt())
                g.imageHeight = max(2, (o.alto * escala).toInt())
            }
            g.start()
            if (tUs > 0) {
                if (exacto) g.setVideoTimestamp(tUs) else runCatching { g.setVideoTimestamp(tUs) }
            }
            val f = g.grabImage() ?: return null
            val buf = f.image[0] as ByteBuffer
            val w = f.imageWidth
            val h = f.imageHeight
            val fila = w * 4
            val bytes = ByteArray(fila * h)
            val paso = maxOf(f.imageStride, fila)
            val d = buf.duplicate()
            for (y in 0 until h) { d.position(y * paso); d.get(bytes, y * fila, fila) }
            val img = Image.makeRaster(ImageInfo(w, h, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL), bytes, fila)
            return girar(img, o.giro)
        } finally {
            runCatching { g.release() }
        }
    }

    private fun girar(img: Image, giro: Int): ImageBitmap {
        if (giro == 0) return androidx.compose.ui.graphics.toComposeImageBitmapDe(img)
        val w = if (giro % 180 == 0) img.width else img.height
        val h = if (giro % 180 == 0) img.height else img.width
        val sup = org.jetbrains.skia.Surface.makeRasterN32Premul(w, h)
        sup.canvas.translate(w / 2f, h / 2f)
        sup.canvas.rotate(giro.toFloat())
        sup.canvas.drawImage(img, -img.width / 2f, -img.height / 2f)
        return androidx.compose.ui.graphics.toComposeImageBitmapDe(sup.makeImageSnapshot())
    }

    override fun onda(o: Origen): FloatArray? {
        ondas[o.uri]?.let { return it }
        if (pedidas.add("onda:" + o.uri)) hiloOndas.execute {
            runCatching { picos(o) }.getOrNull()?.let { r -> SwingUtilities.invokeLater { ondas[o.uri] = r } }
        }
        return null
    }

    private fun picos(o: Origen): FloatArray? {
        val d = DecodificadorAudio.abrir(context, Uri.parse(o.uri), 0) ?: return null
        try {
            val porPico = Pcm.frames(Recursos.PICOS_US).toInt()
            val total = (minOf(o.duracionUs, MAX_ONDA_US) / Recursos.PICOS_US).toInt() + 1
            val r = FloatArray(total)
            val buf = FloatArray(porPico * 2)
            var i = 0
            while (i < total) {
                val n = d.leer(buf, porPico)
                if (n <= 0) break
                var m = 0f
                for (k in 0 until n * 2) m = max(m, abs(buf[k]))
                r[i++] = m.coerceAtMost(1f)
            }
            val tope = r.maxOrNull()?.takeIf { it > 0.01f } ?: 1f
            for (k in r.indices) r[k] = r[k] / tope
            return r
        } finally {
            d.cerrar()
        }
    }

    fun cerrar() {
        cerrado = true
        hilo.shutdownNow(); hiloFotos.shutdownNow(); hiloOndas.shutdownNow()
    }

    private companion object {
        const val LADO = 160
        const val LADO_FOTOGRAMA = 640
        const val MAX_MINIATURAS = 240
        const val MAX_ONDA_US = 15 * 60 * 1_000_000L
    }
}
