package com.royermethod.app.editor.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.royermethod.app.editor.modelo.Animacion
import com.royermethod.app.editor.modelo.AnimacionContinua
import com.royermethod.app.editor.modelo.Clip
import com.royermethod.app.editor.modelo.Combinaciones
import com.royermethod.app.editor.modelo.Continuo
import com.royermethod.app.editor.modelo.Curva
import com.royermethod.app.editor.modelo.Movimiento
import com.royermethod.app.ui.theme.Ink
import kotlin.math.min

/**
 * Animaciones de un bloque.
 *
 * Arriba las combinaciones listas para usar (y las que haya guardado el
 * usuario). Debajo, las piezas de la que esta puesta: cada una se puede
 * abrir para cambiar cuanto se mueve, con que curva y en que parte del
 * tiempo actua, se puede quitar, y se pueden sumar piezas nuevas. Asi una
 * animacion se combina de varias y se modifica a mano, y lo que salga se
 * guarda como propia para reutilizarla.
 */
@Composable
fun PanelAnimacion(estado: EstadoEditor) {
    val c = estado.clipElegido ?: return
    var pestana by rememberSaveable { mutableIntStateOf(0) }
    Pestanas(listOf("Entrada", "Salida", "Siempre"), pestana, { pestana = it })
    Spacer(Modifier.height(6.dp))
    when (pestana) {
        0 -> EntradaOSalida(estado, c, entrada = true)
        1 -> EntradaOSalida(estado, c, entrada = false)
        else -> Continuas(estado, c)
    }
}

@Composable
private fun EntradaOSalida(estado: EstadoEditor, c: Clip, entrada: Boolean) {
    val piezas = if (entrada) c.entrada else c.salida
    val duracion = if (entrada) c.duracionEntradaUs else c.duracionSalidaUs
    fun poner(l: List<Movimiento>, arrastre: Boolean = false) = estado.cambiarClip(arrastre) {
        // Al poner la primera, una duracion que quepa en el bloque
        val dur = if ((if (entrada) it.entrada else it.salida).isEmpty() && l.isNotEmpty())
            min(AjustesEditor.duracionAnimacionUs, it.duracionUs / 2).coerceAtLeast(100_000)
        else if (entrada) it.duracionEntradaUs else it.duracionSalidaUs
        if (entrada) it.copy(entrada = l, duracionEntradaUs = dur) else it.copy(salida = l, duracionSalidaUs = dur)
    }

    val listas = Combinaciones.entrada + AjustesEditor.mias
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Mosaico("Ninguna", piezas.isEmpty(), { poner(emptyList()) }) { col -> ninguna(col) }
        listas.forEach { comb ->
            Mosaico(comb.nombre, comb.piezas == piezas, { poner(comb.piezas) }) { col -> dibujoPieza(comb.piezas.first().tipo, col, !entrada) }
        }
    }
    if (piezas.isEmpty()) return
    Spacer(Modifier.height(4.dp))
    Deslizador(
        "Duracion", duracion / 1e6f, 0.1f, maxDuracion(c),
        onCambio = { v ->
            estado.cambiarClip(true) {
                val us = (v * 1e6).toLong()
                if (entrada) it.copy(duracionEntradaUs = us) else it.copy(duracionSalidaUs = us)
            }
        },
        neutro = 0.1f, paso = 0.1f, texto = { "%.1f s".format(it) }
    )
    Piezas(
        piezas,
        onCambio = { l, arrastre -> poner(l, arrastre) },
        guardada = listas.any { it.piezas == piezas },
        onGuardar = {
            val n = AjustesEditor.mias.size + 1
            AjustesEditor.mias = AjustesEditor.mias + Combinaciones.Combinacion("Mia $n", piezas)
            AjustesEditor.alCambiar()
            estado.avisar("Guardada como \"Mia $n\"")
        }
    )
}

@Composable
private fun Piezas(
    piezas: List<Movimiento>,
    onCambio: (List<Movimiento>, Boolean) -> Unit,
    guardada: Boolean,
    onGuardar: () -> Unit
) {
    var abierta by remember { mutableIntStateOf(-1) }
    var agregando by remember { mutableStateOf(false) }
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Etiqueta("Piezas de esta animacion", Modifier.weight(1f))
        if (!guardada) Text(
            "Guardar como mia",
            style = MaterialTheme.typography.bodyMedium,
            color = Ink.White,
            modifier = Modifier.clickable(onClick = onGuardar).padding(6.dp)
        )
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
                            if (m.desde > 0f || m.hasta < 1f) "  ·  ${(m.desde * 100).toInt()}–${(m.hasta * 100).toInt()}%" else "",
                        style = MaterialTheme.typography.labelSmall, color = Ink.White50
                    )
                }
                BotonIcono(Glifos.cerrar, { onCambio(piezas.filterIndexed { j, _ -> j != i }, false); abierta = -1 }, tam = 36.dp, color = Ink.White50)
            }
            AnimatedVisibility(abierta == i) {
                Column(Modifier.padding(horizontal = 12.dp).padding(bottom = 8.dp)) {
                    Deslizador("Cuanto", m.intensidad, 0f, 3f, { v -> cambiar(m.copy(intensidad = v)) }, neutro = 1f, texto = { "${(it * 100).toInt()}%" })
                    Opciones(Curva.values().toList(), m.curva, { it.nombre }, { cu -> cambiar(m.copy(curva = cu), false) })
                    Deslizador("Empieza", m.desde, 0f, 0.9f, { v -> cambiar(m.copy(desde = v, hasta = maxOf(m.hasta, v + 0.1f))) }, texto = { "${(it * 100).toInt()}%" })
                    Deslizador("Termina", m.hasta, 0.1f, 1f, { v -> cambiar(m.copy(hasta = v, desde = minOf(m.desde, v - 0.1f))) }, neutro = 1f, texto = { "${(it * 100).toInt()}%" })
                }
            }
        }
    }
    Spacer(Modifier.height(4.dp))
    BotonBorde(if (agregando) "Elige la pieza" else "Sumar otra pieza", { agregando = !agregando }, glifo = Glifos.mas)
    AnimatedVisibility(agregando) {
        Opciones(Animacion.values().toList(), null, { it.nombre }, { a ->
            onCambio(piezas + Movimiento(a), false)
            agregando = false
            abierta = piezas.size
        }, Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun Continuas(estado: EstadoEditor, c: Clip) {
    val piezas = c.continua
    fun poner(l: List<Continuo>, arrastre: Boolean = false) = estado.cambiarClip(arrastre) { it.copy(continua = l) }
    val listas = Combinaciones.continuas + AjustesEditor.continuasMias
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Mosaico("Ninguna", piezas.isEmpty(), { poner(emptyList()) }) { col -> ninguna(col) }
        listas.forEach { (nombre, l) ->
            Mosaico(nombre, l == piezas, { poner(l) }) { col -> dibujoContinua(l.first().tipo, col) }
        }
    }
    if (piezas.isEmpty()) return
    var agregando by remember { mutableStateOf(false) }
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Etiqueta("Piezas", Modifier.weight(1f))
        if (listas.none { it.second == piezas }) Text(
            "Guardar como mia",
            style = MaterialTheme.typography.bodyMedium,
            color = Ink.White,
            modifier = Modifier.clickable {
                val n = AjustesEditor.continuasMias.size + 1
                AjustesEditor.continuasMias = AjustesEditor.continuasMias + ("Mia $n" to piezas)
                AjustesEditor.alCambiar()
                estado.avisar("Guardada como \"Mia $n\"")
            }.padding(6.dp)
        )
    }
    piezas.forEachIndexed { i, k ->
        fun cambiar(n: Continuo) = poner(piezas.toMutableList().also { it[i] = n }, true)
        Column(Modifier.fillMaxWidth().padding(vertical = 3.dp).background(Ink.White04, esquinas(12.dp)).padding(horizontal = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(k.tipo.nombre, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = Ink.White90, modifier = Modifier.weight(1f))
                BotonIcono(Glifos.cerrar, { poner(piezas.filterIndexed { j, _ -> j != i }) }, tam = 36.dp, color = Ink.White50)
            }
            Deslizador("Cuanto", k.intensidad, 0f, 3f, { v -> cambiar(k.copy(intensidad = v)) }, neutro = 1f, texto = { "${(it * 100).toInt()}%" })
            Deslizador("Rapidez", k.velocidad, 0.1f, 5f, { v -> cambiar(k.copy(velocidad = v)) }, neutro = 1f, escala = Escala.LOGARITMICA, texto = { "%.2gx".format(it) })
        }
    }
    Spacer(Modifier.height(4.dp))
    BotonBorde(if (agregando) "Elige la pieza" else "Sumar otra pieza", { agregando = !agregando }, glifo = Glifos.mas)
    AnimatedVisibility(agregando) {
        Opciones(AnimacionContinua.values().toList(), null, { it.nombre }, { a ->
            poner(piezas + Continuo(a)); agregando = false
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
    }
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
    }
}
