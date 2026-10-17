package com.royermethod.app.editor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.royermethod.app.editor.modelo.Clip
import com.royermethod.app.editor.modelo.EfectoVideo
import com.royermethod.app.editor.modelo.TiempoPreciso
import com.royermethod.app.ui.components.esquinas
import com.royermethod.app.ui.theme.Ink
import kotlin.math.abs
import kotlin.math.roundToInt

/** Alto de la zona de carriles y de la escala de debajo: la franja es fija y tiene que ocupar poco. */
private val ALTO_CARRILES = 40.dp
private val ALTO_ESCALA = 14.dp
/** Hueco a cada lado para poder agarrar el borde de un efecto que empieza en 0 o acaba al final. */
private val MARGEN = 8.dp
/** Lo que se puede errar el pulgar al agarrar un borde. */
private val ASA = 14.dp

/**
 * La franja de efectos aplicados: una mini linea de tiempo del bloque
 * (0..duracion) con un carril por solape y cada efecto como una barra con
 * su nombre; el elegido, resaltado y con asas en los bordes.
 *
 * Gestos (todos sobre la franja, pensados para el pulgar):
 *  - tocar una barra la elige ([onTocar] con su id); tocar vacio, null;
 *  - arrastrar una barra la elige ([onAgarrar]) y la mueve desde el centro
 *    (conserva lo que dura y su reloj) o la recorta desde un borde, con el
 *    iman de [AjustesEditor.ajustarFotogramas]; todo el arrastre es un paso
 *    de deshacer, calculado siempre desde el efecto tal como se agarro;
 *  - arrastrar en vacio lleva el cabezal por el bloque (en pausa).
 *
 * El cabezal se lee al dibujar, no al componer: mientras suena solo se
 * repinta esta franja. [vista] es el bloque con ids ([conIdsEfectos]).
 */
@Composable
internal fun FranjaEfectos(
    estado: EstadoEditor,
    vista: Clip,
    inicioBloque: Long,
    elegido: Long?,
    onTocar: (Long?) -> Unit,
    onAgarrar: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val efectos = vista.efectos
    val dur = vista.duracionUs
    if (efectos.isEmpty()) {
        Box(
            modifier.fillMaxWidth().height(ALTO_CARRILES).clip(esquinas(8.dp)).background(Ink.White04),
            contentAlignment = Alignment.Center
        ) {
            Text("Sin efectos. Toca + Agregar", style = MaterialTheme.typography.bodyMedium, color = Ink.White50)
        }
        return
    }
    val carriles = remember(efectos) { TramosEfecto.carriles(efectos) }
    val n = TramosEfecto.visibles(carriles)
    val grupoElegido = efectos.firstOrNull { it.id == elegido }?.grupo.orEmpty()
    val densidad = LocalDensity.current

    BoxWithConstraints(modifier.fillMaxWidth().height(ALTO_CARRILES + ALTO_ESCALA)) {
        val anchoPx = constraints.maxWidth.toFloat()
        val margen = with(densidad) { MARGEN.toPx() }
        val altoCarril = with(densidad) { ALTO_CARRILES.toPx() } / n
        val barras = efectos.mapIndexed { i, e ->
            TramosEfecto.Barra(
                e.id,
                TramosEfecto.x(e.inicioUs, dur, anchoPx, margen),
                TramosEfecto.x(e.finUs, dur, anchoPx, margen),
                TramosEfecto.carrilVisible(carriles[i])
            )
        }
        // Lo que necesitan los gestos, siempre al dia sin reiniciarlos
        val datos by rememberUpdatedState(DatosFranja(barras, efectos, elegido, dur, inicioBloque, anchoPx, margen, altoCarril, onTocar, onAgarrar))

        Box(Modifier.fillMaxWidth().height(ALTO_CARRILES).clip(esquinas(8.dp)).background(Ink.White04))
        // La elegida al final, encima de las demas
        val orden = efectos.indices.sortedBy { if (efectos[it].id == elegido) 1 else 0 }
        for (i in orden) {
            BarraEfecto(efectos[i], barras[i], altoCarril, efectos[i].id == elegido,
                grupoElegido.isNotEmpty() && efectos[i].grupo == grupoElegido)
        }
        // Escala: principio y final del bloque
        Text("0 s", style = EstiloEscala, color = Ink.White30,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 2.dp))
        Text(TiempoPreciso.texto(dur), style = EstiloEscala, color = Ink.White30,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 2.dp))

        // Asas del elegido y cabezal. El cabezal se lee aqui, al dibujar
        val sel = barras.firstOrNull { it.id == elegido }
        Canvas(
            Modifier.fillMaxWidth().height(ALTO_CARRILES + ALTO_ESCALA)
                .pointerInput(Unit) { gestos(estado) { datos } }
        ) {
            val alto = ALTO_CARRILES.toPx()
            if (sel != null) {
                val y0 = sel.carril * altoCarril
                val h = altoCarril
                val asa = Size(2.dp.toPx(), (h * 0.5f).coerceAtLeast(3.dp.toPx()).coerceAtMost(h))
                val ya = y0 + (h - asa.height) / 2
                val ancho = sel.x1 - sel.x0
                // Dentro de la barra si cabe; si es muy estrecha, por fuera
                val dentro = ancho >= 12.dp.toPx()
                val tinta = if (dentro) Ink.Black else Ink.White
                val d = 2.5.dp.toPx()
                val xa = if (dentro) sel.x0 + d else sel.x0 - d - asa.width
                val xb = if (dentro) sel.x1 - d - asa.width else sel.x1 + d
                drawRoundRect(tinta, Offset(xa, ya), asa, CornerRadius(asa.width))
                drawRoundRect(tinta, Offset(xb, ya), asa, CornerRadius(asa.width))
            }
            val local = estado.tiempo - inicioBloque
            if (dur > 0 && local in 0..dur) {
                val x = TramosEfecto.x(local, dur, size.width, margen)
                drawLine(Ink.White, Offset(x, 0f), Offset(x, alto), 1.5.dp.toPx())
                drawCircle(Ink.White, 3.dp.toPx(), Offset(x, 0f))
            }
        }
    }
}

private val EstiloEscala = androidx.compose.ui.text.TextStyle(fontSize = 10.sp, lineHeight = 12.sp, letterSpacing = 0.3.sp)

/** Lo que leen los gestos de la franja: se toma una foto al apoyar el dedo. */
private class DatosFranja(
    val barras: List<TramosEfecto.Barra>,
    val efectos: List<EfectoVideo>,
    val elegido: Long?,
    val dur: Long,
    val inicioBloque: Long,
    val anchoPx: Float,
    val margen: Float,
    val altoCarril: Float,
    val onTocar: (Long?) -> Unit,
    val onAgarrar: (Long) -> Unit
)

@Composable
private fun BarraEfecto(e: EfectoVideo, b: TramosEfecto.Barra, altoCarril: Float, elegida: Boolean, hermana: Boolean) {
    val densidad = LocalDensity.current
    val hueco = with(densidad) { 1.5.dp.toPx() }
    val minimo = with(densidad) { 4.dp.toPx() }
    val ancho = (b.x1 - b.x0).coerceAtLeast(minimo)
    val alto = (altoCarril - 2 * hueco).coerceAtLeast(1f)
    val x = if (b.x1 - b.x0 < minimo) (b.x0 + b.x1) / 2 - minimo / 2 else b.x0
    val fondo = when {
        elegida -> Ink.White
        !e.activo -> Ink.White08
        else -> Ink.White15
    }
    val tinta = when {
        elegida -> Ink.Black
        !e.activo -> Ink.White30
        else -> Ink.White90
    }
    val forma = RoundedCornerShape(4.dp)
    Box(
        Modifier
            .offset { IntOffset(x.roundToInt(), (b.carril * altoCarril + hueco).roundToInt()) }
            .size(with(densidad) { ancho.toDp() }, with(densidad) { alto.toDp() })
            .clip(forma)
            .background(fondo)
            .then(if (hermana && !elegida) Modifier.border(1.dp, Ink.White50, forma) else Modifier),
        contentAlignment = Alignment.CenterStart
    ) {
        // El nombre solo si cabe: en una barra muy corta o un carril bajo se leeria a trozos
        if (ancho >= with(densidad) { 22.dp.toPx() } && alto >= with(densidad) { 9.dp.toPx() }) {
            Text(
                TramosEfecto.nombreCorto(e),
                style = androidx.compose.ui.text.TextStyle(fontSize = if (alto >= with(densidad) { 14.dp.toPx() }) 11.sp else 9.sp,
                    lineHeight = 12.sp, letterSpacing = 0.2.sp),
                color = tinta,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = if (elegida) 8.dp else 4.dp)
            )
        }
    }
}

/**
 * Tocar, arrastrar una barra o arrastrar en vacio. Todo se calcula desde lo
 * que habia al apoyar el dedo; el arrastre de una barra va entre
 * empezarGesto y terminarGesto para que deshacer lo quite de una vez.
 */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.gestos(
    estado: EstadoEditor,
    datos: () -> DatosFranja
) {
    awaitEachGesture {
        val abajo = awaitFirstDown(requireUnconsumed = false)
        val d = datos()
        val asa = ASA.toPx()
        val p0 = abajo.position
        val tocada = TramosEfecto.tocada(d.barras, p0.x, p0.y, d.altoCarril, asa, d.elegido)
        val base = tocada?.let { b -> d.efectos.firstOrNull { it.id == b.id } }
        val agarre = tocada?.let { TramosEfecto.agarre(p0.x, it.x0, it.x1, asa) }
        val umbral = viewConfiguration.touchSlop
        var arrastrando = false
        var gesto = false
        try {
            while (true) {
                val ev = awaitPointerEvent()
                val c = ev.changes.firstOrNull { it.id == abajo.id } ?: break
                if (!c.pressed) {
                    if (!arrastrando) d.onTocar(tocada?.id)
                    break
                }
                val dx = c.position.x - p0.x
                if (!arrastrando && abs(dx) > umbral) {
                    arrastrando = true
                    if (base != null && agarre != null) {
                        d.onAgarrar(base.id)
                        estado.empezarGesto()
                        gesto = true
                    }
                }
                if (!arrastrando) continue
                c.consume()
                if (gesto && base != null && agarre != null) {
                    val nuevo = TramosEfecto.arrastrar(
                        base, agarre, TramosEfecto.delta(dx, d.dur, d.anchoPx, d.margen), d.dur,
                        AjustesEditor.ajustarFotogramas, d.inicioBloque
                    )
                    estado.cambiarClip(arrastre = true) { it.conEfecto(base.id) { nuevo } }
                } else {
                    // En vacio: el cabezal recorre el bloque, como la vista previa del bloque de antes
                    val t = TramosEfecto.tiempo(c.position.x, d.dur, d.anchoPx, d.margen)
                    val pegado = TramosEfecto.pegarCabezal(t, AjustesEditor.ajustarFotogramas, d.inicioBloque)
                    estado.pausarEn(d.inicioBloque + pegado.coerceIn(0, (d.dur - 1).coerceAtLeast(0)))
                }
            }
        } finally {
            if (gesto) estado.terminarGesto()
        }
    }
}
