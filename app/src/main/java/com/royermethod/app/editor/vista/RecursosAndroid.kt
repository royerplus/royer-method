package com.royermethod.app.editor.vista

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.royermethod.app.editor.audio.DecodificadorAudio
import com.royermethod.app.editor.audio.Pcm
import com.royermethod.app.editor.modelo.Origen
import com.royermethod.app.editor.ui.Recursos
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max

/**
 * Miniaturas y ondas sacadas de los archivos, en un hilo aparte.
 *
 * Lo pedido se guarda en mapas de estado de Compose: la linea de tiempo pide,
 * recibe null, y cuando llega el dibujo se repinta sola. Cada cosa se pide
 * una sola vez aunque se dibuje cien veces.
 */
class RecursosAndroid(context: Context) : Recursos {

    private val app = context.applicationContext
    private val hilo = Executors.newSingleThreadExecutor { Thread(it, "miniaturas").apply { priority = Thread.MIN_PRIORITY } }
    private val hiloOndas = Executors.newSingleThreadExecutor { Thread(it, "ondas").apply { priority = Thread.MIN_PRIORITY } }
    private val miniaturas = mutableStateMapOf<String, ImageBitmap>()
    private val ondas = mutableStateMapOf<String, FloatArray>()
    private val pedidas = HashSet<String>()
    private val lectores = object : LinkedHashMap<String, MediaMetadataRetriever>(4, 0.75f, true) {
        override fun removeEldestEntry(e: MutableMap.MutableEntry<String, MediaMetadataRetriever>): Boolean {
            if (size > 3) { runCatching { e.value.release() }; return true }
            return false
        }
    }

    override fun miniatura(o: Origen, tUs: Long): ImageBitmap? {
        val clave = "${o.uri}@$tUs"
        miniaturas[clave]?.let { return it }
        if (pedidas.add(clave)) hilo.execute {
            val b = runCatching { if (o.esImagen) foto(o) else fotograma(o, tUs) }.getOrNull()
            if (b != null) miniaturas[clave] = b.asImageBitmap()
        }
        return null
    }

    private fun fotograma(o: Origen, tUs: Long): Bitmap? {
        val r = lectores.getOrPut(o.uri) { MediaMetadataRetriever().apply { setDataSource(app, Uri.parse(o.uri)) } }
        val lado = LADO
        val b = if (Build.VERSION.SDK_INT >= 27) {
            r.getScaledFrameAtTime(tUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, lado, lado)
        } else {
            r.getFrameAtTime(tUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { reducir(it) }
        }
        return b
    }

    private fun foto(o: Origen): Bitmap? {
        val opciones = BitmapFactory.Options().apply {
            inSampleSize = max(1, minOf(o.ancho, o.alto) / LADO)
        }
        val b = app.contentResolver.openInputStream(Uri.parse(o.uri))?.use { BitmapFactory.decodeStream(it, null, opciones) } ?: return null
        if (o.giro == 0) return b
        val m = Matrix().apply { postRotate(o.giro.toFloat()) }
        return Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
    }

    private fun reducir(b: Bitmap): Bitmap {
        val f = LADO.toFloat() / minOf(b.width, b.height)
        if (f >= 1f) return b
        return Bitmap.createScaledBitmap(b, (b.width * f).toInt().coerceAtLeast(1), (b.height * f).toInt().coerceAtLeast(1), true)
    }

    override fun onda(o: Origen): FloatArray? {
        ondas[o.uri]?.let { return it }
        if (pedidas.add("onda:" + o.uri)) hiloOndas.execute {
            runCatching { picos(o) }.getOrNull()?.let { ondas[o.uri] = it }
        }
        return null
    }

    /** Lo mas alto del sonido en cada trozo de [Recursos.PICOS_US]. */
    private fun picos(o: Origen): FloatArray? {
        val d = DecodificadorAudio.abrir(app, Uri.parse(o.uri), 0) ?: return null
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
            // Se normaliza para que una cancion bajita tambien se vea
            val tope = r.maxOrNull()?.takeIf { it > 0.01f } ?: 1f
            for (k in r.indices) r[k] = (r[k] / tope)
            return r
        } finally {
            d.cerrar()
        }
    }

    fun cerrar() {
        hilo.execute {
            lectores.values.forEach { runCatching { it.release() } }
            lectores.clear()
        }
        hilo.shutdown()
        hiloOndas.shutdownNow()
    }

    companion object {
        private const val LADO = 160
        private const val MAX_ONDA_US = 15 * 60 * 1_000_000L
    }
}
