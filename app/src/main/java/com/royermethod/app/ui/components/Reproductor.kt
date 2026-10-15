package com.royermethod.app.ui.components

import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.royermethod.app.media.tiempo
import com.royermethod.app.ui.theme.Ink
import kotlinx.coroutines.delay
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Estado del reproductor, separado de la vista.
 *
 * El MediaPlayer es un recurso del sistema y no puede vivir dentro de una
 * funcion de composicion: hay que crearlo una vez, atarlo a una superficie y
 * soltarlo al salir. Aqui se le pone alrededor lo minimo para que Compose
 * pueda leerlo sin recomponer de mas.
 */
class EstadoVideo {
    val mp = MediaPlayer()
    var listo by mutableStateOf(false)
    var sonando by mutableStateOf(false)
    /**
     * Lo ha parado el usuario a proposito.
     *
     * Distinto de `sonando`, que solo dice si suena ahora mismo. Hace falta
     * separarlo porque cuando dos videos van emparejados, el que lleva el
     * bucle no puede volver a arrancar al companero que alguien acaba de
     * parar: pararlo tiene que servir de algo.
     */
    var pausado by mutableStateOf(false)
    var duracion by mutableIntStateOf(0)
    var proporcion by mutableFloatStateOf(9f / 16f)
    /** Se lee en fase de dibujo, no en composicion. */
    val posicion = mutableIntStateOf(0)

    fun alternar() {
        if (!listo) return
        runCatching {
            if (mp.isPlaying) {
                mp.pause()
                sonando = false
                pausado = true
            } else {
                mp.start()
                sonando = true
                pausado = false
            }
        }
    }

    /**
     * Un MediaPlayer preparado y parado no pinta nada: la superficie se queda
     * negra hasta que empieza a sonar. Un salto de posicion si obliga al
     * decodificador a sacar ese fotograma, asi que sirve para que se vea el
     * principio del video sin tener que darle al play.
     *
     * Hace falta llamarlo tanto al preparar como al aparecer la superficie,
     * porque el orden entre las dos cosas no esta garantizado.
     */
    fun pintarPrimerFotograma() {
        if (!listo || sonando) return
        runCatching { mp.seekTo(mp.currentPosition.coerceAtLeast(1)) }
    }

    fun buscar(fraccion: Float) {
        if (!listo || duracion <= 0) return
        val ms = (fraccion.coerceIn(0f, 1f) * duracion).toInt()
        mp.seekTo(ms)
        posicion.intValue = ms
    }
}

/**
 * Un hilo aparte, solo para cerrar reproductores.
 *
 * Soltar un MediaPlayer libera el decodificador del chip y eso tarda decenas
 * de milisegundos, a veces mas de cien. Hecho en el hilo principal, que es
 * donde caia antes, se traga varios fotogramas justo cuando se cambia de
 * pantalla: al abrir la lupa y al empezar a procesar, que es cuando el
 * usuario notaba el tiron.
 */
private val cerrador: ExecutorService = Executors.newSingleThreadExecutor { tarea ->
    Thread(tarea, "soltar-video").apply { isDaemon = true }
}

private fun soltarAparte(mp: MediaPlayer) {
    runCatching {
        mp.setOnPreparedListener(null)
        mp.setOnCompletionListener(null)
        mp.setSurface(null)
    }
    cerrador.execute { runCatching { mp.reset(); mp.release() } }
}

@Composable
fun recordarVideo(
    uri: Uri,
    arrancar: Boolean = false,
    bucle: Boolean = false,
    sonido: Boolean = true
): EstadoVideo {
    val context = LocalContext.current
    val estado = remember(uri) { EstadoVideo() }

    DisposableEffect(uri) {
        runCatching {
            estado.mp.setDataSource(context, uri)
            estado.mp.isLooping = bucle
            estado.mp.setOnPreparedListener { p ->
                estado.duracion = p.duration
                if (p.videoWidth > 0 && p.videoHeight > 0) {
                    estado.proporcion = p.videoWidth.toFloat() / p.videoHeight
                }
                estado.listo = true
                if (arrancar) {
                    p.start()
                    estado.sonando = true
                } else {
                    estado.pintarPrimerFotograma()
                }
            }
            estado.mp.setOnCompletionListener { if (!bucle) estado.sonando = false }
            estado.mp.prepareAsync()
        }
        onDispose {
            estado.sonando = false
            soltarAparte(estado.mp)
        }
    }

    // El silencio se aplica al volumen, no parando el audio: asi la pista
    // sigue avanzando y al encenderlo entra donde toca en vez de desde cero.
    LaunchedEffect(sonido) {
        runCatching { estado.mp.setVolume(if (sonido) 1f else 0f, if (sonido) 1f else 0f) }
    }

    // La posicion no la avisa nadie: hay que preguntarla. Solo mientras suena,
    // para no despertar el hilo principal con el video parado.
    LaunchedEffect(estado.sonando) {
        while (estado.sonando) {
            estado.posicion.intValue = runCatching { estado.mp.currentPosition }.getOrDefault(0)
            delay(60)
        }
    }

    return estado
}

/**
 * Los dos videos como si fueran uno.
 *
 * Son dos MediaPlayer independientes: arrancan con unos milisegundos de
 * diferencia y cada uno decodifica a su ritmo. Comparando dos versiones del
 * mismo video, medio segundo de desfase hace que se compare un momento con
 * otro y la comparacion no vale nada.
 *
 * El primer intento fue dejarlos en bucle propio y corregir el desfase
 * saltando cada pocos cientos de milisegundos. Iba peor: el salto caia justo
 * cuando uno acababa de dar la vuelta y el otro no, asi que se corregia hacia
 * el sitio equivocado, y a base de saltos el decodificador terminaba
 * atascandose.
 *
 * Aqui el bucle lo lleva la aplicacion: cuando el primero llega al final se
 * rebobinan y arrancan los dos juntos. Dentro de una vuelta apenas se
 * separan, y solo se corrige si el desfase pasa de unos cinco fotogramas,
 * porque por debajo de eso el salto se nota mas que el desfase.
 *
 * Los dos reproductores tienen que venir con bucle desactivado.
 */
@Composable
fun EnPareja(referencia: EstadoVideo, seguidor: EstadoVideo) {
    LaunchedEffect(referencia, seguidor) {
        val r = referencia
        val s = seguidor
        while (!r.listo || !s.listo) delay(30)

        // Arrancan los dos aqui, no cada uno en cuanto se prepara. Preparar
        // tarda distinto en cada archivo, asi que arrancando por separado el
        // que estuviera listo antes salia ya con ventaja.
        saltarExacto(r, 0)
        saltarExacto(s, 0)
        delay(120)
        runCatching {
            r.mp.start()
            s.mp.start()
        }
        r.sonando = true
        s.sonando = true

        // Arrancar los dos seguidos tampoco los deja clavados: cada start
        // tarda lo suyo en soltar el primer fotograma. Se deja medio segundo
        // para que los dos cojan ritmo y se cuadra una vez.
        delay(600)
        alinear(r, s)

        val vuelta = maxOf(r.duracion, s.duracion)
        var velocidad = 1f
        var pausadoAntesR = false
        var pausadoAntesS = false

        while (true) {
            delay(220)

            // Al soltar una pausa, el que estaba parado salta a donde va el
            // otro. Mientras estuvo congelado el otro siguio, y no se va a
            // rebobinar al que iba bien.
            if (pausadoAntesR && !r.pausado) alinear(s, r)
            if (pausadoAntesS && !s.pausado) alinear(r, s)
            pausadoAntesR = r.pausado
            pausadoAntesS = s.pausado

            if (r.pausado || s.pausado) {
                velocidad = ritmo(s, 1f, velocidad)
                continue
            }

            val pr = donde(r) ?: continue
            val ps = donde(s) ?: continue

            // El bucle lo hace el propio reproductor, que es lo suave. Eso
            // significa que uno puede haber dado la vuelta y el otro no, y
            // entonces la resta sale enorme aunque vayan juntos. Se mide por
            // el camino corto del circulo.
            var desfase = (ps - pr).toFloat()
            if (vuelta > 0) {
                if (desfase > vuelta / 2f) desfase -= vuelta
                if (desfase < -vuelta / 2f) desfase += vuelta
            }

            velocidad = when {
                // Muy separados: no queda otra que saltar.
                kotlin.math.abs(desfase) > 250f -> {
                    alinear(r, s)
                    ritmo(s, 1f, velocidad)
                }
                // Separados de poco: en vez de saltar, se le cambia el ritmo
                // un pelin hasta que alcanza al otro. Un salto se ve; un tres
                // por ciento de velocidad durante dos segundos no.
                kotlin.math.abs(desfase) > 45f ->
                    ritmo(s, (1f - desfase / 2000f).coerceIn(0.92f, 1.08f), velocidad)

                else -> ritmo(s, 1f, velocidad)
            }
        }
    }
}

private fun donde(e: EstadoVideo): Int? = runCatching { e.mp.currentPosition }.getOrNull()

/**
 * Salta al milisegundo pedido, no al fotograma clave mas cercano.
 *
 * Este era el fallo gordo. seekTo(int) es en realidad seekTo(msec,
 * SEEK_PREVIOUS_SYNC): retrocede hasta el fotograma clave anterior, y al
 * codificar ponemos uno cada dos segundos. Es decir, cada vez que se
 * "corregia" un desfase de una decima, el video se iba hasta dos segundos
 * atras. La correccion era lo que desincronizaba.
 */
private fun saltarExacto(e: EstadoVideo, ms: Int) {
    runCatching { e.mp.seekTo(ms.toLong(), MediaPlayer.SEEK_CLOSEST) }
}

private fun alinear(guia: EstadoVideo, salta: EstadoVideo) {
    val p = donde(guia) ?: return
    saltarExacto(salta, p)
}

/**
 * Cambia el ritmo del seguidor para que alcance al otro sin dar el salto.
 *
 * Se le toca solo al mudo. Estirar o encoger el tiempo tambien afecta al
 * audio, y aunque un tres por ciento no se oiga, no hay razon para
 * arriesgarlo cuando uno de los dos ya va en silencio por diseno.
 */
private fun ritmo(e: EstadoVideo, nueva: Float, actual: Float): Float {
    if (kotlin.math.abs(nueva - actual) < 0.005f) return actual
    return runCatching {
        if (!e.mp.isPlaying) return actual
        e.mp.playbackParams = e.mp.playbackParams.setSpeed(nueva)
        nueva
    }.getOrDefault(actual)
}

/**
 * La imagen del video.
 *
 * Va sobre TextureView y no sobre SurfaceView porque SurfaceView pinta en una
 * capa aparte del arbol: no respeta las esquinas redondeadas ni se puede
 * poner nada encima. TextureView cuesta algo mas pero se comporta como
 * cualquier otra vista.
 */
@Composable
fun LienzoVideo(
    estado: EstadoVideo,
    modifier: Modifier = Modifier,
    /**
     * true llena la caja y recorta lo que sobra; false encaja el video entero
     * dejando franjas negras. En la vista previa recorta, porque un video
     * vertical encajado en una caja apaisada se queda en una tira de dos dedos
     * y no se distingue nada. A pantalla completa encaja, porque ahi si se
     * esta mirando el resultado y recortarlo seria mentir sobre el encuadre.
     */
    recortar: Boolean = false
) {
    BoxWithConstraints(
        modifier = modifier.clipToBounds(),
        contentAlignment = Alignment.Center
    ) {
        val caja = if (maxHeight.value > 0f) maxWidth / maxHeight else 1f
        // Al recortar manda el lado que se queda corto; al encajar, el que sobra
        val porAncho = if (recortar) estado.proporcion < caja else estado.proporcion > caja

        // Se le da la medida exacta en vez de dejar que la calcule el
        // contenedor. aspectRatio la recorta contra el hueco disponible, y al
        // recortar hace falta justo lo contrario: que se salga y se corte.
        val ancho = if (porAncho) maxWidth else maxHeight * estado.proporcion
        val altura = if (porAncho) maxWidth / estado.proporcion else maxHeight

        AndroidView(
            modifier = Modifier.requiredSize(ancho, altura),
            factory = { ctx ->
                TextureView(ctx).apply {
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                            runCatching {
                                estado.mp.setSurface(Surface(st))
                                estado.pintarPrimerFotograma()
                            }
                        }

                        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) = Unit

                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                            runCatching { estado.mp.setSurface(null) }
                            return true
                        }

                        override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                    }
                }
            }
        )
    }
}

/**
 * Ver el archivo a pantalla completa.
 *
 * Dentro de la seccion la vista previa mide poco mas de un centimetro de
 * ancho en un video vertical, y toda la app va de decidir si algo se ve bien
 * o no. Esa decision no se puede tomar en una miniatura, asi que hay una
 * pantalla entera para mirarlo.
 *
 * Es un reproductor aparte del de la seccion, no el mismo agrandado: dos
 * vistas no pueden compartir la superficie de un MediaPlayer, se pelearian
 * por ella y una de las dos se quedaria en negro.
 */
@Composable
fun VisorCompleto(
    uri: Uri,
    esVideo: Boolean,
    imagen: androidx.compose.ui.graphics.ImageBitmap?,
    onCerrar: () -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onCerrar,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        VentanaCompleta()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Ink.Black)
        ) {
            if (esVideo) {
                val estado = recordarVideo(uri, arrancar = true)
                LienzoVideo(
                    estado = estado,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 40.dp, bottom = 80.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { estado.alternar() }
                )
                // Ahora que la ventana llega al borde fisico de la pantalla,
                // los controles necesitan su propio margen o quedan pegados
                // al canto y a la barra de gestos.
                Transporte(
                    estado = estado,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 34.dp)
                )
            } else if (imagen != null) {
                androidx.compose.foundation.Image(
                    bitmap = imagen,
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }

            BotonRedondo(
                glifo = Simbolo.Cerrar,
                onClick = onCerrar,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 44.dp, end = 18.dp)
                    .size(40.dp)
            )
        }
    }
}

enum class Simbolo { Cerrar, Lupa, Sonido, Silencio, Girar }

/**
 * Hace que una ventana emergente ocupe la pantalla de verdad.
 *
 * Un Dialog de Compose se queda por debajo de la barra de estado aunque se le
 * quite el ancho por defecto, y por arriba se ve asomar la pantalla de
 * detras. Hay que ir a la ventana de Android que lo sostiene y decirle que
 * dibuje tambien bajo las barras del sistema.
 */
@Composable
fun VentanaCompleta() {
    val vista = androidx.compose.ui.platform.LocalView.current
    LaunchedEffect(vista) {
        val ventana = (vista.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
            ?: return@LaunchedEffect
        ventana.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT
        )
        // Esto es lo que de verdad hace falta. Decirle que no se ajuste a las
        // barras del sistema no basta: la ventana sigue midiendo lo que le
        // deja el hueco y por arriba se ve asomar la pantalla de detras. Sin
        // limites, ocupa la pantalla fisica entera.
        ventana.addFlags(android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(ventana, false)
        ventana.statusBarColor = android.graphics.Color.TRANSPARENT
        ventana.navigationBarColor = android.graphics.Color.TRANSPARENT
    }
}

/**
 * Boton circular con el simbolo dibujado a mano.
 *
 * Los simbolos van en codigo y no como recursos porque son cuatro trazos y
 * asi comparten grosor y proporcion exactos con el resto de la app, que es
 * todo geometria dibujada.
 */
@Composable
fun BotonRedondo(
    glifo: Simbolo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    encendido: Boolean = true
) {
    Box(
        modifier = modifier
            .size(38.dp)
            .clip(CircleShape)
            .drawBehind {
                drawCircle(Ink.Black, alpha = 0.5f)
                drawCircle(Ink.White, alpha = 0.10f)
                drawCircle(
                    Ink.White, alpha = 0.22f,
                    radius = size.width / 2 - 0.5.dp.toPx(),
                    style = Stroke(1.dp.toPx())
                )
                val c = size.width / 2
                val u = size.width * 0.22f
                val tinta = if (encendido) Ink.White else Ink.White50
                // En dp y no en pixeles crudos: puesto en pixeles, el mismo
                // numero sale de un grosor en una pantalla y de otro muy
                // distinto en la siguiente.
                val grosor = 1.6.dp.toPx()

                when (glifo) {
                    // Aspa fina y ancha, no la cruz gorda de antes. Las puntas
                    // llegan mas lejos y el trazo es mas delgado: se lee mejor
                    // y no compite con el resto de la interfaz, que es toda de
                    // linea fina.
                    Simbolo.Cerrar -> {
                        val r = size.width * 0.26f
                        raya(tinta, c - r, c - r, c + r, c + r, 1.4.dp.toPx())
                        raya(tinta, c + r, c - r, c - r, c + r, 1.4.dp.toPx())
                    }

                    Simbolo.Lupa -> {
                        drawCircle(
                            tinta, radius = u * 0.78f,
                            center = Offset(c - u * 0.14f, c - u * 0.14f),
                            style = Stroke(grosor)
                        )
                        raya(
                            tinta,
                            c + u * 0.42f, c + u * 0.42f,
                            c + u * 0.95f, c + u * 0.95f, grosor
                        )
                    }

                    // Rectangulo de pie con una flecha curva: cambiar la forma
                    // del marco entre tumbado y de pie.
                    Simbolo.Girar -> {
                        drawRoundRect(
                            color = tinta,
                            topLeft = Offset(c - u * 0.48f, c - u * 0.8f),
                            size = Size(u * 0.96f, u * 1.6f),
                            cornerRadius = CornerRadius(u * 0.2f),
                            style = Stroke(grosor)
                        )
                        drawArc(
                            color = tinta,
                            startAngle = 130f,
                            sweepAngle = 160f,
                            useCenter = false,
                            topLeft = Offset(c - u * 1.1f, c - u * 1.1f),
                            size = Size(u * 2.2f, u * 2.2f),
                            style = Stroke(grosor, cap = StrokeCap.Round)
                        )
                    }

                    Simbolo.Sonido, Simbolo.Silencio -> {
                        // altavoz: cuerpo cuadrado mas la boca triangular
                        val p = Path().apply {
                            moveTo(c - u * 0.85f, c - u * 0.32f)
                            lineTo(c - u * 0.35f, c - u * 0.32f)
                            lineTo(c + u * 0.15f, c - u * 0.9f)
                            lineTo(c + u * 0.15f, c + u * 0.9f)
                            lineTo(c - u * 0.35f, c + u * 0.32f)
                            lineTo(c - u * 0.85f, c + u * 0.32f)
                            close()
                        }
                        drawPath(p, tinta)
                        if (glifo == Simbolo.Sonido) {
                            for (r in listOf(0.45f, 0.8f)) {
                                drawArc(
                                    color = tinta,
                                    startAngle = -55f,
                                    sweepAngle = 110f,
                                    useCenter = false,
                                    topLeft = Offset(c + u * 0.15f - u * r, c - u * r),
                                    size = Size(u * r * 2, u * r * 2),
                                    style = Stroke(grosor * 0.85f, cap = StrokeCap.Round)
                                )
                            }
                        } else {
                            raya(
                                tinta,
                                c + u * 0.45f, c - u * 0.45f,
                                c + u * 1.05f, c + u * 0.45f, grosor
                            )
                            raya(
                                tinta,
                                c + u * 1.05f, c - u * 0.45f,
                                c + u * 0.45f, c + u * 0.45f, grosor
                            )
                        }
                    }
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    )
}

private fun DrawScope.raya(
    color: androidx.compose.ui.graphics.Color,
    x0: Float, y0: Float, x1: Float, y1: Float, grosor: Float
) = drawLine(
    color,
    start = Offset(x0, y0),
    end = Offset(x1, y1),
    strokeWidth = grosor,
    cap = StrokeCap.Round
)

/**
 * Play/pausa, barra para adelantar y el tiempo.
 *
 * Va encima de la imagen, asi que lleva un degradado debajo: sobre un
 * fotograma claro, los controles blancos desaparecerian.
 */
@Composable
fun Transporte(
    estado: EstadoVideo,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .drawBehind {
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Ink.Black.copy(alpha = 0f),
                        1f to Ink.Black.copy(alpha = 0.72f)
                    )
                )
            }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BotonPlay(sonando = estado.sonando, onClick = { estado.alternar() })

        Spacer(Modifier.width(12.dp))

        BarraTiempo(
            posicion = estado.posicion,
            duracion = estado.duracion,
            onBuscar = { estado.buscar(it) },
            modifier = Modifier.weight(1f)
        )

        Spacer(Modifier.width(12.dp))

        // Solo se redibuja al cambiar el segundo, no en cada muestreo
        val segundos by remember(estado) {
            derivedStateOf { estado.posicion.intValue / 1000 }
        }
        Text(
            text = "${tiempo(segundos * 1000)} / ${tiempo(estado.duracion)}",
            style = MaterialTheme.typography.labelSmall,
            color = Ink.White70
        )
    }
}

@Composable
private fun BotonPlay(sonando: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(Ink.White)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .drawBehind {
                val c = size.width / 2
                if (sonando) {
                    val an = size.width * 0.09f
                    val al = size.height * 0.30f
                    drawRoundRect(
                        color = Ink.Black,
                        topLeft = Offset(c - an * 2.1f, c - al / 2),
                        size = Size(an, al),
                        cornerRadius = CornerRadius(an / 2)
                    )
                    drawRoundRect(
                        color = Ink.Black,
                        topLeft = Offset(c + an * 1.1f, c - al / 2),
                        size = Size(an, al),
                        cornerRadius = CornerRadius(an / 2)
                    )
                } else {
                    val r = size.width * 0.17f
                    val p = Path().apply {
                        moveTo(c - r * 0.75f, c - r)
                        lineTo(c + r, c)
                        lineTo(c - r * 0.75f, c + r)
                        close()
                    }
                    drawPath(p, Ink.Black)
                }
            }
    )
}

/**
 * Barra de posicion. Se puede tocar en cualquier punto o arrastrar.
 *
 * La posicion llega como State y se lee dentro de la lambda de dibujo: si se
 * leyera en composicion, la barra recompondria su rama entera dieciseis veces
 * por segundo mientras el video suena.
 */
@Composable
private fun BarraTiempo(
    posicion: State<Int>,
    duracion: Int,
    onBuscar: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(30.dp)
            .pointerInput(duracion) {
                detectTapGestures { o -> onBuscar(o.x / size.width) }
            }
            .pointerInput(duracion) {
                detectHorizontalDragGestures { cambio, _ ->
                    onBuscar(cambio.position.x / size.width)
                }
            }
            .drawBehind {
                val y = size.height / 2
                val gr = 2.5f
                drawLine(
                    color = Ink.White,
                    alpha = 0.18f,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = gr
                )
                val f = if (duracion > 0) {
                    (posicion.value.toFloat() / duracion).coerceIn(0f, 1f)
                } else 0f
                if (f > 0f) {
                    drawLine(
                        color = Ink.White,
                        start = Offset(0f, y),
                        end = Offset(size.width * f, y),
                        strokeWidth = gr
                    )
                }
                drawCircle(Ink.White, radius = 5f, center = Offset(size.width * f, y))
            }
    )
}
