package com.royermethod.app.editor.vista

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
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
    private var ultimoDibujado = -1L

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
    private fun avisarTiempo(t: Long, desde: Int) {
        principal.post { if (busquedas == desde) alTiempo(t) }
    }

    fun irA(t: Long) {
        busquedas++
        val d = Linea.duracion(proyecto)
        tiempo = t.coerceIn(0, maxOf(0, d - 1))
        h.removeCallbacks(dibujoSuelto)
        h.post {
            if (reproduciendo) {
                reiniciarSonido(tiempo)
            }
        }
        h.post(dibujoSuelto)
    }

    fun reproducir() {
        if (reproduciendo) return
        val d = Linea.duracion(proyecto)
        if (d <= 0) return
        // Desde el final, vuelve a empezar
        if (tiempo >= d - Linea.tiempoDe(1)) tiempo = 0
        reproduciendo = true
        principal.post { alReproducir(true) }
        h.post {
            reiniciarSonido(tiempo)
            ultimoDibujado = -1
            h.post(bucle)
        }
    }

    fun pausar() {
        if (!reproduciendo) return
        reproduciendo = false
        principal.post { alReproducir(false) }
        val b = busquedas
        h.post {
            // Se queda donde iba, salvo que justo se haya llevado el cabezal a otro sitio
            if (busquedas == b) {
                tiempo = reloj()
                avisarTiempo(tiempo, b)
            }
            h.removeCallbacks(bucle)
            sonido?.parar()
            sonido = null
            segmentosSonando = null
            // El fotograma de pausa, con todas sus muestras
            ultimoDibujado = -1
            dibujarAhora()
        }
    }

    fun cerrar() {
        reproduciendo = false
        h.post {
            h.removeCallbacksAndMessages(null)
            sonido?.parar()
            sonido = null
            soltarGl()
            hilo.quitSafely()
        }
    }

    // ------------------------------------------------------------------
    // En el hilo de dibujo
    // ------------------------------------------------------------------

    private fun reiniciarSonido(desdeUs: Long) {
        sonido?.parar()
        sonido = null
        val p = proyecto
        val segs = PlanAudio.de(p)
        segmentosSonando = segs
        inicioUs = desdeUs
        inicioNanos = System.nanoTime()
        if (segs.isNotEmpty()) {
            sonido = runCatching { SalidaAudio(app, segs, PlanAudio.total(p), Pcm.frames(desdeUs)) }.getOrNull()
        }
    }

    /** Donde va la reproduccion: lo que ha sonado, o el reloj si no hay sonido. */
    private fun reloj(): Long {
        val s = sonido
        return if (s != null) inicioUs + Pcm.us(s.sonados())
        else inicioUs + (System.nanoTime() - inicioNanos) / 1000
    }

    private val bucle = object : Runnable {
        override fun run() {
            if (!reproduciendo) return
            val d = Linea.duracion(proyecto)
            val t = reloj()
            if (t >= d) {
                // Al final: se queda en el ultimo fotograma
                tiempo = maxOf(0, d - 1)
                reproduciendo = false
                sonido?.parar(); sonido = null
                val fin = tiempo
                principal.post { alReproducir(false) }
                avisarTiempo(fin, busquedas)
                ultimoDibujado = -1
                dibujarAhora()
                return
            }
            tiempo = t
            val n = Linea.fotogramaEn(t)
            if (n != ultimoDibujado) {
                dibujar(Linea.tiempoDe(n), n, rapido = true)
                avisarTiempo(t, busquedas)
            }
            val falta = (Linea.tiempoDe(n + 1) - reloj()) / 1000
            h.postDelayed(this, falta.coerceIn(1, 34))
        }
    }

    private val dibujoSuelto = Runnable {
        ultimoDibujado = -1
        dibujarAhora()
    }

    /**
     * El instante actual tal cual, aunque no caiga en un fotograma exacto (al
     * arrastrar). No avisa a la interfaz: en pausa es ella la que manda.
     */
    private fun dibujarAhora() {
        val t = tiempo
        dibujar(t, Linea.fotogramaEn(t), rapido = false)
    }

    private fun dibujar(t: Long, n: Long, rapido: Boolean) {
        val e = egl ?: return
        val c = compositor ?: return
        val p = proyecto
        // Reproduciendo, la mitad de muestras: sigue viendose fluido y va
        // sobrado en cualquier telefono. En pausa, como saldra exportado.
        val muestras = if (rapido) minOf(4, p.salida.muestras) else p.salida.muestras
        if (escena == null || escenaDe !== p || escenaMuestras != muestras) {
            escena = Escena(p.copy(salida = p.salida.copy(muestras = muestras)), ancho, alto, c.medidor)
            escenaDe = p
            escenaMuestras = muestras
        }
        runCatching {
            e.activar()
            c.dibujar(escena!!.cuadroEn(t, n))
            e.publicar()
        }
        ultimoDibujado = n
    }
}
