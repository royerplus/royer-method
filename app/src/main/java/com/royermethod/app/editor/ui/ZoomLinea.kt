package com.royermethod.app.editor.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.royermethod.app.editor.modelo.Linea
import com.royermethod.app.editor.modelo.TiempoPreciso
import com.royermethod.app.ui.components.Glifo
import com.royermethod.app.ui.components.Glifos
import com.royermethod.app.ui.components.Icono
import com.royermethod.app.ui.components.esquinas
import com.royermethod.app.ui.theme.Ink
import kotlin.math.roundToLong
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/*
 * Zoom de la linea de tiempo: los botones -/+ que van encima de ella y las
 * cuentas de la regla.
 *
 * El zoom normal llega a EstadoEditor.ZOOM_MAX (1 ms ~ 1 dp). Mantener "+"
 * dos segundos desbloquea el fino (hasta 1 ms = 60 dp), que es lo que hace
 * falta para colocar una clave a la milesima con el dedo; la pastilla "Zoom
 * normal" lo apaga. El progreso de la pulsacion se pinta en la fase de
 * dibujo del boton: ni la linea ni la pantalla se recomponen por el.
 */

/** Las cuentas del zoom y de la regla, sin Compose (se prueban en el ordenador). */
internal object ZoomLinea {

    /** Lo que hay que mantener "+" para desbloquear el zoom fino. */
    const val PULSACION_FINO_MS = 2_000

    /** Con el fino ya puesto, lo que tarda en empezar a repetir y cada cuanto repite. */
    const val ESPERA_REPETIR_MS = 400L
    const val REPETIR_MS = 300L

    /** Cuanto lleva la pulsacion, de 0 a 1. */
    fun progreso(ms: Long): Float = (ms.toFloat() / PULSACION_FINO_MS).coerceIn(0f, 1f)

    /**
     * Un paso de "+": x1.5 hasta el limite normal (se para justo en el, donde
     * avisa del fino) y x2 por encima, sin pasar de [maximo].
     */
    fun ampliado(dp: Float, maximo: Float): Float {
        val v = if (dp < EstadoEditor.ZOOM_MAX) minOf(dp * 1.5f, EstadoEditor.ZOOM_MAX) else dp * 2f
        return v.coerceIn(EstadoEditor.ZOOM_MIN, maxOf(EstadoEditor.ZOOM_MIN, maximo))
    }

    /** Un paso de "-": el de vuelta de [ampliado] (en el fino se para tambien en el limite normal). */
    fun reducido(dp: Float): Float =
        if (dp > EstadoEditor.ZOOM_MAX) maxOf(dp / 2f, EstadoEditor.ZOOM_MAX)
        else maxOf(dp / 1.5f, EstadoEditor.ZOOM_MIN)

    /** Pixeles por microsegundo, en Double: a zoom fino un bloque mide millones de pixeles. */
    fun pxPorUs(dpPorSegundo: Float, densidad: Float): Double = dpPorSegundo.toDouble() * densidad / 1_000_000.0

    /** Donde cae [t] con el cabezal [t0] en [centro]: la resta va en Long, sin perder microsegundos. */
    fun xDe(t: Long, t0: Long, centro: Float, pxPorUs: Double): Float = (centro + (t - t0) * pxPorUs).toFloat()

    /** El instante bajo [x], redondeado al microsegundo (no truncado). */
    fun tiempoEnX(x: Float, t0: Long, centro: Float, pxPorUs: Double): Long =
        t0 + ((x - centro) / pxPorUs).roundToLong()

    /** Pasos entre marcas con nombre: de 1 ms a 5 min. */
    val PASOS_REGLA = longArrayOf(
        1_000, 2_000, 5_000, 10_000, 20_000, 50_000, 100_000, 200_000, 500_000,
        1_000_000, 2_000_000, 5_000_000, 10_000_000, 15_000_000, 30_000_000, 60_000_000, 120_000_000, 300_000_000
    )

    /**
     * Lo menos que separa dos marcas con nombre, en dp. Desde 0,1 s es lo de
     * siempre (70 dp, la regla se ve igual que antes en el zoom normal); por
     * debajo bastan 56 dp, asi 1 ms (60 dp en el fino maximo) tiene nombre.
     */
    fun separacionDp(paso: Long): Float = if (paso < 100_000L) 56f else 70f

    /** El primer paso cuyas marcas quedan a [separacionDp] o mas. */
    fun pasoRegla(dpPorSegundo: Float): Long =
        PASOS_REGLA.firstOrNull { it / 1_000_000.0 * dpPorSegundo >= separacionDp(it) } ?: PASOS_REGLA.last()

    /** Puntos entre dos marcas: cuartos en los pasos que empiezan por 2, quintos en los demas. */
    fun menorRegla(paso: Long): Long = paso / (if (paso.toString().startsWith("2")) 4 else 5)

    /**
     * Nombre de una marca: con milesimas si el paso es menor de 0,1 s
     * ("1.005s"), con decimas hasta 1 s ("1.5s") y "m:ss" por encima.
     */
    fun etiquetaRegla(t: Long, paso: Long): String = when {
        paso < 100_000L -> TiempoPreciso.texto(t).removeSuffix(" s") + "s"
        paso < 1_000_000L -> "%.1fs".format(java.util.Locale.ROOT, t / 1_000_000.0)
        else -> {
            val s = t / 1_000_000
            "%d:%02d".format(s / 60, s % 60)
        }
    }

    /**
     * Las fronteras de fotograma de la linea (Linea.tiempoDe(n), donde caen
     * los cuadros que se exportan) entre [desde] y [hasta], si quedan a
     * [minimoPx] o mas una de otra; si no hay sitio, ninguna.
     */
    fun fronteras(desde: Long, hasta: Long, pxPorUs: Double, minimoPx: Float): LongArray {
        if (hasta < 0 || hasta < desde) return LongArray(0)
        if (1_000_000.0 / Linea.FPS * pxPorUs < minimoPx) return LongArray(0)
        val n0 = Linea.fotogramaDe(desde.coerceAtLeast(0))
        val n1 = Linea.fotogramaEn(hasta)
        if (n1 < n0) return LongArray(0)
        // Acotado por si acaso: en pantalla nunca caben tantos
        val n = minOf(n1 - n0 + 1, 2_000L).toInt()
        return LongArray(n) { Linea.tiempoDe(n0 + it) }
    }
}

/**
 * Los botones de zoom que flotan sobre la linea de tiempo: "Zoom normal"
 * (solo con el fino puesto), "-" y "+". Mantener "+" dos segundos desbloquea
 * el fino; mientras se mantiene se ve "Manten para zoom fino" y un anillo que
 * se va llenando. Botones de 40 dp.
 */
@Composable
fun ControlesZoom(estado: EstadoEditor, modifier: Modifier = Modifier) {
    val alcance = rememberCoroutineScope()
    // El anillo de "+": se lee solo al dibujar el boton
    val progreso = remember { Animatable(0f) }
    var pista by remember { mutableStateOf(false) }
    var pistaN by remember { mutableIntStateOf(0) }
    // Derivados: el pellizco cambia el zoom a cada evento, esto solo cuando cruza un limite
    val puedeReducir by remember { derivedStateOf { estado.dpPorSegundo > EstadoEditor.ZOOM_MIN } }
    val puedeAmpliar by remember { derivedStateOf { !estado.zoomFino || estado.dpPorSegundo < estado.zoomMaximo } }
    val fino = estado.zoomFino

    // La pista se va sola un rato despues de soltar
    LaunchedEffect(pistaN) {
        if (pistaN == 0) return@LaunchedEffect
        delay(2_500)
        pista = false
    }

    Column(modifier, horizontalAlignment = Alignment.End) {
        if (pista && !fino) {
            Text(
                "Manten para zoom fino",
                style = MaterialTheme.typography.bodySmall,
                color = Ink.Black,
                maxLines = 1,
                modifier = Modifier
                    .clip(esquinas(12.dp))
                    .background(Ink.White)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
            Spacer(Modifier.height(6.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (fino) {
                Row(
                    Modifier
                        .heightIn(min = 40.dp)
                        .clip(esquinas(20.dp))
                        .background(Ink.White)
                        .clickable { estado.zoomNormal() }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icono(Glifos.lupa, color = Ink.Black, tam = 16.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("Zoom normal", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = Ink.Black, maxLines = 1)
                }
            }
            BotonZoom(Glifos.menos, puedeReducir, Modifier.clickable(enabled = puedeReducir) {
                estado.ponerZoom(ZoomLinea.reducido(estado.dpPorSegundo))
            })
            BotonZoom(
                Glifos.mas, puedeAmpliar,
                Modifier
                    .drawWithContent {
                        drawContent()
                        val p = progreso.value
                        if (p > 0f) {
                            val g = 3.dp.toPx()
                            drawArc(
                                Ink.White, -90f, 360f * p, useCenter = false,
                                topLeft = androidx.compose.ui.geometry.Offset(g / 2, g / 2),
                                size = androidx.compose.ui.geometry.Size(size.width - g, size.height - g),
                                style = Stroke(g, cap = StrokeCap.Round)
                            )
                        }
                    }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val abajo = awaitFirstDown()
                            abajo.consume()
                            val yaFino = estado.zoomFino
                            val alLimite = !yaFino && estado.dpPorSegundo >= EstadoEditor.ZOOM_MAX
                            // Ya amplio solo (al desbloquear o repitiendo): soltar no da otro paso
                            var amplio = false
                            var avisoPista: Job? = null
                            if (!yaFino) avisoPista = alcance.launch {
                                // Un toque rapido no la ensena, salvo en el limite: ahi es la pista
                                if (!alLimite) delay(250)
                                pista = true
                                pistaN++
                            }
                            val trabajo = alcance.launch {
                                if (!yaFino) {
                                    progreso.snapTo(0f)
                                    progreso.animateTo(1f, tween(ZoomLinea.PULSACION_FINO_MS, easing = LinearEasing))
                                    estado.zoomFino = true
                                    pista = false
                                    estado.avisar("Zoom fino: 1 ms = 60 dp")
                                    progreso.snapTo(0f)
                                } else {
                                    delay(ZoomLinea.ESPERA_REPETIR_MS)
                                }
                                // Mientras siga pulsado, sigue ampliando hasta el maximo
                                while (true) {
                                    estado.ponerZoom(ZoomLinea.ampliado(estado.dpPorSegundo, estado.zoomMaximo))
                                    amplio = true
                                    delay(ZoomLinea.REPETIR_MS)
                                }
                            }
                            val arriba = waitForUpOrCancellation()
                            trabajo.cancel()
                            if (arriba != null && !amplio) {
                                // Soltar antes de tiempo: un paso normal
                                avisoPista?.cancel()
                                estado.ponerZoom(ZoomLinea.ampliado(estado.dpPorSegundo, estado.zoomMaximo))
                                if (alLimite) { pista = true; pistaN++ }
                            }
                            alcance.launch { progreso.snapTo(0f) }
                        }
                    }
            )
        }
    }
}

/** Boton redondo del zoom: 40 dp, con fondo para leerse encima de la linea. */
@Composable
private fun BotonZoom(glifo: Glifo, activo: Boolean, gesto: Modifier) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Ink.Black.copy(alpha = 0.88f))
            .border(1.dp, Ink.White30, CircleShape)
            .then(gesto)
            .alpha(if (activo) 1f else 0.35f),
        contentAlignment = Alignment.Center
    ) {
        Icono(glifo, color = Ink.White, tam = 20.dp)
    }
}
