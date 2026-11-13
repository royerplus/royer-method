package com.royermethod.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.remember
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
import com.royermethod.app.ui.theme.Ink
import com.royermethod.app.ui.theme.Motion
import com.royermethod.app.ui.theme.radio
import kotlinx.coroutines.delay

/**
 * Aviso pasajero, arriba del todo.
 *
 * Entra deslizandose desde la derecha, se queda el tiempo justo para leerlo y
 * se va por donde vino. Va arriba porque abajo estan los botones: un mensaje
 * que aparece justo donde acabas de tocar tapa lo que ibas a mirar despues.
 *
 * No corta nada ni pide nada. Guardar ya ha terminado cuando esto sale; lo
 * unico que hace es confirmarlo, asi que se quita solo.
 */
@Composable
fun AvisoFlotante(
    texto: String,
    visible: Boolean,
    modifier: Modifier = Modifier,
    onFin: () -> Unit
) {
    if (!visible) return

    val entrada = remember { Animatable(0f) }

    LaunchedEffect(texto) {
        entrada.snapTo(0f)
        entrada.animateTo(1f, tween(Motion.NORMAL, easing = Motion.EnterSoft))
        // Dos segundos y medio: da para leer siete palabras sin prisa y no
        // tanto como para estorbar si ya lo has leido.
        delay(2500)
        entrada.animateTo(0f, tween(Motion.NORMAL, easing = Motion.ExitSoft))
        onFin()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .graphicsLayer {
                // Se va por donde vino: sale de la derecha y vuelve a ella.
                translationX = (1f - entrada.value) * size.width
                alpha = entrada.value
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(radio(16.dp)))
                .drawBehind {
                    drawRoundRect(
                        color = Ink.Black,
                        cornerRadius = CornerRadius(radio(16.dp).toPx())
                    )
                    drawRoundRect(
                        color = Ink.White,
                        alpha = 0.08f,
                        cornerRadius = CornerRadius(radio(16.dp).toPx())
                    )
                    drawRoundRect(
                        color = Ink.White,
                        alpha = 0.22f,
                        cornerRadius = CornerRadius(radio(16.dp).toPx()),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Palomita dibujada, del mismo lenguaje de linea fina que el resto.
            Box(
                Modifier
                    .size(18.dp)
                    .drawBehind {
                        val g = 1.8.dp.toPx()
                        drawLine(
                            Ink.White,
                            start = Offset(size.width * 0.16f, size.height * 0.52f),
                            end = Offset(size.width * 0.42f, size.height * 0.78f),
                            strokeWidth = g, cap = StrokeCap.Round
                        )
                        drawLine(
                            Ink.White,
                            start = Offset(size.width * 0.42f, size.height * 0.78f),
                            end = Offset(size.width * 0.86f, size.height * 0.24f),
                            strokeWidth = g, cap = StrokeCap.Round
                        )
                    }
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = texto,
                style = MaterialTheme.typography.bodyMedium,
                color = Ink.White90,
                maxLines = 2
            )
        }
    }
}
