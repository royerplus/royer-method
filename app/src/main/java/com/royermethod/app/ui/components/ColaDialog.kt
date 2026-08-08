package com.royermethod.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.royermethod.app.procesado.Procesador
import com.royermethod.app.ui.theme.Ink

/**
 * Ajustes de como trabaja la app, no del archivo.
 *
 * De momento solo hay uno: procesar varios seguidos. Con eso encendido,
 * Procesar deja de arrancar nada y apila; cuando estan todos, se lanzan uno
 * detras de otro.
 *
 * Lo que sale se guarda solo en la galeria. Es lo unico coherente: la cola
 * existe para dejarla trabajando de noche, y no va a haber nadie delante para
 * darle a guardar en cada uno.
 */
@Composable
fun ColaDialog(onIniciar: () -> Unit, onCerrar: () -> Unit) {
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
                .padding(22.dp)
        ) {
            Text(
                text = "AJUSTES",
                style = MaterialTheme.typography.labelSmall,
                color = Ink.White50
            )

            Spacer(Modifier.height(18.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Procesar varios seguidos",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink.White90,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(14.dp))
                Interruptor(
                    activo = Procesador.enLote,
                    onCambio = { Procesador.enLote = it }
                )
            }

            if (Procesador.enLote) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = if (Procesador.cola.isEmpty()) {
                        "Prepara un archivo y dale a Anadir a la cola."
                    } else {
                        "${Procesador.cola.size} en espera. Se guardan solos al terminar."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Ink.White30
                )

                if (Procesador.cola.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Column(
                        Modifier
                            .heightIn(max = 190.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Procesador.cola.forEachIndexed { i, t ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${i + 1}.  ${t.medio.nombre}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Ink.White70,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f)
                                )
                                Box(
                                    Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .clickable(
                                            interactionSource = remember {
                                                MutableInteractionSource()
                                            },
                                            indication = null
                                        ) { Procesador.quitarDeCola(i) }
                                        .drawBehind {
                                            val c = center
                                            val r = size.width * 0.18f
                                            drawLine(
                                                Ink.White, alpha = 0.6f,
                                                start = Offset(c.x - r, c.y - r),
                                                end = Offset(c.x + r, c.y + r),
                                                strokeWidth = 1.4.dp.toPx(),
                                                cap = StrokeCap.Round
                                            )
                                            drawLine(
                                                Ink.White, alpha = 0.6f,
                                                start = Offset(c.x + r, c.y - r),
                                                end = Offset(c.x - r, c.y + r),
                                                strokeWidth = 1.4.dp.toPx(),
                                                cap = StrokeCap.Round
                                            )
                                        }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    NeonButton(
                        text = "Iniciar",
                        onClick = { onIniciar(); onCerrar() },
                        height = 50.dp
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            GhostButton(text = "Cerrar", onClick = onCerrar, height = 46.dp)
        }
    }
}

@Composable
private fun Interruptor(activo: Boolean, onCambio: (Boolean) -> Unit) {
    Box(
        Modifier
            .size(width = 46.dp, height = 26.dp)
            .clip(RoundedCornerShape(13.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onCambio(!activo) }
            .drawBehind {
                drawRoundRect(
                    color = Ink.White,
                    alpha = if (activo) 0.85f else 0.10f,
                    cornerRadius = CornerRadius(size.height / 2)
                )
                val r = size.height / 2 - 3.dp.toPx()
                drawCircle(
                    color = if (activo) Ink.Black else Ink.White,
                    alpha = if (activo) 1f else 0.5f,
                    radius = r,
                    center = Offset(
                        if (activo) size.width - r - 3.dp.toPx() else r + 3.dp.toPx(),
                        size.height / 2
                    )
                )
            }
    )
}
