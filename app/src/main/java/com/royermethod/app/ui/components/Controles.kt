package com.royermethod.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
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

/**
 * Dentro del editor los controles van apretados: cada deslizador en una sola
 * fila (nombre, barra, valor) para que quepan varios en la hoja de abajo sin
 * tapar la imagen. Fuera (Personalizar) hay sitio y la barra va a todo el ancho.
 */
val LocalDenso = staticCompositionLocalOf { false }

/**
 * Letra de los paneles: la de la app sin el espaciado de letrero. En una hoja
 * densa las mayusculas espaciadas ocupan el doble y se leen peor.
 */
object Letra {
    val titulo: TextStyle
        @Composable get() = MaterialTheme.typography.bodyLarge.copy(
            fontSize = 14.5.sp * Estilo.letra, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp, lineHeight = 20.sp * Estilo.letra)
    val cuerpo: TextStyle
        @Composable get() = MaterialTheme.typography.bodyMedium.copy(letterSpacing = 0.1.sp, lineHeight = 17.sp * Estilo.letra)
    val chica: TextStyle
        @Composable get() = MaterialTheme.typography.bodyMedium.copy(
            fontSize = 11.5.sp * Estilo.letra, letterSpacing = 0.1.sp, lineHeight = 15.sp * Estilo.letra)
    val mini: TextStyle
        @Composable get() = MaterialTheme.typography.labelSmall.copy(
            fontSize = 10.5.sp * Estilo.letra, letterSpacing = 0.2.sp, lineHeight = 13.sp * Estilo.letra)
}

@Composable
fun Icono(glifo: Glifo, modifier: Modifier = Modifier, color: Color = Ink.White, tam: Dp = 22.dp) {
    Canvas(modifier.size(tam)) { glifo(color) }
}

/**
 * Un icono que se toca. Apagado se ve a medias y no responde. [encendido] es
 * para los que se prenden y se apagan (Zoom, Mover todo): un disco claro
 * detras, que se vea de un vistazo que siguen puestos.
 */
@Composable
fun BotonIcono(
    glifo: Glifo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    activo: Boolean = true,
    color: Color = Ink.White90,
    tam: Dp = 40.dp,
    descripcion: String? = null,
    encendido: Boolean = false
) {
    Box(
        modifier
            .size(tam)
            .then(if (descripcion != null) Modifier.semantics { contentDescription = descripcion } else Modifier)
            .clip(CircleShape)
            .clickable(enabled = activo, onClick = onClick)
            .alpha(if (activo) 1f else 0.28f),
        contentAlignment = Alignment.Center
    ) {
        if (encendido) Box(Modifier.size(tam * 0.84f).clip(CircleShape).background(Ink.White15))
        Icono(glifo, color = if (encendido) Ink.White else color, tam = tam * 0.55f)
    }
}

/**
 * Una herramienta de la barra de abajo: icono y nombre debajo. [resaltada]
 * pone un punto junto al icono: "aqui ya hay algo puesto". [encendida] es
 * para las que se prenden y se apagan (Mover todo): fondo claro, que se vea
 * de lejos.
 */
@Composable
fun Herramienta(glifo: Glifo, texto: String, onClick: () -> Unit, activa: Boolean = true, resaltada: Boolean = false, encendida: Boolean = false) {
    Column(
        Modifier
            // Un nombre largo ("Compresion") ensancha la suya en vez de partirse
            .widthIn(min = 58.dp)
            .clip(esquinas(12.dp))
            .background(if (encendida) Ink.White15 else Color.Transparent)
            .clickable(enabled = activa, onClick = onClick)
            .alpha(if (activa) 1f else 0.3f)
            .padding(horizontal = 5.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            Icono(glifo, color = if (resaltada || encendida) Ink.White else Ink.White90, tam = 22.dp)
            if (resaltada) Box(Modifier.align(Alignment.TopEnd).offset(x = 5.dp, y = (-2).dp).size(5.dp).clip(CircleShape).background(Ink.White))
        }
        Spacer(Modifier.height(5.dp))
        Text(
            texto,
            style = Letra.mini,
            color = if (resaltada || encendida) Ink.White else Ink.White70,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

/** Una frase de ayuda: chica y tenue, pero sin espaciar para que se lea. */
@Composable
fun Nota(texto: String, modifier: Modifier = Modifier, color: Color = Ink.White50) {
    Text(texto, modifier = modifier, style = Letra.chica, color = color)
}

/** Texto chico que encabeza un grupo de controles. */
@Composable
fun Etiqueta(texto: String, modifier: Modifier = Modifier) {
    Text(
        texto,
        modifier = modifier.padding(top = 6.dp, bottom = 2.dp),
        style = Letra.chica.copy(fontWeight = FontWeight.Medium),
        color = Ink.White50,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/**
 * Una fila "nombre y control", con el nombre en la misma columna que el de los
 * deslizadores: varias seguidas quedan alineadas como una tabla.
 */
@Composable
fun Fila(etiqueta: String, modifier: Modifier = Modifier, contenido: @Composable RowScope.() -> Unit) {
    Row(modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(etiqueta, style = Letra.cuerpo, color = Ink.White70, modifier = Modifier.width(ANCHO_NOMBRE), maxLines = 2, overflow = TextOverflow.Ellipsis)
        contenido()
    }
}

/** Lo que ocupa el nombre de un control en su fila. */
private val ANCHO_NOMBRE = 84.dp

/** Como se mueve el valor a lo largo de la barra. */
enum class Escala { LINEAL, LOGARITMICA }

/**
 * Deslizador propio: barra fina con el tramo desde el valor neutro
 * iluminado y el valor con unidades legibles. Tocar el valor (cuando no es
 * el neutro) lo restablece. Un arrastre horizontal es un solo paso de
 * deshacer; el vertical deja desplazar la página sin tocar el valor.
 *
 * [enFila] lo pone todo en una fila (nombre, barra, valor), que es como va
 * en los paneles del editor; si no, el nombre y el valor van encima y la
 * barra ocupa todo el ancho. [iman] = false quita el salto al neutro cerca
 * de el (para tiempos finos); [cabecera] = false dibuja solo la barra, para
 * quien pone su propia fila de titulo y valor encima (CampoTiempo).
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
    texto: (Float) -> String = { valorDeControl(it, desde, hasta) },
    iman: Boolean = true,
    cabecera: Boolean = true,
    enFila: Boolean = LocalDenso.current
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
        if (iman && abs(aPos(v) - aPos(neutro)) < 0.015f) v = neutro
        return v.coerceIn(desde, hasta)
    }
    val restablecer = { gestos.empezar(); cambio(neutro); gestos.terminar() }

    val barra: @Composable (Modifier) -> Unit = { m ->
        Canvas(
            m
                .height(36.dp)
                .semantics {
                    contentDescription = etiqueta
                    progressBarRangeInfo = ProgressBarRangeInfo(valor.coerceIn(desde,hasta),desde..hasta)
                    setProgress { v ->
                        gestos.empezar(); cambio(v.coerceIn(desde,hasta)); gestos.terminar();true
                    }
                }
                .pointerInput(desde,hasta,escala,paso,iman,neutro) {
                    detectTapGestures { p ->
                        val margen=10.dp.toPx()
                        gestos.empezar()
                        cambio(dePos(((p.x-margen)/(size.width-2*margen)).coerceIn(0f,1f)))
                        gestos.terminar()
                    }
                }
                .pointerInput(desde,hasta,escala,paso,iman,neutro) {
                    val margen=10.dp.toPx()
                    fun poner(x:Float) = cambio(dePos(((x-margen)/(size.width-2*margen)).coerceIn(0f,1f)))
                    detectHorizontalDragGestures(
                        onDragStart={ gestos.empezar();poner(it.x) },
                        onDragEnd={ gestos.terminar() },onDragCancel={ gestos.terminar() }
                    ) { ch,_ -> poner(ch.position.x) }
                }
        ) {
            val margen = 10.dp.toPx()
            val w = size.width - 2 * margen
            val y = size.height / 2
            val alto = 2.5.dp.toPx()
            drawRoundRect(Ink.White15, Offset(margen, y - alto / 2), Size(w, alto), CornerRadius(alto))
            val p0 = aPos(neutro)
            val p1 = aPos(valor)
            val a = margen + w * minOf(p0, p1)
            val b = margen + w * maxOf(p0, p1)
            drawRoundRect(Ink.White, Offset(a, y - alto / 2), Size(b - a, alto), CornerRadius(alto))
            if (p0 > 0.001f && p0 < 0.999f) drawCircle(Ink.White50, 2.dp.toPx(), Offset(margen + w * p0, y))
            val x = margen + w * p1
            drawCircle(Ink.Glow18, 11.dp.toPx(), Offset(x, y))
            drawCircle(Ink.White, 7.dp.toPx(), Offset(x, y))
            drawCircle(Ink.Black, 2.5.dp.toPx(), Offset(x, y))
        }
    }

    when {
        !cabecera -> barra(modifier.fillMaxWidth())
        enFila -> Row(modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(etiqueta, style = Letra.cuerpo, color = Ink.White70, modifier = Modifier.width(ANCHO_NOMBRE), maxLines = 2, overflow = TextOverflow.Ellipsis)
            barra(Modifier.weight(1f))
            ValorDeslizador(texto(valor), valor != neutro, etiqueta, restablecer, Modifier.widthIn(min = 56.dp))
        }
        else -> Column(modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 28.dp)) {
                Text(etiqueta, style = Letra.cuerpo, color = Ink.White70, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                ValorDeslizador(texto(valor), valor != neutro, etiqueta, restablecer)
            }
            barra(Modifier.fillMaxWidth())
        }
    }
}

/** El valor de un deslizador. Cambiado, se ilumina y tocarlo vuelve al neutro. */
@Composable
private fun ValorDeslizador(texto: String, cambiado: Boolean, etiqueta: String, restablecer: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .heightIn(min = 32.dp)
            .clip(esquinas(8.dp))
            .then(if (cambiado) Modifier.semantics { contentDescription = "Restablecer $etiqueta" }.clickable(onClick = restablecer) else Modifier)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        if (cambiado) {
            Icono(Glifos.reiniciar, color = Ink.White50, tam = 11.dp)
            Spacer(Modifier.width(3.dp))
        }
        Text(texto, style = Letra.cuerpo.copy(fontWeight = FontWeight.Medium), color = if (cambiado) Ink.White else Ink.White50, maxLines = 1, softWrap = false)
    }
}

/** Una pastilla elegible. [marca] pone un punto al lado del nombre: "esta tiene algo puesto". */
@Composable
fun Chip(texto: String, elegido: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, marca: Boolean = false) {
    val fondo by animateColorAsState(if (elegido) Ink.White else Ink.White08, tween(180), label = "chip")
    Row(
        modifier
            .heightIn(min = 34.dp)
            .clip(esquinas(17.dp))
            .background(fondo)
            .semantics { selected = elegido }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            texto,
            style = Letra.cuerpo.copy(fontWeight = if (elegido) FontWeight.Medium else FontWeight.Normal),
            color = if (elegido) Ink.Black else Ink.White70,
            maxLines = 1
        )
        if (marca) {
            Spacer(Modifier.width(5.dp))
            Box(Modifier.size(5.dp).clip(CircleShape).background(if (elegido) Ink.Black else Ink.White))
        }
    }
}

/** Pastillas para elegir una opcion. Las que no caben bajan a otra fila: nada queda escondido. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun <T> Opciones(opciones: List<T>, elegida: T?, nombre: (T) -> String, onElegir: (T) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(
        modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        opciones.forEach { o -> Chip(nombre(o), o == elegida, { onElegir(o) }) }
    }
}

/**
 * Pocas opciones (dos a cinco) en un solo control de ancho completo, todas del
 * mismo tamaño: se lee de un vistazo y no deja pastillas sueltas de anchos
 * distintos.
 */
@Composable
fun <T> Segmentos(opciones: List<T>, elegida: T?, nombre: (T) -> String, onElegir: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 3.dp).clip(esquinas(11.dp)).background(Ink.White08).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        opciones.forEach { o ->
            val sel = o == elegida
            val fondo by animateColorAsState(if (sel) Ink.White else Color.Transparent, tween(160), label = "segmento")
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 32.dp)
                    .clip(esquinas(8.dp))
                    .background(fondo)
                    .semantics { selected = sel; role = Role.Tab }
                    .clickable { onElegir(o) }
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    nombre(o),
                    style = Letra.cuerpo.copy(fontWeight = if (sel) FontWeight.Medium else FontWeight.Normal),
                    color = if (sel) Ink.Black else Ink.White70,
                    maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Interruptor con su texto, en una fila entera. */
@Composable
fun Interruptor(texto: String, activo: Boolean, onCambio: (Boolean) -> Unit, detalle: String? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(esquinas(10.dp))
            .semantics { stateDescription = if(activo) "Activado" else "Desactivado"; role = Role.Switch }
            .clickable { onCambio(!activo) }
            .heightIn(min = 40.dp)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(texto, style = Letra.cuerpo, color = Ink.White90)
            if (detalle != null) Nota(detalle)
        }
        Spacer(Modifier.width(10.dp))
        Palanca(activo)
    }
}

/** La palanca de un interruptor, sola. */
@Composable
fun Palanca(activo: Boolean, modifier: Modifier = Modifier) {
    val pista by animateColorAsState(if (activo) Ink.White else Ink.White15, tween(200), label = "pista")
    Box(
        modifier
            .size(38.dp, 22.dp)
            .clip(CircleShape)
            .background(pista)
            .padding(3.dp),
        contentAlignment = if (activo) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(Modifier.size(16.dp).clip(CircleShape).background(if (activo) Ink.Black else Ink.White70))
    }
}

/**
 * Un interruptor en pastilla, para poner varios en una fila ("Tono",
 * "Fluido"): un punto relleno cuando esta encendido.
 */
@Composable
fun ChipInterruptor(texto: String, activo: Boolean, onCambio: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val borde by animateColorAsState(if (activo) Ink.White else Ink.White15, tween(180), label = "chipInterruptor")
    Row(
        modifier
            .heightIn(min = 34.dp)
            .clip(esquinas(17.dp))
            .background(if (activo) Ink.White15 else Color.Transparent)
            .border(1.dp, borde, esquinas(17.dp))
            .semantics { stateDescription = if (activo) "Activado" else "Desactivado"; role = Role.Switch }
            .clickable { onCambio(!activo) }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(if (activo) Ink.White else Color.Transparent).border(1.2.dp, if (activo) Ink.White else Ink.White50, CircleShape))
        Spacer(Modifier.width(7.dp))
        Text(texto, style = Letra.cuerpo, color = if (activo) Ink.White else Ink.White70, maxLines = 1)
    }
}

/** Muestras de color en una tira que se desliza. La elegida lleva un aro. */
@Composable
fun Muestras(colores: List<Int>, elegido: Int?, onElegir: (Int) -> Unit, modifier: Modifier = Modifier, conNinguno: Boolean = false) {
    Tira(modifier.padding(vertical = 3.dp), hueco = 8.dp) {
        if (conNinguno) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .border(if (elegido == 0) 2.dp else 1.dp, if (elegido == 0) Ink.White else Ink.White30, CircleShape)
                    .clickable { onElegir(0) },
                contentAlignment = Alignment.Center
            ) { Icono(Glifos.cerrar, color = Ink.White50, tam = 14.dp) }
        }
        colores.forEach { c ->
            val sel = c == elegido
            Box(
                Modifier
                    .size(34.dp)
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

/**
 * Pestanas de texto: la elegida se ilumina y lleva una raya debajo. Las de
 * [marcas] llevan un punto: "aqui dentro ya hay algo puesto".
 */
@Composable
fun Pestanas(nombres: List<String>, elegida: Int, onElegir: (Int) -> Unit, modifier: Modifier = Modifier, marcas: Set<Int> = emptySet()) {
    Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        nombres.forEachIndexed { i, n ->
            val sel = i == elegida
            Box(
                Modifier
                    .heightIn(min = 38.dp)
                    .clip(esquinas(8.dp))
                    .semantics { selected = sel; role = Role.Tab }
                    .clickable { onElegir(i) }
                    // El hueco entre pestanas es de la de la izquierda: tambien se toca
                    .padding(end = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            n,
                            style = Letra.cuerpo.copy(fontSize = 13.5.sp * Estilo.letra, fontWeight = if (sel) FontWeight.Medium else FontWeight.Normal),
                            color = if (sel) Ink.White else Ink.White50,
                            maxLines = 1, softWrap = false
                        )
                        if (i in marcas) {
                            Spacer(Modifier.width(4.dp))
                            Box(Modifier.size(4.dp).clip(CircleShape).background(if (sel) Ink.White else Ink.White50))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.size(if (sel) 16.dp else 0.dp, 2.dp).clip(CircleShape).background(Ink.White))
                }
            }
        }
    }
}

/**
 * Casilla de una tira o cuadricula de opciones (animaciones, transiciones,
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
            .width(60.dp)
            // Sin recortar: el nombre llega justo al borde y se comeria letras
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(esquinas(12.dp))
                .background(if (elegido) Ink.White15 else Ink.White08)
                .border(1.5.dp, borde, esquinas(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (dibujo != null) Canvas(Modifier.size(34.dp)) { dibujo(if (elegido) Ink.White else Ink.White70) }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            texto,
            style = Letra.mini,
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
 * Casillas en filas que bajan: se ven todas de un vistazo y el panel ya se
 * desliza hacia abajo. Cada columna se estira lo justo para llenar el ancho.
 */
@Composable
fun <T> Cuadricula(
    elementos: List<T>,
    modifier: Modifier = Modifier,
    celda: @Composable (T, Modifier) -> Unit
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val hueco = 6.dp
        val columnas = ((maxWidth + hueco) / (60.dp + hueco)).toInt().coerceAtLeast(3)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            elementos.chunked(columnas).forEach { fila ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(hueco)) {
                    fila.forEach { celda(it, Modifier.weight(1f)) }
                    repeat(columnas - fila.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/**
 * Tira que se desliza de lado, como las de CapCut. Un difuminado en el borde
 * derecho avisa de que sigue: sin el, lo que no cabe parece que no existe.
 */
@Composable
fun Tira(modifier: Modifier = Modifier, hueco: Dp = 6.dp, contenido: @Composable RowScope.() -> Unit) {
    val desliza = rememberScrollState()
    val hayMas by remember(desliza) { derivedStateOf { desliza.value < desliza.maxValue } }
    val hayAntes by remember(desliza) { derivedStateOf { desliza.value > 0 } }
    val fondo = Ink.Black
    Row(
        modifier
            .fillMaxWidth()
            .drawWithContent {
                drawContent()
                val ancho = 22.dp.toPx()
                if (hayMas) drawRect(
                    Brush.horizontalGradient(listOf(Color.Transparent, fondo), startX = size.width - ancho, endX = size.width),
                    topLeft = Offset(size.width - ancho, 0f), size = Size(ancho, size.height)
                )
                if (hayAntes) drawRect(
                    Brush.horizontalGradient(listOf(fondo, Color.Transparent), startX = 0f, endX = ancho),
                    topLeft = Offset.Zero, size = Size(ancho, size.height)
                )
            }
            .horizontalScroll(desliza),
        horizontalArrangement = Arrangement.spacedBy(hueco),
        content = contenido
    )
}

/** Boton de texto con relleno de neon. */
@Composable
fun BotonLleno(texto: String, onClick: () -> Unit, modifier: Modifier = Modifier, activo: Boolean = true) {
    Box(
        modifier
            .heightIn(min = 38.dp)
            .clip(esquinas(19.dp))
            .background(if (activo) Ink.White else Ink.White15)
            .clickable(enabled = activo, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            texto,
            style = Letra.cuerpo.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp),
            color = if (activo) Ink.Black else Ink.White50,
            maxLines = 1
        )
    }
}

/** Boton de texto solo con borde. */
@Composable
fun BotonBorde(texto: String, onClick: () -> Unit, modifier: Modifier = Modifier, glifo: Glifo? = null, peligro: Boolean = false, activo: Boolean = true) {
    val color = if(peligro)Ink.Peligro else Ink.White90
    Row(
        modifier
            .clip(esquinas(18.dp))
            .heightIn(min = 36.dp)
            .border(1.dp, if(peligro)Ink.Peligro.copy(alpha=.5f) else Ink.White30, esquinas(18.dp))
            .alpha(if(activo)1f else .35f)
            .clickable(enabled = activo, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (glifo != null) {
            Icono(glifo, color = color, tam = 15.dp)
            Spacer(Modifier.width(6.dp))
        }
        Text(texto, style = Letra.cuerpo, color = color, maxLines = 1, softWrap = false)
    }
}

/** Accion discreta, sin borde: icono y nombre. Para la barra de titulo de una hoja. */
@Composable
fun BotonTexto(texto: String, onClick: () -> Unit, modifier: Modifier = Modifier, glifo: Glifo? = null, activo: Boolean = true, encendido: Boolean = false) {
    Row(
        modifier
            .heightIn(min = 36.dp)
            .clip(esquinas(18.dp))
            .background(if (encendido) Ink.White15 else Color.Transparent)
            .alpha(if (activo) 1f else .35f)
            .clickable(enabled = activo, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (glifo != null) {
            Icono(glifo, color = if (encendido) Ink.White else Ink.White70, tam = 15.dp)
            Spacer(Modifier.width(5.dp))
        }
        Text(texto, style = Letra.cuerpo, color = if (encendido) Ink.White else Ink.White70, maxLines = 1, softWrap = false)
    }
}

/**
 * Una accion de las que se hacen y ya (Espejo, Girar, Abrir, Guardar): casilla
 * ancha con el icono encima y el nombre debajo. Varias en una fila, cada una
 * con su peso, llenan el ancho y quedan todas iguales.
 */
@Composable
fun BotonAccion(glifo: Glifo, texto: String, onClick: () -> Unit, modifier: Modifier = Modifier, activo: Boolean = true, peligro: Boolean = false) {
    val color = if (peligro) Ink.Peligro else Ink.White90
    Column(
        modifier
            .heightIn(min = 54.dp)
            .clip(esquinas(12.dp))
            .background(Ink.White08)
            .alpha(if (activo) 1f else .35f)
            .clickable(enabled = activo, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icono(glifo, color = color, tam = 20.dp)
        Spacer(Modifier.height(5.dp))
        Text(texto, style = Letra.mini, color = if (peligro) Ink.Peligro else Ink.White70, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

/** "1.5 s", "0.3 s": los tiempos cortos se dicen en segundos con decimal. */
fun segundos(us: Long): String {
    val s = us / 1_000_000.0
    return if (s < 10) "%.1f s".format(s) else "%.0f s".format(s)
}

/**
 * "00:07.4" para el reloj del editor; con [milesimas], "00:07.433" (en pausa,
 * para ver si el cabezal esta en 0,005 s o en 0,001 s).
 */
fun reloj(us: Long, milesimas: Boolean = false): String {
    if (milesimas) {
        val ms = us.coerceAtLeast(0) / 1_000
        val s = ms / 1_000
        return "%02d:%02d.%03d".format(s / 60, s % 60, ms % 1_000)
    }
    val decimas = us / 100_000
    val s = decimas / 10
    return "%02d:%02d.%d".format(s / 60, s % 60, decimas % 10)
}

/** Unidades legibles también para magnitudes pequeñas: .006 equivale a 0,6 %. */
internal fun valorDeControl(v:Float,desde:Float,hasta:Float):String {
    val porcentaje = desde >= -1f && hasta <= 1f
    val n=if(porcentaje)v*100f else v
    val decimales=if(porcentaje)1 else if(hasta-desde<=10f)2 else 0
    val f=java.text.NumberFormat.getNumberInstance().apply { minimumFractionDigits=0;maximumFractionDigits=decimales;isGroupingUsed=false }
    return f.format(n.toDouble()) + if(porcentaje) "%" else ""
}

/** Agrupa ajustes secundarios; su cabecera conserva un objetivo táctil y un estado claro. */
@Composable
fun SeccionAjustes(titulo:String,inicialmenteAbierta:Boolean=false,contenido:@Composable androidx.compose.foundation.layout.ColumnScope.()->Unit) {
    var abierta by rememberSaveable(titulo) { mutableStateOf(inicialmenteAbierta) }
    Column(Modifier.fillMaxWidth().padding(vertical=4.dp).clip(esquinas(10.dp)).background(Ink.White04)) {
        Row(Modifier.fillMaxWidth().heightIn(min=40.dp)
            .semantics { stateDescription=if(abierta) "Expandido" else "Contraído" }
            .clickable { abierta=!abierta }.padding(horizontal=10.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(titulo,style=Letra.cuerpo,color=Ink.White90,modifier=Modifier.weight(1f))
            Icono(Glifos.siguiente,Modifier.rotate(if(abierta)90f else 0f),color=Ink.White50,tam=16.dp)
        }
        AnimatedVisibility(abierta) { Column(Modifier.fillMaxWidth().padding(horizontal=10.dp).padding(bottom=8.dp),content=contenido) }
    }
}
