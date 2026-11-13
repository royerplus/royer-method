package com.royermethod.app.editor.modelo

import com.royermethod.app.editor.audio.FuentePcm
import com.royermethod.app.editor.audio.Mezclador
import com.royermethod.app.editor.audio.Pcm
import com.royermethod.app.editor.audio.PlanAudio
import com.royermethod.app.editor.audio.Remuestreador
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.lang.management.ManagementFactory
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sin

/**
 * Banco de lo que la vista previa y la exportacion hacen en la CPU del modelo:
 * Escena.cuadro por fotograma y lo que se prepara al pulsar play (plan de
 * audio, escena nueva, mezcla del primer trozo). No es una prueba: va apagado
 * y se enciende con -Dbench=1.
 *
 *   ./gradlew :app:testDebugUnitTest -Dbench=1 --tests '*EscenaRendimientoTest'
 *
 * Opciones: -Dbench.pasadas=10, -Dbench.calentar=3 (segundos por caso),
 * -Dbench.salida=ruta.json y -Dbench.version=... (si no, git rev-parse).
 *
 * Saca una linea BANCO por caso y un JSON en build/banco-escena.json (o en
 * -Dbench.salida=...). Son tiempos de la JVM del ordenador, no del telefono:
 * sirven para comparar proyectos entre si y un commit con otro. Lo que solo
 * existe en el telefono (decodificadores, AudioTrack, GPU, Choreographer,
 * Compose) no sale de aqui.
 *
 * El trabajo de la GPU va como cuenta, no como tiempo: dibujos de lienzo
 * completo y lecturas de textura por pixel, con la formula del sombreador
 * CAPA y suponiendo que todo cubre todo (cota superior).
 */
class EscenaRendimientoTest {

    private val encendido = System.getProperty("bench") == "1"
    private val pasadas = System.getProperty("bench.pasadas")?.toIntOrNull() ?: 10
    private val calentarNs = (System.getProperty("bench.calentar")?.toDoubleOrNull() ?: 3.0).times(1e9).toLong()
    private val salida = StringBuilder()

    @Test
    fun cuadrosYPreparacion() {
        assumeTrue("banco apagado: -Dbench=1 para medir", encendido)
        val casos = ProyectosBanco.todos()
        val filas = ArrayList<String>()
        println("BANCO maquina: ${Runtime.getRuntime().availableProcessors()} cpu, " +
            "${System.getProperty("java.vm.name")} ${System.getProperty("java.version")}, version ${version()}")
        for ((nombre, p) in casos) {
            // 4 muestras: reproduciendo en la vista previa (minOf(4, muestras)); 8: pausa y exportacion
            for (k in intArrayOf(4, 8)) filas.add(medirCuadros(nombre, p, k))
        }
        val prep = casos.map { (nombre, p) -> medirPreparacion(nombre, p) }
        val json = "{\"version\":\"${version()}\",\"maquina\":\"${Runtime.getRuntime().availableProcessors()} cpu " +
            "${System.getProperty("java.vm.name")} ${System.getProperty("java.version")}\",\"pasadas\":$pasadas," +
            "\"cuadros\":[${filas.joinToString(",")}],\"preparacion\":[${prep.joinToString(",")}]}"
        val f = File(System.getProperty("bench.salida") ?: "build/banco-escena.json")
        f.parentFile?.mkdirs()
        f.writeText(json)
        println("BANCO json: ${f.absolutePath}")
        print(salida)
    }

    /** Coste de cada llamada que se repite por muestra, en los casos con muchas claves. */
    @Test
    fun desglosePorLlamada() {
        assumeTrue("banco apagado: -Dbench=1 para medir", encendido)
        for ((nombre, p) in ProyectosBanco.todos().filter { it.first.contains("300") }) {
            val clip = p.principal.first { it.piezas.isNotEmpty() || it.claves.isNotEmpty() }
            val dur = clip.duracionUs
            val conPiezas = p.principal.firstOrNull { it.piezas.isNotEmpty() }
            val conProgresos = p.principal.flatMap { it.efectos }.maxByOrNull { it.progresos.size }
            val escena = Escena(p, 576, 1024, ProyectosBanco.medidor)
            val instantes = LongArray(997) { (it * dur / 997) }
            fun linea(que: String, n: Int, f: (Long) -> Any) {
                val (us, bytes) = porLlamada(instantes, f)
                println(String.format(java.util.Locale.ROOT, "BANCO desglose %-26s %-34s n=%4d  %8.3f us/llamada  %7d B/llamada",
                    nombre, que, n, us, bytes))
            }
            linea("Clip.encuadreEn", clip.claves.size) { clip.encuadreEn(it) }
            linea("Escena.encuadreEn (con continuas)", clip.claves.size) { escena.encuadreEn(clip, it) }
            if (conPiezas != null) linea("PiezaVideo.encuadreEn", conPiezas.piezas[0].claves.size) { conPiezas.piezas[0].encuadreEn(it) }
            if (conProgresos != null) linea("EfectoVideo.progresoEn", conProgresos.progresos.size) { conProgresos.progresoEn(it) }
            linea("Escena.efectosVideo (bloque)", clip.efectos.size) { escena.efectosVideo(clip, it) }
            linea("Linea.duracion", p.principal.size) { Linea.duracion(p) }
            linea("Linea.origenesEntre (anticipar)", p.principal.size) { Linea.origenesEntre(p, it, it + 1_000_000) }
        }
    }

    // ------------------------------------------------------------------
    // Por fotograma
    // ------------------------------------------------------------------

    private fun medirCuadros(nombre: String, p0: Proyecto, k: Int): String {
        val p = p0.copy(salida = p0.salida.copy(muestras = k))
        val (w, h) = 576 to 1024
        val e = Escena(p, w, h, ProyectosBanco.medidor)
        val total = Linea.fotogramas(p).toInt()
        val mx = ManagementFactory.getThreadMXBean() as? com.sun.management.ThreadMXBean
        val hilo = Thread.currentThread().id
        var sumidero = 0L
        val fin = System.nanoTime() + calentarNs
        while (System.nanoTime() < fin) for (n in 0 until total) sumidero += e.cuadro(n.toLong()).pasos.size
        val minimos = LongArray(total)
        var sumaTodas = 0L
        val bytes0 = mx?.getThreadAllocatedBytes(hilo) ?: 0L
        repeat(pasadas) { pasada ->
            for (n in 0 until total) {
                val t0 = System.nanoTime()
                val c = e.cuadro(n.toLong())
                val dt = System.nanoTime() - t0
                sumidero += c.pasos.size
                sumaTodas += dt
                minimos[n] = if (pasada == 0) dt else minOf(minimos[n], dt)
            }
        }
        val bytes = if (mx == null) -1L else (mx.getThreadAllocatedBytes(hilo) - bytes0) / (pasadas.toLong() * total)
        val ord = minimos.sorted()
        val media = minimos.average() / 1000.0
        val p95 = ord[(ord.size * 95 / 100).coerceAtMost(ord.lastIndex)] / 1000.0
        val maximo = ord.last() / 1000.0
        val peor = minimos.indices.maxByOrNull { minimos[it] } ?: 0
        // GPU a la medida de exportacion y a una pantalla de 1080 de ancho (H1)
        val g = gpu(e, total, h)
        val gp = gpu(Escena(p, 1080, 1920, ProyectosBanco.medidor), total, 1920)
        val linea = String.format(java.util.Locale.ROOT,
            "BANCO %-26s k=%d cuadros=%4d  cpu us/cuadro: media(todas)=%7.1f  min por cuadro: media=%7.1f p95=%7.1f max=%7.1f (cuadro %d)  " +
                "asig=%7d B/cuadro  gpu 576x1024: dibujos=%.1f (max %d en %d) lecturas/px=%.1f (max %.0f en %d) videos=%.2f (max %d)  " +
                "1080x1920: lecturas/px=%.1f (max %.0f)  millones de lecturas/cuadro 576x1024=%.0f 1080x1920=%.0f (x%.1f)  [%d]",
            nombre, k, total, sumaTodas / 1000.0 / (pasadas.toLong() * total), media, p95, maximo, peor, bytes,
            g.dibujos, g.dibujosMax, g.cuadroDibujosMax, g.lecturas, g.lecturasMax, g.cuadroLecturasMax, g.videos, g.videosMax,
            gp.lecturas, gp.lecturasMax,
            g.lecturas * w * h / 1e6, gp.lecturas * 1080 * 1920 / 1e6, gp.lecturas * 1080 * 1920 / (g.lecturas * w * h), sumidero % 7)
        salida.append(linea).append('\n')
        println(linea)
        return "{\"caso\":\"$nombre\",\"k\":$k,\"cuadros\":$total,\"usMediaTodas\":${r(sumaTodas / 1000.0 / (pasadas.toLong() * total))}," +
            "\"usMedia\":${r(media)},\"usP95\":${r(p95)},\"usMax\":${r(maximo)},\"cuadroMax\":$peor,\"bytesPorCuadro\":$bytes," +
            "\"dibujos\":${r(g.dibujos)},\"dibujosMax\":${g.dibujosMax},\"cuadroDibujosMax\":${g.cuadroDibujosMax},\"lecturasPx\":${r(g.lecturas)},\"lecturasPxMax\":${r(g.lecturasMax)},\"cuadroLecturasMax\":${g.cuadroLecturasMax}," +
            "\"videos\":${r(g.videos)},\"videosMax\":${g.videosMax},\"lecturasPx1080\":${r(gp.lecturas)},\"lecturasPx1080Max\":${r(gp.lecturasMax)}," +
            "\"millonesLecturas\":${r(g.lecturas * w * h / 1e6)},\"millonesLecturas1080\":${r(gp.lecturas * 1080 * 1920 / 1e6)}}"
    }

    private class Gpu(val dibujos: Double, val dibujosMax: Int, val lecturas: Double, val lecturasMax: Double, val videos: Double, val videosMax: Int,
                      val cuadroDibujosMax: Int, val cuadroLecturasMax: Int)

    private class Cuenta { var dibujos = 0; var lecturas = 0.0 }

    private fun gpu(e: Escena, total: Int, alto: Int): Gpu {
        var dib = 0.0; var dibMax = 0; var lec = 0.0; var lecMax = 0.0; var vid = 0.0; var vidMax = 0
        var nDib = 0; var nLec = 0
        for (n in 0 until total) {
            val cuadro = e.cuadro(n.toLong())
            val c = Cuenta()
            contar(cuadro.pasos, c, alto)
            dib += c.dibujos; lec += c.lecturas
            if (c.dibujos > dibMax) { dibMax = c.dibujos; nDib = n }
            if (c.lecturas > lecMax) { lecMax = c.lecturas; nLec = n }
            // Videos que decodificar y copiar en este fotograma (el fondo comparte el del bloque)
            val v = cuadro.clips.filter { it.visible && it.clip.esVideo && !it.esFondo }
                .map { it.clip.id to it.tiempoArchivoUs }.toSet().size
            vid += v; vidMax = maxOf(vidMax, v)
        }
        return Gpu(dib / total, dibMax, lec / total, lecMax, vid / total, vidMax, nDib, nLec)
    }

    /** Lecturas de textura por pixel y por muestra del obturador que hace el sombreador CAPA con este paso. */
    private fun lecturas(p: PasoClip, alto: Int): Int {
        // Lo mismo que sube Compositor.dibujarClip: el fondo va con disco y sin RGB ni rastro
        val rgb = !p.esFondo && p.rgb > 0f
        val dx = if (p.esFondo) 0f else p.desenfoqueX
        val dy = if (p.esFondo) 0f else p.desenfoqueY
        val radio = if (p.esFondo) alto * 0.035f else p.desenfoque
        val rastro = dx != 0f || dy != 0f
        val porToma = if (rgb) 3 else 1
        return when {
            radio <= 0f && rastro -> ceil(2.0 * (abs(dx) + abs(dy)) / 3.0).toInt().coerceIn(16, 64) * porToma
            radio > 0f || rastro -> 12 * porToma
            else -> 1 + (if (!p.esFondo && p.color.nitidez > 0f) 8 else 0) + (if (rgb) 2 else 0)
        }
    }

    /** Las del anillo de la claridad: una vez por pixel, no por muestra del obturador. */
    private fun lecturasClaridad(p: PasoClip): Int = if (!p.esFondo && p.color.claridad != 0f) 16 else 0

    /** Como Compositor.dibujar: un dibujo de lienzo completo por paso, por corte y muestra en las partes. */
    private fun contar(pasos: List<Paso>, c: Cuenta, alto: Int) {
        for (p in pasos) when (p) {
            is PasoSolido -> if (p.opacidadMedia > 0.002f) c.dibujos++
            is PasoLinea -> c.dibujos++
            is PasoClip -> if (p.visible) { c.dibujos++; c.lecturas += p.muestras * lecturas(p, alto).toDouble() + lecturasClaridad(p) }
            is PasoPartes -> if (p.visible) {
                for (i in 0 until p.muestras) {
                    for (corte in p.cortesEn(i)) {
                        val parte = p.partes[corte.parte]
                        if (!parte.visible) continue
                        c.dibujos++; c.lecturas += lecturas(parte, alto) + lecturasClaridad(parte)
                    }
                    // La muestra se suma al promedio
                    c.dibujos++; c.lecturas += 1
                }
                c.dibujos++; c.lecturas += 1
            }
            is PasoGrupo -> if (p.visible) {
                contar(p.pasos, c, alto)
                c.dibujos++; c.lecturas += p.matrices.size
            }
        }
    }

    // ------------------------------------------------------------------
    // Al pulsar play
    // ------------------------------------------------------------------

    /**
     * Lo que corre en la CPU entre reproducir() y el primer fotograma, sin lo
     * que es del telefono: en el hilo de dibujo, el plan de audio y la escena
     * nueva de 4 muestras; en el del sonido, la mezcla de los 150 ms que llenan
     * el buffer antes de que la pista arranque (con un tono en vez del
     * decodificador). Tambien lo que se repite en cada vsync o cada fotograma
     * aparte de la escena.
     */
    private fun medirPreparacion(nombre: String, p: Proyecto): String {
        val p4 = p.copy(salida = p.salida.copy(muestras = 4))
        val medio = Linea.duracion(p) / 2
        val datos = linkedMapOf<String, Double>()
        fun medir(que: String, repeticiones: Int = 400, f: () -> Any) {
            repeat(repeticiones) { f() }   // calentar
            val ts = LongArray(repeticiones)
            for (i in 0 until repeticiones) {
                val t0 = System.nanoTime(); f(); ts[i] = System.nanoTime() - t0
            }
            ts.sort()
            datos[que] = ts[ts.size / 2] / 1000.0
        }
        val p8 = p.copy(salida = p.salida.copy(muestras = 8))
        medir("duracion") { Linea.duracion(p) }
        medir("planAudio") { PlanAudio.de(p).size + PlanAudio.total(p) + PlanAudio.total(p) }
        medir("escenaNueva") { Escena(p4, 576, 1024, ProyectosBanco.medidor) }
        medir("primerCuadro") { Escena(p4, 576, 1024, ProyectosBanco.medidor).cuadroEn(medio) }
        // En pausa cada cambio trae un proyecto nuevo: escena nueva y cuadro con todas las muestras
        medir("cambioPausa") { Escena(p8, 576, 1024, ProyectosBanco.medidor).cuadroEn(medio) }
        medir("anticipar") { Linea.origenesEntre(p, medio, medio + 1_000_000) }
        val segs = PlanAudio.de(p)
        val total = PlanAudio.total(p)
        val desde = Pcm.frames(medio)
        val buffer = Pcm.HZ * 15 / 100
        val trozo = FloatArray(1024 * Pcm.CANALES)
        // Con 44.1 kHz se pasa por el remuestreador (sinc de 32 tomas); con 48 kHz va directo
        fun mezclador(desde: Long, hz: Int) = Mezclador(segs, total, desde) { s, dentro ->
            Mezclador.lector(s, dentro) { _, _ -> Tono(hz) to hz }
        }
        for (hz in intArrayOf(44_100, 48_000)) {
            val sufijo = if (hz == 48_000) "48k" else "44k"
            // Lo que el hilo del sonido mezcla antes de que la pista pueda arrancar (150 ms de buffer)
            medir("llenarBuffer_$sufijo", 60) {
                val m = mezclador(desde, hz)
                var hechos = 0
                while (hechos < buffer) { val n = m.leer(trozo, 1024); if (n <= 0) break; hechos += n }
                m.cerrar()
                hechos
            }
            // En regimen: lo que cuesta mezclar 1024 frames (21.3 ms de sonido), desde el principio
            val m = mezclador(0, hz)
            medir("mezcla1024_$sufijo", 120) { m.leer(trozo, 1024) }
            m.cerrar()
        }
        // Abrir un tramo de 44.1 kHz construye la tabla del filtro
        medir("remuestreadorNuevo", 60) { Remuestreador(Tono(44_100), 44_100.0) }
        val linea = String.format(java.util.Locale.ROOT,
            "BANCO preparacion %-26s segmentos=%2d  us: duracion=%.2f planAudio=%.1f escenaNueva=%.1f primerCuadro(k=4)=%.1f " +
                "cambioPausa(k=8)=%.1f anticipar=%.2f | sonido: llenarBuffer150ms 44.1k=%.0f 48k=%.0f mezcla1024 44.1k=%.1f 48k=%.1f " +
                "remuestreadorNuevo=%.0f",
            nombre, segs.size, datos["duracion"], datos["planAudio"], datos["escenaNueva"], datos["primerCuadro"],
            datos["cambioPausa"], datos["anticipar"], datos["llenarBuffer_44k"], datos["llenarBuffer_48k"],
            datos["mezcla1024_44k"], datos["mezcla1024_48k"], datos["remuestreadorNuevo"])
        salida.append(linea).append('\n')
        println(linea)
        return "{\"caso\":\"$nombre\",\"segmentos\":${segs.size}," + datos.entries.joinToString(",") { "\"${it.key}Us\":${r(it.value)}" } + "}"
    }

    /** Un tono en lugar del archivo: el decodificador es del telefono. */
    private class Tono(private val hz: Int) : FuentePcm {
        private var i = 0L
        override fun leer(destino: FloatArray, frames: Int): Int {
            for (f in 0 until frames) {
                val v = 0.3f * sin(2.0 * Math.PI * 440.0 * (i++) / hz).toFloat()
                destino[f * 2] = v; destino[f * 2 + 1] = v
            }
            return frames
        }
    }

    private fun porLlamada(instantes: LongArray, f: (Long) -> Any): Pair<Double, Long> {
        val mx = ManagementFactory.getThreadMXBean() as? com.sun.management.ThreadMXBean
        val hilo = Thread.currentThread().id
        var s = 0
        repeat(200) { for (t in instantes) s += f(t).hashCode() and 1 }
        var mejor = Long.MAX_VALUE
        var bytes = 0L
        repeat(15) {
            val b0 = mx?.getThreadAllocatedBytes(hilo) ?: 0L
            val t0 = System.nanoTime()
            for (t in instantes) s += f(t).hashCode() and 1
            mejor = minOf(mejor, System.nanoTime() - t0)
            bytes = ((mx?.getThreadAllocatedBytes(hilo) ?: 0L) - b0) / instantes.size
        }
        if (s == 42) println()
        return mejor / 1000.0 / instantes.size to bytes
    }

    private fun r(v: Double) = if (v.isFinite()) String.format(java.util.Locale.ROOT, "%.3f", v) else "null"

    private fun version(): String = System.getProperty("bench.version") ?: runCatching {
        ProcessBuilder("git", "rev-parse", "--short", "HEAD").redirectErrorStream(true).start()
            .inputStream.bufferedReader().readText().trim().takeIf { it.matches(Regex("[0-9a-f]{4,40}")) }
    }.getOrNull() ?: "?"
}

/**
 * Proyectos sinteticos del banco, de lo mas sencillo a lo que se parece a la
 * replica. Los mismos sirven para comparar los cuadros bit a bit antes y
 * despues de tocar la evaluacion. La sacudida aparece solo como carga, tal
 * cual la deja la app.
 */
internal object ProyectosBanco {
    val video = Origen("v", "v", TipoOrigen.VIDEO, 1080, 1920, duracionUs = 20_000_000, fps = 30f, conAudio = true)
    val foto = Origen("f", "f", TipoOrigen.IMAGEN, 3000, 4000)
    private val musica = Origen("m", "m", TipoOrigen.AUDIO, duracionUs = 60_000_000)
    val medidor = Medidor { c, h ->
        if (c.texto != null) (h * 0.4f) to (h * 0.08f)
        else c.origen!!.anchoVisible.toFloat() to c.origen!!.altoVisible.toFloat()
    }

    fun todos(): List<Pair<String, Proyecto>> = listOf(
        "a-sencillo" to sencillo(),
        "b-fondo-3cortes" to fondoTresCortes(),
        "c-complejo" to complejo(0, 0, 0),
        "c-complejo-30claves" to complejo(30, 20, 10),
        "d-complejo-300claves" to complejo(300, 120, 60),
        "e-herramientas" to herramientas(false),
        "f-herramientas-300claves" to herramientas(true)
    )

    /** (a) Un video y nada mas. */
    fun sencillo() = Proyecto(fondo = Fondo.NEGRO, principal = listOf(Clip(1, origen = video, hastaUs = 13_700_000)))

    /** (b) Tres bloques con el fondo desenfocado; uno es una foto encajada (deja ver el fondo). */
    fun fondoTresCortes() = Proyecto(principal = listOf(
        Clip(1, origen = video, hastaUs = 5_000_000),
        Clip(2, origen = foto, hastaUs = 4_000_000, encuadre = Encuadre(ajuste = Ajuste.ENCAJAR)),
        Clip(3, origen = video, desdeUs = 5_000_000, hastaUs = 9_700_000)))

    private fun claves(n: Int, dur: Long, semilla: Int) = List(n) { i ->
        val t = dur * i / n
        Clave(t, Encuadre(escala = 1f + 0.1f * ((i * 7 + semilla) % 5), x = 0.01f * ((i * 3 + semilla) % 7 - 3),
            y = 0.01f * ((i * 5) % 7 - 3), rotacion = ((i * 11) % 9 - 4).toFloat()),
            bezier = if (i % 2 == 0) Bezier(0.3f, 0f, 0.2f, 1f) else null)
    }

    /**
     * Claves por debajo de la centesima: las primeras a 1 ms una de otra (0,
     * 0.001, 0.002...) y el resto repartidas. Con [n] = 4 son 0, 1, 5 y 10 ms.
     */
    private fun clavesFinas(n: Int, dur: Long, semilla: Int): List<Clave> {
        if (n <= 4) return listOf(0L, 1_000L, 5_000L, 10_000L).take(n).mapIndexed { i, t ->
            Clave(t, Encuadre(escala = 1f + 0.02f * i, x = 0.004f * (i - semilla % 3), rotacion = 0.5f * i))
        }
        val finas = n / 3
        return List(n) { i ->
            val t = if (i < finas) i * 1_000L else finas * 1_000L + (dur - finas * 1_000L) * (i - finas) / (n - finas)
            Clave(t, Encuadre(escala = 1f + 0.05f * ((i * 7 + semilla) % 5), x = 0.006f * ((i * 3 + semilla) % 7 - 3),
                y = 0.005f * ((i * 5) % 7 - 3), rotacion = ((i * 11) % 9 - 4) * 0.5f),
                curva = Curva.entries[i % Curva.entries.size],
                bezier = if (i % 3 == 0) Bezier(0.25f, 0.1f, 0.25f, 1f) else null)
        }
    }

    private fun progresos(n: Int, paso: Long) = List(n) { ProgresoEfecto(it * paso, it / n.toFloat()) }

    private fun efectos(dur: Long, progresos: Int) = listOf(
        EfectoVideo(TipoEfectoVideo.SACUDIDA, 200_000, 700_000),
        EfectoVideo(TipoEfectoVideo.DESENFOQUE_HORIZONTAL, 250_000, 400_000, intensidad = 2f),
        EfectoVideo(TipoEfectoVideo.DESENFOQUE_VERTICAL, 600_000, 700_000),
        EfectoVideo(TipoEfectoVideo.CIRCULO_CERRAR, dur / 2, dur / 2 + 600_000, progresos = progresos(progresos, 20_000L)),
        EfectoVideo(TipoEfectoVideo.REVELAR_HORIZONTAL, 0, 500_000, progresos = progresos(progresos, 15_000L)),
        EfectoVideo(TipoEfectoVideo.BORDES_ESPEJO, 0, dur),
        EfectoVideo(TipoEfectoVideo.RGB, dur - 400_000, dur),
        EfectoVideo(TipoEfectoVideo.FLASH, dur - 300_000, dur)
    )

    /** (c) y (d): parecido en forma a la replica. Transiciones, mitades con claves, efectos con progresos, capas. */
    fun complejo(clavesClip: Int, clavesPieza: Int, progresos: Int): Proyecto {
        val trans = listOf(Transicion.EMPUJAR, Transicion.CIRCULO, Transicion.LINEA, Transicion.ZOOM,
            Transicion.DESENFOQUE, Transicion.APLASTAR, Transicion.GIRO, Transicion.FUNDIDO)
        val principal = (0 until 8).map { i ->
            val dur = 1_800_000L
            val origen = if (i % 3 == 2) foto else video
            Clip(10L + i, origen = origen, desdeUs = 0, hastaUs = dur,
                claves = claves(clavesClip, dur, i),
                entrada = listOf(Movimiento(Animacion.ZOOM_ENTRA), Movimiento(Animacion.GIRO, 0.5f), Movimiento(Animacion.DESENFOCAR)),
                salida = listOf(Movimiento(Animacion.SACUDIR)),
                duracionEntradaUs = 400_000, duracionSalidaUs = 300_000,
                continua = if (i % 2 == 0) listOf(Continuo(AnimacionContinua.CAMARA_EN_MANO)) else emptyList(),
                efectos = efectos(dur, progresos),
                piezas = if (i % 2 == 0) listOf(
                    PiezaVideo("arriba", RegionUv(0f, 0f, 1f, 0.5f), claves = claves(clavesPieza, 600_000, 1)),
                    PiezaVideo("abajo", RegionUv(0f, 0.5f, 1f, 1f), claves = claves(clavesPieza, 600_000, 2))
                ) else emptyList(),
                transicion = trans[i], duracionTransicionUs = 400_000)
        }
        val capas = listOf(
            Capa(0, 0, Clip(100, texto = Texto("TITULO"), hastaUs = 3_000_000, entrada = listOf(Movimiento(Animacion.REBOTE)))),
            Capa(2_000_000, 1, Clip(101, origen = foto, hastaUs = 4_000_000, claves = claves(clavesClip, 4_000_000, 5),
                encuadre = Encuadre(escala = 0.4f), efectos = efectos(4_000_000, progresos))),
            Capa(6_000_000, 0, Clip(102, texto = Texto("FIN"), hastaUs = 5_000_000, continua = listOf(Continuo(AnimacionContinua.PULSO))))
        )
        return Proyecto(principal = principal, capas = capas, audios = listOf(PistaAudio(1, musica, hastaUs = 20_000_000)))
    }

    /**
     * (e) y (f): lo nuevo de las herramientas de movimiento. Las cinco
     * combinaciones del catalogo, los tipos nuevos sueltos y solapados,
     * animaciones acumuladas (entrada, salida y siempre), una animacion
     * convertida en efecto, partes y bloques con claves finas. [pesado]:
     * 300 claves por bloque (100 a 1 ms), 120 por parte y 60 puntos de
     * progreso en los efectos de mascara y de mitades.
     */
    fun herramientas(pesado: Boolean): Proyecto {
        val dur = 1_800_000L
        val nClip = if (pesado) 300 else 4
        val nPieza = if (pesado) 120 else 4
        val nProg = if (pesado) 60 else 0
        fun ajustes(t: TipoEfectoVideo, a: AjustesEfecto.() -> AjustesEfecto = { this }) = CatalogoEfectos.ajustesDe(t).a()
        fun ef(t: TipoEfectoVideo, ini: Long, fin: Long, id: Long, intensidad: Float = 1f, prog: Boolean = false,
               a: AjustesEfecto.() -> AjustesEfecto = { this }) =
            EfectoVideo(t, ini, fin, intensidad = intensidad, id = id, ajustes = ajustes(t, a),
                progresos = if (prog && nProg > 0) progresos(nProg, (fin - ini) / nProg) else emptyList())
        fun comb(c: Clip, nombre: String, ini: Long) = CatalogoEfectos.aplicar(CatalogoEfectos.combinaciones.single { it.nombre == nombre }, c, ini)
        fun base(i: Int, origen: Origen = video) = Clip(20L + i, origen = origen, hastaUs = dur,
            claves = clavesFinas(nClip, dur, i))
        val trans = listOf(Transicion.EMPUJAR, Transicion.CIRCULO, Transicion.ZOOM, Transicion.LINEA, Transicion.DESENFOQUE, Transicion.FUNDIDO)

        // 0: mitades que se juntan (sin claves: MITADES solo actua sin partes) y sacudida con desenfoque
        var c0 = Clip(20, origen = video, hastaUs = dur)
        c0 = comb(c0, "Mitades que se juntan", 0)
        c0 = comb(c0, "Sacudida con desenfoque", 900_000)
        if (nProg > 0) c0 = c0.copy(efectos = c0.efectos.map {
            if (it.tipo == TipoEfectoVideo.MITADES) it.copy(progresos = progresos(nProg, it.duracionUs / nProg)) else it
        })
        // 1: circulo y anillo del catalogo, un barrido en diagonal y un circulo descentrado con progresos
        var c1 = base(1)
        c1 = comb(c1, "Circulo que se abre", 0)
        c1 = comb(c1, "Anillo que se abre", 1_000_000)
        c1 = c1.copy(efectos = c1.efectos + ef(TipoEfectoVideo.BARRIDO, 500_000, 1_100_000, 90, prog = true) { copy(angulo = 45f) } +
            ef(TipoEfectoVideo.CIRCULO, 1_200_000, 1_700_000, 91, prog = true) { copy(centroX = 0.2f, centroY = -0.15f, grosor = 0.1f) })
        // 2: latigazo con desenfoque y espejo, y mover, zoom, girar y dos rastros solapados
        var c2 = base(2, foto)
        c2 = comb(c2, "Latigazo con desenfoque", 200_000)
        c2 = c2.copy(efectos = c2.efectos + listOf(
            ef(TipoEfectoVideo.MOVER, 600_000, 1_100_000, 90),
            ef(TipoEfectoVideo.ZOOM, 700_000, 1_300_000, 91),
            ef(TipoEfectoVideo.GIRAR, 800_000, 1_300_000, 92),
            ef(TipoEfectoVideo.DESENFOQUE_DIRECCIONAL, 600_000, 1_200_000, 93) { copy(angulo = 30f) },
            ef(TipoEfectoVideo.DESENFOQUE_DIRECCIONAL, 900_000, 1_500_000, 94, intensidad = 2f) { copy(angulo = 120f) }))
        // 3: animaciones acumuladas y una de ellas convertida en efecto
        var c3 = base(3)
        c3 = c3.conAnimacion(AnimacionAplicada(0, "Entrada", MomentoAnimacion.ENTRADA, Combinaciones.entrada[3].piezas, duracionUs = 500_000))
        c3 = c3.conAnimacion(AnimacionAplicada(0, "Otra", MomentoAnimacion.ENTRADA, listOf(Movimiento(Animacion.GIRO), Movimiento(Animacion.DESENFOCAR)), duracionUs = 400_000))
        c3 = c3.conAnimacion(AnimacionAplicada(0, "Salida", MomentoAnimacion.SALIDA, Combinaciones.entrada[7].piezas, duracionUs = 400_000))
        c3 = c3.conAnimacion(AnimacionAplicada(0, "Siempre", MomentoAnimacion.SIEMPRE, continuas = Combinaciones.continuas[2].second))
        c3 = c3.convertirEnEfecto(c3.animaciones[1].id)?.clip ?: c3
        // 4: partes a mano con claves finas (y su bloque con claves)
        val c4 = base(4).copy(piezas = listOf(
            PiezaVideo("izquierda", RegionUv(0f, 0f, 0.5f, 1f), claves = clavesFinas(nPieza, 900_000, 1)),
            PiezaVideo("derecha", RegionUv(0.5f, 0f, 1f, 1f), claves = clavesFinas(nPieza, 900_000, 2))),
            efectos = listOf(ef(TipoEfectoVideo.DESENFOQUE_DIRECCIONAL, 0, 600_000, 1) { copy(angulo = 0f) }))
        // 5: mitades en vertical con entrada heredada y continuas de antes
        var c5 = Clip(25, origen = video, hastaUs = dur, entrada = listOf(Movimiento(Animacion.ZOOM_ENTRA)),
            continua = listOf(Continuo(AnimacionContinua.CAMARA_EN_MANO)))
        c5 = c5.copy(efectos = listOf(ef(TipoEfectoVideo.MITADES, 300_000, 900_000, 1, prog = true) { copy(vertical = true, angulo = 0f) },
            ef(TipoEfectoVideo.DESENFOQUE_DIRECCIONAL, 300_000, 900_000, 2) { copy(angulo = 0f) }))
        val principal = listOf(c0, c1, c2, c3, c4, c5).mapIndexed { i, c ->
            c.copy(transicion = trans[i], duracionTransicionUs = 400_000)
        }
        // Capas: un texto con animaciones aplicadas y una foto con circulo y claves finas
        val texto = Clip(200, texto = Texto("TITULO"), hastaUs = 3_000_000)
            .conAnimacion(AnimacionAplicada(0, "Rebote", MomentoAnimacion.ENTRADA, listOf(Movimiento(Animacion.REBOTE)), duracionUs = 700_000))
            .conAnimacion(AnimacionAplicada(0, "Pulso", MomentoAnimacion.SIEMPRE, continuas = listOf(Continuo(AnimacionContinua.PULSO))))
        val imagen = Clip(201, origen = foto, hastaUs = 4_000_000, encuadre = Encuadre(escala = 0.4f),
            claves = clavesFinas(nClip, 4_000_000, 7),
            efectos = listOf(ef(TipoEfectoVideo.CIRCULO, 0, 800_000, 1, prog = true),
                ef(TipoEfectoVideo.BARRIDO, 2_000_000, 2_600_000, 2, prog = true) { copy(angulo = 270f, invertir = true) }))
        return Proyecto(principal = principal,
            capas = listOf(Capa(0, 0, texto), Capa(3_000_000, 1, imagen)),
            audios = listOf(PistaAudio(1, musica, hastaUs = 20_000_000)))
    }
}
