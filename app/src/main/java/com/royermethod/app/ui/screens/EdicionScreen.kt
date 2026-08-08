package com.royermethod.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.royermethod.app.media.Medio
import com.royermethod.app.media.leerMedio
import com.royermethod.app.media.tiempo
import com.royermethod.app.ui.components.LienzoVideo
import com.royermethod.app.ui.components.recordarVideo
import com.royermethod.app.ui.theme.Ink
import com.royermethod.app.ui.theme.Motion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Un clip en la linea de tiempo.
 *
 * Guarda el archivo y que trozo de el se usa. Recortar no toca el archivo: se
 * mueven [desdeMs] y [hastaMs], y el original se queda entero por si se quiere
 * volver atras.
 */
data class Clip(
    val medio: Medio,
    val desdeMs: Int = 0,
    val hastaMs: Int = medio.duracionMs
) {
    val duracionMs: Int get() = (hastaMs - desdeMs).coerceAtLeast(0)
}

private data class Herramienta(
    val nombre: String,
    val glifo: DrawScope.() -> Unit,
    val accion: () -> Unit
)

/**
 * Editor.
 *
 * La colocacion es la que la gente ya tiene aprendida de cualquier editor de
 * movil: imagen arriba, linea de tiempo en medio con el cabezal fijo en el
 * centro, y herramientas abajo. Lo demas es el lenguaje de esta app.
 *
 * La fila de abajo cambia segun haya un clip elegido o no, como en CapCut: sin
 * seleccion salen las cosas del proyecto, con un clip elegido salen las de ese
 * clip y una flecha para volver.
 */
@Composable
fun EdicionScreen(onVolver: () -> Unit) {
    val context = LocalContext.current
    val alcance = rememberCoroutineScope()

    val clips = remember { mutableStateListOf<Clip>() }
    // Deshacer guarda copias enteras de la lista de clips. Son unos pocos
    // objetos ligeros (el video no se copia, solo la referencia), asi que sale
    // mucho mas barato que llevar la cuenta de que cambio en cada accion.
    val historia = remember { mutableStateListOf<List<Clip>>() }
    val rehacer = remember { mutableStateListOf<List<Clip>>() }
    var seleccionado by remember { mutableIntStateOf(-1) }
    var posicionMs by remember { mutableFloatStateOf(0f) }
    var escala by remember { mutableFloatStateOf(60f) }
    var enMarcha by remember { mutableStateOf(false) }
    var aviso by remember { mutableStateOf<String?>(null) }

    val total = clips.sumOf { it.duracionMs }

    /** Guarda el estado antes de tocarlo. Todo lo que cambia pasa por aqui. */
    fun apuntar() {
        historia.add(clips.toList())
        if (historia.size > 30) historia.removeAt(0)
        rehacer.clear()
    }

    val selector = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            alcance.launch {
                val m = withContext(Dispatchers.IO) { leerMedio(context, uri, true) }
                if (m != null) {
                    apuntar()
                    clips.add(Clip(m))
                }
            }
        }
    }

    fun anadir() = selector.launch(
        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
    )

    /** Parte el clip elegido justo por donde esta el cabezal. */
    fun dividir() {
        val i = seleccionado
        if (i !in clips.indices) return
        var inicio = 0
        for (n in 0 until i) inicio += clips[n].duracionMs
        val dentro = posicionMs.toInt() - inicio
        val c = clips[i]
        if (dentro < 300 || dentro > c.duracionMs - 300) {
            aviso = "Mueve el cabezal a donde quieras cortar"
            return
        }
        apuntar()
        val corte = c.desdeMs + dentro
        clips[i] = c.copy(hastaMs = corte)
        clips.add(i + 1, c.copy(desdeMs = corte))
    }

    fun borrar() {
        val i = seleccionado
        if (i in clips.indices) {
            apuntar()
            clips.removeAt(i)
            seleccionado = -1
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Ink.Black)
    ) {
        BarraSuperior(
            enMarcha = enMarcha,
            puedeDeshacer = historia.isNotEmpty(),
            puedeRehacer = rehacer.isNotEmpty(),
            onVolver = onVolver,
            onPlay = { enMarcha = !enMarcha },
            onDeshacer = {
                if (historia.isNotEmpty()) {
                    rehacer.add(clips.toList())
                    val previo = historia.removeAt(historia.lastIndex)
                    clips.clear(); clips.addAll(previo)
                    seleccionado = -1
                }
            },
            onRehacer = {
                if (rehacer.isNotEmpty()) {
                    historia.add(clips.toList())
                    val siguiente = rehacer.removeAt(rehacer.lastIndex)
                    clips.clear(); clips.addAll(siguiente)
                    seleccionado = -1
                }
            },
            onExportar = { aviso = "La exportacion todavia no esta hecha" }
        )

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            val actual = clipEn(clips, posicionMs.toInt())
            if (actual == null) {
                Text(
                    text = "ANADE UN CLIP PARA EMPEZAR",
                    style = MaterialTheme.typography.labelSmall,
                    color = Ink.White30
                )
            } else {
                val estado = recordarVideo(actual.medio.uri)
                LienzoVideo(
                    estado = estado,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(14.dp))
                )
            }
        }

        Regla(posicionMs = posicionMs, totalMs = total, escala = escala)

        LineaDeTiempo(
            clips = clips,
            posicionMs = posicionMs,
            escala = escala,
            seleccionado = seleccionado,
            onPosicion = { posicionMs = it.coerceIn(0f, total.toFloat()) },
            onSeleccionar = { seleccionado = if (seleccionado == it) -1 else it },
            onAnadir = { anadir() }
        )

        Spacer(Modifier.height(10.dp))

        PistaVacia(texto = "Agregar musica") {
            aviso = "La pista de musica todavia no esta hecha"
        }

        Spacer(Modifier.weight(0.001f))

        aviso?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = Ink.White50,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )
        }

        BarraHerramientas(
            hayClip = seleccionado >= 0,
            onDeseleccionar = { seleccionado = -1 },
            acciones = mapOf(
                "Dividir" to { dividir() },
                "Eliminar" to { borrar() },
                "Anadir" to { anadir() }
            ),
            onPendiente = { aviso = "$it todavia no esta hecho" }
        )

        Spacer(Modifier.height(14.dp))
    }
}

/** Que clip cae en ese instante de la linea de tiempo. */
private fun clipEn(clips: List<Clip>, ms: Int): Clip? {
    var acumulado = 0
    for (c in clips) {
        if (ms < acumulado + c.duracionMs) return c
        acumulado += c.duracionMs
    }
    return clips.lastOrNull()
}

/**
 * Barra de arriba, con la colocacion de siempre: salir a la izquierda, el play
 * en el centro, y a la derecha lo que actua sobre el proyecto entero.
 */
@Composable
private fun BarraSuperior(
    enMarcha: Boolean,
    puedeDeshacer: Boolean,
    puedeRehacer: Boolean,
    onVolver: () -> Unit,
    onPlay: () -> Unit,
    onDeshacer: () -> Unit,
    onRehacer: () -> Unit,
    onExportar: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, top = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconoBarra(onClick = onVolver) { glifoAtras(this) }

        Spacer(Modifier.weight(1f))

        IconoBarra(onClick = onPlay, grande = true) {
            if (enMarcha) glifoPausa(this) else glifoPlay(this)
        }

        Spacer(Modifier.weight(1f))

        IconoBarra(onClick = onDeshacer, apagado = !puedeDeshacer) { glifoDeshacer(this) }
        Spacer(Modifier.width(2.dp))
        IconoBarra(onClick = onRehacer, apagado = !puedeRehacer) { glifoRehacer(this) }
        Spacer(Modifier.width(8.dp))

        Box(
            Modifier
                .height(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Ink.White)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onExportar
                )
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "EXPORTAR",
                style = MaterialTheme.typography.labelSmall,
                color = Ink.Black
            )
        }
    }
}

@Composable
private fun IconoBarra(
    onClick: () -> Unit,
    grande: Boolean = false,
    apagado: Boolean = false,
    glifo: DrawScope.() -> Unit
) {
    Box(
        Modifier
            .size(if (grande) 40.dp else 34.dp)
            .clip(RoundedCornerShape(11.dp))
            .alpha(if (apagado) 0.28f else 1f)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = !apagado,
                onClick = onClick
            )
            .drawBehind { glifo() }
    )
}

/**
 * La regla de tiempo.
 *
 * Marcas cada dos segundos con el numero, y un punto en los impares. Sin ella
 * la cinta es una mancha: no se sabe si un clip dura uno o diez segundos.
 */
@Composable
private fun Regla(posicionMs: Float, totalMs: Int, escala: Float) {
    val densidad = LocalDensity.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = tiempo(posicionMs.toInt()),
            style = MaterialTheme.typography.labelSmall,
            color = Ink.White
        )
        Text(
            text = "  /  ${tiempo(totalMs)}",
            style = MaterialTheme.typography.labelSmall,
            color = Ink.White30
        )
        Spacer(Modifier.width(14.dp))
        Box(
            Modifier
                .weight(1f)
                .height(18.dp)
                .clipToBounds()
                .drawBehind {
                    val pxPorSeg = with(densidad) { escala.dp.toPx() }
                    if (pxPorSeg <= 0f) return@drawBehind
                    val centro = size.width / 2
                    val desdeSeg = posicionMs / 1000f
                    var s = (desdeSeg - centro / pxPorSeg).toInt() - 1
                    while (true) {
                        val x = centro + (s - desdeSeg) * pxPorSeg
                        if (x > size.width) break
                        if (s >= 0 && x >= 0) {
                            drawCircle(
                                Ink.White,
                                alpha = if (s % 2 == 0) 0.45f else 0.18f,
                                radius = if (s % 2 == 0) 1.6.dp.toPx() else 1.dp.toPx(),
                                center = Offset(x, size.height / 2)
                            )
                        }
                        s++
                    }
                }
        )
    }
}

/**
 * La cinta.
 *
 * El cabezal se queda quieto en el centro y lo que se arrastra es la cinta.
 * Asi el punto exacto donde vas a cortar nunca queda debajo del dedo, que es
 * el problema de las lineas de tiempo donde se arrastra el cabezal.
 */
@Composable
private fun LineaDeTiempo(
    clips: List<Clip>,
    posicionMs: Float,
    escala: Float,
    seleccionado: Int,
    onPosicion: (Float) -> Unit,
    onSeleccionar: (Int) -> Unit,
    onAnadir: () -> Unit
) {
    val densidad = LocalDensity.current

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(86.dp)
            .clipToBounds()
            // El arrastre va en el contenedor, que ocupa todo el ancho y no se
            // mueve. Puesto en la cinta, que se desplaza y mide lo que midan
            // los clips, con un clip corto no habia casi nada que agarrar.
            .pointerInput(escala) {
                detectHorizontalDragGestures { cambio, desplazamiento ->
                    cambio.consume()
                    val dp = with(densidad) { desplazamiento.toDp().value }
                    onPosicion(posicionMs - dp / escala * 1000f)
                }
            }
    ) {
        val mitad = maxWidth / 2
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 12.dp)
                .offset(x = mitad - (posicionMs / 1000f * escala).dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            clips.forEachIndexed { i, c ->
                BloqueClip(
                    clip = c,
                    anchoDp = (c.duracionMs / 1000f * escala).dp,
                    activo = i == seleccionado,
                    onClick = { onSeleccionar(i) }
                )
            }
            BotonAnadir(onClick = onAnadir)
        }

        Box(
            Modifier
                .fillMaxHeight()
                .width(2.dp)
                .align(Alignment.Center)
                .background(Ink.White)
        )
    }
}

@Composable
private fun BloqueClip(
    clip: Clip,
    anchoDp: androidx.compose.ui.unit.Dp,
    activo: Boolean,
    onClick: () -> Unit
) {
    val borde by animateFloatAsState(
        targetValue = if (activo) 0.9f else 0.18f,
        animationSpec = tween(Motion.QUICK, easing = Motion.Smooth),
        label = "clipBorde"
    )
    Box(
        Modifier
            .width(anchoDp.coerceAtLeast(28.dp))
            .fillMaxHeight()
            .clip(RoundedCornerShape(6.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .drawBehind {
                drawRoundRect(
                    color = Ink.White, alpha = 0.10f,
                    cornerRadius = CornerRadius(6.dp.toPx())
                )
            },
        contentAlignment = Alignment.Center
    ) {
        clip.medio.miniatura?.let {
            androidx.compose.foundation.Image(
                bitmap = it,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRoundRect(
                        color = Ink.White, alpha = borde,
                        cornerRadius = CornerRadius(6.dp.toPx()),
                        style = Stroke(1.6.dp.toPx())
                    )
                }
        )
    }
}

@Composable
private fun BotonAnadir(onClick: () -> Unit) {
    Box(
        Modifier
            .width(48.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(6.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .drawBehind {
                drawRoundRect(
                    color = Ink.White, alpha = 0.06f,
                    cornerRadius = CornerRadius(6.dp.toPx())
                )
                val c = center
                val r = 8.dp.toPx()
                drawLine(
                    Ink.White, alpha = 0.7f,
                    start = Offset(c.x - r, c.y), end = Offset(c.x + r, c.y),
                    strokeWidth = 1.8.dp.toPx(), cap = StrokeCap.Round
                )
                drawLine(
                    Ink.White, alpha = 0.7f,
                    start = Offset(c.x, c.y - r), end = Offset(c.x, c.y + r),
                    strokeWidth = 1.8.dp.toPx(), cap = StrokeCap.Round
                )
            }
    )
}

@Composable
private fun PistaVacia(texto: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .drawBehind {
                drawRoundRect(
                    color = Ink.White, alpha = 0.05f,
                    cornerRadius = CornerRadius(10.dp.toPx())
                )
            }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(12.dp)
                .drawBehind {
                    drawLine(
                        Ink.White, alpha = 0.5f,
                        start = Offset(0f, size.height / 2), end = Offset(size.width, size.height / 2),
                        strokeWidth = 1.4.dp.toPx(), cap = StrokeCap.Round
                    )
                    drawLine(
                        Ink.White, alpha = 0.5f,
                        start = Offset(size.width / 2, 0f), end = Offset(size.width / 2, size.height),
                        strokeWidth = 1.4.dp.toPx(), cap = StrokeCap.Round
                    )
                }
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = texto,
            style = MaterialTheme.typography.bodyMedium,
            color = Ink.White50
        )
    }
}

/**
 * Fila de herramientas, que cambia segun haya clip elegido.
 *
 * Sin seleccion salen las del proyecto; con un clip elegido salen las suyas y
 * una flecha para volver, igual que en cualquier editor de movil. Lo que
 * todavia no esta hecho lo dice en vez de no hacer nada, que es lo peor que
 * puede hacer un boton.
 */
@Composable
private fun BarraHerramientas(
    hayClip: Boolean,
    onDeseleccionar: () -> Unit,
    acciones: Map<String, () -> Unit>,
    onPendiente: (String) -> Unit
) {
    val lista = remember(hayClip) {
        if (hayClip) {
            listOf(
                Herramienta("Dividir", { glifoDividir(this) }) {},
                Herramienta("Fotogramas", { glifoLlave(this) }) {},
                Herramienta("Animacion", { glifoAnimacion(this) }) {},
                Herramienta("Efectos", { glifoEfectos(this) }) {},
                Herramienta("Transicion", { glifoTransicion(this) }) {},
                Herramienta("Eliminar", { glifoPapelera(this) }) {}
            )
        } else {
            listOf(
                Herramienta("Anadir", { glifoMas(this) }) {},
                Herramienta("Efectos", { glifoEfectos(this) }) {},
                Herramienta("Transicion", { glifoTransicion(this) }) {}
            )
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (hayClip) {
            Box(
                Modifier
                    .padding(start = 12.dp)
                    .size(width = 34.dp, height = 46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDeseleccionar
                    )
                    .drawBehind {
                        drawRoundRect(
                            color = Ink.White, alpha = 0.06f,
                            cornerRadius = CornerRadius(12.dp.toPx())
                        )
                        glifoAtras(this)
                    }
            )
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            lista.forEach { h ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(74.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            val accion = acciones[h.nombre]
                            if (accion != null) accion() else onPendiente(h.nombre)
                        }
                        .padding(vertical = 6.dp)
                ) {
                    Box(
                        Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .drawBehind {
                                drawRoundRect(
                                    color = Ink.White, alpha = 0.07f,
                                    cornerRadius = CornerRadius(14.dp.toPx())
                                )
                                h.glifo(this)
                            }
                    )
                    Spacer(Modifier.height(6.dp))
                    // Sin el espaciado de letras del resto de la app: aqui son
                    // palabras largas en columnas estrechas, y con tracking se
                    // cortaban por la mitad.
                    Text(
                        text = h.nombre,
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 0.2.sp,
                            fontSize = 10.sp
                        ),
                        color = Ink.White50,
                        maxLines = 1,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

// --- glifos ---

private fun glifoDeshacer(d: DrawScope) = with(d) { flecha(d, alReves = false) }

private fun glifoRehacer(d: DrawScope) = with(d) { flecha(d, alReves = true) }

/** Flecha curva de deshacer. Espejada, es la de rehacer. */
private fun flecha(d: DrawScope, alReves: Boolean) = with(d) {
    val c = center
    val r = size.minDimension * 0.20f
    val signo = if (alReves) -1f else 1f
    drawArc(
        color = Ink.White, alpha = 0.85f,
        startAngle = if (alReves) 200f else -20f,
        sweepAngle = if (alReves) -200f else 200f,
        useCenter = false,
        topLeft = Offset(c.x - r, c.y - r * 0.7f),
        size = Size(r * 2, r * 1.7f),
        style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round)
    )
    val px = c.x - r * signo
    val py = c.y - r * 0.7f
    drawLine(
        Ink.White, alpha = 0.85f,
        start = Offset(px, py), end = Offset(px + r * 0.7f * signo, py - r * 0.4f),
        strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round
    )
    drawLine(
        Ink.White, alpha = 0.85f,
        start = Offset(px, py), end = Offset(px + r * 0.7f * signo, py + r * 0.5f),
        strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round
    )
}

private fun glifoAtras(d: DrawScope) = with(d) {
    val c = center
    val r = size.minDimension * 0.16f
    drawLine(
        Ink.White, alpha = 0.8f,
        start = Offset(c.x + r * 0.6f, c.y - r), end = Offset(c.x - r * 0.6f, c.y),
        strokeWidth = 1.6.dp.toPx(), cap = StrokeCap.Round
    )
    drawLine(
        Ink.White, alpha = 0.8f,
        start = Offset(c.x - r * 0.6f, c.y), end = Offset(c.x + r * 0.6f, c.y + r),
        strokeWidth = 1.6.dp.toPx(), cap = StrokeCap.Round
    )
}

private fun glifoPlay(d: DrawScope) = with(d) {
    val c = center
    val r = size.minDimension * 0.22f
    val p = androidx.compose.ui.graphics.Path().apply {
        moveTo(c.x - r * 0.7f, c.y - r); lineTo(c.x + r, c.y); lineTo(c.x - r * 0.7f, c.y + r)
        close()
    }
    drawPath(p, Ink.White)
}

private fun glifoPausa(d: DrawScope) = with(d) {
    val c = center
    val an = size.minDimension * 0.08f
    val al = size.minDimension * 0.42f
    drawRoundRect(
        Ink.White, topLeft = Offset(c.x - an * 2.1f, c.y - al / 2),
        size = Size(an, al), cornerRadius = CornerRadius(an / 2)
    )
    drawRoundRect(
        Ink.White, topLeft = Offset(c.x + an * 1.1f, c.y - al / 2),
        size = Size(an, al), cornerRadius = CornerRadius(an / 2)
    )
}

private fun glifoMas(d: DrawScope) = with(d) {
    val c = center
    val r = size.minDimension * 0.18f
    drawLine(
        Ink.White, alpha = 0.8f, start = Offset(c.x - r, c.y), end = Offset(c.x + r, c.y),
        strokeWidth = 1.6.dp.toPx(), cap = StrokeCap.Round
    )
    drawLine(
        Ink.White, alpha = 0.8f, start = Offset(c.x, c.y - r), end = Offset(c.x, c.y + r),
        strokeWidth = 1.6.dp.toPx(), cap = StrokeCap.Round
    )
}

private fun glifoDividir(d: DrawScope) = with(d) {
    val c = center
    val r = size.minDimension * 0.20f
    drawLine(
        Ink.White, alpha = 0.85f,
        start = Offset(c.x, c.y - r * 1.3f), end = Offset(c.x, c.y + r * 1.3f),
        strokeWidth = 1.4.dp.toPx()
    )
    drawRoundRect(
        Ink.White, alpha = 0.55f,
        topLeft = Offset(c.x - r * 1.9f, c.y - r),
        size = Size(r * 1.2f, r * 2),
        cornerRadius = CornerRadius(2.dp.toPx()),
        style = Stroke(1.4.dp.toPx())
    )
    drawRoundRect(
        Ink.White, alpha = 0.55f,
        topLeft = Offset(c.x + r * 0.7f, c.y - r),
        size = Size(r * 1.2f, r * 2),
        cornerRadius = CornerRadius(2.dp.toPx()),
        style = Stroke(1.4.dp.toPx())
    )
}

private fun glifoLlave(d: DrawScope) = with(d) {
    val c = center
    val r = size.minDimension * 0.17f
    val p = androidx.compose.ui.graphics.Path().apply {
        moveTo(c.x, c.y - r); lineTo(c.x + r, c.y); lineTo(c.x, c.y + r); lineTo(c.x - r, c.y)
        close()
    }
    drawPath(p, Ink.White, alpha = 0.8f)
}

private fun glifoAnimacion(d: DrawScope) = with(d) {
    val c = center
    val r = size.minDimension * 0.20f
    for (n in 0..2) {
        drawCircle(
            Ink.White, alpha = 0.25f + n * 0.28f,
            radius = 2.2.dp.toPx(),
            center = Offset(c.x - r + n * r, c.y)
        )
    }
}

private fun glifoEfectos(d: DrawScope) = with(d) {
    val c = center
    val r = size.minDimension * 0.19f
    for (a in listOf(0f, 90f, 45f, 135f)) {
        val rad = Math.toRadians(a.toDouble())
        val dx = (kotlin.math.cos(rad) * r).toFloat()
        val dy = (kotlin.math.sin(rad) * r).toFloat()
        drawLine(
            Ink.White, alpha = 0.75f,
            start = Offset(c.x - dx, c.y - dy), end = Offset(c.x + dx, c.y + dy),
            strokeWidth = 1.4.dp.toPx(), cap = StrokeCap.Round
        )
    }
}

private fun glifoTransicion(d: DrawScope) = with(d) {
    val c = center
    val r = size.minDimension * 0.17f
    drawRoundRect(
        Ink.White, alpha = 0.75f,
        topLeft = Offset(c.x - r * 1.7f, c.y - r),
        size = Size(r * 1.5f, r * 2),
        cornerRadius = CornerRadius(2.dp.toPx()),
        style = Stroke(1.4.dp.toPx())
    )
    drawRoundRect(
        Ink.White, alpha = 0.75f,
        topLeft = Offset(c.x + r * 0.2f, c.y - r),
        size = Size(r * 1.5f, r * 2),
        cornerRadius = CornerRadius(2.dp.toPx()),
        style = Stroke(1.4.dp.toPx())
    )
}

private fun glifoPapelera(d: DrawScope) = with(d) {
    val c = center
    val r = size.minDimension * 0.17f
    drawRoundRect(
        Ink.White, alpha = 0.75f,
        topLeft = Offset(c.x - r, c.y - r * 0.6f),
        size = Size(r * 2, r * 1.9f),
        cornerRadius = CornerRadius(2.dp.toPx()),
        style = Stroke(1.4.dp.toPx())
    )
    drawLine(
        Ink.White, alpha = 0.75f,
        start = Offset(c.x - r * 1.35f, c.y - r * 0.6f),
        end = Offset(c.x + r * 1.35f, c.y - r * 0.6f),
        strokeWidth = 1.4.dp.toPx(), cap = StrokeCap.Round
    )
}
