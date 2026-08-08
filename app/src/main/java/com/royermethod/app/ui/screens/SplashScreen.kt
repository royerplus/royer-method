package com.royermethod.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.royermethod.app.ui.components.rememberBreath
import com.royermethod.app.ui.logo.RoyerLogo
import com.royermethod.app.ui.theme.Ink
import com.royermethod.app.ui.theme.Motion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val APP_NAME = "ROYER METHOD"

// Medidas calibradas para que el bloque completo (logo + hueco + nombre)
// quepa entero en un telefono estrecho de 360dp. Con el logo a 116dp y el
// nombre a 34sp el conjunto medida ~490dp y se salia de la pantalla.
private val LOGO_SIZE = 100.dp
private val LOGO_GAP = 16.dp

/** Estilo propio del nombre en el splash, no el displayLarge del tema. */
private val NameStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Light,
    fontSize = 20.sp,
    letterSpacing = 5.sp
)

/**
 * Secuencia:
 *  1. El logo aparece en el centro exacto de la pantalla y se dibuja solo,
 *     por capas: primero la silueta, luego las aristas, al final los ojos.
 *  2. Pausa breve respirando.
 *  3. El logo se desliza a la izquierda. Mientras se mueve van encendiendose
 *     las letras del nombre una por una, como un letrero que prende.
 *  4. Al terminar, logo y nombre quedan centrados como un solo bloque.
 *  5. Todo se desvanece y entra la pantalla principal.
 *
 * Truco de posicionamiento: el bloque final (logo + hueco + nombre) se
 * maqueta ya centrado. Al logo se le aplica un desplazamiento hacia la
 * derecha igual a la mitad de (hueco + nombre), que es exactamente lo que
 * lo deja en el centro de la pantalla. Ese desplazamiento se anima a cero.
 * Asi el punto de partida y el de llegada son correctos en cualquier ancho
 * de pantalla, sin numeros magicos.
 */
@Composable
fun SplashScreen(
    onFinished: () -> Unit
) {
    val density = LocalDensity.current
    val finished by rememberUpdatedState(onFinished)

    var nameWidthPx by remember { mutableFloatStateOf(0f) }
    val ready = nameWidthPx > 0f

    val logoAlpha = remember { Animatable(0f) }
    val logoScale = remember { Animatable(0.82f) }
    val drawProgress = remember { Animatable(0f) }
    val slide = remember { Animatable(0f) }
    val screenAlpha = remember { Animatable(1f) }

    val breath = rememberBreath(0.70f, 1f)

    LaunchedEffect(ready) {
        if (!ready) return@LaunchedEffect

        // 1. entrada del logo
        launch { logoAlpha.animateTo(1f, tween(Motion.SPLASH_LOGO_IN, easing = Motion.EnterSoft)) }
        launch { logoScale.animateTo(1f, tween(Motion.SPLASH_LOGO_IN, easing = Motion.Settle)) }
        drawProgress.animateTo(1f, tween(Motion.SPLASH_LOGO_IN, easing = Motion.Smooth))

        // 2. respiro
        delay(Motion.SPLASH_HOLD.toLong())

        // 3. desplazamiento + encendido de letras
        slide.animateTo(1f, tween(Motion.SPLASH_SLIDE, easing = Motion.Smooth))

        // 4. asentado
        delay(Motion.SPLASH_SETTLE.toLong())

        // 5. salida
        screenAlpha.animateTo(0f, tween(Motion.SPLASH_OUT, easing = Motion.ExitSoft))
        finished()
    }

    val gapPx = with(density) { LOGO_GAP.toPx() }
    val logoPx = with(density) { LOGO_SIZE.toPx() }
    // Cuanto tiene que correrse el logo a la derecha para quedar centrado.
    val shift = (nameWidthPx + gapPx) / 2f

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink.Black)
            .graphicsLayer { alpha = screenAlpha.value },
        contentAlignment = Alignment.Center
    ) {
        // El bloque completo mide unos 313dp. En un telefono de 360dp entra
        // por poco, y en uno mas estrecho o con la fuente del sistema en
        // grande se sale y el nombre aparece cortado. En vez de confiar en
        // que quepa, se mide y se reduce lo justo para que quepa siempre.
        val margenPx = with(density) { 40.dp.toPx() }
        val disponible = constraints.maxWidth - margenPx
        val total = logoPx + gapPx + nameWidthPx
        val encaje = if (nameWidthPx > 0f && total > disponible) disponible / total else 1f

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.graphicsLayer {
                scaleX = encaje
                scaleY = encaje
            }
        ) {

            RoyerLogo(
                size = LOGO_SIZE,
                glow = 1f,
                progress = drawProgress.value,
                modifier = Modifier.graphicsLayer {
                    alpha = logoAlpha.value * breath.value
                    scaleX = logoScale.value
                    scaleY = logoScale.value
                    // se va de derecha (centro de pantalla) a su sitio en la fila
                    translationX = shift * (1f - slide.value)
                }
            )

            Spacer(Modifier.width(LOGO_GAP))

            NameReveal(
                progress = { slide.value },
                breath = breath,
                modifier = Modifier.onGloballyPositioned {
                    if (nameWidthPx == 0f) nameWidthPx = it.size.width.toFloat()
                }
            )
        }
    }
}

/**
 * El nombre ocupa su ancho completo desde el primer frame (por eso la
 * medicion es estable), pero cada letra arranca invisible y se enciende
 * en cadena conforme avanza el progreso.
 */
@Composable
private fun NameReveal(
    progress: () -> Float,
    breath: State<Float>,
    modifier: Modifier = Modifier
) {
    val chars = APP_NAME.toList()
    val step = 0.055f
    val window = 0.34f

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        chars.forEachIndexed { i, c ->
            if (c == ' ') {
                Spacer(Modifier.width(10.dp))
            } else {
                Text(
                    text = c.toString(),
                    style = NameStyle,
                    color = Ink.White,
                    textAlign = TextAlign.Center,
                    // El progreso se lee aqui dentro, no en la composicion.
                    // Leyendolo fuera, estas doce letras se recomponian en
                    // cada frame durante los 2s del desplazamiento, que es
                    // justo cuando se veia entrecortado.
                    modifier = Modifier.graphicsLayer {
                        val a = ((progress() - i * step) / window).coerceIn(0f, 1f)
                        alpha = a * (0.80f + 0.20f * breath.value)
                        // sube un poco al aparecer
                        translationY = (1f - a) * 14f
                        // y entra ligeramente mas ancha
                        scaleX = 0.94f + 0.06f * a
                        scaleY = 0.94f + 0.06f * a
                    }
                )
            }
        }
    }
}
