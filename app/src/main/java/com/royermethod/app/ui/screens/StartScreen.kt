package com.royermethod.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.royermethod.app.ui.components.ActionCard
import com.royermethod.app.ui.components.AppHeader
import com.royermethod.app.ui.components.Marca
import com.royermethod.app.ui.components.SectionLabel
import com.royermethod.app.ui.components.SegmentedToggle
import com.royermethod.app.ui.theme.Ink
import com.royermethod.app.ui.theme.Motion

/**
 * Entrada al editor.
 *
 * Deliberadamente mas chico que las dos tarjetas de arriba: mejorar un archivo
 * es lo que la app hace de serie, y editar es otro sitio al que se entra
 * sabiendo lo que se busca. Que pese menos en la pantalla lo dice sin tener
 * que explicarlo.
 */
@Composable
private fun BotonEdicion(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaccion = remember { MutableInteractionSource() }
    val pulsado by interaccion.collectIsPressedAsState()
    val fondo by animateFloatAsState(
        targetValue = if (pulsado) 0.11f else 0.035f,
        animationSpec = tween(if (pulsado) Motion.TAP else Motion.NORMAL, easing = Motion.Smooth),
        label = "edicionFondo"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(18.dp))
            .drawBehind {
                drawRoundRect(
                    color = Ink.White, alpha = fondo,
                    cornerRadius = CornerRadius(18.dp.toPx())
                )
                drawRoundRect(
                    color = Ink.White, alpha = 0.12f,
                    cornerRadius = CornerRadius(18.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
            .clickable(interactionSource = interaccion, indication = null, onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Tres bloques en fila: una linea de tiempo en miniatura.
        Box(
            Modifier
                .size(width = 22.dp, height = 14.dp)
                .drawBehind {
                    val hueco = size.width * 0.14f
                    val ancho = (size.width - hueco * 2) / 3
                    for (n in 0..2) {
                        drawRoundRect(
                            color = Ink.White,
                            alpha = if (n == 1) 0.85f else 0.35f,
                            topLeft = Offset(n * (ancho + hueco), 0f),
                            size = Size(ancho, size.height),
                            cornerRadius = CornerRadius(1.5.dp.toPx())
                        )
                    }
                }
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = "EDICION",
            style = MaterialTheme.typography.labelSmall,
            color = Ink.White70
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "Beta",
            style = MaterialTheme.typography.labelSmall,
            color = Ink.White30
        )
    }
}

/**
 * Los bloques no entran todos a la vez: cada uno arranca un poco despues que
 * el anterior. El progreso se lee dentro del graphicsLayer, asi que la
 * entrada anima sin recomponer nada.
 */
private fun Modifier.aparece(progreso: () -> Float, orden: Int): Modifier =
    this.graphicsLayer {
        val retardo = orden * 0.10f
        val a = ((progreso() - retardo) / 0.55f).coerceIn(0f, 1f)
        alpha = a
        translationY = (1f - a) * 46f
    }

@Composable
fun StartScreen(
    onVideo: () -> Unit,
    onImagen: () -> Unit,
    onEdicion: () -> Unit
) {
    val entrada = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        entrada.animateTo(1f, tween(Motion.SECTION, easing = Motion.EnterSoft))
    }
    val p = { entrada.value }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink.Black)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(Modifier.height(28.dp))

        AppHeader(modifier = Modifier.aparece(p, 0))

        Spacer(Modifier.height(56.dp))

        Text(
            text = "MEJORA\nTUS SUBIDAS",
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 30.sp,
                letterSpacing = 7.sp,
                lineHeight = 42.sp
            ),
            color = Ink.White,
            modifier = Modifier.aparece(p, 1)
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = "Mejoramos la calidad y la fluidez de tus videos para que " +
                "al subirlos a redes sociales vayan sin ningun tipo de lag, " +
                "con la mayor fluidez y calidad posible.",
            style = MaterialTheme.typography.bodyLarge,
            color = Ink.White70,
            modifier = Modifier.aparece(p, 2)
        )

        Spacer(Modifier.height(48.dp))

        SectionLabel("Que quieres mejorar", modifier = Modifier.aparece(p, 4))

        Spacer(Modifier.height(18.dp))

        ActionCard(
            titulo = "Mejorar video",
            subtitulo = "Mas fluidez, menos peso",
            marca = Marca.Video,
            onClick = onVideo,
            desfase = 0f,
            modifier = Modifier.aparece(p, 5)
        )

        Spacer(Modifier.height(14.dp))

        ActionCard(
            titulo = "Mejorar imagen",
            subtitulo = "Nitidez sin perder detalle",
            marca = Marca.Imagen,
            onClick = onImagen,
            // Medio ciclo de desfase: los dos destellos nunca coinciden, que
            // es lo que hace que se vea vivo y no como un parpadeo unico.
            desfase = 0.5f,
            modifier = Modifier.aparece(p, 6)
        )

        Spacer(Modifier.height(14.dp))

        // Mas pequeno que los otros dos a proposito: mejorar un archivo es lo
        // que hace la app, y editar es un sitio aparte al que se entra
        // sabiendo lo que se busca.
        BotonEdicion(onClick = onEdicion, modifier = Modifier.aparece(p, 7))

        Spacer(Modifier.height(40.dp))

        Text(
            text = "30 fps estables  ·  maxima calidad",
            style = MaterialTheme.typography.labelSmall,
            color = Ink.White30,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .aparece(p, 7)
        )

        Spacer(Modifier.height(48.dp))
    }
}

