package com.royermethod.app.editor.ui

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.royermethod.app.editor.modelo.Alineacion
import com.royermethod.app.editor.modelo.Edicion
import com.royermethod.app.editor.modelo.Encuadre
import com.royermethod.app.editor.modelo.Fuente
import com.royermethod.app.editor.modelo.GrupoPropiedad
import com.royermethod.app.editor.modelo.Perspectiva
import com.royermethod.app.editor.modelo.Proyecto
import com.royermethod.app.editor.modelo.Texto
import com.royermethod.app.editor.modelo.propiedadesEn
import com.royermethod.app.editor.motor.MaquetaTexto
import com.royermethod.app.ui.components.Chip
import com.royermethod.app.ui.components.Deslizador
import com.royermethod.app.ui.components.Fila
import com.royermethod.app.ui.components.Letra
import com.royermethod.app.ui.components.Mosaico
import com.royermethod.app.ui.components.Muestras
import com.royermethod.app.ui.components.Nota
import com.royermethod.app.ui.components.Pestanas
import com.royermethod.app.ui.components.Tira
import com.royermethod.app.ui.components.esquinas
import com.royermethod.app.ui.theme.Ink
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val PESTANAS_TEXTO = listOf("Letra", "Borde", "Sombra", "Fondo", "3D")

/**
 * La hoja del texto. Arriba, siempre a la vista, lo que se escribe y las
 * pestanas (con un punto las que ya tienen algo puesto); debajo, lo de la
 * elegida. Lo que se cambia se ve en la imagen, que sigue encima: aqui no hay
 * muestras aparte.
 */
@Composable
fun HojaTexto(estado: EstadoEditor, onCerrar: () -> Unit) {
    val c = estado.clipElegido ?: return
    val local = estado.localElegido ?: 0L
    val t = c.propiedadesEn(local).texto ?: return
    var pestana by rememberSaveable { mutableIntStateOf(0) }
    var animar by rememberSaveable(c.id) { mutableStateOf(false) }
    val inclinado = c.encuadreEn(local.coerceIn(0L, c.duracionUs)).conPerspectiva
    HojaPanel(Panel.TEXTO.titulo, onCerrar = onCerrar, acciones = {
        Chip("Animar", animar, { animar = !animar }, marca = c.claves.isNotEmpty() || c.propiedades.keys.any { it.grupo == GrupoPropiedad.TEXTO })
    }, claveDesplazamiento = pestana, fijo = {
        CampoTexto(estado, c.id, t)
        Pestanas(PESTANAS_TEXTO, pestana, { pestana = it },
            marcas = setOfNotNull(1.takeIf { t.contorno > 0f }, 2.takeIf { t.sombra }, 3.takeIf { t.fondo != 0 }, 4.takeIf { inclinado }))
        if (animar) TiempoPropiedades(estado, GrupoPropiedad.TEXTO, true)
    }) {
        if (animar) OpcionesPropiedades(estado, GrupoPropiedad.TEXTO, true)
        when (pestana) {
            0 -> TextoLetra(estado, t)
            1 -> TextoBorde(estado, t)
            2 -> TextoSombra(estado, t)
            3 -> TextoFondo(estado, t)
            else -> TextoPerspectiva(estado, c.id)
        }
    }
}

private fun cambiar(estado: EstadoEditor, arrastre: Boolean = false, f: (Texto) -> Texto) =
    estado.cambiarAspecto(arrastre) { it.copy(texto = f(it.texto!!)) }

@Composable
private fun CampoTexto(estado: EstadoEditor, id: Long, t: Texto) {
    // Un texto recien puesto llega con el cursor dentro y todo elegido:
    // se escribe encima sin tener que borrar nada.
    val nuevo = t.contenido == TEXTO_NUEVO
    val foco = remember { FocusRequester() }
    var valor by remember(id) {
        mutableStateOf(TextFieldValue(t.contenido, if (nuevo) TextRange(0, t.contenido.length) else TextRange(t.contenido.length)))
    }
    // Si cambio por fuera (deshacer), se sigue al proyecto
    if (valor.text != t.contenido) valor = TextFieldValue(t.contenido, TextRange(t.contenido.length))
    LaunchedEffect(id) { if (nuevo) runCatching { foco.requestFocus() } }
    BasicTextField(
        value = valor,
        onValueChange = { v ->
            valor = v
            if (v.text != t.contenido) cambiar(estado, true) { it.copy(contenido = v.text) }
        },
        textStyle = Letra.titulo.copy(color = Ink.White, fontWeight = FontWeight.Normal),
        cursorBrush = SolidColor(Ink.White),
        // Tres lineas a la vista; lo demas se desliza dentro del campo
        maxLines = 3,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .clip(esquinas(10.dp))
            .background(Ink.White08)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics { contentDescription = "Lo que dice el texto" }
            .focusRequester(foco)
            .onFocusChanged { if (it.isFocused) estado.empezarGesto() else estado.terminarGesto() }
    )
}

private fun familia(f: Fuente, negrita: Boolean = false): FontFamily = FontFamily(MaquetaTexto.tipo(f, negrita))

/** Una casilla con una muestra de letra y su nombre debajo, con las medidas de Mosaico. */
@Composable
private fun Casilla(nombre: String, elegida: Boolean, onClick: () -> Unit, contenido: @Composable () -> Unit) {
    Column(
        Modifier
            .width(60.dp)
            .semantics { selected = elegida }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(esquinas(12.dp))
                .background(if (elegida) Ink.White15 else Ink.White08)
                .border(1.5.dp, if (elegida) Ink.White else Color.Transparent, esquinas(12.dp)),
            contentAlignment = Alignment.Center
        ) { contenido() }
        Spacer(Modifier.height(4.dp))
        Text(nombre, style = Letra.mini, color = if (elegida) Ink.White else Ink.White70, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

/** Un interruptor cuadrado con una letra o un dibujo: negrita, cursiva, alineacion. */
@Composable
private fun Tecla(descripcion: String, activa: Boolean, onClick: () -> Unit, contenido: @Composable (Color) -> Unit) {
    Box(
        Modifier
            .size(42.dp, 36.dp)
            .clip(esquinas(9.dp))
            .background(if (activa) Ink.White else Ink.White08)
            .semantics { contentDescription = descripcion; selected = activa }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { contenido(if (activa) Ink.Black else Ink.White90) }
}

// ---------------------------------------------------------------- Letra

/** Mas tonos que los de siempre: los basicos, pasteles y oscuros. */
private val COLORES_AMPLIOS = (COLORES_TEXTO + listOf(
    0xFFFFD6E0, 0xFFFFE8C2, 0xFFFFF6B3, 0xFFC9FFD9, 0xFFC2EEFF, 0xFFE0D6FF,
    0xFFB00020, 0xFFB35C00, 0xFF8A7A00, 0xFF00704A, 0xFF005A8C, 0xFF3D2B99,
    0xFF2B2B2B, 0xFF5C5C5C, 0xFFBDBDBD
).map { it.toInt() }).distinct()

/** La fuente (cada una en su letra), el color, el estilo y las medidas. */
@Composable
private fun TextoLetra(estado: EstadoEditor, t: Texto) {
    Tira(Modifier.padding(top = 2.dp, bottom = 4.dp)) {
        for (f in Fuente.values()) {
            Casilla(Rotulos.de(f.nombre), t.fuente == f, { cambiar(estado) { it.copy(fuente = f) } }) {
                Text("Aa", style = TextStyle(fontSize = 21.sp, color = Ink.White, fontFamily = familia(f, t.negrita)))
            }
        }
    }
    Fila("Color") { Muestras(COLORES_AMPLIOS, t.color, { v -> cambiar(estado) { it.copy(color = v) } }, Modifier.weight(1f)) }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        val letra = TextStyle(fontSize = 17.sp)
        Tecla("Negrita", t.negrita, { cambiar(estado) { it.copy(negrita = !it.negrita) } }) { col ->
            Text("B", style = letra.copy(fontWeight = FontWeight.Black, color = col))
        }
        Tecla("Cursiva", t.cursiva, { cambiar(estado) { it.copy(cursiva = !it.cursiva) } }) { col ->
            Text("I", style = letra.copy(fontStyle = FontStyle.Italic, fontFamily = FontFamily.Serif, color = col))
        }
        Tecla("Subrayado", t.subrayado, { cambiar(estado) { it.copy(subrayado = !it.subrayado) } }) { col ->
            Text("U", style = letra.copy(textDecoration = TextDecoration.Underline, color = col))
        }
        Spacer(Modifier.weight(1f))
        for (a in Alineacion.values()) {
            val nombre = when (a) { Alineacion.IZQUIERDA -> "A la izquierda"; Alineacion.CENTRO -> "Al centro"; Alineacion.DERECHA -> "A la derecha" }
            Tecla(nombre, t.alineacion == a, { cambiar(estado) { it.copy(alineacion = a) } }) { col ->
                Canvas(Modifier.size(18.dp)) { lineasAlineadas(a, col) }
            }
        }
    }
    Deslizador("Tamaño", t.tamano, 0.02f, 0.2f, { v -> cambiar(estado, true) { it.copy(tamano = v) } }, neutro = 0.055f, texto = { "${(it * 1000).toInt()}" })
    Deslizador("Espaciado", t.espaciado, -0.1f, 0.5f, { v -> cambiar(estado, true) { it.copy(espaciado = v) } }, neutro = 0f, texto = { "%+d".format((it * 100).toInt()) })
    Deslizador("Interlineado", t.interlineado, 0.6f, 2.5f, { v -> cambiar(estado, true) { it.copy(interlineado = v) } }, neutro = 1f, texto = { "%.1f".format(it) })
}

private fun DrawScope.lineasAlineadas(a: Alineacion, col: Color) {
    val anchos = listOf(1f, 0.6f, 0.85f, 0.5f)
    anchos.forEachIndexed { i, f ->
        val w = size.width * f
        val y = size.height * (0.15f + i * 0.23f)
        val x0 = when (a) { Alineacion.IZQUIERDA -> 0f; Alineacion.CENTRO -> (size.width - w) / 2; Alineacion.DERECHA -> size.width - w }
        drawLine(col, Offset(x0, y), Offset(x0 + w, y), 1.8.dp.toPx(), cap = StrokeCap.Round)
    }
}

// ---------------------------------------------------------------- Borde

@Composable
private fun TextoBorde(estado: EstadoEditor, t: Texto) {
    Deslizador("Grosor", t.contorno, 0f, 0.3f, { v -> cambiar(estado, true) { it.copy(contorno = v) } },
        texto = { if (it <= 0f) "Sin borde" else "${(it * 100).toInt()}" })
    Fila("Color") {
        Muestras(COLORES_AMPLIOS, t.colorContorno, { v ->
            // Elegir color sin borde lo pone fino: si no, no se veria nada
            cambiar(estado) { it.copy(colorContorno = v, contorno = if (it.contorno > 0f) it.contorno else 0.08f) }
        }, Modifier.weight(1f))
    }
}

// ---------------------------------------------------------------- Sombra

private data class SombraLista(val nombre: String, val color: Int?, val opacidad: Float, val desenfoque: Float, val distancia: Float, val angulo: Float)

private val SOMBRAS = listOf(
    SombraLista("Suave", null, 150f / 255f, 0.12f, 0.05f, 90f),
    SombraLista("Dura", null, 0.85f, 0.01f, 0.08f, 45f),
    SombraLista("Lejana", null, 0.5f, 0.2f, 0.16f, 70f),
    SombraLista("Larga", null, 0.9f, 0f, 0.2f, 45f),
    SombraLista("Brillo", 0xFFFFFFFF.toInt(), 0.85f, 0.3f, 0f, 90f),
    SombraLista("Neón", 0xFF2EC4FF.toInt(), 1f, 0.35f, 0f, 90f)
)

/** Sombras hechas en una tira ("Ninguna" la quita) y, con sombra, su color y sus medidas. */
@Composable
private fun TextoSombra(estado: EstadoEditor, t: Texto) {
    Tira(Modifier.padding(top = 2.dp, bottom = 4.dp)) {
        Mosaico("Ninguna", !t.sombra, { cambiar(estado) { it.copy(sombra = false) } }) { col -> dibujoNinguna(col) }
        for (s in SOMBRAS) {
            val elegida = t.sombra && (s.color == null || s.color == t.sombraColor) && abs(s.opacidad - t.sombraOpacidad) < 0.01f &&
                abs(s.desenfoque - t.sombraDesenfoque) < 0.005f && abs(s.distancia - t.sombraDistancia) < 0.005f
            Casilla(s.nombre, elegida, {
                cambiar(estado) {
                    it.copy(sombra = true, sombraColor = s.color ?: it.sombraColor, sombraOpacidad = s.opacidad, sombraDesenfoque = s.desenfoque,
                        sombraDistancia = s.distancia, sombraAngulo = s.angulo)
                }
            }) {
                Text("A", style = TextStyle(fontSize = 23.sp, fontWeight = FontWeight.Black, color = Ink.White, shadow = sombraCompose(
                    t.copy(sombraColor = s.color ?: t.sombraColor, sombraOpacidad = s.opacidad, sombraDesenfoque = s.desenfoque,
                        sombraDistancia = s.distancia, sombraAngulo = s.angulo), 23f)))
            }
        }
    }
    if (!t.sombra) return
    if (t.fondo != 0) Nota("Con fondo, la sombra no se ve.", Modifier.padding(bottom = 2.dp), color = Ink.Peligro)
    Fila("Color") { Muestras(COLORES_AMPLIOS, (t.sombraColor or (0xFF shl 24)), { v -> cambiar(estado) { it.copy(sombraColor = v) } }, Modifier.weight(1f)) }
    Deslizador("Opacidad", t.sombraOpacidad, 0f, 1f, { v -> cambiar(estado, true) { it.copy(sombraOpacidad = v) } }, texto = { "${(it * 100).roundToInt()}%" })
    Deslizador("Desenfoque", t.sombraDesenfoque, 0f, 0.4f, { v -> cambiar(estado, true) { it.copy(sombraDesenfoque = v) } }, texto = { "${(it * 100).roundToInt()}" })
    Deslizador("Distancia", t.sombraDistancia, 0f, 0.2f, { v -> cambiar(estado, true) { it.copy(sombraDistancia = v) } }, texto = { "${(it * 100).roundToInt()}" })
    Deslizador("Dirección", ((t.sombraAngulo % 360f) + 360f) % 360f, 0f, 359f, { v -> cambiar(estado, true) { it.copy(sombraAngulo = v) } },
        neutro = 90f, texto = { "${it.roundToInt()}°" })
}

/** La sombra del texto [t] para una muestra de letra de [tam] sp (en px aprox.). */
private fun sombraCompose(t: Texto, tam: Float): Shadow {
    val ang = Math.toRadians(t.sombraAngulo.toDouble())
    val d = t.sombraDistancia * tam * 2.6f
    return Shadow(
        Color(t.sombraColor).copy(alpha = t.sombraOpacidad.coerceIn(0f, 1f)),
        Offset((cos(ang) * d).toFloat(), (sin(ang) * d).toFloat()),
        (t.sombraDesenfoque * tam * 2.6f).coerceAtLeast(0.5f)
    )
}

// ---------------------------------------------------------------- Fondo

/** La caja de detras de las letras: su color (o ninguna) y cuanto tapa. */
@Composable
private fun TextoFondo(estado: EstadoEditor, t: Texto) {
    val opacidad = if (t.fondo == 0) 0xB0 else (t.fondo ushr 24)
    Fila("Color") {
        Muestras(COLORES_AMPLIOS.map { (it and 0x00FFFFFF) or (0xFF shl 24) }, if (t.fondo == 0) 0 else (t.fondo or (0xFF shl 24)), { v ->
            cambiar(estado) { it.copy(fondo = if (v == 0) 0 else (v and 0x00FFFFFF) or (opacidad shl 24)) }
        }, Modifier.weight(1f), conNinguno = true)
    }
    if (t.fondo != 0) {
        Deslizador("Opacidad", (t.fondo ushr 24) / 255f, 0.05f, 1f, { v ->
            cambiar(estado, true) { it.copy(fondo = (it.fondo and 0x00FFFFFF) or ((v * 255f).roundToInt().coerceIn(13, 255) shl 24)) }
        }, texto = { "${(it * 100).roundToInt()}%" })
    }
}

// ---------------------------------------------------------------- Perspectiva

private val PERSPECTIVAS = listOf(
    "Plano" to (0f to 0f),
    "Suelo" to (55f to 0f),
    "Techo" to (-55f to 0f),
    "Izquierda" to (0f to 45f),
    "Derecha" to (0f to -45f),
    "Esquina" to (30f to -30f),
    "Cartel" to (-20f to 35f)
)

/** La forma de una tarjeta con esa perspectiva, centrada en la casilla. */
private fun DrawScope.tarjeta(inclX: Float, inclY: Float, col: Color, relleno: Boolean) {
    val w = size.width * 0.78f
    val h = size.height * 0.56f
    val esq = Perspectiva.esquinas(inclX, inclY, w, h).map { (x, y) -> Offset(size.width / 2 + x, size.height / 2 + y) }
    val p = Path().apply {
        moveTo(esq[0].x, esq[0].y)
        for (k in 1..3) lineTo(esq[k].x, esq[k].y)
        close()
    }
    if (relleno) drawPath(p, col.copy(alpha = 0.25f))
    drawPath(p, col, style = Stroke(1.6.dp.toPx()))
    // Dos rayas como lineas de texto, para ver hacia donde se tumba
    for (f in listOf(0.38f, 0.62f)) {
        val a = Perspectiva.aplicar(Perspectiva.proyeccion(inclX, inclY, w, h), -w * 0.3f, h * (f - 0.5f))
        val b = Perspectiva.aplicar(Perspectiva.proyeccion(inclX, inclY, w, h), w * 0.3f, h * (f - 0.5f))
        drawLine(col, Offset(size.width / 2 + a.first, size.height / 2 + a.second), Offset(size.width / 2 + b.first, size.height / 2 + b.second),
            1.6.dp.toPx(), cap = StrokeCap.Round)
    }
}

/**
 * Inclinar en 3D: unas posturas hechas ("Plano" la quita) y las dos barras.
 * Es del encuadre: con fotogramas clave se pone en la clave de bajo el
 * cabezal y se anima como la posicion.
 */
@Composable
internal fun TextoPerspectiva(estado: EstadoEditor, id: Long) {
    val c = estado.clipElegido ?: return
    val local = estado.localElegido ?: 0L
    val e = c.encuadreEn(local.coerceIn(0L, c.duracionUs))
    fun poner(arrastre: Boolean, f: (Encuadre) -> Encuadre) {
        val t = estado.tiempo
        val g: (Proyecto) -> Proyecto = { Edicion.ajustarEncuadre(it, id, t, toleranciaClaves(), f) }
        if (arrastre) estado.ajustar(g) else estado.hacer(g)
    }
    Tira(Modifier.padding(top = 2.dp, bottom = 4.dp)) {
        for ((n, a) in PERSPECTIVAS) {
            val elegida = abs(e.inclinacionX - a.first) < 0.5f && abs(e.inclinacionY - a.second) < 0.5f
            Mosaico(n, elegida, { poner(false) { it.copy(inclinacionX = a.first, inclinacionY = a.second) } }) { col ->
                tarjeta(a.first, a.second, col, relleno = elegida)
            }
        }
    }
    val m = Perspectiva.MAXIMA
    Deslizador("Tumbar", e.inclinacionX, -m, m, { v -> poner(true) { it.copy(inclinacionX = v) } }, neutro = 0f, texto = { "${it.roundToInt()}°" })
    Deslizador("Girar", e.inclinacionY, -m, m, { v -> poner(true) { it.copy(inclinacionY = v) } }, neutro = 0f, texto = { "${it.roundToInt()}°" })
}
