package com.royermethod.app.editor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.unit.sp
import com.royermethod.app.editor.modelo.Ajuste
import com.royermethod.app.editor.modelo.AjustesColor
import com.royermethod.app.editor.modelo.Alineacion
import com.royermethod.app.editor.modelo.Bezier
import com.royermethod.app.editor.modelo.Clip
import com.royermethod.app.editor.modelo.Curva
import com.royermethod.app.editor.modelo.Edicion
import com.royermethod.app.editor.modelo.Escena
import com.royermethod.app.editor.modelo.Filtro
import com.royermethod.app.editor.modelo.Fondo
import com.royermethod.app.editor.modelo.Formato
import com.royermethod.app.editor.modelo.Fuente
import com.royermethod.app.editor.modelo.Mezcla
import com.royermethod.app.editor.modelo.Recorte
import com.royermethod.app.editor.modelo.Texto
import com.royermethod.app.ui.components.BotonBorde
import com.royermethod.app.ui.components.BotonIcono
import com.royermethod.app.ui.components.BotonLleno
import com.royermethod.app.ui.components.Chip
import com.royermethod.app.ui.components.Cuadricula
import com.royermethod.app.ui.components.Deslizador
import com.royermethod.app.ui.components.Escala
import com.royermethod.app.ui.components.Etiqueta
import com.royermethod.app.ui.components.Glifos
import com.royermethod.app.ui.components.Interruptor
import com.royermethod.app.ui.components.LocalGestos
import com.royermethod.app.ui.components.Mosaico
import com.royermethod.app.ui.components.Muestras
import com.royermethod.app.ui.components.Nota
import com.royermethod.app.ui.components.Opciones
import com.royermethod.app.ui.components.Pestanas
import com.royermethod.app.ui.components.esquinas
import com.royermethod.app.ui.components.segundos
import com.royermethod.app.ui.theme.Ink
import kotlin.math.max
import kotlin.math.min

/**
 * Hoja de abajo con un panel. Ocupa el sitio de la linea de tiempo y la
 * barra de herramientas: la imagen sigue a la vista y del mismo tamano, asi
 * que cada cambio se ve mientras se hace.
 */
@Composable
fun HojaPanel(titulo: String, onCerrar: () -> Unit, modifier: Modifier = Modifier, contenido: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxSize()
            .background(Ink.Black)
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.White08))
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(titulo, style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 1.2.sp), color = Ink.White, modifier = Modifier.weight(1f))
            BotonIcono(Glifos.listo, onCerrar)
        }
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
            content = contenido
        )
    }
}

@Composable
private fun Hueco(alto: Int = 12) = Spacer(Modifier.height(alto.dp))

// ----------------------------------------------------------------------
// Velocidad
// ----------------------------------------------------------------------

@Composable
fun PanelVelocidad(estado: EstadoEditor) {
    val c = estado.clipElegido ?: return
    Deslizador(
        "Velocidad", c.velocidad, 0.1f, 10f,
        onCambio = { v -> estado.cambiarClip(arrastre = true) { it.conVelocidad(v) } },
        neutro = 1f, escala = Escala.LOGARITMICA,
        texto = { "%.2gx".format(it) }
    )
    Opciones(listOf(0.25f, 0.5f, 0.75f, 1f, 1.5f, 2f, 3f, 5f), c.velocidad, { "%.2gx".format(it) }, { v ->
        estado.cambiarClip { it.conVelocidad(v) }
    })
    Hueco(4)
    Text("Dura ${segundos(c.duracionUs)}", style = MaterialTheme.typography.bodyMedium, color = Ink.White50)
    Hueco(8)
    Interruptor("Mantener el tono", c.mantenerTono, { v -> estado.cambiarClip { it.copy(mantenerTono = v) } },
        "Que la voz no suene aguda al acelerar ni grave al frenar")
    Interruptor("Fluido", c.fluido, { v -> estado.cambiarClip { it.copy(fluido = v) } },
        "Genera los fotogramas que faltan: 30 fps sin tirones y camara lenta real. Exporta mas despacio")
}

// ----------------------------------------------------------------------
// Volumen
// ----------------------------------------------------------------------

@Composable
fun PanelVolumen(estado: EstadoEditor) {
    val a = estado.sonidoElegido
    if (a != null) {
        val id = a.id
        fun cambiar(f: (com.royermethod.app.editor.modelo.PistaAudio) -> com.royermethod.app.editor.modelo.PistaAudio) =
            estado.ajustar { Edicion.cambiarAudio(it, id, f) }
        val maxFundido = min(a.duracionUs / 2, 10_000_000L) / 1_000_000f
        Deslizador("Volumen", a.volumen, 0f, 2f, { v -> cambiar { it.copy(volumen = v) } }, neutro = 1f, texto = { "${(it * 100).toInt()}%" })
        Deslizador("Aparece poco a poco", a.fundidoEntradaUs / 1e6f, 0f, maxFundido, { v -> cambiar { it.copy(fundidoEntradaUs = (v * 1e6).toLong()) } }, paso = 0.1f, texto = { "%.1f s".format(it) })
        Deslizador("Se va poco a poco", a.fundidoSalidaUs / 1e6f, 0f, maxFundido, { v -> cambiar { it.copy(fundidoSalidaUs = (v * 1e6).toLong()) } }, paso = 0.1f, texto = { "%.1f s".format(it) })
        return
    }
    val c = estado.clipElegido ?: return
    val maxFundido = min(c.duracionUs / 2, 10_000_000L) / 1_000_000f
    Deslizador("Volumen", c.volumen, 0f, 2f, { v -> estado.cambiarClip(true) { it.copy(volumen = v) } }, neutro = 1f, texto = { "${(it * 100).toInt()}%" })
    Deslizador("Aparece poco a poco", c.fundidoEntradaUs / 1e6f, 0f, maxFundido, { v -> estado.cambiarClip(true) { it.copy(fundidoEntradaUs = (v * 1e6).toLong()) } }, paso = 0.1f, texto = { "%.1f s".format(it) })
    Deslizador("Se va poco a poco", c.fundidoSalidaUs / 1e6f, 0f, maxFundido, { v -> estado.cambiarClip(true) { it.copy(fundidoSalidaUs = (v * 1e6).toLong()) } }, paso = 0.1f, texto = { "%.1f s".format(it) })
}

// ----------------------------------------------------------------------
// Color
// ----------------------------------------------------------------------

private class Ajustito(val nombre: String, val desde: Float, val leer: (AjustesColor) -> Float, val poner: (AjustesColor, Float) -> AjustesColor)

private val AJUSTES = listOf(
    Ajustito("Brillo", -1f, { it.brillo }, { a, v -> a.copy(brillo = v) }),
    Ajustito("Contraste", -1f, { it.contraste }, { a, v -> a.copy(contraste = v) }),
    Ajustito("Saturacion", -1f, { it.saturacion }, { a, v -> a.copy(saturacion = v) }),
    Ajustito("Exposicion", -1f, { it.exposicion }, { a, v -> a.copy(exposicion = v) }),
    Ajustito("Calidez", -1f, { it.calidez }, { a, v -> a.copy(calidez = v) }),
    Ajustito("Tinte", -1f, { it.tinte }, { a, v -> a.copy(tinte = v) }),
    Ajustito("Sombras", -1f, { it.sombras }, { a, v -> a.copy(sombras = v) }),
    Ajustito("Luces", -1f, { it.luces }, { a, v -> a.copy(luces = v) }),
    Ajustito("Nitidez", 0f, { it.nitidez }, { a, v -> a.copy(nitidez = v) }),
    Ajustito("Vineta", 0f, { it.vineta }, { a, v -> a.copy(vineta = v) })
)

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

@Composable
fun PanelColor(estado: EstadoEditor) {
    val c = estado.clipElegido ?: return
    var pestana by rememberSaveable { mutableIntStateOf(0) }
    Pestanas(listOf("Filtros", "Ajustes"), pestana, { pestana = it })
    Hueco(6)
    if (pestana == 0) {
        Cuadricula(Filtro.values().toList()) { f, m ->
            val colores = muestraDe(f)
            Mosaico(f.nombre, c.filtro == f, { estado.cambiarClip { it.copy(filtro = f) } }, m) { _ ->
                drawCircle(colores[0], size.minDimension * 0.34f, Offset(size.width * 0.38f, size.height * 0.5f))
                drawCircle(colores[1].copy(alpha = 0.85f), size.minDimension * 0.34f, Offset(size.width * 0.62f, size.height * 0.5f))
            }
        }
        if (c.filtro != Filtro.NINGUNO) {
            Hueco(6)
            Deslizador("Intensidad", c.intensidadFiltro, 0f, 1f, { v -> estado.cambiarClip(true) { it.copy(intensidadFiltro = v) } }, neutro = 1f, texto = { "${(it * 100).toInt()}%" })
        }
    } else {
        AJUSTES.forEach { a ->
            Deslizador(a.nombre, a.leer(c.color), a.desde, 1f, { v -> estado.cambiarClip(true) { it.copy(color = a.poner(it.color, v)) } },
                neutro = 0f, texto = { if (a.desde < 0) "%+d".format((it * 100).toInt()) else "${(it * 100).toInt()}" })
        }
    }
    Hueco(10)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        BotonBorde("Restablecer", { estado.cambiarClip { it.copy(color = AjustesColor(), filtro = Filtro.NINGUNO, intensidadFiltro = 1f) } }, glifo = Glifos.reiniciar)
        BotonBorde("Aplicar a todos", {
            estado.hacer { p ->
                p.copy(principal = p.principal.map { it.copy(color = c.color, filtro = c.filtro, intensidadFiltro = c.intensidadFiltro) })
            }
            estado.avisar("Color aplicado a toda la pista principal")
        })
    }
}

// ----------------------------------------------------------------------
// Encuadre
// ----------------------------------------------------------------------

@Composable
fun PanelEncuadre(estado: EstadoEditor) {
    val c = estado.clipElegido ?: return
    val id = c.id
    val e = c.encuadreEn(estado.localElegido ?: 0)
    fun cambiar(arrastre: Boolean = true, f: (com.royermethod.app.editor.modelo.Encuadre) -> com.royermethod.app.editor.modelo.Encuadre) {
        val g: (com.royermethod.app.editor.modelo.Proyecto) -> com.royermethod.app.editor.modelo.Proyecto = { Edicion.ajustarEncuadre(it, id, estado.tiempo, f) }
        if (arrastre) estado.ajustar(g) else estado.hacer(g)
    }
    if (c.claves.isNotEmpty()) {
        Nota("Tiene fotogramas clave: lo que cambies aqui se guarda en el del cabezal", color = Ink.White50)
        Hueco(6)
    }
    if (!c.esTexto) {
        Opciones(Ajuste.values().toList(), e.ajuste, { it.nombre }, { a -> cambiar(false) { it.copy(ajuste = a) } })
    }
    Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        BotonBorde("Espejo", { cambiar(false) { it.copy(espejo = !it.espejo) } }, glifo = Glifos.espejo)
        BotonBorde("Girar", { cambiar(false) { it.copy(rotacion = ((it.rotacion + 90f + 180f) % 360f) - 180f) } }, glifo = Glifos.girar)
        BotonBorde("Centrar", { cambiar(false) { it.copy(x = 0f, y = 0f, escala = 1f, rotacion = 0f) } }, glifo = Glifos.reiniciar)
    }
    Deslizador("Tamano", e.escala, 0.1f, 6f, { v -> cambiar { it.copy(escala = v) } }, neutro = 1f, escala = Escala.LOGARITMICA, texto = { "${(it * 100).toInt()}%" })
    Deslizador("Horizontal", e.x, -1f, 1f, { v -> cambiar { it.copy(x = v) } }, neutro = 0f, texto = { "%+d".format((it * 100).toInt()) })
    Deslizador("Vertical", e.y, -1f, 1f, { v -> cambiar { it.copy(y = v) } }, neutro = 0f, texto = { "%+d".format((it * 100).toInt()) })
    Deslizador("Giro", e.rotacion, -180f, 180f, { v -> cambiar { it.copy(rotacion = v) } }, neutro = 0f, texto = { "${it.toInt()}°" })
    Deslizador("Opacidad", e.opacidad, 0f, 1f, { v -> cambiar { it.copy(opacidad = v) } }, neutro = 1f, texto = { "${(it * 100).toInt()}%" })
    if (!c.esTexto) {
        Hueco(6)
        Etiqueta("Recortar")
        val r = e.recorte
        fun rc(f: (Recorte) -> Recorte) = cambiar { it.copy(recorte = f(it.recorte)) }
        Deslizador("Izquierda", r.izquierda, 0f, 0.45f, { v -> rc { it.copy(izquierda = v) } }, texto = { "${(it * 100).toInt()}%" })
        Deslizador("Derecha", r.derecha, 0f, 0.45f, { v -> rc { it.copy(derecha = v) } }, texto = { "${(it * 100).toInt()}%" })
        Deslizador("Arriba", r.arriba, 0f, 0.45f, { v -> rc { it.copy(arriba = v) } }, texto = { "${(it * 100).toInt()}%" })
        Deslizador("Abajo", r.abajo, 0f, 0.45f, { v -> rc { it.copy(abajo = v) } }, texto = { "${(it * 100).toInt()}%" })
    }
    val capa = estado.capaElegida
    if (capa != null) {
        Hueco(6)
        Etiqueta("Mezcla con lo de abajo")
        Opciones(Mezcla.values().toList(), capa.mezcla, { it.nombre }, { m -> estado.hacer { Edicion.cambiarCapa(it, id) { k -> k.copy(mezcla = m) } } })
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

@Composable
fun PanelTexto(estado: EstadoEditor) {
    val c = estado.clipElegido ?: return
    val t = c.texto ?: return
    fun cambiar(arrastre: Boolean = false, f: (Texto) -> Texto) = estado.cambiarClip(arrastre) { it.copy(texto = f(it.texto!!)) }
    // Un texto recien puesto llega con el cursor dentro y todo elegido:
    // se escribe encima sin tener que borrar nada.
    val nuevo = t.contenido == TEXTO_NUEVO
    val foco = remember { FocusRequester() }
    var valor by remember(c.id) {
        mutableStateOf(TextFieldValue(t.contenido, if (nuevo) TextRange(0, t.contenido.length) else TextRange(t.contenido.length)))
    }
    // Si cambio por fuera (deshacer), se sigue al proyecto
    if (valor.text != t.contenido) valor = TextFieldValue(t.contenido, TextRange(t.contenido.length))
    LaunchedEffect(c.id) { if (nuevo) runCatching { foco.requestFocus() } }
    BasicTextField(
        value = valor,
        onValueChange = { v ->
            valor = v
            if (v.text != t.contenido) cambiar(true) { it.copy(contenido = v.text) }
        },
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink.White),
        cursorBrush = SolidColor(Ink.White),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .border(1.dp, Ink.White30, esquinas(12.dp))
            .padding(12.dp)
            .focusRequester(foco)
            .onFocusChanged { if (it.isFocused) estado.empezarGesto() else estado.terminarGesto() }
    )
    Hueco(8)
    Opciones(Fuente.values().toList(), t.fuente, { it.nombre }, { f -> cambiar { it.copy(fuente = f) } })
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        Chip("Negrita", t.negrita, { cambiar { it.copy(negrita = !it.negrita) } })
        Chip("Sombra", t.sombra, { cambiar { it.copy(sombra = !it.sombra) } })
        Alineacion.values().forEach { a ->
            Chip(when (a) { Alineacion.IZQUIERDA -> "Izq"; Alineacion.CENTRO -> "Centro"; Alineacion.DERECHA -> "Der" }, t.alineacion == a, { cambiar { it.copy(alineacion = a) } })
        }
    }
    Deslizador("Tamano", t.tamano, 0.02f, 0.2f, { v -> cambiar(true) { it.copy(tamano = v) } }, neutro = 0.055f, texto = { "${(it * 1000).toInt()}" })
    Etiqueta("Color")
    Muestras(COLORES_TEXTO, t.color, { v -> cambiar { it.copy(color = v) } })
    Deslizador("Contorno", t.contorno, 0f, 0.3f, { v -> cambiar(true) { it.copy(contorno = v) } }, texto = { "${(it * 100).toInt()}" })
    if (t.contorno > 0f) Muestras(COLORES_TEXTO, t.colorContorno, { v -> cambiar { it.copy(colorContorno = v) } })
    Etiqueta("Caja de fondo")
    Muestras(COLORES_TEXTO.map { (it and 0x00FFFFFF) or (0xB0 shl 24) }, t.fondo, { v -> cambiar { it.copy(fondo = v) } }, conNinguno = true)
    Deslizador("Espacio entre letras", t.espaciado, -0.1f, 0.5f, { v -> cambiar(true) { it.copy(espaciado = v) } }, neutro = 0f, texto = { "%+d".format((it * 100).toInt()) })
}

// ----------------------------------------------------------------------
// Formato
// ----------------------------------------------------------------------

@Composable
fun PanelFormato(estado: EstadoEditor) {
    val p = estado.proyecto
    Etiqueta("Proporcion")
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Formato.values().forEach { f ->
            Mosaico(f.nombre, p.formato == f, { estado.hacer { it.copy(formato = f) } }) { col ->
                val a = f.ancho.toFloat() / f.alto
                val w = if (a >= 1) size.width * 0.9f else size.height * 0.9f * a
                val h = if (a >= 1) size.width * 0.9f / a else size.height * 0.9f
                drawRoundRect(col, Offset((size.width - w) / 2, (size.height - h) / 2), androidx.compose.ui.geometry.Size(w, h),
                    androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()), style = Stroke(1.6.dp.toPx()))
            }
        }
    }
    Etiqueta("Fondo donde el video no llega")
    Opciones(Fondo.values().toList(), p.fondo, { it.nombre }, { f -> estado.hacer { it.copy(fondo = f) } })
    Hueco(20)
    // Empezar de cero borra todo lo que hay: pide un segundo toque
    var seguro by remember { mutableStateOf(false) }
    if (!seguro) {
        BotonBorde("Empezar proyecto nuevo", { seguro = true }, glifo = Glifos.mas)
    } else {
        Nota("Se quita todo lo que hay en este proyecto.")
        Hueco(6)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BotonLleno("Si, empezar de cero", {
                estado.hacer { AjustesEditor.proyectoNuevo() }
                estado.panel = null
                estado.irA(0)
                estado.avisar("Proyecto nuevo. Si fue sin querer, deshaz")
            })
            BotonBorde("No", { seguro = false })
        }
    }
}

// ----------------------------------------------------------------------
// Fotograma clave
// ----------------------------------------------------------------------

/** Dibuja la forma de una curva dentro de la casilla. */
fun DrawScope.dibujoCurva(f: (Float) -> Float, color: Color) {
    val path = Path()
    val n = 32
    for (i in 0..n) {
        val x = i / n.toFloat()
        val y = f(x)
        val px = x * size.width
        val py = size.height * (0.85f - 0.7f * y)
        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }
    drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
}

@Composable
fun PanelCurva(estado: EstadoEditor) {
    val c = estado.clipElegido ?: return
    val local = estado.localElegido
    val clave = local?.let { l -> c.claves.firstOrNull { kotlin.math.abs(it.tiempoUs - l) <= Edicion.TOLERANCIA_CLAVE } }
    if (clave == null) {
        Text(
            "Pon el cabezal sobre un fotograma clave (los rombos del bloque) para cambiar como se mueve hasta el siguiente.",
            style = MaterialTheme.typography.bodyMedium, color = Ink.White70
        )
        Hueco()
        BotonLleno("Poner uno aqui", {
            val ini = estado.inicioElegido ?: return@BotonLleno
            estado.hacer { Edicion.ponerClave(it, c.id, estado.tiempo, c.encuadreEn(estado.tiempo - ini)) }
        }, activo = local != null)
        return
    }
    val t = estado.tiempo
    fun poner(f: (com.royermethod.app.editor.modelo.Clave) -> com.royermethod.app.editor.modelo.Clave, arrastre: Boolean = false) {
        val g: (com.royermethod.app.editor.modelo.Proyecto) -> com.royermethod.app.editor.modelo.Proyecto = { Edicion.cambiarClave(it, c.id, t, f) }
        if (arrastre) estado.ajustar(g) else estado.hacer(g)
    }
    val ultima = c.claves.maxOf { it.tiempoUs } == clave.tiempoUs
    Nota(if (ultima) "Es el ultimo fotograma clave: la curva se usa si pones otro despues." else "Como se llega desde aqui al siguiente fotograma clave", color = Ink.White50)
    Hueco(8)
    // null es "A mano"
    Cuadricula(Curva.values().toList() + listOf<Curva?>(null)) { cu, m ->
        if (cu != null) {
            Mosaico(cu.nombre, clave.bezier == null && clave.curva == cu, { poner({ it.copy(curva = cu, bezier = null) }) }, m) { col ->
                dibujoCurva({ Escena.curva(cu, it) }, col)
            }
        } else {
            Mosaico("A mano", clave.bezier != null, { poner({ it.copy(bezier = it.bezier ?: Bezier.SUAVE) }) }, m) { col ->
                dibujoCurva({ Bezier(0.1f, 0.9f, 0.4f, 0.1f).en(it) }, col)
                drawCircle(col, 2.5.dp.toPx(), Offset(size.width * 0.1f, size.height * (0.85f - 0.7f * 0.9f)))
            }
        }
    }
    val b = clave.bezier
    if (b != null) {
        Hueco(10)
        EditorBezier(b, { nb -> poner({ it.copy(bezier = nb) }, arrastre = true) })
    }
    Hueco(10)
    BotonBorde("Quitar este fotograma clave", { estado.hacer { Edicion.quitarClave(it, c.id, t) } }, glifo = Glifos.papelera)
}

/**
 * Curva libre con dos puntos que se arrastran. El eje de abajo es el tiempo
 * y el vertical cuanto se ha llegado: mas inclinada, mas rapido.
 */
@Composable
private fun EditorBezier(b: Bezier, onCambio: (Bezier) -> Unit) {
    val gestos = LocalGestos.current
    val actual by androidx.compose.runtime.rememberUpdatedState(b)
    val cambio by androidx.compose.runtime.rememberUpdatedState(onCambio)
    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1.8f)
            .border(1.dp, Ink.White15, esquinas(12.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val abajo = awaitFirstDown()
                    val m = 18.dp.toPx()
                    val w = size.width - 2 * m
                    val h = size.height - 2 * m
                    fun aPx(x: Float, y: Float) = Offset(m + x * w, m + h * (1f - (y + 0.3f) / 1.6f))
                    val p1 = aPx(actual.x1, actual.y1)
                    val p2 = aPx(actual.x2, actual.y2)
                    val primero = (abajo.position - p1).getDistance() <= (abajo.position - p2).getDistance()
                    gestos.empezar()
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == abajo.id } ?: break
                        if (!ch.pressed) break
                        val x = ((ch.position.x - m) / w).coerceIn(0f, 1f)
                        val y = (1f - (ch.position.y - m) / h) * 1.6f - 0.3f
                        val yy = y.coerceIn(-0.3f, 1.3f)
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
        fun aPx(x: Float, y: Float) = Offset(m + x * w, m + h * (1f - (y + 0.3f) / 1.6f))
        val cero = aPx(0f, 0f)
        val uno = aPx(1f, 1f)
        drawLine(Ink.White15, aPx(0f, 0f), aPx(1f, 0f), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
        drawLine(Ink.White15, aPx(0f, 1f), aPx(1f, 1f), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
        val p1 = aPx(b.x1, b.y1)
        val p2 = aPx(b.x2, b.y2)
        drawLine(Ink.White30, cero, p1, 1.5.dp.toPx())
        drawLine(Ink.White30, uno, p2, 1.5.dp.toPx())
        val path = Path()
        for (i in 0..48) {
            val x = i / 48f
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

/** Para los paneles que no tienen nada que ensenar con lo elegido. */
@Composable
fun SinNada(texto: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 30.dp), contentAlignment = Alignment.Center) {
        Text(texto, style = MaterialTheme.typography.bodyMedium, color = Ink.White50)
    }
}

internal fun maxDuracion(c: Clip, desde: Long = 100_000L): Float = max(desde, min(c.duracionUs, 5_000_000L)) / 1_000_000f
