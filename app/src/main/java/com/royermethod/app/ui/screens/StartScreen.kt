package com.royermethod.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.royermethod.app.ui.components.ActionCard
import com.royermethod.app.ui.components.AppHeader
import com.royermethod.app.ui.components.Marca
import com.royermethod.app.ui.components.SectionLabel
import com.royermethod.app.ui.theme.Ink
import com.royermethod.app.ui.theme.Motion

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
    onEdicion: () -> Unit,
    onPersonalizar: () -> Unit
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

        AppHeader(modifier = Modifier.aparece(p, 0), onPersonalizar = onPersonalizar)

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
            text = "Mas calidad y fluidez para tus videos, y un editor para " +
                "dejarlos listos antes de subirlos: sin lag y con el menor peso posible.",
            style = MaterialTheme.typography.bodyLarge,
            color = Ink.White70,
            modifier = Modifier.aparece(p, 2)
        )

        Spacer(Modifier.height(48.dp))

        SectionLabel("Que quieres hacer", modifier = Modifier.aparece(p, 4))

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

        ActionCard(
            titulo = "Editar video",
            subtitulo = "Bloques, capas y transiciones",
            marca = Marca.Edicion,
            onClick = onEdicion,
            desfase = 0.25f,
            modifier = Modifier.aparece(p, 7)
        )

        Spacer(Modifier.height(40.dp))

        Text(
            text = "30 fps estables  ·  maxima calidad",
            style = MaterialTheme.typography.labelSmall,
            color = Ink.White30,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .aparece(p, 8)
        )

        Spacer(Modifier.height(48.dp))
    }
}

