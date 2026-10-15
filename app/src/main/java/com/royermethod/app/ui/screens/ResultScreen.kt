package com.royermethod.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.royermethod.app.procesado.guardarEnGaleria
import com.royermethod.app.ui.components.AvisoFlotante
import com.royermethod.app.ui.components.BotonRedondo
import com.royermethod.app.ui.components.ComparadorCompleto
import com.royermethod.app.ui.components.ComparadorImagenes
import com.royermethod.app.ui.components.GhostButton
import com.royermethod.app.ui.components.Glifo
import com.royermethod.app.ui.components.LienzoLado
import com.royermethod.app.ui.components.NeonButton
import com.royermethod.app.ui.components.recordarPareja
import com.royermethod.app.ui.theme.Ink
import com.royermethod.app.ui.theme.Motion
import com.royermethod.app.ui.theme.radio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Luz tenue de arriba, del color del neon que haya elegido el usuario. */
private val LUZ_ARRIBA: Color get() = Ink.White.copy(alpha = 0.09f)

/**
 * Antes y despues, los dos en marcha y en bucle.
 *
 * Van en paralelo y no uno detras de otro porque comparar de memoria no
 * funciona: para cuando termina el primero ya no se recuerda el detalle del
 * segundo. Cada uno se puede parar por separado, por si interesa congelar un
 * momento de uno mientras el otro sigue.
 *
 * El sonido sale una sola vez, del resultado, y arranca apagado. Es el mismo
 * audio en los dos: sonando a la vez se oiria doble y desfasado.
 */
@Composable
fun ResultScreen(
    original: Uri,
    resultado: File,
    descripcion: String,
    esVideo: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val alcance = rememberCoroutineScope()

    val nuevo = remember(resultado) { Uri.fromFile(resultado) }
    var sonido by remember { mutableStateOf(false) }
    var comparando by remember { mutableStateOf(false) }
    var guardado by remember { mutableStateOf(false) }
    var guardando by remember { mutableStateOf(false) }
    var fallo by remember { mutableStateOf<String?>(null) }
    var avisoGuardado by remember { mutableStateOf(false) }

    val entrada = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        entrada.animateTo(1f, tween(Motion.SECTION, easing = Motion.EnterSoft))
    }

    fun guardar() {
        guardando = true
        fallo = null
        alcance.launch {
            val destino = withContext(Dispatchers.IO) {
                guardarEnGaleria(context, resultado, esVideo)
            }
            guardando = false
            if (destino != null) {
                guardado = true
                avisoGuardado = true
            } else {
                fallo = "No se pudo guardar. Revisa el espacio libre."
            }
        }
    }

    // Hasta Android 9 la galeria es una carpeta del almacenamiento y escribir
    // ahi es un permiso que hay que pedir en el momento. Desde Android 10 no
    // hace falta nada: MediaStore da permiso solo para el archivo que creamos.
    val permiso = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedido ->
        if (concedido) guardar()
        else fallo = "Sin permiso para escribir en la galeria."
    }

    // Las dos imagenes se cargan una vez y se reutilizan para los recuadros y
    // para la lupa: decodificar dos veces lo mismo por abrir la lupa seria
    // medio segundo de espera cada vez.
    var imagenAntes by remember { mutableStateOf<ImageBitmap?>(null) }
    var imagenDespues by remember { mutableStateOf<ImageBitmap?>(null) }
    if (!esVideo) {
        LaunchedEffect(original, resultado) {
            withContext(Dispatchers.IO) {
                imagenAntes = cargarImagen(context, original)
                imagenDespues = runCatching {
                    BitmapFactory.decodeFile(resultado.absolutePath)?.asImageBitmap()
                }.getOrNull()
            }
        }
    }

    if (comparando) {
        if (esVideo) {
            ComparadorCompleto(
                antes = original,
                despues = nuevo,
                sonido = sonido,
                onCerrar = { comparando = false }
            )
        } else {
            val a = imagenAntes
            val b = imagenDespues
            if (a != null && b != null) {
                ComparadorImagenes(antes = a, despues = b, onCerrar = { comparando = false })
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Una luz muy tenue saliendo de arriba. En negro puro la pantalla
            // quedaba plana; una sola fuente de luz basta para que tenga
            // profundidad sin dejar de ser monocroma.
            .drawBehind {
                drawRect(Ink.Black)
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(LUZ_ARRIBA, Color.Transparent),
                        center = Offset(size.width * 0.5f, -size.height * 0.15f),
                        radius = size.height * 0.85f
                    )
                )
            }
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp)
            .graphicsLayer {
                alpha = entrada.value
                translationY = (1f - entrada.value) * 40f
            }
    ) {
        Spacer(Modifier.height(26.dp))

        Text(
            text = "LISTO",
            style = MaterialTheme.typography.titleMedium,
            color = Ink.White
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = descripcion,
            style = MaterialTheme.typography.labelSmall,
            color = Ink.White50
        )

        Spacer(Modifier.height(18.dp))

        // Mientras la lupa esta abierta se sueltan estos dos.
        //
        // La lupa monta sus propios reproductores, asi que con estos vivos
        // habria cuatro decodificadores de video a la vez. Muchos moviles de
        // gama media solo permiten dos o tres y el cuarto sale en negro o
        // tumba la app. Los de detras no se ven de todos modos.
        if (esVideo && !comparando) {
            // Un solo reloj para los dos, decodificando a mano. Ver Pareja.kt
            // para por que no puede hacerse con dos reproductores normales.
            val pareja = recordarPareja(original, nuevo, sonido)

            // Todo el bloque va junto y centrado en el hueco que quede. Antes
            // las etiquetas se pegaban arriba del todo, los videos flotaban en
            // medio y los botones caian abajo, con dos huecos negros enormes
            // entre medias.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Panel(
                            titulo = "ANTIGUO",
                            proporcion = pareja.izquierdo.proporcion,
                            modifier = Modifier.weight(1f)
                        ) {
                            LienzoLado(
                                lado = pareja.izquierdo,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { pareja.izquierdo.pausado = !pareja.izquierdo.pausado }
                            )
                        }
                        Panel(
                            titulo = "NUEVO",
                            proporcion = pareja.derecho.proporcion,
                            modifier = Modifier.weight(1f)
                        ) {
                            LienzoLado(
                                lado = pareja.derecho,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { pareja.derecho.pausado = !pareja.derecho.pausado }
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BotonRedondo(
                            glifo = Glifo.Lupa,
                            onClick = { comparando = true },
                            modifier = Modifier.size(42.dp)
                        )
                        BotonRedondo(
                            glifo = if (sonido) Glifo.Sonido else Glifo.Silencio,
                            onClick = { sonido = !sonido },
                            encendido = sonido,
                            modifier = Modifier.size(42.dp)
                        )
                        Text(
                            text = "Toca uno para pararlo.\nLa lupa los superpone.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Ink.White30
                        )
                    }
                }
            }
        } else {
            val a = imagenAntes
            val b = imagenDespues
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Panel(
                            titulo = "ANTIGUO",
                            proporcion = proporcionDe(a),
                            modifier = Modifier.weight(1f)
                        ) { a?.let { Lamina(it) } }
                        Panel(
                            titulo = "NUEVO",
                            proporcion = proporcionDe(b),
                            modifier = Modifier.weight(1f)
                        ) { b?.let { Lamina(it) } }
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BotonRedondo(
                            glifo = Glifo.Lupa,
                            onClick = { comparando = true },
                            modifier = Modifier.size(42.dp)
                        )
                        Text(
                            text = "La lupa las superpone.\nDos dedos para acercar.",
                            style = MaterialTheme.typography.labelSmall,
                            color = Ink.White30
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        fallo?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = Ink.White70,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
        }

        NeonButton(
            text = when {
                guardado -> "Guardado en la galeria"
                guardando -> "Guardando..."
                else -> "Guardar"
            },
            enabled = !guardado && !guardando,
            onClick = {
                val haceFaltaPermiso = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                    ContextCompat.checkSelfPermission(
                        context, Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ) != PackageManager.PERMISSION_GRANTED
                if (haceFaltaPermiso) permiso.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                else guardar()
            },
            height = 54.dp
        )

        Spacer(Modifier.height(10.dp))

        GhostButton(text = "Hacer otro", onClick = onBack, height = 50.dp)

        Spacer(Modifier.height(14.dp))
    }

    AvisoFlotante(
        texto = if (esVideo) "Tu video se ha guardado en la galeria"
        else "Tu imagen se ha guardado en la galeria",
        visible = avisoGuardado,
        onFin = { avisoGuardado = false },
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 26.dp)
    )
    }
}

/**
 * Marco que se cine al video.
 *
 * El recuadro toma la proporcion del propio video en vez de ocupar todo el
 * alto disponible. Con un marco fijo, un video vertical dejaba una banda de
 * imagen en medio de una caja enorme y parecian dos recuadros vacios con algo
 * en el centro.
 */
private fun proporcionDe(b: ImageBitmap?): Float =
    if (b == null || b.height == 0) 1f else b.width.toFloat() / b.height

@Composable
private fun Lamina(bitmap: ImageBitmap) {
    Image(
        bitmap = bitmap,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxSize()
    )
}

/**
 * Carga la imagen original a un tamano razonable para verla.
 *
 * Sin limitar, una foto de movil son unos cincuenta megas de mapa de bits, y
 * aqui hay dos a la vez mas las de la lupa.
 */
private fun cargarImagen(context: android.content.Context, uri: android.net.Uri): ImageBitmap? {
    val medidas = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    runCatching {
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, medidas)
        }
    }
    if (medidas.outWidth <= 0) return null
    val opciones = BitmapFactory.Options().apply {
        inSampleSize = generateSequence(1) { it * 2 }
            .first { medidas.outWidth / it <= 2048 && medidas.outHeight / it <= 2048 }
    }
    return runCatching {
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opciones)?.asImageBitmap()
        }
    }.getOrNull()
}

@Composable
private fun Panel(
    titulo: String,
    proporcion: Float,
    modifier: Modifier = Modifier,
    contenido: @Composable () -> Unit
) {
    Column(modifier) {
        Text(
            text = titulo,
            style = MaterialTheme.typography.labelSmall,
            color = Ink.White50
        )
        Spacer(Modifier.height(7.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(proporcion)
                .clip(RoundedCornerShape(radio(16.dp)))
                .drawBehind {
                    drawRoundRect(
                        color = Ink.White,
                        alpha = 0.04f,
                        cornerRadius = CornerRadius(radio(16.dp).toPx())
                    )
                }
        ) {
            contenido()
        }
    }
}
