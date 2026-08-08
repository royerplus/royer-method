package com.royermethod.app.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.royermethod.app.ui.theme.Ink

/**
 * Comparador a pantalla completa: el mismo instante de los dos videos, uno a
 * cada lado de una linea que se arrastra.
 *
 * Los dos van a la vez y en bucle. Es la unica forma de comparar de verdad:
 * puestos uno detras de otro no se recuerda el detalle, y en paralelo pero
 * pequenos tampoco se aprecia. Con la linea, el ojo compara el mismo pixel
 * antes y despues sin moverse.
 *
 * Se puede acercar con dos dedos, porque la diferencia entre reducir bien y
 * reducir mal esta en el filo de las lineas y a tamano de pantalla no se ve.
 * Al acercar, los dos se mueven juntos: si cada uno fuera por su lado no se
 * estaria comparando el mismo trozo de imagen.
 */
@Composable
fun ComparadorCompleto(
    antes: Uri,
    despues: Uri,
    sonido: Boolean,
    onCerrar: () -> Unit
) {
    Dialog(
        onDismissRequest = onCerrar,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        VentanaCompleta()

        val pareja = recordarPareja(antes, despues, sonido)

        var division by remember { mutableFloatStateOf(0.5f) }
        var escala by remember { mutableFloatStateOf(1f) }
        var mover by remember { mutableStateOf(Offset.Zero) }
        val medidor = rememberTextMeasurer()
        val densidad = LocalDensity.current

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Ink.Black)
                .clipToBounds()
                // Dos dedos acercan y arrastran. El asa de la linea, que es un
                // hijo, recibe antes el toque, asi que arrastrarla no mueve la
                // imagen y viceversa.
                .pointerInput(Unit) {
                    detectTransformGestures { _, arrastre, acercar, _ ->
                        escala = (escala * acercar).coerceIn(1f, 8f)
                        mover = if (escala <= 1.001f) Offset.Zero else mover + arrastre
                    }
                }
                .pointerInput(Unit) {
                    // Dos toques rapidos: acercar y volver, que es lo que todo
                    // el mundo intenta antes de pellizcar.
                    detectTapGestures(onDoubleTap = {
                        if (escala > 1.001f) {
                            escala = 1f
                            mover = Offset.Zero
                        } else {
                            escala = 3f
                        }
                    })
                }
        ) {
            val lente = Modifier.graphicsLayer {
                scaleX = escala
                scaleY = escala
                translationX = mover.x
                translationY = mover.y
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        clipRect(right = size.width * division) {
                            this@drawWithContent.drawContent()
                        }
                    }
            ) {
                LienzoLado(pareja.izquierdo, Modifier.fillMaxSize().then(lente))
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        clipRect(left = size.width * division) {
                            this@drawWithContent.drawContent()
                        }
                    }
            ) {
                LienzoLado(pareja.derecho, Modifier.fillMaxSize().then(lente))
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        drawContent()
                        dibujarDivision(division, medidor)
                    }
            )

            // Franja invisible pegada a la linea: es lo que se arrastra para
            // moverla. Sin ella habria que acertar sobre dos pixeles de ancho.
            val anchoTotal = maxWidth
            Box(
                Modifier
                    .offset(x = anchoTotal * division - 32.dp)
                    .width(64.dp)
                    .fillMaxHeight()
                    .pointerInput(anchoTotal) {
                        detectDragGestures { cambio, desplazamiento ->
                            cambio.consume()
                            val paso = with(densidad) { desplazamiento.x.toDp() / anchoTotal }
                            division = (division + paso).coerceIn(0f, 1f)
                        }
                    }
            )

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 44.dp, end = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (escala > 1.001f) {
                    BotonRedondo(
                        glifo = Glifo.Lupa,
                        onClick = { escala = 1f; mover = Offset.Zero },
                        modifier = Modifier.size(40.dp)
                    )
                }
                BotonRedondo(
                    glifo = Glifo.Cerrar,
                    onClick = onCerrar,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
    }
}

/**
 * El mismo comparador, pero con dos imagenes quietas.
 *
 * Aqui el zoom importa todavia mas que en el video: la diferencia entre
 * reducir bien y reducir mal esta en el filo de las lineas, y a tamano de
 * pantalla no se distingue nada.
 */
@Composable
fun ComparadorImagenes(
    antes: androidx.compose.ui.graphics.ImageBitmap,
    despues: androidx.compose.ui.graphics.ImageBitmap,
    onCerrar: () -> Unit
) {
    Dialog(
        onDismissRequest = onCerrar,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        VentanaCompleta()

        var division by remember { mutableFloatStateOf(0.5f) }
        var escala by remember { mutableFloatStateOf(1f) }
        var mover by remember { mutableStateOf(Offset.Zero) }
        val medidor = rememberTextMeasurer()
        val densidad = LocalDensity.current

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Ink.Black)
                .clipToBounds()
                .pointerInput(Unit) {
                    detectTransformGestures { _, arrastre, acercar, _ ->
                        escala = (escala * acercar).coerceIn(1f, 12f)
                        mover = if (escala <= 1.001f) Offset.Zero else mover + arrastre
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = {
                        if (escala > 1.001f) {
                            escala = 1f
                            mover = Offset.Zero
                        } else escala = 4f
                    })
                }
        ) {
            val lente = Modifier.graphicsLayer {
                scaleX = escala
                scaleY = escala
                translationX = mover.x
                translationY = mover.y
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        clipRect(right = size.width * division) {
                            this@drawWithContent.drawContent()
                        }
                    }
            ) {
                androidx.compose.foundation.Image(
                    bitmap = antes,
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().then(lente)
                )
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        clipRect(left = size.width * division) {
                            this@drawWithContent.drawContent()
                        }
                    }
            ) {
                androidx.compose.foundation.Image(
                    bitmap = despues,
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().then(lente)
                )
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        drawContent()
                        dibujarDivision(division, medidor)
                    }
            )

            val anchoTotal = maxWidth
            Box(
                Modifier
                    .offset(x = anchoTotal * division - 32.dp)
                    .width(64.dp)
                    .fillMaxHeight()
                    .pointerInput(anchoTotal) {
                        detectDragGestures { cambio, desplazamiento ->
                            cambio.consume()
                            val paso = with(densidad) { desplazamiento.x.toDp() / anchoTotal }
                            division = (division + paso).coerceIn(0f, 1f)
                        }
                    }
            )

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 44.dp, end = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (escala > 1.001f) {
                    BotonRedondo(
                        glifo = Glifo.Lupa,
                        onClick = { escala = 1f; mover = Offset.Zero },
                        modifier = Modifier.size(40.dp)
                    )
                }
                BotonRedondo(
                    glifo = Glifo.Cerrar,
                    onClick = onCerrar,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
    }
}

/**
 * La linea y las dos etiquetas.
 *
 * Cada etiqueta se dibuja recortada por la linea: la parte que queda del lado
 * de su propio video se ve, y la que la linea ya ha cruzado se borra del todo.
 * Asi la palabra se va comiendo conforme pasa la linea en lugar de
 * desaparecer de golpe, y nunca queda un rotulo sobre el video que no le
 * corresponde.
 */
private fun DrawScope.dibujarDivision(division: Float, medidor: TextMeasurer) {
    val x = size.width * division
    val estilo = TextStyle(color = Color.White, fontSize = 12.sp, letterSpacing = 3.sp)
    val y = size.height * 0.075f

    fun etiqueta(texto: String, cx: Float, propioIzquierda: Boolean) {
        val medido = medidor.measure(texto, estilo)
        val izq = cx - medido.size.width / 2f
        val caja = Rect(izq, y, izq + medido.size.width, y + medido.size.height)
        val visible = if (propioIzquierda) {
            Rect(caja.left, caja.top, minOf(caja.right, x), caja.bottom)
        } else {
            Rect(maxOf(caja.left, x), caja.top, caja.right, caja.bottom)
        }
        if (visible.width <= 0f) return
        clipRect(visible.left, visible.top, visible.right, visible.bottom) {
            // Sombra debajo: sobre un fotograma claro el texto blanco se
            // perderia igual que se perdia la linea.
            drawText(medidor, texto, Offset(izq + 1f, y + 1f), estilo.copy(color = Color.Black.copy(alpha = 0.5f)))
            drawText(medidor, texto, Offset(izq, y), estilo)
        }
    }

    etiqueta("ANTIGUO", size.width * 0.25f, propioIzquierda = true)
    etiqueta("NUEVO", size.width * 0.75f, propioIzquierda = false)

    // halo para que la linea se vea sobre fondo claro y sobre fondo oscuro
    drawLine(
        Color.Black, alpha = 0.35f,
        start = Offset(x, 0f), end = Offset(x, size.height), strokeWidth = 7f
    )
    drawLine(
        Color.White,
        start = Offset(x, 0f), end = Offset(x, size.height), strokeWidth = 2.5f
    )

    // asa central, para que se entienda que se arrastra
    val cy = size.height / 2
    drawCircle(Color.Black, alpha = 0.35f, radius = 23f, center = Offset(x, cy))
    drawCircle(Color.White, radius = 20f, center = Offset(x, cy), style = Stroke(2.5f))
    for (lado in listOf(-1f, 1f)) {
        drawLine(
            Color.White,
            start = Offset(x + 6f * lado, cy - 5f),
            end = Offset(x + 11f * lado, cy),
            strokeWidth = 2.5f, cap = StrokeCap.Round
        )
        drawLine(
            Color.White,
            start = Offset(x + 11f * lado, cy),
            end = Offset(x + 6f * lado, cy + 5f),
            strokeWidth = 2.5f, cap = StrokeCap.Round
        )
    }
}
