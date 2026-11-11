package com.royermethod.app.editor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.royermethod.app.editor.modelo.propiedadesEn
import com.royermethod.app.editor.modelo.Clip
import com.royermethod.app.editor.modelo.Edicion
import com.royermethod.app.editor.modelo.Encuadre
import com.royermethod.app.editor.modelo.Escena
import com.royermethod.app.editor.modelo.Linea
import com.royermethod.app.editor.modelo.Medidor
import com.royermethod.app.editor.modelo.Perspectiva
import com.royermethod.app.editor.modelo.ZoomVista
import com.royermethod.app.ui.components.esquinas
import com.royermethod.app.ui.theme.Ink
import com.royermethod.app.ui.theme.radio
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * La imagen del proyecto con lo que se puede hacer encima.
 *
 * La imagen en si la pone [vista] (en el telefono, la superficie donde
 * dibuja el motor). Aqui se encaja con la proporcion del proyecto, se dibuja
 * el marco del bloque elegido y se atienden los dedos: tocar elige el
 * bloque de mas arriba que haya debajo, arrastrar lo mueve, pellizcar lo
 * escala y girar con dos dedos lo gira. Cerca del centro y de los angulos
 * rectos se pega, con una guia, como en cualquier editor.
 */
@Composable
fun Lienzo(
    estado: EstadoEditor,
    medidor: Medidor,
    modifier: Modifier = Modifier,
    vista: @Composable (Modifier) -> Unit
) {
    val p = estado.proyecto
    val (cw, ch) = p.formato.medidas(p.salida.ladoCorto)
    val escena = remember(p, medidor) { Escena(p, cw, ch, medidor) }
    // El gesto no se reinicia con cada cambio del proyecto (que es lo que hace
    // el propio gesto): lee la escena del momento por aqui.
    val escenaActual by rememberUpdatedState(escena)
    var guiaX by remember { mutableStateOf(false) }
    var guiaY by remember { mutableStateOf(false) }
    // El ultimo toque con la herramienta Zoom (cuando y donde), para el doble toque
    var toqueZoom by remember { mutableStateOf<Pair<Long, Offset>?>(null) }

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val densidad = LocalDensity.current
        val aspecto = cw.toFloat() / ch
        val anchoMax = constraints.maxWidth.toFloat()
        val altoMax = constraints.maxHeight.toFloat()
        val (vw, vh) = if (anchoMax / altoMax > aspecto) altoMax * aspecto to altoMax else anchoMax to anchoMax / aspecto
        val escala = vw / cw

        Box(
            Modifier
                .size(with(densidad) { vw.toDp() }, with(densidad) { vh.toDp() })
                .clip(esquinas(6.dp))
                .background(Ink.White04)
        ) {
            vista(Modifier.fillMaxSize())

            Canvas(
                Modifier
                    .fillMaxSize()
                    .pointerInput(escala, vw, vh) {
                        awaitEachGesture {
                            val abajo = awaitFirstDown(requireUnconsumed = false)
                            if (estado.modoZoom) {
                                // Herramienta Zoom: solo la vista, sin elegir ni mover nada.
                                // Dos toques seguidos: acercar ahi (o quitar el zoom).
                                var recorridoZoom = 0f
                                var dosDedos = false
                                try {
                                    while (true) {
                                        val ev = awaitPointerEvent()
                                        if (ev.changes.none { it.pressed }) break
                                        if (ev.changes.count { it.pressed } >= 2) dosDedos = true
                                        recorridoZoom += ev.changes.first().positionChange().getDistance()
                                        val foco = ev.calculateCentroid(useCurrent = false)
                                        val pan = ev.calculatePan()
                                        val f = ev.calculateZoom()
                                        val fx = if (foco.isSpecified) foco.x else vw / 2
                                        val fy = if (foco.isSpecified) foco.y else vh / 2
                                        estado.zoomVista = estado.zoomVista.mover(f, fx, fy, pan.x, pan.y, vw, vh)
                                        ev.changes.forEach { it.consume() }
                                    }
                                } finally {
                                    if (!estado.zoomVista.puesto) estado.zoomVista = ZoomVista.NINGUNO
                                }
                                if (!dosDedos && recorridoZoom < viewConfiguration.touchSlop) {
                                    val ahora = abajo.uptimeMillis
                                    val antes = toqueZoom
                                    if (antes != null && ahora - antes.first < 320 && (abajo.position - antes.second).getDistance() < 60.dp.toPx()) {
                                        estado.zoomVista = if (estado.zoomVista.puesto) ZoomVista.NINGUNO
                                        else ZoomVista.NINGUNO.mover(2.5f, abajo.position.x, abajo.position.y, 0f, 0f, vw, vh)
                                        toqueZoom = null
                                    } else toqueZoom = ahora to abajo.position
                                }
                                return@awaitEachGesture
                            }
                            estado.reproduciendo = false
                            val t = estado.tiempo
                            // Con zoom, el dedo toca la pantalla, no el lienzo
                            val zv = estado.zoomVista
                            val (lx, ly) = zv.aLienzo(abajo.position.x, abajo.position.y, vw, vh)
                            val enLienzo = Offset(lx / escala, ly / escala)
                            val tocado = bloqueEn(estado, escenaActual, enLienzo.x, enLienzo.y, t)

                            // El bloque que se mueve: el elegido, toque donde toque (solo un
                            // toque sin arrastrar elige otro); sin nada elegido, el de debajo.
                            val elegido = (estado.seleccion as? Seleccion.Bloque)?.id
                            // Una esquina del elegido: arrastrarla cambia su tamano
                            val esquina = elegido?.takeIf { visible(estado, it, t) }?.let { id ->
                                esquinasEnPantalla(estado, escenaActual, id, t, escala, vw, vh)
                            }?.takeIf { esq -> esq.any { (it - abajo.position).getDistance() < 30.dp.toPx() } }
                            val centroEsquina = esquina?.let { e -> Offset(e.sumOf { it.x.toDouble() }.toFloat() / 4f, e.sumOf { it.y.toDouble() }.toFloat() / 4f) }
                            val dist0 = centroEsquina?.let { (abajo.position - it).getDistance().coerceAtLeast(1f) } ?: 1f
                            var objetivo: Long? = null
                            var movido = false
                            var e0: Encuadre? = null
                            // Las claves que se mueven juntas (null: la de bajo el cabezal)
                            var grupo: Set<Long>? = null
                            var pan = Offset.Zero
                            var zoom = 1f
                            var giro = 0f
                            val umbral = viewConfiguration.touchSlop
                            var recorrido = 0f
                            val base = estado.proyecto

                            try {
                                while (true) {
                                    val ev = awaitPointerEvent()
                                    val dedos = ev.changes.count { it.pressed }
                                    if (dedos == 0) break
                                    recorrido += ev.changes.first().positionChange().getDistance()
                                    if (!movido && (recorrido > umbral || dedos >= 2)) {
                                        movido = true
                                        objetivo = when {
                                            elegido != null && visible(estado, elegido, t) -> elegido
                                            tocado != null -> tocado
                                            else -> null
                                        }
                                        val id = objetivo
                                        if (id != null) {
                                            if (id != elegido) estado.elegir(Seleccion.Bloque(id))
                                            val c = Edicion.clip(base, id)!!
                                            val ini = Edicion.inicio(base, id) ?: 0
                                            e0 = c.encuadreEn((t - ini).coerceIn(0, c.duracionUs))
                                            grupo = estado.clavesQueMueve(c)
                                            estado.empezarGesto()
                                        }
                                    }
                                    val id = objetivo
                                    val inicial = e0
                                    if (movido && id != null && inicial != null && centroEsquina != null && dedos == 1) {
                                        // Esquina: el tamano sigue a la distancia del dedo al centro
                                        val dedo = ev.changes.first { it.pressed }.position
                                        val s = (inicial.escala * (dedo - centroEsquina).getDistance() / dist0).coerceIn(0.05f, 20f)
                                        val g = grupo
                                        if (g != null) estado.ajustar {
                                            Edicion.desplazarClaves(base, id, g, 0f, 0f, s / inicial.escala, 0f)
                                        } else estado.ajustar { q ->
                                            Edicion.ajustarEncuadre(q, id, t, toleranciaClaves()) { it.copy(escala = s) }
                                        }
                                        ev.changes.forEach { it.consume() }
                                    } else if (movido && id != null && inicial != null) {
                                        pan += ev.calculatePan()
                                        zoom *= ev.calculateZoom()
                                        giro += ev.calculateRotation()
                                        var x = inicial.x + pan.x / (vw * zv.escala)
                                        var y = inicial.y + pan.y / (vh * zv.escala)
                                        var r = inicial.rotacion + giro
                                        guiaX = abs(x) < 0.012f
                                        guiaY = abs(y) < 0.012f
                                        if (guiaX) x = 0f
                                        if (guiaY) y = 0f
                                        val recto = (r / 90f).roundToInt() * 90f
                                        if (abs(r - recto) < 4f) r = recto
                                        val s = (inicial.escala * zoom).coerceIn(0.05f, 20f)
                                        val g = grupo
                                        if (g != null) {
                                            // Todas las claves lo mismo, siempre desde como estaban
                                            // al empezar: el movimiento entre ellas no se deforma
                                            estado.ajustar {
                                                Edicion.desplazarClaves(base, id, g, x - inicial.x, y - inicial.y, s / inicial.escala, r - inicial.rotacion)
                                            }
                                        } else estado.ajustar { q ->
                                            Edicion.ajustarEncuadre(q, id, t, toleranciaClaves()) { it.copy(x = x, y = y, rotacion = r, escala = s) }
                                        }
                                        ev.changes.forEach { it.consume() }
                                    }
                                }
                            } finally {
                                // Tambien si el gesto se corta: que no quede abierto
                                // el paso de deshacer ni las guias pintadas.
                                guiaX = false
                                guiaY = false
                                if (objetivo != null) estado.terminarGesto()
                            }
                            if (!movido) {
                                // Un toque: elige; tocar otra vez un texto elegido lo abre para escribir
                                if (tocado != null && tocado == elegido && Edicion.clip(estado.proyecto, tocado)?.esTexto == true) {
                                    estado.panel = Panel.TEXTO
                                } else {
                                    estado.elegir(tocado?.let { Seleccion.Bloque(it) })
                                }
                            }
                        }
                    }
            ) {
                val pr = estado.proyecto
                val z = estado.zoomVista
                fun ap(px: Float, py: Float): Offset {
                    val (sx, sy) = z.aPantalla(px, py, size.width, size.height)
                    return Offset(sx, sy)
                }
                // El borde de lo que se exporta, siempre a la vista: lo que se
                // salga de esta linea no va a salir en el video.
                val grosor = 1.5.dp.toPx()
                val esquina = radio(6.dp).toPx()
                val o = ap(0f, 0f)
                drawRoundRect(
                    Color.White, Offset(o.x + grosor / 2, o.y + grosor / 2),
                    Size(size.width * z.escala - grosor, size.height * z.escala - grosor),
                    CornerRadius(esquina, esquina), style = Stroke(grosor)
                )
                val centro = ap(size.width / 2, size.height / 2)
                if (guiaX) drawLine(Ink.White70, Offset(centro.x, 0f), Offset(centro.x, size.height), 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
                if (guiaY) drawLine(Ink.White70, Offset(0f, centro.y), Offset(size.width, centro.y), 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
                if (estado.modoZoom) return@Canvas
                val id = (estado.seleccion as? Seleccion.Bloque)?.id ?: return@Canvas
                val c = Edicion.clip(pr, id) ?: return@Canvas
                val ini = Edicion.inicio(pr, id) ?: return@Canvas
                val local = estado.tiempo - ini
                if (local < 0 || local >= c.duracionUs) return@Canvas
                marcoElegido(esquinasEnPantalla(estado, escena, id, estado.tiempo, escala, size.width, size.height) ?: return@Canvas)
            }
            val z = estado.zoomVista
            if (z.puesto || estado.modoZoom) {
                Text(
                    if (z.puesto) "%.1fx".format(z.escala) else "Zoom",
                    style = MaterialTheme.typography.labelSmall,
                    color = Ink.White90,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .clip(esquinas(12.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }
}

/**
 * Las 4 esquinas del bloque [id] en la pantalla (arriba izq, arriba der,
 * abajo der, abajo izq), con su giro, su perspectiva y el zoom de la vista.
 * [vw] x [vh] es la vista en pixeles y [escala] de lienzo a vista.
 */
private fun esquinasEnPantalla(estado: EstadoEditor, escena: Escena, id: Long, t: Long, escala: Float, vw: Float, vh: Float): List<Offset>? {
    val p = estado.proyecto
    val original = Edicion.clip(p, id) ?: return null
    if (original.esAjuste) return null
    val c = original.propiedadesEn(t - (Edicion.inicio(p, id) ?: 0L))
    val ini = Edicion.inicio(p, id) ?: return null
    val local = t - ini
    if (local < 0 || local >= c.duracionUs) return null
    val en = escena.encuadreEn(c, local)
    val m = escena.marco(c, en)
    val r = Math.toRadians(m.rotacion.toDouble())
    val co = kotlin.math.cos(r).toFloat()
    val si = kotlin.math.sin(r).toFloat()
    val z = estado.zoomVista
    return Perspectiva.esquinas(en.inclinacionX, en.inclinacionY, m.ancho, m.alto).map { (dx, dy) ->
        val px = (m.cx + dx * co - dy * si) * escala
        val py = (m.cy + dx * si + dy * co) * escala
        val (sx, sy) = z.aPantalla(px, py, vw, vh)
        Offset(sx, sy)
    }
}

/**
 * El marco del bloque elegido: una linea fina y escuadras en las esquinas,
 * que crecen con el bloque pero nunca lo tapan (en uno chico son apenas
 * una marca); se cogen para cambiar el tamano. Lleva una sombra fina para
 * verse sobre fondo blanco.
 */
private fun DrawScope.marcoElegido(esq: List<Offset>) {
    val linea = 1.dp.toPx()
    val trazo = 2.5.dp.toPx()
    val sombra = Color.Black.copy(alpha = 0.45f)
    val lado = minOf((esq[1] - esq[0]).getDistance(), (esq[3] - esq[0]).getDistance())
    val brazo = minOf(14.dp.toPx(), lado * 0.3f).coerceAtLeast(3.dp.toPx())
    for ((color, g) in listOf(sombra to linea + 1.5.dp.toPx(), Color.White.copy(alpha = 0.9f) to linea)) {
        for (i in 0 until 4) drawLine(color, esq[i], esq[(i + 1) % 4], g)
    }
    for (i in 0 until 4) {
        val e = esq[i]
        val brazos = listOf(esq[(i + 1) % 4], esq[(i + 3) % 4]).map { o ->
            val d = o - e
            val l = d.getDistance().coerceAtLeast(1e-3f)
            e + d * (brazo / l)
        }
        for ((color, g) in listOf(sombra to trazo + 1.5.dp.toPx(), Color.White to trazo)) {
            for (b in brazos) drawLine(color, e, b, g, cap = StrokeCap.Round)
        }
    }
}

private fun visible(estado: EstadoEditor, id: Long, t: Long): Boolean {
    val c = Edicion.clip(estado.proyecto, id) ?: return false
    val ini = Edicion.inicio(estado.proyecto, id) ?: return false
    return t >= ini && t < ini + c.duracionUs
}

/** El bloque de mas arriba que se ve en ese punto del lienzo. */
private fun bloqueEn(estado: EstadoEditor, escena: Escena, x: Float, y: Float, t: Long): Long? {
    val p = estado.proyecto
    val margen = 12f
    for (capa in Linea.capasEn(p.capas, t).asReversed()) {
        if (!capa.clip.esAjuste && dentro(escena, capa.clip, t - capa.inicioUs, x, y, margen)) return capa.id
    }
    val en = Linea.principalEn(p.principal, Linea.inicios(p.principal), t) ?: return null
    val c = en.segundo ?: en.primero
    val local = if (en.segundo != null) en.localSegundo else en.localPrimero
    return if (dentro(escena, c, local, x, y, 0f)) c.id else null
}

private fun dentro(escena: Escena, c: Clip, local: Long, x: Float, y: Float, margen: Float): Boolean =
    escena.marco(c.propiedadesEn(local), escena.encuadreEn(c, local)).contiene(x, y, margen)
