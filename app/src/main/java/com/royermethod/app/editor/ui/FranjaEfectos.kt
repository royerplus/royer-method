package com.royermethod.app.editor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.royermethod.app.editor.modelo.Clip
import com.royermethod.app.editor.modelo.EfectoVideo
import com.royermethod.app.editor.modelo.TiempoPreciso
import com.royermethod.app.ui.components.esquinas
import com.royermethod.app.ui.theme.Estilo
import com.royermethod.app.ui.theme.Ink
import kotlin.math.abs

/**
 * Alto de la zona de carriles y de la regla de debajo: la franja es fija y
 * tiene que ocupar poco. Con un solo carril basta menos.
 */
private val ALTO_CARRILES = 40.dp
private val ALTO_UN_CARRIL = 32.dp
private val ALTO_REGLA = 14.dp
/** Hueco a cada lado para poder agarrar el borde de un efecto que empieza en 0 o acaba al final. */
private val MARGEN = 8.dp
/** Lo que se puede errar el pulgar al agarrar un borde. */
private val ASA = 14.dp

/**
 * La franja de efectos aplicados: una mini linea de tiempo del bloque
 * (0..duracion) con un carril por solape y cada efecto como una barra con
 * su nombre (dentro si cabe, si no a su derecha); el elegido, resaltado y
 * con asas en los bordes; los de su mismo grupo, con borde.
 *
 * Gestos (todos sobre la franja, pensados para el pulgar):
 *  - tocar una barra la elige ([onTocar] con su id); tocar vacio, null;
 *  - arrastrar una barra la elige ([onAgarrar]) y la mueve desde el centro
 *    (conserva lo que dura y su reloj) o la recorta desde un borde, con el
 *    iman de [AjustesEditor.ajustarFotogramas]; manda la barra que esta
 *    justo debajo del dedo y, si no hay ninguna, la elegida aunque el dedo
 *    caiga en el carril de al lado. Todo el arrastre es un paso de
 *    deshacer, calculado siempre desde el efecto tal como se agarro;
 *  - arrastrar en vacio, o tocar o arrastrar en la regla de abajo, lleva el
 *    cabezal por el bloque (en pausa).
 *
 * El cabezal se lee al dibujar, no al componer: mientras suena solo se
 * repinta la capa del cabezal. [vista] es el bloque con ids ([conIdsEfectos]).
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
    // Mientras se arrastra, cada barra se queda en su carril
    var fijos by remember { mutableStateOf<Map<Long, Int>?>(null) }
    val carriles = remember(efectos, fijos) { TramosEfecto.carriles(efectos, fijos) }
    val n = TramosEfecto.visibles(carriles)
    val densidad = LocalDensity.current
    val medidor = rememberTextMeasurer()
    val zona = if (n == 1) ALTO_UN_CARRIL else ALTO_CARRILES

    BoxWithConstraints(modifier.fillMaxWidth().height(zona + ALTO_REGLA)) {
        val anchoPx = constraints.maxWidth.toFloat()
        val margen = with(densidad) { MARGEN.toPx() }
        val altoCarriles = with(densidad) { zona.toPx() }
        val altoCarril = altoCarriles / n
        val barras = remember(efectos, dur, anchoPx, margen) {
            efectos.mapIndexed { i, e ->
                TramosEfecto.Barra(
                    e.id,
                    TramosEfecto.x(e.inicioUs, dur, anchoPx, margen),
                    TramosEfecto.x(e.finUs, dur, anchoPx, margen),
                    TramosEfecto.carrilVisible(carriles[i])
                )
            }
        }
        // Lo que necesitan los gestos, siempre al dia sin reiniciarlos
        val datos by rememberUpdatedState(
            DatosFranja(barras, efectos, carriles, elegido, dur, inicioBloque, anchoPx, margen, altoCarriles, altoCarril,
                onTocar, onAgarrar, { fijos = it })
        )

        // Barras y nombres: solo cambian con los efectos o la eleccion
        Canvas(Modifier.fillMaxWidth().height(zona)) {
            barrasEfectos(efectos, barras, elegido, altoCarril, medidor)
        }
        // Regla: principio y final del bloque
        Text("0 s", style = EstiloRegla, color = Ink.White30,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 2.dp))
        Text(TiempoPreciso.texto(dur), style = EstiloRegla, color = Ink.White30,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 2.dp))

        // Asas del elegido y cabezal, encima. El cabezal se lee aqui, al dibujar
        val sel = barras.firstOrNull { it.id == elegido }
        Canvas(
            Modifier.fillMaxWidth().height(zona + ALTO_REGLA)
                .pointerInput(Unit) { gestos(estado) { datos } }
        ) {
            if (sel != null) asas(sel, altoCarril)
            val local = estado.tiempo - inicioBloque
            if (dur > 0 && local in 0..dur) {
                val x = TramosEfecto.x(local, dur, size.width, margen)
                drawLine(Ink.White, Offset(x, 0f), Offset(x, altoCarriles), 1.5.dp.toPx())
                drawCircle(Ink.White, 3.dp.toPx(), Offset(x, 0f))
            }
        }
    }
}

private val EstiloRegla = TextStyle(fontSize = 10.sp, lineHeight = 12.sp, letterSpacing = 0.3.sp)

/** Lo que leen los gestos de la franja: se toma una foto al apoyar el dedo. */
private class DatosFranja(
    val barras: List<TramosEfecto.Barra>,
    val efectos: List<EfectoVideo>,
    val carriles: IntArray,
    val elegido: Long?,
    val dur: Long,
    val inicioBloque: Long,
    val anchoPx: Float,
    val margen: Float,
    val altoCarriles: Float,
    val altoCarril: Float,
    val onTocar: (Long?) -> Unit,
    val onAgarrar: (Long) -> Unit,
    val fijar: (Map<Long, Int>?) -> Unit
)

/** El fondo, las barras (la elegida al final, encima) y sus nombres. */
private fun DrawScope.barrasEfectos(
    efectos: List<EfectoVideo>, barras: List<TramosEfecto.Barra>, elegido: Long?,
    altoCarril: Float, medidor: TextMeasurer
) {
    drawRoundRect(Ink.White04, Offset.Zero, size, CornerRadius(8.dp.toPx()))
    val grupo = efectos.firstOrNull { it.id == elegido }?.grupo.orEmpty()
    val hueco = 1.5.dp.toPx()
    val minimo = 4.dp.toPx()
    val alto = (altoCarril - 2 * hueco).coerceAtLeast(1f)
    val radio = CornerRadius(4.dp.toPx())
    // El nombre solo si el carril deja leerlo
    val letra = if (alto >= 14.dp.toPx()) 11f else 9f
    val conNombre = alto >= 9.dp.toPx()
    val orden = efectos.indices.sortedBy { if (efectos[it].id == elegido) 1 else 0 }
    for (i in orden) {
        val e = efectos[i]
        val b = barras[i]
        val esElegida = e.id == elegido
        val ancho = (b.x1 - b.x0).coerceAtLeast(minimo)
        val x = if (b.x1 - b.x0 < minimo) (b.x0 + b.x1) / 2 - minimo / 2 else b.x0
        val y = b.carril * altoCarril + hueco
        val fondo = when {
            esElegida -> Ink.White
            !e.activo -> Ink.White08
            else -> Ink.White15
        }
        drawRoundRect(fondo, Offset(x, y), Size(ancho, alto), radio)
        if (!esElegida && grupo.isNotEmpty() && e.grupo == grupo) {
            drawRoundRect(Ink.White50, Offset(x, y), Size(ancho, alto), radio, style = Stroke(1.dp.toPx()))
        }
        if (!conNombre) continue
        // Dentro deja sitio a las asas de la elegida
        val relleno = (if (esElegida) 8.dp else 4.dp).toPx()
        val estilo = TextStyle(fontSize = (letra * Estilo.letra).sp, letterSpacing = 0.2.sp)
        val nombre = TramosEfecto.nombreCorto(e)
        val entero = medidor.measure(nombre, estilo, maxLines = 1, softWrap = false)
        val libre = TramosEfecto.libreDerecha(barras, i, size.width) - 4.dp.toPx()
        val donde = TramosEfecto.rotulo(entero.size.width.toFloat(), ancho, relleno, libre, 18.dp.toPx())
        val cy = y + alto / 2 - entero.size.height / 2
        when (donde) {
            TramosEfecto.Rotulo.DENTRO ->
                drawText(entero, if (esElegida) Ink.Black else if (e.activo) Ink.White90 else Ink.White30, Offset(x + relleno, cy))
            TramosEfecto.Rotulo.FUERA ->
                drawText(entero, if (esElegida) Ink.White else if (e.activo) Ink.White50 else Ink.White30, Offset(b.x1 + 4.dp.toPx(), cy))
            TramosEfecto.Rotulo.CORTADO -> {
                val corto = medidor.measure(
                    nombre, estilo, overflow = TextOverflow.Ellipsis, maxLines = 1, softWrap = false,
                    constraints = Constraints(maxWidth = (ancho - 2 * relleno).toInt().coerceAtLeast(1))
                )
                drawText(corto, if (esElegida) Ink.Black else if (e.activo) Ink.White90 else Ink.White30, Offset(x + relleno, cy))
            }
            TramosEfecto.Rotulo.NADA -> Unit
        }
    }
}

/** Las dos asas de la elegida: dentro de la barra si cabe; si es muy estrecha, por fuera. */
private fun DrawScope.asas(sel: TramosEfecto.Barra, altoCarril: Float) {
    val y0 = sel.carril * altoCarril
    val h = altoCarril
    val asa = Size(2.dp.toPx(), (h * 0.5f).coerceAtLeast(3.dp.toPx()).coerceAtMost(h))
    val ya = y0 + (h - asa.height) / 2
    val dentro = sel.x1 - sel.x0 >= 12.dp.toPx()
    val tinta = if (dentro) Ink.Black else Ink.White
    val d = 2.5.dp.toPx()
    val xa = if (dentro) sel.x0 + d else sel.x0 - d - asa.width
    val xb = if (dentro) sel.x1 - d - asa.width else sel.x1 + d
    drawRoundRect(tinta, Offset(xa, ya), asa, CornerRadius(asa.width))
    drawRoundRect(tinta, Offset(xb, ya), asa, CornerRadius(asa.width))
}

/**
 * Tocar, arrastrar una barra o arrastrar en vacio. Todo se calcula desde lo
 * que habia al apoyar el dedo; el arrastre de una barra es un [ArrastreTramo]
 * (un paso de deshacer).
 */
private suspend fun PointerInputScope.gestos(estado: EstadoEditor, datos: () -> DatosFranja) {
    awaitEachGesture {
        val abajo = awaitFirstDown(requireUnconsumed = false)
        val d = datos()
        val asa = ASA.toPx()
        val p0 = abajo.position
        // La regla de abajo es solo para el cabezal
        val enRegla = p0.y >= d.altoCarriles
        val tocada = if (enRegla) null else TramosEfecto.objetivo(d.barras, p0.x, p0.y, d.altoCarril, asa, d.elegido)
        val agarrada = if (enRegla) null else TramosEfecto.agarrada(d.barras, p0.x, p0.y, d.altoCarril, asa, d.elegido)
        val base = agarrada?.let { (b, _) -> d.efectos.firstOrNull { it.id == b.id } }
        val umbral = viewConfiguration.touchSlop
        var arrastrando = false
        var tramo: ArrastreTramo? = null
        fun cabezalEn(x: Float) {
            val t = TramosEfecto.tiempo(x, d.dur, d.anchoPx, d.margen)
            val pegado = TramosEfecto.pegarCabezal(t, AjustesEditor.ajustarFotogramas, d.inicioBloque)
            estado.pausarEn(d.inicioBloque + pegado.coerceIn(0, (d.dur - 1).coerceAtLeast(0)))
        }
        try {
            while (true) {
                val ev = awaitPointerEvent()
                val c = ev.changes.firstOrNull { it.id == abajo.id } ?: break
                if (!c.pressed) {
                    if (!arrastrando) {
                        if (enRegla) cabezalEn(p0.x) else d.onTocar(tocada?.id)
                    }
                    break
                }
                val dx = c.position.x - p0.x
                if (!arrastrando && abs(dx) > umbral) {
                    arrastrando = true
                    if (base != null && agarrada != null) {
                        d.fijar(d.efectos.indices.associate { d.efectos[it].id to d.carriles[it] })
                        d.onAgarrar(base.id)
                        tramo = ArrastreTramo(estado, base, agarrada.second, d.dur, d.inicioBloque).also { it.empezar() }
                    }
                }
                if (!arrastrando) continue
                c.consume()
                val t = tramo
                if (t != null) t.seguir(TramosEfecto.delta(dx, d.dur, d.anchoPx, d.margen), AjustesEditor.ajustarFotogramas)
                else cabezalEn(c.position.x)
            }
        } finally {
            if (tramo != null) d.fijar(null)
            tramo?.terminar()
        }
    }
}
