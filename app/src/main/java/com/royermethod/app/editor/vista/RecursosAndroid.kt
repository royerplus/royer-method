package com.royermethod.app.editor.vista

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
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
    private val principal = Handler(Looper.getMainLooper())
    // Colocar un brillo sobre un ojo requiere un cuadro real, no el keyframe anterior.
    // Una sola petición pendiente: arrastrar nunca acumula búsquedas antiguas.
    private val hiloFotogramas = Executors.newSingleThreadExecutor { Thread(it,"fotograma-editor").apply { priority=Thread.MIN_PRIORITY } }
    private val fotogramas = mutableStateMapOf<String,ImageBitmap>()
    private val usoFotogramas = LinkedHashMap<String,Unit>(8,.75f,true)
    private val fallosFotogramas = LinkedHashSet<String>()
    private data class PedidoFotograma(val origen:Origen,val tiempoUs:Long) { val clave get()="${origen.uri}@$tiempoUs" }
    private val bloqueoFotogramas=Any()
    private var pendienteFotograma:PedidoFotograma?=null
    private var procesandoFotograma:String?=null
    private var lectorFotograma:MediaMetadataRetriever?=null // Solo hiloFotogramas.
    private var uriFotograma:String?=null
    private var trabajadorFotogramas=false
    @Volatile private var cerrado=false

    private val lectores = object : LinkedHashMap<String, MediaMetadataRetriever>(4, 0.75f, true) {
        override fun removeEldestEntry(e: MutableMap.MutableEntry<String, MediaMetadataRetriever>): Boolean {
            if (size > 3) { runCatching { e.value.release() }; return true }
            return false
        }
    }

    /** Orden de uso de las miniaturas, para tirar las mas viejas. Solo en el hilo principal. */
    private val usoMiniaturas = LinkedHashMap<String, Unit>(64, 0.75f, true)

    override fun miniatura(o: Origen, tUs: Long): ImageBitmap? {
        val clave = "${o.uri}@$tUs"
        usoMiniaturas[clave] = Unit
        if (usoMiniaturas.size > MAX_MINIATURAS) {
            val vieja = usoMiniaturas.keys.first()
            usoMiniaturas.remove(vieja)
            pedidas.remove(vieja)
            // Se pide desde el dibujo: el estado se toca despues, no en mitad
            principal.post { miniaturas.remove(vieja) }
        }
        miniaturas[clave]?.let { return it }
        if (pedidas.add(clave)) hilo.execute {
            val b = runCatching { if (o.esImagen) foto(o) else miniaturaVideo(o, tUs) }.getOrNull()
            // Se publica en el hilo principal: el mapa es estado de Compose
            if (b != null) principal.post { if (clave in pedidas) miniaturas[clave] = b.asImageBitmap() }
        }
        return null
    }

    private fun miniaturaVideo(o: Origen, tUs: Long): Bitmap? {
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

    /** Devuelve únicamente ese instante; durante la carga no sustituye por un cuadro viejo. */
    override fun fotograma(o:Origen,tUs:Long):ImageBitmap? {
        if(cerrado)return null
        val pedido=PedidoFotograma(o,tUs)
        fotogramas[pedido.clave]?.let {
            usoFotogramas[pedido.clave]=Unit
            synchronized(bloqueoFotogramas) { pendienteFotograma=null }
            return it
        }
        if(pedido.clave in fallosFotogramas)return null
        val iniciar=synchronized(bloqueoFotogramas) {
            if(cerrado)return null
            pendienteFotograma=if(procesandoFotograma==pedido.clave)null else pedido
            if(trabajadorFotogramas)false else { trabajadorFotogramas=true;true }
        }
        if(iniciar)hiloFotogramas.execute { cargarFotogramas() }
        return null
    }

    private fun cargarFotogramas() {
        while(true) {
            val pedido=synchronized(bloqueoFotogramas) {
                if(cerrado) { trabajadorFotogramas=false;procesandoFotograma=null;return }
                val siguiente=pendienteFotograma
                if(siguiente==null) { trabajadorFotogramas=false;procesandoFotograma=null;return }
                pendienteFotograma=null
                procesandoFotograma=siguiente.clave
                siguiente
            }
            val bitmap=runCatching { fotogramaPreciso(pedido.origen,pedido.tiempoUs) }.getOrNull()
            principal.post {
                if(cerrado)bitmap?.recycle()
                else if(bitmap!=null) {
                    usoFotogramas[pedido.clave]=Unit
                    fotogramas[pedido.clave]=bitmap.asImageBitmap()
                    while(usoFotogramas.size>MAX_FOTOGRAMAS) {
                        val vieja=usoFotogramas.keys.first()
                        usoFotogramas.remove(vieja);fotogramas.remove(vieja)
                    }
                } else {
                    fallosFotogramas.add(pedido.clave)
                    while(fallosFotogramas.size>MAX_FOTOGRAMAS)fallosFotogramas.remove(fallosFotogramas.first())
                }
            }
            synchronized(bloqueoFotogramas) { procesandoFotograma=null }
        }
    }

    private fun fotogramaPreciso(o:Origen,tUs:Long):Bitmap? {
        if(o.esImagen) {
            val opciones=BitmapFactory.Options().apply { inSampleSize=max(1,maxOf(o.ancho,o.alto)/LADO_FOTOGRAMA) }
            val b=app.contentResolver.openInputStream(Uri.parse(o.uri))?.use { BitmapFactory.decodeStream(it,null,opciones) } ?: return null
            val orientada=if(o.giro==0)b else Bitmap.createBitmap(b,0,0,b.width,b.height,Matrix().apply { postRotate(o.giro.toFloat()) },true).also { if(it!==b)b.recycle() }
            return reducirFotograma(orientada)
        }
        if(uriFotograma!=o.uri) {
            runCatching { lectorFotograma?.release() }
            lectorFotograma=null;uriFotograma=null
            val nuevo=MediaMetadataRetriever()
            try { nuevo.setDataSource(app,Uri.parse(o.uri)) }
            catch(e:Exception) { runCatching { nuevo.release() };throw e }
            lectorFotograma=nuevo;uriFotograma=o.uri
        }
        val r=lectorFotograma ?: return null
        return if(Build.VERSION.SDK_INT>=27)r.getScaledFrameAtTime(tUs,MediaMetadataRetriever.OPTION_CLOSEST,LADO_FOTOGRAMA,LADO_FOTOGRAMA)
        else r.getFrameAtTime(tUs,MediaMetadataRetriever.OPTION_CLOSEST)?.let { reducirFotograma(it) }
    }

    private fun reducirFotograma(b:Bitmap):Bitmap {
        val f=LADO_FOTOGRAMA.toFloat()/maxOf(b.width,b.height)
        if(f>=1f)return b
        return Bitmap.createScaledBitmap(b,(b.width*f).toInt().coerceAtLeast(1),(b.height*f).toInt().coerceAtLeast(1),true).also { if(it!==b)b.recycle() }
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
        synchronized(bloqueoFotogramas) {
            if(cerrado)return
            cerrado=true;pendienteFotograma=null
        }
        hiloFotogramas.execute {
            runCatching { lectorFotograma?.release() };lectorFotograma=null;uriFotograma=null
        }
        hiloFotogramas.shutdown()
        hilo.execute {
            lectores.values.forEach { runCatching { it.release() } }
            lectores.clear()
        }
        hilo.shutdown()
        hiloOndas.shutdownNow()
    }

    companion object {
        private const val LADO = 160
        private const val LADO_FOTOGRAMA = 640
        private const val MAX_FOTOGRAMAS = 6
        /** Unos 25 MB de miniaturas como mucho. */
        private const val MAX_MINIATURAS = 240
        private const val MAX_ONDA_US = 15 * 60 * 1_000_000L
    }
}
