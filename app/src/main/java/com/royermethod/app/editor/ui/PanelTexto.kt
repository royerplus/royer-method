package com.royermethod.app.editor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.royermethod.app.editor.modelo.Alineacion
import com.royermethod.app.editor.modelo.Edicion
import com.royermethod.app.editor.modelo.Encuadre
import com.royermethod.app.editor.modelo.Fuente
import com.royermethod.app.editor.modelo.Perspectiva
import com.royermethod.app.editor.modelo.propiedadesEn
import com.royermethod.app.editor.modelo.GrupoPropiedad
import com.royermethod.app.editor.modelo.Texto
import com.royermethod.app.editor.motor.MaquetaTexto
import com.royermethod.app.ui.components.BotonBorde
import com.royermethod.app.ui.components.Deslizador
import com.royermethod.app.ui.components.Etiqueta
import com.royermethod.app.ui.components.Glifos
import com.royermethod.app.ui.components.Interruptor
import com.royermethod.app.ui.components.Muestras
import com.royermethod.app.ui.components.Nota
import com.royermethod.app.ui.components.esquinas
import com.royermethod.app.ui.theme.Ink
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * La hoja del texto: arriba, siempre a la vista, lo que se escribe y las
 * pestanas (cada una con su dibujo); debajo lo de la pestana elegida.
 * Todo con dibujos y una vista previa donde ayuda: las fuentes se ven en su
 * letra, la sombra y la perspectiva en una muestra que cambia al mover.
 */
@Composable
fun HojaTexto(estado: EstadoEditor, onCerrar: () -> Unit) {
    val c = estado.clipElegido ?: return
    val t = c.propiedadesEn(estado.localElegido ?: 0L).texto ?: return
    var pestana by rememberSaveable { mutableStateOf(0) }
    HojaPanel("Texto", onCerrar = onCerrar, fijo = {
        TiempoPropiedades(estado, GrupoPropiedad.TEXTO, true)
        Spacer(Modifier.height(8.dp))
        PestanasTexto(pestana, t) { pestana = it }
        Spacer(Modifier.height(4.dp))
    }) {
        CampoTexto(estado, c.id, t)
        OpcionesPropiedades(estado, GrupoPropiedad.TEXTO, true)
        Spacer(Modifier.height(6.dp))
        when (pestana) {
            0 -> TextoFuente(estado, t)
            1 -> TextoColor(estado, t)
            2 -> TextoBorde(estado, t)
            3 -> TextoSombra(estado, t)
            4 -> TextoFondo(estado, t)
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
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink.White),
        cursorBrush = SolidColor(Ink.White),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .border(1.dp, Ink.White30, esquinas(12.dp))
            .padding(12.dp)
            .focusRequester(foco)
            .onFocusChanged { if (it.isFocused) estado.empezarGesto() else estado.terminarGesto() }
    )
}

private val NOMBRES_PESTANAS = listOf("Fuente", "Color", "Borde", "Sombra", "Fondo", "3D")

/** Las pestanas: un dibujo que dice que hay dentro y el nombre. */
@Composable
private fun PestanasTexto(elegida: Int, t: Texto, onElegir: (Int) -> Unit) {
    // Las seis a lo ancho, sin desplazar: todas a la vista
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        NOMBRES_PESTANAS.forEachIndexed { i, n ->
            val sel = i == elegida
            Column(
                Modifier
                    .weight(1f)
                    .clip(esquinas(10.dp))
                    .background(if (sel) Ink.White15 else Color.Transparent)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onElegir(i) }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) { DibujoPestana(i, t, sel) }
                Spacer(Modifier.height(3.dp))
                Text(n, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp), color = if (sel) Ink.White else Ink.White50, maxLines = 1)
            }
        }
    }
}

@Composable
private fun DibujoPestana(i: Int, t: Texto, sel: Boolean) {
    val col = if (sel) Ink.White else Ink.White70
    val estilo = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Bold, color = col)
    when (i) {
        0 -> Text("Aa", style = estilo.copy(fontFamily = familia(t.fuente)))
        1 -> Box(Modifier.size(20.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color(t.color)).border(1.dp, Ink.White30, androidx.compose.foundation.shape.CircleShape))
        2 -> Canvas(Modifier.size(24.dp)) { letraHueca(col) }
        3 -> Text("A", style = estilo.copy(shadow = Shadow(Color.Black.copy(alpha = 0.9f), Offset(3f, 4f), 5f)))
        4 -> Box(Modifier.clip(esquinas(5.dp)).background(Ink.White30).padding(horizontal = 4.dp)) { Text("A", style = estilo.copy(fontSize = 15.sp)) }
        else -> Canvas(Modifier.size(26.dp)) { tarjeta(30f, -25f, col, relleno = false) }
    }
}

/** Una A hecha de contorno, para la pestana del borde. */
private fun DrawScope.letraHueca(col: Color) {
    val w = size.width
    val h = size.height
    val p = Path().apply {
        moveTo(w * 0.1f, h * 0.92f); lineTo(w * 0.5f, h * 0.08f); lineTo(w * 0.9f, h * 0.92f)
        moveTo(w * 0.27f, h * 0.6f); lineTo(w * 0.73f, h * 0.6f)
    }
    drawPath(p, col, style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
    drawPath(p, Ink.Black, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
}

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

private fun familia(f: Fuente, negrita: Boolean = false): FontFamily = FontFamily(MaquetaTexto.tipo(f, negrita))

/** Una casilla con un dibujo o una muestra y su nombre debajo. */
@Composable
private fun Casilla(nombre: String, elegida: Boolean, onClick: () -> Unit, ancho: Int = 74, contenido: @Composable () -> Unit) {
    Column(
        Modifier
            .width(ancho.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(esquinas(10.dp))
                .background(if (elegida) Ink.White15 else Ink.White08)
                .border(1.5.dp, if (elegida) Ink.White else Color.Transparent, esquinas(10.dp)),
            contentAlignment = Alignment.Center
        ) { contenido() }
        Spacer(Modifier.height(4.dp))
        Text(nombre, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp), color = if (elegida) Ink.White else Ink.White70, maxLines = 1)
    }
}

@Composable
private fun Tira(contenido: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { contenido() }
}

// ---------------------------------------------------------------- Fuente

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TextoFuente(estado: EstadoEditor, t: Texto) {
    Etiqueta("Letra")
    Tira {
        for (f in Fuente.values()) {
            Casilla(f.nombre, t.fuente == f, { cambiar(estado) { it.copy(fuente = f) } }) {
                Text("Aa", style = TextStyle(fontSize = 22.sp, color = Ink.White, fontFamily = familia(f, t.negrita)))
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    Etiqueta("Estilo")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Casilla("Negrita", t.negrita, { cambiar(estado) { it.copy(negrita = !it.negrita) } }, ancho = 64) {
            Text("B", style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Black, color = Ink.White))
        }
        Casilla("Cursiva", t.cursiva, { cambiar(estado) { it.copy(cursiva = !it.cursiva) } }, ancho = 64) {
            Text("I", style = TextStyle(fontSize = 22.sp, fontStyle = FontStyle.Italic, fontFamily = FontFamily.Serif, color = Ink.White))
        }
        for (a in Alineacion.values()) {
            val nombre = when (a) { Alineacion.IZQUIERDA -> "Izquierda"; Alineacion.CENTRO -> "Centro"; Alineacion.DERECHA -> "Derecha" }
            Casilla(nombre, t.alineacion == a, { cambiar(estado) { it.copy(alineacion = a) } }, ancho = 64) {
                Canvas(Modifier.size(26.dp)) { lineasAlineadas(a) }
            }
        }
    }
    Interruptor("Subrayado", t.subrayado, { v -> cambiar(estado) { it.copy(subrayado = v) } })
    Spacer(Modifier.height(8.dp))
    Deslizador("Tamano", t.tamano, 0.02f, 0.2f, { v -> cambiar(estado, true) { it.copy(tamano = v) } }, neutro = 0.055f, texto = { "${(it * 1000).toInt()}" })
    Deslizador("Espacio entre letras", t.espaciado, -0.1f, 0.5f, { v -> cambiar(estado, true) { it.copy(espaciado = v) } }, neutro = 0f, texto = { "%+d".format((it * 100).toInt()) })
    Deslizador("Espacio entre lineas", t.interlineado, 0.6f, 2.5f, { v -> cambiar(estado, true) { it.copy(interlineado = v) } }, neutro = 1f, texto = { "%.1f".format(it) })
}

private fun DrawScope.lineasAlineadas(a: Alineacion) {
    val anchos = listOf(1f, 0.6f, 0.85f, 0.5f)
    anchos.forEachIndexed { i, f ->
        val w = size.width * f
        val y = size.height * (0.15f + i * 0.23f)
        val x0 = when (a) { Alineacion.IZQUIERDA -> 0f; Alineacion.CENTRO -> (size.width - w) / 2; Alineacion.DERECHA -> size.width - w }
        drawLine(Ink.White, Offset(x0, y), Offset(x0 + w, y), 2.dp.toPx(), cap = StrokeCap.Round)
    }
}

// ---------------------------------------------------------------- Color

/** Mas tonos que los de siempre: los basicos, pasteles y oscuros. */
private val COLORES_AMPLIOS = (COLORES_TEXTO + listOf(
    0xFFFFD6E0, 0xFFFFE8C2, 0xFFFFF6B3, 0xFFC9FFD9, 0xFFC2EEFF, 0xFFE0D6FF,
    0xFFB00020, 0xFFB35C00, 0xFF8A7A00, 0xFF00704A, 0xFF005A8C, 0xFF3D2B99,
    0xFF2B2B2B, 0xFF5C5C5C, 0xFFBDBDBD
).map { it.toInt() }).distinct()

@Composable
private fun TextoColor(estado: EstadoEditor, t: Texto) {
    Etiqueta("Color de la letra")
    Muestras(COLORES_AMPLIOS, t.color, { v -> cambiar(estado) { it.copy(color = v) } })
}

// ---------------------------------------------------------------- Borde

@Composable
private fun TextoBorde(estado: EstadoEditor, t: Texto) {
    Etiqueta("Grosor")
    Tira {
        for ((n, g) in listOf("Sin borde" to 0f, "Fino" to 0.05f, "Medio" to 0.1f, "Grueso" to 0.18f, "Enorme" to 0.28f)) {
            Casilla(n, kotlin.math.abs(t.contorno - g) < 0.004f, { cambiar(estado) { it.copy(contorno = g) } }) {
                Text("A", style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color(t.color),
                    shadow = if (g > 0f) Shadow(Color(t.colorContorno), Offset.Zero, 2f + g * 40f) else null))
            }
        }
    }
    Deslizador("Grosor exacto", t.contorno, 0f, 0.3f, { v -> cambiar(estado, true) { it.copy(contorno = v) } }, texto = { "${(it * 100).toInt()}" })
    Etiqueta("Color del borde")
    Muestras(COLORES_AMPLIOS, t.colorContorno, { v ->
        // Elegir color sin borde lo pone fino: si no, no se veria nada
        cambiar(estado) { it.copy(colorContorno = v, contorno = if (it.contorno > 0f) it.contorno else 0.08f) }
    })
}

// ---------------------------------------------------------------- Sombra

private data class SombraLista(val nombre: String, val color: Int?, val opacidad: Float, val desenfoque: Float, val distancia: Float, val angulo: Float)

private val SOMBRAS = listOf(
    SombraLista("Suave", null, 150f / 255f, 0.12f, 0.05f, 90f),
    SombraLista("Dura", null, 0.85f, 0.01f, 0.08f, 45f),
    SombraLista("Lejana", null, 0.5f, 0.2f, 0.16f, 70f),
    SombraLista("Larga", null, 0.9f, 0f, 0.2f, 45f),
    SombraLista("Brillo", 0xFFFFFFFF.toInt(), 0.85f, 0.3f, 0f, 90f),
    SombraLista("Neon", 0xFF2EC4FF.toInt(), 1f, 0.35f, 0f, 90f)
)

@Composable
private fun TextoSombra(estado: EstadoEditor, t: Texto) {
    if (t.fondo != 0) Nota("Con caja de fondo la sombra no se ve: quita el fondo para usarla.", color = Ink.White50)
    Interruptor("Sombra", t.sombra, { v -> cambiar(estado) { it.copy(sombra = v) } })
    if (!t.sombra) return
    MuestraSombra(t)
    Spacer(Modifier.height(10.dp))
    Etiqueta("Estilos")
    Tira {
        for (s in SOMBRAS) {
            val elegida = (s.color == null || s.color == t.sombraColor) && kotlin.math.abs(s.opacidad - t.sombraOpacidad) < 0.01f &&
                kotlin.math.abs(s.desenfoque - t.sombraDesenfoque) < 0.005f && kotlin.math.abs(s.distancia - t.sombraDistancia) < 0.005f
            Casilla(s.nombre, elegida, {
                cambiar(estado) {
                    it.copy(sombraColor = s.color ?: it.sombraColor, sombraOpacidad = s.opacidad, sombraDesenfoque = s.desenfoque,
                        sombraDistancia = s.distancia, sombraAngulo = s.angulo)
                }
            }) {
                Text("A", style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Black, color = Ink.White, shadow = sombraCompose(
                    t.copy(sombraColor = s.color ?: t.sombraColor, sombraOpacidad = s.opacidad, sombraDesenfoque = s.desenfoque,
                        sombraDistancia = s.distancia, sombraAngulo = s.angulo), 24f)))
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    Etiqueta("Color de la sombra")
    Muestras(COLORES_AMPLIOS, (t.sombraColor or (0xFF shl 24)), { v -> cambiar(estado) { it.copy(sombraColor = v) } })
    Deslizador("Intensidad", t.sombraOpacidad, 0f, 1f, { v -> cambiar(estado, true) { it.copy(sombraOpacidad = v) } }, texto = { "${(it * 100).roundToInt()} %" })
    Deslizador("Desenfoque", t.sombraDesenfoque, 0f, 0.4f, { v -> cambiar(estado, true) { it.copy(sombraDesenfoque = v) } }, texto = { "${(it * 100).roundToInt()}" })
    Deslizador("Distancia", t.sombraDistancia, 0f, 0.2f, { v -> cambiar(estado, true) { it.copy(sombraDistancia = v) } }, texto = { "${(it * 100).roundToInt()}" })
    Deslizador("Direccion", ((t.sombraAngulo % 360f) + 360f) % 360f, 0f, 359f, { v -> cambiar(estado, true) { it.copy(sombraAngulo = v) } },
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

/** Una muestra grande de la letra con la sombra de ahora, sobre fondo claro y oscuro. */
@Composable
private fun MuestraSombra(t: Texto) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (fondo in listOf(Color(0xFF8A8F98), Color(0xFF15171A))) {
            Box(
                Modifier.weight(1f).height(70.dp).clip(esquinas(12.dp)).background(fondo),
                contentAlignment = Alignment.Center
            ) {
                Text("Aa", style = TextStyle(fontSize = 34.sp,
                    fontFamily = familia(t.fuente, t.negrita), color = Color(t.color), shadow = sombraCompose(t, 34f)))
            }
        }
    }
}

// ---------------------------------------------------------------- Fondo

@Composable
private fun TextoFondo(estado: EstadoEditor, t: Texto) {
    Etiqueta("Caja de fondo")
    val opacidad = if (t.fondo == 0) 0xB0 else (t.fondo ushr 24)
    Muestras(COLORES_AMPLIOS.map { (it and 0x00FFFFFF) or (0xFF shl 24) }, if (t.fondo == 0) 0 else (t.fondo or (0xFF shl 24)), { v ->
        cambiar(estado) { it.copy(fondo = if (v == 0) 0 else (v and 0x00FFFFFF) or (opacidad shl 24)) }
    }, conNinguno = true)
    if (t.fondo != 0) {
        Deslizador("Opacidad de la caja", (t.fondo ushr 24) / 255f, 0.05f, 1f, { v ->
            cambiar(estado, true) { it.copy(fondo = (it.fondo and 0x00FFFFFF) or ((v * 255f).roundToInt().coerceIn(13, 255) shl 24)) }
        }, texto = { "${(it * 100).roundToInt()} %" })
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

/**
 * Inclinar el texto en 3D. Es del encuadre: con fotogramas clave se pone en
 * la clave de bajo el cabezal y se anima como la posicion.
 */
@Composable
internal fun TextoPerspectiva(estado: EstadoEditor, id: Long) {
    val c = estado.clipElegido ?: return
    val local = estado.localElegido ?: 0L
    val e = c.encuadreEn(local.coerceIn(0L, c.duracionUs))
    fun poner(arrastre: Boolean, f: (Encuadre) -> Encuadre) {
        val t = estado.tiempo
        val g: (com.royermethod.app.editor.modelo.Proyecto) -> com.royermethod.app.editor.modelo.Proyecto =
            { Edicion.ajustarEncuadre(it, id, t, toleranciaClaves(), f) }
        if (arrastre) estado.ajustar(g) else estado.hacer(g)
    }
    Box(
        Modifier.fillMaxWidth().height(110.dp).clip(esquinas(12.dp)).background(Ink.White08),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize().padding(vertical = 10.dp)) { tarjeta(e.inclinacionX, e.inclinacionY, Ink.White, relleno = true) }
    }
    Spacer(Modifier.height(10.dp))
    Tira {
        for ((n, a) in PERSPECTIVAS) {
            val elegida = kotlin.math.abs(e.inclinacionX - a.first) < 0.5f && kotlin.math.abs(e.inclinacionY - a.second) < 0.5f
            Casilla(n, elegida, { poner(false) { it.copy(inclinacionX = a.first, inclinacionY = a.second) } }, ancho = 66) {
                Canvas(Modifier.size(40.dp)) { tarjeta(a.first, a.second, if (elegida) Ink.White else Ink.White70, relleno = elegida) }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    val m = Perspectiva.MAXIMA
    Deslizador("Tumbar (adelante / atras)", e.inclinacionX, -m, m, { v -> poner(true) { it.copy(inclinacionX = v) } }, neutro = 0f, texto = { "${it.roundToInt()}°" })
    Deslizador("Girar a los lados", e.inclinacionY, -m, m, { v -> poner(true) { it.copy(inclinacionY = v) } }, neutro = 0f, texto = { "${it.roundToInt()}°" })
    if (c.claves.isNotEmpty()) Nota("Tiene fotogramas clave: la perspectiva se guarda en la clave de aqui y se anima.", color = Ink.White50)
    if (e.conPerspectiva) {
        Spacer(Modifier.height(6.dp))
        BotonBorde("Quitar perspectiva", { poner(false) { it.copy(inclinacionX = 0f, inclinacionY = 0f) } }, glifo = Glifos.reiniciar)
    }
}
