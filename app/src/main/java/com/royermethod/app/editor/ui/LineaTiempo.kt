package com.royermethod.app.editor.ui

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.royermethod.app.editor.modelo.Clip
import com.royermethod.app.editor.modelo.Edicion
import com.royermethod.app.editor.modelo.Linea
import com.royermethod.app.editor.modelo.PistaAudio
import com.royermethod.app.editor.modelo.Proyecto
import com.royermethod.app.editor.modelo.Transicion
import com.royermethod.app.ui.theme.Estilo
import com.royermethod.app.ui.theme.Ink
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * La linea de tiempo.
 *
 * Se dibuja entera en un solo lienzo, en vez de con un componente por
 * bloque. Asi el desplazamiento y el zoom solo repintan (no recomponen) y
 * no hay limite de ancho: un proyecto de diez minutos a zoom maximo mediria
 * cientos de miles de pixeles, mas de lo que admite un componente.
 *
 * El cabezal esta fijo en el centro y lo que se mueve es el tiempo, como en
 * todos los editores de movil. Arriba la pista principal (siempre a la
 * vista), debajo una fila por cada nivel de capas y luego el sonido.
 *
 * Gestos:
 *  - arrastrar de lado: mover el cabezal (con inercia)
 *  - pellizcar: zoom
 *  - tocar un bloque: elegirlo; tocar el vacio: soltarlo
 *  - mantener y arrastrar: cambiar de sitio un bloque
 *  - arrastrar las asas del elegido: recortarlo
 */

private const val REGLA = 24f
private const val PRINCIPAL_Y = 30f
private const val PRINCIPAL_H = 56f
private const val FILAS_Y = PRINCIPAL_Y + PRINCIPAL_H + 10f
private const val CAPA_H = 34f
private const val CAPA_PASO = 40f
private const val AUDIO_H = 30f
private const val AUDIO_PASO = 36f
private const val ASA = 16f
private const val BOTON_PASO = 22f
private const val IMAN = 10f

private enum class Pista { PRINCIPAL, CAPA, AUDIO }

/** Un rectangulo de la linea de tiempo: un bloque o un sonido. */
private class Pieza(
    val id: Long,
    val pista: Pista,
    /** Lo que se ve de el en la linea (en la principal, hasta la mitad de cada transicion). */
    val desde: Long,
    val hasta: Long,
    /** Donde empieza su contenido (en la principal puede ser antes de [desde]). */
    val inicio: Long,
    /** Fila dentro de su zona: capa o sonido. */
    val fila: Int,
    val clip: Clip? = null,
    val audio: PistaAudio? = null
)

private class Geometria(
    val piezas: List<Pieza>,
    /** Uniones de la pista principal: (id del bloque que sale, instante). */
    val pasos: List<Pair<Clip, Long>>,
    val finPrincipal: Long,
    val filasCapa: Int,
    val filasAudio: Int
)

private fun geometria(p: Proyecto): Geometria {
    val piezas = ArrayList<Pieza>()
    val ini = Linea.inicios(p.principal)
    val pasos = ArrayList<Pair<Clip, Long>>()
    for (i in p.principal.indices) {
        val c = p.principal[i]
        val antes = if (i > 0) Linea.solape(p.principal[i - 1], c) / 2 else 0L
        val despues = if (i + 1 < p.principal.size) Linea.solape(c, p.principal[i + 1]) / 2 else 0L
        piezas.add(Pieza(c.id, Pista.PRINCIPAL, ini[i] + antes, ini[i] + c.duracionUs - despues, ini[i], 0, clip = c))
        if (i + 1 < p.principal.size) pasos.add(c to ini[i] + c.duracionUs - despues)
    }
    var filasCapa = 0
    for (k in p.capas) {
        piezas.add(Pieza(k.id, Pista.CAPA, k.inicioUs, k.finUs, k.inicioUs, k.fila, clip = k.clip))
        filasCapa = max(filasCapa, k.fila + 1)
    }
    // Los sonidos van en la primera fila donde no pisan a otro
    val finDeFila = ArrayList<Long>()
    for (a in p.audios.sortedBy { it.inicioUs }) {
        var f = finDeFila.indexOfFirst { it <= a.inicioUs }
        if (f < 0) { finDeFila.add(a.finUs); f = finDeFila.lastIndex } else finDeFila[f] = a.finUs
        piezas.add(Pieza(a.id, Pista.AUDIO, a.inicioUs, a.finUs, a.inicioUs, f, audio = a))
    }
    return Geometria(piezas, pasos, Linea.duracionPrincipal(p.principal), filasCapa, finDeFila.size)
}

/** Lo que hay bajo el dedo. */
private sealed interface Objetivo {
    data class Bloque(val pieza: Pieza) : Objetivo
    data class Asa(val pieza: Pieza, val derecha: Boolean) : Objetivo
    data class Paso(val clip: Clip) : Objetivo
    data class Clave(val t: Long) : Objetivo
    data object Agregar : Objetivo
    data object AgregarSonido : Objetivo
    data object Nada : Objetivo
}

private enum class Modo { TOQUE, ARRASTRE, VERTICAL, ZOOM, LARGO, NADA }

/** Un bloque cogido para llevarlo a otro sitio de la pista principal. */
private class Cogido(val pieza: Pieza, val x: Float, val y: Float, val destino: Int)

@Composable
fun LineaTiempo(
    estado: EstadoEditor,
    recursos: Recursos,
    onAgregar: () -> Unit,
    onAgregarSonido: () -> Unit,
    modifier: Modifier = Modifier
) {
    val medidor = rememberTextMeasurer()
    val alcance = rememberCoroutineScope()
    val proyecto = estado.proyecto
    val geo = remember(proyecto) { geometria(proyecto) }
    val geoActual by rememberUpdatedState(geo)
    val agregar by rememberUpdatedState(onAgregar)
    val agregarSonido by rememberUpdatedState(onAgregarSonido)
    var desplazamientoY by remember { mutableFloatStateOf(0f) }
    var cogido by remember { mutableStateOf<Cogido?>(null) }
    var inercia by remember { mutableStateOf<Job?>(null) }

    Canvas(
        modifier
            .clipToBounds()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val abajo = awaitFirstDown(requireUnconsumed = false)
                    inercia?.cancel()
                    estado.reproduciendo = false
                    val g = geoActual
                    val pxUs = estado.dpPorSegundo.dp.toPx() / 1_000_000f
                    val centro = size.width / 2f
                    fun tiempoEnX(x: Float) = estado.tiempo + ((x - centro) / pxUs).toLong()
                    fun xDe(t: Long) = centro + (t - estado.tiempo) * pxUs

                    // ---- Que hay debajo ----
                    val pos = abajo.position
                    val objetivo: Objetivo = run {
                        val elegido = estado.seleccion
                        val idElegido = (elegido as? Seleccion.Bloque)?.id ?: (elegido as? Seleccion.Sonido)?.id
                        // Primero las asas del elegido, que sobresalen del bloque
                        g.piezas.firstOrNull { it.id == idElegido }?.let { pz ->
                            val (y0, y1) = filaY(pz, desplazamientoY, g)
                            val yy0 = y0.dp.toPx(); val yy1 = y1.dp.toPx()
                            if (pos.y in yy0..yy1) {
                                val x0 = xDe(pz.desde); val x1 = xDe(pz.hasta)
                                val asa = ASA.dp.toPx()
                                if (pos.x in (x0 - asa - 6.dp.toPx())..(x0 + 4.dp.toPx())) return@run Objetivo.Asa(pz, false)
                                if (pos.x in (x1 - 4.dp.toPx())..(x1 + asa + 6.dp.toPx())) return@run Objetivo.Asa(pz, true)
                                // Los rombos de los fotogramas clave, abajo del bloque
                                pz.clip?.claves?.forEach { k ->
                                    val xk = xDe(pz.inicio + k.tiempoUs)
                                    if (abs(pos.x - xk) < 12.dp.toPx() && abs(pos.y - (yy0 + yy1) / 2) < 14.dp.toPx()) return@run Objetivo.Clave(pz.inicio + k.tiempoUs)
                                }
                            }
                        }
                        // Botones de transicion entre bloques
                        val yBoton = (PRINCIPAL_Y + PRINCIPAL_H / 2).dp.toPx()
                        g.pasos.forEach { (c, t) ->
                            if (abs(pos.x - xDe(t)) < 16.dp.toPx() && abs(pos.y - yBoton) < 18.dp.toPx()) return@run Objetivo.Paso(c)
                        }
                        // El "+" del final
                        val xMas = xDe(g.finPrincipal) + 14.dp.toPx()
                        if (pos.x in (xMas - 4.dp.toPx())..(xMas + 46.dp.toPx()) && pos.y in PRINCIPAL_Y.dp.toPx()..(PRINCIPAL_Y + PRINCIPAL_H).dp.toPx()) {
                            return@run Objetivo.Agregar
                        }
                        if (estado.proyecto.principal.isEmpty() && pos.y in PRINCIPAL_Y.dp.toPx()..(PRINCIPAL_Y + PRINCIPAL_H).dp.toPx()) {
                            return@run Objetivo.Agregar
                        }
                        g.piezas.lastOrNull { pz ->
                            val (y0, y1) = filaY(pz, desplazamientoY, g)
                            pos.y in y0.dp.toPx()..y1.dp.toPx() && pos.x in xDe(pz.desde)..xDe(pz.hasta)
                        }?.let { return@run Objetivo.Bloque(it) }
                        // La fila de "agregar sonido" cuando no hay ninguno
                        if (g.filasAudio == 0) {
                            val y0 = (filasY(g) - desplazamientoY).dp.toPx()
                            if (pos.y in y0..(y0 + AUDIO_H.dp.toPx())) return@run Objetivo.AgregarSonido
                        }
                        Objetivo.Nada
                    }

                    // ---- Que gesto es ----
                    val umbral = viewConfiguration.touchSlop
                    val modo = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        var m = Modo.NADA
                        while (true) {
                            val ev = awaitPointerEvent()
                            if (ev.changes.count { it.pressed } >= 2) { m = Modo.ZOOM; break }
                            val c = ev.changes.firstOrNull { it.id == abajo.id }
                            if (c == null || !c.pressed) { m = Modo.TOQUE; break }
                            val d = c.position - abajo.position
                            if (d.getDistance() > umbral) {
                                m = if (abs(d.y) > abs(d.x) * 1.2f && objetivo !is Objetivo.Asa) Modo.VERTICAL else Modo.ARRASTRE
                                break
                            }
                        }
                        m
                    } ?: Modo.LARGO

                    when (modo) {
                        Modo.TOQUE -> when (objetivo) {
                            is Objetivo.Bloque -> estado.elegir(
                                if (objetivo.pieza.pista == Pista.AUDIO) Seleccion.Sonido(objetivo.pieza.id) else Seleccion.Bloque(objetivo.pieza.id)
                            )
                            is Objetivo.Paso -> estado.elegir(Seleccion.Paso(objetivo.clip.id))
                            is Objetivo.Clave -> estado.irA(objetivo.t)
                            Objetivo.Agregar -> agregar()
                            Objetivo.AgregarSonido -> agregarSonido()
                            is Objetivo.Asa -> {}
                            Objetivo.Nada -> estado.elegir(null)
                        }

                        Modo.ZOOM -> {
                            while (true) {
                                val ev = awaitPointerEvent()
                                if (ev.changes.none { it.pressed }) break
                                val z = ev.calculateZoom()
                                if (z != 1f) {
                                    estado.dpPorSegundo = (estado.dpPorSegundo * z).coerceIn(EstadoEditor.ZOOM_MIN, EstadoEditor.ZOOM_MAX)
                                }
                                ev.changes.forEach { it.consume() }
                            }
                        }

                        Modo.VERTICAL -> {
                            val maximo = max(0f, filasY(g) + (max(g.filasAudio, 1)) * AUDIO_PASO + 16f - size.height.toDp().value)
                            while (true) {
                                val ev = awaitPointerEvent()
                                val c = ev.changes.firstOrNull { it.id == abajo.id } ?: break
                                if (!c.pressed) break
                                desplazamientoY = (desplazamientoY - c.positionChange().y.toDp().value).coerceIn(0f, maximo)
                                c.consume()
                            }
                        }

                        Modo.ARRASTRE, Modo.LARGO -> {
                            val asa = objetivo as? Objetivo.Asa
                            val bloque = (objetivo as? Objetivo.Bloque)?.takeIf { modo == Modo.LARGO }
                            val base = estado.proyecto
                            val iman = IMAN.dp.toPx() / pxUs
                            fun imanes(sin: Long): List<Long> = buildList {
                                add(0L); add(estado.tiempo)
                                g.piezas.filter { it.id != sin }.forEach { add(it.desde); add(it.hasta) }
                            }
                            fun pegar(t: Long, puntos: List<Long>): Long {
                                if (!AjustesEditor.imanes) return t
                                val cerca = puntos.minByOrNull { abs(it - t) } ?: return t
                                return if (abs(cerca - t) < iman) cerca else t
                            }

                            if (asa != null) {
                                // ---- Recortar ----
                                estado.empezarGesto()
                                val pz = asa.pieza
                                val puntos = imanes(pz.id)
                                var acumulado = 0f
                                arrastrar(abajo.id) { dx ->
                                    acumulado += dx
                                    var d = (acumulado / pxUs).toLong()
                                    val borde = if (asa.derecha) pz.hasta else pz.desde
                                    d = pegar(borde + d, puntos) - borde
                                    estado.ajustar { recorte(base, pz, asa.derecha, d) }
                                }
                                estado.terminarGesto()
                            } else if (bloque != null) {
                                // ---- Llevar a otro sitio ----
                                val pz = bloque.pieza
                                estado.elegir(if (pz.pista == Pista.AUDIO) Seleccion.Sonido(pz.id) else Seleccion.Bloque(pz.id))
                                estado.empezarGesto()
                                val puntos = imanes(pz.id)
                                var dx = 0f
                                var dy = 0f
                                var x = abajo.position.x
                                when (pz.pista) {
                                    Pista.PRINCIPAL -> {
                                        val sin = base.copy(principal = base.principal.filter { it.id != pz.id })
                                        fun destino() = Edicion.puntoDeInsercion(sin, tiempoEnX(x))
                                        cogido = Cogido(pz, x, abajo.position.y, destino())
                                        arrastrar(abajo.id) { d ->
                                            x += d
                                            cogido = Cogido(pz, x, abajo.position.y, destino())
                                        }
                                        val a = destino()
                                        cogido = null
                                        estado.ajustar { Edicion.moverPrincipal(base, pz.id, a) }
                                    }
                                    Pista.CAPA -> arrastrar(abajo.id, vertical = { dy += it }) { d ->
                                        dx += d
                                        val dur = pz.hasta - pz.desde
                                        var ini = pz.desde + (dx / pxUs).toLong()
                                        // Se pega por el principio o por el final, lo que quede mas cerca
                                        val a = pegar(ini, puntos)
                                        val b = pegar(ini + dur, puntos) - dur
                                        ini = if (abs(a - ini) <= abs(b - ini) && a != ini) a else if (b != ini) b else ini
                                        val fila = (pz.fila + (dy / CAPA_PASO.dp.toPx()).roundToInt()).coerceAtLeast(0)
                                        estado.ajustar { Edicion.moverCapa(base, pz.id, ini.coerceAtLeast(0), fila) }
                                    }
                                    Pista.AUDIO -> arrastrar(abajo.id) { d ->
                                        dx += d
                                        val ini = pegar(pz.desde + (dx / pxUs).toLong(), puntos).coerceAtLeast(0)
                                        estado.ajustar { Edicion.cambiarAudio(base, pz.id) { it.copy(inicioUs = ini) } }
                                    }
                                }
                                estado.terminarGesto()
                            } else {
                                // ---- Mover el cabezal, con inercia al soltar ----
                                val rastro = VelocityTracker()
                                rastro.addPosition(abajo.uptimeMillis, abajo.position)
                                while (true) {
                                    val ev = awaitPointerEvent()
                                    val c = ev.changes.firstOrNull { it.id == abajo.id } ?: break
                                    if (!c.pressed) break
                                    rastro.addPosition(c.uptimeMillis, c.position)
                                    val d = c.positionChange().x
                                    if (d != 0f) estado.irA(estado.tiempo - (d / pxUs).toLong())
                                    c.consume()
                                }
                                val v = rastro.calculateVelocity().x
                                if (abs(v) > 300f) {
                                    inercia = alcance.launch {
                                        var previo = 0f
                                        AnimationState(0f, -v).animateDecay(exponentialDecay(frictionMultiplier = 1.6f)) {
                                            val paso = value - previo
                                            previo = value
                                            val antes = estado.tiempo
                                            estado.irA(estado.tiempo + (paso / pxUs).toLong())
                                            if (estado.tiempo == antes) cancelAnimation()
                                        }
                                    }
                                }
                            }
                        }

                        Modo.NADA -> {}
                    }
                }
            }
    ) {
        dibujar(estado, geo, recursos, medidor, desplazamientoY, cogido)
    }
}

/** Sigue un dedo hasta que se levanta, avisando de cuanto se mueve de lado. */
private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.arrastrar(
    id: androidx.compose.ui.input.pointer.PointerId,
    vertical: (Float) -> Unit = {},
    lateral: (Float) -> Unit
) {
    while (true) {
        val ev = awaitPointerEvent()
        val c = ev.changes.firstOrNull { it.id == id } ?: break
        if (!c.pressed) break
        val d = c.positionChange()
        if (d.y != 0f) vertical(d.y)
        if (d.x != 0f || d.y != 0f) lateral(d.x)
        c.consume()
    }
}

/** El recorte de una asa, calculado siempre desde como estaba al cogerla. */
private fun recorte(base: Proyecto, pz: Pieza, derecha: Boolean, d: Long): Proyecto {
    pz.audio?.let { a ->
        return if (derecha) {
            Edicion.recortarAudio(base, a.id, a.inicioUs, a.desdeUs, a.hastaUs + d)
        } else {
            val nd = (a.desdeUs + d).coerceIn(0, a.hastaUs - Clip.MINIMO_US).coerceAtLeast(a.desdeUs - a.inicioUs)
            val delta = nd - a.desdeUs
            Edicion.recortarAudio(base, a.id, a.inicioUs + delta, nd, a.hastaUs)
        }
    }
    val c = pz.clip ?: return base
    val v = if (c.esVideo) c.velocidad.toDouble() else 1.0
    val enArchivo = (d * v).toLong()
    val capa = base.capas.firstOrNull { it.id == c.id }
    return if (derecha) {
        if (c.esVideo) Edicion.recortar(base, c.id, c.desdeUs, c.hastaUs + enArchivo)
        else Edicion.recortar(base, c.id, 0, c.duracionUs + d)
    } else {
        // Por la izquierda una capa no puede empezar antes de cero
        val dd = if (capa != null) d.coerceAtLeast(-capa.inicioUs) else d
        val q = if (c.esVideo) Edicion.recortar(base, c.id, c.desdeUs + (dd * v).toLong(), c.hastaUs)
        else Edicion.recortar(base, c.id, 0, c.duracionUs - dd)
        if (capa == null) q else {
            val nueva = Edicion.clip(q, c.id) ?: return q
            Edicion.cambiarCapa(q, c.id) { it.copy(inicioUs = capa.inicioUs + (c.duracionUs - nueva.duracionUs)) }
        }
    }
}

/** Donde empiezan las filas de sonido (en dp, sin desplazar). */
private fun filasY(g: Geometria) = FILAS_Y + g.filasCapa * CAPA_PASO + (if (g.filasCapa > 0) 6f else 0f)

private fun filaY(pz: Pieza, desplazamientoY: Float, g: Geometria? = null): Pair<Float, Float> = when (pz.pista) {
    Pista.PRINCIPAL -> PRINCIPAL_Y to PRINCIPAL_Y + PRINCIPAL_H
    Pista.CAPA -> {
        val y = FILAS_Y + pz.fila * CAPA_PASO - desplazamientoY
        y to y + CAPA_H
    }
    Pista.AUDIO -> {
        val y = (g?.let { filasY(it) } ?: 0f) + pz.fila * AUDIO_PASO - desplazamientoY
        y to y + AUDIO_H
    }
}

// ----------------------------------------------------------------------
// Dibujo
// ----------------------------------------------------------------------

private fun DrawScope.dibujar(
    estado: EstadoEditor,
    g: Geometria,
    recursos: Recursos,
    medidor: TextMeasurer,
    desplazamientoY: Float,
    cogido: Cogido?
) {
    val pxUs = estado.dpPorSegundo.dp.toPx() / 1_000_000f
    val centro = size.width / 2f
    val t0 = estado.tiempo
    fun x(t: Long) = centro + (t - t0) * pxUs
    val radio = CornerRadius(7.dp.toPx() * Estilo.redondez)
    val elegido = estado.idElegido
    val p = estado.proyecto

    // ---- Filas de abajo (capas y sonido), recortadas bajo la principal ----
    val arriba = FILAS_Y.dp.toPx() - 4.dp.toPx()
    clipRect(top = arriba) {
        for (pz in g.piezas) {
            if (pz.pista == Pista.PRINCIPAL) continue
            val (y0d, y1d) = filaY(pz, desplazamientoY, g)
            val y0 = y0d.dp.toPx(); val y1 = y1d.dp.toPx()
            val x0 = x(pz.desde); val x1 = x(pz.hasta)
            if (x1 < -50 || x0 > size.width + 50 || y1 < arriba || y0 > size.height) continue
            if (pz.pista == Pista.CAPA) bloque(pz, x0, x1, y0, y1, radio, pxUs, recursos, medidor, pz.id == elegido)
            else sonido(pz, x0, x1, y0, y1, radio, recursos, medidor, pz.id == elegido)
        }
        if (g.filasAudio == 0) {
            val y0 = (filasY(g) - desplazamientoY).dp.toPx()
            val ancho = 150.dp.toPx()
            val x0 = max(x(0), 12.dp.toPx())
            drawRoundRect(Ink.White04, Offset(x0, y0), Size(ancho, AUDIO_H.dp.toPx()), radio)
            drawRoundRect(Ink.White15, Offset(x0, y0), Size(ancho, AUDIO_H.dp.toPx()), radio, style = Stroke(1.dp.toPx()))
            texto(medidor, "+  Agregar sonido", Offset(x0 + 12.dp.toPx(), y0 + AUDIO_H.dp.toPx() / 2), Ink.White50, 11.5f)
        }
    }

    // ---- Regla ----
    regla(t0, pxUs, centro, medidor, estado.duracion)

    // ---- Pista principal ----
    val y0 = PRINCIPAL_Y.dp.toPx(); val y1 = (PRINCIPAL_Y + PRINCIPAL_H).dp.toPx()
    if (p.principal.isEmpty()) {
        val w = 210.dp.toPx()
        val x0 = centro + 12.dp.toPx()
        drawRoundRect(Ink.White, Offset(x0, y0), Size(w, y1 - y0), radio)
        val cruz = 9.dp.toPx(); val cx = x0 + 26.dp.toPx(); val cy = (y0 + y1) / 2
        drawLine(Ink.Black, Offset(cx - cruz, cy), Offset(cx + cruz, cy), 2.2.dp.toPx(), StrokeCap.Round)
        drawLine(Ink.Black, Offset(cx, cy - cruz), Offset(cx, cy + cruz), 2.2.dp.toPx(), StrokeCap.Round)
        texto(medidor, "Agregar video o foto", Offset(x0 + 46.dp.toPx(), cy), Ink.Black, 13f, negrita = true)
    }
    for (pz in g.piezas) {
        if (pz.pista != Pista.PRINCIPAL) continue
        if (cogido?.pieza?.id == pz.id) continue
        val x0 = x(pz.desde); val x1 = x(pz.hasta)
        if (x1 < -50 || x0 > size.width + 50) continue
        bloque(pz, x0, x1, y0, y1, radio, pxUs, recursos, medidor, pz.id == elegido)
    }
    // Botones de transicion entre bloques
    val elegidoPaso = (estado.seleccion as? Seleccion.Paso)?.id
    // Las del bloque elegido se esconden: ahi van sus asas
    val vecinos = p.principal.indexOfFirst { it.id == elegido }.let { i -> if (i < 0) emptySet() else setOfNotNull(elegido, p.principal.getOrNull(i - 1)?.id) }
    for ((c, t) in g.pasos) {
        if (c.id in vecinos && elegidoPaso == null) continue
        val cx = x(t)
        if (cx < -30 || cx > size.width + 30) continue
        val r = BOTON_PASO.dp.toPx() / 2
        val cy = (y0 + y1) / 2
        val activo = c.id == elegidoPaso
        drawRoundRect(if (activo) Ink.White else Ink.Black, Offset(cx - r, cy - r), Size(2 * r, 2 * r), CornerRadius(6.dp.toPx() * Estilo.redondez))
        drawRoundRect(Ink.White50, Offset(cx - r, cy - r), Size(2 * r, 2 * r), CornerRadius(6.dp.toPx() * Estilo.redondez), style = Stroke(1.dp.toPx()))
        translate(cx - r * 0.75f, cy - r * 0.75f) {
            val s = Size(r * 1.5f, r * 1.5f)
            val color = if (activo) Ink.Black else Ink.White
            if (c.transicion == Transicion.NINGUNA) {
                drawLine(color, Offset(s.width / 2, s.height * 0.2f), Offset(s.width / 2, s.height * 0.8f), 1.6.dp.toPx(), StrokeCap.Round)
            } else {
                val camino = Path().apply {
                    moveTo(s.width * 0.12f, s.height * 0.18f); lineTo(s.width * 0.5f, s.height * 0.5f); lineTo(s.width * 0.12f, s.height * 0.82f); close()
                    moveTo(s.width * 0.88f, s.height * 0.18f); lineTo(s.width * 0.5f, s.height * 0.5f); lineTo(s.width * 0.88f, s.height * 0.82f); close()
                }
                drawPath(camino, color)
            }
        }
    }
    // El "+" para seguir agregando
    if (p.principal.isNotEmpty()) {
        val xm = x(g.finPrincipal) + 14.dp.toPx()
        val lado = 40.dp.toPx()
        val ym = (y0 + y1) / 2 - lado / 2
        drawRoundRect(Ink.White, Offset(xm, ym), Size(lado, lado), CornerRadius(9.dp.toPx() * Estilo.redondez))
        val cruz = 8.dp.toPx()
        drawLine(Ink.Black, Offset(xm + lado / 2 - cruz, ym + lado / 2), Offset(xm + lado / 2 + cruz, ym + lado / 2), 2.2.dp.toPx(), StrokeCap.Round)
        drawLine(Ink.Black, Offset(xm + lado / 2, ym + lado / 2 - cruz), Offset(xm + lado / 2, ym + lado / 2 + cruz), 2.2.dp.toPx(), StrokeCap.Round)
    }

    // ---- Bloque cogido y donde caeria ----
    if (cogido != null) {
        val ini = Linea.inicios(p.principal.filter { it.id != cogido.pieza.id })
        val resto = p.principal.filter { it.id != cogido.pieza.id }
        val tDestino = if (cogido.destino < resto.size) ini[cogido.destino] else Linea.duracionPrincipal(resto)
        val xd = x(tDestino)
        drawLine(Ink.White, Offset(xd, y0 - 4.dp.toPx()), Offset(xd, y1 + 4.dp.toPx()), 3.dp.toPx(), StrokeCap.Round)
        val w = min(x(cogido.pieza.hasta) - x(cogido.pieza.desde), 120.dp.toPx())
        val gx = cogido.x - w / 2
        drawRoundRect(Ink.Glow18, Offset(gx - 6, y0 - 10), Size(w + 12, y1 - y0 + 12), radio)
        translate(0f, -8f) {
            bloque(cogido.pieza, gx, gx + w, y0, y1, radio, pxUs, recursos, medidor, true, desdeX = gx)
        }
    }

    // ---- Cabezal ----
    drawLine(Ink.Glow18, Offset(centro, 4.dp.toPx()), Offset(centro, size.height), 6.dp.toPx())
    drawLine(Ink.White, Offset(centro, 4.dp.toPx()), Offset(centro, size.height), 2.dp.toPx())
    drawCircle(Ink.White, 4.5.dp.toPx(), Offset(centro, 6.dp.toPx()))
}

private fun DrawScope.regla(t0: Long, pxUs: Float, centro: Float, medidor: TextMeasurer, duracion: Long) {
    val pxS = pxUs * 1_000_000f
    // Paso entre marcas con nombre: el primero que deje al menos 70 dp
    val pasos = longArrayOf(33_333, 100_000, 200_000, 500_000, 1_000_000, 2_000_000, 5_000_000, 10_000_000, 15_000_000, 30_000_000, 60_000_000, 120_000_000, 300_000_000)
    val paso = pasos.firstOrNull { it / 1_000_000f * pxS >= 70.dp.toPx() } ?: pasos.last()
    val menor = if (paso == 33_333L) paso else paso / (if (paso.toString().startsWith("2")) 4 else 5)
    val desde = t0 - (centro / pxUs).toLong()
    val hasta = t0 + ((size.width - centro) / pxUs).toLong()
    val yTexto = 10.dp.toPx()
    var t = max(0L, floor(desde.toDouble() / menor).toLong() * menor)
    while (t <= hasta) {
        val xx = centro + (t - t0) * pxUs
        val esMayor = if (paso == 33_333L) (t / 33_333L) % 10 == 0L else t % paso == 0L
        if (esMayor) {
            val etiqueta = if (paso < 1_000_000) {
                if (paso == 33_333L) "${t / 1_000_000}:${"%02d".format((t % 1_000_000) / 33_333)}f" else "%.1fs".format(t / 1_000_000f)
            } else {
                val s = t / 1_000_000
                "%d:%02d".format(s / 60, s % 60)
            }
            texto(medidor, etiqueta, Offset(xx, yTexto), if (t <= duracion) Ink.White50 else Ink.White30, 9.5f, centrado = true)
        } else {
            // Sin puntos pegados a una etiqueta: se leeria peor
            val mayorCerca = paso != 33_333L && run {
                val m = Math.round(t.toDouble() / paso) * paso
                abs((m - t) * pxUs) < 20.dp.toPx()
            }
            if (!mayorCerca) drawCircle(if (t <= duracion) Ink.White30 else Ink.White15, 1.3.dp.toPx(), Offset(xx, yTexto))
        }
        t += menor
        if (paso == 33_333L) t = Linea.tiempoDe(Linea.fotogramaDe(t))
    }
    drawLine(Ink.White08, Offset(0f, REGLA.dp.toPx() - 1), Offset(size.width, REGLA.dp.toPx() - 1), 1f)
}

private fun DrawScope.bloque(
    pz: Pieza, x0: Float, x1: Float, y0: Float, y1: Float, radio: CornerRadius, pxUs: Float,
    recursos: Recursos, medidor: TextMeasurer, elegido: Boolean, desdeX: Float? = null
) {
    val c = pz.clip ?: return
    val alto = y1 - y0
    val forma = Path().apply { addRoundRect(RoundRect(Rect(x0, y0, x1, y1), radio)) }
    clipPath(forma) {
        drawRect(if (c.esTexto) Ink.White15 else Ink.White08, Offset(x0, y0), Size(x1 - x0, alto))
        val o = c.origen
        if (o != null) {
            // Tira de fotogramas: cuadrados alineados con el principio del contenido
            val lado = alto
            val xIni = desdeX ?: (x0 - (pz.desde - pz.inicio) * pxUs)
            val k0 = max(0, floor((max(x0, 0f) - xIni) / lado).toInt())
            val k1 = ceil((min(x1, size.width) - xIni) / lado).toInt()
            val usPorLado = (lado / pxUs).toLong().coerceAtLeast(1)
            val v = if (c.esVideo) c.velocidad else 1f
            val cuanto = max((usPorLado * v).toLong(), 200_000L)
            for (k in k0..k1) {
                val local = (k * lado + lado / 2) / pxUs
                val ta = if (c.esVideo) (c.tiempoArchivo(local.toLong()) / cuanto) * cuanto else 0L
                val img = recursos.miniatura(o, ta) ?: continue
                val dx = xIni + k * lado
                // Recorte al centro para llenar el cuadrado
                val iw = img.width; val ih = img.height
                val m = min(iw, ih)
                drawImage(
                    img,
                    srcOffset = IntOffset((iw - m) / 2, (ih - m) / 2),
                    srcSize = IntSize(m, m),
                    dstOffset = IntOffset(dx.roundToInt(), y0.roundToInt()),
                    dstSize = IntSize(ceil(lado).toInt() + 1, alto.roundToInt())
                )
            }
            drawRect(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)), y0 + alto * 0.45f, y1),
                Offset(x0, y0), Size(x1 - x0, alto)
            )
        }
        val ancho = x1 - x0
        if (c.esTexto) {
            texto(medidor, "T  " + c.nombre(), Offset(x0 + 10.dp.toPx(), y0 + alto / 2), Ink.White90, 11.5f, maxAncho = ancho - 14.dp.toPx())
        } else if (ancho > 44.dp.toPx()) {
            var etiqueta = segundos(c.duracionUs)
            if (c.esVideo && c.velocidad != 1f) etiqueta += "  ·  ${"%.2g".format(c.velocidad)}x"
            texto(medidor, etiqueta, Offset(x0 + 7.dp.toPx(), y1 - 9.dp.toPx()), Color.White.copy(alpha = 0.92f), 9.5f, maxAncho = ancho - 10.dp.toPx())
        }
        if (c.entrada.isNotEmpty() || c.salida.isNotEmpty() || c.continua.isNotEmpty()) {
            // Una raya arriba marca que el bloque lleva animacion
            val ini = if (c.entrada.isNotEmpty()) c.duracionEntradaUs * pxUs else 0f
            val fin = if (c.salida.isNotEmpty()) c.duracionSalidaUs * pxUs else 0f
            val xb = x0 - (pz.desde - pz.inicio) * pxUs
            if (ini > 0) drawRect(Ink.White70, Offset(xb, y0), Size(ini, 3.dp.toPx()))
            if (fin > 0) drawRect(Ink.White70, Offset(xb + c.duracionUs * pxUs - fin, y0), Size(fin, 3.dp.toPx()))
            if (c.continua.isNotEmpty()) drawRect(Ink.White30, Offset(x0, y0), Size(ancho, 1.5.dp.toPx()))
        }
    }
    if (elegido) {
        drawRoundRect(Ink.White, Offset(x0, y0), Size(x1 - x0, alto), radio, style = Stroke(2.dp.toPx()))
        asas(x0, x1, y0, y1)
        // Fotogramas clave
        val xb = x0 - (pz.desde - pz.inicio) * pxUs
        for (k in c.claves) {
            val xk = xb + k.tiempoUs * pxUs
            if (xk < x0 - 1 || xk > x1 + 1) continue
            val r = 4.5.dp.toPx()
            val cy = y0 + alto / 2
            rotate(45f, Offset(xk, cy)) {
                drawRect(Ink.Black, Offset(xk - r, cy - r), Size(2 * r, 2 * r))
                drawRect(Ink.White, Offset(xk - r * 0.7f, cy - r * 0.7f), Size(1.4f * r, 1.4f * r))
            }
        }
    } else {
        drawRoundRect(Color.Black.copy(alpha = 0.6f), Offset(x0, y0), Size(x1 - x0, alto), radio, style = Stroke(1.dp.toPx()))
    }
}

private fun DrawScope.asas(x0: Float, x1: Float, y0: Float, y1: Float) {
    val w = ASA.dp.toPx()
    val r = CornerRadius(5.dp.toPx())
    val alto = y1 - y0
    for (der in listOf(false, true)) {
        val xa = if (der) x1 else x0 - w
        drawRoundRect(Ink.White, Offset(xa, y0), Size(w, alto), r)
        val cx = xa + w / 2
        val cy = y0 + alto / 2
        val s = 4.dp.toPx()
        val dir = if (der) 1 else -1
        drawLine(Ink.Black, Offset(cx - dir * s / 2, cy - s), Offset(cx + dir * s / 2, cy), 1.8.dp.toPx(), StrokeCap.Round)
        drawLine(Ink.Black, Offset(cx + dir * s / 2, cy), Offset(cx - dir * s / 2, cy + s), 1.8.dp.toPx(), StrokeCap.Round)
    }
}

private fun DrawScope.sonido(
    pz: Pieza, x0: Float, x1: Float, y0: Float, y1: Float, radio: CornerRadius,
    recursos: Recursos, medidor: TextMeasurer, elegido: Boolean
) {
    val a = pz.audio ?: return
    val alto = y1 - y0
    val forma = Path().apply { addRoundRect(RoundRect(Rect(x0, y0, x1, y1), radio)) }
    clipPath(forma) {
        drawRect(Ink.White08, Offset(x0, y0), Size(x1 - x0, alto))
        val picos = recursos.onda(a.origen)
        if (picos != null && picos.isNotEmpty()) {
            val paso = 3.dp.toPx()
            val usPx = (pz.hasta - pz.desde) / max(1f, x1 - x0)
            var xx = max(x0, 0f)
            val fin = min(x1, size.width)
            val cy = y0 + alto * 0.55f
            while (xx < fin) {
                val t = a.desdeUs + ((xx - x0) * usPx).toLong()
                val i = (t / Recursos.PICOS_US).toInt().coerceIn(0, picos.lastIndex)
                val h = max(1.dp.toPx(), picos[i] * alto * 0.7f) * a.volumen.coerceIn(0.2f, 1.5f)
                drawLine(Ink.White50, Offset(xx, cy - h / 2), Offset(xx, cy + h / 2), 1.6.dp.toPx(), StrokeCap.Round)
                xx += paso
            }
        }
        texto(medidor, "♪  " + a.origen.nombre, Offset(x0 + 8.dp.toPx(), y0 + 9.dp.toPx()), Ink.White90, 9.5f, maxAncho = x1 - x0 - 12.dp.toPx())
    }
    if (elegido) {
        drawRoundRect(Ink.White, Offset(x0, y0), Size(x1 - x0, alto), radio, style = Stroke(2.dp.toPx()))
        asas(x0, x1, y0, y1)
    }
}

private fun DrawScope.texto(
    medidor: TextMeasurer, s: String, en: Offset, color: Color, sp: Float,
    centrado: Boolean = false, negrita: Boolean = false, maxAncho: Float = Float.MAX_VALUE
) {
    if (maxAncho < 8f) return
    val estilo = TextStyle(color = color, fontSize = (sp * Estilo.letra).sp, fontWeight = if (negrita) FontWeight.Medium else FontWeight.Normal, letterSpacing = 0.2.sp)
    val r = medidor.measure(
        s, estilo, overflow = TextOverflow.Ellipsis, maxLines = 1,
        constraints = Constraints(maxWidth = if (maxAncho == Float.MAX_VALUE) Constraints.Infinity else maxAncho.toInt().coerceAtLeast(1))
    )
    val x = if (centrado) en.x - r.size.width / 2 else en.x
    drawText(r, topLeft = Offset(x, en.y - r.size.height / 2))
}
