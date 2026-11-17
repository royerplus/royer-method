package com.royermethod.pc.vista

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.royermethod.app.editor.audio.Pcm
import com.royermethod.app.editor.audio.PlanAudio
import com.royermethod.app.editor.audio.Segmento
import com.royermethod.app.editor.modelo.Escena
import com.royermethod.app.editor.modelo.Linea
import com.royermethod.app.editor.modelo.Proyecto
import com.royermethod.app.editor.modelo.ZoomVista
import com.royermethod.app.editor.motor.Compositor
import com.royermethod.app.editor.motor.Egl
import com.royermethod.app.editor.motor.Lamina
import com.royermethod.app.editor.motor.LecturaAbandonada
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit

/**
 * La vista previa del PC.
 *
 * Usa el mismo [Compositor] y la misma [Escena] que la exportacion, en su
 * propio hilo con su contexto de OpenGL. Cada fotograma se dibuja en una
 * lamina, se lee y se entrega a la interfaz como imagen ([alImagen]).
 *
 * Sonando, el reloj es el sonido ([SalidaAudio]); sin sonido, el del sistema.
 * Se dibuja un fotograma solo cuando cambia el que toca (30 por segundo).
 */
class Previsualizador(context: Context) {
    private val app = context.applicationContext

    /** La imagen nueva, en el hilo de dibujo. */
    @Volatile
    var alImagen: (ImageBitmap) -> Unit = {}
    /** El cabezal mientras suena o tras una busqueda. */
    @Volatile
    var alTiempo: (Long) -> Unit = {}
    @Volatile
    var alReproducir: (Boolean) -> Unit = {}

    @Volatile
    var reproduciendo = false
        private set
    /** Ocupada sonando (otros trabajos de fondo esperan). */
    val ocupada: Boolean get() = reproduciendo

    @Volatile
    private var proyecto: Proyecto = Proyecto()
    @Volatile
    private var tiempo = 0L
    @Volatile
    private var cerrado = false
    @Volatile
    private var busquedas = 0

    private val tareas = LinkedBlockingDeque<() -> Unit>()
    private val hilo = Thread({ bucle() }, "vista-previa").apply { isDaemon = true; start() }

    // Solo en el hilo de dibujo
    private var egl: Egl? = null
    private var compositor: Compositor? = null
    private var lamina: Lamina? = null
    private var pixeles: ByteBuffer? = null
    private var ancho = 0
    private var alto = 0
    private var escena: Escena? = null
    private var escenaDe: Proyecto? = null
    private var escenaMuestras = 0
    private var zoomVista = ZoomVista.NINGUNO
    private var ultimoDibujado = -1L
    private var sonido: SalidaAudio? = null
    private var segmentosSonando: List<Segmento>? = null
    private var t0 = 0L
    private var t0Ns = 0L
    private var frame0 = 0L

    private fun enHilo(f: () -> Unit) {
        if (!cerrado) tareas.add(f)
    }

    private fun bucle() {
        while (!cerrado) {
            val t = tareas.poll(if (reproduciendo) 3 else 200, TimeUnit.MILLISECONDS)
            try {
                if (t != null) t()
                if (reproduciendo && tareas.isEmpty()) paso()
            } catch (e: Throwable) {
                System.err.println("vista previa: $e")
            }
        }
        soltarSonido()
        soltarGl()
    }

    /** La vista cambio de tamano (pixeles). */
    fun cambiarTamano(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        enHilo {
            if (w == ancho && h == alto && compositor != null) return@enHilo
            val e = egl ?: Egl().also { egl = it }
            e.activar()
            runCatching { compositor?.soltar() }
            lamina?.soltar()
            ancho = w; alto = h
            compositor = Compositor(app, w, h, vista = true).also { it.zoom = zoomVista }
            lamina = Lamina(w, h)
            pixeles = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder())
            escena = null
            ultimoDibujado = -1
            dibujar(tiempo)
        }
    }

    fun zoom(z: ZoomVista) {
        enHilo {
            zoomVista = z
            compositor?.zoom = z
            if (!reproduciendo) { ultimoDibujado = -1; dibujar(tiempo) }
        }
    }

    fun proyecto(p: Proyecto) {
        proyecto = p
        enHilo {
            if (p !== proyecto) return@enHilo
            if (reproduciendo) {
                val segs = PlanAudio.de(p)
                if (segs != segmentosSonando) reiniciarSonido(relojAhora())
            } else {
                ultimoDibujado = -1
                dibujar(tiempo)
            }
        }
    }

    fun irA(t: Long) {
        if (cerrado) return
        val d = Linea.duracion(proyecto)
        val destino = t.coerceIn(0, maxOf(0, d - 1))
        tiempo = destino
        val b = ++busquedas
        enHilo {
            if (b != busquedas) return@enHilo
            if (reproduciendo) reiniciarSonido(destino)
            ultimoDibujado = -1
            dibujar(destino) { b == busquedas }
        }
    }

    fun reproducir() {
        if (cerrado || reproduciendo) return
        val d = Linea.duracion(proyecto)
        if (d <= 0) return
        if (tiempo >= d - Linea.tiempoDe(1)) tiempo = 0
        reproduciendo = true
        alReproducir(true)
        enHilo { reiniciarSonido(tiempo) }
    }

    fun pausar() {
        if (!reproduciendo) return
        reproduciendo = false
        sonido?.pausar()
        enHilo {
            val t = Linea.tiempoDe(Linea.fotogramaEn(tiempo))
            soltarSonido()
            tiempo = t
            alTiempo(t)
            ultimoDibujado = -1
            dibujar(t)
        }
        alReproducir(false)
    }

    /**
     * Donde va la reproduccion en [frameNs] (System.nanoTime), para que la
     * linea de tiempo avance suave; null si no suena.
     */
    fun instanteEn(frameNs: Long): Long? {
        if (!reproduciendo) return null
        return tiempo + (frameNs - ultimoNs).coerceIn(0L, 100_000_000L) / 1000
    }

    @Volatile
    private var ultimoNs = System.nanoTime()

    fun cerrar() {
        reproduciendo = false
        cerrado = true
        tareas.clear()
        hilo.join(3000)
    }

    // ------------------------------------------------------------------

    private fun reiniciarSonido(desde: Long) {
        soltarSonido()
        val p = proyecto
        val segs = PlanAudio.de(p)
        segmentosSonando = segs
        t0 = desde
        frame0 = Pcm.frames(desde)
        t0Ns = System.nanoTime()
        // El primer fotograma antes de que suene: que no entre el sonido con la imagen quieta
        ultimoDibujado = -1
        dibujar(desde)
        t0Ns = System.nanoTime()
        if (segs.isNotEmpty()) {
            sonido = runCatching { SalidaAudio(app, segs, PlanAudio.total(p), frame0) }.getOrNull()
            sonido?.arrancar()
        }
    }

    private fun soltarSonido() {
        sonido?.parar()
        sonido = null
        segmentosSonando = null
    }

    private fun relojAhora(): Long {
        val s = sonido
        return if (s != null && !s.acabada) Pcm.us(s.frame)
        else t0 + (System.nanoTime() - t0Ns) / 1000
    }

    /** Sonando: dibuja el fotograma que toca si cambio, y avisa del instante. */
    private fun paso() {
        val d = Linea.duracion(proyecto)
        val t = relojAhora()
        if (t >= d) {
            reproduciendo = false
            soltarSonido()
            val fin = maxOf(0L, Linea.tiempoDe(Linea.fotogramaEn(d - 1)))
            tiempo = fin
            alTiempo(fin)
            alReproducir(false)
            return
        }
        tiempo = t
        ultimoNs = System.nanoTime()
        val n = Linea.fotogramaEn(t)
        if (n != ultimoDibujado) {
            dibujar(t, rapido = true)
            alTiempo(t)
        }
    }

    private fun dibujar(t: Long, rapido: Boolean = false, seguir: (() -> Boolean)? = null) {
        val c = compositor ?: return
        val e = egl ?: return
        val l = lamina ?: return
        val buf = pixeles ?: return
        val p = proyecto
        val muestras = if (rapido) minOf(4, p.salida.muestras) else p.salida.muestras
        if (escena == null || escenaDe !== p || escenaMuestras != muestras) {
            escena = Escena(p.copy(salida = p.salida.copy(muestras = muestras)), ancho, alto, c.medidor)
            escenaDe = p
            escenaMuestras = muestras
        }
        val n = Linea.fotogramaEn(t)
        try {
            e.activar()
            val cuadro = escena!!.cuadroEn(t, n)
            if (!c.dibujar(cuadro, l, seguir)) return
            l.leer(buf)
            c.anticipar(Linea.origenesEntre(p, t, t + 1_500_000L))
        } catch (_: LecturaAbandonada) {
            return
        }
        ultimoDibujado = n
        alImagen(imagen(buf, ancho, alto))
    }

    private fun imagen(buf: ByteBuffer, w: Int, h: Int): ImageBitmap {
        val bytes = ByteArray(w * h * 4)
        buf.position(0)
        buf.get(bytes)
        buf.position(0)
        val info = ImageInfo(w, h, ColorType.RGBA_8888, ColorAlphaType.PREMUL)
        return Image.makeRaster(info, bytes, w * 4).toComposeImageBitmap()
    }

    private fun soltarGl() {
        runCatching { egl?.activar() }
        runCatching { compositor?.soltar() }
        runCatching { lamina?.soltar() }
        compositor = null
        lamina = null
        runCatching { egl?.soltar() }
        egl = null
    }
}
