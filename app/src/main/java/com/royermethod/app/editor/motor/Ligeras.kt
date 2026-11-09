package com.royermethod.app.editor.motor

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.opengl.GLES20
import android.os.Bundle
import android.os.Process
import android.util.Log
import android.view.Surface
import com.royermethod.app.editor.exportar.Codificadores
import com.royermethod.app.editor.exportar.Contenedor
import com.royermethod.app.editor.modelo.Origen
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * Copias ligeras de los videos, solo para la vista previa.
 *
 * Un video de movil trae un fotograma clave cada 1 a 7 s, y para ensenar un
 * instante hay que decodificar desde el clave de antes. Hacia atras cada paso
 * del cabezal son cientos de fotogramas: por eso al arrastrar hacia atras la
 * imagen se quedaba quieta, y despues de mover el cabezal el play tardaba. La
 * copia lleva un clave cada medio segundo, sin fotogramas B y a lo sumo 720
 * de lado corto: cualquier instante sale en unos pocos fotogramas pequenos.
 *
 * Cada fotograma conserva su instante del original, sin girar (el giro lo
 * sigue poniendo FuenteVideo) y sin sonido. Exportar usa siempre el original.
 *
 * Se hacen en segundo plano, de una en una, y se paran mientras [ocupado]
 * (suena la vista previa, se arrastra el cabezal o se exporta). Van a la cache
 * de la app: si el sistema la vacia, se vuelven a hacer.
 */
object Ligeras {
    /** Una copia lista: [archivo] y lo que hay que sumar a sus instantes para tener los del original. */
    class Copia(val archivo: File, val corrimientoUs: Long)

    private const val ETIQUETA = "RoyerVista"
    /** Cambia si cambia como se hacen: las de antes dejan de valer. */
    private const val VERSION = 1

    private val listas = ConcurrentHashMap<String, Copia>()
    /** Originales que no necesitan copia o con los que fallo: no se reintentan en esta sesion. */
    private val sinCopia = ConcurrentHashMap.newKeySet<String>()
    private val pedidas = ConcurrentHashMap.newKeySet<String>()
    private val hilo = Executors.newSingleThreadExecutor { r ->
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            r.run()
        }, "copias-ligeras")
    }

    /** Mientras devuelva true, la copia en curso espera (no le quita el movil a la vista previa). */
    @Volatile
    var ocupado: () -> Boolean = { false }

    /** Se llama (en el hilo de las copias) cuando una queda lista, con la uri del original. */
    @Volatile
    var alListo: ((String) -> Unit)? = null

    /** La copia de [uri] si ya esta hecha. */
    fun de(uri: String): Copia? = listas[uri]

    /** La copia no se pudo abrir: se usa el original y no se vuelve a probar. */
    fun descartar(uri: String) {
        listas.remove(uri)?.let { runCatching { it.archivo.delete(); lado(it.archivo).delete() } }
        sinCopia.add(uri)
    }

    /** Encola las copias que falten de [origenes] (las imagenes se ignoran). */
    fun pedir(context: Context, origenes: Collection<Origen>) {
        val app = context.applicationContext
        for (o in origenes) {
            if (!o.esVideo || listas.containsKey(o.uri) || o.uri in sinCopia || !pedidas.add(o.uri)) continue
            hilo.execute {
                try {
                    preparar(app, o)
                } catch (e: Throwable) {
                    sinCopia.add(o.uri)
                    runCatching { Log.i(ETIQUETA, "copia ligera fallo: ${Uri.parse(o.uri).lastPathSegment}: $e") }
                } finally {
                    pedidas.remove(o.uri)
                }
            }
        }
    }

    private fun carpeta(context: Context) = File(context.cacheDir, "ligeras").apply { mkdirs() }

    /** El archivo de al lado, con el corrimiento: se escribe al final, y sin el la copia no vale. */
    private fun lado(archivo: File) = File(archivo.path + ".txt")

    private fun nombre(o: Origen): String {
        val texto = "$VERSION|${o.uri}|${o.bytes}|${o.duracionUs}"
        val h = MessageDigest.getInstance("SHA-1").digest(texto.toByteArray())
        return h.joinToString("") { "%02x".format(it) } + ".mp4"
    }

    private fun preparar(context: Context, o: Origen) {
        val dir = carpeta(context)
        val archivo = File(dir, nombre(o))
        val datos = lado(archivo)
        if (archivo.isFile && datos.isFile) {
            val c = datos.readText().trim().toLongOrNull()
            if (c != null) {
                archivo.setLastModified(System.currentTimeMillis())
                listar(o.uri, Copia(archivo, c))
                return
            }
        }
        val m = medir(context, Uri.parse(o.uri))
        if (m == null || !PlanLigera.haceFalta(m.ancho, m.alto, m.claveMaxUs) || m.hdr) {
            sinCopia.add(o.uri)
            return
        }
        val (w, h) = PlanLigera.medidas(m.ancho, m.alto)
        if (!Codificadores.hay(Codificadores.AVC, w, h)) {
            sinCopia.add(o.uri)
            return
        }
        val tmp = File(dir, archivo.name + ".tmp")
        val inicioNs = System.nanoTime()
        val corrimiento = hacer(context, o, tmp, w, h, m.fps)
        if (corrimiento == null) {
            tmp.delete()
            sinCopia.add(o.uri)
            return
        }
        archivo.delete()
        check(tmp.renameTo(archivo)) { "no se pudo guardar la copia" }
        datos.writeText(corrimiento.toString())
        runCatching {
            Log.i(ETIQUETA, "copia ligera de ${Uri.parse(o.uri).lastPathSegment}: ${w}x$h, " +
                "${archivo.length() / 1024} KB en ${(System.nanoTime() - inicioNs) / 1_000_000} ms")
        }
        PlanLigera.sobrantes(dir.listFiles()?.filter { it.name.endsWith(".mp4") }.orEmpty()
            .map { PlanLigera.Guardada(it.name, it.length(), it.lastModified()) }, LIMITE_BYTES)
            .forEach { n -> File(dir, n).let { it.delete(); lado(it).delete() } }
        listar(o.uri, Copia(archivo, corrimiento))
    }

    private fun listar(uri: String, c: Copia) {
        listas[uri] = c
        runCatching { alListo?.invoke(uri) }
    }

    private class Medida(val ancho: Int, val alto: Int, val fps: Float, val claveMaxUs: Long, val hdr: Boolean)

    /** Medidas, ritmo y separacion maxima entre claves, sin decodificar nada. */
    private fun medir(context: Context, uri: Uri): Medida? {
        val ex = MediaExtractor()
        try {
            ex.setDataSource(context, uri, null)
            var pista = -1
            for (i in 0 until ex.trackCount) {
                if (ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true) { pista = i; break }
            }
            if (pista < 0) return null
            ex.selectTrack(pista)
            val f = ex.getTrackFormat(pista)
            val w = f.getInteger(MediaFormat.KEY_WIDTH)
            val h = f.getInteger(MediaFormat.KEY_HEIGHT)
            val transferencia = if (f.containsKey(MediaFormat.KEY_COLOR_TRANSFER)) f.getInteger(MediaFormat.KEY_COLOR_TRANSFER) else 0
            val hdr = transferencia == MediaFormat.COLOR_TRANSFER_ST2084 || transferencia == MediaFormat.COLOR_TRANSFER_HLG
            var claveMax = 0L
            var ultimaClave = -1L
            var cuadros = 0
            var primero = Long.MAX_VALUE
            var ultimo = Long.MIN_VALUE
            while (true) {
                val t = ex.sampleTime
                if (t < 0) break
                cuadros++
                primero = minOf(primero, t); ultimo = maxOf(ultimo, t)
                if (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                    if (ultimaClave >= 0) claveMax = maxOf(claveMax, t - ultimaClave)
                    ultimaClave = t
                }
                if (!ex.advance()) break
            }
            if (cuadros < 2) return null
            if (ultimaClave >= 0) claveMax = maxOf(claveMax, ultimo - ultimaClave)
            val fps = if (f.containsKey(MediaFormat.KEY_FRAME_RATE)) runCatching { f.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat() }
                .recoverCatching { f.getFloat(MediaFormat.KEY_FRAME_RATE) }.getOrDefault(0f) else 0f
            val medido = (cuadros - 1) * 1e6f / maxOf(1L, ultimo - primero)
            return Medida(w, h, if (fps > 0f) fps else medido, claveMax, hdr)
        } finally {
            runCatching { ex.release() }
        }
    }

    /**
     * Hace la copia en [destino]. Devuelve el corrimiento (instante del
     * original menos el de la copia), o null si no salio bien.
     */
    private fun hacer(context: Context, o: Origen, destino: File, w: Int, h: Int, fps: Float): Long? {
        var codec: MediaCodec? = null
        var entrada: Surface? = null
        var egl: Egl? = null
        var fuente: FuenteVideo? = null
        var reduccion: Reduccion? = null
        var copia: Programa? = null
        var contenedor: Contenedor? = null
        try {
            codec = codificador(w, h, fps)
            entrada = codec.createInputSurface()
            val e = Egl(entrada, w, h, paraCodificador = true)
            egl = e
            e.activar()
            codec.start()
            val f = FuenteVideo(context, Uri.parse(o.uri), 0, vista = true)
            fuente = f
            val r = Reduccion()
            reduccion = r
            val p = Programa(Sombreadores.VERTICE, Sombreadores.COPIA_EXACTA)
            copia = p
            val c = Contenedor(destino, 1)
            contenedor = c
            val info = MediaCodec.BufferInfo()
            val pista = intArrayOf(-1)
            var primero = Long.MAX_VALUE
            var anterior = Long.MIN_VALUE
            var ultimaClave = Long.MIN_VALUE
            var cuadros = 0
            while (true) {
                while (ocupado()) Thread.sleep(150)
                if (!f.siguiente()) break
                val pts = f.ptsActual
                // Un instante repetido o hacia atras no se puede escribir en orden
                if (pts <= anterior) continue
                anterior = pts
                primero = minOf(primero, pts)
                if (ultimaClave == Long.MIN_VALUE || pts - ultimaClave >= PlanLigera.CLAVE_US) {
                    codec.setParameters(Bundle().apply { putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0) })
                    ultimaClave = pts
                }
                val l = f.copiar(w, h, r)
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
                GLES20.glViewport(0, 0, w, h)
                GLES20.glDisable(GLES20.GL_BLEND)
                p.usar()
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, l.textura)
                GLES20.glUniform1i(p.u("sTex"), 0)
                p.dibujar(voltear = false)
                e.marcarTiempo(pts * 1000L)
                check(e.publicar()) { "swap" }
                cuadros++
                vaciar(codec, c, info, pista, false)
            }
            codec.signalEndOfInputStream()
            vaciar(codec, c, info, pista, true)
            c.cerrar()
            contenedor = null
            if (cuadros < 2) return null
            return revisar(destino, cuadros, primero)
        } finally {
            runCatching { contenedor?.cerrar() }
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { fuente?.soltar() }
            runCatching { reduccion?.soltar() }
            runCatching { copia?.soltar() }
            runCatching { egl?.soltar() }
            runCatching { entrada?.release() }
        }
    }

    /** H.264 sin B; la frecuencia de claves la pone [hacer] pidiendolas. */
    private fun codificador(w: Int, h: Int, fps: Float): MediaCodec {
        val bitrate = PlanLigera.bitrate(w, h, fps)
        var ultimo: Exception? = null
        for (info in Codificadores.candidatos(Codificadores.AVC, w, h)) {
            for (perfil in listOf(true, false)) {
                val codec = runCatching { MediaCodec.createByCodecName(info.name) }.getOrNull() ?: continue
                try {
                    val f = Codificadores.formatoVideo(info, Codificadores.AVC, w, h, bitrate, perfil, conB = false)
                    f.setInteger(MediaFormat.KEY_FRAME_RATE, fps.roundToInt().coerceIn(1, 60))
                    f.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                    codec.configure(f, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                    return codec
                } catch (e: Exception) {
                    ultimo = e
                    codec.release()
                }
            }
        }
        throw IllegalStateException("sin codificador para ${w}x$h", ultimo)
    }

    private fun vaciar(codec: MediaCodec, c: Contenedor, info: MediaCodec.BufferInfo, pista: IntArray, hastaElFinal: Boolean) {
        val limite = System.nanoTime() + 10_000_000_000L
        while (true) {
            val r = codec.dequeueOutputBuffer(info, if (hastaElFinal) 10_000L else 0L)
            when {
                r == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!hastaElFinal || System.nanoTime() > limite) return
                r == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> pista[0] = c.pista(codec.outputFormat)
                r >= 0 -> {
                    val b = codec.getOutputBuffer(r)
                    val config = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    if (b != null && !config && info.size > 0 && pista[0] >= 0) c.escribir(pista[0], b, info)
                    codec.releaseOutputBuffer(r, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    /**
     * Vuelve a abrir la copia y comprueba que esta entera y con claves cada
     * poco. Devuelve el corrimiento, o null si no vale.
     */
    private fun revisar(archivo: File, cuadros: Int, primeroOriginal: Long): Long? {
        val ex = MediaExtractor()
        try {
            ex.setDataSource(archivo.path)
            if (ex.trackCount != 1) return null
            ex.selectTrack(0)
            var n = 0
            var primero = -1L
            var ultimaClave = -1L
            var claveMax = 0L
            var ultimo = -1L
            while (true) {
                val t = ex.sampleTime
                if (t < 0) break
                if (primero < 0) primero = t
                if (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                    if (ultimaClave >= 0) claveMax = maxOf(claveMax, t - ultimaClave)
                    ultimaClave = t
                }
                ultimo = t
                n++
                if (!ex.advance()) break
            }
            if (ultimaClave < 0 || primero < 0) return null
            claveMax = maxOf(claveMax, ultimo - ultimaClave)
            if (!PlanLigera.copiaValida(cuadros, n, claveMax)) {
                runCatching { Log.i(ETIQUETA, "copia ligera descartada: $n de $cuadros cuadros, claves cada ${claveMax / 1000} ms") }
                return null
            }
            return primeroOriginal - primero
        } finally {
            runCatching { ex.release() }
        }
    }

    /** Lo que ocupan como mucho todas juntas; se borran las de uso mas viejo. */
    private const val LIMITE_BYTES = 1_500L * 1024 * 1024
}

/** Las cuentas de [Ligeras], sin Android para poder probarlas. */
internal object PlanLigera {
    /** Lado corto de la copia: de sobra para la vista previa (unos 620 de ancho). */
    const val LADO_CORTO = 720
    /** Cada cuanto un fotograma clave en la copia. */
    const val CLAVE_US = 500_000L

    data class Guardada(val nombre: String, val bytes: Long, val usadaMs: Long)

    /** Solo si el original es grande o tiene las claves separadas; si no, ya va ligero. */
    fun haceFalta(ancho: Int, alto: Int, claveMaxUs: Long): Boolean =
        minOf(ancho, alto) > LADO_CORTO || claveMaxUs > 1_000_000L

    /** Medidas de la copia: lado corto [LADO_CORTO] como mucho, multiplos de 16 (lo piden muchos codificadores). */
    fun medidas(ancho: Int, alto: Int): Pair<Int, Int> {
        val escala = minOf(1.0, LADO_CORTO.toDouble() / minOf(ancho, alto))
        fun redondear(v: Int) = maxOf(16, ((v * escala) / 16).roundToInt() * 16)
        return redondear(ancho) to redondear(alto)
    }

    /** Unos 0,1 bits por pixel y fotograma: se ve limpio en la vista previa y pesa poco. */
    fun bitrate(ancho: Int, alto: Int, fps: Float): Int {
        val f = if (fps > 0f) fps.coerceIn(10f, 120f) else 30f
        return (ancho.toLong() * alto * f * 0.1).toLong().coerceIn(1_500_000L, 10_000_000L).toInt()
    }

    /** Entera (casi todos los fotogramas) y con claves cada poco (el codificador pudo no hacer caso). */
    fun copiaValida(cuadrosEscritos: Int, cuadrosLeidos: Int, claveMaxUs: Long): Boolean =
        cuadrosLeidos >= cuadrosEscritos - maxOf(2, cuadrosEscritos / 100) && claveMaxUs <= 1_200_000L

    /** Las que sobran para no pasar de [limite], empezando por la usada hace mas tiempo. */
    fun sobrantes(guardadas: List<Guardada>, limite: Long): List<String> {
        var total = guardadas.sumOf { it.bytes }
        val fuera = ArrayList<String>()
        for (g in guardadas.sortedBy { it.usadaMs }) {
            if (total <= limite) break
            fuera += g.nombre
            total -= g.bytes
        }
        return fuera
    }
}
