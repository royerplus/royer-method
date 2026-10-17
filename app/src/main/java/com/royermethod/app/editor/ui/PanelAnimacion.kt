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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.draw.clip
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
import com.royermethod.app.editor.modelo.Linea
import com.royermethod.app.editor.modelo.Movimiento
import com.royermethod.app.ui.components.BotonBorde
import com.royermethod.app.ui.components.BotonIcono
import com.royermethod.app.ui.components.Chip
import com.royermethod.app.ui.components.Cuadricula
import com.royermethod.app.ui.components.Deslizador
import com.royermethod.app.ui.components.Escala
import com.royermethod.app.ui.components.Etiqueta
import com.royermethod.app.ui.components.Glifos
import com.royermethod.app.ui.components.Icono
import com.royermethod.app.ui.components.Mosaico
import com.royermethod.app.ui.components.Opciones
import com.royermethod.app.ui.components.Pestanas
import com.royermethod.app.ui.components.esquinas
import com.royermethod.app.ui.theme.Ink
import kotlin.math.min
import kotlin.math.roundToLong

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
    Pestanas(listOf("Entrada", "Salida", "Siempre", "Head bop"), pestana, { pestana = it })
    Spacer(Modifier.height(6.dp))
    when (pestana) {
        0 -> EntradaOSalida(estado, c, entrada = true)
        1 -> EntradaOSalida(estado, c, entrada = false)
        2 -> Continuas(estado, c)
        else -> PanelHeadBop(estado, c)
    }
}

@Composable
private fun EntradaOSalida(estado: EstadoEditor, c: Clip, entrada: Boolean) {
    val piezas = if (entrada) c.entrada else c.salida
    val duracion = if (entrada) c.duracionEntradaUs else c.duracionSalidaUs
    val maximo = c.duracionUs.coerceAtLeast(0)
    val minimo = min(Linea.tiempoDe(1), maximo)
    fun verDuracion(us: Long) {
        estado.reproduciendo = false
        val local = if (entrada) us / 2 else c.duracionUs - us / 2
        estado.irA((estado.inicioElegido ?: 0L) + local.coerceIn(0, maximo))
    }
    fun poner(l: List<Movimiento>, arrastre: Boolean = false) = estado.cambiarClip(arrastre) {
        // Al poner la primera, una duracion que quepa en el bloque
        val dur = if ((if (entrada) it.entrada else it.salida).isEmpty() && l.isNotEmpty())
            min(AjustesEditor.duracionAnimacionUs, it.duracionUs / 2).coerceIn(minimo, maximo)
        else if (entrada) it.duracionEntradaUs else it.duracionSalidaUs
        if (entrada) it.copy(entrada = l, duracionEntradaUs = dur) else it.copy(salida = l, duracionSalidaUs = dur)
    }

    val listas = Combinaciones.entrada + AjustesEditor.mias
    val grupos = Combinaciones.entradaPorGrupo +
        (if (AjustesEditor.mias.isNotEmpty()) listOf("Mias" to AjustesEditor.mias) else emptyList())
    grupos.forEachIndexed { g, (nombre, combinaciones) ->
        Etiqueta(nombre, if (g > 0) Modifier.padding(top = 8.dp) else Modifier)
        // "Ninguna" va la primera de todas
        Cuadricula((if (g == 0) listOf<Combinaciones.Combinacion?>(null) else emptyList()) + combinaciones) { comb, mod ->
            if (comb == null) Mosaico("Ninguna", piezas.isEmpty(), { poner(emptyList()) }, mod) { col -> ninguna(col) }
            else Mosaico(comb.nombre, comb.piezas == piezas, { poner(comb.piezas) }, mod) { col -> dibujoCombinacion(comb.piezas, col, !entrada) }
        }
    }
    if (piezas.isEmpty()) return
    Spacer(Modifier.height(4.dp))
    TiempoFotogramas(
        "Duracion", duracion, minimo, maximo,
        onCambio = { us, arrastre ->
            estado.cambiarClip(arrastre) {
                if (entrada) it.copy(duracionEntradaUs = us) else it.copy(duracionSalidaUs = us)
            }
            verDuracion(us)
        }
    )
    Piezas(
        piezas,
        duracionUs = duracion.coerceIn(minimo, maximo),
        onCambio = { l, arrastre -> poner(l, arrastre) },
        guardada = listas.any { it.piezas == piezas },
        onGuardar = {
            val nombre = AjustesEditor.nombreNuevo("Mia", AjustesEditor.mias.map { it.nombre })
            AjustesEditor.mias = AjustesEditor.mias + Combinaciones.Combinacion(nombre, piezas)
            AjustesEditor.alCambiar()
            estado.avisar("Guardada como \"$nombre\"")
        }
    )
}

@Composable
private fun Piezas(
    piezas: List<Movimiento>,
    duracionUs: Long,
    onCambio: (List<Movimiento>, Boolean) -> Unit,
    guardada: Boolean,
    onGuardar: () -> Unit
) {
    var abierta by remember { mutableIntStateOf(-1) }
    var agregando by remember { mutableStateOf(false) }
    Spacer(Modifier.height(6.dp))
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
                            if (m.desde > 0f || m.hasta < 1f) "  ·  ${"%.3f–%.3f s".format(m.desde * duracionUs / 1e6, m.hasta * duracionUs / 1e6)}" else "",
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
                        val inicio = (m.desde * duracionUs).toLong().coerceIn(0, duracionUs)
                        val fin = (m.hasta * duracionUs).toLong().coerceIn(inicio, duracionUs)
                        // El motor admite tramos desde 0.001 de la animacion;
                        // para tiempos usuales el limite real es un fotograma.
                        val paso = min(duracionUs, maxOf(Linea.tiempoDe(1), (duracionUs * 0.001).roundToLong()))
                        TiempoFotogramas("Empieza", inicio, 0, (fin - paso).coerceAtLeast(0)) { us, arrastre ->
                            cambiar(m.copy(desde = us.toFloat() / duracionUs), arrastre)
                        }
                        TiempoFotogramas("Termina", fin, (inicio + paso).coerceAtMost(duracionUs), duracionUs) { us, arrastre ->
                            cambiar(m.copy(hasta = us.toFloat() / duracionUs), arrastre)
                        }
                    }
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

/** Unidades reales y pasos exactos de 30 fps, tambien para tramos menores de 0.1 s. */
@Composable
internal fun TiempoFotogramas(
    titulo: String,
    valorUs: Long,
    minimoUs: Long,
    maximoUs: Long,
    onCambio: (Long, Boolean) -> Unit
) {
    val maximo = maxOf(minimoUs, maximoUs)
    val valor = valorUs.coerceIn(minimoUs, maximo)
    fun cuantizar(us: Long) = Linea.tiempoDe((us.coerceAtLeast(0) * Linea.FPS / 1_000_000.0).roundToLong()).coerceIn(minimoUs, maximo)
    if (maximo > minimoUs) {
        Deslizador(
            titulo, valor / 1e6f, minimoUs / 1e6f, maximo / 1e6f,
            { v -> onCambio(cuantizar((v * 1e6).toLong()), true) },
            texto = { "%.3f s".format(it) }
        )
    } else {
        Text("$titulo  ${"%.3f s".format(valor / 1e6)}", style = MaterialTheme.typography.bodyMedium, color = Ink.White70, modifier = Modifier.padding(vertical = 8.dp))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Chip("−1 f", false, {
            val n = (valor * Linea.FPS / 1_000_000.0).roundToLong()
            onCambio(Linea.tiempoDe((n - 1).coerceAtLeast(0)).coerceIn(minimoUs, maximo), false)
        })
        Chip("+1 f", false, {
            val n = (valor * Linea.FPS / 1_000_000.0).roundToLong()
            onCambio(Linea.tiempoDe(n + 1).coerceIn(minimoUs, maximo), false)
        })
        Text("1 f = 0.033 s", style = MaterialTheme.typography.bodySmall, color = Ink.White50)
    }
}

@Composable
private fun Continuas(estado: EstadoEditor, c: Clip) {
    val piezas = c.continua
    fun poner(l: List<Continuo>, arrastre: Boolean = false) = estado.cambiarClip(arrastre) { it.copy(continua = l) }
    val listas = Combinaciones.continuas + AjustesEditor.continuasMias
    val grupos = Combinaciones.continuasPorGrupo +
        (if (AjustesEditor.continuasMias.isNotEmpty()) listOf("Mias" to AjustesEditor.continuasMias) else emptyList())
    grupos.forEachIndexed { g, (nombre, combinaciones) ->
        Etiqueta(nombre, if (g > 0) Modifier.padding(top = 8.dp) else Modifier)
        Cuadricula((if (g == 0) listOf<Pair<String, List<Continuo>>?>(null) else emptyList()) + combinaciones) { par, mod ->
            if (par == null) Mosaico("Ninguna", piezas.isEmpty(), { poner(emptyList()) }, mod) { col -> ninguna(col) }
            else Mosaico(par.first, par.second == piezas, { poner(par.second) }, mod) { col ->
                dibujoContinua(par.second.first().tipo, col)
                // "Zoom y flash" se distingue de "Zoom al ritmo" por los rayos
                if (par.second.drop(1).any { it.tipo == AnimacionContinua.SHAKE_FLASH }) {
                    val (tl, s) = marco()
                    rayos(col, tl, s.width, s.height)
                }
            }
        }
    }
    if (piezas.isEmpty()) return
    var agregando by remember { mutableStateOf(false) }
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Etiqueta("Piezas", Modifier.weight(1f))
        if (listas.none { it.second == piezas }) BotonGuardar {
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
