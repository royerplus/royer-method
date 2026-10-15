package com.royermethod.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.royermethod.app.procesado.Etapas
import com.royermethod.app.procesado.ProcesadoService
import com.royermethod.app.procesado.Procesador
import com.royermethod.app.procesado.Salida
import com.royermethod.app.procesado.Tareas
import com.royermethod.app.procesado.Trabajo
import com.royermethod.app.ui.components.GhostButton
import com.royermethod.app.ui.components.rememberBreath
import com.royermethod.app.ui.logo.RoyerLogo
import com.royermethod.app.ui.theme.Ink
import com.royermethod.app.ui.theme.Motion
import kotlinx.coroutines.delay

/**
 * Lo que se le esta haciendo al archivo.
 *
 * No es una secuencia de fases: todo ocurre en la misma pasada, fotograma a
 * fotograma. Antes esta lista se encendia por tramos del porcentaje, lo cual
 * era mentira: no habia un momento "comprimiendo" distinto de otro
 * "reduciendo". Ahora sale lo que de verdad se aplica en este encargo, y lo
 * que no se aplica no sale.
 */
private data class Tarea(val etapa: Int, val nombre: String)

private fun tareasDe(trabajo: Trabajo): List<Tarea> = buildList {
    val conModelo = if (trabajo.medio.esVideo) {
        Etapas.FRAMES in trabajo.etapas
    } else {
        Etapas.REALZAR in trabajo.etapas
    }

    add(Tarea(Tareas.PREPARAR, if (conModelo) "Preparando el modelo" else "Analizando el archivo"))

    if (trabajo.medio.esVideo) {
        if (Etapas.FRAMES in trabajo.etapas) add(Tarea(Tareas.GENERAR, "Generando fotogramas"))
        if (Etapas.ESTABILIZAR in trabajo.etapas) add(Tarea(Tareas.ESTABILIZAR, "Estabilizando"))
    } else if (conModelo) {
        add(Tarea(Tareas.GENERAR, "Reconstruyendo"))
    }

    // Solo lo que de verdad se va a hacer. Antes salia "comprimir" siempre,
    // incluso con el boton apagado, y eso es decirle al usuario que se le esta
    // haciendo algo que no se le esta haciendo.
    val hace = buildList {
        if (trabajo.ladoCorto > 0) add("reducir a ${trabajo.ladoCorto}p")
        if (trabajo.medio.esVideo && trabajo.fpsSalida > 0 &&
            Etapas.PESO in trabajo.etapas
        ) {
            add("dejarlo en ${trabajo.fpsSalida} fps")
        }
        if (Etapas.PESO in trabajo.etapas) add("comprimir")
    }.joinToString(", ")

    if (hace.isNotEmpty()) {
        add(Tarea(Tareas.PROCESAR, hace.replaceFirstChar { it.uppercase() }))
    } else {
        add(Tarea(Tareas.PROCESAR, "Escribiendo el video"))
    }

    add(Tarea(Tareas.CERRAR, "Cerrando el archivo"))
}

private fun reloj(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

/**
 * Progreso real.
 *
 * El porcentaje sale de por donde va el video, no de un reloj: un clip de
 * ocho segundos y uno de un minuto tardan cosas muy distintas y una barra
 * inventada mentiria en los dos.
 *
 * El trabajo va en un hilo aparte porque es decodificar y recodificar el
 * archivo entero; hecho en el hilo principal la pantalla se quedaria clavada
 * y las animaciones, que son el punto del diseno, no se moverian.
 */
@Composable
fun ProcessingScreen(
    trabajo: Trabajo,
    onDone: (Salida) -> Unit,
    onCancel: () -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val done by rememberUpdatedState(onDone)
    val error by rememberUpdatedState(onError)
    val tareas = remember(trabajo) { tareasDe(trabajo) }

    val transcurrido = remember { mutableLongStateOf(0L) }
    val enter = remember { Animatable(0f) }

    // El trabajo no vive aqui: lo lleva el servicio, y esta pantalla solo
    // mira. Por eso salir de la app y volver ya no reinicia nada, se
    // reencuentra el trabajo donde estaba.
    LaunchedEffect(Unit) {
        enter.animateTo(1f, tween(Motion.SECTION, easing = Motion.EnterSoft))
    }

    // En cola el servicio ya viene lanzado desde los ajustes; aqui solo se
    // arranca el encargo suelto.
    LaunchedEffect(Unit) {
        if (!Procesador.enLote && !Procesador.corriendo &&
            Procesador.salida == null && Procesador.fallo == null
        ) {
            ProcesadoService.arrancar(context)
        }
    }

    // El cronometro va aparte: el trabajo avisa cuando avanza, que en las
    // etapas caras puede ser cada varios segundos, y mientras tanto el tiempo
    // tiene que seguir corriendo en pantalla.
    LaunchedEffect(Unit) {
        while (true) {
            transcurrido.longValue = System.currentTimeMillis() - Procesador.arranqueMs
            delay(250)
        }
    }

    // En cola, cada archivo terminado deja su salida, pero no hay que ir a la
    // pantalla de resultado hasta que estan todos: los de la cola se guardan
    // solos porque nadie va a estar delante.
    val salida = Procesador.salida
    val terminado = Procesador.terminado
    LaunchedEffect(salida, terminado) {
        if (salida != null && (!Procesador.enLote || terminado)) done(salida)
    }

    val fallo = Procesador.fallo
    LaunchedEffect(fallo) { fallo?.let { error(it) } }

    val avanceActual = Procesador.avance

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink.Black)
            .graphicsLayer { alpha = enter.value },
        contentAlignment = Alignment.Center
    ) {
        // Estos dos derivados son la diferencia entre recomponer en cada
        // fotograma procesado y recomponer solo cuando el numero cambia de
        // verdad: el porcentaje entero se mueve 100 veces, y el paso 5.
        val pct = (avanceActual.fraccion * 100).toInt()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ProgressRing(progress = { Procesador.avance.fraccion })

            Spacer(Modifier.height(44.dp))

            Text(
                text = "$pct%",
                style = MaterialTheme.typography.displayLarge,
                color = Ink.White
            )

            Spacer(Modifier.height(10.dp))

            Tiempos(
                transcurridoMs = transcurrido.longValue,
                fraccion = avanceActual.fraccion,
                hechos = avanceActual.hechos,
                total = avanceActual.total,
                unidad = if (trabajo.medio.esVideo) "fotograma" else "franja"
            )

            Spacer(Modifier.height(20.dp))

            if (Procesador.enLote && Procesador.cola.isNotEmpty()) {
                Text(
                    text = "Archivo ${Procesador.hechosDeLote + 1} de ${Procesador.cola.size}" +
                        "  ·  se guardan solos",
                    style = MaterialTheme.typography.labelSmall,
                    color = Ink.White50
                )
                Spacer(Modifier.height(8.dp))
            }

            Text(
                text = "Puedes salir de la app: sigue en segundo plano",
                style = MaterialTheme.typography.labelSmall,
                color = Ink.White30
            )

            Spacer(Modifier.height(24.dp))

            ListaTareas(tareas = tareas, actual = avanceActual.etapa)

            Spacer(Modifier.height(36.dp))

            GhostButton(
                text = "Cancelar",
                onClick = {
                    // Solo se avisa: el servicio lo ve en el siguiente
                    // fotograma, suelta la GPU y se cierra solo. Pararlo desde
                    // aqui lo daba por terminado con el trabajo aun en marcha,
                    // y otro encargo podia entrar a usar la GPU a la vez.
                    Procesador.cancelar()
                    onCancel()
                }
            )
        }
    }
}

/**
 * Anillo de progreso con el logo dentro. El arco de fondo respira y el de
 * progreso deja una estela mas tenue por delante, para que se lea el avance
 * incluso cuando esta casi lleno.
 */
@Composable
private fun ProgressRing(progress: () -> Float) {
    val breath = rememberBreath(0.5f, 0.9f)
    val spin = rememberInfiniteTransition(label = "spin")
    // Sin "by": el angulo se lee dentro del Canvas, en fase de dibujo. Leerlo
    // aqui recompondria el anillo entero en cada frame.
    val angle = spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spinAngle"
    )

    Box(contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(228.dp)) {
            val p = progress()
            val stroke = 3f
            val inset = 18f
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)

            // pista
            drawArc(
                color = Ink.White,
                alpha = 0.10f,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )

            // halo del progreso
            for ((w, a) in listOf(16f to 0.05f, 9f to 0.08f, 4f to 0.14f)) {
                drawArc(
                    color = Ink.White,
                    alpha = a,
                    startAngle = -90f,
                    sweepAngle = 360f * p,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = w, cap = StrokeCap.Round)
                )
            }

            // progreso
            drawArc(
                color = Ink.White,
                startAngle = -90f,
                sweepAngle = 360f * p,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke * 1.6f, cap = StrokeCap.Round)
            )

            // destello que gira, da sensacion de actividad continua
            drawArc(
                color = Ink.White,
                alpha = 0.22f,
                startAngle = angle.value,
                sweepAngle = 26f,
                useCenter = false,
                topLeft = Offset(inset - 9f, inset - 9f),
                size = Size(arcSize.width + 18f, arcSize.height + 18f),
                style = Stroke(width = 1.5f, cap = StrokeCap.Round)
            )
        }

        RoyerLogo(
            size = 96.dp,
            glow = 0.9f,
            modifier = Modifier.graphicsLayer { alpha = breath.value }
        )
    }
}

/**
 * Tiempo que lleva y tiempo que falta.
 *
 * Lo que queda se calcula con lo que ha costado hasta ahora, no con una
 * estimacion inventada de antemano: la velocidad depende del archivo y del
 * telefono, y solo el propio trabajo sabe a que ritmo va. Hasta que hay
 * suficiente avance para que la cuenta signifique algo, no se ensena nada.
 */
@Composable
private fun Tiempos(
    transcurridoMs: Long,
    fraccion: Float,
    hechos: Int,
    total: Int,
    unidad: String
) {
    val queda = if (fraccion > 0.02f) {
        (transcurridoMs / fraccion - transcurridoMs).toLong()
    } else -1L

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = if (queda >= 0) "${reloj(transcurridoMs)}  ·  faltan ${reloj(queda)}"
            else "${reloj(transcurridoMs)}  ·  calculando",
            style = MaterialTheme.typography.labelSmall,
            color = Ink.White50
        )
        if (total > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "$unidad $hechos de $total",
                style = MaterialTheme.typography.labelSmall,
                color = Ink.White30
            )
        }
    }
}

/**
 * Las etapas, con la que corre encendida.
 *
 * Las hechas quedan tenues pero visibles, la de ahora en blanco con la raya
 * larga, y las que faltan casi apagadas. Se lee de un vistazo por donde va sin
 * tener que interpretar el porcentaje.
 */
@Composable
private fun ListaTareas(tareas: List<Tarea>, actual: Int) {
    Column(Modifier.fillMaxWidth()) {
        tareas.forEach { tarea ->
            val estado = when {
                tarea.etapa < actual -> 1      // hecha
                tarea.etapa == actual -> 2     // en marcha
                else -> 0                      // pendiente
            }
            val alpha by animateFloatAsState(
                targetValue = when (estado) {
                    1 -> 0.38f
                    2 -> 1f
                    else -> 0.16f
                },
                animationSpec = tween(Motion.NORMAL, easing = Motion.Smooth),
                label = "tareaAlpha${tarea.etapa}"
            )
            val raya by animateFloatAsState(
                targetValue = if (estado == 2) 26f else 10f,
                animationSpec = tween(Motion.NORMAL, easing = Motion.Smooth),
                label = "tareaRaya${tarea.etapa}"
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .width(raya.dp)
                        .height(1.dp)
                        .background(Ink.White.copy(alpha = alpha))
                )
                Spacer(Modifier.width(30.dp - raya.dp + 4.dp))
                Text(
                    text = tarea.nombre,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ink.White.copy(alpha = alpha),
                    textAlign = TextAlign.Start
                )
            }
        }
    }
}
