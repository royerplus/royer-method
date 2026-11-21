package com.royermethod.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.royermethod.app.procesado.Procesador
import com.royermethod.app.procesado.Salida
import com.royermethod.app.procesado.Trabajo
import com.royermethod.app.ui.components.ErrorDialog
import com.royermethod.app.ui.screens.CONFIG_IMAGEN
import com.royermethod.app.ui.screens.CONFIG_VIDEO
import com.royermethod.app.ui.screens.EditorScreen
import com.royermethod.app.ui.screens.MejoraScreen
import com.royermethod.app.ui.screens.PersonalizarScreen
import com.royermethod.app.ui.screens.ProcessingScreen
import com.royermethod.app.ui.screens.ResultScreen
import com.royermethod.app.ui.screens.SplashScreen
import com.royermethod.app.ui.screens.StartScreen
import com.royermethod.app.ui.theme.Ink
import com.royermethod.app.ui.theme.Motion

// El orden importa: la direccion de la transicion se decide comparando el
// ordinal, asi que avanzar en la lista sube y retroceder baja.
private enum class Screen { Splash, Start, Video, Imagen, Edicion, Personalizar, Processing, Result }

private val VUELVEN_AL_INICIO = setOf(Screen.Video, Screen.Imagen, Screen.Personalizar, Screen.Result)

/**
 * Navegacion propia en vez de navigation-compose: son cuatro pantallas y asi
 * controlo exactamente la duracion y la curva de cada transicion, que es el
 * punto del diseno.
 */
@Composable
fun AppRoot() {
    var screen by remember { mutableStateOf(Screen.Splash) }
    // El encargo y su resultado viven aqui y no dentro de cada pantalla: son
    // lo unico que tiene que sobrevivir a un cambio de pantalla.
    var trabajo by remember { mutableStateOf<Trabajo?>(null) }
    var salida by remember { mutableStateOf<Salida?>(null) }
    var fallo by remember { mutableStateOf<String?>(null) }

    fallo?.let { mensaje ->
        ErrorDialog(mensaje = mensaje, onCerrar = { fallo = null })
    }

    // Atras vuelve al inicio en vez de cerrar la app. El editor lleva el suyo
    // (primero cierra paneles); durante el procesado no se toca, que sigue
    // en segundo plano igual.
    BackHandler(enabled = screen in VUELVEN_AL_INICIO) {
        screen = Screen.Start
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Ink.Black)
    ) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                val d = Motion.SECTION
                when {
                    // del splash a la principal: la principal emerge desde el fondo
                    initialState == Screen.Splash ->
                        (fadeIn(tween(d, easing = Motion.EnterSoft)) +
                            scaleIn(
                                initialScale = 1.06f,
                                animationSpec = tween(d, easing = Motion.EnterSoft)
                            ))
                            .togetherWith(fadeOut(tween(Motion.NORMAL, easing = Motion.ExitSoft)))

                    // hacia adelante: sube
                    targetState.ordinal > initialState.ordinal ->
                        (slideInVertically(
                            animationSpec = tween(d, easing = Motion.EnterSoft),
                            initialOffsetY = { it / 5 }
                        ) + fadeIn(tween(d, easing = Motion.EnterSoft)))
                            .togetherWith(
                                slideOutVertically(
                                    animationSpec = tween(d, easing = Motion.Smooth),
                                    targetOffsetY = { -it / 8 }
                                ) + fadeOut(tween(Motion.NORMAL, easing = Motion.ExitSoft))
                            )

                    // hacia atras: baja
                    else ->
                        (slideInVertically(
                            animationSpec = tween(d, easing = Motion.EnterSoft),
                            initialOffsetY = { -it / 5 }
                        ) + fadeIn(tween(d, easing = Motion.EnterSoft)))
                            .togetherWith(
                                slideOutVertically(
                                    animationSpec = tween(d, easing = Motion.Smooth),
                                    targetOffsetY = { it / 8 }
                                ) + fadeOut(tween(Motion.NORMAL, easing = Motion.ExitSoft))
                            )
                }
            },
            label = "screens"
        ) { current ->
            when (current) {
                Screen.Splash -> SplashScreen(onFinished = { screen = Screen.Start })

                Screen.Start -> StartScreen(
                    onVideo = { screen = Screen.Video },
                    onImagen = { screen = Screen.Imagen },
                    onEdicion = { screen = Screen.Edicion },
                    onPersonalizar = { screen = Screen.Personalizar }
                )

                Screen.Edicion -> EditorScreen(onVolver = { screen = Screen.Start })

                Screen.Personalizar -> PersonalizarScreen(onVolver = { screen = Screen.Start })

                // Las dos usan la misma pantalla; solo cambia la configuracion
                // que se le pasa.
                Screen.Video -> MejoraScreen(
                    config = CONFIG_VIDEO,
                    onProcesar = { t ->
                        trabajo = t
                        Procesador.empezar(t)
                        screen = Screen.Processing
                    },
                    onVolver = { screen = Screen.Start },
                    onCola = { screen = Screen.Processing }
                )
                Screen.Imagen -> MejoraScreen(
                    config = CONFIG_IMAGEN,
                    onProcesar = { t ->
                        trabajo = t
                        Procesador.empezar(t)
                        screen = Screen.Processing
                    },
                    onVolver = { screen = Screen.Start },
                    onCola = { screen = Screen.Processing }
                )

                // En cola el encargo lo va poniendo el servicio, uno por uno.
                Screen.Processing -> (Procesador.trabajo ?: trabajo)?.let { t ->
                    ProcessingScreen(
                        trabajo = t,
                        onDone = { s -> salida = s; screen = Screen.Result },
                        onCancel = { screen = Screen.Start },
                        onError = { m -> fallo = m; screen = Screen.Start }
                    )
                }

                Screen.Result -> {
                    val t = Procesador.trabajo ?: trabajo
                    val s = salida
                    if (t != null && s != null) {
                        ResultScreen(
                            original = t.medio.uri,
                            resultado = s.archivo,
                            descripcion = s.descripcion,
                            esVideo = t.medio.esVideo,
                            onBack = {
                                Procesador.limpiar()
                                screen = Screen.Start
                            }
                        )
                    }
                }
            }
        }
    }
}

