package com.royermethod.app.editor.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.royermethod.app.ui.components.BotonTexto
import com.royermethod.app.ui.components.Chip
import com.royermethod.app.ui.components.Cuadricula
import com.royermethod.app.ui.components.Deslizador
import com.royermethod.app.ui.components.Escala
import com.royermethod.app.ui.components.Etiqueta
import com.royermethod.app.ui.components.Glifos
import com.royermethod.app.ui.components.Icono
import com.royermethod.app.ui.components.Letra
import com.royermethod.app.ui.components.Nota
import com.royermethod.app.ui.components.Opciones
import com.royermethod.app.ui.components.Pestanas
import com.royermethod.app.ui.components.Tira
import com.royermethod.app.ui.components.esquinas
import com.royermethod.app.ui.theme.Ink
import kotlin.math.min
import kotlin.math.roundToLong

/*
 * Panel "Animar".
 *
 * Arriba, fijas, las pestanas Entrada / Salida / Siempre (con un punto las
 * que ya tienen alguna) y, con una elegida, lo que dura. Debajo, lo que se
 * desliza: el catalogo (una tira de categorias y las casillas; la primera,
 * "Ninguna", quita las de esa pestana) o los ajustes de la elegida.
 * En la barra de abajo, con alguna puesta: "Puestas n" (las del bloque, para
 * elegir, ajustar o quitar) y "+" (tocar una del catalogo la suma a las que
 * ya hay en vez de cambiar la elegida).
 * Lo que hace cada toque esta en LogicaAnimacion.
 */

/** Alto de la barra de abajo de HojaPanel (raya + fila): el desplegable de las puestas sale encima. */
private val ALTO_BARRA = 47.dp

private val MOMENTOS = MomentoAnimacion.values().toList()

/** El aviso de como se suman o se cambian sale una vez por sesion, al poner la primera. */
private var consejoSumarVisto = false

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
     * El boton atras del sistema (ver [AtrasAnimacion.de]). True si cerro algo;
     * si no, se cierra la hoja. El dialogo de convertir es una ventana y
     * atiende su propio atras.
     */
    fun alAtras(hayElegida: Boolean): Boolean {
        when (AtrasAnimacion.de(menu, agregando, ajustes, hayElegida) ?: return false) {
            AtrasAnimacion.MENU -> menu = false
            AtrasAnimacion.AGREGANDO -> agregando = false
            AtrasAnimacion.AJUSTES -> ajustes = false
        }
        return true
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

/** Lo que cierra el boton atras dentro de Animar. */
internal enum class AtrasAnimacion {
    MENU, AGREGANDO, AJUSTES;

    companion object {
        /**
         * Por orden: el desplegable, el "+" y los ajustes de la elegida
         * ([hayElegida]: si no, no se ven). Null si nada: entonces se cierra
         * la hoja. Aqui y no en [VistaAnimacion] ni suelta en este fichero,
         * que necesitan Compose para cargarse: asi se prueba en el ordenador.
         */
        fun de(menu: Boolean, agregando: Boolean, ajustes: Boolean, hayElegida: Boolean): AtrasAnimacion? = when {
            menu -> MENU
            agregando -> AGREGANDO
            ajustes && hayElegida -> AJUSTES
            else -> null
        }
    }
}

/** Por bloque: al elegir otro bloque se empieza de nuevo. */
@Composable
private fun recordarVista(c: Clip): VistaAnimacion =
    rememberSaveable(c.id, saver = VistaAnimacion.Guardar) {
        VistaAnimacion(LogicaAnimacion.momentoInicial(LogicaAnimacion.aplicadas(c)))
    }

/**
 * La hoja del panel de animacion: pestanas y duracion fijas arriba, el
 * catalogo (o los ajustes de la elegida) deslizandose debajo y, en la barra,
 * "Puestas n" y "+". El desplegable de las puestas sale por encima de la
 * barra, dentro de la hoja: no tapa la imagen.
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
                acciones = if (aplicadas.isNotEmpty()) ({ AccionesBarra(vista, aplicadas.size) }) else null,
                fijo = { ZonaFija(estado, c, vista, aplicadas, elegida) }
            ) {
                if (verAjustes) AjustesElegida(estado, c, elegida) { vista.convertir = elegida.id }
                else CatalogoAnimaciones(estado, vista, aplicadas, elegida)
            }
        }
        if (vista.menu) MenuAplicadas(estado, vista, aplicadas, elegida)
    }
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
// Barra de abajo
// ----------------------------------------------------------------------

@Composable
private fun AccionesBarra(vista: VistaAnimacion, n: Int) {
    SelectorAplicadas(n, vista.menu) { vista.menu = !vista.menu }
    BotonIcono(Glifos.mas, {
        vista.agregando = !vista.agregando
        vista.ajustes = false
        vista.menu = false
    }, tam = 38.dp, descripcion = "Sumar otra animación", encendido = vista.agregando)
}

/** "Puestas 2 ▾": abre el desplegable de las que tiene el bloque. */
@Composable
private fun SelectorAplicadas(n: Int, abierto: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .heightIn(min = 34.dp)
            .clip(esquinas(17.dp))
            .background(if (abierto) Ink.White15 else Ink.White08)
            .semantics { contentDescription = "Animaciones puestas: $n" }
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Puestas", style = Letra.cuerpo, color = Ink.White70, maxLines = 1)
        Spacer(Modifier.width(6.dp))
        Text("$n", style = Letra.cuerpo.copy(fontWeight = FontWeight.Medium), color = Ink.White, maxLines = 1)
        Spacer(Modifier.width(5.dp))
        // Abierto apunta hacia abajo (se cierra); cerrado, hacia arriba, que es hacia donde sale
        Icono(Glifos.siguiente, Modifier.rotate(if (abierto) 90f else -90f), color = Ink.White50, tam = 12.dp)
    }
}

// ----------------------------------------------------------------------
// Zona fija: pestanas y duracion
// ----------------------------------------------------------------------

@Composable
private fun ZonaFija(estado: EstadoEditor, c: Clip, vista: VistaAnimacion, aplicadas: List<AnimacionAplicada>, elegida: AnimacionAplicada?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Pestanas(
            MOMENTOS.map { it.nombre }, MOMENTOS.indexOf(vista.pestana), { vista.pestana = MOMENTOS[it] }, Modifier.weight(1f),
            marcas = MOMENTOS.indices.filter { i -> aplicadas.any { it.momento == MOMENTOS[i] } }.toSet()
        )
        when {
            vista.agregando -> BotonTexto("Cancelar", { vista.agregando = false }, glifo = Glifos.cerrar)
            elegida != null -> BotonTexto("Ajustar", { vista.ajustes = !vista.ajustes }, glifo = Glifos.ajustes, encendido = vista.ajustes)
        }
    }
    when {
        // Sumando, la zona fija dice que hace el siguiente toque
        vista.agregando -> Text(
            "Toca otra para sumarla a las que ya hay",
            style = Letra.cuerpo, color = Ink.White,
            modifier = Modifier.fillMaxWidth().heightIn(min = 42.dp).padding(vertical = 11.dp)
        )
        // Cada pestana tiene su elegida; las de Siempre duran lo que el bloque
        elegida == null || elegida.momento == MomentoAnimacion.SIEMPRE -> Unit
        else -> key(elegida.id) {
            val (minimo, limite) = LogicaAnimacion.limitesDuracion(c.duracionUs)
            // Si ya dura mas que el bloque (se recorto despues), se ensena la de
            // verdad, la que usa la escena, y se puede bajar desde ahi
            val maximo = maxOf(limite, elegida.duracionUs)
            val fotogramas = AjustesEditor.ajustarFotogramas
            // Una duracion: el ajuste a fotogramas se cuenta desde 0. No mueve el
            // cabezal. Con el ajuste, sin barra: -/+ (un fotograma) y escribir el
            // valor bastan, y el catalogo necesita ese sitio
            CampoTiempo(
                "Duración", elegida.duracionUs, minimo, maximo,
                onCambio = { us, arrastre -> LogicaAnimacion.cambiarDuracion(estado, elegida.id, us, arrastre) },
                fotogramas = fotogramas,
                deslizador = !fotogramas
            )
        }
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
    // Las categorias en una tira: tocar una ensena solo esa (otra vez, o "Todas", todas)
    Tira(Modifier.padding(top = 2.dp, bottom = 6.dp)) {
        Chip(LogicaAnimacion.TODAS, categoria == null, { vista.categoria = null })
        nombres.forEach { n -> Chip(rotulo(n), n == categoria, { vista.categoria = if (n == categoria) null else n }, marca = n in conPuestas) }
    }
    val tocar by rememberUpdatedState { o: LogicaAnimacion.Opcion? ->
        if (o == null) {
            // "Ninguna": fuera las de esta pestana, en un solo paso
            if (puestas.isNotEmpty()) LogicaAnimacion.quitarMomento(estado, momento)
            vista.agregando = false
        } else {
            val r = LogicaAnimacion.aplicar(estado, o, elegida?.id, vista.agregando)
            if (r != null) {
                vista.elegidaId = r.elegidaId
                vista.agregando = false
                if (r.accion == LogicaAnimacion.Accion.SUMADA && !consejoSumarVisto) {
                    consejoSumarVisto = true
                    estado.avisar("Tocar otra la cambia. Con + se suma a esta")
                }
            }
        }
    }
    val alTocar = remember { { o: LogicaAnimacion.Opcion? -> tocar(o) } }
    Casillas(momento, grupos, marcas, puestas.isEmpty(), categoria == null, alTocar)
}

/** Las categorias y las casillas se guardan sin tildes; en pantalla, con ellas. */
private fun rotulo(nombre: String): String = when (nombre) {
    "Basicas" -> "Básicas"
    "Mias" -> "Mías"
    else -> nombre
}

/**
 * Las casillas, por categorias (con su nombre solo cuando se ven todas). La
 * primera es "Ninguna": encendida cuando la pestana no tiene ninguna, y
 * tocarla las quita.
 */
@Composable
private fun Casillas(
    momento: MomentoAnimacion,
    grupos: List<Pair<String, List<LogicaAnimacion.Opcion>>>,
    marcas: Map<LogicaAnimacion.Opcion, LogicaAnimacion.Marca>,
    sinNinguna: Boolean,
    conNombres: Boolean,
    onTocar: (LogicaAnimacion.Opcion?) -> Unit
) {
    grupos.forEachIndexed { g, (nombre, opciones) ->
        if (conNombres) Etiqueta(rotulo(nombre), if (g == 0) Modifier else Modifier.padding(top = 4.dp))
        // null es "Ninguna": solo en la primera fila
        val celdas: List<LogicaAnimacion.Opcion?> = if (g == 0) listOf<LogicaAnimacion.Opcion?>(null) + opciones else opciones
        Cuadricula(celdas) { o, mod ->
            if (o == null) {
                Casilla("Ninguna", if (sinNinguna) LogicaAnimacion.Marca.ELEGIDA else LogicaAnimacion.Marca.DISPONIBLE, { onTocar(null) }, mod, visto = false) { col -> ninguna(col) }
            } else Casilla(o.nombre, marcas[o] ?: LogicaAnimacion.Marca.DISPONIBLE, { onTocar(o) }, mod) { col ->
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
 * Casilla del catalogo, con las medidas de Mosaico. Las puestas en el bloque
 * llevan borde; la elegida, ademas, fondo claro y un visto en la esquina.
 */
@Composable
private fun Casilla(
    texto: String,
    marca: LogicaAnimacion.Marca,
    onClick: () -> Unit,
    modifier: Modifier,
    visto: Boolean = true,
    dibujo: DrawScope.(Color) -> Unit
) {
    val puesta = marca != LogicaAnimacion.Marca.DISPONIBLE
    val elegida = marca == LogicaAnimacion.Marca.ELEGIDA
    Column(
        modifier
            .width(60.dp)
            .semantics { selected = elegida; if (puesta && !elegida) stateDescription = "Puesta" }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(56.dp)) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(esquinas(12.dp))
                    .background(if (elegida) Ink.White15 else Ink.White08)
                    .border(1.5.dp, if (elegida) Ink.White else if (puesta) Ink.White50 else Color.Transparent, esquinas(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.size(34.dp)) { dibujo(if (puesta) Ink.White else Ink.White70) }
            }
            if (elegida && visto) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(4.dp).size(14.dp).clip(CircleShape).background(Ink.White),
                    contentAlignment = Alignment.Center
                ) { Icono(Glifos.listo, color = Ink.Black, tam = 10.dp) }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            texto,
            style = Letra.mini,
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
 * Las animaciones del bloque, justo encima de la barra: tocar una la elige y
 * lleva a su pestana; a su derecha, ajustarla y quitarla. Tocar fuera lo cierra.
 */
@Composable
private fun MenuAplicadas(estado: EstadoEditor, vista: VistaAnimacion, aplicadas: List<AnimacionAplicada>, elegida: AnimacionAplicada?) {
    Box(Modifier.fillMaxSize().padding(bottom = ALTO_BARRA), contentAlignment = Alignment.BottomCenter) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Ink.Black.copy(alpha = 0.6f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { vista.menu = false }
        )
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .clip(esquinas(14.dp))
                .background(Ink.Black)
                .background(Ink.White08)
                .border(1.dp, Ink.White15, esquinas(14.dp))
                .verticalScroll(rememberScrollState())
                .padding(vertical = 4.dp)
        ) {
            aplicadas.forEach { a ->
                FilaAplicada(
                    a, a.id == elegida?.id,
                    onElegir = { vista.elegidaId = a.id; vista.pestana = a.momento; vista.agregando = false; vista.menu = false },
                    onAjustar = { vista.elegidaId = a.id; vista.pestana = a.momento; vista.agregando = false; vista.ajustes = true; vista.menu = false },
                    onQuitar = { LogicaAnimacion.quitar(estado, a.id) }
                )
            }
            if (aplicadas.isEmpty()) Nota("Ya no queda ninguna en este bloque.", Modifier.padding(horizontal = 14.dp, vertical = 12.dp))
        }
    }
}

@Composable
private fun FilaAplicada(a: AnimacionAplicada, elegida: Boolean, onElegir: () -> Unit, onAjustar: () -> Unit, onQuitar: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .background(if (elegida) Ink.White08 else Color.Transparent)
            .semantics { selected = elegida }
            .clickable(onClick = onElegir)
            .padding(start = 14.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                a.nombre,
                style = Letra.cuerpo.copy(fontWeight = FontWeight.Medium),
                color = if (elegida) Ink.White else Ink.White90,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Nota(LogicaAnimacion.detalle(a))
        }
        BotonIcono(Glifos.ajustes, onAjustar, color = Ink.White70, tam = 38.dp, descripcion = "Ajustar ${a.nombre}")
        BotonIcono(Glifos.cerrar, onQuitar, color = Ink.White50, tam = 38.dp, descripcion = "Quitar ${a.nombre}")
    }
}

/** Antes de convertir: lo que hace y lo que deja de ser igual despues. */
@Composable
private fun DialogoConvertir(a: AnimacionAplicada, onCancelar: () -> Unit, onConvertir: () -> Unit) {
    Dialog(onDismissRequest = onCancelar) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(esquinas(18.dp))
                .background(Ink.Black)
                .background(Ink.White08)
                .border(1.dp, Ink.White15, esquinas(18.dp))
                .verticalScroll(rememberScrollState())
                .padding(18.dp)
        ) {
            Text("Convertir en efecto", style = Letra.titulo, color = Ink.White)
            Spacer(Modifier.height(8.dp))
            Text(
                "\"${a.nombre}\" pasa a ser un efecto con su propio tramo, que se mueve y se recorta en Efectos. " +
                    "Ahora se ve igual y no quedan dos copias. Se puede deshacer.",
                style = Letra.cuerpo,
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
    // El nombre y quitar, en una fila: lo que dura ya se ve arriba
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            a.nombre,
            style = Letra.titulo,
            color = Ink.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        // Quitar: un paso de deshacer, como la ✕ del desplegable
        BotonIcono(Glifos.papelera, { LogicaAnimacion.quitar(estado, a.id) }, color = Ink.White70, tam = 38.dp, descripcion = "Quitar ${a.nombre}")
    }
    if (a.momento == MomentoAnimacion.SIEMPRE) {
        PiezasContinuas(estado, a)
    } else {
        // Los tramos cuentan sobre lo que dura la animacion, como en la escena,
        // aunque el bloque se haya quedado mas corto; solo se ve lo que cabe
        val (base, visible) = LogicaAnimacion.baseTramos(a, c.duracionUs)
        if (base > visible) Nota("Dura ${TiempoPreciso.texto(base)}, más que el bloque: lo que pasa de ${TiempoPreciso.texto(visible)} no se ve.", color = Ink.Peligro)
        Piezas(
            a.id,
            a.piezas,
            duracionUs = base,
            visibleUs = visible,
            // En la entrada los tramos son instantes desde el comienzo del bloque: el
            // ajuste a fotogramas se cuenta desde donde empieza en la linea
            origenUs = if (a.momento == MomentoAnimacion.ENTRADA) estado.inicioElegido ?: 0L else 0L,
            deSalida = a.momento == MomentoAnimacion.SALIDA,
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
    // Solo si se puede, y si no, por que
    val motivo = LogicaAnimacion.motivoNoConvertible(a)
    if (motivo == null) BotonTexto("Convertir en efecto", onConvertir, glifo = Glifos.estrella)
    else Nota(motivo, Modifier.padding(top = 6.dp))
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
    visibleUs: Long,
    origenUs: Long,
    deSalida: Boolean,
    onCambio: (List<Movimiento>, Boolean) -> Unit,
    guardada: Boolean,
    onGuardar: () -> Unit
) {
    var abierta by remember(idAnimacion) { mutableIntStateOf(-1) }
    var agregando by remember(idAnimacion) { mutableStateOf(false) }
    CabeceraPiezas(guardada, onGuardar)
    piezas.forEachIndexed { i, m ->
        fun cambiar(n: Movimiento, arrastre: Boolean = true) = onCambio(piezas.toMutableList().also { it[i] = n }, arrastre)
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp)
                .clip(esquinas(10.dp))
                .background(Ink.White04)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 42.dp)
                    .clickable { abierta = if (abierta == i) -1 else i }
                    .padding(start = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icono({ col -> dibujoPieza(m.tipo, col, false) }, tam = 22.dp, color = Ink.White90)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(m.tipo.nombre, style = Letra.cuerpo, color = Ink.White90, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${(m.intensidad * 100).toInt()}%  ·  ${m.curva.nombre}" +
                            if (m.desde > 0f || m.hasta < 1f) "  ·  ${TiempoPreciso.texto(tramo(m.desde, duracionUs))}–${TiempoPreciso.texto(tramo(m.hasta, duracionUs))}" else "",
                        style = Letra.mini, color = Ink.White50, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                BotonIcono(Glifos.cerrar, { onCambio(piezas.filterIndexed { j, _ -> j != i }, false); abierta = -1 }, color = Ink.White50, tam = 38.dp, descripcion = "Quitar ${m.tipo.nombre}")
            }
            AnimatedVisibility(abierta == i) {
                Column(Modifier.padding(horizontal = 10.dp).padding(bottom = 8.dp)) {
                    Deslizador("Cuánto", m.intensidad, 0f, 3f, { v -> cambiar(m.copy(intensidad = v)) }, neutro = 1f, texto = { "${(it * 100).toInt()}%" })
                    Tira(Modifier.padding(vertical = 3.dp)) {
                        Curva.values().forEach { cu -> Chip(cu.nombre, cu == m.curva, { cambiar(m.copy(curva = cu), false) }) }
                    }
                    if (duracionUs > 0 && visibleUs > 0) {
                        val inicio = tramo(m.desde, duracionUs)
                        val fin = tramo(m.hasta, duracionUs).coerceAtLeast(inicio)
                        // El motor admite tramos desde 0.001 de la animacion; con
                        // el ajuste a fotogramas el limite practico es un fotograma
                        val minimo = if (AjustesEditor.ajustarFotogramas) Linea.tiempoDe(1) else 1L
                        val paso = min(visibleUs, maxOf(minimo, (duracionUs + 999) / 1000))
                        // Se escribe sobre lo que dura la animacion, pero solo dentro de lo que se ve
                        CampoTiempo("Empieza", inicio, 0, (min(fin, visibleUs) - paso).coerceAtLeast(0), { us, arrastre ->
                            cambiar(m.copy(desde = us.toFloat() / duracionUs), arrastre)
                        }, deslizador = false, origenUs = origenUs)
                        CampoTiempo("Termina", fin, (inicio + paso).coerceAtMost(visibleUs), visibleUs, { us, arrastre ->
                            cambiar(m.copy(hasta = us.toFloat() / duracionUs), arrastre)
                        }, deslizador = false, origenUs = origenUs)
                        if (deSalida) Nota("En la salida se cuentan hacia atrás, desde el final del bloque.")
                    }
                }
            }
        }
    }
    BotonTexto(if (agregando) "Elige la pieza" else "Sumar otra pieza", { agregando = !agregando }, glifo = Glifos.mas, encendido = agregando)
    AnimatedVisibility(agregando) {
        Opciones(Animacion.values().toList(), null, { it.nombre }, { t ->
            onCambio(piezas + Movimiento(t), false)
            agregando = false
            abierta = piezas.size
        })
    }
}

/** "Piezas", el ajuste a fotogramas de sus tiempos y, si no esta ya guardada, "Guardar". */
@Composable
private fun CabeceraPiezas(guardada: Boolean, onGuardar: () -> Unit, conFotogramas: Boolean = true) {
    Row(Modifier.fillMaxWidth().padding(bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Etiqueta("Piezas", Modifier.weight(1f))
        if (!guardada) BotonTexto("Guardar", onGuardar, glifo = Glifos.guardar)
        if (conFotogramas) InterruptorFotogramas()
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
    CabeceraPiezas((Combinaciones.continuas + AjustesEditor.continuasMias).any { it.second == piezas }, {
        val nombre = AjustesEditor.nombreNuevo("Mia continua", AjustesEditor.continuasMias.map { it.first })
        AjustesEditor.continuasMias = AjustesEditor.continuasMias + (nombre to piezas)
        AjustesEditor.alCambiar()
        estado.avisar("Guardada como \"$nombre\"")
    }, conFotogramas = false)
    piezas.forEachIndexed { i, k ->
        fun cambiar(n: Continuo) = poner(piezas.toMutableList().also { it[i] = n }, true)
        Column(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(esquinas(10.dp)).background(Ink.White04).padding(start = 10.dp, end = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(k.tipo.nombre, style = Letra.cuerpo, color = Ink.White90, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                BotonIcono(Glifos.cerrar, { poner(piezas.filterIndexed { j, _ -> j != i }) }, color = Ink.White50, tam = 38.dp, descripcion = "Quitar ${k.tipo.nombre}")
            }
            Deslizador("Cuánto", k.intensidad, 0f, 3f, { v -> cambiar(k.copy(intensidad = v)) }, neutro = 1f, texto = { "${(it * 100).toInt()}%" })
            Deslizador("Rapidez", k.velocidad, 0.1f, 5f, { v -> cambiar(k.copy(velocidad = v)) }, neutro = 1f, escala = Escala.LOGARITMICA, texto = { "%.2gx".format(it) })
        }
    }
    BotonTexto(if (agregando) "Elige la pieza" else "Sumar otra pieza", { agregando = !agregando }, glifo = Glifos.mas, encendido = agregando)
    AnimatedVisibility(agregando) {
        Opciones(AnimacionContinua.values().toList(), null, { it.nombre }, { t ->
            poner(piezas + Continuo(t)); agregando = false
        })
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
