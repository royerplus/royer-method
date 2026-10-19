package com.royermethod.app.editor.vista

import java.util.Locale

/*
 * Cuentas de la vista previa para medirla en el telefono. Aqui no hay nada de
 * Android: el Previsualizador pasa marcas de System.nanoTime y escribe el
 * resumen en el registro con la etiqueta Previsualizador.ETIQUETA. Solo se
 * crean si se pidio con adb; si no, no hay objeto y no cuesta nada.
 *
 * Separa lo que pide el punto 7: la preparacion al pulsar play (sonido,
 * primer cuadro, primer movimiento), el trabajo de cada cuadro (modelo, CPU,
 * GPU, swap) y el ritmo de publicacion y de presentacion.
 */

/** Duraciones en ns, sin cajas. */
internal class Serie {
    private var v = LongArray(64)
    var n = 0
        private set

    fun add(x: Long) {
        if (n == v.size) v = v.copyOf(n * 2)
        v[n++] = x
    }

    fun media(): Double {
        var s = 0.0
        for (i in 0 until n) s += v[i]
        return if (n == 0) 0.0 else s / n
    }

    /** El de la posicion n*95/100 de los ordenados, como el banco de la JVM. */
    fun p95(): Long {
        if (n == 0) return 0
        val o = v.copyOf(n)
        o.sort()
        return o[minOf(n - 1, n * 95 / 100)]
    }

    fun max(): Long {
        var m = 0L
        for (i in 0 until n) m = maxOf(m, v[i])
        return m
    }

    /** Cuantos quedan fuera de [minimo, maximo]. */
    fun fuera(minimo: Long, maximo: Long): Int {
        var c = 0
        for (i in 0 until n) if (v[i] < minimo || v[i] > maximo) c++
        return c
    }

    /** "media p95 max" en ms, o "-" si no hay nada. */
    fun ms(): String = if (n == 0) "-" else
        String.format(Locale.ROOT, "%.2f %.2f %.2f", media() / 1e6, p95() / 1e6, max() / 1e6)

    /** Intervalos: lo de [ms] y cuantos se salen del ritmo de 30 por segundo. */
    fun ritmo(): String = if (n == 0) "sin datos" else {
        val f = fuera(RITMO_MIN_NS, RITMO_MAX_NS)
        String.format(Locale.ROOT, "intervalo ms %s, fuera de 25-42 ms: %d de %d (%.1f%%)", ms(), f, n, 100.0 * f / n)
    }

    companion object {
        /** A 30 por segundo cada cuadro dura 33,3 ms; fuera de esto se nota el tiron. */
        const val RITMO_MIN_NS = 25_000_000L
        const val RITMO_MAX_NS = 42_000_000L
    }
}

/**
 * Una reproduccion, desde que se pide (play, una busqueda sonando o un cambio
 * del sonido) hasta que para. Las marcas del hilo de dibujo y las de la
 * TextureView (hilo principal) llegan a la vez: por eso va sincronizada.
 */
internal class SesionVista(
    /** "play", "busqueda" o "sonido". */
    private val motivo: String,
    /** Cuando se pidio, en el hilo principal. */
    private val pedidoNs: Long,
    private val ancho: Int,
    private val alto: Int,
    private val muestras: Int,
    /** Si se espera a la GPU en cada cuadro para medirla. */
    val conGpu: Boolean,
    /**
     * Fotogramas en que aparece un bloque, ordenados. Un pico de CPU ahi
     * apunta a abrir su decodificador o su foto (hipotesis H4 del mapa).
     */
    private val comienzos: LongArray = LongArray(0)
) {
    private var sonidoNs = -1L
    private var segmentos = 0
    // Lo que se ve del sonido en cada consulta del reloj (una por vsync)
    private var cabezalNs = -1L
    private var escritosAlArrancar = -1L
    private var marcaNs = -1L
    private var avanceNs = -1L
    private var fotogramaInicial = Long.MIN_VALUE
    private var primeroNs = -1L
    private var movimientoNs = -1L
    private var ultimoNs = -1L
    private var ultimoFotograma = Long.MIN_VALUE
    private var saltos = 0
    private var saltados = 0L
    private var publicados = 0
    private val escena = Serie()
    private val cpu = Serie()
    private val gpu = Serie()
    private val swap = Serie()
    private val anticipar = Serie()
    private val intervalos = Serie()
    private var presentados = 0
    private var primeraPresentacionNs = -1L
    private var ultimaPresentacionNs = -1L
    private val presentaciones = Serie()
    // Los cuadros que mas CPU costaron, de mas a menos
    private val peoresFotograma = LongArray(PEORES)
    private val peoresCpu = LongArray(PEORES) { -1L }

    /** Lo que tardo en prepararse el sonido en el hilo de dibujo (plan, AudioTrack, hilo). */
    @Synchronized
    fun sonido(duracionNs: Long, segmentos: Int) {
        sonidoNs = duracionNs
        this.segmentos = segmentos
    }

    /**
     * Una consulta del reloj al sonido (SalidaAudio.alLeer): cuando empieza a
     * moverse el cabezal de la pista, cuando llega la primera marca de tiempo
     * y cuando avanza lo que suena, que es lo que mueve la imagen.
     */
    @Synchronized
    fun audio(ahoraNs: Long, cabezal: Long, escritos: Long, marcaFrames: Long?, posicion: Long) {
        if (cabezalNs < 0 && cabezal > 0) { cabezalNs = ahoraNs - pedidoNs; escritosAlArrancar = escritos }
        if (marcaNs < 0 && marcaFrames != null && marcaFrames > 0) marcaNs = ahoraNs - pedidoNs
        if (avanceNs < 0 && posicion > 0) avanceNs = ahoraNs - pedidoNs
    }

    /**
     * Un cuadro publicado (swap correcto). [marcaNs] es su marca de buffer;
     * las duraciones, en ns ([gpuNs] < 0 si no se midio).
     */
    @Synchronized
    fun publicado(marcaNs: Long, fotograma: Long, escenaNs: Long, cpuNs: Long, gpuNs: Long, swapNs: Long, anticiparNs: Long) {
        if (publicados == 0) {
            primeroNs = marcaNs - pedidoNs
            fotogramaInicial = fotograma
        } else {
            intervalos.add(marcaNs - ultimoNs)
            val d = fotograma - ultimoFotograma
            if (d > 1) { saltos++; saltados += d - 1 }
        }
        if (movimientoNs < 0 && fotograma != fotogramaInicial) movimientoNs = marcaNs - pedidoNs
        publicados++
        ultimoNs = marcaNs
        ultimoFotograma = fotograma
        escena.add(escenaNs)
        cpu.add(cpuNs)
        var i = PEORES
        while (i > 0 && cpuNs > peoresCpu[i - 1]) i--
        if (i < PEORES) {
            for (j in PEORES - 1 downTo i + 1) { peoresCpu[j] = peoresCpu[j - 1]; peoresFotograma[j] = peoresFotograma[j - 1] }
            peoresCpu[i] = cpuNs; peoresFotograma[i] = fotograma
        }
        if (gpuNs >= 0) gpu.add(gpuNs)
        swap.add(swapNs)
        anticipar.add(anticiparNs)
    }

    /** La TextureView recibio un cuadro nuevo (onSurfaceTextureUpdated). */
    @Synchronized
    fun presentado(ahoraNs: Long) {
        if (ahoraNs < pedidoNs) return
        if (presentados == 0) primeraPresentacionNs = ahoraNs - pedidoNs
        else presentaciones.add(ahoraNs - ultimaPresentacionNs)
        presentados++
        ultimaPresentacionNs = ahoraNs
    }

    /**
     * El resumen, en lineas cortas para el registro.
     *
     * @param gc recogidas de basura durante la sesion, ya en texto, o null
     */
    @Synchronized
    fun resumen(finNs: Long, porque: String, gc: String?): List<String> {
        val s = (finNs - pedidoNs) / 1e9
        fun ms(ns: Long) = if (ns < 0) "-" else String.format(Locale.ROOT, "%.1f ms", ns / 1e6)
        val l = ArrayList<String>()
        l += String.format(Locale.ROOT, "%s %dx%d k=%d hasta %s: %d cuadros en %.2f s (%.1f por s), %d saltos de fotograma (%d cuadros sin dibujar)",
            motivo, ancho, alto, muestras, porque, publicados, s, if (s > 0) publicados / s else 0.0, saltos, saltados)
        l += "arranque: sonido listo en ${ms(sonidoNs)} ($segmentos tramos), primer cuadro publicado ${ms(primeroNs)}, " +
            "primer movimiento ${ms(movimientoNs)}, primera presentacion ${ms(primeraPresentacionNs)}"
        if (segmentos > 0) l += "sonido (a un vsync): el cabezal arranca a ${ms(cabezalNs)}" +
            (if (escritosAlArrancar >= 0) " con $escritosAlArrancar frames escritos" else "") +
            ", primera marca a ${ms(marcaNs)}, lo que suena avanza a ${ms(avanceNs)}"
        l += "cuadro ms (media p95 max): escena ${escena.ms()} | cpu ${cpu.ms()} | gpu ${if (conGpu) gpu.ms() else "sin medir"} | " +
            "swap ${swap.ms()} | anticipar ${anticipar.ms()}"
        val peores = (0 until PEORES).filter { peoresCpu[it] >= 0 }.joinToString(", ") {
            val n = peoresFotograma[it]
            // El primer o el segundo cuadro de un bloque
            val k = comienzos.binarySearch(n).let { r -> if (r >= 0) r else -r - 2 }
            val marca = if (k >= 0 && n - comienzos[k] <= 1) " al empezar un bloque" else ""
            String.format(Locale.ROOT, "%d (%.1f ms%s)", n, peoresCpu[it] / 1e6, marca)
        }
        if (peores.isNotEmpty()) l += "peores cuadros por cpu: $peores"
        l += "publicacion: ${intervalos.ritmo()}"
        l += if (presentados == 0) "presentacion: sin datos (la TextureView no llama a Previsualizador.presentado)"
            else "presentacion: $presentados cuadros, ${presentaciones.ritmo()}"
        if (gc != null) l += "gc: $gc"
        return l
    }

    private companion object {
        const val PEORES = 5
    }
}

/**
 * Los dibujos en pausa (hipotesis H3 del mapa: un cambio, un dibujo completo
 * en la cola, aunque ya haya otro mas nuevo detras). Solo del hilo de dibujo.
 */
internal class PausaVista(private val desdeNs: Long) {
    private var cambios = 0
    private var dibujos = 0
    private var repetidos = 0
    private val espera = Serie()
    private val dibujo = Serie()
    private var ancho = 0
    private var alto = 0
    private var muestras = 0

    /** Llego a la cola de dibujo un proyecto cambiado, [esperaNs] despues de pedirse. */
    fun cambio(esperaNs: Long) {
        cambios++
        espera.add(esperaNs)
    }

    /** Un dibujo en pausa; [repetido] si es el mismo proyecto y el mismo instante que el anterior. */
    fun dibujado(duracionNs: Long, repetido: Boolean, ancho: Int, alto: Int, muestras: Int) {
        dibujos++
        if (repetido) repetidos++
        dibujo.add(duracionNs)
        this.ancho = ancho; this.alto = alto; this.muestras = muestras
    }

    val vacia: Boolean get() = cambios == 0 && dibujos == 0

    fun resumen(finNs: Long): List<String> = listOf(
        String.format(Locale.ROOT, "pausa %dx%d k=%d en %.2f s: %d cambios, %d dibujos (%d repetidos: mismo proyecto e instante que el anterior)",
            ancho, alto, muestras, (finNs - desdeNs) / 1e9, cambios, dibujos, repetidos),
        "pausa ms (media p95 max): espera en cola tras un cambio ${espera.ms()} | dibujo ${dibujo.ms()}"
    )
}
