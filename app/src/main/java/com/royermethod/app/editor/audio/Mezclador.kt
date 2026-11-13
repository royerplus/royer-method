package com.royermethod.app.editor.audio

import java.util.IdentityHashMap
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * Suma todos los [Segmento]s del proyecto en una sola pista estereo.
 *
 * Abre cada segmento justo cuando empieza a sonar y lo cierra al acabar, asi
 * que nunca hay mas de dos o tres archivos abiertos aunque el proyecto tenga
 * cincuenta clips. Entrega exactamente [total] frames: ni uno mas ni uno
 * menos que lo que dura el video.
 *
 * @param desde frame por el que empezar (la vista previa empieza donde este
 *   el cursor)
 * @param antelacion frames de adelanto con que se abre cada segmento en otro
 *   hilo, o 0 para abrirlo en este justo cuando empieza a sonar. Abrir un
 *   archivo y dejar listo su decodificador tarda una o dos decenas de
 *   milisegundos en un telefono; en directo, hacerlo en el mismo hilo que
 *   alimenta al altavoz lo deja sin sonido justo en cada junta.
 * @param abrir da el audio de un segmento desde un frame suyo ya a [Pcm.HZ]
 *   y a la velocidad del segmento
 */
class Mezclador(
    segmentos: List<Segmento>,
    private val total: Long,
    desde: Long = 0,
    private val antelacion: Long = 0,
    private val abrir: (Segmento, Long) -> FuentePcm
) : FuentePcm {

    private val pendientes = segmentos.filter { it.fin > desde }.sortedBy { it.inicio }.toMutableList()
    private val abiertos = ArrayList<Pair<Segmento, FuentePcm>>()

    /** Los que se estan abriendo por adelantado. Por identidad: dos segmentos pueden ser iguales. */
    private val preparados = IdentityHashMap<Segmento, Future<FuentePcm>>()
    private val apertura: ExecutorService? = if (antelacion > 0) {
        Executors.newSingleThreadExecutor { Thread(it, "abrir-sonido").apply { isDaemon = true } }
    } else null

    /** Siguiente frame a mezclar (va [Limitador.LATENCIA] por delante de lo entregado). */
    private var mezcla = desde
    /** Siguiente frame a entregar. */
    private var entregado = desde
    private val limitador = Limitador()
    private var cebado = false

    private var trozo = FloatArray(0)
    private val tmp = FloatArray(1024 * Pcm.CANALES)

    override fun leer(destino: FloatArray, frames: Int): Int {
        val n = minOf(frames.toLong(), total - entregado).toInt()
        if (n <= 0) return 0
        if (!cebado) {
            // El limitador va con retraso: se le adelanta ese trozo para que lo
            // que salga cuadre con el frame que se pide.
            cebado = true
            val previo = FloatArray(Limitador.LATENCIA * Pcm.CANALES)
            mezclar(previo, Limitador.LATENCIA)
            limitador.procesar(previo, Limitador.LATENCIA)
        }
        mezclar(destino, n)
        limitador.procesar(destino, n)
        entregado += n
        return n
    }

    /** Mezcla los [n] frames siguientes en [destino]. */
    private fun mezclar(destino: FloatArray, n: Int) {
        java.util.Arrays.fill(destino, 0, n * Pcm.CANALES, 0f)
        val desde = mezcla
        val hasta = desde + n

        // Los que van a empezar pronto se abren ya, aparte. Solo los que aun no
        // han empezado: esos se abren siempre desde su primer frame.
        apertura?.let { ex ->
            for (s in pendientes) {
                if (s.inicio >= hasta + antelacion) break
                if (s.inicio >= desde && s !in preparados) {
                    preparados[s] = ex.submit(Callable { runCatching { abrir(s, 0) }.getOrDefault(Silencio) })
                }
            }
        }

        // Los que empiezan en este trozo
        while (pendientes.isNotEmpty() && pendientes[0].inicio < hasta) {
            val s = pendientes.removeAt(0)
            val dentro = (desde - s.inicio).coerceAtLeast(0)
            val listo = preparados.remove(s)
            val fuente = if (listo != null) {
                runCatching { listo.get() }.getOrDefault(Silencio)
            } else {
                runCatching { abrir(s, dentro) }.getOrDefault(Silencio)
            }
            abiertos.add(s to fuente)
        }

        val it = abiertos.iterator()
        while (it.hasNext()) {
            val (s, fuente) = it.next()
            val a = maxOf(desde, s.inicio)
            val b = minOf(hasta, s.fin)
            if (b > a) {
                val m = (b - a).toInt()
                if (trozo.size < m * Pcm.CANALES) trozo = FloatArray(m * Pcm.CANALES)
                val leidos = fuente.leerTodo(trozo, m, tmp)
                // Si el archivo se acaba antes de tiempo, lo que falta es silencio
                java.util.Arrays.fill(trozo, leidos * Pcm.CANALES, m * Pcm.CANALES, 0f)
                val off = (a - desde).toInt()
                for (f in 0 until m) {
                    val g = s.ganancia(a - s.inicio + f)
                    val j = (off + f) * 2
                    destino[j] += trozo[f * 2] * g
                    destino[j + 1] += trozo[f * 2 + 1] * g
                }
            }
            if (s.fin <= hasta) {
                fuente.cerrar()
                it.remove()
            }
        }
        mezcla = hasta
    }

    override fun cerrar() {
        abiertos.forEach { it.second.cerrar() }
        abiertos.clear()
        // Lo abierto por adelantado que no llego a sonar tambien se cierra
        for (f in preparados.values) {
            if (!f.cancel(false)) runCatching { f.get(2, TimeUnit.SECONDS).cerrar() }
        }
        preparados.clear()
        apertura?.shutdown()
    }

    companion object {
        /**
         * El audio de un segmento a partir de su frame [dentro], listo para
         * mezclar: a [Pcm.HZ] y a su velocidad.
         *
         * @param decodificar abre el archivo en un instante y dice a que
         *   frecuencia viene; null si no se puede leer
         */
        fun lector(
            s: Segmento,
            dentro: Long,
            decodificar: (uri: String, desdeUs: Long) -> Pair<FuentePcm, Int>?
        ): FuentePcm {
            val enArchivo = s.desdeUs + (Pcm.us(dentro) * s.velocidad.toDouble()).toLong()
            val (pcm, hz) = decodificar(s.uri, enArchivo) ?: return Silencio
            return when {
                kotlin.math.abs(s.velocidad - 1f) < 1e-4f -> Remuestreador(pcm, hz.toDouble())
                s.mantenerTono -> Estirador(Remuestreador(pcm, hz.toDouble()), s.velocidad)
                // Sin conservar el tono: leer el archivo como si fuera mas rapido
                else -> Remuestreador(pcm, hz * s.velocidad.toDouble())
            }
        }
    }
}
