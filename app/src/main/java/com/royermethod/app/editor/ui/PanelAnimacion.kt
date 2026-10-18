package com.royermethod.app.editor.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.royermethod.app.editor.modelo.Animacion
import com.royermethod.app.editor.modelo.AnimacionAplicada
import com.royermethod.app.editor.modelo.AnimacionContinua
import com.royermethod.app.editor.modelo.Clip
import com.royermethod.app.editor.modelo.Combinaciones
import com.royermethod.app.editor.modelo.Continuo
import com.royermethod.app.editor.modelo.Curva
import com.royermethod.app.editor.modelo.Linea
import com.royermethod.app.editor.modelo.MomentoAnimacion
import com.royermethod.app.editor.modelo.Movimiento
import com.royermethod.app.editor.modelo.TiempoPreciso
import com.royermethod.app.editor.modelo.limitacionesConversion
import com.royermethod.app.ui.components.BotonBorde
import com.royermethod.app.ui.components.BotonIcono
import com.royermethod.app.ui.components.BotonLleno
import com.royermethod.app.ui.components.Chip
import com.royermethod.app.ui.components.Cuadricula
import com.royermethod.app.ui.components.Deslizador
import com.royermethod.app.ui.components.Escala
import com.royermethod.app.ui.components.Etiqueta
import com.royermethod.app.ui.components.Glifos
import com.royermethod.app.ui.components.Icono
import com.royermethod.app.ui.components.Nota
import com.royermethod.app.ui.components.Opciones
import com.royermethod.app.ui.components.esquinas
import com.royermethod.app.ui.theme.Estilo
import com.royermethod.app.ui.theme.Ink
import kotlin.math.min
import kotlin.math.roundToLong

/*
 * Panel "Animar".
 *
 * Fila del titulo: "Animar", y a la derecha "Animaciones (n) ▾" (las puestas
 * en el bloque, en un desplegable para elegir, ajustar, quitar o convertir en
 * efecto) y "+" (tocar una del catalogo la suma a las que ya hay).
 * Zona fija, que no se desliza: pestanas Entrada/Salida/Siempre y la
 * duracion de la elegida. Debajo, lo que se desliza: el catalogo por
 * categorias (con una fila para ensenar solo una; las puestas con borde y
 * etiqueta) o los ajustes de la elegida.
 * Lo que hace cada toque esta en LogicaAnimacion.
 */

/** Alto de la fila del titulo de HojaPanel (raya + margen + boton): el desplegable sale debajo. */
private val ALTO_TITULO = 47.dp

private val MOMENTOS = MomentoAnimacion.values().toList()

/** Lo que el panel recuerda mientras esta abierto. No es del proyecto: no se deshace. */
@Stable
internal class VistaAnimacion(
    pestana: MomentoAnimacion,
    elegidaId: Long? = null,
    agregando: Boolean = false,
    ajustes: Boolean = false
) {
    var pestana by mutableStateOf(pestana)
    /** La elegida; si ya no existe o es de otra pestana manda [LogicaAnimacion.elegida]. */
    var elegidaId by mutableStateOf(elegidaId)
    /** Modo "+": tocar una del catalogo la suma. */
    var agregando by mutableStateOf(agregando)
    /** Bajo la zona fija, los ajustes de la elegida en vez del catalogo. */
    var ajustes by mutableStateOf(ajustes)
    var menu by mutableStateOf(false)
    /** La que se va a convertir en efecto: con esto el dialogo esta abierto. */
    var convertir by mutableStateOf<Long?>(null)
    /** La categoria del catalogo que se ensena; null, todas. */
    var categoria by mutableStateOf<String?>(null)

    /**
     * El boton atras del sistema: cierra primero el desplegable y despues sale
     * de "+" o de los ajustes de la elegida ([hayElegida]: si no, no se ven), de
     * vuelta al catalogo. True si cerro algo; si no, se cierra la hoja. El
     * dialogo de convertir es una ventana y atiende su propio atras.
     */
    fun alAtras(hayElegida: Boolean): Boolean = when {
        menu -> { menu = false; true }
        agregando -> { agregando = false; true }
        ajustes && hayElegida -> { ajustes = false; true }
        else -> false
    }

    companion object {
        val Guardar: Saver<VistaAnimacion, Any> = listSaver(
            save = { listOf(it.pestana.ordinal, it.elegidaId ?: -1L, it.agregando, it.ajustes, it.categoria ?: "") },
            restore = {
                VistaAnimacion(
                    MOMENTOS[it[0] as Int], (it[1] as Long).takeIf { id -> id > 0 },
                    it[2] as Boolean, it[3] as Boolean
                ).apply { categoria = (it[4] as String).ifEmpty { null } }
            }
        )
    }
}

/** Por bloque: al elegir otro bloque se empieza de nuevo. */
@Composable
private fun recordarVista(c: Clip): VistaAnimacion =
    rememberSaveable(c.id, saver = VistaAnimacion.Guardar) {
        VistaAnimacion(LogicaAnimacion.momentoInicial(LogicaAnimacion.aplicadas(c)))
    }

/**
 * La hoja del panel de animacion: "Animar ... Animaciones (n) ▾  +" en la
 * fila del titulo, la duracion fija arriba y el catalogo (o los ajustes de
 * la elegida) debajo, deslizandose. El desplegable de las puestas sale por
 * encima, dentro de la hoja: no tapa la imagen.
 */
@Composable
fun HojaAnimacion(estado: EstadoEditor, onCerrar: () -> Unit) {
    val c = estado.clipElegido
    if (c == null) {
        HojaPanel(Panel.ANIMACION.titulo, onCerrar) {}
        return
    }
    val vista = recordarVista(c)
    val aplicadas = LogicaAnimacion.aplicadas(c)
    val elegida = LogicaAnimacion.elegida(aplicadas, vista.elegidaId, vista.pestana)
    val verAjustes = vista.ajustes && !vista.agregando && elegida != null
    // Atras cierra antes el desplegable o el modo de dentro que la hoja
    val hayElegida by rememberUpdatedState(elegida != null)
    DisposableEffect(estado, vista) {
        val quitar = estado.alAtras { vista.alAtras(hayElegida) }
        onDispose { quitar() }
    }
    Box(Modifier.fillMaxSize()) {
        // Otra pestana, o pasar del catalogo a los ajustes, empieza arriba del todo
        key(vista.pestana, verAjustes) {
            HojaPanel(
                Panel.ANIMACION.titulo, onCerrar,
                acciones = { AccionesTitulo(vista, aplicadas.size) },
                fijo = { ZonaFija(estado, c, vista, elegida) }
            ) {
                if (verAjustes) AjustesElegida(estado, c, elegida) { vista.convertir = elegida.id }
                else CatalogoAnimaciones(estado, vista, aplicadas, elegida)
            }
        }
        if (vista.menu) MenuAplicadas(estado, vista, aplicadas, elegida)
    }
    Convertir(estado, vista, aplicadas)
}

/**
 * Lo de dentro de la hoja sin la fila del titulo (sin desplegable ni "+"):
 * la zona fija y el catalogo seguidos. La hoja de verdad es [HojaAnimacion].
 */
@Composable
fun PanelAnimacion(estado: EstadoEditor) {
    val c = estado.clipElegido ?: return
    val vista = recordarVista(c)
    val aplicadas = LogicaAnimacion.aplicadas(c)
    val elegida = LogicaAnimacion.elegida(aplicadas, vista.elegidaId, vista.pestana)
    ZonaFija(estado, c, vista, elegida)
    if (vista.ajustes && !vista.agregando && elegida != null) AjustesElegida(estado, c, elegida) { vista.convertir = elegida.id }
    else CatalogoAnimaciones(estado, vista, aplicadas, elegida)
    Convertir(estado, vista, aplicadas)
}

/** El dialogo de "Convertir en efecto", si hay una pedida y sigue en el bloque. */
@Composable
private fun Convertir(estado: EstadoEditor, vista: VistaAnimacion, aplicadas: List<AnimacionAplicada>) {
    val id = vista.convertir ?: return
    val a = aplicadas.firstOrNull { it.id == id }
    if (a == null) {
        // Se fue (deshacer, quitada): que no vuelva a salir sola al rehacer
        SideEffect { vista.convertir = null }
        return
    }
    DialogoConvertir(
        a,
        onCancelar = { vista.convertir = null },
        onConvertir = {
            vista.convertir = null
            vista.menu = false
            if (LogicaAnimacion.convertir(estado, a.id) == null) estado.avisar("No se pudo convertir")
        }
    )
}

// ----------------------------------------------------------------------
// Fila del titulo
// ----------------------------------------------------------------------

@Composable
private fun RowScope.AccionesTitulo(vista: VistaAnimacion, n: Int) {
    SelectorAplicadas(n, vista.menu) { vista.menu = !vista.menu }
    Spacer(Modifier.width(6.dp))
    BotonSumar(vista.agregando) {
        vista.agregando = !vista.agregando
        vista.ajustes = false
        vista.menu = false
    }
    Spacer(Modifier.width(4.dp))
}

/** "Animaciones (2) ▾": pastilla de 40 dp que abre el desplegable de las puestas. */
@Composable
private fun SelectorAplicadas(n: Int, abierto: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .heightIn(min = 40.dp)
            .clip(esquinas(20.dp))
            .background(if (abierto) Ink.White15 else Ink.White08)
            .border(1.dp, if (abierto) Ink.White else Ink.White15, esquinas(20.dp))
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Animaciones ($n)",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = if (n > 0) Ink.White else Ink.White70,
            maxLines = 1
        )
        Spacer(Modifier.width(6.dp))
        // ▾ dibujado (no todas las fuentes del telefono traen el caracter); abierto, ▴
        Canvas(Modifier.size(10.dp)) {
            val p = Path().apply {
                if (abierto) { moveTo(0f, size.height * 0.75f); lineTo(size.width, size.height * 0.75f); lineTo(size.width / 2, size.height * 0.2f) }
                else { moveTo(0f, size.height * 0.25f); lineTo(size.width, size.height * 0.25f); lineTo(size.width / 2, size.height * 0.8f) }
                close()
            }
            drawPath(p, Ink.White90)
        }
    }
}

/** "+": encendido, el proximo toque en el catalogo suma otra. */
@Composable
private fun BotonSumar(activo: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (activo) Ink.White else Ink.White08)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icono(Glifos.mas, color = if (activo) Ink.Black else Ink.White, tam = 22.dp)
    }
}

// ----------------------------------------------------------------------
// Zona fija: pestanas y duracion
// ----------------------------------------------------------------------

@Composable
private fun ZonaFija(estado: EstadoEditor, c: Clip, vista: VistaAnimacion, elegida: AnimacionAplicada?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        PestanasMomento(vista.pestana, { vista.pestana = it }, Modifier.weight(1f))
        when {
            vista.agregando -> Chip("Cancelar", false, { vista.agregando = false })
            elegida != null -> Chip(if (vista.ajustes) "Catalogo" else "Ajustar", false, { vista.ajustes = !vista.ajustes })
        }
    }
    when {
        // Cada pestana tiene su elegida: aqui no hay ninguna puesta todavia
        elegida == null -> FilaFija("Duracion", "Ninguna en ${vista.pestana.nombre}: toca una")
        elegida.momento == MomentoAnimacion.SIEMPRE ->
            FilaFija("Duracion", "Todo el bloque (${TiempoPreciso.texto(c.duracionUs.coerceAtLeast(0))})")
        else -> key(elegida.id) {
            val (minimo, maximo) = LogicaAnimacion.limitesDuracion(c.duracionUs)
            val fotogramas = AjustesEditor.ajustarFotogramas
            // Una duracion: el ajuste a fotogramas se cuenta desde 0. No mueve el
            // cabezal. Con el ajuste, sin deslizador: en 330 dp el catalogo
            // necesita ese sitio y -/+ (un fotograma) y escribir el valor bastan.
            // Sin el ajuste CampoTiempo pone igualmente la fila del paso (1/10 ms):
            // ahi cabe el deslizador
            CampoTiempo(
                "Duracion · ${elegida.nombre}", elegida.duracionUs, minimo, maximo,
                onCambio = { us, arrastre -> LogicaAnimacion.cambiarDuracion(estado, elegida.id, us, arrastre) },
                fotogramas = fotogramas,
                deslizador = !fotogramas
            )
        }
    }
    Box(Modifier.fillMaxWidth().padding(top = 2.dp).height(1.dp).background(Ink.White08))
}

/**
 * Entrada / Salida / Siempre, como las Pestanas de siempre pero mas
 * estrechas y sin partir la palabra: con la pastilla de al lado caben en 360 dp.
 */
@Composable
private fun PestanasMomento(elegida: MomentoAnimacion, onElegir: (MomentoAnimacion) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        MOMENTOS.forEach { m ->
            val sel = m == elegida
            Column(
                Modifier
                    .heightIn(min = 40.dp)
                    .clip(esquinas(8.dp))
                    .clickable { onElegir(m) }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    m.nombre,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (sel) FontWeight.Medium else FontWeight.Normal),
                    color = if (sel) Ink.White else Ink.White50,
                    maxLines = 1,
                    softWrap = false
                )
                Spacer(Modifier.height(5.dp))
                Box(Modifier.size(if (sel) 18.dp else 0.dp, 2.dp).clip(CircleShape).background(Ink.White))
            }
        }
    }
}

/** Una fila de la zona fija sin control (del alto de los botones de CampoTiempo). */
@Composable
private fun FilaFija(titulo: String, texto: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(titulo, style = MaterialTheme.typography.bodyMedium, color = Ink.White70, modifier = Modifier.weight(1f))
        Nota(texto)
    }
}

// ----------------------------------------------------------------------
// Catalogo
// ----------------------------------------------------------------------

@Composable
private fun CatalogoAnimaciones(estado: EstadoEditor, vista: VistaAnimacion, aplicadas: List<AnimacionAplicada>, elegida: AnimacionAplicada?) {
    val momento = vista.pestana
    val mias = AjustesEditor.mias
    val continuasMias = AjustesEditor.continuasMias
    val todos = remember(momento, mias, continuasMias) { LogicaAnimacion.grupos(momento, mias, continuasMias) }
    // Una categoria que no hay en esta pestana (las de Siempre son otras) cuenta como todas
    val categoria = vista.categoria?.takeIf { cat -> todos.any { it.first == cat } }
    val grupos = remember(todos, categoria) { LogicaAnimacion.filtrar(todos, categoria) }
    // Las marcas solo cambian con lo puesto o la elegida, no con una duracion:
    // asi el catalogo no se vuelve a componer en cada paso de un arrastre
    val puestas = aplicadas.filter { it.momento == momento }.map { it.copy(duracionUs = 0, activa = true) }
    val marcas = remember(todos, puestas, elegida?.id) {
        val e = elegida?.let { el -> puestas.firstOrNull { it.id == el.id } }
        todos.flatMap { it.second }.associateWith { LogicaAnimacion.marca(it, puestas, e) }
    }
    val conPuestas = remember(todos, puestas) { LogicaAnimacion.categoriasConPuestas(todos, puestas) }
    val nombres = remember(todos) { todos.map { it.first } }
    FiltroCategorias(nombres, categoria, conPuestas) { vista.categoria = it }
    val tocar by rememberUpdatedState { o: LogicaAnimacion.Opcion ->
        val r = LogicaAnimacion.aplicar(estado, o, elegida?.id, vista.agregando)
        if (r != null) {
            vista.elegidaId = r.elegidaId
            vista.agregando = false
        }
    }
    val alTocar = remember { { o: LogicaAnimacion.Opcion -> tocar(o) } }
    Casillas(momento, grupos, marcas, vista.agregando, elegida != null, alTocar)
}

/**
 * Las categorias de siempre en una fila que se desliza de lado: tocar una
 * ensena solo esa (tocarla otra vez, o "Todas", las ensena todas). Un punto
 * marca las que tienen alguna puesta en el bloque.
 */
@Composable
private fun FiltroCategorias(nombres: List<String>, elegida: String?, conPuestas: Set<String>, onElegir: (String?) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        PastillaCategoria(LogicaAnimacion.TODAS, elegida == null, false) { onElegir(null) }
        nombres.forEach { n -> PastillaCategoria(n, n == elegida, n in conPuestas) { onElegir(if (n == elegida) null else n) } }
    }
}

/** Como un Chip, con el punto de "hay alguna puesta" al lado del nombre. */
@Composable
private fun PastillaCategoria(texto: String, elegida: Boolean, conPuestas: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .heightIn(min = 40.dp)
            .clip(esquinas(20.dp))
            .background(if (elegida) Ink.White else Ink.White08)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            texto,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (elegida) FontWeight.Medium else FontWeight.Normal),
            color = if (elegida) Ink.Black else Ink.White70,
            maxLines = 1,
            softWrap = false
        )
        if (conPuestas) {
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(6.dp).clip(CircleShape).background(if (elegida) Ink.Black else Ink.White))
        }
    }
}

@Composable
private fun Casillas(
    momento: MomentoAnimacion,
    grupos: List<Pair<String, List<LogicaAnimacion.Opcion>>>,
    marcas: Map<LogicaAnimacion.Opcion, LogicaAnimacion.Marca>,
    agregando: Boolean,
    hayElegida: Boolean,
    onTocar: (LogicaAnimacion.Opcion) -> Unit
) {
    Nota(
        when {
            agregando -> "Toca una para sumarla a las que ya hay"
            hayElegida -> "Tocar otra cambia la elegida. Con + se suma"
            else -> "Toca una para ponerla"
        },
        Modifier.padding(top = 6.dp),
        color = if (agregando) Ink.White else Ink.White50
    )
    grupos.forEach { (nombre, opciones) ->
        Etiqueta(nombre, Modifier.padding(top = 6.dp))
        Cuadricula(opciones) { o, mod ->
            Casilla(o.nombre, marcas[o] ?: LogicaAnimacion.Marca.DISPONIBLE, { onTocar(o) }, mod) { col ->
                if (momento == MomentoAnimacion.SIEMPRE) {
                    if (o.continuas.isNotEmpty()) dibujoContinua(o.continuas.first().tipo, col)
                    // "Zoom y flash" se distingue de "Zoom al ritmo" por los rayos
                    if (o.continuas.drop(1).any { it.tipo == AnimacionContinua.SHAKE_FLASH }) {
                        val (tl, s) = marco()
                        rayos(col, tl, s.width, s.height)
                    }
                } else if (o.piezas.isNotEmpty()) {
                    dibujoCombinacion(o.piezas, col, momento == MomentoAnimacion.SALIDA)
                }
            }
        }
    }
}

/**
 * Casilla del catalogo. Las puestas en el bloque llevan borde del acento y la
 * etiqueta PUESTA; la elegida, ademas, fondo y la etiqueta ELEGIDA rellena.
 */
@Composable
private fun Casilla(
    texto: String,
    marca: LogicaAnimacion.Marca,
    onClick: () -> Unit,
    modifier: Modifier,
    dibujo: DrawScope.(Color) -> Unit
) {
    val puesta = marca != LogicaAnimacion.Marca.DISPONIBLE
    val elegida = marca == LogicaAnimacion.Marca.ELEGIDA
    Column(
        modifier
            .width(72.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(62.dp)) {
            Box(
                Modifier
                    .size(62.dp)
                    .clip(esquinas(12.dp))
                    .background(if (elegida) Ink.White15 else Ink.White08)
                    .border(if (elegida) 2.5.dp else 1.5.dp, if (puesta) Ink.White else Color.Transparent, esquinas(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.size(38.dp)) { dibujo(if (puesta) Ink.White else Ink.White70) }
            }
            if (puesta) {
                Text(
                    if (elegida) "ELEGIDA" else "PUESTA",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp * Estilo.letra, letterSpacing = 0.4.sp, fontWeight = FontWeight.Medium),
                    color = if (elegida) Ink.Black else Ink.White,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = 6.dp)
                        .clip(esquinas(8.dp))
                        .background(if (elegida) Ink.White else Ink.Black)
                        .border(1.dp, Ink.White, esquinas(8.dp))
                        .padding(horizontal = 5.dp)
                )
            }
        }
        // El mismo hueco con etiqueta o sin ella: las filas no se descuadran
        Spacer(Modifier.height(12.dp))
        Text(
            texto,
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.2.sp, fontSize = 10.5.sp * Estilo.letra),
            color = if (puesta) Ink.White else Ink.White70,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

// ----------------------------------------------------------------------
// Desplegable de las puestas
// ----------------------------------------------------------------------

/**
 * Las animaciones del bloque, por encima de la hoja: tocar una la elige (y
 * ensena "Ajustar" y "Convertir en efecto"), la ✕ la quita. Tocar fuera lo
 * cierra.
 */
@Composable
private fun MenuAplicadas(estado: EstadoEditor, vista: VistaAnimacion, aplicadas: List<AnimacionAplicada>, elegida: AnimacionAplicada?) {
    Box(Modifier.fillMaxSize().padding(top = ALTO_TITULO)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Ink.Black.copy(alpha = 0.6f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { vista.menu = false }
        )
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(esquinas(16.dp))
                .background(Ink.Black)
                .background(Ink.White08)
                .border(1.dp, Ink.White15, esquinas(16.dp))
                .verticalScroll(rememberScrollState())
                .padding(vertical = 4.dp)
        ) {
            if (aplicadas.isEmpty()) {
                Nota("Todavia no hay ninguna en este bloque. Toca una del catalogo o +", Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
            }
            aplicadas.forEach { a ->
                FilaAplicada(
                    a, a.id == elegida?.id,
                    onElegir = { vista.elegidaId = a.id; vista.pestana = a.momento },
                    onAjustar = { vista.elegidaId = a.id; vista.pestana = a.momento; vista.agregando = false; vista.ajustes = true; vista.menu = false },
                    onQuitar = { LogicaAnimacion.quitar(estado, a.id) },
                    onConvertir = { vista.convertir = a.id }
                )
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.White08))
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable { vista.menu = false; vista.ajustes = false; vista.agregando = true }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icono(Glifos.mas, color = Ink.White90, tam = 18.dp)
                Spacer(Modifier.width(10.dp))
                Text("Sumar otra", style = MaterialTheme.typography.bodyMedium, color = Ink.White90)
            }
        }
    }
}

@Composable
private fun FilaAplicada(
    a: AnimacionAplicada,
    elegida: Boolean,
    onElegir: () -> Unit,
    onAjustar: () -> Unit,
    onQuitar: () -> Unit,
    onConvertir: () -> Unit
) {
    Column(Modifier.fillMaxWidth().background(if (elegida) Ink.White08 else Color.Transparent)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clickable(onClick = onElegir)
                .padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // La elegida, con el punto relleno
            Box(
                Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (elegida) Ink.White else Color.Transparent)
                    .border(1.5.dp, if (elegida) Ink.White else Ink.White30, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (elegida) Icono(Glifos.listo, color = Ink.Black, tam = 14.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    a.nombre,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = if (elegida) Ink.White else Ink.White90,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Nota(LogicaAnimacion.detalle(a))
            }
            BotonIcono(Glifos.cerrar, onQuitar, color = Ink.White50)
        }
        if (elegida) {
            Row(
                Modifier.fillMaxWidth().padding(start = 46.dp, end = 12.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Chip("Ajustar", false, onAjustar)
                val motivo = LogicaAnimacion.motivoNoConvertible(a)
                if (motivo == null) Chip("Convertir en efecto", false, onConvertir)
                else Nota(motivo, Modifier.weight(1f))
            }
        }
    }
}

/** Antes de convertir: lo que hace y lo que deja de ser igual despues. */
@Composable
private fun DialogoConvertir(a: AnimacionAplicada, onCancelar: () -> Unit, onConvertir: () -> Unit) {
    Dialog(onDismissRequest = onCancelar) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(esquinas(20.dp))
                .background(Ink.Black)
                .background(Ink.White08)
                .border(1.dp, Ink.White15, esquinas(20.dp))
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Text("Convertir en efecto", style = MaterialTheme.typography.titleMedium, color = Ink.White)
            Spacer(Modifier.height(8.dp))
            Text(
                "\"${a.nombre}\" pasa a ser un efecto con su propio tramo, que se mueve y se recorta en Efectos. " +
                    "Ahora se ve igual y no quedan dos copias. Se puede deshacer.",
                style = MaterialTheme.typography.bodyMedium,
                color = Ink.White90
            )
            Etiqueta("Ten en cuenta", Modifier.padding(top = 6.dp))
            limitacionesConversion(a).forEach { t ->
                Row(Modifier.padding(vertical = 2.dp)) {
                    Nota("•", Modifier.width(14.dp), color = Ink.White70)
                    Nota(t, Modifier.weight(1f), color = Ink.White70)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                BotonBorde("Cancelar", onCancelar)
                BotonLleno("Convertir", onConvertir)
            }
        }
    }
}

// ----------------------------------------------------------------------
// Ajustes de la elegida
// ----------------------------------------------------------------------

@Composable
private fun AjustesElegida(estado: EstadoEditor, c: Clip, a: AnimacionAplicada, onConvertir: () -> Unit) {
    // El nombre, el ajuste a fotogramas (de la duracion y de los tramos) y quitar,
    // en una fila: lo que dura ya se ve arriba
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            a.nombre,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = Ink.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        InterruptorFotogramas()
        // Quitar: un paso de deshacer, como la ✕ del desplegable
        BotonIcono(Glifos.papelera, { LogicaAnimacion.quitar(estado, a.id) }, color = Ink.White70)
    }
    if (a.momento == MomentoAnimacion.SIEMPRE) {
        PiezasContinuas(estado, a)
    } else {
        if (a.momento == MomentoAnimacion.SALIDA) Nota("En la salida, Empieza y Termina se cuentan hacia atras desde el final del bloque.")
        Piezas(
            a.id,
            a.piezas,
            duracionUs = a.duracionUs.coerceIn(0, c.duracionUs.coerceAtLeast(0)),
            // En la entrada los tramos son instantes desde el comienzo del bloque: el
            // ajuste a fotogramas se cuenta desde donde empieza en la linea
            origenUs = if (a.momento == MomentoAnimacion.ENTRADA) estado.inicioElegido ?: 0L else 0L,
            onCambio = { l, arrastre -> LogicaAnimacion.cambiarPiezas(estado, a.id, l, arrastre) },
            guardada = (Combinaciones.entrada + AjustesEditor.mias).any { it.piezas == a.piezas },
            onGuardar = {
                val nombre = AjustesEditor.nombreNuevo("Mia", AjustesEditor.mias.map { it.nombre })
                AjustesEditor.mias = AjustesEditor.mias + Combinaciones.Combinacion(nombre, a.piezas)
                AjustesEditor.alCambiar()
                estado.avisar("Guardada como \"$nombre\"")
            }
        )
    }
    // Lo mismo que en el desplegable: solo si se puede, y si no, por que
    Spacer(Modifier.height(10.dp))
    val motivo = LogicaAnimacion.motivoNoConvertible(a)
    if (motivo == null) BotonBorde("Convertir en efecto", onConvertir)
    else Nota(motivo)
}

/**
 * Las piezas de la elegida: cada una se abre para cambiar cuanto se mueve,
 * con que curva y en que parte de la duracion actua; se quita con la ✕ y se
 * suman otras. Quitar la ultima quita la animacion.
 */
@Composable
private fun Piezas(
    idAnimacion: Long,
    piezas: List<Movimiento>,
    duracionUs: Long,
    origenUs: Long,
    onCambio: (List<Movimiento>, Boolean) -> Unit,
    guardada: Boolean,
    onGuardar: () -> Unit
) {
    var abierta by remember(idAnimacion) { mutableIntStateOf(-1) }
    var agregando by remember(idAnimacion) { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Etiqueta("Piezas de esta animacion", Modifier.weight(1f))
        if (!guardada) BotonGuardar(onGuardar)
    }
    piezas.forEachIndexed { i, m ->
        fun cambiar(n: Movimiento, arrastre: Boolean = true) = onCambio(piezas.toMutableList().also { it[i] = n }, arrastre)
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp)
                .background(Ink.White04, esquinas(12.dp))
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .clickable { abierta = if (abierta == i) -1 else i }
                    .padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icono({ col -> dibujoPieza(m.tipo, col, false) }, tam = 22.dp, color = Ink.White90)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(m.tipo.nombre, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = Ink.White90)
                    Text(
                        "${(m.intensidad * 100).toInt()}%  ·  ${m.curva.nombre}" +
                            if (m.desde > 0f || m.hasta < 1f) "  ·  ${TiempoPreciso.texto(tramo(m.desde, duracionUs))}–${TiempoPreciso.texto(tramo(m.hasta, duracionUs))}" else "",
                        style = MaterialTheme.typography.labelSmall, color = Ink.White50
                    )
                }
                BotonIcono(Glifos.cerrar, { onCambio(piezas.filterIndexed { j, _ -> j != i }, false); abierta = -1 }, color = Ink.White50)
            }
            AnimatedVisibility(abierta == i) {
                Column(Modifier.padding(horizontal = 12.dp).padding(bottom = 8.dp)) {
                    Deslizador("Cuanto", m.intensidad, 0f, 3f, { v -> cambiar(m.copy(intensidad = v)) }, neutro = 1f, texto = { "${(it * 100).toInt()}%" })
                    Opciones(Curva.values().toList(), m.curva, { it.nombre }, { cu -> cambiar(m.copy(curva = cu), false) })
                    if (duracionUs > 0) {
                        val inicio = tramo(m.desde, duracionUs)
                        val fin = tramo(m.hasta, duracionUs).coerceAtLeast(inicio)
                        // El motor admite tramos desde 0.001 de la animacion; con
                        // el ajuste a fotogramas el limite practico es un fotograma
                        val minimo = if (AjustesEditor.ajustarFotogramas) Linea.tiempoDe(1) else 1L
                        val paso = min(duracionUs, maxOf(minimo, (duracionUs + 999) / 1000))
                        CampoTiempo("Empieza", inicio, 0, (fin - paso).coerceAtLeast(0), { us, arrastre ->
                            cambiar(m.copy(desde = us.toFloat() / duracionUs), arrastre)
                        }, origenUs = origenUs)
                        CampoTiempo("Termina", fin, (inicio + paso).coerceAtMost(duracionUs), duracionUs, { us, arrastre ->
                            cambiar(m.copy(hasta = us.toFloat() / duracionUs), arrastre)
                        }, origenUs = origenUs)
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(4.dp))
    BotonBorde(if (agregando) "Elige la pieza" else "Sumar otra pieza", { agregando = !agregando }, glifo = Glifos.mas)
    AnimatedVisibility(agregando) {
        Opciones(Animacion.values().toList(), null, { it.nombre }, { t ->
            onCambio(piezas + Movimiento(t), false)
            agregando = false
            abierta = piezas.size
        }, Modifier.padding(top = 6.dp))
    }
}

/** Una fraccion de la duracion en microsegundos, redondeada (no truncada). */
private fun tramo(fraccion: Float, duracionUs: Long): Long =
    (fraccion.toDouble() * duracionUs).roundToLong().coerceIn(0, duracionUs.coerceAtLeast(0))

/** Las piezas de una SIEMPRE: cuanto y con que rapidez; se quitan y se suman. */
@Composable
private fun PiezasContinuas(estado: EstadoEditor, a: AnimacionAplicada) {
    val piezas = a.continuas
    var agregando by remember(a.id) { mutableStateOf(false) }
    fun poner(l: List<Continuo>, arrastre: Boolean = false) = LogicaAnimacion.cambiarContinuas(estado, a.id, l, arrastre)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Etiqueta("Piezas", Modifier.weight(1f))
        if ((Combinaciones.continuas + AjustesEditor.continuasMias).none { it.second == piezas }) BotonGuardar {
            val nombre = AjustesEditor.nombreNuevo("Mia continua", AjustesEditor.continuasMias.map { it.first })
            AjustesEditor.continuasMias = AjustesEditor.continuasMias + (nombre to piezas)
            AjustesEditor.alCambiar()
            estado.avisar("Guardada como \"$nombre\"")
        }
    }
    piezas.forEachIndexed { i, k ->
        fun cambiar(n: Continuo) = poner(piezas.toMutableList().also { it[i] = n }, true)
        Column(Modifier.fillMaxWidth().padding(vertical = 3.dp).background(Ink.White04, esquinas(12.dp)).padding(horizontal = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(k.tipo.nombre, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = Ink.White90, modifier = Modifier.weight(1f))
                BotonIcono(Glifos.cerrar, { poner(piezas.filterIndexed { j, _ -> j != i }) }, color = Ink.White50)
            }
            Deslizador("Cuanto", k.intensidad, 0f, 3f, { v -> cambiar(k.copy(intensidad = v)) }, neutro = 1f, texto = { "${(it * 100).toInt()}%" })
            Deslizador("Rapidez", k.velocidad, 0.1f, 5f, { v -> cambiar(k.copy(velocidad = v)) }, neutro = 1f, escala = Escala.LOGARITMICA, texto = { "%.2gx".format(it) })
        }
    }
    Spacer(Modifier.height(4.dp))
    BotonBorde(if (agregando) "Elige la pieza" else "Sumar otra pieza", { agregando = !agregando }, glifo = Glifos.mas)
    AnimatedVisibility(agregando) {
        Opciones(AnimacionContinua.values().toList(), null, { it.nombre }, { t ->
            poner(piezas + Continuo(t)); agregando = false
        }, Modifier.padding(top = 6.dp))
    }
}

// ----------------------------------------------------------------------
// Dibujos de las casillas
// ----------------------------------------------------------------------

private fun DrawScope.ninguna(col: Color) {
    val r = size.minDimension * 0.32f
    drawCircle(col, r, center, style = Stroke(1.6.dp.toPx()))
    drawLine(col, Offset(center.x - r * 0.7f, center.y + r * 0.7f), Offset(center.x + r * 0.7f, center.y - r * 0.7f), 1.6.dp.toPx())
}

/** Un rectangulo (el bloque) y una pista de como se mueve. */
fun DrawScope.dibujoPieza(t: Animacion, col: Color, alReves: Boolean) {
    val w = size.width * 0.46f
    val h = size.height * 0.62f
    val c = center
    val tl = Offset(c.x - w / 2, c.y - h / 2)
    val trazo = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round)
    val discontinuo = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)))
    val radio = CornerRadius(3.dp.toPx())
    fun flecha(a: Offset, b: Offset) {
        val (o, d) = if (alReves) b to a else a to b
        drawLine(col, o, d, 1.6.dp.toPx(), StrokeCap.Round)
        val v = d - o
        val n = v / v.getDistance() * 4.dp.toPx()
        val p = Offset(-n.y, n.x) * 0.7f
        drawLine(col, d, d - n + p, 1.6.dp.toPx(), StrokeCap.Round)
        drawLine(col, d, d - n - p, 1.6.dp.toPx(), StrokeCap.Round)
    }
    when (t) {
        Animacion.APARECER -> {
            drawRoundRect(col.copy(alpha = 0.25f), tl, Size(w, h), radio)
            drawRoundRect(col, tl, Size(w, h), radio, style = trazo)
        }
        Animacion.ZOOM_ENTRA, Animacion.ZOOM_SALE -> {
            val grande = t == Animacion.ZOOM_ENTRA
            drawRoundRect(col, tl, Size(w, h), radio, style = trazo)
            val f = if (grande) 1.45f else 0.5f
            drawRoundRect(col, Offset(c.x - w * f / 2, c.y - h * f / 2), Size(w * f, h * f), radio, style = discontinuo)
        }
        Animacion.IZQUIERDA, Animacion.DERECHA, Animacion.ARRIBA, Animacion.ABAJO -> {
            drawRoundRect(col, tl, Size(w * 0.8f, h * 0.8f) , radio, style = trazo)
            val d = when (t) {
                Animacion.IZQUIERDA -> Offset(-1f, 0f); Animacion.DERECHA -> Offset(1f, 0f)
                Animacion.ARRIBA -> Offset(0f, -1f); else -> Offset(0f, 1f)
            }
            val dentro = Offset(c.x - w * 0.1f, c.y - h * 0.1f)
            flecha(dentro + d * (size.minDimension * 0.48f), dentro + d * (size.minDimension * 0.12f))
        }
        Animacion.GIRO -> rotate(-25f) {
            drawRoundRect(col, tl, Size(w, h), radio, style = trazo)
        }
        Animacion.REBOTE -> {
            drawRoundRect(col, Offset(tl.x, tl.y + h * 0.2f), Size(w, h * 0.8f), radio, style = trazo)
            drawLine(col, Offset(c.x - w * 0.6f, tl.y - 2), Offset(c.x + w * 0.6f, tl.y - 2), 1.dp.toPx())
        }
        Animacion.REVELAR -> {
            drawRoundRect(col, tl, Size(w * 0.55f, h), radio)
            drawRoundRect(col, tl, Size(w, h), radio, style = discontinuo)
        }
        Animacion.DESENFOCAR -> {
            for (k in 3 downTo 1) drawRoundRect(col.copy(alpha = 0.18f), Offset(tl.x - k * 2f, tl.y - k * 2f), Size(w + k * 4f, h + k * 4f), radio)
            drawRoundRect(col, tl, Size(w, h), radio, style = trazo)
        }
        Animacion.SACUDIR -> {
            drawRoundRect(col.copy(alpha = 0.4f), Offset(tl.x - 4.dp.toPx(), tl.y), Size(w, h), radio, style = trazo)
            drawRoundRect(col, Offset(tl.x + 3.dp.toPx(), tl.y), Size(w, h), radio, style = trazo)
        }
        Animacion.PARPADEO -> {
            drawRoundRect(col, tl, Size(w, h), radio, style = discontinuo)
            drawRoundRect(col.copy(alpha = 0.6f), Offset(tl.x + w * 0.25f, tl.y + h * 0.25f), Size(w * 0.5f, h * 0.5f), radio)
        }
        Animacion.SACUDIR_VERTICAL -> {
            drawRoundRect(col.copy(alpha = 0.4f), Offset(tl.x, tl.y - 4.dp.toPx()), Size(w, h), radio, style = trazo)
            drawRoundRect(col, Offset(tl.x, tl.y + 3.dp.toPx()), Size(w, h), radio, style = trazo)
        }
        Animacion.PENDULO -> {
            rotate(-14f, Offset(c.x, tl.y)) { drawRoundRect(col.copy(alpha = 0.35f), tl, Size(w, h), radio, style = trazo) }
            rotate(14f, Offset(c.x, tl.y)) { drawRoundRect(col, tl, Size(w, h), radio, style = trazo) }
        }
        Animacion.ZOOM_VIBRA -> {
            for (k in 0..2) {
                val f = 1f + k * 0.18f
                drawRoundRect(col.copy(alpha = 1f - k * 0.35f), Offset(c.x - w * f / 2, c.y - h * f / 2), Size(w * f, h * f), radio, style = Stroke(1.2.dp.toPx()))
            }
        }
        Animacion.GLITCH -> {
            drawRoundRect(col.copy(alpha = 0.35f), Offset(tl.x - 4.dp.toPx(), tl.y), Size(w, h * 0.4f), radio)
            drawRoundRect(col, Offset(tl.x + 3.dp.toPx(), tl.y + h * 0.4f), Size(w, h * 0.25f), radio)
            drawRoundRect(col.copy(alpha = 0.6f), Offset(tl.x - 1.dp.toPx(), tl.y + h * 0.65f), Size(w, h * 0.35f), radio)
        }
        Animacion.FLASH, Animacion.FLASH_NEGRO -> {
            if (t == Animacion.FLASH) drawRoundRect(col, tl, Size(w, h), radio)
            else {
                drawRoundRect(col.copy(alpha = 0.15f), tl, Size(w, h), radio)
                drawRoundRect(col, tl, Size(w, h), radio, style = trazo)
            }
            rayos(col.copy(alpha = if (t == Animacion.FLASH) 1f else 0.45f), tl, w, h)
        }
        Animacion.ESTROBO -> {
            val paso = w * 0.32f
            for (k in 0..2) {
                val o = Offset(tl.x - paso + k * paso, tl.y + (k - 1) * 2.dp.toPx())
                if (k % 2 == 0) drawRoundRect(col.copy(alpha = 0.5f + k * 0.25f), o, Size(w * 0.8f, h), radio)
                else drawRoundRect(col, o, Size(w * 0.8f, h), radio, style = trazo)
            }
        }
        Animacion.RGB -> {
            val d = 3.dp.toPx()
            drawRoundRect(ROJO, Offset(tl.x - d, tl.y), Size(w, h), radio, style = trazo)
            drawRoundRect(AZUL, Offset(tl.x + d, tl.y), Size(w, h), radio, style = trazo)
            drawRoundRect(col, tl, Size(w, h), radio, style = trazo)
        }
        Animacion.NEGATIVO -> {
            drawRoundRect(col, tl, Size(w, h), radio, style = trazo)
            val p = androidx.compose.ui.graphics.Path().apply {
                moveTo(tl.x, tl.y); lineTo(tl.x + w, tl.y); lineTo(tl.x, tl.y + h); close()
            }
            drawPath(p, col)
        }
        Animacion.CAMARA -> {
            rotate(-8f) { drawRoundRect(col.copy(alpha = 0.35f), Offset(tl.x - 3.dp.toPx(), tl.y - 2.dp.toPx()), Size(w, h), radio, style = trazo) }
            rotate(6f) { drawRoundRect(col, Offset(tl.x + 2.dp.toPx(), tl.y + 2.dp.toPx()), Size(w, h), radio, style = trazo) }
            val x = tl.x + w + 5.dp.toPx()
            for (k in 0..1) drawLine(col.copy(alpha = 0.6f), Offset(x + k * 3.dp.toPx(), c.y - h * 0.25f), Offset(x + k * 3.dp.toPx(), c.y + h * 0.25f), 1.2.dp.toPx(), StrokeCap.Round)
        }
        Animacion.TWITCH -> {
            drawRoundRect(AZUL, Offset(tl.x + 3.dp.toPx(), tl.y + h * 0.1f), Size(w * 0.8f, h * 0.8f), radio, style = Stroke(1.2.dp.toPx()))
            drawRoundRect(ROJO, Offset(tl.x - 4.dp.toPx(), tl.y - h * 0.05f), Size(w * 1.1f, h * 1.1f), radio, style = Stroke(1.2.dp.toPx()))
            drawRoundRect(col, Offset(tl.x - 1.dp.toPx(), tl.y), Size(w, h), radio, style = trazo)
        }
    }
}

/**
 * La casilla de una combinacion: el dibujo de su primera pieza y, si ademas
 * lleva flash, RGB o negativo, la marca de eso encima. Sin la marca "Shake
 * con flash" y "Shake de camara" se verian iguales.
 */
private fun DrawScope.dibujoCombinacion(piezas: List<Movimiento>, col: Color, alReves: Boolean) {
    val primera = piezas.first().tipo
    dibujoPieza(primera, col, alReves)
    val resto = piezas.drop(1).map { it.tipo }.toSet()
    val (tl, s) = marco()
    if (primera !in DESTELLOS && resto.any { it in DESTELLOS })
        rayos(col.copy(alpha = if (Animacion.FLASH_NEGRO in resto) 0.45f else 1f), tl, s.width, s.height)
    if (primera != Animacion.RGB && primera != Animacion.TWITCH && Animacion.RGB in resto) {
        val d = 3.dp.toPx()
        drawLine(ROJO, Offset(tl.x - d, tl.y + s.height * 0.2f), Offset(tl.x - d, tl.y + s.height * 0.8f), 1.4.dp.toPx(), StrokeCap.Round)
        drawLine(AZUL, Offset(tl.x + s.width + d, tl.y + s.height * 0.2f), Offset(tl.x + s.width + d, tl.y + s.height * 0.8f), 1.4.dp.toPx(), StrokeCap.Round)
    }
    if (primera != Animacion.NEGATIVO && Animacion.NEGATIVO in resto) {
        val p = androidx.compose.ui.graphics.Path().apply {
            moveTo(tl.x, tl.y); lineTo(tl.x + s.width * 0.6f, tl.y); lineTo(tl.x, tl.y + s.height * 0.6f); close()
        }
        drawPath(p, col.copy(alpha = 0.7f))
    }
}

/** Donde va el rectangulo del bloque en los dibujos: esquina y tamano. */
private fun DrawScope.marco(): Pair<Offset, Size> {
    val w = size.width * 0.46f
    val h = size.height * 0.62f
    return Offset(center.x - w / 2, center.y - h / 2) to Size(w, h)
}

private val DESTELLOS = setOf(Animacion.FLASH, Animacion.FLASH_NEGRO, Animacion.ESTROBO)
private val ROJO = Color(0xFFFF4D6D)
private val AZUL = Color(0xFF4DA6FF)

/** Cuatro rayitos saliendo de las esquinas: el destello. */
private fun DrawScope.rayos(col: Color, tl: Offset, w: Float, h: Float) {
    val l = 4.dp.toPx()
    val g = 2.dp.toPx()
    val esquinas = listOf(tl to Offset(-1f, -1f), Offset(tl.x + w, tl.y) to Offset(1f, -1f), Offset(tl.x, tl.y + h) to Offset(-1f, 1f), Offset(tl.x + w, tl.y + h) to Offset(1f, 1f))
    for ((p, d) in esquinas) drawLine(col, p + d * g, p + d * (g + l), 1.4.dp.toPx(), StrokeCap.Round)
}

private fun DrawScope.dibujoContinua(t: AnimacionContinua, col: Color) {
    val w = size.width * 0.46f
    val h = size.height * 0.62f
    val tl = Offset(center.x - w / 2, center.y - h / 2)
    val trazo = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round)
    val radio = CornerRadius(3.dp.toPx())
    when (t) {
        AnimacionContinua.ACERCAMIENTO, AnimacionContinua.ALEJAMIENTO, AnimacionContinua.PULSO -> {
            drawRoundRect(col, tl, Size(w, h), radio, style = trazo)
            val f = if (t == AnimacionContinua.ALEJAMIENTO) 0.7f else 1.25f
            drawRoundRect(col.copy(alpha = 0.4f), Offset(center.x - w * f / 2, center.y - h * f / 2), Size(w * f, h * f), radio, style = Stroke(1.dp.toPx()))
        }
        AnimacionContinua.TEMBLOR -> {
            drawRoundRect(col.copy(alpha = 0.4f), Offset(tl.x - 3.dp.toPx(), tl.y + 2.dp.toPx()), Size(w, h), radio, style = trazo)
            drawRoundRect(col, tl, Size(w, h), radio, style = trazo)
        }
        AnimacionContinua.BALANCEO -> {
            rotate(-12f) { drawRoundRect(col.copy(alpha = 0.4f), tl, Size(w, h), radio, style = trazo) }
            rotate(12f) { drawRoundRect(col, tl, Size(w, h), radio, style = trazo) }
        }
        AnimacionContinua.FLOTAR -> {
            drawRoundRect(col, Offset(tl.x, tl.y - 3.dp.toPx()), Size(w, h), radio, style = trazo)
            drawLine(col.copy(alpha = 0.5f), Offset(tl.x, tl.y + h + 4.dp.toPx()), Offset(tl.x + w, tl.y + h + 4.dp.toPx()), 1.dp.toPx())
        }
        AnimacionContinua.GIRAR -> {
            rotate(30f) { drawRoundRect(col, tl, Size(w, h), radio, style = trazo) }
            drawArc(col.copy(alpha = 0.6f), -60f, 120f, false, Offset(center.x - w * 0.9f, center.y - w * 0.9f), Size(w * 1.8f, w * 1.8f), style = Stroke(1.dp.toPx()))
        }
        AnimacionContinua.GLITCH -> {
            drawRoundRect(col.copy(alpha = 0.35f), Offset(tl.x - 4.dp.toPx(), tl.y), Size(w, h * 0.45f), radio)
            drawRoundRect(col, Offset(tl.x + 3.dp.toPx(), tl.y + h * 0.45f), Size(w, h * 0.55f), radio)
        }
        AnimacionContinua.VIBRAR -> {
            for (k in -1..1) drawRoundRect(col.copy(alpha = if (k == 0) 1f else 0.35f), Offset(tl.x + k * 2.dp.toPx(), tl.y), Size(w, h), radio, style = trazo)
        }
        AnimacionContinua.RESPIRAR -> {
            drawRoundRect(col, tl, Size(w, h), radio, style = trazo)
            drawRoundRect(col.copy(alpha = 0.3f), Offset(tl.x - 3.dp.toPx(), tl.y - 3.dp.toPx()), Size(w + 6.dp.toPx(), h + 6.dp.toPx()), radio, style = Stroke(1.dp.toPx()))
        }
        AnimacionContinua.PANEO -> {
            drawRoundRect(col, tl, Size(w, h), radio, style = trazo)
            val y = tl.y + h + 5.dp.toPx()
            drawLine(col.copy(alpha = 0.6f), Offset(tl.x, y), Offset(tl.x + w, y), 1.2.dp.toPx(), StrokeCap.Round)
            drawLine(col.copy(alpha = 0.6f), Offset(tl.x + w, y), Offset(tl.x + w - 3.dp.toPx(), y - 3.dp.toPx()), 1.2.dp.toPx(), StrokeCap.Round)
        }
        AnimacionContinua.SHAKE_FLASH -> {
            drawRoundRect(col.copy(alpha = 0.35f), Offset(tl.x - 3.dp.toPx(), tl.y + 2.dp.toPx()), Size(w, h), radio, style = trazo)
            drawRoundRect(col, tl, Size(w, h), radio)
            pulsos(col, tl, w, h)
        }
        AnimacionContinua.ZOOM_RITMO -> {
            drawRoundRect(col, tl, Size(w, h), radio, style = trazo)
            drawRoundRect(col.copy(alpha = 0.4f), Offset(center.x - w * 0.62f, center.y - h * 0.62f), Size(w * 1.24f, h * 1.24f), radio, style = Stroke(1.dp.toPx()))
            pulsos(col, tl, w, h)
        }
        AnimacionContinua.ESTROBO -> dibujoPieza(Animacion.ESTROBO, col, false)
        AnimacionContinua.RGB -> dibujoPieza(Animacion.RGB, col, false)
        AnimacionContinua.TWITCH -> dibujoPieza(Animacion.TWITCH, col, false)
        AnimacionContinua.CAMARA_EN_MANO -> {
            rotate(-5f) { drawRoundRect(col, tl, Size(w, h), radio, style = trazo) }
            val y = tl.y + h + 5.dp.toPx()
            val p = androidx.compose.ui.graphics.Path().apply {
                moveTo(tl.x, y)
                cubicTo(tl.x + w * 0.3f, y - 4.dp.toPx(), tl.x + w * 0.6f, y + 4.dp.toPx(), tl.x + w, y)
            }
            drawPath(p, col.copy(alpha = 0.6f), style = Stroke(1.2.dp.toPx(), cap = StrokeCap.Round))
        }
        AnimacionContinua.LATIDO -> {
            drawRoundRect(col.copy(alpha = 0.5f), tl, Size(w, h), radio, style = Stroke(1.dp.toPx()))
            // La linea del pulso, de lado a lado
            val y = center.y
            val x0 = tl.x - 3.dp.toPx()
            val x1 = tl.x + w + 3.dp.toPx()
            val u = (x1 - x0) / 10f
            val p = androidx.compose.ui.graphics.Path().apply {
                moveTo(x0, y); lineTo(x0 + u * 3, y); lineTo(x0 + u * 4, y - h * 0.35f); lineTo(x0 + u * 5, y + h * 0.25f)
                lineTo(x0 + u * 6, y - h * 0.18f); lineTo(x0 + u * 7, y); lineTo(x1, y)
            }
            drawPath(p, col, style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        }
    }
}

/** Tres puntos debajo: va con los golpes de la musica. */
private fun DrawScope.pulsos(col: Color, tl: Offset, w: Float, h: Float) {
    val y = tl.y + h + 6.dp.toPx()
    for (k in 0..2) drawCircle(col.copy(alpha = if (k == 0) 1f else 0.45f), 1.6.dp.toPx(), Offset(tl.x + w * (0.2f + k * 0.3f), y))
}

/** "Guardar como mia", con sitio de sobra para el dedo. */
@Composable
private fun BotonGuardar(onClick: () -> Unit) {
    Row(
        Modifier
            .heightIn(min = 40.dp)
            .clip(esquinas(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icono(Glifos.guardar, tam = 16.dp)
        Spacer(Modifier.width(6.dp))
        Text("Guardar como mia", style = MaterialTheme.typography.bodyMedium, color = Ink.White)
    }
}
