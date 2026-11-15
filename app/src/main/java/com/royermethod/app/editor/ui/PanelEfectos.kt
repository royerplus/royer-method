package com.royermethod.app.editor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.royermethod.app.editor.modelo.CatalogoEfectos
import com.royermethod.app.editor.modelo.Clip
import com.royermethod.app.editor.modelo.CombinacionEfectos
import com.royermethod.app.editor.modelo.Curva
import com.royermethod.app.editor.modelo.EfectoVideo
import com.royermethod.app.editor.modelo.Linea
import com.royermethod.app.editor.modelo.Parametro
import com.royermethod.app.editor.modelo.ProgresoEfecto
import com.royermethod.app.editor.modelo.TiempoPreciso
import com.royermethod.app.editor.modelo.TipoEfectoVideo
import com.royermethod.app.editor.modelo.conIdsEfectos
import com.royermethod.app.ui.components.BotonIcono
import com.royermethod.app.ui.components.BotonTexto
import com.royermethod.app.ui.components.Chip
import com.royermethod.app.ui.components.ChipInterruptor
import com.royermethod.app.ui.components.Deslizador
import com.royermethod.app.ui.components.Escala
import com.royermethod.app.ui.components.Etiqueta
import com.royermethod.app.ui.components.Fila
import com.royermethod.app.ui.components.Glifos
import com.royermethod.app.ui.components.Icono
import com.royermethod.app.ui.components.Letra
import com.royermethod.app.ui.components.Nota
import com.royermethod.app.ui.components.Pestanas
import com.royermethod.app.ui.components.SeccionAjustes
import com.royermethod.app.ui.components.Tira
import com.royermethod.app.ui.components.esquinas
import com.royermethod.app.ui.theme.Ink
import kotlinx.coroutines.flow.first
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/*
 * Panel de efectos.
 *
 * Sin efectos (o al tocar "Agregar"), el catalogo: unas pestanas fijas con los
 * grupos (la ultima, las combinaciones) y debajo las casillas del grupo. Tocar
 * una la pone en el cabezal y pasa a ajustarla.
 * Con efectos: arriba, fija, la franja (FranjaEfectos: el bloque de 0 a su
 * duracion, un carril por solape, el elegido resaltado; ahi se elige, se mueve
 * y se recorta) y, con uno elegido, sus pestanas (Ajustes, Tiempo, Mas) con
 * apagarlo, probarlo y quitarlo al lado. Sin ninguno elegido, la lista de los
 * puestos.
 * En la barra de abajo: a que capas llega (en un tramo compartido) y "Agregar".
 *
 * Los efectos se eligen por id con el bloque visto por conIdsEfectos (ver
 * TramosEfecto.kt), asi los de proyectos viejos se eligen igual y sus ids se
 * escriben con el primer cambio, sin cambiar la imagen.
 */

/** La hoja del panel de efectos. */
@Composable
fun HojaEfectos(estado: EstadoEditor, onCerrar: () -> Unit) {
    val c = estado.clipElegido
    if (c == null || c.duracionUs <= 0) {
        HojaPanel(Panel.EFECTOS.titulo, onCerrar, corta = true) {
            if (c != null) SinNada("Este bloque necesita una duración para llevar efectos")
        }
        return
    }
    val p = recordarPanel(estado, c)
    // Atras con el catalogo abierto vuelve a los efectos (como "Volver"), no cierra la hoja
    val eleccion = p.eleccion
    val hayEfectos by rememberUpdatedState(p.vista.efectos.isNotEmpty())
    DisposableEffect(estado, eleccion) {
        val quitar = estado.alAtras { eleccion.alAtras(hayEfectos) }
        onDispose { quitar() }
    }
    val e = p.elegido
    val catalogo = p.enCatalogo
    var grupo by rememberSaveable { mutableIntStateOf(0) }
    var pestana by rememberSaveable(c.id) { mutableIntStateOf(0) }
    var destinos by rememberSaveable(c.id) { mutableStateOf(false) }
    // Al salir del catalogo (con un efecto recien puesto, casi siempre) se empieza por sus ajustes
    LaunchedEffect(catalogo) { if (!catalogo) pestana = 0 }
    HojaPanel(
        Panel.EFECTOS.titulo, onCerrar,
        acciones = if (c.esAjuste || p.vista.efectos.isNotEmpty()) ({
            // Un tramo compartido: a que capas llega, siempre a mano
            if (c.esAjuste) {
                val resumen = resumenDestinos(c)
                BotonTexto(resumen, { destinos = !destinos }, Modifier.semantics { contentDescription = "Afecta a: $resumen" },
                    glifo = Glifos.capas, encendido = destinos)
            }
            if (p.vista.efectos.isNotEmpty()) BotonAgregar(p)
        }) else null,
        fijo = {
            if (destinos && c.esAjuste) DestinosCapa(estado, desplegado = true)
            if (catalogo) PestanasCatalogo(p, grupo) { grupo = it }
            else {
                Franja(p)
                if (e != null) CabeceraElegido(p, e, pestana) { pestana = it }
            }
        },
        // Otro grupo, otra pestana u otro efecto empiezan arriba del todo
        claveDesplazamiento = listOf(catalogo, grupo, pestana, e?.id)
    ) {
        when {
            catalogo -> Catalogo(p, grupo)
            e != null -> Elegido(p, e, pestana)
            else -> Aplicados(p)
        }
    }
}

// ----------------------------------------------------------------------
// Estado del panel
// ----------------------------------------------------------------------

/** Lo elegido en el panel (por id) y si esta abierto el catalogo; se recuerda por bloque. */
@Stable
internal class EleccionEfectos(id: Long?, catalogo: Boolean) {
    var id by mutableStateOf(id)
    var catalogo by mutableStateOf(catalogo)

    /**
     * Otro panel pide [pedido] (convertir una animacion en efecto): si esta
     * en el bloque se elige y se sale del catalogo. Devuelve si lo eligio; el
     * pedido se consume igual (si no esta aqui, ya no tiene sentido).
     */
    fun atender(pedido: Long?, vista: Clip): Boolean {
        if (pedido == null || vista.efectos.none { it.id == pedido }) return false
        id = pedido
        catalogo = false
        return true
    }

    /**
     * El boton atras: con el catalogo abierto y efectos puestos lo cierra,
     * como "Volver" (true). Sin efectos el catalogo es todo lo que hay: false,
     * y se cierra la hoja.
     */
    fun alAtras(hayEfectos: Boolean): Boolean {
        if (!catalogo || !hayEfectos) return false
        catalogo = false
        return true
    }
}

/** Para recordar la eleccion al girar el telefono. Fuera de la clase: asi esta no necesita Compose para probarse. */
private val GuardarEleccion = listSaver<EleccionEfectos, Any>(
    save = { listOf(it.id ?: -1L, it.catalogo) },
    restore = { EleccionEfectos((it[0] as Long).takeIf { v -> v > 0 }, it[1] as Boolean) }
)

/**
 * Lo que comparten las tres partes de la hoja (boton, franja y contenido) y
 * las acciones del panel. Se rehace en cada composicion con el bloque del
 * momento; las acciones cambian el proyecto con lo que haya al tocarlas.
 * El cabezal ([local]) solo se lee dentro de las acciones, nunca al componer.
 */
internal class PanelFx(
    val estado: EstadoEditor,
    /** El bloque con ids en todos sus efectos. */
    val vista: Clip,
    /** Donde empieza el bloque en la linea: el origen del iman a fotogramas. */
    val inicioBloque: Long,
    val eleccion: EleccionEfectos,
    /** Fin del tramo que se esta probando (en la linea), o null. */
    val repetir: MutableState<Long?>
) {
    val dur: Long get() = vista.duracionUs
    val elegido: EfectoVideo? get() = eleccion.id?.let { id -> vista.efectos.firstOrNull { it.id == id } }
    val enCatalogo: Boolean get() = eleccion.catalogo || vista.efectos.isEmpty()
    val fotogramas: Boolean get() = AjustesEditor.ajustarFotogramas

    /** El cabezal dentro del bloque. Solo en acciones: lee el tiempo. */
    fun local(): Long = (estado.tiempo - inicioBloque).coerceIn(0, (dur - 1).coerceAtLeast(0))

    /** El cabezal donde poner algo: pegado al fotograma con el iman. */
    fun cabezal(): Long = TramosEfecto.pegarCabezal(local(), fotogramas, inicioBloque).coerceIn(0, dur)

    fun ver(localUs: Long) {
        repetir.value = null
        estado.pausarEn(inicioBloque + localUs.coerceIn(0, (dur - 1).coerceAtLeast(0)))
    }

    /** Cambia el elegido; [arrastre] junta los pasos de un deslizador en uno. */
    fun cambiar(arrastre: Boolean = false, f: (EfectoVideo) -> EfectoVideo) {
        val id = eleccion.id ?: return
        estado.cambiarClip(arrastre) { it.conEfecto(id, f) }
    }

    fun cambiarBloque(f: (Clip) -> Clip) = estado.cambiarClip { f(it) }

    /** Tocar una barra la elige (y si el cabezal no esta en ella, lo lleva dentro); tocar vacio no elige nada. */
    fun tocar(id: Long?) {
        eleccion.id = id
        val e = elegido ?: return
        eleccion.catalogo = false
        val l = local()
        if (l < e.inicioUs || l >= e.finUs) {
            ver(TramosEfecto.pegarCabezal(e.inicioUs + e.duracionUs / 3, fotogramas, inicioBloque).coerceIn(e.inicioUs, (e.finUs - 1).coerceAtLeast(e.inicioUs)))
        }
    }

    /**
     * Agrega en el cabezal, en un paso de deshacer, y deja elegido lo nuevo.
     * Con el cabezal fuera del bloque se pone en su borde y el cabezal va al
     * principio de lo nuevo, como al tocar una barra: si no, la vista previa
     * seguiria fuera y no se veria lo que se ha puesto.
     */
    fun agregar(tipo: TipoEfectoVideo) {
        val desde = cabezal()
        var nuevo: Long? = null
        estado.cambiarClip { actual -> actual.conEfectoNuevo(tipo, desde).also { nuevo = it.second }.first }
        nuevo?.let { eleccion.id = it }
        eleccion.catalogo = false
        verSiFuera(nuevo)
    }

    fun agregar(combinacion: CombinacionEfectos) {
        val desde = cabezal()
        var nuevo: Long? = null
        estado.cambiarClip { actual -> actual.conCombinacion(combinacion, desde).also { nuevo = it.second }.first }
        nuevo?.let { eleccion.id = it }
        eleccion.catalogo = false
        verSiFuera(nuevo)
    }

    private fun verSiFuera(id: Long?) {
        val l = estado.tiempo - inicioBloque
        if (id == null || (l >= 0 && l < dur)) return
        estado.clipElegido?.inicioDelGrupo(id)?.let { ver(it) }
    }

    fun duplicar(id: Long) {
        var nuevo: Long? = null
        estado.cambiarClip { actual -> actual.conDuplicado(id)?.also { nuevo = it.second }?.first ?: actual }
        nuevo?.let { eleccion.id = it }
    }

    fun quitar(id: Long) {
        val siguiente = TramosEfecto.vecinoAlQuitar(vista.efectos, id)
        estado.cambiarClip { it.sinElEfecto(id) }
        eleccion.id = siguiente
    }

    /** "Probar": reproduce solo el tramo del efecto y se para al acabar. */
    fun probar(e: EfectoVideo) {
        if (estado.reproduciendo && repetir.value != null) {
            estado.reproduciendo = false
            repetir.value = null
        } else {
            estado.irA(inicioBloque + e.inicioUs)
            repetir.value = inicioBloque + minOf(e.finUs, dur)
            estado.reproduciendo = true
        }
    }
}

@Composable
private fun recordarPanel(estado: EstadoEditor, c: Clip): PanelFx {
    val vista = remember(c) { c.conIdsEfectos() }
    val pedido = estado.efectoAElegir
    val eleccion = rememberSaveable(c.id, saver = GuardarEleccion) {
        // Al abrir: el que pidio otro panel, o el que esta bajo el cabezal (sin
        // apuntarse al tiempo: si no, el panel se recompondria al reproducir)
        val local = Snapshot.withoutReadObservation { estado.localElegido }
        EleccionEfectos(TramosEfecto.elegidoAlAbrir(vista.efectos, local, pedido), vista.efectos.isEmpty())
    }
    // Otro panel pide un efecto (al abrir o con el panel ya abierto): se elige y se consume
    LaunchedEffect(pedido, vista) {
        if (pedido == null) return@LaunchedEffect
        eleccion.atender(pedido, vista)
        estado.efectoAElegir = null
    }
    val repetir = remember(c.id) { mutableStateOf<Long?>(null) }
    // La prueba se para al final del tramo. Se vigila con snapshotFlow para no
    // leer el cabezal al componer
    LaunchedEffect(repetir.value) {
        val hasta = repetir.value ?: return@LaunchedEffect
        snapshotFlow { !estado.reproduciendo || estado.tiempo >= hasta }.first { it }
        if (estado.reproduciendo) estado.reproduciendo = false
        repetir.value = null
    }
    return PanelFx(estado, vista, estado.inicioElegido ?: 0L, eleccion, repetir)
}

// ----------------------------------------------------------------------
// Partes de la hoja
// ----------------------------------------------------------------------

/** "Agregar" abre el catalogo; con el catalogo abierto, "Volver" lo cierra. Solo sale con efectos puestos. */
@Composable
private fun BotonAgregar(p: PanelFx) {
    if (p.eleccion.catalogo) BotonTexto("Volver", { p.eleccion.catalogo = false }, glifo = Glifos.atras)
    else BotonTexto("Agregar", { p.eleccion.catalogo = true }, glifo = Glifos.mas)
}

@Composable
private fun Franja(p: PanelFx) {
    FranjaEfectos(
        p.estado, p.vista, p.inicioBloque, p.elegido?.id,
        onTocar = { p.tocar(it) },
        onAgarrar = { id -> p.eleccion.id = id; p.eleccion.catalogo = false },
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

// ----------------------------------------------------------------------
// Catalogo
// ----------------------------------------------------------------------

/** Los grupos, como caben en una pestana. */
private val GRUPO_CORTO = mapOf("Mascaras y cortes" to "Máscaras", "Desenfoque y bordes" to "Desenfoque")
private const val COMBINADOS = "Combinados"

/** Una pestana por grupo y, al final, las combinaciones. Con un punto, los que ya tienen algo puesto. */
@Composable
private fun PestanasCatalogo(p: PanelFx, elegida: Int, onElegir: (Int) -> Unit) {
    val efectos = p.vista.efectos
    val grupos = CatalogoEfectos.grupos
    val marcas = remember(efectos) {
        val tipos = efectos.map { it.tipo }.toSet()
        val con = grupos.indices.filter { g -> grupos[g].second.any { it in tipos } }.toMutableSet()
        if (CatalogoEfectos.combinaciones.any { TramosEfecto.vecesCombinacion(efectos, it) > 0 }) con += grupos.size
        con.toSet()
    }
    Pestanas(grupos.map { GRUPO_CORTO[it.first] ?: it.first } + COMBINADOS, elegida.coerceIn(0, grupos.size), onElegir, marcas = marcas)
}

/** Las casillas del grupo elegido: tocar una la agrega en el cabezal. */
@Composable
private fun Catalogo(p: PanelFx, grupo: Int) {
    val efectos = p.vista.efectos
    val tipos = CatalogoEfectos.grupos.getOrNull(grupo)?.second
    if (tipos != null) {
        val cuantos = remember(efectos) { TramosEfecto.cuantosPorTipo(efectos) }
        Rejilla(tipos, 3) { t, m -> CasillaEfecto(Rotulos.de(t.nombre), cuantos[t] ?: 0, m) { p.agregar(t) } }
    } else {
        // Varios efectos de una vez; cada uno queda suelto
        Rejilla(CatalogoEfectos.combinaciones, 2) { c, m ->
            CasillaEfecto(Rotulos.de(c.nombre), TramosEfecto.vecesCombinacion(efectos, c), m) { p.agregar(c) }
        }
    }
}

/** Casillas del mismo ancho y del mismo alto, en filas de [columnas]. */
@Composable
private fun <T> Rejilla(elementos: List<T>, columnas: Int, celda: @Composable (T, Modifier) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        elementos.chunked(columnas).forEach { fila ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                fila.forEach { celda(it, Modifier.weight(1f).fillMaxHeight()) }
                repeat(columnas - fila.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Un efecto del catalogo: su nombre y, si ya esta en el bloque, cuantos hay. */
@Composable
private fun CasillaEfecto(nombre: String, cuantos: Int, modifier: Modifier, onClick: () -> Unit) {
    val puesto = cuantos > 0
    Box(
        modifier
            .heightIn(min = 44.dp)
            .clip(esquinas(10.dp))
            .background(if (puesto) Ink.White15 else Ink.White08)
            .semantics { if (puesto) stateDescription = "Puesto: $cuantos" }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            nombre, style = Letra.chica, color = if (puesto) Ink.White else Ink.White90,
            maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        )
        if (puesto) Contador(cuantos, Modifier.align(Alignment.TopEnd).padding(3.dp))
    }
}

@Composable
private fun Contador(n: Int, modifier: Modifier = Modifier) {
    Box(
        modifier.widthIn(min = 15.dp).heightIn(min = 15.dp).clip(CircleShape).background(Ink.White).padding(horizontal = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("$n", style = Letra.mini.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.sp), color = Ink.Black, maxLines = 1)
    }
}

// ----------------------------------------------------------------------
// Sin nada elegido: los puestos
// ----------------------------------------------------------------------

@Composable
private fun Aplicados(p: PanelFx) {
    p.vista.efectos.sortedWith(compareBy({ it.inicioUs }, { it.finUs })).forEach { e ->
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp)
                .clip(esquinas(10.dp))
                .clickable { p.tocar(e.id) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                Rotulos.de(TramosEfecto.nombre(e)),
                style = Letra.cuerpo,
                color = if (e.activo) Ink.White90 else Ink.White50,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (!e.activo) {
                Icono(Glifos.ojoCerrado, color = Ink.White50, tam = 15.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text("${TiempoPreciso.texto(e.inicioUs)} – ${TiempoPreciso.texto(e.finUs)}", style = Letra.chica, color = Ink.White50, maxLines = 1)
        }
    }
}

// ----------------------------------------------------------------------
// El efecto elegido
// ----------------------------------------------------------------------

private val PESTANAS_EFECTO = listOf("Ajustes", "Tiempo", "Más")

/**
 * Las pestanas del elegido y, al lado, lo que se hace con el entero: apagarlo,
 * probarlo y quitarlo. Su nombre ya se lee en la franja, resaltado.
 */
@Composable
private fun CabeceraElegido(p: PanelFx, e: EfectoVideo, pestana: Int, onPestana: (Int) -> Unit) {
    val nombre = Rotulos.de(TramosEfecto.nombre(e))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Pestanas(PESTANAS_EFECTO, pestana, onPestana, Modifier.weight(1f))
        BotonIcono(
            if (e.activo) Glifos.ojo else Glifos.ojoCerrado, { p.cambiar { it.copy(activo = !it.activo) } },
            color = if (e.activo) Ink.White90 else Ink.White50, tam = 38.dp,
            descripcion = if (e.activo) "Apagar $nombre" else "Encender $nombre"
        )
        val probando = p.estado.reproduciendo && p.repetir.value != null
        BotonIcono(if (probando) Glifos.pausa else Glifos.play, { p.probar(e) }, tam = 38.dp,
            descripcion = if (probando) "Parar la prueba" else "Probar $nombre")
        BotonIcono(Glifos.papelera, { p.quitar(e.id) }, tam = 38.dp, descripcion = "Quitar $nombre")
    }
}

@Composable
private fun Elegido(p: PanelFx, e: EfectoVideo, pestana: Int) {
    val parametros = CatalogoEfectos.parametros(e.tipo)
    val minimo = TramosEfecto.minimo(e, p.fotogramas, p.dur)
    when (pestana) {
        0 -> AjustesEfecto(p, e, parametros)
        1 -> TiempoEfecto(p, e, parametros, minimo)
        else -> MasEfecto(p, e, parametros)
    }
}

/** Como se ve: su fuerza, su ritmo y su forma. */
@Composable
private fun AjustesEfecto(p: PanelFx, e: EfectoVideo, parametros: List<Parametro>) {
    val forma = parametros.filter { it in FORMA }
    AvisosEfecto(p, e)
    if (Parametro.INTENSIDAD in parametros) {
        Deslizador("Intensidad", e.intensidad, 0f, 3f, { v -> p.cambiar(true) { it.copy(intensidad = v) } },
            neutro = 1f, paso = 0.01f, texto = { "${(it * 100).roundToLong()}%" })
    }
    // En los que van al ritmo, la rapidez es lo que mas se toca
    if (Parametro.RAPIDEZ in parametros) {
        Deslizador("Rapidez", e.rapidez, 0.1f, 10f, { v -> p.cambiar(true) { it.copy(rapidez = v) } },
            neutro = 1f, escala = Escala.LOGARITMICA, texto = { "%.2fx".format(it) })
    }
    if (forma.isNotEmpty() || e.tipo == TipoEfectoVideo.MITADES) Forma(p, e, forma)
}

/**
 * Lo que el motor no va a ensenar del elegido (sin cambiar como se dibuja):
 * una mascara tapada por otra puesta antes, porque solo cuenta una a la vez,
 * o un efecto tan corto que no cae en ningun cuadro del video.
 */
@Composable
private fun AvisosEfecto(p: PanelFx, e: EfectoVideo) {
    val delante = remember(p.vista.efectos, e.id) { TramosEfecto.mascarasDelante(p.vista.efectos, e.id) }
    if (delante.isNotEmpty()) {
        val otra = Rotulos.de(TramosEfecto.nombre(delante.first()))
        val texto = if (TramosEfecto.tapadaEntera(e, delante)) {
            "Solo se ve una máscara a la vez y manda la puesta antes ($otra): esta no se verá. Apaga o aparta la otra."
        } else {
            "Solo se ve una máscara a la vez: mientras coincide con $otra, puesta antes, esta no se ve."
        }
        Nota(texto, Modifier.padding(vertical = 4.dp), color = Ink.Peligro)
    }
    val salida = p.estado.proyecto.salida
    val muestras = if (salida.desenfoqueMovimiento) salida.muestras else 1
    val seVe = remember(e, p.inicioBloque, p.dur, muestras) { TramosEfecto.seVeAlExportar(e, p.inicioBloque, p.dur, muestras) }
    if (e.activo && !seVe) {
        Nota("Tan corto que no cae en ningún fotograma: en el video no saldrá. Alárgalo o muévelo (un fotograma son 33 ms).",
            Modifier.padding(vertical = 4.dp), color = Ink.Peligro)
    }
}

/** Lo que va en la forma, en el orden de [CatalogoEfectos.parametros]. */
private val FORMA = setOf(Parametro.DESDE, Parametro.HASTA, Parametro.ANGULO, Parametro.CURVA, Parametro.CENTRO,
    Parametro.GROSOR, Parametro.SUAVIDAD, Parametro.INVERTIR, Parametro.CORTE_VERTICAL,
    Parametro.COLOR, Parametro.HALO, Parametro.UMBRAL, Parametro.LINEAS)

@Composable
private fun Forma(p: PanelFx, e: EfectoVideo, forma: List<Parametro>) {
    val a = e.ajustes
    val defecto = CatalogoEfectos.ajustesDe(e.tipo)
    ControlesVfxPantalla(p, e)
    if (e.tipo == TipoEfectoVideo.MITADES && p.vista.piezas.isNotEmpty()) {
        Nota("Este bloque ya está partido en partes: las mitades no se ven. Quita las partes para usarlas.",
            Modifier.padding(vertical = 4.dp), color = Ink.Peligro)
    }
    val rango = CatalogoEfectos.rango(e.tipo)
    if (rango != null && (Parametro.DESDE in forma || Parametro.HASTA in forma)) {
        val texto: (Float) -> String = { formatoValor(it, rango.unidad) }
        val paso = if (rango.unidad == "grados") 1f else 0.01f
        val dos = Parametro.DESDE in forma && Parametro.HASTA in forma
        val nombre = Rotulos.de(rango.nombre)
        // Con los dos extremos, lo que miden va de titulo: "Recorrido", "Escala"...
        if (dos) Etiqueta(nombre)
        if (Parametro.DESDE in forma) Deslizador(if (dos) "Desde" else nombre, a.desde, rango.minimo, rango.maximo,
            { v -> p.cambiar(true) { it.copy(ajustes = it.ajustes.copy(desde = v)) } }, neutro = defecto.desde, paso = paso, texto = texto)
        if (Parametro.HASTA in forma) Deslizador("Hasta", a.hasta, rango.minimo, rango.maximo,
            { v -> p.cambiar(true) { it.copy(ajustes = it.ajustes.copy(hasta = v)) } }, neutro = defecto.hasta, paso = paso, texto = texto)
    }
    if (Parametro.ANGULO in forma) {
        val titulo = when (e.tipo) {
            TipoEfectoVideo.LATIGAZO -> "Sale hacia"
            TipoEfectoVideo.MITADES, TipoEfectoVideo.BARRIDO -> "Avanza hacia"
            TipoEfectoVideo.DESENFOQUE_DIRECCIONAL -> "Rastro"
            else -> "Dirección"
        }
        Deslizador(titulo, normalizarAngulo(a.angulo), 0f, 359f, { v -> p.cambiar(true) { it.copy(ajustes = it.ajustes.copy(angulo = v)) } },
            neutro = normalizarAngulo(defecto.angulo), paso = 1f, texto = { "${it.roundToInt()}°" })
        Direcciones(normalizarAngulo(a.angulo)) { v -> p.cambiar { it.copy(ajustes = it.ajustes.copy(angulo = v)) } }
    }
    if (Parametro.CURVA in forma) {
        Fila("Curva") {
            Tira(Modifier.weight(1f)) {
                Curva.values().forEach { cv ->
                    Chip(Rotulos.de(cv.nombre), a.bezier == null && cv == a.curva, { p.cambiar { it.copy(ajustes = it.ajustes.copy(curva = cv, bezier = null)) } })
                }
            }
        }
        if (a.bezier != null) Nota("Lleva una curva a medida. Elige otra para cambiarla.")
    }
    // En la luz por mitades el centro se coloca en el plano de arriba
    if (Parametro.CENTRO in forma && e.tipo != TipoEfectoVideo.PULSO_MITADES) {
        val pct: (Float) -> String = { "${if (it > 0f) "+" else ""}${(it * 100).roundToInt()}%" }
        Deslizador("Centro X", a.centroX, -0.5f, 0.5f, { v -> p.cambiar(true) { it.copy(ajustes = it.ajustes.copy(centroX = v)) } },
            neutro = 0f, paso = 0.01f, texto = pct)
        Deslizador("Centro Y", a.centroY, -0.5f, 0.5f, { v -> p.cambiar(true) { it.copy(ajustes = it.ajustes.copy(centroY = v)) } },
            neutro = 0f, paso = 0.01f, texto = pct)
    }
    if (Parametro.GROSOR in forma) {
        val luz = e.tipo == TipoEfectoVideo.RESPLANDOR || e.tipo == TipoEfectoVideo.ARCOIRIS_BORDES || e.tipo == TipoEfectoVideo.SEMITONO
        val titulo = when (e.tipo) {
            TipoEfectoVideo.RESPLANDOR -> "Radio"
            TipoEfectoVideo.SEMITONO -> "Tamaño"
            TipoEfectoVideo.ARCOIRIS_BORDES -> "Anchura"
            else -> "Grosor"
        }
        val min = if (luz) .002f else 0f
        val max = if (e.tipo == TipoEfectoVideo.ARCOIRIS_BORDES) .04f else if (luz) .1f else 1f
        Deslizador(titulo, a.grosor.coerceIn(min, max), min, max, { v -> p.cambiar(true) { it.copy(ajustes = it.ajustes.copy(grosor = v)) } },
            neutro = defecto.grosor, paso = if (luz) .001f else .01f,
            texto = { if (it <= 0f) "Lleno" else "%.1f%%".format(it * 100) })
    }
    if (Parametro.SUAVIDAD in forma) {
        Deslizador("Suavidad", a.suavidad, 0f, 0.2f, { v -> p.cambiar(true) { it.copy(ajustes = it.ajustes.copy(suavidad = v)) } },
            neutro = 0f, paso = 0.001f, texto = { if (it <= 0f) "Normal" else "%.1f%%".format(it * 100) })
    }
    if (Parametro.INVERTIR in forma || Parametro.CORTE_VERTICAL in forma || a != defecto) {
        Tira(Modifier.padding(vertical = 4.dp)) {
            if (Parametro.INVERTIR in forma) ChipInterruptor(
                if (e.tipo == TipoEfectoVideo.PULSO_MITADES) "Alternar el orden" else "Invertir", a.invertir,
                { v -> p.cambiar { it.copy(ajustes = it.ajustes.copy(invertir = v)) } })
            if (Parametro.CORTE_VERTICAL in forma) ChipInterruptor("Corte vertical", a.vertical,
                { v -> p.cambiar { it.copy(ajustes = it.ajustes.copy(vertical = v)) } })
            if (a != defecto) BotonTexto("Restablecer", { p.cambiar { it.copy(ajustes = defecto) } }, glifo = Glifos.reiniciar)
        }
    }
}

/**
 * Donde cae: empieza (mueve el efecto entero, con lo que dura), cuanto dura,
 * el final exacto y recortar el principio sin mover el resto; ponerlo en el
 * cabezal o en todo el bloque; lo que tarda en entrar y en irse, y su grupo.
 */
@Composable
private fun TiempoEfecto(p: PanelFx, e: EfectoVideo, parametros: List<Parametro>, minimo: Long) {
    CampoTiempo(
        "Inicio", e.inicioUs, 0, (p.dur - e.duracionUs).coerceAtLeast(0),
        { us, arrastre -> p.cambiar(arrastre) { x -> TramosEfecto.conInicio(x, us, p.dur) } },
        deslizador = false, origenUs = p.inicioBloque
    )
    // La duracion se pega al fotograma contando desde donde empieza el
    // efecto en la linea: asi el final cae en un cuadro que se exporta
    val maxDuracion = (p.dur - e.inicioUs).coerceAtLeast(0)
    CampoTiempo(
        "Duración", e.duracionUs, minimo.coerceAtMost(maxDuracion), maxDuracion,
        { us, arrastre -> p.cambiar(arrastre) { x -> TramosEfecto.conDuracion(x, us, p.dur, minimo) } },
        deslizador = false, origenUs = p.inicioBloque + e.inicioUs
    )
    Tira(Modifier.padding(vertical = 4.dp)) {
        // Empieza donde esta el cabezal, con lo que dura
        Chip("Al cabezal", false, { val t = p.cabezal(); p.cambiar { TramosEfecto.conInicio(it, t, p.dur) } })
        Chip("Fin al cabezal", false, { val t = p.cabezal(); p.cambiar { TramosEfecto.conFin(it, t, p.dur, minimo) } })
        Chip("Todo el bloque", false, { p.cambiar { TramosEfecto.todoElBloque(it, p.dur) } })
        Chip("Ver inicio", false, { p.ver(e.inicioUs) })
        InterruptorFotogramas()
    }
    CampoTiempo(
        "Fin", e.finUs, minimoFinEfecto(e.inicioUs, minimo, p.dur), p.dur,
        { us, arrastre -> p.cambiar(arrastre) { x -> TramosEfecto.conFin(x, us, p.dur, minimo) } },
        deslizador = false, origenUs = p.inicioBloque
    )
    // Quita o devuelve un trozo del principio sin mover lo demas
    CampoTiempo(
        "Recortar inicio", e.inicioUs, 0, (e.finUs - minimo).coerceAtLeast(0),
        { us, arrastre -> p.cambiar(arrastre) { x -> TramosEfecto.conInicioRecortado(x, us, minimo) } },
        deslizador = false, origenUs = p.inicioBloque
    )
    if (Parametro.FUNDIDOS in parametros) {
        // La intensidad crece al empezar y se apaga al acabar
        CampoTiempo("Entrada suave", e.fundidoEntradaUs, 0, e.duracionUs, { us, arrastre ->
            p.cambiar(arrastre) { it.copy(fundidoEntradaUs = us.coerceIn(0, it.duracionUs)) }
        }, deslizador = false)
        CampoTiempo("Salida suave", e.fundidoSalidaUs, 0, e.duracionUs, { us, arrastre ->
            p.cambiar(arrastre) { it.copy(fundidoSalidaUs = us.coerceIn(0, it.duracionUs)) }
        }, deslizador = false)
    }
    if (e.grupo.isNotEmpty()) {
        // Todos los del grupo a la vez, sin descolocarse
        Etiqueta("Grupo ${Rotulos.de(e.grupo)}")
        val paso = if (p.fotogramas) "1 f" else "1 ms"
        Tira(Modifier.padding(vertical = 3.dp)) {
            Chip("−$paso", false, { p.cambiarBloque { it.conGrupoMovido(e.id, -pasoGrupo(p)) } })
            Chip("+$paso", false, { p.cambiarBloque { it.conGrupoMovido(e.id, pasoGrupo(p)) } })
            Chip("Al cabezal", false, {
                val t = p.cabezal()
                p.cambiarBloque { c -> c.inicioDelGrupo(e.id)?.let { c.conGrupoMovido(e.id, t - it) } ?: c }
            })
            Chip("Separar este", false, { p.cambiarBloque { it.separadoDelGrupo(e.id) } })
        }
    }
}

/** El grupo se mueve un fotograma entero (no se pega al cuadro: los del grupo pueden estar entre dos) o 1 ms. */
private fun pasoGrupo(p: PanelFx): Long = if (p.fotogramas) Linea.tiempoDe(1) else 1_000L

/** Lo de vez en cuando: que hace, duplicarlo, compartirlo, el progreso medido a mano y su reloj. */
@Composable
private fun MasEfecto(p: PanelFx, e: EfectoVideo, parametros: List<Parametro>) {
    Nota(Rotulos.de(CatalogoEfectos.descripcion(e.tipo)), Modifier.padding(top = 2.dp, bottom = 4.dp))
    if (e.grupo.isEmpty()) BotonTexto("Duplicar", { p.duplicar(e.id) }, glifo = Glifos.duplicar)
    // En un bloque suelto el efecto es solo suyo: compartirlo lo pasa a un tramo aparte
    if (!p.vista.esAjuste) DestinosCapa(p.estado, e.id)
    if (Parametro.PROGRESO in parametros) SeccionAjustes("Progreso medido") { PuntosProgreso(p, e) }
    // El reloj que conserva al recortar (lo que pasaba al empezar sigue pasando a la vez)
    if (e.desfaseUs != 0L || e.duracionBaseUs > 0L) {
        val base = e.duracionBaseUs.takeIf { it > 0 } ?: e.duracionUs
        val desfase = if (e.desfaseUs >= 0) "empieza ${TiempoPreciso.texto(e.desfaseUs)} después" else "empieza ${TiempoPreciso.texto(-e.desfaseUs)} antes"
        Nota("Reloj del efecto: $desfase del recorte, dura ${TiempoPreciso.texto(base)} entero.", Modifier.padding(vertical = 6.dp))
    }
}

/**
 * Puntos de progreso medidos a mano, para seguir una trayectoria: el tiempo es
 * el del reloj del efecto y se busca con faseControl/puntoEnFase/localDelPunto
 * (los arreglos de la rapidez fraccionaria). Lee el cabezal, por eso va
 * plegado: solo se recompone al reproducir con su seccion abierta.
 */
@Composable
private fun PuntosProgreso(p: PanelFx, efecto: EfectoVideo) {
    val base = efecto.duracionBaseUs.takeIf { it > 0 } ?: efecto.duracionUs
    val localFx = (p.estado.tiempo - p.inicioBloque).coerceIn(efecto.inicioUs, efecto.finUs)
    val fase = efecto.faseControl(localFx)
    val puntoActual = efecto.puntoEnFase(fase)
    val faseEdicion = puntoActual?.tiempoUs ?: fase
    fun progreso(valor: Float, arrastre: Boolean) = p.cambiar(arrastre) { actual ->
        val puntos = actual.progresos.ifEmpty { listOf(ProgresoEfecto(0, 0f), ProgresoEfecto(base, 1f)) }
        actual.copy(progresos = (puntos.filter { it.tiempoUs != faseEdicion } + ProgresoEfecto(faseEdicion, valor)).sortedBy { it.tiempoUs })
    }
    Deslizador("En ${TiempoPreciso.texto(faseEdicion)}", puntoActual?.valor ?: efecto.progresoEn(localFx), 0f, 1f,
        { v -> progreso(v, true) }, texto = { "${(it * 100).roundToLong()}%" })
    Tira(Modifier.padding(vertical = 3.dp)) {
        Chip("Registrar punto", false, { progreso(efecto.progresoEn(localFx), false) })
        if (puntoActual != null) Chip("Quitar este", false, {
            p.cambiar { it.copy(progresos = it.progresos.filter { q -> q.tiempoUs != puntoActual.tiempoUs }) }
        })
        if (efecto.progresos.isNotEmpty()) Chip("Sin puntos", false, { p.cambiar { it.copy(progresos = emptyList()) } })
    }
    if (efecto.progresos.isNotEmpty()) Tira(Modifier.padding(vertical = 3.dp)) {
        efecto.progresos.forEach { punto ->
            Chip("${TiempoPreciso.texto(punto.tiempoUs)} · ${(punto.valor * 100).roundToLong()}%", punto == puntoActual, {
                p.ver(efecto.localDelPunto(punto))
            })
        }
    }
}

// ----------------------------------------------------------------------
// Piezas pequenas
// ----------------------------------------------------------------------

/** Ocho flechas para elegir la direccion de un toque (0 derecha, 90 abajo, como las transiciones). */
@Composable
private fun Direcciones(angulo: Float, onElegir: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        for (k in 0 until 8) {
            val a = k * 45f
            val sel = abs(angulo - a) < 0.5f
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (sel) Ink.White else Ink.White08)
                    .semantics { selected = sel; contentDescription = "${a.roundToInt()} grados" }
                    .clickable { onElegir(a) },
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.size(18.dp).rotate(a)) { Glifos.flecha(this, if (sel) Ink.Black else Ink.White70) }
            }
        }
    }
}

private fun normalizarAngulo(a: Float): Float {
    if (!a.isFinite()) return 0f
    val r = a % 360f
    return if (r < 0f) r + 360f else r
}

private fun formatoValor(v: Float, unidad: String): String = when (unidad) {
    "x" -> "%.2fx".format(v)
    "grados" -> "${v.roundToInt()}°"
    else -> "${(v * 100).roundToInt()}%"
}
