package com.royermethod.app.editor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.royermethod.app.editor.modelo.Clip
import com.royermethod.app.editor.modelo.Edicion
import com.royermethod.app.editor.modelo.Encuadre
import com.royermethod.app.editor.modelo.Escena
import com.royermethod.app.editor.modelo.Linea
import com.royermethod.app.editor.modelo.Medidor
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
                            estado.reproduciendo = false
                            val t = estado.tiempo
                            val enLienzo = Offset(abajo.position.x / escala, abajo.position.y / escala)
                            val tocado = bloqueEn(estado, escenaActual, enLienzo.x, enLienzo.y, t)

                            // El bloque que se mueve: el elegido si se toco dentro de el
                            // (o si se usan dos dedos), si no, el que hay debajo.
                            val elegido = (estado.seleccion as? Seleccion.Bloque)?.id
                            var objetivo: Long? = null
                            var movido = false
                            var e0: Encuadre? = null
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
                                            elegido != null && (dedos >= 2 || tocado == elegido) && visible(estado, elegido, t) -> elegido
                                            tocado != null -> tocado
                                            else -> null
                                        }
                                        val id = objetivo
                                        if (id != null) {
                                            if (id != elegido) estado.elegir(Seleccion.Bloque(id))
                                            val c = Edicion.clip(base, id)!!
                                            val ini = Edicion.inicio(base, id) ?: 0
                                            e0 = c.encuadreEn((t - ini).coerceIn(0, c.duracionUs))
                                            estado.empezarGesto()
                                        }
                                    }
                                    val id = objetivo
                                    val inicial = e0
                                    if (movido && id != null && inicial != null) {
                                        pan += ev.calculatePan()
                                        zoom *= ev.calculateZoom()
                                        giro += ev.calculateRotation()
                                        var x = inicial.x + pan.x / vw
                                        var y = inicial.y + pan.y / vh
                                        var r = inicial.rotacion + giro
                                        guiaX = abs(x) < 0.012f
                                        guiaY = abs(y) < 0.012f
                                        if (guiaX) x = 0f
                                        if (guiaY) y = 0f
                                        val recto = (r / 90f).roundToInt() * 90f
                                        if (abs(r - recto) < 4f) r = recto
                                        val s = (inicial.escala * zoom).coerceIn(0.05f, 20f)
                                        estado.ajustar { q ->
                                            Edicion.ajustarEncuadre(q, id, t) { it.copy(x = x, y = y, rotacion = r, escala = s) }
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
                // El borde de lo que se exporta, siempre a la vista: lo que se
                // salga de esta linea no va a salir en el video.
                val grosor = 1.5.dp.toPx()
                val esquina = radio(6.dp).toPx()
                drawRoundRect(
                    Color.White, Offset(grosor / 2, grosor / 2), Size(size.width - grosor, size.height - grosor),
                    CornerRadius(esquina, esquina), style = Stroke(grosor)
                )
                if (guiaX) drawLine(Ink.White70, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
                if (guiaY) drawLine(Ink.White70, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
                val id = (estado.seleccion as? Seleccion.Bloque)?.id ?: return@Canvas
                val c = Edicion.clip(pr, id) ?: return@Canvas
                val ini = Edicion.inicio(pr, id) ?: return@Canvas
                val local = estado.tiempo - ini
                if (local < 0 || local >= c.duracionUs) return@Canvas
                val m = escena.marco(c, escena.encuadreEn(c, local))
                val cx = m.cx * escala
                val cy = m.cy * escala
                val w = m.ancho * escala
                val h = m.alto * escala
                rotate(m.rotacion, Offset(cx, cy)) {
                    drawRect(Ink.Glow35, Offset(cx - w / 2, cy - h / 2), Size(w, h), style = Stroke(4.dp.toPx()))
                    drawRect(Ink.White, Offset(cx - w / 2, cy - h / 2), Size(w, h), style = Stroke(1.5.dp.toPx()))
                    val r = 4.dp.toPx()
                    for (sx in listOf(-1, 1)) for (sy in listOf(-1, 1)) {
                        val o = Offset(cx + sx * w / 2, cy + sy * h / 2)
                        drawCircle(Ink.Black, r + 1.5.dp.toPx(), o)
                        drawCircle(Ink.White, r, o)
                    }
                }
            }
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
        if (dentro(escena, capa.clip, t - capa.inicioUs, x, y, margen)) return capa.id
    }
    val en = Linea.principalEn(p.principal, Linea.inicios(p.principal), t) ?: return null
    val c = en.segundo ?: en.primero
    val local = if (en.segundo != null) en.localSegundo else en.localPrimero
    return if (dentro(escena, c, local, x, y, 0f)) c.id else null
}

private fun dentro(escena: Escena, c: Clip, local: Long, x: Float, y: Float, margen: Float): Boolean =
    escena.marco(c, escena.encuadreEn(c, local)).contiene(x, y, margen)
