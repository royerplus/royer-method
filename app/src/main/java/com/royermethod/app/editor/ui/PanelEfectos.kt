package com.royermethod.app.editor.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.royermethod.app.editor.modelo.CatalogoEfectos
import com.royermethod.app.editor.modelo.Clip
import com.royermethod.app.editor.modelo.CombinacionEfectos
import com.royermethod.app.editor.modelo.Curva
import com.royermethod.app.editor.modelo.EfectoVideo
import com.royermethod.app.editor.modelo.Parametro
import com.royermethod.app.editor.modelo.ProgresoEfecto
import com.royermethod.app.editor.modelo.TiempoPreciso
import com.royermethod.app.editor.modelo.TipoEfectoVideo
import com.royermethod.app.editor.modelo.conIdsEfectos
import com.royermethod.app.ui.components.BotonBorde
import com.royermethod.app.ui.components.BotonIcono
import com.royermethod.app.ui.components.Chip
import com.royermethod.app.ui.components.Deslizador
import com.royermethod.app.ui.components.Escala
import com.royermethod.app.ui.components.Etiqueta
import com.royermethod.app.ui.components.Glifos
import com.royermethod.app.ui.components.Icono
import com.royermethod.app.ui.components.Interruptor
import com.royermethod.app.ui.components.LocalGestos
import com.royermethod.app.ui.components.Nota
import com.royermethod.app.ui.components.Pestanas
import com.royermethod.app.ui.components.esquinas
import com.royermethod.app.ui.theme.Ink
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/*
 * Panel de efectos.
 *
 * Arriba, fija, la franja de efectos aplicados (FranjaEfectos): el bloque de
 * 0 a su duracion, un carril por solape, el elegido resaltado. Ahi se elige,
 * se mueve y se recorta. "+ Agregar", en la fila del titulo, abre el
 * catalogo. Lo que se desliza depende de lo que haya:
 *  - el catalogo (al agregar o si no hay efectos): grupos de tipos y
 *    combinaciones rapidas, con lo ya puesto marcado;
 *  - el efecto elegido: su cabecera, lo de siempre a la vista (Inicio,
 *    Duracion, Intensidad) y el resto en secciones plegables;
 *  - sin nada elegido: la lista de los aplicados.
 *
 * Los efectos se eligen por id con el bloque visto por conIdsEfectos (ver
 * TramosEfecto.kt), asi los de proyectos viejos se eligen igual y sus ids se
 * escriben con el primer cambio, sin cambiar la imagen.
 */

/**
 * La hoja del panel de efectos: "+ Agregar" en la fila del titulo, la franja
 * fija debajo y el catalogo o el efecto elegido en lo que se desliza.
 */
@Composable
fun HojaEfectos(estado: EstadoEditor, onCerrar: () -> Unit) {
    val c = estado.clipElegido
    if (c == null || c.duracionUs <= 0) {
        HojaPanel(Panel.EFECTOS.titulo, onCerrar) { PanelEfectos(estado) }
        return
    }
    val p = recordarPanel(estado, c)
    HojaPanel(
        Panel.EFECTOS.titulo, onCerrar,
        acciones = { BotonAgregar(p) },
        fijo = { Franja(p) }
    ) { Contenido(p) }
}

/**
 * Lo mismo sin hoja, para meterlo en una columna que ya se desliza: el boton
 * de agregar, la franja y el contenido seguidos.
 */
@Composable
fun PanelEfectos(estado: EstadoEditor) {
    val c = estado.clipElegido ?: return
    if (c.duracionUs <= 0) {
        SinNada("Este bloque necesita una duracion para agregar efectos")
        return
    }
    val p = recordarPanel(estado, c)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { BotonAgregar(p) }
    Franja(p)
    Contenido(p)
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

    /** Agrega en el cabezal, en un paso de deshacer, y deja elegido lo nuevo. */
    fun agregar(tipo: TipoEfectoVideo) {
        val desde = cabezal()
        var nuevo: Long? = null
        estado.cambiarClip { actual -> actual.conEfectoNuevo(tipo, desde).also { nuevo = it.second }.first }
        nuevo?.let { eleccion.id = it }
        eleccion.catalogo = false
    }

    fun agregar(combinacion: CombinacionEfectos) {
        val desde = cabezal()
        var nuevo: Long? = null
        estado.cambiarClip { actual -> actual.conCombinacion(combinacion, desde).also { nuevo = it.second }.first }
        nuevo?.let { eleccion.id = it }
        eleccion.catalogo = false
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

/** "+ Agregar" abre el catalogo; con el catalogo abierto y efectos puestos, "Volver" lo cierra. */
@Composable
private fun BotonAgregar(p: PanelFx) {
    if (p.eleccion.catalogo && p.vista.efectos.isNotEmpty()) {
        BotonBorde("Volver", { p.eleccion.catalogo = false }, glifo = Glifos.atras)
    } else {
        BotonBorde("Agregar", { p.eleccion.catalogo = true }, glifo = Glifos.mas)
    }
    Spacer(Modifier.width(4.dp))
}

@Composable
private fun Franja(p: PanelFx) {
    FranjaEfectos(
        p.estado, p.vista, p.inicioBloque, p.elegido?.id,
        onTocar = { p.tocar(it) },
        onAgarrar = { id -> p.eleccion.id = id; p.eleccion.catalogo = false },
        modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)
    )
}

@Composable
private fun Contenido(p: PanelFx) {
    val e = p.elegido
    when {
        p.enCatalogo -> Catalogo(p)
        e != null -> Elegido(p, e)
        else -> Aplicados(p)
    }
}

// ----------------------------------------------------------------------
// Catalogo
// ----------------------------------------------------------------------

/** Que hay en cada grupo del catalogo, en una frase. */
private fun descripcionGrupo(nombre: String): String? = when (nombre) {
    "Movimiento" -> "Mover, acercar, girar, partir en mitades o sacudir la imagen."
    "Mascaras y cortes" -> "Circulos, anillos y barridos que descubren u ocultan."
    "Desenfoque y bordes" -> "Rastro en cualquier direccion, desenfoques y bordes espejo."
    "Luz" -> "Destellos blancos u oscuros."
    "Imagen" -> "Color separado, negativo y fallos digitales."
    else -> null
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Catalogo(p: PanelFx) {
    var pestana by rememberSaveable { mutableIntStateOf(0) }
    Pestanas(listOf("Efectos", "Combinaciones rapidas"), pestana, { pestana = it })
    Nota("Se pone donde esta el cabezal.", Modifier.padding(bottom = 2.dp))
    if (pestana == 0) {
        val cuantos = remember(p.vista.efectos) { TramosEfecto.cuantosPorTipo(p.vista.efectos) }
        CatalogoEfectos.grupos.forEach { (nombre, tipos) ->
            Etiqueta(nombre)
            descripcionGrupo(nombre)?.let { Nota(it) }
            FlowRow(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tipos.forEach { t -> ChipTipo(t.nombre, cuantos[t] ?: 0) { p.agregar(t) } }
            }
        }
    } else {
        Nota("Varios efectos de una vez. Cada uno queda suelto: se ajusta, se mueve y se quita por separado.",
            Modifier.padding(bottom = 6.dp))
        CatalogoEfectos.combinaciones.forEach { c ->
            TarjetaCombinacion(c, TramosEfecto.vecesCombinacion(p.vista.efectos, c)) { p.agregar(c) }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Un tipo del catalogo; si ya esta en el bloque lleva borde y cuantos hay. */
@Composable
private fun ChipTipo(nombre: String, cuantos: Int, onClick: () -> Unit) {
    val puesto = cuantos > 0
    Row(
        Modifier
            .heightIn(min = 40.dp)
            .clip(esquinas(20.dp))
            .background(if (puesto) Ink.White15 else Ink.White08)
            .then(if (puesto) Modifier.border(1.dp, Ink.White50, esquinas(20.dp)) else Modifier)
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = if (puesto) 8.dp else 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(nombre, style = MaterialTheme.typography.bodyMedium, color = if (puesto) Ink.White else Ink.White70, maxLines = 1)
        if (puesto) {
            Spacer(Modifier.width(8.dp))
            Contador(cuantos)
        }
    }
}

@Composable
private fun Contador(n: Int) {
    Box(Modifier.size(20.dp).clip(CircleShape).background(Ink.White), contentAlignment = Alignment.Center) {
        Text("$n", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp), color = Ink.Black)
    }
}

@Composable
private fun TarjetaCombinacion(c: CombinacionEfectos, veces: Int, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(esquinas(12.dp))
            .background(if (veces > 0) Ink.White15 else Ink.White08)
            .then(if (veces > 0) Modifier.border(1.dp, Ink.White50, esquinas(12.dp)) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(c.nombre, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = Ink.White90)
            Nota(c.descripcion)
        }
        Spacer(Modifier.width(10.dp))
        if (veces > 0) Contador(veces) else Icono(Glifos.mas, color = Ink.White70, tam = 18.dp)
    }
}

// ----------------------------------------------------------------------
// Sin nada elegido: los aplicados
// ----------------------------------------------------------------------

@Composable
private fun Aplicados(p: PanelFx) {
    Nota("Toca un efecto para ajustarlo. Arrastra en un hueco de la franja para mover el cabezal.",
        Modifier.padding(vertical = 4.dp))
    p.vista.efectos.sortedWith(compareBy({ it.inicioUs }, { it.finUs })).forEach { e ->
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clip(esquinas(10.dp))
                .clickable { p.tocar(e.id) }
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                TramosEfecto.nombre(e) + if (!e.activo) " · Apagado" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = if (e.activo) Ink.White90 else Ink.White50,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text("${TiempoPreciso.texto(e.inicioUs)} - ${TiempoPreciso.texto(e.finUs)}",
                style = MaterialTheme.typography.bodyMedium, color = Ink.White50, maxLines = 1)
        }
    }
}

// ----------------------------------------------------------------------
// El efecto elegido
// ----------------------------------------------------------------------

@Composable
private fun Elegido(p: PanelFx, e: EfectoVideo) {
    val parametros = CatalogoEfectos.parametros(e.tipo)
    val minimo = TramosEfecto.minimo(e, p.fotogramas, p.dur)
    val forma = parametros.filter { it in FORMA }

    Cabecera(p, e)
    // Lo de siempre, a la vista sin desplazar: donde empieza (mueve el efecto
    // entero, con lo que dura), cuanto dura y su fuerza
    CampoTiempo(
        "Inicio", e.inicioUs, 0, (p.dur - e.duracionUs).coerceAtLeast(0),
        { us, arrastre -> p.cambiar(arrastre) { x -> TramosEfecto.conInicio(x, us, p.dur) } },
        deslizador = false, origenUs = p.inicioBloque
    )
    // La duracion se pega al fotograma contando desde donde empieza el
    // efecto en la linea: asi el final cae en un cuadro que se exporta
    val maxDuracion = (p.dur - e.inicioUs).coerceAtLeast(0)
    CampoTiempo(
        "Duracion", e.duracionUs, minimo.coerceAtMost(maxDuracion), maxDuracion,
        { us, arrastre -> p.cambiar(arrastre) { x -> TramosEfecto.conDuracion(x, us, p.dur, minimo) } },
        deslizador = false, origenUs = p.inicioBloque + e.inicioUs
    )
    if (Parametro.INTENSIDAD in parametros) {
        FilaValor("Intensidad", e.intensidad, 0f, 3f, neutro = 1f, paso = 0.01f, texto = { "${(it * 100).roundToLong()}%" }) { v ->
            p.cambiar(true) { it.copy(intensidad = v) }
        }
    }
    Nota(CatalogoEfectos.descripcion(e.tipo), Modifier.padding(top = 2.dp, bottom = 4.dp))

    var abiertaTiempo by rememberSaveable { mutableStateOf(false) }
    var abiertaForma by rememberSaveable { mutableStateOf(true) }
    var abiertaSuave by rememberSaveable { mutableStateOf(false) }
    var abiertaAvanzado by rememberSaveable { mutableStateOf(false) }

    Seccion("Tiempo", "Fin ${TiempoPreciso.texto(e.finUs)}", abiertaTiempo, { abiertaTiempo = it }) {
        SeccionTiempo(p, e, minimo)
    }
    if (forma.isNotEmpty() || e.tipo == TipoEfectoVideo.MITADES) {
        Seccion("Forma", null, abiertaForma, { abiertaForma = it }) { SeccionForma(p, e, forma) }
    }
    if (Parametro.FUNDIDOS in parametros) {
        val resumen = if (e.fundidoEntradaUs == 0L && e.fundidoSalidaUs == 0L) "Sin suavizar" else
            "${TiempoPreciso.texto(e.fundidoEntradaUs)} / ${TiempoPreciso.texto(e.fundidoSalidaUs)}"
        Seccion("Entrada y salida", resumen, abiertaSuave, { abiertaSuave = it }) {
            Nota("La intensidad crece al empezar y se apaga al acabar.", Modifier.padding(bottom = 2.dp))
            CampoTiempo("Entrada suave", e.fundidoEntradaUs, 0, e.duracionUs, { us, arrastre ->
                p.cambiar(arrastre) { it.copy(fundidoEntradaUs = us.coerceIn(0, it.duracionUs)) }
            })
            CampoTiempo("Salida suave", e.fundidoSalidaUs, 0, e.duracionUs, { us, arrastre ->
                p.cambiar(arrastre) { it.copy(fundidoSalidaUs = us.coerceIn(0, it.duracionUs)) }
            })
        }
    }
    Seccion("Avanzado", null, abiertaAvanzado, { abiertaAvanzado = it }) { SeccionAvanzado(p, e, parametros) }
}

/** Lo que va en "Forma", en el orden de [CatalogoEfectos.parametros]. */
private val FORMA = setOf(Parametro.DESDE, Parametro.HASTA, Parametro.ANGULO, Parametro.CURVA, Parametro.CENTRO,
    Parametro.GROSOR, Parametro.SUAVIDAD, Parametro.INVERTIR, Parametro.CORTE_VERTICAL)

/**
 * Nombre, encendido, probar, duplicar (si va suelto) y quitar. Si es de una
 * combinacion, debajo su grupo y "Separar del grupo".
 */
@Composable
private fun Cabecera(p: PanelFx, e: EfectoVideo) {
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                TramosEfecto.nombre(e),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = if (e.activo) Ink.White else Ink.White50,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            if (!e.activo) Nota("Apagado")
        }
        MiniInterruptor(e.activo) { v -> p.cambiar { it.copy(activo = v) } }
        val probando = p.estado.reproduciendo && p.repetir.value != null
        BotonIcono(if (probando) Glifos.pausa else Glifos.play, { p.probar(e) })
        if (e.grupo.isEmpty()) BotonIcono(Glifos.duplicar, { p.duplicar(e.id) })
        BotonIcono(Glifos.papelera, { p.quitar(e.id) })
    }
    if (e.grupo.isNotEmpty()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "De \"${e.grupo}\"",
                style = MaterialTheme.typography.bodyMedium, color = Ink.White50,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            ChipPequeno("Separar del grupo") { p.cambiarBloque { it.separadoDelGrupo(e.id) } }
        }
    }
}

/** Encendido o apagado, sin texto: va junto al nombre del efecto. 40 dp para el pulgar. */
@Composable
private fun MiniInterruptor(activo: Boolean, onCambio: (Boolean) -> Unit) {
    val pista by animateColorAsState(if (activo) Ink.White else Ink.White15, tween(200), label = "activo")
    Box(
        Modifier.size(48.dp, 40.dp).clip(esquinas(20.dp)).clickable { onCambio(!activo) },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.size(36.dp, 22.dp).clip(CircleShape).background(pista).padding(3.dp),
            contentAlignment = if (activo) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Box(Modifier.size(16.dp).clip(CircleShape).background(if (activo) Ink.Black else Ink.White70))
        }
    }
}

/** Una pastilla baja (36 dp) para acciones que acompanan a una linea de texto. */
@Composable
private fun ChipPequeno(texto: String, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 36.dp)
            .clip(esquinas(18.dp))
            .background(Ink.White08)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(texto, style = MaterialTheme.typography.bodyMedium, color = Ink.White90, maxLines = 1)
    }
}

/**
 * Lo demas del tiempo: el final exacto, recortar el principio sin mover el
 * resto, ponerlo en el cabezal o en todo el bloque y mover su grupo.
 */
@Composable
private fun SeccionTiempo(p: PanelFx, e: EfectoVideo, minimo: Long) {
    InterruptorFotogramas(Modifier.padding(vertical = 4.dp))
    CampoTiempo(
        "Fin", e.finUs, minimoFinEfecto(e.inicioUs, minimo, p.dur), p.dur,
        { us, arrastre -> p.cambiar(arrastre) { x -> TramosEfecto.conFin(x, us, p.dur, minimo) } },
        deslizador = false, origenUs = p.inicioBloque
    )
    CampoTiempo(
        "Recortar inicio", e.inicioUs, 0, (e.finUs - minimo).coerceAtLeast(0),
        { us, arrastre -> p.cambiar(arrastre) { x -> TramosEfecto.conInicioRecortado(x, us, minimo) } },
        deslizador = false, origenUs = p.inicioBloque
    )
    Nota("Inicio mueve el efecto entero. Recortar quita o devuelve un trozo de un borde sin mover lo demas.",
        Modifier.padding(bottom = 2.dp))
    FilaChips(null) {
        // Empieza donde esta el cabezal, con lo que dura
        Chip("Al cabezal", false, { val t = p.cabezal(); p.cambiar { TramosEfecto.conInicio(it, t, p.dur) } })
        Chip("Fin al cabezal", false, { val t = p.cabezal(); p.cambiar { TramosEfecto.conFin(it, t, p.dur, minimo) } })
        Chip("Ver inicio", false, { p.ver(e.inicioUs) })
        Chip("Todo el bloque", false, { p.cambiar { TramosEfecto.todoElBloque(it, p.dur) } })
    }
    if (e.grupo.isNotEmpty()) {
        Etiqueta("Mover el grupo")
        Nota("Todos los de \"${e.grupo}\" a la vez, sin descolocarse.")
        val paso = if (p.fotogramas) "1 f" else "1 ms"
        FilaChips(null) {
            Chip("−$paso", false, { p.cambiarBloque { it.conGrupoMovido(e.id, -pasoGrupo(p)) } })
            Chip("+$paso", false, { p.cambiarBloque { it.conGrupoMovido(e.id, pasoGrupo(p)) } })
            Chip("Grupo al cabezal", false, {
                val t = p.cabezal()
                p.cambiarBloque { c -> c.inicioDelGrupo(e.id)?.let { c.conGrupoMovido(e.id, t - it) } ?: c }
            })
        }
    }
}

/** El grupo se mueve un fotograma entero (no se pega al cuadro: los del grupo pueden estar entre dos) o 1 ms. */
private fun pasoGrupo(p: PanelFx): Long = if (p.fotogramas) com.royermethod.app.editor.modelo.Linea.tiempoDe(1) else 1_000L

@Composable
private fun SeccionForma(p: PanelFx, e: EfectoVideo, forma: List<Parametro>) {
    val a = e.ajustes
    val defecto = CatalogoEfectos.ajustesDe(e.tipo)
    if (e.tipo == TipoEfectoVideo.MITADES && p.vista.piezas.isNotEmpty()) {
        Nota("Este bloque ya esta partido en partes: las mitades no se ven. Quita las partes para usarlas.",
            Modifier.padding(vertical = 4.dp), color = Ink.Peligro)
    }
    val rango = CatalogoEfectos.rango(e.tipo)
    if (rango != null && (Parametro.DESDE in forma || Parametro.HASTA in forma)) {
        Nota("${rango.nombre} · ${unidad(e.tipo, rango.unidad)}", Modifier.padding(top = 4.dp))
        val texto: (Float) -> String = { formatoValor(it, rango.unidad) }
        val paso = if (rango.unidad == "grados") 1f else 0.01f
        val dos = Parametro.DESDE in forma && Parametro.HASTA in forma
        if (Parametro.DESDE in forma) FilaValor(if (dos) "Desde" else rango.nombre, a.desde, rango.minimo, rango.maximo,
            neutro = defecto.desde, paso = paso, texto = texto) { v -> p.cambiar(true) { it.copy(ajustes = it.ajustes.copy(desde = v)) } }
        if (Parametro.HASTA in forma) FilaValor("Hasta", a.hasta, rango.minimo, rango.maximo,
            neutro = defecto.hasta, paso = paso, texto = texto) { v -> p.cambiar(true) { it.copy(ajustes = it.ajustes.copy(hasta = v)) } }
    }
    if (Parametro.ANGULO in forma) {
        val titulo = when (e.tipo) {
            TipoEfectoVideo.LATIGAZO -> "Sale hacia"
            TipoEfectoVideo.MITADES, TipoEfectoVideo.BARRIDO -> "Avanza hacia"
            TipoEfectoVideo.DESENFOQUE_DIRECCIONAL -> "Rastro"
            else -> "Direccion"
        }
        FilaValor(titulo, normalizarAngulo(a.angulo), 0f, 359f, neutro = normalizarAngulo(defecto.angulo), paso = 1f,
            texto = { "${it.roundToInt()}°" }) { v -> p.cambiar(true) { it.copy(ajustes = it.ajustes.copy(angulo = v)) } }
        Direcciones(normalizarAngulo(a.angulo)) { v -> p.cambiar { it.copy(ajustes = it.ajustes.copy(angulo = v)) } }
        if (e.tipo == TipoEfectoVideo.MITADES) Nota("La mitad de arriba (o la izquierda) avanza hacia ahi; la otra llega por el lado contrario.")
    }
    if (Parametro.CURVA in forma) {
        Etiqueta("Curva")
        CurvasEfecto(if (a.bezier != null) null else a.curva) { cv ->
            p.cambiar { it.copy(ajustes = it.ajustes.copy(curva = cv, bezier = null)) }
        }
        if (a.bezier != null) Nota("Lleva una curva a medida. Elige otra para cambiarla.")
    }
    if (Parametro.CENTRO in forma) {
        val pct: (Float) -> String = { "${if (it > 0f) "+" else ""}${(it * 100).roundToInt()}%" }
        FilaValor("Centro X", a.centroX, -0.5f, 0.5f, neutro = 0f, paso = 0.01f, texto = pct) { v ->
            p.cambiar(true) { it.copy(ajustes = it.ajustes.copy(centroX = v)) }
        }
        FilaValor("Centro Y", a.centroY, -0.5f, 0.5f, neutro = 0f, paso = 0.01f, texto = pct) { v ->
            p.cambiar(true) { it.copy(ajustes = it.ajustes.copy(centroY = v)) }
        }
    }
    if (Parametro.GROSOR in forma) {
        FilaValor("Grosor", a.grosor, 0f, 1f, neutro = 0f, paso = 0.01f,
            texto = { if (it <= 0f) "Lleno" else "${(it * 100).roundToInt()}%" }) { v ->
            p.cambiar(true) { it.copy(ajustes = it.ajustes.copy(grosor = v)) }
        }
    }
    if (Parametro.SUAVIDAD in forma) {
        FilaValor("Suavidad", a.suavidad, 0f, 0.2f, neutro = 0f, paso = 0.001f,
            texto = { if (it <= 0f) "Normal" else "%.1f%%".format(it * 100) }) { v ->
            p.cambiar(true) { it.copy(ajustes = it.ajustes.copy(suavidad = v)) }
        }
    }
    if (Parametro.INVERTIR in forma) {
        val detalle = if (e.tipo == TipoEfectoVideo.BARRIDO) "Oculta en vez de descubrir" else "Se ve fuera en vez de dentro"
        Interruptor("Invertir", a.invertir, { v -> p.cambiar { it.copy(ajustes = it.ajustes.copy(invertir = v)) } }, detalle)
    }
    if (Parametro.CORTE_VERTICAL in forma) {
        Interruptor("Corte vertical", a.vertical, { v -> p.cambiar { it.copy(ajustes = it.ajustes.copy(vertical = v)) } },
            "Izquierda y derecha en vez de arriba y abajo")
    }
    if (a != defecto) {
        Row(Modifier.padding(vertical = 6.dp)) {
            BotonBorde("Forma de siempre", { p.cambiar { it.copy(ajustes = defecto) } }, glifo = Glifos.reiniciar)
        }
    }
}

@Composable
private fun SeccionAvanzado(p: PanelFx, e: EfectoVideo, parametros: List<Parametro>) {
    if (Parametro.RAPIDEZ in parametros) {
        FilaValor("Rapidez", e.rapidez, 0.1f, 10f, neutro = 1f, escala = Escala.LOGARITMICA, texto = { "%.2fx".format(it) }) { v ->
            p.cambiar(true) { it.copy(rapidez = v) }
        }
    }
    if (Parametro.PROGRESO in parametros) PuntosProgreso(p, e)
    // El reloj que conserva al recortar (lo que pasaba al empezar sigue pasando a la vez)
    if (e.desfaseUs != 0L || e.duracionBaseUs > 0L) {
        val base = e.duracionBaseUs.takeIf { it > 0 } ?: e.duracionUs
        val desfase = if (e.desfaseUs >= 0) "empieza ${TiempoPreciso.texto(e.desfaseUs)} despues" else "empieza ${TiempoPreciso.texto(-e.desfaseUs)} antes"
        Nota("Reloj del efecto: $desfase del recorte, dura ${TiempoPreciso.texto(base)} entero.", Modifier.padding(vertical = 6.dp))
    }
    if (Parametro.RAPIDEZ !in parametros && Parametro.PROGRESO !in parametros && e.desfaseUs == 0L && e.duracionBaseUs <= 0L) {
        Nota("Este efecto no tiene mas ajustes.", Modifier.padding(vertical = 6.dp))
    }
}

/**
 * Puntos de progreso medidos a mano, como antes: el tiempo es el del reloj
 * del efecto y se busca con faseControl/puntoEnFase/localDelPunto (los
 * arreglos de la rapidez fraccionaria). Lee el cabezal, por eso va aparte:
 * solo se recompone esto al reproducir con "Avanzado" abierto.
 */
@Composable
private fun PuntosProgreso(p: PanelFx, efecto: EfectoVideo) {
    Etiqueta("Progreso medido")
    Nota("Registra puntos para seguir una trayectoria medida. El tiempo es el de este efecto.")
    val base = efecto.duracionBaseUs.takeIf { it > 0 } ?: efecto.duracionUs
    val localFx = (p.estado.tiempo - p.inicioBloque).coerceIn(efecto.inicioUs, efecto.finUs)
    val fase = efecto.faseControl(localFx)
    val puntoActual = efecto.puntoEnFase(fase)
    val faseEdicion = puntoActual?.tiempoUs ?: fase
    fun progreso(valor: Float, arrastre: Boolean) = p.cambiar(arrastre) { actual ->
        val puntos = actual.progresos.ifEmpty { listOf(ProgresoEfecto(0, 0f), ProgresoEfecto(base, 1f)) }
        actual.copy(progresos = (puntos.filter { it.tiempoUs != faseEdicion } + ProgresoEfecto(faseEdicion, valor)).sortedBy { it.tiempoUs })
    }
    Deslizador("Progreso en ${TiempoPreciso.texto(faseEdicion)}", puntoActual?.valor ?: efecto.progresoEn(localFx), 0f, 1f,
        { v -> progreso(v, true) }, texto = { "${(it * 100).roundToLong()}%" })
    FilaChips(null) {
        Chip("Registrar punto", false, { progreso(efecto.progresoEn(localFx), false) })
        if (efecto.progresos.isNotEmpty()) Chip("Sin puntos", false, { p.cambiar { it.copy(progresos = emptyList()) } })
        if (puntoActual != null) Chip("Quitar este", false, {
            p.cambiar { it.copy(progresos = it.progresos.filter { q -> q.tiempoUs != puntoActual.tiempoUs }) }
        })
    }
    if (efecto.progresos.isNotEmpty()) FilaChips(null) {
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

/**
 * Una seccion plegable: la cabecera entera se toca (44 dp) y lleva a la
 * derecha un resumen de lo que hay dentro.
 */
@Composable
private fun Seccion(
    titulo: String, resumen: String?, abierta: Boolean, onAbrir: (Boolean) -> Unit,
    contenido: @Composable ColumnScope.() -> Unit
) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.White08))
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { onAbrir(!abierta) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(Modifier.size(16.dp).rotate(if (abierta) 90f else 0f)) {
            val w = size.width
            val trazo = 1.6.dp.toPx()
            drawLine(Ink.White70, Offset(w * 0.38f, w * 0.22f), Offset(w * 0.68f, w * 0.5f), trazo, StrokeCap.Round)
            drawLine(Ink.White70, Offset(w * 0.68f, w * 0.5f), Offset(w * 0.38f, w * 0.78f), trazo, StrokeCap.Round)
        }
        Spacer(Modifier.width(8.dp))
        Text(titulo.uppercase(), style = MaterialTheme.typography.labelSmall, color = Ink.White70, maxLines = 1, modifier = Modifier.weight(1f))
        if (resumen != null && !abierta) Text(resumen, style = MaterialTheme.typography.bodyMedium, color = Ink.White50, maxLines = 1)
    }
    if (abierta) Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), content = contenido)
}

/**
 * Un valor con su deslizador en una sola fila (44 dp): titulo, barra y
 * valor; tocar el valor lo devuelve a [neutro]. Un arrastre es un paso de
 * deshacer, igual que el Deslizador de siempre.
 */
@Composable
private fun FilaValor(
    titulo: String, valor: Float, desde: Float, hasta: Float,
    neutro: Float = desde, paso: Float = 0f, escala: Escala = Escala.LINEAL,
    texto: (Float) -> String, onCambio: (Float) -> Unit
) {
    val gestos = LocalGestos.current
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(titulo, style = MaterialTheme.typography.bodyMedium, color = Ink.White70, maxLines = 2,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.width(92.dp))
        Deslizador(titulo, valor.coerceIn(desde, hasta), desde, hasta, onCambio, Modifier.weight(1f),
            neutro = neutro, escala = escala, paso = paso, texto = texto, cabecera = false)
        Text(
            texto(valor),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = if (valor == neutro) Ink.White50 else Ink.White,
            maxLines = 1,
            modifier = Modifier
                .widthIn(min = 60.dp)
                .clip(esquinas(6.dp))
                .clickable { gestos.empezar(); onCambio(neutro); gestos.terminar() }
                .padding(horizontal = 6.dp, vertical = 12.dp)
        )
    }
}

/** Fila de pastillas que bajan si no caben, con un titulo opcional delante. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilaChips(titulo: String?, contenido: @Composable () -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (titulo != null) Box(Modifier.heightIn(min = 40.dp), contentAlignment = Alignment.CenterStart) {
            Text(titulo, style = MaterialTheme.typography.bodyMedium, color = Ink.White70)
        }
        contenido()
    }
}

/** Ocho flechas para elegir la direccion de un toque (0 derecha, 90 abajo, como las transiciones). */
@Composable
private fun Direcciones(angulo: Float, onElegir: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        for (k in 0 until 8) {
            val a = k * 45f
            val sel = kotlin.math.abs(angulo - a) < 0.5f
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(if (sel) Ink.White else Ink.White08).clickable { onElegir(a) },
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.size(20.dp).rotate(a)) { Glifos.flecha(this, if (sel) Ink.Black else Ink.White70) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CurvasEfecto(elegida: Curva?, onElegir: (Curva) -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Curva.values().forEach { cv -> Chip(cv.nombre, cv == elegida, { onElegir(cv) }) }
    }
}

private fun normalizarAngulo(a: Float): Float {
    if (!a.isFinite()) return 0f
    val r = a % 360f
    return if (r < 0f) r + 360f else r
}

/** Como leer Desde y Hasta de cada tipo. */
private fun unidad(tipo: TipoEfectoVideo, unidad: String): String = when {
    unidad == "lienzos" -> "100% = un lienzo"
    unidad == "x" -> "1x = tamano normal"
    unidad == "grados" -> "en grados"
    tipo == TipoEfectoVideo.CIRCULO -> "100% = cubre todo"
    else -> "100% = entero"
}

private fun formatoValor(v: Float, unidad: String): String = when (unidad) {
    "x" -> "%.2fx".format(v)
    "grados" -> "${v.roundToInt()}°"
    else -> "${(v * 100).roundToInt()}%"
}
