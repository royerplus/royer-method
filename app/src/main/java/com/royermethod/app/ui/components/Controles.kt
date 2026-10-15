package com.royermethod.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.royermethod.app.ui.theme.Estilo
import com.royermethod.app.ui.theme.Ink
import com.royermethod.app.ui.theme.radio
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.exp

/** Esquinas redondeadas segun la redondez elegida en Personalizar. */
fun esquinas(r: Dp): Shape = RoundedCornerShape(radio(r))

/**
 * Para que un deslizador junte todo un arrastre en un solo paso de deshacer.
 * Lo pone la pantalla del editor; fuera de ella no hace nada.
 */
interface Gestos {
    fun empezar()
    fun terminar()
}

val LocalGestos = compositionLocalOf<Gestos> {
    object : Gestos {
        override fun empezar() {}
        override fun terminar() {}
    }
}

@Composable
fun Icono(glifo: Glifo, modifier: Modifier = Modifier, color: Color = Ink.White, tam: Dp = 22.dp) {
    Canvas(modifier.size(tam)) { glifo(color) }
}

/** Un icono que se toca. Apagado se ve a medias y no responde. */
@Composable
fun BotonIcono(
    glifo: Glifo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    activo: Boolean = true,
    color: Color = Ink.White90,
    tam: Dp = 40.dp
) {
    Box(
        modifier
            .size(tam)
            .clip(CircleShape)
            .clickable(enabled = activo, onClick = onClick)
            .alpha(if (activo) 1f else 0.28f),
        contentAlignment = Alignment.Center
    ) {
        Icono(glifo, color = color, tam = tam * 0.55f)
    }
}

/** Una herramienta de la barra de abajo: icono y nombre debajo. */
@Composable
fun Herramienta(glifo: Glifo, texto: String, onClick: () -> Unit, activa: Boolean = true, resaltada: Boolean = false) {
    Column(
        Modifier
            // Un nombre largo ("Compresion") ensancha la suya en vez de partirse
            .widthIn(min = 64.dp)
            .clip(esquinas(12.dp))
            .clickable(enabled = activa, onClick = onClick)
            .alpha(if (activa) 1f else 0.3f)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icono(glifo, color = if (resaltada) Ink.White else Ink.White90, tam = 22.dp)
        Spacer(Modifier.height(6.dp))
        Text(
            texto,
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.4.sp, fontSize = 10.5.sp * Estilo.letra),
            color = if (resaltada) Ink.White else Ink.White70,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

/** Una frase de ayuda: chica y tenue, pero sin espaciar para que se lea. */
@Composable
fun Nota(texto: String, modifier: Modifier = Modifier, color: Color = Ink.White50) {
    Text(texto, modifier = modifier, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp * Estilo.letra, letterSpacing = 0.2.sp), color = color)
}

/** Texto chico y espaciado que encabeza un grupo de controles. */
@Composable
fun Etiqueta(texto: String, modifier: Modifier = Modifier) {
    Text(
        texto.uppercase(),
        modifier = modifier.padding(top = 8.dp, bottom = 2.dp),
        style = MaterialTheme.typography.labelSmall,
        color = Ink.White50,
        maxLines = 1
    )
}

/** Como se mueve el valor a lo largo de la barra. */
enum class Escala { LINEAL, LOGARITMICA }

/**
 * Deslizador propio: barra fina con el tramo desde el valor neutro
 * iluminado, y el valor escrito al lado. Tocar el valor lo devuelve al
 * neutro. Un arrastre entero es un solo paso de deshacer.
 */
@Composable
fun Deslizador(
    etiqueta: String,
    valor: Float,
    desde: Float,
    hasta: Float,
    onCambio: (Float) -> Unit,
    modifier: Modifier = Modifier,
    neutro: Float = desde,
    escala: Escala = Escala.LINEAL,
    paso: Float = 0f,
    texto: (Float) -> String = { "%.0f".format(it) }
) {
    val gestos = LocalGestos.current
    val cambio by rememberUpdatedState(onCambio)
    fun aPos(v: Float): Float = when (escala) {
        Escala.LINEAL -> (v - desde) / (hasta - desde)
        Escala.LOGARITMICA -> (ln(v / desde) / ln(hasta / desde))
    }.coerceIn(0f, 1f)
    fun dePos(p: Float): Float {
        var v = when (escala) {
            Escala.LINEAL -> desde + (hasta - desde) * p
            Escala.LOGARITMICA -> desde * exp(ln(hasta / desde) * p)
        }
        if (paso > 0f) v = Math.round(v / paso) * paso
        // El neutro "imanta": es el valor al que mas se vuelve
        if (abs(aPos(v) - aPos(neutro)) < 0.015f) v = neutro
        return v.coerceIn(desde, hasta)
    }

    Column(modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(etiqueta, style = MaterialTheme.typography.bodyMedium, color = Ink.White70, modifier = Modifier.weight(1f))
            Text(
                texto(valor),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = if (valor == neutro) Ink.White50 else Ink.White,
                modifier = Modifier
                    .clip(esquinas(6.dp))
                    .clickable {
                        gestos.empezar(); cambio(neutro); gestos.terminar()
                    }
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            )
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(30.dp)
                .pointerInput(desde, hasta, escala) {
                    awaitEachGesture {
                        val abajo = awaitFirstDown()
                        gestos.empezar()
                        val margen = 10.dp.toPx()
                        fun valorEn(x: Float) = dePos(((x - margen) / (size.width - 2 * margen)).coerceIn(0f, 1f))
                        try {
                            cambio(valorEn(abajo.position.x))
                            abajo.consume()
                            while (true) {
                                val ev = awaitPointerEvent()
                                val c = ev.changes.firstOrNull { it.id == abajo.id } ?: break
                                if (!c.pressed) break
                                if (c.positionChange() != Offset.Zero) {
                                    cambio(valorEn(c.position.x))
                                    c.consume()
                                }
                            }
                        } finally {
                            gestos.terminar()
                        }
                    }
                }
        ) {
            val margen = 10.dp.toPx()
            val w = size.width - 2 * margen
            val y = size.height / 2
            val alto = 3.dp.toPx()
            drawRoundRect(Ink.White15, Offset(margen, y - alto / 2), Size(w, alto), CornerRadius(alto))
            val p0 = aPos(neutro)
            val p1 = aPos(valor)
            val a = margen + w * minOf(p0, p1)
            val b = margen + w * maxOf(p0, p1)
            drawRoundRect(Ink.White, Offset(a, y - alto / 2), Size(b - a, alto), CornerRadius(alto))
            if (p0 > 0.001f && p0 < 0.999f) drawCircle(Ink.White50, 2.dp.toPx(), Offset(margen + w * p0, y))
            val x = margen + w * p1
            drawCircle(Ink.Glow18, 13.dp.toPx(), Offset(x, y))
            drawCircle(Ink.White, 8.dp.toPx(), Offset(x, y))
            drawCircle(Ink.Black, 3.dp.toPx(), Offset(x, y))
        }
    }
}

/** Una pastilla elegible. */
@Composable
fun Chip(texto: String, elegido: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val fondo by animateColorAsState(if (elegido) Ink.White else Ink.White08, tween(180), label = "chip")
    Box(
        modifier
            .heightIn(min = 40.dp)
            .clip(esquinas(20.dp))
            .background(fondo)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            texto,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (elegido) FontWeight.Medium else FontWeight.Normal),
            color = if (elegido) Ink.Black else Ink.White70,
            maxLines = 1
        )
    }
}

/** Fila de pastillas que se desliza de lado si no caben. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun <T> Opciones(opciones: List<T>, elegida: T?, nombre: (T) -> String, onElegir: (T) -> Unit, modifier: Modifier = Modifier) {
    // Las que no caben bajan a otra fila: nada queda escondido a la derecha
    FlowRow(
        modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        opciones.forEach { o -> Chip(nombre(o), o == elegida, { onElegir(o) }) }
    }
}

/** Interruptor con su texto, en una fila entera. */
@Composable
fun Interruptor(texto: String, activo: Boolean, onCambio: (Boolean) -> Unit, detalle: String? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(esquinas(10.dp))
            .clickable { onCambio(!activo) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(texto, style = MaterialTheme.typography.bodyMedium, color = Ink.White90)
            if (detalle != null) Nota(detalle)
        }
        Spacer(Modifier.width(12.dp))
        val pista by animateColorAsState(if (activo) Ink.White else Ink.White15, tween(200), label = "pista")
        Box(
            Modifier
                .size(44.dp, 26.dp)
                .clip(CircleShape)
                .background(pista)
                .padding(3.dp),
            contentAlignment = if (activo) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Box(Modifier.size(20.dp).clip(CircleShape).background(if (activo) Ink.Black else Ink.White70))
        }
    }
}

/** Muestras de color. La elegida lleva un aro. Las que no caben bajan a otra fila. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Muestras(colores: List<Int>, elegido: Int?, onElegir: (Int) -> Unit, modifier: Modifier = Modifier, conNinguno: Boolean = false) {
    FlowRow(
        modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (conNinguno) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .border(if (elegido == 0) 2.dp else 1.dp, if (elegido == 0) Ink.White else Ink.White30, CircleShape)
                    .clickable { onElegir(0) },
                contentAlignment = Alignment.Center
            ) { Icono(Glifos.cerrar, color = Ink.White50, tam = 16.dp) }
        }
        colores.forEach { c ->
            val sel = c == elegido
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .border(2.dp, if (sel) Ink.White else Color.Transparent, CircleShape)
                    .padding(if (sel) 4.dp else 0.dp)
                    .clip(CircleShape)
                    .background(Color(c))
                    // Un aro tenue para que el negro se vea sobre el fondo negro
                    .border(1.dp, Ink.White15, CircleShape)
                    .clickable { onElegir(c) }
            )
        }
    }
}

/** Pestanas con una raya debajo de la elegida. */
@Composable
fun Pestanas(nombres: List<String>, elegida: Int, onElegir: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        nombres.forEachIndexed { i, n ->
            val sel = i == elegida
            Column(
                Modifier
                    .clip(esquinas(8.dp))
                    .clickable { onElegir(i) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    n,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (sel) FontWeight.Medium else FontWeight.Normal),
                    color = if (sel) Ink.White else Ink.White50
                )
                Spacer(Modifier.height(5.dp))
                Box(Modifier.size(if (sel) 18.dp else 0.dp, 2.dp).clip(CircleShape).background(Ink.White))
            }
        }
    }
}

/**
 * Casilla de una cuadricula de opciones (animaciones, transiciones,
 * filtros). Arriba un dibujo que explica la opcion, abajo su nombre.
 */
@Composable
fun Mosaico(
    texto: String,
    elegido: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dibujo: (androidx.compose.ui.graphics.drawscope.DrawScope.(Color) -> Unit)? = null
) {
    val borde by animateColorAsState(if (elegido) Ink.White else Color.Transparent, tween(180), label = "mosaico")
    Column(
        modifier
            // Con peso dentro de una Cuadricula, el peso manda y se estira
            .width(72.dp)
            // Sin recortar: el nombre llega justo al borde y se comeria letras
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(62.dp)
                .clip(esquinas(12.dp))
                .background(if (elegido) Ink.White15 else Ink.White08)
                .border(1.5.dp, borde, esquinas(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (dibujo != null) Canvas(Modifier.size(38.dp)) { dibujo(if (elegido) Ink.White else Ink.White70) }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            texto,
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.2.sp, fontSize = 10.5.sp * Estilo.letra),
            color = if (elegido) Ink.White else Ink.White70,
            // Dos lineas: "Desde la izquierda" y "Desde la derecha" no pueden
            // quedar las dos en "Desde la…"
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Casillas en filas que bajan, en vez de una tira que hay que arrastrar de
 * lado: se ven todas de un vistazo y el panel ya se desliza hacia abajo.
 * Cada columna se estira lo justo para llenar el ancho.
 */
@Composable
fun <T> Cuadricula(
    elementos: List<T>,
    modifier: Modifier = Modifier,
    celda: @Composable (T, Modifier) -> Unit
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val hueco = 8.dp
        val columnas = ((maxWidth + hueco) / (72.dp + hueco)).toInt().coerceAtLeast(3)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            elementos.chunked(columnas).forEach { fila ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(hueco)) {
                    fila.forEach { celda(it, Modifier.weight(1f)) }
                    repeat(columnas - fila.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** Boton de texto con relleno de neon. */
@Composable
fun BotonLleno(texto: String, onClick: () -> Unit, modifier: Modifier = Modifier, activo: Boolean = true) {
    Box(
        modifier
            .clip(esquinas(22.dp))
            .background(if (activo) Ink.White else Ink.White15)
            .clickable(enabled = activo, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            texto,
            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.5.sp),
            color = if (activo) Ink.Black else Ink.White50,
            maxLines = 1
        )
    }
}

/** Boton de texto solo con borde. */
@Composable
fun BotonBorde(texto: String, onClick: () -> Unit, modifier: Modifier = Modifier, glifo: Glifo? = null) {
    Row(
        modifier
            .clip(esquinas(22.dp))
            .border(1.dp, Ink.White30, esquinas(22.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (glifo != null) {
            Icono(glifo, color = Ink.White90, tam = 16.dp)
            Spacer(Modifier.width(6.dp))
        }
        Text(texto, style = MaterialTheme.typography.bodyMedium, color = Ink.White90, maxLines = 1)
    }
}

/** "1.5 s", "0.3 s": los tiempos cortos se dicen en segundos con decimal. */
fun segundos(us: Long): String {
    val s = us / 1_000_000.0
    return if (s < 10) "%.1f s".format(s) else "%.0f s".format(s)
}

/** "00:07.4" para el reloj del editor. */
fun reloj(us: Long): String {
    val decimas = us / 100_000
    val s = decimas / 10
    return "%02d:%02d.%d".format(s / 60, s % 60, decimas % 10)
}
