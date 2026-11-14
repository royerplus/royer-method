package com.royermethod.app.editor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.royermethod.app.editor.modelo.propiedadesEn
import com.royermethod.app.editor.modelo.Ajuste
import com.royermethod.app.editor.modelo.AjustesColor
import com.royermethod.app.editor.modelo.Alineacion
import com.royermethod.app.editor.modelo.Bezier
import com.royermethod.app.editor.modelo.Clip
import com.royermethod.app.editor.modelo.Curva
import com.royermethod.app.editor.modelo.CurvaMia
import com.royermethod.app.editor.modelo.CurvaVelocidad
import com.royermethod.app.editor.modelo.PlantillasVelocidad
import com.royermethod.app.editor.modelo.PuntoVelocidad
import com.royermethod.app.editor.modelo.PlantillasCurva
import com.royermethod.app.editor.modelo.Edicion
import com.royermethod.app.editor.modelo.Escena
import com.royermethod.app.editor.modelo.Filtro
import com.royermethod.app.editor.modelo.Fondo
import com.royermethod.app.editor.modelo.Formato
import com.royermethod.app.editor.modelo.Fuente
import com.royermethod.app.editor.modelo.Mezcla
import com.royermethod.app.editor.modelo.Recorte
import com.royermethod.app.editor.modelo.Texto
import com.royermethod.app.editor.modelo.TiempoPreciso
import com.royermethod.app.ui.components.BotonAccion
import com.royermethod.app.ui.components.BotonIcono
import com.royermethod.app.ui.components.BotonTexto
import com.royermethod.app.ui.components.Chip
import com.royermethod.app.ui.components.ChipInterruptor
import com.royermethod.app.ui.components.Deslizador
import com.royermethod.app.ui.components.Escala
import com.royermethod.app.ui.components.Etiqueta
import com.royermethod.app.ui.components.Glifos
import com.royermethod.app.ui.components.Letra
import com.royermethod.app.ui.components.LocalGestos
import com.royermethod.app.ui.components.Mosaico
import com.royermethod.app.ui.components.Nota
import com.royermethod.app.ui.components.Opciones
import com.royermethod.app.ui.components.Pestanas
import com.royermethod.app.ui.components.Segmentos
import com.royermethod.app.ui.components.Tira
import com.royermethod.app.ui.components.esquinas
import com.royermethod.app.ui.theme.Ink
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.text.style.TextOverflow
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.max
import kotlin.math.min

/**
 * Hoja de abajo con un panel, ordenada como en CapCut: arriba lo que se
 * toca, abajo una barra con el nombre y el boton de listo, justo donde
 * estaba la barra de herramientas (y donde cae el pulgar).
 *
 * Ocupa el sitio de la linea de tiempo y la barra de herramientas, nunca el
 * de la imagen: abrir un panel no mueve ni encoge lo que se esta viendo. Con
 * el asa de arriba se puede ampliar para ver mas controles a la vez.
 *
 * [corta] es para los paneles de dos o tres controles: la hoja mide lo que su
 * contenido y la linea de tiempo se sigue viendo (y usando) encima.
 *
 * [acciones] va en la barra de abajo, entre el nombre y el boton de listo
 * (p. ej. "Animar"). [fijo] queda arriba sin desplazarse, encima del
 * [contenido], que es lo unico que se desliza (p. ej. unas pestanas).
 */
@Composable
fun HojaPanel(
    titulo: String,
    onCerrar: () -> Unit,
    modifier: Modifier = Modifier,
    acciones: (@Composable RowScope.() -> Unit)? = null,
    fijo: (@Composable ColumnScope.() -> Unit)? = null,
    claveDesplazamiento: Any? = null,
    corta: Boolean = false,
    contenido: @Composable ColumnScope.() -> Unit
) {
    val desplazamiento=rememberScrollState()
    LaunchedEffect(claveDesplazamiento) { desplazamiento.scrollTo(0) }
    val tamano=LocalTamanoPanelEditor.current.takeUnless { corta }
    Column(
        modifier
            .fillMaxWidth()
            .then(if (corta) Modifier else Modifier.fillMaxHeight())
            .background(Ink.Black)
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.White08))
        if(tamano!=null) Box(Modifier.fillMaxWidth().height(20.dp)
            .pointerInput(tamano.ampliado) {
                var distancia=0f
                detectVerticalDragGestures(onDragStart={distancia=0f},onDragEnd={
                    if((distancia < -16.dp.toPx() && !tamano.ampliado) || (distancia > 16.dp.toPx() && tamano.ampliado))tamano.cambiar()
                }) { ch,dy -> distancia+=dy;ch.consume() }
            }
            .semantics { contentDescription=if(tamano.ampliado) "Reducir panel" else "Ampliar panel" }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { tamano.cambiar() },contentAlignment=Alignment.Center) {
            Box(Modifier.width(30.dp).height(3.dp).clip(esquinas(2.dp)).background(Ink.White30))
        } else Spacer(Modifier.height(10.dp))
        if (fijo != null) Column(Modifier.fillMaxWidth().padding(horizontal = MARGEN_HOJA), content = fijo)
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f, fill = !corta)
                .verticalScroll(desplazamiento)
                .padding(horizontal = MARGEN_HOJA)
                .padding(bottom = 8.dp),
            content = contenido
        )
        BarraHoja(titulo, onCerrar, acciones)
    }
}

/** Margen lateral del contenido de una hoja. */
internal val MARGEN_HOJA = 14.dp

/** La barra de abajo de una hoja: nombre, acciones y listo. */
@Composable
internal fun BarraHoja(titulo: String, onCerrar: () -> Unit, acciones: (@Composable RowScope.() -> Unit)? = null) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.White08))
    Row(
        Modifier.fillMaxWidth().heightIn(min = 46.dp).padding(start = MARGEN_HOJA, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(titulo, style = Letra.titulo, color = Ink.White, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        acciones?.invoke(this)
        BotonIcono(Glifos.listo, onCerrar, color = Ink.White, tam = 44.dp, descripcion = "Cerrar panel")
    }
}

/**
 * Un dato corto en la barra de una hoja, junto al boton de listo: lo que dura,
 * lo que mide, lo que pesa. Va ahi para no gastar una linea del contenido.
 */
@Composable
internal fun DatoHoja(texto: String) {
    Text(texto, style = Letra.chica, color = Ink.White50, maxLines = 1, softWrap = false)
    Spacer(Modifier.width(4.dp))
}

@Composable
private fun Hueco(alto: Int = 12) = Spacer(Modifier.height(alto.dp))

// ----------------------------------------------------------------------
// Velocidad
// ----------------------------------------------------------------------

private val VELOCIDADES = listOf(0.25f, 0.5f, 0.75f, 1f, 1.5f, 2f, 3f, 5f)

/** "0.5x", "2x": sin ceros de mas. */
private fun veces(v: Float): String = (if (v == v.toInt().toFloat()) "${v.toInt()}" else "$v") + "x"

/**
 * Velocidad: la barra, los valores de siempre en una tira y, debajo, los dos
 * interruptores. Lo que dura el bloque va en la barra de la hoja.
 */
@Composable
fun PanelVelocidad(estado: EstadoEditor) {
    val c = estado.clipElegido ?: return
    Deslizador(
        "Velocidad", c.velocidad, 0.1f, 10f,
        onCambio = { v -> estado.cambiarClip(arrastre = true) { it.conVelocidad(v) } },
        neutro = 1f, escala = Escala.LOGARITMICA,
        texto = { "%.2gx".format(it) }
    )
    Tira(Modifier.padding(vertical = 3.dp)) {
        VELOCIDADES.forEach { v -> Chip(veces(v), c.velocidad == v, { estado.cambiarClip { it.conVelocidad(v) } }) }
    }
    Row(Modifier.fillMaxWidth().padding(top = 5.dp, bottom = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChipInterruptor("Mantener tono", c.mantenerTono, { v ->
            estado.cambiarClip { it.copy(mantenerTono = v) }
            if (v) estado.avisar("La voz no suena aguda al acelerar ni grave al frenar")
        })
        ChipInterruptor("Fluido", c.fluido, { v ->
            estado.cambiarClip { it.copy(fluido = v) }
            if (v) estado.avisar("Fluido: se generan los fotogramas que faltan, sin tirones. Exporta más despacio")
        })
    }
}

// ----------------------------------------------------------------------
// Volumen
// ----------------------------------------------------------------------

/** Volumen y los dos fundidos, de un sonido o del audio de un video. */
@Composable
fun PanelVolumen(estado: EstadoEditor) {
    val a = estado.sonidoElegido
    if (a != null) {
        val id = a.id
        fun cambiar(f: (com.royermethod.app.editor.modelo.PistaAudio) -> com.royermethod.app.editor.modelo.PistaAudio) =
            estado.ajustar { Edicion.cambiarAudio(it, id, f) }
        ControlesVolumen(
            a.volumen, a.fundidoEntradaUs, a.fundidoSalidaUs, a.duracionUs,
            { v -> cambiar { it.copy(volumen = v) } },
            { v -> cambiar { it.copy(fundidoEntradaUs = v) } },
            { v -> cambiar { it.copy(fundidoSalidaUs = v) } }
        )
        return
    }
    val c = estado.clipElegido ?: return
    ControlesVolumen(
        c.volumen, c.fundidoEntradaUs, c.fundidoSalidaUs, c.duracionUs,
        { v -> estado.cambiarClip(true) { it.copy(volumen = v) } },
        { v -> estado.cambiarClip(true) { it.copy(fundidoEntradaUs = v) } },
        { v -> estado.cambiarClip(true) { it.copy(fundidoSalidaUs = v) } }
    )
}

@Composable
private fun ControlesVolumen(
    volumen: Float, entradaUs: Long, salidaUs: Long, duracionUs: Long,
    onVolumen: (Float) -> Unit, onEntrada: (Long) -> Unit, onSalida: (Long) -> Unit
) {
    val maxFundido = min(duracionUs / 2, 10_000_000L) / 1_000_000f
    Deslizador("Volumen", volumen, 0f, 2f, onVolumen, neutro = 1f, texto = { "${(it * 100).toInt()}%" })
    // Subir desde el silencio al empezar y bajar hasta el al terminar
    Etiqueta("Fundido")
    Deslizador("Entrada", entradaUs / 1e6f, 0f, maxFundido, { v -> onEntrada((v * 1e6).toLong()) }, paso = 0.1f, texto = { "%.1f s".format(it) })
    Deslizador("Salida", salidaUs / 1e6f, 0f, maxFundido, { v -> onSalida((v * 1e6).toLong()) }, paso = 0.1f, texto = { "%.1f s".format(it) })
}

// ----------------------------------------------------------------------
// Color
// ----------------------------------------------------------------------

private class Ajustito(
    val nombre: String,
    val desde: Float,
    val leer: (AjustesColor) -> Float,
    val poner: (AjustesColor, Float) -> AjustesColor,
    /** Lo que hace, para los que no se explican solos: sale un momento la primera vez que se elige. */
    val nota: String? = null
)

// En el orden de CapCut, con lo mas usado al principio: solo caben unas
// cuatro pastillas a lo ancho del telefono.
private val AJUSTES = listOf(
    Ajustito("Brillo", -1f, { it.brillo }, { a, v -> a.copy(brillo = v) }),
    Ajustito("Contraste", -1f, { it.contraste }, { a, v -> a.copy(contraste = v) }),
    Ajustito("Saturación", -1f, { it.saturacion }, { a, v -> a.copy(saturacion = v) }),
    Ajustito("Nitidez", 0f, { it.nitidez }, { a, v -> a.copy(nitidez = v) }, "Nitidez: marca el filo de las líneas"),
    Ajustito("Claridad", -1f, { it.claridad }, { a, v -> a.copy(claridad = v) }, "Claridad: más volumen en el pelo y la ropa; a la izquierda suaviza"),
    Ajustito("Exposición", -1f, { it.exposicion }, { a, v -> a.copy(exposicion = v) }),
    Ajustito("Luces", -1f, { it.luces }, { a, v -> a.copy(luces = v) }),
    Ajustito("Sombras", -1f, { it.sombras }, { a, v -> a.copy(sombras = v) }),
    Ajustito("Calidez", -1f, { it.calidez }, { a, v -> a.copy(calidez = v) }),
    Ajustito("Tinte", -1f, { it.tinte }, { a, v -> a.copy(tinte = v) }),
    Ajustito("Vivacidad", -1f, { it.vivacidad }, { a, v -> a.copy(vivacidad = v) }, "Vivacidad: sube los colores apagados sin quemar los fuertes"),
    Ajustito("Desvanecer", 0f, { it.desvanecer }, { a, v -> a.copy(desvanecer = v) }),
    Ajustito("Viñeta", 0f, { it.vineta }, { a, v -> a.copy(vineta = v) }),
    Ajustito("Grano", 0f, { it.grano }, { a, v -> a.copy(grano = v) }, "El grano cambia en cada fotograma: el video exportado pesa más")
)

/** Las notas de ajustes que ya salieron en esta sesion: cada una se ensena una sola vez. */
private val notasVistas = HashSet<String>()

/** Un color que representa cada filtro en su casilla. */
private fun muestraDe(f: Filtro): List<Color> = when (f) {
    Filtro.NINGUNO -> listOf(Color(0xFF8E9AAF), Color(0xFFCBC0D3))
    Filtro.VIVO -> listOf(Color(0xFFFF4D6D), Color(0xFFFFD60A))
    Filtro.ANIME -> listOf(Color(0xFF4CC9F0), Color(0xFFF72585))
    Filtro.FRIO -> listOf(Color(0xFF4361EE), Color(0xFF90E0EF))
    Filtro.CALIDO -> listOf(Color(0xFFF8961E), Color(0xFFF9C74F))
    Filtro.CINE -> listOf(Color(0xFF264653), Color(0xFFE9C46A))
    Filtro.NOCHE -> listOf(Color(0xFF03045E), Color(0xFF0077B6))
    Filtro.DESVAIDO -> listOf(Color(0xFFB5838D), Color(0xFFE5989B))
    Filtro.BYN -> listOf(Color(0xFF111111), Color(0xFFEEEEEE))
    Filtro.DRAMA -> listOf(Color(0xFF370617), Color(0xFFE85D04))
}

/**
 * Los ajustes del bloque, como en CapCut: un solo deslizador, el del ajuste
 * elegido en la tira de abajo; un punto marca los que tienen algo puesto.
 * Asi caben todos sin una columna de catorce barras.
 */
@Composable
internal fun AjustesDeColor(estado: EstadoEditor) {
    val c = estado.clipElegido?.propiedadesEn(estado.localElegido ?: 0L) ?: return
    var elegido by rememberSaveable { mutableIntStateOf(0) }
    val a = AJUSTES[elegido]
    // key: cada ajuste su deslizador, que el gesto guarda el rango y el neutro con que empezo
    key(elegido) {
        Deslizador(a.nombre, a.leer(c.color), a.desde, 1f, { v -> estado.cambiarAspecto(true) { it.copy(color = a.poner(it.color, v)) } },
            neutro = 0f, texto = { if (a.desde < 0) "%+d".format((it * 100).toInt()) else "${(it * 100).toInt()}" })
    }
    Tira(Modifier.padding(top = 4.dp, bottom = 4.dp)) {
        AJUSTES.forEachIndexed { i, x ->
            Chip(x.nombre, i == elegido, {
                elegido = i
                if (x.nota != null && notasVistas.add(x.nombre)) estado.avisar(x.nota)
            }, marca = x.leer(c.color) != 0f)
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        BotonTexto("Restablecer", { estado.cambiarAspecto { it.copy(color = AjustesColor()) } }, glifo = Glifos.reiniciar, activo = !c.color.neutro)
        if (!c.esAjuste) BotonTexto("Copiar a la pista principal", { copiarColorAPrincipal(estado, c) })
    }
}

/** Los filtros en una tira que se desliza y, con uno puesto, cuanto se nota. */
@Composable
internal fun FiltrosDeColor(estado: EstadoEditor) {
    val c = estado.clipElegido?.propiedadesEn(estado.localElegido ?: 0L) ?: return
    Tira(Modifier.padding(top = 4.dp)) {
        Filtro.values().forEach { f ->
            val colores = muestraDe(f)
            Mosaico(f.nombre, c.filtro == f, { estado.cambiarAspecto { if (f == Filtro.NINGUNO) it.copy(filtro = f, intensidadFiltro = 1f) else it.copy(filtro = f) } }) { _ ->
                drawCircle(colores[0], size.minDimension * 0.34f, Offset(size.width * 0.38f, size.height * 0.5f))
                drawCircle(colores[1].copy(alpha = 0.85f), size.minDimension * 0.34f, Offset(size.width * 0.62f, size.height * 0.5f))
            }
        }
    }
    if (c.filtro != Filtro.NINGUNO) {
        Hueco(4)
        Deslizador("Intensidad", c.intensidadFiltro, 0f, 1f, { v -> estado.cambiarAspecto(true) { it.copy(intensidadFiltro = v) } }, neutro = 1f, texto = { "${(it * 100).toInt()}%" })
        if (!c.esAjuste) BotonTexto("Copiar a la pista principal", { copiarColorAPrincipal(estado, c) })
    }
}

/** Los ajustes y el filtro de [c], a todos los bloques de la pista principal. */
private fun copiarColorAPrincipal(estado: EstadoEditor, c: Clip) {
    estado.hacer { p ->
        // Los textos no tienen ajustes de imagen
        p.copy(principal = p.principal.map { if (it.esTexto) it else it.copy(color = c.color, filtro = c.filtro, intensidadFiltro = c.intensidadFiltro) })
    }
    estado.avisar("Ajustes y filtro aplicados a toda la pista principal")
}

// ----------------------------------------------------------------------
// Encuadre
// ----------------------------------------------------------------------

/**
 * Encuadre: donde esta el bloque, cuanto mide y como se mezcla. En pestanas,
 * que eran catorce controles en una sola columna: Posicion (lo de todos los
 * dias), Recorte (los cuatro bordes) y, en una capa, Mezcla.
 */
@Composable
internal fun HojaEncuadre(estado: EstadoEditor, cerrar: () -> Unit) {
    val c = estado.clipElegido ?: return
    val id = c.id
    val e = c.encuadreEn(estado.localElegido ?: 0)
    val capa = estado.capaElegida
    fun cambiar(arrastre: Boolean = true, f: (com.royermethod.app.editor.modelo.Encuadre) -> com.royermethod.app.editor.modelo.Encuadre) {
        val g: (com.royermethod.app.editor.modelo.Proyecto) -> com.royermethod.app.editor.modelo.Proyecto = { Edicion.ajustarEncuadre(it, id, estado.tiempo, toleranciaClaves(), f) }
        if (arrastre) estado.ajustar(g) else estado.hacer(g)
    }
    val pestanas = buildList {
        add("Posición")
        if (!c.esTexto) add("Recorte")
        if (capa != null) add("Mezcla")
    }
    var elegida by rememberSaveable(id) { mutableStateOf(pestanas[0]) }
    val pestana = if (elegida in pestanas) elegida else pestanas[0]
    HojaPanel(
        Panel.ENCUADRE.titulo, cerrar, claveDesplazamiento = pestana,
        fijo = if (pestanas.size > 1) ({ Pestanas(pestanas, pestanas.indexOf(pestana), { elegida = pestanas[it] }) }) else null
    ) {
        when (pestana) {
            "Recorte" -> {
                val r = e.recorte
                fun rc(f: (Recorte) -> Recorte) = cambiar { it.copy(recorte = f(it.recorte)) }
                Deslizador("Izquierda", r.izquierda, 0f, 0.45f, { v -> rc { it.copy(izquierda = v) } }, texto = { "${(it * 100).toInt()}%" })
                Deslizador("Derecha", r.derecha, 0f, 0.45f, { v -> rc { it.copy(derecha = v) } }, texto = { "${(it * 100).toInt()}%" })
                Deslizador("Arriba", r.arriba, 0f, 0.45f, { v -> rc { it.copy(arriba = v) } }, texto = { "${(it * 100).toInt()}%" })
                Deslizador("Abajo", r.abajo, 0f, 0.45f, { v -> rc { it.copy(abajo = v) } }, texto = { "${(it * 100).toInt()}%" })
            }
            "Mezcla" -> if (capa != null) {
                Hueco(4)
                Opciones(Mezcla.values().toList(), capa.mezcla, { it.nombre }, { m -> estado.hacer { Edicion.cambiarCapa(it, id) { k -> k.copy(mezcla = m) } } })
            }
            else -> {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BotonAccion(Glifos.espejo, "Espejo", { cambiar(false) { it.copy(espejo = !it.espejo) } }, Modifier.weight(1f))
                    BotonAccion(Glifos.girar, "Girar 90°", { cambiar(false) { it.copy(rotacion = ((it.rotacion + 90f + 180f) % 360f) - 180f) } }, Modifier.weight(1f))
                    BotonAccion(Glifos.reiniciar, "Centrar", { cambiar(false) { it.copy(x = 0f, y = 0f, escala = 1f, rotacion = 0f) } }, Modifier.weight(1f))
                }
                if (!c.esTexto) Segmentos(Ajuste.values().toList(), e.ajuste, { it.nombre }, { a -> cambiar(false) { it.copy(ajuste = a) } })
                Deslizador("Tamaño", e.escala, 0.1f, 6f, { v -> cambiar { it.copy(escala = v) } }, neutro = 1f, escala = Escala.LOGARITMICA, texto = { "${(it * 100).toInt()}%" })
                Deslizador("Horizontal", e.x, -1f, 1f, { v -> cambiar { it.copy(x = v) } }, neutro = 0f, texto = { "%+d".format((it * 100).toInt()) })
                Deslizador("Vertical", e.y, -1f, 1f, { v -> cambiar { it.copy(y = v) } }, neutro = 0f, texto = { "%+d".format((it * 100).toInt()) })
                Deslizador("Giro", e.rotacion, -180f, 180f, { v -> cambiar { it.copy(rotacion = v) } }, neutro = 0f, texto = { "${it.toInt()}°" })
                Deslizador("Opacidad", e.opacidad, 0f, 1f, { v -> cambiar { it.copy(opacidad = v) } }, neutro = 1f, texto = { "${(it * 100).toInt()}%" })
            }
        }
    }
}

// ----------------------------------------------------------------------
// Texto
// ----------------------------------------------------------------------

val COLORES_TEXTO = listOf(
    0xFFFFFFFF, 0xFF000000, 0xFFFF3B5C, 0xFFFF9F1C, 0xFFFFE066, 0xFF5BFF8F,
    0xFF2EC4FF, 0xFF7B61FF, 0xFFFF6AD5, 0xFF8D99AE
).map { it.toInt() }

/** Lo que lleva un texto recien puesto, hasta que se escribe encima. */
internal const val TEXTO_NUEVO = "Tu texto"

// ----------------------------------------------------------------------
// Formato
// ----------------------------------------------------------------------

/** La proporcion del video y lo que se ve donde la imagen no llega. */
@Composable
fun PanelFormato(estado: EstadoEditor) {
    val p = estado.proyecto
    Row(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Formato.values().forEach { f ->
            Mosaico(f.nombre, p.formato == f, { estado.hacer { it.copy(formato = f) } }, Modifier.weight(1f)) { col ->
                val a = f.ancho.toFloat() / f.alto
                val w = if (a >= 1) size.width * 0.9f else size.height * 0.9f * a
                val h = if (a >= 1) size.width * 0.9f / a else size.height * 0.9f
                drawRoundRect(col, Offset((size.width - w) / 2, (size.height - h) / 2), androidx.compose.ui.geometry.Size(w, h),
                    androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()), style = Stroke(1.6.dp.toPx()))
            }
        }
    }
    Etiqueta("Fondo")
    Segmentos(Fondo.values().toList(), p.fondo, { it.nombre }, { f -> estado.hacer { it.copy(fondo = f) } })
}

// ----------------------------------------------------------------------
// Fotograma clave
// ----------------------------------------------------------------------

/** Dibuja la forma de una curva dentro de la casilla. */
fun DrawScope.dibujoCurva(f: (Float) -> Float, color: Color) {
    val path = Path()
    val n = 48
    val ys = FloatArray(n + 1) { f(it / n.toFloat()) }
    // Las que se pasan de 0..1 (rebotes, anticipaciones) se encogen para caber
    val lo = minOf(0f, ys.min())
    val hi = maxOf(1f, ys.max())
    for (i in 0..n) {
        val x = i / n.toFloat()
        val y = (ys[i] - lo) / (hi - lo)
        val px = x * size.width
        val py = size.height * (0.85f - 0.7f * y)
        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }
    drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
}

/**
 * Curvas: como se mueve el bloque entre un fotograma clave y el siguiente.
 * Se abre directo en las curvas del tramo que hay bajo el cabezal (el
 * tramo sale de un estado derivado: al reproducir solo se recompone al
 * pasar de un tramo a otro).
 */
@Composable
fun PanelCurva(estado: EstadoEditor) {
    val c = estado.clipElegido ?: return
    val ini = estado.inicioElegido ?: return
    val tolerancia = toleranciaClaves()
    val tiempos = remember(c.claves) { c.claves.map { it.tiempoUs }.distinct().sorted() }
    if (tiempos.size < 2) {
        Hueco(4)
        Text(
            "Pon dos fotogramas clave en momentos distintos (el rombo de arriba) y aquí eliges cómo se mueve de uno a otro.",
            style = Letra.cuerpo, color = Ink.White70
        )
        return
    }
    // El tramo que empieza en la ultima clave antes del cabezal (nunca la ultima de todas)
    val tramo by remember(tiempos) {
        derivedStateOf {
            val l = estado.localElegido ?: 0L
            (tiempos.indexOfLast { it <= l + tolerancia }).coerceIn(0, tiempos.size - 2)
        }
    }
    val i = tramo
    val tk = tiempos[i]
    val k = c.claves.lastOrNull { it.tiempoUs == tk } ?: return
    // De que clave a que clave: las flechas llevan el cabezal al tramo de al lado
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BotonIcono(Glifos.atras, { estado.pausarEn(ini + tiempos[i - 1]) }, activo = i > 0, tam = 36.dp, descripcion = "Tramo anterior")
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text("Tramo ${i + 1} de ${tiempos.size - 1}", style = Letra.cuerpo.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium), color = Ink.White, maxLines = 1)
            Spacer(Modifier.width(8.dp))
            Text("%.2f a %.2f s".format(tk / 1e6, tiempos[i + 1] / 1e6), style = Letra.chica, color = Ink.White50, maxLines = 1)
        }
        BotonIcono(Glifos.siguiente, { estado.pausarEn(ini + tiempos[i + 1]) }, activo = i < tiempos.size - 2, tam = 36.dp, descripcion = "Tramo siguiente")
    }
    SelectorCurva(k.curva, k.bezier, k.velocidad) { cu, b, v, arrastre ->
        val g: (com.royermethod.app.editor.modelo.Proyecto) -> com.royermethod.app.editor.modelo.Proyecto =
            { Edicion.cambiarClave(it, c.id, ini + tk, tolerancia) { x -> x.copy(curva = cu, bezier = b, velocidad = v) } }
        if (arrastre) estado.ajustar(g) else estado.hacer(g)
    }
    if (tiempos.size >= 3) {
        BotonTexto("Usar en todos los tramos", {
            estado.hacer { Edicion.curvaEnClaves(it, c.id, null, k.curva, k.bezier, k.velocidad) }
            estado.avisar("Curva puesta en los ${tiempos.size - 1} tramos")
        }, glifo = Glifos.duplicar)
    }
}

/**
 * Curva libre con dos puntos que se arrastran. El eje de abajo es el tiempo
 * y el vertical cuanto se ha llegado: mas inclinada, mas rapido.
 */
@Composable
internal fun EditorBezier(b: Bezier, onCambio: (Bezier) -> Unit) {
    val gestos = LocalGestos.current
    val actual by androidx.compose.runtime.rememberUpdatedState(b)
    val cambio by androidx.compose.runtime.rememberUpdatedState(onCambio)
    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1.6f)
            .border(1.dp, Ink.White15, esquinas(12.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val abajo = awaitFirstDown()
                    val m = 18.dp.toPx()
                    val w = size.width - 2 * m
                    val h = size.height - 2 * m
                    fun aPx(x: Float, y: Float) = Offset(m + x * w, m + h * (1f - (y - BEZIER_MIN) / (BEZIER_MAX - BEZIER_MIN)))
                    val p1 = aPx(actual.x1, actual.y1)
                    val p2 = aPx(actual.x2, actual.y2)
                    val d1 = (abajo.position - p1).getDistance()
                    val d2 = (abajo.position - p2).getDistance()
                    // Lejos de los dos puntos no se coge nada: asi el dedo sobre la
                    // curva sigue desplazando el panel (antes la caja se lo comia)
                    if (minOf(d1, d2) > 44.dp.toPx()) return@awaitEachGesture
                    abajo.consume()
                    val primero = d1 <= d2
                    gestos.empezar()
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == abajo.id } ?: break
                        if (!ch.pressed) break
                        val x = ((ch.position.x - m) / w).coerceIn(0f, 1f)
                        val y = (1f - (ch.position.y - m) / h) * (BEZIER_MAX - BEZIER_MIN) + BEZIER_MIN
                        val yy = y.coerceIn(BEZIER_MIN, BEZIER_MAX)
                        cambio(if (primero) actual.copy(x1 = x, y1 = yy) else actual.copy(x2 = x, y2 = yy))
                        ch.consume()
                    }
                    gestos.terminar()
                }
            }
    ) {
        val m = 18.dp.toPx()
        val w = size.width - 2 * m
        val h = size.height - 2 * m
        fun aPx(x: Float, y: Float) = Offset(m + x * w, m + h * (1f - (y - BEZIER_MIN) / (BEZIER_MAX - BEZIER_MIN)))
        val cero = aPx(0f, 0f)
        val uno = aPx(1f, 1f)
        drawLine(Ink.White15, aPx(0f, 0f), aPx(1f, 0f), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
        drawLine(Ink.White15, aPx(0f, 1f), aPx(1f, 1f), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
        val p1 = aPx(b.x1, b.y1)
        val p2 = aPx(b.x2, b.y2)
        drawLine(Ink.White30, cero, p1, 1.5.dp.toPx())
        drawLine(Ink.White30, uno, p2, 1.5.dp.toPx())
        val path = Path()
        for (i in 0..96) {
            val x = i / 96f
            val o = aPx(x, b.en(x))
            if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
        }
        drawPath(path, Ink.Glow35, style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
        drawPath(path, Ink.White, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(Ink.White, 4.dp.toPx(), cero)
        drawCircle(Ink.White, 4.dp.toPx(), uno)
        for (pt in listOf(p1, p2)) {
            drawCircle(Ink.Glow35, 14.dp.toPx(), pt)
            drawCircle(Ink.White, 8.dp.toPx(), pt)
            drawCircle(Ink.Black, 3.dp.toPx(), pt)
        }
    }
}

/** Lo que deja subir y bajar los puntos de la curva a mano: lo justo para rebotes y anticipaciones fuertes. */
private const val BEZIER_MIN = -0.7f
private const val BEZIER_MAX = 1.7f

/**
 * Elegir la curva de velocidad de una clave, como en CapCut: una tira que se
 * desliza a la derecha con el dibujo de la velocidad de cada curva (primero
 * Personalizar y las tuyas, luego las de velocidad, las de siempre y las
 * Bezier). Debajo, la de ahora: con puntos de velocidad se mueven, se
 * agregan y se quitan ahi mismo. [poner] recibe la curva con nombre, la
 * Bezier, los puntos de velocidad y si es parte de un arrastre.
 */
@Composable
internal fun SelectorCurva(
    curva: Curva,
    bezier: Bezier?,
    velocidad: CurvaVelocidad?,
    poner: (Curva, Bezier?, CurvaVelocidad?, Boolean) -> Unit
) {
    val mias = AjustesEditor.curvasMias
    fun esMia(cm: CurvaMia) = if (cm.velocidad != null) velocidad == cm.velocidad else velocidad == null && bezier == cm.bezier
    val deVelocidad = PlantillasVelocidad.TODAS.firstOrNull { it.curva == velocidad }
    val mia = mias.firstOrNull { esMia(it) }
    val propia = velocidad != null && deVelocidad == null && mia == null
    val f: (Float) -> Float = when {
        velocidad != null -> ({ x: Float -> velocidad.en(x) })
        bezier != null -> ({ x: Float -> bezier.en(x) })
        else -> ({ x: Float -> Escena.curva(curva, x) })
    }
    val nombre = deVelocidad?.nombre ?: mia?.nombre ?: PlantillasCurva.nombreDe(bezier)
        ?: if (velocidad != null || bezier != null) "Personalizada" else curva.nombre
    Tira(Modifier.padding(top = 2.dp), hueco = 8.dp) {
        CasillaCurva("Personalizar", propia, Unit, null, {
            // Parte de la curva de ahora, ya con sus puntos para moverlos
            if (!propia) poner(curva, null, velocidad ?: CurvaVelocidad.desde(f), false)
        })
        for (cm in mias) {
            val g: (Float) -> Float = cm.velocidad?.let { v -> { x: Float -> v.en(x) } } ?: cm.bezier!!.let { b -> { x: Float -> b.en(x) } }
            CasillaCurva(cm.nombre, esMia(cm), cm, g, { poner(curva, cm.bezier, cm.velocidad, false) })
        }
        for (pl in PlantillasVelocidad.TODAS) {
            CasillaCurva(pl.nombre, velocidad == pl.curva, pl.curva, { pl.curva.en(it) }, { poner(curva, null, pl.curva, false) })
        }
        for (cu in Curva.values()) {
            CasillaCurva(cu.nombre, velocidad == null && bezier == null && curva == cu, cu, { Escena.curva(cu, it) }, { poner(cu, null, null, false) })
        }
        for (pl in PlantillasCurva.TODAS) {
            CasillaCurva(pl.nombre, velocidad == null && bezier == pl.bezier, pl.bezier, { pl.bezier.en(it) }, { poner(curva, pl.bezier, null, false) })
        }
    }
    Hueco(8)
    // La curva de ahora, con su nombre encima: con puntos de velocidad se editan ahi mismo
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(nombre, style = Letra.chica.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium), color = Ink.White70, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (velocidad != null || bezier != null) {
            if (mia == null) {
                BotonTexto("Guardar en mis curvas", {
                    AjustesEditor.curvasMias = mias + CurvaMia(AjustesEditor.nombreNuevo("Curva", mias.map { it.nombre }), if (velocidad == null) bezier else null, velocidad)
                    AjustesEditor.alCambiar()
                }, glifo = Glifos.mas)
            } else {
                BotonTexto("Quitar de mis curvas", {
                    AjustesEditor.curvasMias = mias.filter { it !== mia }
                    AjustesEditor.alCambiar()
                }, glifo = Glifos.papelera)
            }
        }
    }
    if (velocidad != null) {
        EditorVelocidad(velocidad) { nv, arrastre -> poner(curva, null, nv, arrastre) }
    } else {
        GraficaVelocidad(f)
    }
}

/**
 * Una casilla de la tira de curvas: el dibujo de la velocidad (arriba, mas
 * rapido) y el nombre. [clave] solo sirve para no recalcular el dibujo en
 * cada recomposicion. Sin [f] es la de Personalizar.
 */
@Composable
private fun CasillaCurva(nombre: String, elegida: Boolean, clave: Any, f: ((Float) -> Float)?, onClick: () -> Unit) {
    val grafica = remember(clave) { f?.let { PlantillasCurva.grafica(it, 48) } }
    Column(
        Modifier
            .width(70.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(esquinas(10.dp))
                .background(if (elegida) Ink.White15 else Ink.White08)
                .border(1.5.dp, if (elegida) Ink.White else Color.Transparent, esquinas(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize().padding(horizontal = 7.dp, vertical = 8.dp)) {
                val col = if (elegida) Ink.White else Ink.White70
                if (grafica == null) {
                    // Personalizar: una curva con sus puntos
                    val pts = listOf(0f to 0.25f, 0.35f to 0.85f, 0.7f to 0.35f, 1f to 0.7f)
                    val cv = CurvaVelocidad(pts.map { PuntoVelocidad(it.first, it.second * 4f) })
                    val path = Path()
                    for (i in 0..40) {
                        val x = i / 40f
                        val o = Offset(x * size.width, size.height * (1f - cv.velocidadCruda(x) / 4f))
                        if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
                    }
                    drawPath(path, col, style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round))
                    for ((x, v) in pts) drawCircle(col, 2.6.dp.toPx(), Offset(x * size.width, size.height * (1f - v)))
                } else {
                    dibujoVelocidad(grafica.first, grafica.second, col, if (elegida) Ink.Glow35 else Ink.White08)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            nombre,
            style = Letra.mini,
            color = if (elegida) Ink.White else Ink.White70,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** La grafica de velocidad [v] (con su [pico]) en todo el sitio: linea y relleno. */
private fun DrawScope.dibujoVelocidad(v: FloatArray, pico: Float, linea: Color, relleno: Color) {
    val atras = v.any { it < -1e-3f }
    val base = if (atras) size.height * 0.7f else size.height
    val alto = if (atras) size.height * 0.7f else size.height
    fun aPx(i: Int) = Offset(size.width * i / (v.size - 1), base - alto * (v[i] / pico).coerceIn(-0.43f, 1f))
    val l = Path()
    val a = Path()
    a.moveTo(0f, base)
    for (i in v.indices) {
        val o = aPx(i)
        if (i == 0) l.moveTo(o.x, o.y) else l.lineTo(o.x, o.y)
        a.lineTo(o.x, o.y)
    }
    a.lineTo(size.width, base)
    a.close()
    drawPath(a, relleno)
    drawPath(l, linea, style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round))
}

/**
 * La velocidad de la curva en el tiempo: arriba es mas rapido, la linea de
 * puntos es ir siempre igual (una lineal). Lo que baja de cero va hacia atras
 * (la anticipacion, el rebote que vuelve).
 */
@Composable
private fun GraficaVelocidad(f: (Float) -> Float) {
    val (v, pico) = PlantillasCurva.grafica(f)
    val atras = v.any { it < -1e-3f }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(76.dp)
            .border(1.dp, Ink.White15, esquinas(12.dp))
            .padding(10.dp)
    ) {
        val base = if (atras) size.height * 0.7f else size.height
        val alto = if (atras) size.height * 0.7f else size.height
        drawLine(Ink.White15, Offset(0f, base), Offset(size.width, base), 1.dp.toPx())
        val uno = base - alto / pico
        drawLine(Ink.White30, Offset(0f, uno), Offset(size.width, uno), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
        dibujoVelocidad(v, pico, Ink.White, Ink.Glow35)
    }
}

/**
 * Curva de velocidad a mano: arriba es mas rapido. Los puntos se arrastran
 * (los de los extremos solo arriba y abajo), tocar donde no hay punto agrega
 * uno, y el elegido se quita con el boton. Solo se coge un punto si el dedo
 * cae cerca: si no, el panel se sigue desplazando.
 */
@Composable
private fun EditorVelocidad(cv: CurvaVelocidad, onCambio: (CurvaVelocidad, Boolean) -> Unit) {
    val gestos = LocalGestos.current
    val actual by androidx.compose.runtime.rememberUpdatedState(cv)
    val cambio by androidx.compose.runtime.rememberUpdatedState(onCambio)
    var elegido by remember { mutableStateOf(-1) }
    val tope = CurvaVelocidad.MAXIMA
    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1.9f)
            .border(1.dp, Ink.White15, esquinas(12.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val abajo = awaitFirstDown()
                    val m = 16.dp.toPx()
                    val w = size.width - 2 * m
                    val h = size.height - 2 * m
                    fun aPx(p: PuntoVelocidad) = Offset(m + p.x * w, m + h * (1f - p.v / tope))
                    fun xDe(px: Float) = ((px - m) / w).coerceIn(0f, 1f)
                    fun vDe(py: Float) = ((1f - (py - m) / h) * tope).coerceIn(0f, tope)
                    val pts = actual.puntos
                    val cerca = pts.indices.minByOrNull { (aPx(pts[it]) - abajo.position).getDistance() }
                    val agarra = cerca != null && (aPx(pts[cerca]) - abajo.position).getDistance() <= 32.dp.toPx()
                    if (agarra && cerca != null) {
                        abajo.consume()
                        elegido = cerca
                        val i = cerca
                        gestos.empezar()
                        try {
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull { it.id == abajo.id } ?: break
                                if (!ch.pressed) break
                                val ps = actual.puntos
                                if (i !in ps.indices) break
                                val sep = CurvaVelocidad.SEPARACION
                                val x = when (i) {
                                    0 -> 0f
                                    ps.lastIndex -> 1f
                                    else -> xDe(ch.position.x).coerceIn(ps[i - 1].x + sep * 1.01f, ps[i + 1].x - sep * 1.01f)
                                }
                                val nuevo = ps.toMutableList().also { it[i] = PuntoVelocidad(x, vDe(ch.position.y)) }
                                cambio(CurvaVelocidad(nuevo), true)
                                ch.consume()
                            }
                        } finally {
                            gestos.terminar()
                        }
                        return@awaitEachGesture
                    }
                    // Lejos de los puntos: si el dedo se suelta sin moverse es un
                    // toque y agrega un punto; si se mueve, desplaza el panel
                    val slop = viewConfiguration.touchSlop
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == abajo.id } ?: return@awaitEachGesture
                        if (ch.isConsumed || (ch.position - abajo.position).getDistance() > slop) return@awaitEachGesture
                        if (!ch.pressed) break
                    }
                    val x = xDe(abajo.position.x)
                    val ps = actual.puntos
                    if (ps.any { abs(it.x - x) < CurvaVelocidad.SEPARACION * 1.01f }) return@awaitEachGesture
                    val nuevo = (ps + PuntoVelocidad(x, vDe(abajo.position.y))).sortedBy { it.x }
                    elegido = nuevo.indexOfFirst { it.x == x }
                    cambio(CurvaVelocidad(nuevo), false)
                }
            }
    ) {
        val m = 16.dp.toPx()
        val w = size.width - 2 * m
        val h = size.height - 2 * m
        fun aPx(x: Float, v: Float) = Offset(m + x * w, m + h * (1f - v / tope))
        // La velocidad pareja (lo que seria ir siempre igual): la media
        val media = (0..64).sumOf { cv.velocidadCruda(it / 64f).toDouble() }.toFloat() / 65f
        if (media > 1e-3f) drawLine(Ink.White30, aPx(0f, media), aPx(1f, media), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
        drawLine(Ink.White15, aPx(0f, 0f), aPx(1f, 0f), 1.dp.toPx())
        val linea = Path()
        val area = Path()
        area.moveTo(aPx(0f, 0f).x, aPx(0f, 0f).y)
        for (i in 0..120) {
            val x = i / 120f
            val o = aPx(x, cv.velocidadCruda(x))
            if (i == 0) linea.moveTo(o.x, o.y) else linea.lineTo(o.x, o.y)
            area.lineTo(o.x, o.y)
        }
        area.lineTo(aPx(1f, 0f).x, aPx(1f, 0f).y)
        area.close()
        drawPath(area, Ink.Glow35)
        drawPath(linea, Ink.White, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
        cv.puntos.forEachIndexed { i, p ->
            val o = aPx(p.x, p.v)
            if (i == elegido) {
                drawCircle(Ink.Glow35, 13.dp.toPx(), o)
                drawCircle(Ink.White, 7.dp.toPx(), o)
                drawCircle(Ink.Black, 2.5.dp.toPx(), o)
            } else {
                drawCircle(Ink.Black, 7.dp.toPx(), o)
                drawCircle(Ink.White, 5.5.dp.toPx(), o)
            }
        }
    }
    Hueco(4)
    val p = cv.puntos.getOrNull(elegido)
    val media = remember(cv) { (0..64).sumOf { cv.velocidadCruda(it / 64f).toDouble() }.toFloat() / 65f }
    // Una sola linea debajo: lo que vale el punto elegido o, sin ninguno, como se usa
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Nota(
            if (p != null && media > 1e-3f) "%d %% del tramo · %.1f× la velocidad pareja".format((p.x * 100).roundToInt(), p.v / media)
            else "Arrastra los puntos · toca un hueco para añadir otro",
            Modifier.weight(1f)
        )
        val quitable = p != null && elegido in 1 until cv.puntos.lastIndex
        if (quitable) BotonTexto("Quitar", {
            onCambio(CurvaVelocidad(cv.puntos.filterIndexed { i, _ -> i != elegido }), false)
            elegido = -1
        }, glifo = Glifos.menos)
        BotonTexto("Pareja", {
            onCambio(CurvaVelocidad(listOf(PuntoVelocidad(0f, 1f), PuntoVelocidad(1f, 1f))), false)
            elegido = -1
        }, glifo = Glifos.reiniciar)
    }
}

/** Para los paneles que no tienen nada que ensenar con lo elegido. */
@Composable
fun SinNada(texto: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 30.dp), contentAlignment = Alignment.Center) {
        Text(texto, style = Letra.cuerpo, color = Ink.White50)
    }
}

internal fun maxDuracion(c: Clip, desde: Long = 100_000L): Float = max(desde, min(c.duracionUs, 5_000_000L)) / 1_000_000f
