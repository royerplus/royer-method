package com.royermethod.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.royermethod.app.ui.theme.Ink
import com.royermethod.app.ui.theme.Motion

/** Vuelta completa del destello alrededor del borde. */
private const val RECORRIDO_MS = 5200

/** Barrido de luz cruzando la cara del boton. */
private const val BARRIDO_MS = 4600

/**
 * Marca geometrica de cada accion. Se dibujan a mano, con el mismo lenguaje
 * angular del logo, en vez de tirar de iconos redondeados de Material que
 * desentonarian.
 */
enum class Marca { Video, Imagen }

private fun DrawScope.marca(tipo: Marca, lado: Float) {
    val g = 1.6.dp.toPx()
    val trazo = Stroke(width = g, cap = StrokeCap.Round, join = StrokeJoin.Round)
    when (tipo) {
        Marca.Video -> {
            // marco con un triangulo de reproduccion dentro
            drawRoundRect(
                color = Ink.White,
                alpha = 0.85f,
                size = Size(lado, lado * 0.72f),
                topLeft = Offset(0f, lado * 0.14f),
                cornerRadius = CornerRadius(g * 2),
                style = trazo
            )
            val p = Path().apply {
                moveTo(lado * 0.40f, lado * 0.34f)
                lineTo(lado * 0.66f, lado * 0.50f)
                lineTo(lado * 0.40f, lado * 0.66f)
                close()
            }
            drawPath(p, Ink.White, alpha = 0.95f)
        }
        Marca.Imagen -> {
            // marco con horizonte quebrado, como una foto de paisaje
            drawRoundRect(
                color = Ink.White,
                alpha = 0.85f,
                size = Size(lado, lado * 0.72f),
                topLeft = Offset(0f, lado * 0.14f),
                cornerRadius = CornerRadius(g * 2),
                style = trazo
            )
            val p = Path().apply {
                moveTo(lado * 0.14f, lado * 0.66f)
                lineTo(lado * 0.36f, lado * 0.42f)
                lineTo(lado * 0.54f, lado * 0.60f)
                lineTo(lado * 0.68f, lado * 0.48f)
                lineTo(lado * 0.86f, lado * 0.66f)
            }
            drawPath(p, Ink.White, alpha = 0.9f, style = trazo)
            drawCircle(Ink.White, radius = g * 1.6f, center = Offset(lado * 0.70f, lado * 0.30f), alpha = 0.9f)
        }
    }
}

/**
 * Tarjeta de accion con dos animaciones lentas superpuestas:
 *
 *  - un destello que recorre el perimetro del borde, extraido con PathMeasure
 *    en vez de con un PathEffect a rayas. El PathEffect habria que recrearlo
 *    en cada frame y son cientos de objetos nativos por segundo; asi se
 *    reutiliza siempre el mismo Path y no se asigna nada.
 *  - un barrido de luz que cruza la cara, con el degradado construido una
 *    sola vez y movido con una traslacion.
 *
 * Todo se lee dentro de las lambdas de dibujo, asi que anima sin recomponer.
 *
 * @param desfase 0f..1f separa el destello de esta tarjeta del de las demas,
 *                para que no latan todas a la vez.
 */
@Composable
fun ActionCard(
    titulo: String,
    subtitulo: String,
    marca: Marca,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    desfase: Float = 0f,
    etiqueta: String? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val density = LocalDensity.current

    val ciclo = rememberInfiniteTransition(label = "card")
    val recorrido = ciclo.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(RECORRIDO_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "recorrido"
    )
    val barrido = ciclo.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(BARRIDO_MS, easing = Motion.Breathe),
            repeatMode = RepeatMode.Restart
        ),
        label = "barrido"
    )

    val escalaA = animateFloatAsState(
        targetValue = if (pressed) 0.975f else 1f,
        animationSpec = tween(
            durationMillis = if (pressed) Motion.TAP else Motion.NORMAL,
            easing = Motion.Smooth
        ),
        label = "cardScale"
    )
    val fondoA = animateFloatAsState(
        targetValue = if (pressed) 0.10f else 0.035f,
        animationSpec = tween(if (pressed) Motion.TAP else Motion.NORMAL, easing = Motion.Smooth),
        label = "cardFondo"
    )

    val radio = 24.dp
    val radioPx = with(density) { radio.toPx() }
    val shape = RoundedCornerShape(radio)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(132.dp)
            .graphicsLayer {
                val s = escalaA.value
                scaleX = s
                scaleY = s
            }
            .clip(shape)
            .drawWithCache {
                // Todo esto se construye solo cuando cambia el tamano, no en
                // cada frame: el recorrido del borde, el Path reutilizable
                // donde se recorta el destello, y el degradado del barrido.
                val borde = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = 0f, top = 0f,
                            right = size.width, bottom = size.height,
                            cornerRadius = CornerRadius(radioPx)
                        )
                    )
                }
                val medidor = PathMeasure().apply { setPath(borde, true) }
                val largo = medidor.length
                val destello = Path()
                val trozo = largo * 0.16f

                val anchoBarrido = size.width * 0.42f
                val degradado = Brush.horizontalGradient(
                    0.00f to Color.Transparent,
                    0.45f to Ink.White.copy(alpha = 0.055f),
                    0.55f to Ink.White.copy(alpha = 0.055f),
                    1.00f to Color.Transparent,
                    startX = 0f,
                    endX = anchoBarrido
                )

                onDrawWithContent {
                    // fondo y borde base
                    drawRoundRect(
                        color = Ink.White,
                        alpha = fondoA.value,
                        cornerRadius = CornerRadius(radioPx)
                    )
                    drawRoundRect(
                        color = Ink.White,
                        alpha = 0.13f,
                        cornerRadius = CornerRadius(radioPx),
                        style = Stroke(width = 1.dp.toPx())
                    )

                    // barrido de luz cruzando la cara
                    val x = -anchoBarrido + barrido.value * (size.width + anchoBarrido * 2f)
                    translate(left = x) {
                        drawRect(brush = degradado, size = Size(anchoBarrido, size.height))
                    }

                    drawContent()

                    // destello viajando por el perimetro
                    val t = (recorrido.value + desfase) % 1f
                    val ini = t * largo
                    val fin = ini + trozo
                    destello.reset()
                    if (fin <= largo) {
                        medidor.getSegment(ini, fin, destello, true)
                    } else {
                        // el trozo cruza el punto de cierre: dos tramos
                        medidor.getSegment(ini, largo, destello, true)
                        medidor.getSegment(0f, fin - largo, destello, true)
                    }

                    val capas = floatArrayOf(
                        7.0f, 0.045f,
                        4.0f, 0.075f,
                        2.2f, 0.130f,
                        1.2f, 0.300f
                    )
                    var i = 0
                    while (i < capas.size) {
                        drawPath(
                            path = destello,
                            color = Ink.White,
                            alpha = capas[i + 1],
                            style = Stroke(
                                width = capas[i],
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                        i += 2
                    }
                }
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.foundation.Canvas(modifier = Modifier.size(38.dp)) {
                marca(marca, size.minDimension)
            }

            Spacer(Modifier.width(20.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = titulo.uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink.White
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = subtitulo,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink.White50
                )
            }
        }

        // La etiqueta va anclada a la esquina, no dentro de la fila del
        // titulo: ahi se quedaba sin ancho y se partia en vertical, una letra
        // por linea.
        if (etiqueta != null) {
            Etiqueta(
                texto = etiqueta,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 14.dp, end = 16.dp)
            )
        }
    }
}

/** Distintivo pequeno, para marcar lo que aun no esta hecho. */
@Composable
private fun Etiqueta(texto: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .drawWithCache {
                onDrawBehind {
                    drawRoundRect(
                        color = Ink.White,
                        alpha = 0.10f,
                        cornerRadius = CornerRadius(6.dp.toPx())
                    )
                }
            }
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = texto.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = Ink.White50
        )
    }
}
