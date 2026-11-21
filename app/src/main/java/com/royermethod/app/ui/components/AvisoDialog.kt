package com.royermethod.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.royermethod.app.ui.theme.Ink
import com.royermethod.app.ui.theme.Motion
import com.royermethod.app.ui.theme.radio

/**
 * Ventana de error.
 *
 * Si el procesado falla hay que decirlo, no volver al principio sin mas: sin
 * mensaje el usuario repite lo mismo esperando otro resultado.
 */
@Composable
fun ErrorDialog(mensaje: String, onCerrar: () -> Unit) {
    Dialog(onDismissRequest = onCerrar) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(radio(24.dp)))
                .drawBehind {
                    drawRoundRect(color = Ink.Black, cornerRadius = CornerRadius(radio(24.dp).toPx()))
                    drawRoundRect(
                        color = Ink.White, alpha = 0.06f,
                        cornerRadius = CornerRadius(radio(24.dp).toPx())
                    )
                    drawRoundRect(
                        color = Ink.White, alpha = 0.20f,
                        cornerRadius = CornerRadius(radio(24.dp).toPx()),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
                .padding(22.dp)
        ) {
            Text(
                text = "NO SE PUDO",
                style = MaterialTheme.typography.labelSmall,
                color = Ink.White50
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = mensaje,
                style = MaterialTheme.typography.bodyMedium,
                color = Ink.White90
            )
            Spacer(Modifier.height(20.dp))
            NeonButton(text = "Entendido", onClick = onCerrar, height = 50.dp)
        }
    }
}

/**
 * Aviso al elegir una resolucion mayor que la del archivo.
 *
 * No se prohibe, se avisa: fuera de las redes sociales puede tener sentido, y
 * quien lo esta pidiendo sabra por que. Pero para una subida a redes es
 * contraproducente y merece decirse antes de que gaste el tiempo.
 */
@Composable
fun SubirDialog(onSeguir: () -> Unit, onCancelar: () -> Unit) {
    Dialog(onDismissRequest = onCancelar) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(radio(24.dp)))
                .drawBehind {
                    drawRoundRect(color = Ink.Black, cornerRadius = CornerRadius(radio(24.dp).toPx()))
                    drawRoundRect(
                        color = Ink.White, alpha = 0.06f,
                        cornerRadius = CornerRadius(radio(24.dp).toPx())
                    )
                    drawRoundRect(
                        color = Ink.White, alpha = 0.20f,
                        cornerRadius = CornerRadius(radio(24.dp).toPx()),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
                .padding(22.dp)
        ) {
            Text(
                text = "VAS POR ENCIMA DEL ORIGINAL",
                style = MaterialTheme.typography.labelSmall,
                color = Ink.White50
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Para videos de redes sociales, aunque subas la resolucion la " +
                    "red la va a bajar igual, y el resultado sale peor que sin tocarla. " +
                    "Si no es para una red social, adelante.",
                style = MaterialTheme.typography.bodyMedium,
                color = Ink.White90
            )
            Spacer(Modifier.height(20.dp))
            NeonButton(text = "Continuar", onClick = onSeguir, height = 50.dp)
            Spacer(Modifier.height(10.dp))
            GhostButton(text = "Dejarlo como esta", onClick = onCancelar, height = 46.dp)
        }
    }
}

/**
 * Aviso emergente al pasar a personalizada.
 *
 * Antes era una franja fija dentro del panel y se comia 65dp permanentes de
 * una pantalla que no puede tener scroll. Como advertencia solo hace falta
 * la primera vez, sale como ventana y con la casilla de no repetirla.
 */
@Composable
fun AvisoDialog(
    texto: String,
    onCerrar: (noRepetir: Boolean) -> Unit
) {
    var noRepetir by remember { mutableStateOf(false) }
    val entrada = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        entrada.animateTo(1f, tween(Motion.NORMAL, easing = Motion.EnterSoft))
    }

    Dialog(onDismissRequest = { onCerrar(noRepetir) }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = entrada.value
                    val s = 0.94f + 0.06f * entrada.value
                    scaleX = s
                    scaleY = s
                }
                .clip(RoundedCornerShape(radio(24.dp)))
                .drawBehind {
                    drawRoundRect(
                        color = Ink.Black,
                        cornerRadius = CornerRadius(radio(24.dp).toPx())
                    )
                    drawRoundRect(
                        color = Ink.White,
                        alpha = 0.06f,
                        cornerRadius = CornerRadius(radio(24.dp).toPx())
                    )
                    drawRoundRect(
                        color = Ink.White,
                        alpha = 0.20f,
                        cornerRadius = CornerRadius(radio(24.dp).toPx()),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
                .padding(22.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(width = 14.dp, height = 26.dp)) {
                    val x = size.width / 2
                    drawLine(
                        Ink.White, alpha = 0.85f,
                        start = Offset(x, size.height * 0.08f),
                        end = Offset(x, size.height * 0.55f),
                        strokeWidth = 1.8.dp.toPx(), cap = StrokeCap.Round
                    )
                    drawCircle(
                        Ink.White, alpha = 0.85f,
                        radius = 1.6.dp.toPx(),
                        center = Offset(x, size.height * 0.76f)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "ANTES DE TOCAR NADA",
                    style = MaterialTheme.typography.labelSmall,
                    color = Ink.White50
                )
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = texto,
                style = MaterialTheme.typography.bodyMedium,
                color = Ink.White90
            )

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(radio(10.dp)))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { noRepetir = !noRepetir }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(18.dp)
                        .clip(RoundedCornerShape(radio(5.dp)))
                        .drawBehind {
                            drawRoundRect(
                                color = Ink.White,
                                alpha = if (noRepetir) 0.9f else 0.06f,
                                cornerRadius = CornerRadius(radio(5.dp).toPx())
                            )
                            drawRoundRect(
                                color = Ink.White,
                                alpha = 0.3f,
                                cornerRadius = CornerRadius(radio(5.dp).toPx()),
                                style = Stroke(width = 1.dp.toPx())
                            )
                        }
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "No volver a mostrar",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink.White70
                )
            }

            Spacer(Modifier.height(18.dp))

            NeonButton(
                text = "Entendido",
                onClick = { onCerrar(noRepetir) },
                height = 50.dp
            )
        }
    }
}
