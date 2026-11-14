package com.royermethod.app.editor.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.royermethod.app.editor.modelo.Clip
import com.royermethod.app.editor.modelo.Edicion
import com.royermethod.app.editor.modelo.Filtro
import com.royermethod.app.editor.modelo.GrupoPropiedad
import com.royermethod.app.editor.modelo.Linea
import com.royermethod.app.editor.modelo.Medidor
import com.royermethod.app.editor.modelo.Origen
import com.royermethod.app.ui.components.BotonIcono
import com.royermethod.app.ui.components.Glifos
import com.royermethod.app.ui.components.Herramienta
import com.royermethod.app.ui.components.Icono
import com.royermethod.app.ui.components.Letra
import com.royermethod.app.ui.components.LocalDenso
import com.royermethod.app.ui.components.LocalGestos
import com.royermethod.app.ui.components.esquinas
import com.royermethod.app.ui.components.reloj
import com.royermethod.app.ui.components.segundos
import com.royermethod.app.ui.theme.Ink
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Altos fijos de la pantalla: de ellos sale cuanto queda para la imagen. */
private val ALTO_BARRA = 52.dp
private val ALTO_TRANSPORTE = 48.dp
private val ALTO_HERRAMIENTAS = 62.dp
/** Con menos alto que este la linea de tiempo no ensena ni la pista principal: mejor nada. */
private val LINEA_MINIMA = 92.dp

/**
 * El editor.
 *
 * Arriba la imagen, en medio la linea de tiempo con el cabezal fijo y abajo
 * las herramientas, como en CapCut: es la colocacion que todo el mundo ya
 * tiene aprendida. Lo propio de esta app es que hay muy poco a la vez:
 *
 *  - Sin nada elegido, abajo solo salen las cosas del proyecto (agregar,
 *    texto, sonido, formato).
 *  - Con un bloque elegido salen solo las suyas, por grupos.
 *  - Cada herramienta abre una hoja que ocupa el sitio de la linea de tiempo,
 *    nunca el de la imagen: abrir un panel no mueve ni encoge lo que se ve.
 *    Las hojas de dos o tres controles son cortas y dejan la linea a la vista.
 *  - Lo que cambia como responden los dedos sobre la imagen (Zoom, Mover
 *    todo) no es una herramienta mas de la fila: va junto al reloj, donde
 *    sigue a mano con un panel abierto.
 *
 * Esto no sabe nada de Android: lo que dibuja la imagen, las miniaturas y
 * el elegir archivos llegan de fuera. Asi se puede dibujar y probar tambien
 * fuera del telefono.
 */
@Composable
fun PantallaEditor(
    estado: EstadoEditor,
    recursos: Recursos,
    medidor: Medidor,
    exportar: EstadoExportar,
    onVolver: () -> Unit,
    onAgregarPrincipal: () -> Unit,
    onAgregarCapa: () -> Unit,
    onAgregarSonido: () -> Unit,
    onExportar: () -> Unit,
    onCancelarExportar: () -> Unit,
    onCompartir: () -> Unit,
    onCerrarExportar: () -> Unit,
    onAbrirProyecto: () -> Unit = {},
    onGuardarProyecto: () -> Unit = {},
    /** Agrega los ejemplos de movimiento (mitades, circulo y anillo, latigazo, barrido). */
    onEjemplos: () -> Unit = {},
    /** Elegir otro archivo para todos los bloques que usan este. */
    onReemplazar: (Origen) -> Unit = {},
    modifier: Modifier = Modifier,
    vista: @Composable (Modifier) -> Unit
) {
    var exportando by remember { mutableStateOf(false) }
    // Si hay una exportacion en marcha o recien hecha, se ensena
    LaunchedEffect(exportar.corriendo, exportar.resultado, exportar.fallo) {
        if (exportar.corriendo || exportar.resultado != null || exportar.fallo != null) exportando = true
    }

    CompositionLocalProvider(LocalGestos provides estado, LocalRecursosEditor provides recursos, LocalDenso provides true) {
        BoxWithConstraints(modifier.fillMaxSize().background(Ink.Black)) {
            val panel = estado.panel
            // Toda hoja abre del alto de la linea de tiempo: la imagen se queda
            // donde estaba. Ampliarla es cosa del asa y dura lo que la hoja.
            var ampliado by remember(panel, exportando) { mutableStateOf(false) }
            val vistaMinima = if (maxHeight < 560.dp) 96.dp else 160.dp
            val maximoPanel = (maxHeight - ALTO_BARRA - ALTO_TRANSPORTE - vistaMinima).coerceAtLeast(80.dp)
            val compacto = min(maxHeight * 0.42f, 330.dp).coerceAtMost(maximoPanel)
            val amplio = (maxHeight * 0.62f).coerceAtMost(maximoPanel).coerceAtLeast(compacto)
            val abajo = if ((panel != null || exportando) && ampliado) amplio else compacto
            val tamanoPanel = if (amplio > compacto + 4.dp)
                TamanoPanelEditor(ampliado) { ampliado = !ampliado } else null
            val estrecho = maxWidth < 356.dp
            Column(Modifier.fillMaxSize()) {
                BarraArriba(estado, onVolver, onExportar = { exportando = true })
                Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Lienzo(estado, medidor, Modifier.fillMaxSize(), vista)
                    Aviso(estado, Modifier.align(Alignment.TopCenter).padding(top = 10.dp))
                }
                Transporte(estado, estrecho)
                Column(Modifier.fillMaxWidth().height(abajo)) {
                    // La linea de tiempo se queda con lo que no ocupe la fila de
                    // herramientas o la hoja: con una hoja corta se sigue viendo (y
                    // usando); con una entera no queda sitio y sale de la pantalla
                    BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                        if (maxHeight >= LINEA_MINIMA) {
                            LineaTiempo(estado, recursos, onAgregarPrincipal, onAgregarSonido, Modifier.fillMaxSize())
                            // El zoom, abajo a la derecha: donde no tapa la pista
                            // principal ni el "+ Agregar sonido"
                            if (maxHeight >= 150.dp)
                                ControlesZoom(estado, Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 6.dp))
                        }
                    }
                    AnimatedContent(
                        targetState = when {
                            exportando -> "exportar"
                            panel != null -> panel.name
                            else -> ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                        transitionSpec = {
                            (fadeIn(tween(200, delayMillis = 40)) + slideInVertically(tween(240)) { it / 10 })
                                .togetherWith(fadeOut(tween(120)))
                                .using(SizeTransform(clip = true) { _, _ -> tween(220) })
                        },
                        // La hoja crece desde abajo, donde estaba la fila de herramientas
                        contentAlignment = Alignment.BottomCenter,
                        label = "abajo"
                    ) { que ->
                        when {
                            que == "exportar" -> PanelExportar(
                                estado, exportar, onExportar, onCancelarExportar, onCompartir,
                                onCerrar = {
                                    exportando = false
                                    if (!exportar.corriendo) onCerrarExportar()
                                }
                            )
                            que.isNotEmpty() -> {
                                val p = Panel.valueOf(que)
                                val cerrar = {
                                    estado.panel = null
                                    if (estado.seleccion is Seleccion.Paso) estado.elegir(null)
                                }
                                CompositionLocalProvider(LocalTamanoPanelEditor provides tamanoPanel) {
                                    when (p) {
                                        Panel.ANIMACION -> HojaAnimacion(estado, cerrar)
                                        Panel.EFECTOS -> HojaEfectos(estado, cerrar)
                                        Panel.TEXTO -> HojaTexto(estado, cerrar)
                                        Panel.MARCO -> HojaMarco(estado, cerrar)
                                        Panel.COLOR -> HojaColor(estado, cerrar)
                                        Panel.RECORTE -> HojaRecorte(estado, cerrar)
                                        Panel.LUCES -> HojaLuces(estado, cerrar)
                                        Panel.CAPAS -> HojaCapas(estado, cerrar)
                                        Panel.ENCUADRE -> HojaEncuadre(estado, cerrar)
                                        Panel.PROYECTO -> HojaProyecto(estado, cerrar, onAbrirProyecto, onGuardarProyecto, onEjemplos)
                                        else -> {
                                            val dato = datoDe(estado, p)
                                            HojaPanel(
                                                p.titulo, onCerrar = cerrar, corta = p in HOJAS_CORTAS,
                                                acciones = if (dato != null) ({ DatoHoja(dato) }) else null
                                            ) { ContenidoPanel(estado, p) }
                                        }
                                    }
                                }
                            }
                            else -> Herramientas(estado, onAgregarPrincipal, onAgregarCapa, onAgregarSonido, onReemplazar)
                        }
                    }
                }
            }
        }
    }
}

/** Las hojas de dos o tres controles: miden lo que su contenido y dejan ver la linea de tiempo. */
private val HOJAS_CORTAS = setOf(Panel.VELOCIDAD, Panel.VOLUMEN, Panel.FORMATO, Panel.FLUIDEZ, Panel.COMPRESION, Panel.RESOLUCION)

/** Un dato corto para la barra de la hoja: lo que dura, lo que mide, a cuantos fps va. */
private fun datoDe(estado: EstadoEditor, p: Panel): String? = when (p) {
    Panel.VELOCIDAD -> estado.clipElegido?.let { "Dura ${segundos(it.duracionUs)}" }
    Panel.FLUIDEZ -> fpsElegido(estado)
    Panel.RESOLUCION -> medidasSalida(estado.proyecto)
    else -> null
}

@Composable
private fun ContenidoPanel(estado: EstadoEditor, p: Panel) {
    when (p) {
        Panel.VELOCIDAD -> PanelVelocidad(estado)
        Panel.VOLUMEN -> PanelVolumen(estado)
        Panel.PIEZAS -> PanelPiezas(estado)
        Panel.TRANSICION -> PanelTransicion(estado)
        Panel.FORMATO -> PanelFormato(estado)
        Panel.CURVA -> PanelCurva(estado)
        Panel.FLUIDEZ -> PanelFluidez(estado)
        Panel.COMPRESION -> PanelCompresion(estado)
        Panel.RESOLUCION -> PanelResolucion(estado)
        // Van con su propia hoja (acciones en la barra y zona fija)
        Panel.ANIMACION, Panel.EFECTOS, Panel.TEXTO, Panel.COLOR, Panel.MARCO, Panel.RECORTE, Panel.LUCES,
        Panel.CAPAS, Panel.ENCUADRE, Panel.PROYECTO -> Unit
    }
}

/**
 * Arriba solo lo del proyecto entero: salir, ver u ocultar las guias, la
 * resolucion con que sale y exportar. El formato vive en la barra de abajo
 * (y se ve en la propia forma de la imagen).
 */
@Composable
private fun BarraArriba(estado: EstadoEditor, onVolver: () -> Unit, onExportar: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(ALTO_BARRA).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        BotonIcono(Glifos.atras, onVolver, tam = 44.dp, descripcion = "Volver")
        Spacer(Modifier.weight(1f))
        val marcos = AjustesEditor.mostrarMarcos
        BotonIcono(
            if (marcos) Glifos.ojo else Glifos.ojoCerrado,
            {
                AjustesEditor.mostrarMarcos = !marcos
                AjustesEditor.alCambiar()
                estado.avisar(if (marcos) "Guías y marcos ocultos" else "Guías y marcos a la vista")
            },
            color = if (marcos) Ink.White70 else Ink.White50, tam = 44.dp,
            descripcion = if (marcos) "Ocultar guías y marcos" else "Mostrar guías y marcos"
        )
        // La resolucion con que sale: junto a Exportar, que es donde importa
        Row(
            Modifier
                .heightIn(min = 44.dp)
                .clip(esquinas(12.dp))
                .clickable { estado.elegir(null); estado.panel = Panel.RESOLUCION }
                .semantics { contentDescription = "Resolución ${estado.proyecto.salida.ladoCorto}p" }
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("${estado.proyecto.salida.ladoCorto}p", style = Letra.cuerpo.copy(fontWeight = FontWeight.Medium),
                color = Ink.White70, maxLines = 1, softWrap = false)
            Spacer(Modifier.width(3.dp))
            Icono(Glifos.siguiente, Modifier.rotate(90f), color = Ink.White50, tam = 11.dp)
        }
        Spacer(Modifier.width(2.dp))
        Row(
            Modifier
                .heightIn(min = 36.dp)
                .clip(esquinas(18.dp))
                .background(Ink.White)
                .clickable(onClick = onExportar)
                .semantics { contentDescription = "Exportar video" }
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icono(Glifos.exportar, color = Ink.Black, tam = 15.dp)
            Spacer(Modifier.width(6.dp))
            Text("Exportar", style = Letra.cuerpo.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
                color = Ink.Black, maxLines = 1, softWrap = false)
        }
        Spacer(Modifier.width(4.dp))
    }
}

/**
 * El reloj a la izquierda, reproducir en el centro y, a la derecha, las claves
 * y deshacer. Junto al reloj va el Zoom de la vista y, con un bloque que tiene
 * claves, Mover todo: son modos de los dedos sobre la imagen y asi siguen a
 * mano con cualquier panel abierto. Los dos lados pesan lo mismo, de modo que
 * reproducir queda en el centro aunque aparezcan o no botones.
 */
@Composable
private fun Transporte(estado: EstadoEditor, estrecho: Boolean) {
    val cabezal by remember(estado) { derivedStateOf { reloj(estado.tiempo, milesimas = estado.zoomFino && !estado.reproduciendo) } }
    val duracion = reloj(estado.duracion)
    val tam = if (estrecho) 34.dp else 38.dp
    Row(
        Modifier.fillMaxWidth().height(ALTO_TRANSPORTE).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.padding(start = 6.dp, end = 4.dp).clearAndSetSemantics {
                contentDescription = "Tiempo $cabezal, duración $duracion"
            }) {
                Text(cabezal, style = Letra.cuerpo.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
                    color = Ink.White, maxLines = 1, softWrap = false)
                Text("/ $duracion", style = Letra.mini.copy(letterSpacing = 0.sp),
                    color = Ink.White50, maxLines = 1, softWrap = false)
            }
            BotonZoomVista(estado, tam)
        }
        BotonIcono(if (estado.reproduciendo) Glifos.pausa else Glifos.play,
            { estado.reproduciendo = !estado.reproduciendo }, activo = estado.duracion > 0,
            color = Ink.White, tam = 44.dp, descripcion = if (estado.reproduciendo) "Pausar" else "Reproducir")
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
            BotonMoverTodo(estado, tam)
            BotonClave(estado, tam)
            BotonIcono(Glifos.deshacer, { estado.deshacer() }, activo = estado.puedeDeshacer,
                tam = tam, descripcion = "Deshacer")
            BotonIcono(Glifos.rehacer, { estado.rehacer() }, activo = estado.puedeRehacer,
                tam = tam, descripcion = "Rehacer")
        }
    }
}

/**
 * Zoom de la vista: encendido, los dedos sobre la imagen solo acercan y
 * desplazan (no eligen ni mueven nada). Al apagarlo el zoom se queda y se
 * edita con el; se quita desde la etiqueta que sale sobre la imagen.
 */
@Composable
private fun BotonZoomVista(estado: EstadoEditor, tam: Dp) {
    BotonIcono(Glifos.zoom, {
        estado.modoZoom = !estado.modoZoom
        estado.avisar(
            if (estado.modoZoom) "Pellizca o toca dos veces la imagen para acercar. Toca la lupa otra vez para editar"
            else if (estado.zoomVista.puesto) "El zoom se queda: ya puedes editar con él"
            else "Zoom apagado"
        )
    }, tam = tam, encendido = estado.modoZoom, descripcion = if (estado.modoZoom) "Apagar zoom de la vista" else "Zoom de la vista")
}

/**
 * Mover todo: encendido, arrastrar en la imagen mueve el bloque entero con
 * todas sus claves (o solo las elegidas); otro toque lo apaga. Solo sale con
 * un bloque que tiene claves, que es cuando cambia algo.
 */
@Composable
private fun BotonMoverTodo(estado: EstadoEditor, tam: Dp) {
    val c = (estado.seleccion as? Seleccion.Bloque)?.let { estado.clipElegido } ?: return
    if (c.claves.isEmpty()) return
    BotonIcono(Glifos.moverTodo, {
        estado.moverTodo = !estado.moverTodo
        val elegidas = estado.elegidasDe(c).size
        estado.avisar(
            if (!estado.moverTodo) "Mover todo apagado: arrastrar cambia solo la clave de aquí"
            else if (elegidas > 0) "Mover todo: arrastrar mueve tus $elegidas claves elegidas. Toca otra vez para apagarlo"
            else "Mover todo: arrastrar mueve el clip entero con sus claves. Toca otra vez para apagarlo"
        )
    }, tam = tam, encendido = estado.moverTodo, descripcion = if (estado.moverTodo) "Apagar mover todo" else "Mover todo")
}

/**
 * El rombo de fotograma clave. Con un bloque elegido y el cabezal dentro:
 * si ya hay uno ahi lo quita, si no lo pone con el encuadre que se ve.
 */
@Composable
private fun BotonClave(estado: EstadoEditor, tam: Dp) {
    if (estado.panel == Panel.LUCES) {
        BotonIcono(Glifos.rombo, { ponerClaveLuz(estado, false) }, activo = estado.localElegido != null && estado.clipElegido?.luces?.isNotEmpty() == true,
            tam = tam, descripcion = "Añadir clave de luz")
        return
    }
    val c = (estado.seleccion as? Seleccion.Bloque)?.let { estado.clipElegido }
    // Sin el ajuste a fotogramas, "el mismo instante" es 0,0002 s: claves a una milesima son distintas
    if (c != null && (c.esTexto || c.esAjuste || estado.panel == Panel.MARCO || estado.panel == Panel.COLOR || estado.panel == Panel.RECORTE)) {
        val grupo = when {
            estado.panel == Panel.RECORTE -> GrupoPropiedad.RECORTE
            c.esAjuste || estado.panel == Panel.COLOR -> GrupoPropiedad.AJUSTES
            estado.panel == Panel.MARCO -> GrupoPropiedad.MARCO
            else -> GrupoPropiedad.TEXTO
        }
        BotonIcono(Glifos.rombo, { cambiarClavePropiedades(estado, grupo, c.esTexto && estado.panel != Panel.RECORTE, false) }, activo = estado.localElegido != null,
            tam = tam, descripcion = "Añadir fotograma clave")
        return
    }
    val tolerancia = toleranciaClaves()
    // El cabezal se lee en derivedStateOf: al reproducir solo se recompone si cambia lo que se ve
    val activo by remember(c) { derivedStateOf { c != null && estado.localElegido != null } }
    val hay by remember(c, tolerancia) {
        derivedStateOf { val l = estado.localElegido; c != null && l != null && Edicion.claveCercana(c.claves, l, tolerancia) != null }
    }
    BotonIcono(
        if (hay) Glifos.romboLleno else Glifos.rombo,
        {
            val clip = c ?: return@BotonIcono
            val l = estado.localElegido ?: return@BotonIcono
            if (hay) {
                estado.hacer { Edicion.quitarClave(it, clip.id, estado.tiempo, tolerancia) }
            } else {
                estado.hacer { Edicion.ponerClave(it, clip.id, estado.tiempo, clip.encuadreEn(l), tolerancia) }
                if (clip.claves.isEmpty()) estado.avisar("Mueve el cabezal y cambia el bloque: se crea otro fotograma clave")
            }
        },
        activo = activo, tam = tam,
        descripcion = if (hay) "Quitar clave de movimiento" else "Añadir clave de movimiento"
    )
}

/**
 * El bloque que se ve bajo el cabezal: la capa de mas arriba y si no la
 * pista principal (en una transicion, el que entra). Con [sinTextos] se
 * salta las capas de texto, para lo que solo tiene sentido en una imagen.
 */
private fun bloqueBajoCabezal(estado: EstadoEditor, sinTextos: Boolean = false): Clip? {
    val p = estado.proyecto
    val en = Linea.principalEn(p.principal, Linea.inicios(p.principal), estado.tiempo)
    return Linea.capasEn(p.capas, estado.tiempo)
        .lastOrNull { !it.clip.esAjuste && (!sinTextos || !it.clip.esTexto) }?.clip
        ?: en?.segundo ?: en?.primero
}

/** Raya fina entre dos grupos de herramientas. */
@Composable
private fun Separador() {
    Box(Modifier.padding(horizontal = 5.dp).width(1.dp).height(26.dp).background(Ink.White15))
}

/**
 * La fila de herramientas: cambia segun lo elegido y va por grupos, separados
 * por una raya. Primero lo de todos los dias; lo de vez en cuando, al final.
 * Con algo elegido, la flecha de volver se queda fija a la izquierda.
 */
@Composable
private fun Herramientas(
    estado: EstadoEditor,
    onAgregarPrincipal: () -> Unit,
    onAgregarCapa: () -> Unit,
    onAgregarSonido: () -> Unit,
    onReemplazar: (Origen) -> Unit
) {
    val s = estado.seleccion
    val desliza = rememberScrollState()
    LaunchedEffect(s) { desliza.scrollTo(0) }
    Column(Modifier.fillMaxWidth().background(Ink.Black)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.White08))
        Row(Modifier.fillMaxWidth().height(ALTO_HERRAMIENTAS), verticalAlignment = Alignment.CenterVertically) {
            if (s != null) {
                Box(
                    Modifier
                        .width(42.dp)
                        .fillMaxHeight()
                        .clickable { estado.elegir(null) }
                        .semantics { contentDescription = "Volver a las herramientas del proyecto" },
                    contentAlignment = Alignment.Center
                ) { Icono(Glifos.atras, color = Ink.White70, tam = 20.dp) }
                Box(Modifier.width(1.dp).height(30.dp).background(Ink.White15))
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                Row(
                    Modifier
                        .fillMaxSize()
                        .horizontalScroll(desliza)
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when (s) {
                        null -> HerramientasProyecto(estado, onAgregarPrincipal, onAgregarCapa, onAgregarSonido)
                        is Seleccion.Sonido -> HerramientasSonido(estado, s.id)
                        else -> {
                            val c = estado.clipElegido
                            if (c == null) Unit
                            else if (c.esAjuste) HerramientasAjuste(estado, c)
                            else HerramientasBloque(estado, c, onReemplazar)
                        }
                    }
                }
                MasALaDerecha(desliza, Modifier.align(Alignment.CenterEnd))
            }
        }
    }
}

/** Sin nada elegido: agregar cosas, lo que afecta a todo y el proyecto. */
@Composable
private fun RowScope.HerramientasProyecto(
    estado: EstadoEditor,
    onAgregarPrincipal: () -> Unit,
    onAgregarCapa: () -> Unit,
    onAgregarSonido: () -> Unit
) {
    Herramienta(Glifos.mas, "Agregar", onAgregarPrincipal)
    if (bloqueBajoCabezal(estado) != null) Herramienta(Glifos.editar, "Editar", {
        bloqueBajoCabezal(estado)?.let { estado.elegir(Seleccion.Bloque(it.id)) }
    })
    Herramienta(Glifos.superponer, "Capa", onAgregarCapa)
    Herramienta(Glifos.texto, "Texto", {
        var nuevo = 0L
        estado.hacer { p -> Edicion.agregarTexto(p, TEXTO_NUEVO, estado.tiempo).also { nuevo = it.second }.first }
        estado.elegir(Seleccion.Bloque(nuevo))
        estado.panel = Panel.TEXTO
    })
    Herramienta(Glifos.audio, "Sonido", onAgregarSonido)
    Separador()
    Herramienta(Glifos.estrella, "Efectos", { crearCapaAjuste(estado, efectos = true) })
    Herramienta(Glifos.ajustes, "Ajustes", { crearCapaAjuste(estado) })
    Herramienta(Glifos.formato, "Formato", { estado.panel = Panel.FORMATO })
    if (hayCapasQueOrdenar(estado)) Herramienta(Glifos.capas, "Orden", { estado.panel = Panel.CAPAS })
    Separador()
    // Abrir, guardar, ejemplos y borrar todo: juntos en una hoja
    Herramienta(Glifos.carpeta, "Proyecto", { estado.panel = Panel.PROYECTO })
}

@Composable
private fun RowScope.HerramientasSonido(estado: EstadoEditor, id: Long) {
    Herramienta(Glifos.dividir, "Dividir", {
        val r = Edicion.dividir(estado.proyecto, id, estado.tiempo)
        if (r == null) estado.avisar("Pon el cabezal dentro del sonido para dividirlo") else estado.hacer { r.first }
    })
    Herramienta(Glifos.volumen, "Volumen", { estado.panel = Panel.VOLUMEN })
    Herramienta(Glifos.duplicar, "Duplicar", { Edicion.duplicar(estado.proyecto, id)?.let { r -> estado.hacer { r.first } } })
    Herramienta(Glifos.papelera, "Eliminar", { estado.hacer { Edicion.eliminar(it, id) }; estado.elegir(null) })
}

/** Un tramo de ajustes o efectos compartidos. */
@Composable
private fun RowScope.HerramientasAjuste(estado: EstadoEditor, c: Clip) {
    val id = c.id
    Herramienta(Glifos.estrella, "Efectos", { estado.panel = Panel.EFECTOS }, resaltada = c.efectos.isNotEmpty())
    Herramienta(Glifos.ajustes, "Ajustes", { estado.panel = Panel.COLOR })
    Separador()
    Herramienta(Glifos.dividir, "Dividir", { Edicion.dividir(estado.proyecto, id, estado.tiempo)?.let { r -> estado.hacer { r.first } } })
    Herramienta(Glifos.duplicar, "Duplicar", { Edicion.duplicar(estado.proyecto, id)?.let { r -> estado.hacer { r.first } } })
    Herramienta(Glifos.papelera, "Eliminar", { estado.hacer { Edicion.eliminar(it, id) }; estado.elegir(null) })
}

/**
 * Un video, una foto o un texto. Cuatro grupos: lo basico (cortar, tiempo,
 * animar, color, efectos, quitar), la forma y el aspecto, el sitio que ocupa
 * entre los demas y, al final, lo de Mejorar (fotogramas y peso).
 */
@Composable
private fun RowScope.HerramientasBloque(estado: EstadoEditor, c: Clip, onReemplazar: (Origen) -> Unit) {
    val id = c.id
    val principal = estado.proyecto.principal
    val i = principal.indexOfFirst { it.id == id }
    // Algunas hojas se abren con el cabezal dentro del bloque y en pausa
    fun abrirEnBloque(p: Panel) {
        val inicio = estado.inicioElegido ?: 0L
        estado.pausarEn(estado.tiempo.coerceIn(inicio, inicio + c.duracionUs - 1))
        estado.panel = p
    }

    Herramienta(Glifos.dividir, "Dividir", {
        val r = Edicion.dividir(estado.proyecto, id, estado.tiempo)
        if (r == null) estado.avisar("Pon el cabezal dentro del bloque, lejos de sus bordes") else estado.hacer { r.first }
    })
    if (c.esTexto) Herramienta(Glifos.texto, "Texto", { estado.panel = Panel.TEXTO })
    if (c.esVideo) Herramienta(Glifos.velocidad, "Velocidad", { estado.panel = Panel.VELOCIDAD })
    Herramienta(Glifos.animacion, "Animar", { estado.panel = Panel.ANIMACION })
    Herramienta(Glifos.estrella, "Efectos", {
        if (c.efectos.isNotEmpty()) estado.panel = Panel.EFECTOS
        else crearCapaAjuste(estado, efectos = true, desdeElegido = true)
    }, resaltada = c.efectos.isNotEmpty())
    if (!c.esTexto) {
        Herramienta(Glifos.ajustes, "Ajustes", { crearCapaAjuste(estado, desdeElegido = true) })
        if (!c.color.neutro || c.filtro != Filtro.NINGUNO || c.propiedades.keys.any { it.grupo == GrupoPropiedad.AJUSTES })
            Herramienta(Glifos.color, "Ajuste local", { estado.panel = Panel.COLOR }, resaltada = true)
    }
    Herramienta(Glifos.papelera, "Eliminar", { estado.hacer { Edicion.eliminar(it, id) }; estado.elegir(null) })

    Separador()
    Herramienta(Glifos.curva, "Curvas", { estado.panel = Panel.CURVA }, resaltada = c.claves.size >= 2)
    Herramienta(Glifos.transformar, "Encuadre", { estado.panel = Panel.ENCUADRE })
    Herramienta(Glifos.recorte, "Recorte", { abrirEnBloque(Panel.RECORTE) }, resaltada = c.recorteVisual.activo)
    Herramienta(Glifos.borde, "Borde", { estado.panel = Panel.MARCO }, resaltada = c.marco.activo)
    Herramienta(Glifos.luz, "Luces", { abrirEnBloque(Panel.LUCES) }, resaltada = c.luces.isNotEmpty())
    Herramienta(Glifos.partes, "Partes", { estado.panel = Panel.PIEZAS }, resaltada = c.piezas.isNotEmpty())
    if (c.esVideo) Herramienta(Glifos.volumen, "Volumen", { estado.panel = Panel.VOLUMEN })

    Separador()
    if (i >= 0 && i < principal.lastIndex) Herramienta(Glifos.transicion, "Transición", { estado.elegir(Seleccion.Paso(id)) })
    if (hayCapasQueOrdenar(estado)) Herramienta(Glifos.capas, "Orden", { estado.panel = Panel.CAPAS })
    if (i >= 0) Herramienta(Glifos.aCapa, "A capa", { estado.hacer { Edicion.aCapa(it, id) } })
    else if (!c.esTexto) Herramienta(Glifos.aPrincipal, "A principal", { estado.hacer { Edicion.aPrincipal(it, id) } })
    Herramienta(Glifos.duplicar, "Duplicar", { Edicion.duplicar(estado.proyecto, id)?.let { r -> estado.hacer { r.first } } })
    // Otra version del mismo video: se cambia en todos los bloques que lo usan
    c.origen?.let { o -> Herramienta(Glifos.reemplazar, "Reemplazar", { onReemplazar(o) }) }

    Separador()
    // Lo de la pantalla de Mejorar: fotogramas y peso de este bloque
    if (c.esVideo) Herramienta(Glifos.fluidez, "Fluidez", { estado.panel = Panel.FLUIDEZ }, resaltada = c.fluido)
    Herramienta(Glifos.compresion, "Compresión", { estado.panel = Panel.COMPRESION }, resaltada = c.calidad != 1f)
}

/** Solo hay orden que cambiar si hay algo encima de la pista principal. */
private fun hayCapasQueOrdenar(estado: EstadoEditor): Boolean = estado.proyecto.capas.any { !it.clip.esAjuste }

/**
 * Pista de que la fila de herramientas sigue a la derecha: un difuminado con
 * una flecha que avanza una pagina, para no saltarse herramientas. Se oculta
 * al llegar al final.
 */
@Composable
internal fun MasALaDerecha(desliza: ScrollState, modifier: Modifier) {
    val alcance = rememberCoroutineScope()
    val hayMas by remember(desliza) { derivedStateOf { desliza.value < desliza.maxValue } }
    if (!hayMas) return
    val pagina = with(LocalDensity.current) { 240.dp.roundToPx() }
    Box(
        modifier
            .width(34.dp)
            .fillMaxHeight()
            .background(Brush.horizontalGradient(listOf(Color.Transparent, Ink.Black.copy(alpha = 0.94f))))
            .clickable {
                alcance.launch {
                    desliza.animateScrollTo((desliza.value + pagina).coerceAtMost(desliza.maxValue))
                }
            }
            .semantics { contentDescription = "Ver más herramientas" },
        contentAlignment = Alignment.CenterEnd
    ) {
        Icono(Glifos.siguiente, Modifier.padding(end = 4.dp), color = Ink.White70, tam = 16.dp)
    }
}

/** Mensaje corto sobre la imagen que se va solo. */
@Composable
private fun Aviso(estado: EstadoEditor, modifier: Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(estado.avisoN) {
        if (estado.avisoN == 0) return@LaunchedEffect
        visible = true
        // Los largos (varias lineas) se quedan mas para poder leerlos
        delay(if ((estado.aviso?.length ?: 0) > 60) 4500L else 2600L)
        visible = false
    }
    AnimatedVisibility(visible, modifier, enter = fadeIn(tween(160)) + slideInVertically { -it / 2 }, exit = fadeOut(tween(300)) + slideOutVertically { -it / 3 }) {
        Text(
            estado.aviso ?: "",
            style = Letra.cuerpo,
            color = Ink.Black,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .clip(esquinas(16.dp))
                .background(Ink.White)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}
