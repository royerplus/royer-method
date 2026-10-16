package com.royermethod.app.editor.vista

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.Choreographer
import android.view.Surface
import com.royermethod.app.editor.audio.Pcm
import com.royermethod.app.editor.audio.PlanAudio
import com.royermethod.app.editor.audio.Segmento
import com.royermethod.app.editor.modelo.Escena
import com.royermethod.app.editor.modelo.Linea
import com.royermethod.app.editor.modelo.Proyecto
import com.royermethod.app.editor.motor.Compositor
import com.royermethod.app.editor.motor.Egl
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Reproduce el proyecto en una superficie de la pantalla.
 *
 * Usa el mismo [Compositor] y la misma [Escena] que la exportacion: lo que se
 * ve aqui es lo que va a salir, fotograma por fotograma, con sus mismas
 * transiciones y su mismo desenfoque de movimiento. Lo unico que cambia es
 * que no genera fotogramas con el modelo (no da tiempo en directo).
 *
 * Todo el dibujo ocurre en un hilo propio, que es el duenio del contexto de
 * OpenGL. La interfaz solo manda ordenes y recibe el instante actual.
 */
class Previsualizador(context: Context) {

    private val app = context.applicationContext
    private val hilo = HandlerThread("vista").apply { start() }
    private val h = Handler(hilo.looper)
    private val principal = Handler(Looper.getMainLooper())

    /** Lo llama en el hilo principal con el instante que se esta viendo. */
    var alTiempo: (Long) -> Unit = {}
    /** Lo llama en el hilo principal al empezar o parar de reproducir. */
    var alReproducir: (Boolean) -> Unit = {}
    /** Opcional, en el hilo GL y solo tras un swap exitoso. Sin observador no mide CPU. */
    @Volatile
    var alCuadroPublicado: ((CuadroPublicado) -> Unit)? = null

    data class CuadroPublicado(
        val tiempoUs: Long,
        val fotograma: Long,
        /** Marca del buffer EGL, en la base de System.nanoTime. */
        val nanoTime: Long,
        val cpuMs: Double,
        val swapMs: Double,
        val enReproduccion: Boolean
    )

    // ---- Solo en el hilo de dibujo ----
    private var egl: Egl? = null
    private var compositor: Compositor? = null
    private var ancho = 0
    private var alto = 0
    private var escena: Escena? = null
    private var escenaDe: Proyecto? = null
    private var escenaMuestras = 0
    private var sonido: SalidaAudio? = null
    private var segmentosSonando: List<Segmento>? = null
    private var inicioUs = 0L
    private var inicioNanos = 0L
    /** Orden y busqueda que realmente llegaron a preparar este reloj en GL. */
    private var ordenDelReloj = -1
    private var busquedaDelReloj = -1
    private var ultimoDibujado = -1L
    private var coreografo: Choreographer? = null
    private var buclePendiente = false
    private var busquedaDelBucle = 0
    private val ordenes = AtomicInteger(0)
    private val controlTiempo = Any()
    @Volatile
    private var cerrado = false

    @Volatile
    private var proyecto = Proyecto()
    @Volatile
    var reproduciendo = false
        private set
    @Volatile
    var tiempo = 0L
        private set

    // ------------------------------------------------------------------
    // Superficie
    // ------------------------------------------------------------------

    fun adjuntar(superficie: Surface, w: Int, hgt: Int) {
        h.post {
            soltarGl()
            runCatching {
                val e = Egl(superficie)
                e.activar()
                egl = e
                ancho = w; alto = hgt
                compositor = Compositor(app, w, hgt)
                escena = null
                ultimoDibujado = -1
                dibujarAhora()
                programarBucle()
            }
        }
    }

    fun cambiarTamano(w: Int, hgt: Int) {
        h.post {
            if (w == ancho && hgt == alto) return@post
            val e = egl ?: return@post
            compositor?.soltar()
            ancho = w; alto = hgt
            e.activar()
            compositor = Compositor(app, w, hgt)
            escena = null
            dibujarAhora()
        }
    }

    /** Espera a que se deje de usar la superficie: despues se puede destruir. */
    fun soltarSuperficie() {
        val listo = CountDownLatch(1)
        h.post {
            soltarGl()
            listo.countDown()
        }
        listo.await(2, TimeUnit.SECONDS)
    }

    private fun soltarGl() {
        detenerBucle()
        runCatching { compositor?.soltar() }
        compositor = null
        runCatching { egl?.soltar() }
        egl = null
        escena = null
    }

    // ------------------------------------------------------------------
    // Ordenes
    // ------------------------------------------------------------------

    /** El proyecto cambio: se redibuja, y si esta sonando, se rehace el sonido. */
    fun proyecto(p: Proyecto) {
        proyecto = p
        h.post {
            if (reproduciendo) {
                val segs = PlanAudio.de(p)
                if (segs != segmentosSonando) reiniciarSonido(reloj())
            } else {
                ultimoDibujado = -1
                dibujarAhora()
            }
        }
    }

    /**
     * Cuantas veces ha movido el cabezal la interfaz. Un instante que el hilo
     * de dibujo calculo antes de la ultima busqueda ya no vale y no se manda:
     * si no, al arrastrar, un fotograma lento devolveria el cabezal atras.
     */
    @Volatile
    private var busquedas = 0

    /** Avisa del instante a la interfaz, salvo que se haya buscado otro entretanto. */
    private fun avisarTiempo(t: Long, desde: Int, orden: Int = ordenes.get()) {
        principal.post { if (!cerrado && busquedas == desde && ordenes.get() == orden) alTiempo(t) }
    }

    private fun avisarReproduccion(activo: Boolean, orden: Int) {
        principal.post { if (!cerrado && ordenes.get() == orden) alReproducir(activo) }
    }

    fun irA(t: Long) {
        if (cerrado) return
        val d = Linea.duracion(proyecto)
        val destino = t.coerceIn(0, maxOf(0, d - 1))
        val busqueda = synchronized(controlTiempo) { tiempo = destino; ++busquedas }
        h.post {
            if (cerrado || busqueda != busquedas) return@post
            detenerBucle()
            if (reproduciendo) {
                reiniciarSonido(destino, ordenes.get(), busqueda)
            }
            if (busqueda != busquedas) return@post
            ultimoDibujado = -1
            dibujar(destino, Linea.fotogramaEn(destino), rapido = reproduciendo)
            programarBucle()
        }
    }

    fun reproducir() {
        if (cerrado || reproduciendo) return
        val d = Linea.duracion(proyecto)
        if (d <= 0) return
        // Desde el final, vuelve a empezar
        val orden = ordenes.incrementAndGet()
        synchronized(controlTiempo) {
            if (tiempo >= d - Linea.tiempoDe(1)) tiempo = 0
            reproduciendo = true
        }
        avisarReproduccion(true, orden)
        h.post {
            if (cerrado || ordenes.get() != orden) return@post
            detenerBucle()
            reiniciarSonido(tiempo, orden, busquedas)
            ultimoDibujado = -1
            programarBucle()
        }
    }

    fun pausar() {
        if (!reproduciendo) return
        val ordenAnterior = ordenes.get()
        reproduciendo = false
        val orden = ordenes.incrementAndGet()
        avisarReproduccion(false, orden)
        val b = busquedas
        h.post {
            if (cerrado || ordenes.get() != orden) return@post
            detenerBucle()
            // Se queda donde iba, salvo que justo se haya llevado el cabezal a otro sitio
            if (busquedas == b) {
                val t = reloj(ordenAnterior, b)
                synchronized(controlTiempo) {
                    if (busquedas == b && ordenes.get() == orden) {
                        tiempo = t.coerceIn(0L, maxOf(0L, Linea.duracion(proyecto) - 1L))
                        avisarTiempo(tiempo, b, orden)
                    }
                }
            }
            sonido?.parar()
            sonido = null
            invalidarReloj()
            segmentosSonando = null
            if (cerrado || ordenes.get() != orden) return@post
            // El fotograma de pausa, con todas sus muestras
            ultimoDibujado = -1
            dibujarAhora()
        }
    }

    fun cerrar() {
        if (cerrado) return
        cerrado = true
        ordenes.incrementAndGet()
        reproduciendo = false
        h.post {
            detenerBucle()
            h.removeCallbacksAndMessages(null)
            sonido?.parar()
            sonido = null
            invalidarReloj()
            soltarGl()
            hilo.quitSafely()
        }
    }

    // ------------------------------------------------------------------
    // En el hilo de dibujo
    // ------------------------------------------------------------------

    private fun reiniciarSonido(desdeUs: Long, orden: Int = ordenes.get(), busqueda: Int = busquedas) {
        sonido?.parar()
        sonido = null
        val p = proyecto
        val segs = PlanAudio.de(p)
        segmentosSonando = segs
        inicioUs = desdeUs
        if (segs.isNotEmpty()) {
            sonido = runCatching { SalidaAudio(app, segs, PlanAudio.total(p), Pcm.frames(desdeUs)) }.getOrNull()
        }
        inicioNanos = System.nanoTime()
        ordenDelReloj = orden
        busquedaDelReloj = busqueda
    }

    private fun invalidarReloj() {
        ordenDelReloj = -1
        busquedaDelReloj = -1
    }

    private fun relojVigente(orden: Int = ordenes.get(), busqueda: Int = busquedas): Boolean =
        ordenDelReloj == orden && busquedaDelReloj == busqueda

    /** Donde va la reproduccion: lo que ha sonado, o el reloj si no hay sonido. */
    private fun reloj(orden: Int = ordenes.get(), busqueda: Int = busquedas): Long {
        // Una orden puede haberse cancelado antes de preparar su audio. Tampoco
        // se usa el audio anterior para sobrescribir una busqueda pendiente.
        if (!relojVigente(orden, busqueda)) return tiempo
        val s = sonido
        if (s != null) {
            val frames = runCatching { s.sonados() }.getOrNull()
            val audioUs = frames?.let { inicioUs + Pcm.us(it) } ?: tiempo
            if (s.fallo || frames == null) {
                // El escritor murio: cambiar de reloj desde la ultima posicion
                // alcanzada, nunca desde el comienzo ni desde un ancla antigua.
                inicioUs = maxOf(tiempo, audioUs)
                inicioNanos = System.nanoTime()
                sonido = null
                runCatching { s.parar() }
                return inicioUs
            }
            return if (s.agotada) maxOf(audioUs, Linea.duracion(proyecto)) else audioUs
        }
        return inicioUs + (System.nanoTime() - inicioNanos) / 1000
    }

    private fun programarBucle() {
        if (cerrado || !reproduciendo || egl == null || buclePendiente || !relojVigente()) return
        val c = coreografo ?: Choreographer.getInstance().also { coreografo = it }
        busquedaDelBucle = busquedas
        buclePendiente = true
        c.postFrameCallback(bucle)
    }

    private fun detenerBucle() {
        coreografo?.removeFrameCallback(bucle)
        buclePendiente = false
    }

    private val bucle = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            buclePendiente = false
            val b = busquedaDelBucle
            val orden = ordenes.get()
            if (cerrado || !reproduciendo || b != busquedas) return
            val d = Linea.duracion(proyecto)
            val t = reloj(orden, b)
            val vigente = synchronized(controlTiempo) {
                if (b != busquedas || ordenes.get() != orden || !reproduciendo) false
                else {
                    tiempo = t.coerceAtMost(maxOf(0L, d - 1L))
                    if (t >= d) reproduciendo = false
                    true
                }
            }
            if (!vigente) return
            if (t >= d) {
                // Al final: se queda en el ultimo fotograma
                sonido?.parar(); sonido = null
                invalidarReloj()
                val fin = tiempo
                avisarReproduccion(false, orden)
                avisarTiempo(fin, b, orden)
                ultimoDibujado = -1
                dibujarAhora()
                return
            }
            val n = Linea.fotogramaEn(t)
            if (n != ultimoDibujado) {
                if (dibujar(Linea.tiempoDe(n), n, rapido = true)) avisarTiempo(t, b, orden)
            }
            if (b == busquedas) programarBucle()
        }
    }

    /**
     * El instante actual tal cual, aunque no caiga en un fotograma exacto (al
     * arrastrar). No avisa a la interfaz: en pausa es ella la que manda.
     */
    private fun dibujarAhora() {
        val t = tiempo
        dibujar(t, Linea.fotogramaEn(t), rapido = false)
    }

    private fun dibujar(t: Long, n: Long, rapido: Boolean): Boolean {
        val e = egl ?: return false
        val c = compositor ?: return false
        val observador = alCuadroPublicado
        val inicioCpuNs = if (observador != null) System.nanoTime() else 0L
        val p = proyecto
        // Reproduciendo, la mitad de muestras: sigue viendose fluido y va
        // sobrado en cualquier telefono. En pausa, como saldra exportado.
        val muestras = if (rapido) minOf(4, p.salida.muestras) else p.salida.muestras
        if (escena == null || escenaDe !== p || escenaMuestras != muestras) {
            escena = Escena(p.copy(salida = p.salida.copy(muestras = muestras)), ancho, alto, c.medidor)
            escenaDe = p
            escenaMuestras = muestras
        }
        return runCatching {
            e.activar()
            c.dibujar(escena!!.cuadroEn(t, n))
            val marcaNs = System.nanoTime()
            e.marcarTiempo(marcaNs)
            val publicada = e.publicar()
            if (publicada) {
                ultimoDibujado = n
                if (observador != null) {
                    val finNs = System.nanoTime()
                    // El observador es diagnostico: no puede romper el motor.
                    runCatching { observador(CuadroPublicado(t, n, marcaNs,
                        (marcaNs - inicioCpuNs) / 1e6, (finNs - marcaNs) / 1e6, rapido)) }
                }
            }
            publicada
        }.getOrDefault(false)
    }
}
