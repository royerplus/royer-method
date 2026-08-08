package com.royermethod.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.royermethod.app.media.tiempo
import com.royermethod.app.procesado.Tramo
import com.royermethod.app.ui.theme.Ink

/**
 * Que trozos del video van a camara lenta.
 *
 * Un tramo es un intervalo de tiempo con su factor. Se pueden poner varios y
 * lo que quede fuera va a velocidad normal, asi que se puede alternar rapido
 * y lento sin salir de aqui.
 *
 * El factor tiene techo, y no es un capricho: la camara lenta sale de repartir
 * los fotogramas generados en vez de mezclarlos, asi que no se puede ir mas
 * lento que fotogramas se hayan generado por cada uno de salida. Para bajar
 * mas, hay que generar mas.
 */
@Composable
fun TramosDialog(
    duracionMs: Int,
    tramos: List<Tramo>,
    factorMaximo: Int,
    onGuardar: (List<Tramo>) -> Unit,
    onCerrar: () -> Unit
) {
    val lista = remember {
        mutableStateListOf<Tramo>().apply {
            if (tramos.isEmpty()) {
                // Por defecto, el clip entero: es lo que casi siempre se quiere.
                add(Tramo(0, duracionMs, minOf(2, factorMaximo).coerceAtLeast(1)))
            } else {
                addAll(tramos)
            }
        }
    }
    val factores = remember(factorMaximo) {
        listOf(2, 4, 8).filter { it <= factorMaximo }.ifEmpty { listOf(1) }
    }

    Dialog(onDismissRequest = onCerrar) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .drawBehind {
                    drawRoundRect(color = Ink.Black, cornerRadius = CornerRadius(24.dp.toPx()))
                    drawRoundRect(
                        color = Ink.White, alpha = 0.06f,
                        cornerRadius = CornerRadius(24.dp.toPx())
                    )
                    drawRoundRect(
                        color = Ink.White, alpha = 0.20f,
                        cornerRadius = CornerRadius(24.dp.toPx()),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
                .padding(20.dp)
        ) {
            Text(
                text = "CAMARA LENTA",
                style = MaterialTheme.typography.labelSmall,
                color = Ink.White50
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Marca los trozos que van despacio. Lo que quede fuera va " +
                    "a su velocidad.",
                style = MaterialTheme.typography.labelSmall,
                color = Ink.White30
            )

            Spacer(Modifier.height(16.dp))

            Column(
                Modifier
                    .heightIn(max = 300.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                lista.forEachIndexed { i, t ->
                    FilaTramo(
                        tramo = t,
                        duracionMs = duracionMs,
                        factores = factores,
                        onCambio = { lista[i] = it },
                        onBorrar = { if (lista.size > 1) lista.removeAt(i) }
                    )
                    Spacer(Modifier.height(14.dp))
                }
            }

            if (lista.size < 4) {
                BotonLinea(texto = "Anadir tramo") {
                    val ultimo = lista.maxOf { it.hastaMs }
                    val hueco = duracionMs - ultimo
                    if (hueco > 400) {
                        lista.add(Tramo(ultimo, minOf(duracionMs, ultimo + hueco / 2), factores[0]))
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            NeonButton(
                text = "Listo",
                onClick = { onGuardar(lista.toList()); onCerrar() },
                height = 50.dp
            )
        }
    }
}

@Composable
private fun FilaTramo(
    tramo: Tramo,
    duracionMs: Int,
    factores: List<Int>,
    onCambio: (Tramo) -> Unit,
    onBorrar: () -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${tiempo(tramo.desdeMs)}  a  ${tiempo(tramo.hastaMs)}",
                style = MaterialTheme.typography.bodyMedium,
                color = Ink.White90,
                modifier = Modifier.weight(1f)
            )
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .drawBehind {
                        drawCircle(Ink.White, alpha = 0.08f)
                        val r = size.width * 0.20f
                        val c = size.width / 2
                        drawLine(
                            Ink.White, alpha = 0.7f,
                            start = Offset(c - r, c - r), end = Offset(c + r, c + r),
                            strokeWidth = 1.4.dp.toPx(), cap = StrokeCap.Round
                        )
                        drawLine(
                            Ink.White, alpha = 0.7f,
                            start = Offset(c + r, c - r), end = Offset(c - r, c + r),
                            strokeWidth = 1.4.dp.toPx(), cap = StrokeCap.Round
                        )
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onBorrar
                    )
            )
        }

        Spacer(Modifier.height(8.dp))

        BarraTramo(
            tramo = tramo,
            duracionMs = duracionMs,
            onCambio = onCambio
        )

        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            factores.forEach { f ->
                ChipFactor(
                    texto = "${f}x",
                    activo = tramo.factor == f,
                    onClick = { onCambio(tramo.copy(factor = f)) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * El tramo dibujado sobre la duracion del clip.
 *
 * Se arrastra por los extremos. Cual de los dos se mueve lo decide cual esta
 * mas cerca del dedo al empezar, que evita tener que acertar sobre un asa de
 * dos milimetros.
 */
@Composable
private fun BarraTramo(tramo: Tramo, duracionMs: Int, onCambio: (Tramo) -> Unit) {
    val densidad = LocalDensity.current
    var moviendoInicio by remember { mutableStateOf(true) }

    Box(
        Modifier
            .fillMaxWidth()
            .height(34.dp)
            .pointerInput(duracionMs) {
                detectHorizontalDragGestures(
                    onDragStart = { punto ->
                        val f = punto.x / size.width
                        val a = tramo.desdeMs.toFloat() / duracionMs
                        val b = tramo.hastaMs.toFloat() / duracionMs
                        moviendoInicio = kotlin.math.abs(f - a) < kotlin.math.abs(f - b)
                    }
                ) { cambio, _ ->
                    cambio.consume()
                    val ms = ((cambio.position.x / size.width) * duracionMs)
                        .toInt().coerceIn(0, duracionMs)
                    if (moviendoInicio) {
                        onCambio(tramo.copy(desdeMs = ms.coerceAtMost(tramo.hastaMs - 200)))
                    } else {
                        onCambio(tramo.copy(hastaMs = ms.coerceAtLeast(tramo.desdeMs + 200)))
                    }
                }
            }
            .drawBehind {
                val y = size.height / 2
                val alto = 6.dp.toPx()
                drawRoundRect(
                    color = Ink.White, alpha = 0.10f,
                    topLeft = Offset(0f, y - alto / 2),
                    size = Size(size.width, alto),
                    cornerRadius = CornerRadius(alto / 2)
                )
                val a = size.width * (tramo.desdeMs.toFloat() / duracionMs)
                val b = size.width * (tramo.hastaMs.toFloat() / duracionMs)
                drawRoundRect(
                    color = Ink.White, alpha = 0.85f,
                    topLeft = Offset(a, y - alto / 2),
                    size = Size((b - a).coerceAtLeast(2f), alto),
                    cornerRadius = CornerRadius(alto / 2)
                )
                drawCircle(Ink.White, radius = 8.dp.toPx(), center = Offset(a, y))
                drawCircle(Ink.White, radius = 8.dp.toPx(), center = Offset(b, y))
                drawCircle(Ink.Black, radius = 3.dp.toPx(), center = Offset(a, y))
                drawCircle(Ink.Black, radius = 3.dp.toPx(), center = Offset(b, y))
            }
    )
}

@Composable
private fun ChipFactor(
    texto: String,
    activo: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(11.dp))
            .drawBehind {
                drawRoundRect(
                    color = Ink.White,
                    alpha = if (activo) 1f else 0.06f,
                    cornerRadius = CornerRadius(11.dp.toPx())
                )
                if (!activo) {
                    drawRoundRect(
                        color = Ink.White, alpha = 0.14f,
                        cornerRadius = CornerRadius(11.dp.toPx()),
                        style = Stroke(1.dp.toPx())
                    )
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = texto,
            style = MaterialTheme.typography.bodyMedium,
            color = if (activo) Ink.Black else Ink.White70
        )
    }
}

@Composable
private fun BotonLinea(texto: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(13.dp))
            .drawBehind {
                drawRoundRect(
                    color = Ink.White, alpha = 0.04f,
                    cornerRadius = CornerRadius(13.dp.toPx())
                )
                drawRoundRect(
                    color = Ink.White, alpha = 0.14f,
                    cornerRadius = CornerRadius(13.dp.toPx()),
                    style = Stroke(1.dp.toPx())
                )
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = texto.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = Ink.White70
        )
    }
}
