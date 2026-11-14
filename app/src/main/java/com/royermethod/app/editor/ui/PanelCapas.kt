package com.royermethod.app.editor.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.royermethod.app.editor.modelo.*
import com.royermethod.app.ui.components.*
import com.royermethod.app.ui.theme.Ink
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

// ----------------------------------------------------------------------
// La cara de un bloque en chico
// ----------------------------------------------------------------------

/**
 * Un bloque en una casilla: el primer fotograma de su tramo o, si es un
 * texto, sus letras con su fuente y su color. Se reconoce de un vistazo, sin
 * tener que leer el nombre del archivo.
 */
@Composable
internal fun ImagenClip(c: Clip, modifier: Modifier = Modifier) {
    val origen = c.origen
    // En la rejilla de un cuarto de segundo: la misma que pide la linea de tiempo, asi se aprovecha su cache
    val instante = if (c.esVideo) c.tiempoArchivo(0) / 250_000L * 250_000L else 0L
    val miniatura = origen?.let { LocalRecursosEditor.current.miniatura(it, instante) }
    val t = c.texto
    Box(modifier.background(Ink.White08), contentAlignment = Alignment.Center) {
        when {
            miniatura != null -> Image(miniatura, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            t != null -> {
                val color = Color(t.color)
                val fuente = when (t.fuente) { Fuente.SERIF -> FontFamily.Serif; Fuente.MONO -> FontFamily.Monospace; else -> FontFamily.SansSerif }
                // El fondo, al contrario que la letra: un texto negro no se pierde
                Box(Modifier.fillMaxSize().background(if (color.luminance() > 0.45f) Color(0xFF2B2E35) else Color(0xFFD5D8DE)), contentAlignment = Alignment.Center) {
                    Text(t.contenido.trim().ifBlank { "Aa" }, color = color, fontFamily = fuente,
                        fontWeight = if (t.negrita) FontWeight.Bold else FontWeight.Normal,
                        fontStyle = if (t.cursiva) FontStyle.Italic else FontStyle.Normal,
                        fontSize = 11.sp, lineHeight = 13.sp, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(3.dp))
                }
            }
            else -> Icono(if (c.esVideo) Glifos.play else Glifos.superponer, color = Ink.White50, tam = 20.dp)
        }
    }
}

private fun tipoDe(c: Clip): String = if (c.esTexto) "Texto" else if (c.esVideo) "Video" else "Imagen"

// ----------------------------------------------------------------------
// A que capas llega un ajuste o un efecto compartido
// ----------------------------------------------------------------------

/**
 * "Afecta a": una fila con lo elegido que se despliega en una tira de
 * casillas. El destino se guarda por identidad, de modo que reordenar las
 * capas no lo cambia.
 */
@Composable
internal fun DestinosCapa(estado: EstadoEditor, efectoId: Long? = null) {
    val c = estado.clipElegido ?: return
    var abierto by rememberSaveable(c.id) { mutableStateOf(false) }
    val resumen = when {
        !c.esAjuste -> "Solo este"
        c.afectaA == null -> "Todas las capas"
        c.afectaA.isEmpty() -> "Ninguna"
        c.afectaA.size == 1 -> "1 capa"
        else -> "${c.afectaA.size} capas"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .clip(esquinas(10.dp))
            .clickable { abierto = !abierto }
            .semantics { contentDescription = "Afecta a: $resumen" },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Afecta a", style = Letra.cuerpo, color = Ink.White70, modifier = Modifier.weight(1f))
        Text(resumen, style = Letra.cuerpo.copy(fontWeight = FontWeight.Medium), color = Ink.White, maxLines = 1)
        Spacer(Modifier.width(6.dp))
        Icono(Glifos.siguiente, Modifier.rotate(if (abierto) -90f else 90f), color = Ink.White50, tam = 13.dp)
    }
    if (!abierto) return
    if (!c.esAjuste) {
        // Lo puesto en un bloque es solo suyo: compartirlo lo pasa a un tramo aparte
        BotonBorde(if (efectoId == null) "Crear ajuste compartido" else "Compartir efecto", {
            if (efectoId == null) crearCapaAjuste(estado, efectos = estado.panel == Panel.EFECTOS, desdeElegido = true)
            else {
                val r = compartirEfecto(estado.proyecto, c.id, efectoId) ?: return@BotonBorde
                estado.hacer { r.first }
                estado.elegir(Seleccion.Bloque(r.second))
                estado.efectoAElegir = efectoId
                estado.panel = Panel.EFECTOS
            }
        }, glifo = Glifos.capas)
        Spacer(Modifier.height(8.dp))
        return
    }
    val p = estado.proyecto
    val candidatos = p.capas.filterNot { it.clip.esAjuste }.sortedByDescending { it.fila }.map { it.clip } + p.principal
    val ids = candidatos.map { it.id }.toSet()
    Tira(Modifier.padding(top = 2.dp, bottom = 8.dp), hueco = 8.dp) {
        Row(Modifier.height(CASILLA), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip("Todas", c.afectaA == null, { estado.cambiarClip { it.copy(afectaA = null) } })
            Chip("Ninguna", c.afectaA?.isEmpty() == true, { estado.cambiarClip { it.copy(afectaA = emptySet()) } })
        }
        candidatos.forEach { destino ->
            key(destino.id) {
                MiniaturaDestino(destino, AlcanceCapas.acepta(c, destino.id)) { alternarDestino(estado, destino.id, ids) }
            }
        }
    }
}

/** Desde Todas, quitar una conserva activas las demás; cada toque es reversible. */
internal fun alternarDestino(estado: EstadoEditor, id: Long, disponibles: Set<Long>) {
    estado.cambiarClip { actual ->
        val ids = actual.afectaA ?: disponibles
        actual.copy(afectaA = if (id in ids) ids - id else ids + id)
    }
}

/** Una capa que se marca o se desmarca: encendida, a todo color y con su visto. */
@Composable
private fun MiniaturaDestino(c: Clip, activa: Boolean, cambiar: () -> Unit) {
    val borde by animateColorAsState(if (activa) Ink.White else Ink.White15, tween(150), label = "destino")
    val forma = esquinas(10.dp)
    Box(
        Modifier
            .size(CASILLA)
            .clip(forma)
            .toggleable(value = activa, role = Role.Checkbox, onValueChange = { cambiar() })
            .semantics { contentDescription = "${tipoDe(c)}: ${c.nombre()}" }
            .border(if (activa) 2.dp else 1.dp, borde, forma)
    ) {
        ImagenClip(c, Modifier.fillMaxSize().alpha(if (activa) 1f else .4f))
        if (activa) Box(
            Modifier.align(Alignment.TopEnd).padding(4.dp).size(15.dp).clip(CircleShape).background(Ink.White),
            contentAlignment = Alignment.Center
        ) { Icono(Glifos.listo, color = Ink.Black, tam = 10.dp) }
    }
}

// ----------------------------------------------------------------------
// Orden de las capas
// ----------------------------------------------------------------------

private val CASILLA = 54.dp
private val HUECO_CASILLA = 8.dp

/**
 * Una fila de la linea de tiempo con algo que se ve (los tramos de ajustes no
 * cuentan: su fila no decide quien tapa a quien). [clave] la identifica aunque
 * cambie de fila: al reordenar, sus bloques se mueven juntos.
 */
internal class FilaOrden(val fila: Int, val capas: List<Capa>) {
    val clave: Long = capas.minOf { it.id }

    /** El bloque de la fila que esta bajo el cabezal o, si no hay, el mas cercano. */
    fun enCabezal(t: Long): Capa = capas.firstOrNull { t >= it.inicioUs && t < it.finUs }
        ?: capas.minBy { minOf(abs(it.inicioUs - t), abs(it.finUs - t)) }
}

/** Las filas con algo que se ve, de la que esta delante a la que esta detras. */
internal fun filasOrden(p: Proyecto): List<FilaOrden> =
    p.capas.filterNot { it.clip.esAjuste }.groupBy { it.fila }
        .map { (fila, capas) -> FilaOrden(fila, capas.sortedBy { it.inicioUs }) }
        .sortedByDescending { it.fila }

/**
 * Lleva la fila [clave] al puesto [destino] de la lista de [filasOrden] (0 es
 * la de delante), cambiandola con sus vecinas de una en una. Las demas
 * conservan su orden entre si.
 */
internal fun moverFila(p: Proyecto, clave: Long, destino: Int): Proyecto {
    var q = p
    while (true) {
        val filas = filasOrden(q)
        val actual = filas.indexOfFirst { it.clave == clave }
        if (actual < 0 || actual == destino.coerceIn(0, filas.lastIndex)) return q
        val siguiente = AlcanceCapas.cambiarOrden(q, filas[actual].fila, delante = destino < actual)
        if (siguiente == q) return q
        q = siguiente
    }
}

/**
 * Orden: una casilla por capa con su imagen, la de delante a la izquierda.
 * Se mantiene pulsada una y se arrastra a su sitio; el cambio se ve en la
 * imagen mientras se arrastra y es un solo paso de deshacer. Tocar una casilla
 * elige esa capa. La pista principal va al final, fija: siempre queda detras.
 */
@Composable
internal fun HojaCapas(estado: EstadoEditor, onCerrar: () -> Unit) {
    val p = estado.proyecto
    val filas = remember(p.capas) { filasOrden(p) }
    HojaPanel(
        Panel.CAPAS.titulo, onCerrar, corta = true,
        acciones = if (filas.size >= 2) ({
            Text("Mantén y arrastra", style = Letra.chica, color = Ink.White50, maxLines = 1, softWrap = false)
            Spacer(Modifier.width(4.dp))
        }) else null
    ) {
        if (filas.isEmpty()) {
            Nota("No hay capas encima de la pista principal.", Modifier.padding(vertical = 14.dp))
        } else {
            Row(Modifier.fillMaxWidth().padding(bottom = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Delante", style = Letra.mini, color = Ink.White50)
                Box(Modifier.weight(1f).padding(horizontal = 8.dp).height(1.dp).background(Ink.White08))
                Text("Detrás", style = Letra.mini, color = Ink.White50)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TiraOrden(estado, filas, Modifier.weight(1f))
                val t = estado.tiempo
                val base = Linea.principalEn(p.principal, Linea.inicios(p.principal), t)?.let { it.segundo ?: it.primero } ?: p.principal.firstOrNull()
                if (base != null) {
                    Box(Modifier.padding(horizontal = 8.dp).width(1.dp).height(34.dp).background(Ink.White15))
                    val forma = esquinas(10.dp)
                    Box(
                        Modifier
                            .size(CASILLA)
                            .clip(forma)
                            .clickable { estado.avisar("La pista principal siempre va detrás. Para poner uno de sus bloques delante, conviértelo con «A capa»") }
                            .semantics { contentDescription = "Pista principal: siempre detrás" }
                            .border(1.dp, Ink.White15, forma)
                    ) {
                        ImagenClip(base, Modifier.fillMaxSize().alpha(.45f))
                        Box(
                            Modifier.align(Alignment.Center).size(22.dp).clip(CircleShape).background(Color.Black.copy(alpha = .55f)),
                            contentAlignment = Alignment.Center
                        ) { Icono(Glifos.candado, color = Ink.White90, tam = 13.dp) }
                    }
                }
            }
        }
    }
}

/** Lo que el gesto de arrastrar lleva apuntado y no hace falta redibujar. */
private class GestoOrden {
    /** Del dedo al borde izquierdo de la casilla cogida. */
    var agarre = 0f
    /** El dedo, medido en lo que se ve de la tira (no en su contenido, que se desliza). */
    var dedoVista = 0f
    var asentando: Job? = null
    var deslizando: Job? = null
}

@Composable
private fun TiraOrden(estado: EstadoEditor, filas: List<FilaOrden>, modifier: Modifier) {
    val densidad = LocalDensity.current
    val paso = with(densidad) { (CASILLA + HUECO_CASILLA).toPx() }
    val lado = with(densidad) { CASILLA.toPx() }
    val orilla = with(densidad) { 34.dp.toPx() }
    val desliza = rememberScrollState()
    val alcance = rememberCoroutineScope()
    val vibrar = LocalHapticFeedback.current
    val gesto = remember { GestoOrden() }
    // La casilla que no esta en su puesto: sigue al dedo o esta volviendo a su sitio
    var arrastrada by remember { mutableStateOf<Long?>(null) }
    // El dedo la tiene cogida
    var levantada by remember { mutableStateOf(false) }
    // Su borde izquierdo, en el contenido de la tira
    val izquierda = remember { mutableFloatStateOf(0f) }
    var anchoVista by remember { mutableIntStateOf(0) }

    // Pone la casilla bajo el dedo y, si ya paso de la mitad de una vecina, cambia el orden
    fun recolocar() {
        val clave = arrastrada ?: return
        if (!levantada) return
        val total = filasOrden(estado.proyecto).size
        if (total == 0) return
        izquierda.floatValue = (gesto.dedoVista + desliza.value - gesto.agarre).coerceIn(0f, (total - 1) * paso)
        val destino = (izquierda.floatValue / paso).roundToInt().coerceIn(0, total - 1)
        val antes = estado.proyecto
        estado.ajustar { moverFila(it, clave, destino) }
        if (estado.proyecto != antes) vibrar.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun soltar() {
        gesto.deslizando?.cancel()
        gesto.deslizando = null
        if (!levantada) return
        levantada = false
        estado.terminarGesto()
        val clave = arrastrada ?: return
        gesto.asentando = alcance.launch {
            val puesto = filasOrden(estado.proyecto).indexOfFirst { it.clave == clave }
            if (puesto >= 0) animate(izquierda.floatValue, puesto * paso, animationSpec = spring(stiffness = Spring.StiffnessMedium)) { v, _ -> izquierda.floatValue = v }
            arrastrada = null
        }
    }

    Box(
        modifier
            .onSizeChanged { anchoVista = it.width }
            .horizontalScroll(desliza)
    ) {
        Box(
            Modifier
                // Margen para que la casilla levantada, que crece un poco, no se corte
                .padding(vertical = 5.dp)
                .size(width = (CASILLA + HUECO_CASILLA) * filas.size - HUECO_CASILLA, height = CASILLA)
                .pointerInput(paso) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { o ->
                            gesto.asentando?.cancel()
                            val actuales = filasOrden(estado.proyecto)
                            val i = (o.x / paso).toInt()
                            if (actuales.size >= 2 && i in actuales.indices && o.x - i * paso <= lado) {
                                arrastrada = actuales[i].clave
                                levantada = true
                                gesto.agarre = o.x - i * paso
                                gesto.dedoVista = o.x - desliza.value
                                izquierda.floatValue = i * paso
                                vibrar.performHapticFeedback(HapticFeedbackType.LongPress)
                                estado.empezarGesto()
                                // Cerca de una orilla la tira se desliza sola: se llega a las capas que no caben
                                gesto.deslizando?.cancel()
                                gesto.deslizando = alcance.launch {
                                    while (isActive) {
                                        withFrameNanos { }
                                        val x = gesto.dedoVista
                                        val empuje = when {
                                            x < orilla && desliza.value > 0 -> -(orilla - x) / orilla
                                            x > anchoVista - orilla && desliza.value < desliza.maxValue -> (x - (anchoVista - orilla)) / orilla
                                            else -> 0f
                                        }
                                        if (empuje != 0f) {
                                            desliza.scrollBy(empuje.coerceIn(-1.5f, 1.5f) * paso * 0.14f)
                                            recolocar()
                                        }
                                    }
                                }
                            } else {
                                arrastrada = null
                            }
                        },
                        onDrag = { ch, _ ->
                            ch.consume()
                            gesto.dedoVista = ch.position.x - desliza.value
                            recolocar()
                        },
                        onDragEnd = { soltar() },
                        onDragCancel = { soltar() }
                    )
                }
        ) {
            filas.forEachIndexed { indice, f ->
                key(f.clave) {
                    val esta = arrastrada == f.clave
                    val enSitio = animateFloatAsState(indice * paso, spring(stiffness = Spring.StiffnessMediumLow), label = "puesto")
                    val alzada by animateFloatAsState(if (esta && levantada) 1f else 0f, tween(140), label = "alzada")
                    val capa = f.enCabezal(estado.tiempo)
                    val elegida = f.capas.any { it.id == estado.idElegido }
                    val forma = esquinas(10.dp)
                    Box(
                        Modifier
                            .offset { IntOffset((if (esta) izquierda.floatValue else enSitio.value).roundToInt(), 0) }
                            .zIndex(if (esta) 1f else 0f)
                            .size(CASILLA)
                            .graphicsLayer {
                                val s = 1f + 0.1f * alzada
                                scaleX = s
                                scaleY = s
                            }
                            .clip(forma)
                            .clickable {
                                estado.elegir(Seleccion.Bloque(capa.id))
                                estado.panel = Panel.CAPAS
                            }
                            .semantics {
                                contentDescription = "Capa ${indice + 1} de ${filas.size}, ${tipoDe(capa.clip)}: ${capa.clip.nombre()}"
                                customActions = listOfNotNull(
                                    if (indice > 0) CustomAccessibilityAction("Traer delante") { estado.hacer { AlcanceCapas.cambiarOrden(it, f.fila, true) }; true } else null,
                                    if (indice < filas.lastIndex) CustomAccessibilityAction("Enviar detrás") { estado.hacer { AlcanceCapas.cambiarOrden(it, f.fila, false) }; true } else null
                                )
                            }
                            .border(if (elegida || alzada > 0f) 2.dp else 1.dp, if (elegida || alzada > 0f) Ink.White else Ink.White15, forma)
                    ) {
                        ImagenClip(capa.clip, Modifier.fillMaxSize())
                        // Varios bloques en la misma fila se mueven juntos: cuantos son
                        if (f.capas.size > 1) Text(
                            "${f.capas.size}", color = Ink.White, fontSize = 9.sp, lineHeight = 11.sp, fontWeight = FontWeight.Medium,
                            modifier = Modifier.align(Alignment.TopEnd).padding(3.dp).clip(esquinas(6.dp))
                                .background(Color.Black.copy(alpha = .6f)).padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }
        }
    }
}
