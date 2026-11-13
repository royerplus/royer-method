package com.royermethod.app.editor.audio

import com.royermethod.app.editor.modelo.Clip
import com.royermethod.app.editor.modelo.Linea
import com.royermethod.app.editor.modelo.Proyecto
import kotlin.math.PI
import kotlin.math.sin

/**
 * Un trozo de audio colocado en la linea de tiempo, con su volumen y sus
 * fundidos. Todo en frames de audio a [Pcm.HZ]: contar en frames y no en
 * microsegundos es lo que garantiza que el audio dure exactamente lo mismo
 * que el video, sin un frame de mas ni de menos en cada junta.
 */
data class Segmento(
    val uri: String,
    /** Primer frame en la linea de tiempo. */
    val inicio: Long,
    /** Frames que ocupa en la linea de tiempo. */
    val frames: Long,
    /** Donde empieza en el archivo. */
    val desdeUs: Long,
    val velocidad: Float = 1f,
    val mantenerTono: Boolean = true,
    val volumen: Float = 1f,
    /** Frames de subida al empezar y de bajada al acabar. */
    val entrada: Long = 0,
    val salida: Long = 0,
    /** La subida o la bajada es un cruce con otro clip, no desde silencio. */
    val entradaCruce: Boolean = false,
    val salidaCruce: Boolean = false
) {
    val fin: Long get() = inicio + frames

    /** Volumen en el frame [local] del segmento. */
    fun ganancia(local: Long): Float {
        var g = volumen
        if (entrada > 0 && local < entrada) g *= curva((local + 0.5f) / entrada, entradaCruce)
        if (salida > 0 && local >= frames - salida) g *= curva((frames - local - 0.5f) / salida, salidaCruce)
        return g
    }

    private fun curva(x: Float, cruce: Boolean): Float {
        val t = x.coerceIn(0f, 1f)
        // En un cruce, igual potencia: a mitad los dos suenan a 0.707 y la
        // suma se oye igual de fuerte, sin el bajon de cruzar en linea recta.
        // Desde silencio, cuadratica: el oido la percibe como una subida pareja.
        return if (cruce) sin(t * PI.toFloat() / 2f) else t * t
    }
}

object PlanAudio {

    /** 1.5 ms: lo justo para que un corte seco no haga clic. */
    const val ANTICLIC = 72L

    /** Frames de audio que tiene el proyecto exportado. */
    fun total(p: Proyecto): Long = Pcm.frames(Linea.duracion(p))

    fun de(p: Proyecto): List<Segmento> {
        val total = total(p)
        val r = ArrayList<Segmento>()

        // Pista principal, con los cruces de las transiciones
        val ini = Linea.inicios(p.principal)
        val inicios = LongArray(p.principal.size) { Pcm.frames(ini[it]) }
        val fines = LongArray(p.principal.size) { Pcm.frames(ini[it] + p.principal[it].duracionUs) }
        for ((i, c) in p.principal.withIndex()) {
            if (!suena(c)) continue
            val cruceAntes = if (i > 0 && Linea.solape(p.principal[i - 1], c) > 0) fines[i - 1] - inicios[i] else 0L
            val cruceDespues = if (i + 1 < p.principal.size && Linea.solape(c, p.principal[i + 1]) > 0) fines[i] - inicios[i + 1] else 0L
            r.add(segmento(c, inicios[i], fines[i] - inicios[i], cruceAntes, cruceDespues))
        }

        // Capas: suenan tambien, como en cualquier editor
        for (capa in p.capas) {
            if (!suena(capa.clip)) continue
            val a = Pcm.frames(capa.inicioUs)
            val b = Pcm.frames(capa.finUs)
            r.add(segmento(capa.clip, a, b - a, 0, 0))
        }

        // Musica y sonidos sueltos
        for (pista in p.audios) {
            if (pista.volumen <= 0f) continue
            val a = Pcm.frames(pista.inicioUs)
            val b = Pcm.frames(pista.finUs)
            r.add(
                Segmento(
                    uri = pista.origen.uri,
                    inicio = a,
                    frames = b - a,
                    desdeUs = pista.desdeUs,
                    volumen = pista.volumen,
                    entrada = maxOf(ANTICLIC, Pcm.frames(pista.fundidoEntradaUs)),
                    salida = maxOf(ANTICLIC, Pcm.frames(pista.fundidoSalidaUs))
                )
            )
        }

        // Lo que pase del final del video no se oye: se corta ahi, con su
        // pequeno fundido para que no acabe en clic.
        return r.mapNotNull { s ->
            when {
                s.inicio >= total || s.frames <= 0 -> null
                s.fin <= total -> s
                else -> {
                    val frames = total - s.inicio
                    s.copy(frames = frames, salida = minOf(frames, maxOf(ANTICLIC, s.salida)), salidaCruce = false)
                }
            }
        }
    }

    private fun suena(c: Clip): Boolean =
        c.esVideo && c.origen?.conAudio == true && c.volumen > 0f

    private fun segmento(c: Clip, inicio: Long, frames: Long, cruceAntes: Long, cruceDespues: Long): Segmento {
        val fundidoEntrada = Pcm.frames(c.fundidoEntradaUs)
        val fundidoSalida = Pcm.frames(c.fundidoSalidaUs)
        return Segmento(
            uri = c.origen!!.uri,
            inicio = inicio,
            frames = frames,
            desdeUs = c.desdeUs,
            velocidad = c.velocidad,
            mantenerTono = c.mantenerTono,
            volumen = c.volumen,
            entrada = maxOf(cruceAntes, fundidoEntrada, ANTICLIC).coerceAtMost(frames),
            salida = maxOf(cruceDespues, fundidoSalida, ANTICLIC).coerceAtMost(frames),
            entradaCruce = cruceAntes > 0 && cruceAntes >= fundidoEntrada,
            salidaCruce = cruceDespues > 0 && cruceDespues >= fundidoSalida
        )
    }
}
