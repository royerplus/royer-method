package com.royermethod.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.royermethod.app.editor.exportar.Exportacion
import com.royermethod.app.media.Medio
import com.royermethod.app.media.leerMedio
import com.royermethod.app.procesado.ESPERA_EXPORTACION
import com.royermethod.app.procesado.ProcesadoService
import com.royermethod.app.procesado.Procesador
import com.royermethod.app.procesado.Trabajo
import com.royermethod.app.procesado.Tramo
import com.royermethod.app.procesado.bppDe
import com.royermethod.app.procesado.ladoDe
import com.royermethod.app.ui.components.Simbolo
import com.royermethod.app.ui.components.AvisoDialog
import com.royermethod.app.ui.components.AvisoFlotante
import com.royermethod.app.ui.components.BotonRedondo
import com.royermethod.app.ui.components.ColaDialog
import com.royermethod.app.ui.components.ErrorDialog
import com.royermethod.app.ui.components.LienzoVideo
import com.royermethod.app.ui.components.NeonButton
import com.royermethod.app.ui.components.OptionSelector
import com.royermethod.app.ui.components.SectionLabel
import com.royermethod.app.ui.components.SegmentedToggle
import com.royermethod.app.ui.components.SubirDialog
import com.royermethod.app.ui.components.ToggleChip
import com.royermethod.app.ui.components.TramosDialog
import com.royermethod.app.ui.components.Transporte
import com.royermethod.app.ui.components.VisorCompleto
import com.royermethod.app.ui.components.recordarVideo
import com.royermethod.app.ui.components.rememberBreath
import com.royermethod.app.ui.logo.RoyerLogo
import com.royermethod.app.ui.theme.Ink
import com.royermethod.app.ui.theme.Motion
import com.royermethod.app.ui.theme.radio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------------------
// Modelo
// ---------------------------------------------------------------------------

/** Lo que ofrece cada plan. Se ve en modo automatico. */
data class Plan(val nombre: String, val donde: String, val detalle: String)

/**
 * Una etapa del proceso que se puede apagar por separado.
 *
 * Solo aparecen aqui las que tiene sentido desactivar. Reducir resolucion y
 * comprimir no estan porque son el motivo de existir de la app: apagarlas
 * seria devolver el archivo tal cual.
 */
data class Etapa(
    val clave: String,
    val nombre: String,
    val porDefecto: Boolean = true,
    /** Cuesta mucho tiempo de proceso. Se avisa. */
    val pesada: Boolean = false,
    /**
     * false cuando la etapa todavia no existe.
     *
     * Sale igual en la lista, apagada y sin poder encenderse, en vez de
     * esconderla o dejar que se encienda sin hacer nada. Una casilla que se
     * marca y no cambia el resultado es peor que una que no esta.
     */
    val disponible: Boolean = true,
    /** Lleva lapiz dentro del boton porque tiene algo que ajustar. */
    val conAjustes: Boolean = false
)

/** Grupo de opciones excluyentes. */
data class Grupo(
    val etiqueta: String,
    val opciones: List<String>,
    val porDefecto: Int,
    /**
     * Etapas que hacen aparecer el grupo. Vacio = siempre visible.
     *
     * Basta con que una este encendida. Aparecer y desaparecer en vez de salir
     * apagado: una opcion visible pero bloqueada obliga a adivinar que la
     * desbloquea; si solo sale cuando aplica, no hay nada que adivinar.
     *
     * La resolucion es el caso de dos: la tocan tanto comprimir (para bajarla)
     * como mejorar (que ademas puede subirla). Si no esta ninguna de las dos,
     * nadie la va a cambiar y no pinta nada en pantalla.
     */
    val dependeDe: List<String> = emptyList()
)

data class ConfigMejora(
    val titulo: String,
    /** Decide que pide el selector del sistema y como se lee el archivo. */
    val esVideo: Boolean,
    val etiquetaZona: String,
    val zonaVacia: String,
    val promesa: String,
    val gratis: Plan,
    val pro: Plan,
    /** Lo que se ve en personalizada. */
    val grupos: List<Grupo>,
    val etapas: List<Etapa>,
    val aviso: String
)

// Indices de etapa que se referencian desde la logica de dependencias
private const val E_FRAMES = 0
private const val E_ESTABILIZAR = 1

val CONFIG_VIDEO = ConfigMejora(
    titulo = "Mejorar video",
    esVideo = true,
    etiquetaZona = "Tu video",
    zonaVacia = "Seleccionar video",
    promesa = "Aumentamos la fluidez y la calidad, y comprimimos el video " +
        "para conseguir el mejor resultado posible.",
    gratis = Plan("Modo gratis", "En tu telefono", "240 fps  ·  30 fps  ·  576p"),
    pro = Plan("Modo pro", "Servidor", "960 fps  ·  30 fps  ·  576p"),
    grupos = listOf(
        // 576 por defecto: en las pruebas contra la referencia fue el punto
        // donde el detalle se mantiene y el peso se desploma. Original no
        // toca la resolucion; sigue reduciendo peso por compresion.
        // Original y 1440p solo salen con Mejorar encendido: son las unicas
        // que pueden quedar por encima de lo que trae el archivo, y subir sin
        // reconstruir es estirar pixeles.
        Grupo(
            "Resolucion", listOf("576p", "720p", "1080p", "1440p", "Original"), 0,
            dependeDe = listOf("peso", "imagen")
        ),
        // Hasta 960: mas alla el modelo empieza a inventar mas de lo que suma
        Grupo("Frames a generar", listOf("120", "240", "480", "960"), 1, dependeDe = listOf("frames")),
        // Bajar los fps es reducir, no generar: por eso vive en Comprimir y no
        // en Generar frames. Con Comprimir apagado el video sale a los
        // fotogramas que se hayan generado, sin recortar nada.
        Grupo("Dejarlo en", listOf("24 fps", "30 fps", "60 fps"), 1, dependeDe = listOf("peso")),
        Grupo("Compresion", listOf("Ligera", "Alta", "Maxima"), 1, dependeDe = listOf("peso")),
        // La misma limpieza que en imagen: es el mismo modelo.
        Grupo("Limpieza", listOf("Nada", "Suave", "Media", "Fuerte"), 1, dependeDe = listOf("imagen"))
    ),
    etapas = listOf(
        Etapa("frames", "Generar frames", porDefecto = false, pesada = true),
        Etapa("estabilizar", "Estabilizar", porDefecto = false),
        // Antes era "Realzar", que era afilar: subrayar lo que ya estaba, sin
        // devolver nada que no estuviera. Ahora es el mismo modelo que en
        // imagen, reconstruyendo fotograma a fotograma. Cuesta mucho mas.
        Etapa("imagen", "Mejorar", porDefecto = false, pesada = true),
        Etapa("peso", "Comprimir"),
        // Necesita fotogramas generados: sin ellos, ralentizar solo repetiria
        // los que hay y quedaria a tirones, que es justo lo contrario.
        Etapa("lento", "Camara lenta", porDefecto = false, conAjustes = true)
    ),
    aviso = "Subir la resolucion o los fps no mejora el resultado, lo empeora. " +
        "TikTok reparte un presupuesto de bits parecido sin importar el tamano, " +
        "asi que cuantos mas pixeles le des, menos le toca a cada uno."
)

val CONFIG_IMAGEN = ConfigMejora(
    titulo = "Mejorar imagen",
    esVideo = false,
    etiquetaZona = "Tu imagen",
    zonaVacia = "Seleccionar imagen",
    promesa = "Reconstruimos el detalle y la linea, y comprimimos la imagen " +
        "sin que se note, para conseguir el mejor resultado posible.",
    gratis = Plan("Modo gratis", "En tu telefono", "Limpieza  ·  realce  ·  1080p"),
    pro = Plan("Modo pro", "Servidor", "Reconstruccion con IA  ·  hasta 2048"),
    grupos = listOf(
        // Original por defecto: en una foto, reducir no aporta nada. Lo que
        // sube la calidad es reconstruir, y el peso ya lo baja la compresion.
        Grupo(
            "Resolucion", listOf("1080", "1440", "2048", "Original"), 3,
            dependeDe = listOf("peso", "imagen")
        ),
        Grupo("Formato", listOf("JPG", "WEBP", "PNG", "Original"), 3),
        // Cuanto limpiar al reconstruir. Mas no siempre es mejor: sobre una
        // imagen ya limpia, un nivel alto se lleva la textura fina con el
        // ruido.
        Grupo("Limpieza", listOf("Nada", "Suave", "Media", "Fuerte"), 1, dependeDe = listOf("imagen")),
        Grupo("Compresion", listOf("Ligera", "Alta", "Maxima"), 1, dependeDe = listOf("peso"))
    ),
    etapas = listOf(
        Etapa("imagen", "Mejorar", pesada = true),
        Etapa("peso", "Comprimir")
    ),
    aviso = "Forzar PNG o resoluciones altas dispara el peso sin ganar nada. " +
        "Las redes recomprimen lo que pesa de mas y suele acabar peor que el original."
)

// ---------------------------------------------------------------------------
// Pantalla
// ---------------------------------------------------------------------------

private const val AUTO = 0
private const val PERSONAL = 1

/**
 * Vista plana: todo cabe de una, sin arrastrar.
 *
 * El orden del proceso es fijo y no se puede reordenar desde la interfaz,
 * porque el orden importa y ya esta medido:
 *
 *   generar frames -> estabilizar -> limpiar y realzar -> reducir -> comprimir
 *
 * Comprimir va siempre al final: comprimir antes obliga a las etapas que
 * vengan detras a trabajar sobre material ya danado. Y el realce va despues
 * de estabilizar pero antes de reducir, porque afilar un desenfoque no sirve
 * de nada y reducir siempre quita definicion al filo.
 */
@Composable
fun MejoraScreen(
    config: ConfigMejora,
    onProcesar: (Trabajo) -> Unit,
    onVolver: () -> Unit,
    /** La cola ya esta lanzada: hay que ir a mirar como va. */
    onCola: () -> Unit
) {
    var pro by remember { mutableStateOf(false) }
    var modo by remember { mutableIntStateOf(AUTO) }
    var mostrarAviso by remember { mutableStateOf(false) }
    var avisoSilenciado by remember { mutableStateOf(false) }
    // El editor esta exportando: no se puede empezar a la vez
    var ocupado by remember { mutableStateOf(false) }

    var medio by remember { mutableStateOf<Medio?>(null) }
    var leyendo by remember { mutableStateOf(false) }
    var visor by remember { mutableStateOf(false) }
    var editandoTramos by remember { mutableStateOf(false) }
    var mostrarCola by remember { mutableStateOf(false) }
    var encolado by remember { mutableStateOf(false) }
    var tramos by remember(medio) { mutableStateOf<List<Tramo>>(emptyList()) }

    val context = LocalContext.current
    val alcance = rememberCoroutineScope()

    // El selector de fotos del sistema no pide permisos: devuelve un permiso
    // temporal sobre ese archivo y nada mas. Pedir acceso a toda la galeria
    // para leer un archivo que el usuario ya nos esta senalando sobra.
    val selector = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            leyendo = true
            alcance.launch {
                // Decodificar y sacar metadatos toca disco: fuera del hilo
                // principal o la pantalla se queda tiesa al elegir.
                val leido = withContext(Dispatchers.IO) {
                    leerMedio(context, uri, config.esVideo)
                }
                medio = leido
                leyendo = false
            }
        }
    }

    val abrirSelector = {
        selector.launch(
            PickVisualMediaRequest(
                if (config.esVideo) ActivityResultContracts.PickVisualMedia.VideoOnly
                else ActivityResultContracts.PickVisualMedia.ImageOnly
            )
        )
    }

    val activas = remember {
        mutableStateListOf<Boolean>().apply { config.etapas.forEach { add(it.porDefecto) } }
    }
    val elegidas = remember {
        mutableStateListOf<Int>().apply { config.grupos.forEach { add(it.porDefecto) } }
    }
    val entrada = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        entrada.animateTo(1f, tween(Motion.SECTION, easing = Motion.EnterSoft))
    }

    medio?.let { m ->
        if (visor) {
            VisorCompleto(
                uri = m.uri,
                esVideo = m.esVideo,
                imagen = m.miniatura,
                onCerrar = { visor = false }
            )
        }
    }

    if (editandoTramos && medio != null) {
        // El factor no puede pasar de los fotogramas que se generan por cada
        // uno de salida: la camara lenta sale de repartirlos, no de inventar.
        val generados = config.grupos
            .indexOfFirst { it.etiqueta == "Frames a generar" }
            .let { if (it >= 0) config.grupos[it].opciones[elegidas[it]].toIntOrNull() ?: 0 else 0 }
        val fpsFinal = config.grupos
            .indexOfFirst { it.etiqueta == "Dejarlo en" }
            .let {
                if (it >= 0) config.grupos[it].opciones[elegidas[it]]
                    .filter { c -> c.isDigit() }.toIntOrNull() ?: 30 else 30
            }
        TramosDialog(
            duracionMs = medio!!.duracionMs,
            tramos = tramos,
            factorMaximo = if (fpsFinal > 0) (generados / fpsFinal).coerceAtLeast(1) else 1,
            onGuardar = { tramos = it },
            onCerrar = { editandoTramos = false }
        )
    }

    if (ocupado) ErrorDialog(mensaje = ESPERA_EXPORTACION, onCerrar = { ocupado = false })

    if (mostrarCola) {
        ColaDialog(
            onIniciar = {
                if (Exportacion.corriendo) {
                    mostrarCola = false
                    ocupado = true
                } else {
                    Procesador.arrancarCola()
                    ProcesadoService.arrancar(context, enLote = true)
                    onCola()
                }
            },
            onCerrar = { mostrarCola = false }
        )
    }

    if (mostrarAviso) {
        AvisoDialog(
            texto = config.aviso,
            onCerrar = { noRepetir ->
                mostrarAviso = false
                if (noRepetir) avisoSilenciado = true
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink.Black)
            .padding(horizontal = 22.dp)
            .graphicsLayer {
                alpha = entrada.value
                translationY = (1f - entrada.value) * 40f
            }
    ) {
        Spacer(Modifier.height(22.dp))

        Barra(
            titulo = config.titulo,
            onVolver = onVolver,
            onConfig = { mostrarCola = true }
        )

        Spacer(Modifier.height(18.dp))

        SegmentedToggle(
            izquierda = "Gratis",
            derecha = "Pro",
            derechaActiva = pro,
            onChange = { pro = it },
            height = 44.dp
        )

        Spacer(Modifier.height(16.dp))

        SectionLabel(config.etiquetaZona, derecha = medio?.nombre)

        Spacer(Modifier.height(8.dp))

        ZonaMedio(
            vacia = config.zonaVacia,
            medio = medio,
            leyendo = leyendo,
            // Configurando, la vista previa se encoge. Es cuando menos falta
            // hace verla grande y cuando mas sitio hacen falta abajo, sobre
            // todo si se encienden las opciones de generar fotogramas.
            compacto = modo != AUTO,
            // Con el visor abierto se suelta el reproductor de aqui: son dos
            // decodificadores de video sobre el mismo archivo y en muchos
            // moviles el segundo se queda en negro o tumba la app.
            enPausa = visor,
            onElegir = abrirSelector,
            onVer = { visor = true }
        )

        Spacer(Modifier.height(16.dp))

        SegmentedToggle(
            izquierda = "Automatica",
            derecha = "Personalizada",
            derechaActiva = modo != AUTO,
            onChange = { aPersonal ->
                if (aPersonal) {
                    if (!avisoSilenciado) mostrarAviso = true
                    modo = PERSONAL
                } else {
                    modo = AUTO
                }
            },
            height = 44.dp
        )

        Spacer(Modifier.height(10.dp))

        Box(Modifier.weight(1f)) {
            AnimatedContent(
                targetState = modo,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(
                        animationSpec = tween(Motion.SECTION, easing = Motion.Smooth),
                        initialOffsetX = { it / 4 * dir }
                    ) + fadeIn(tween(Motion.SECTION, easing = Motion.Smooth)))
                        .togetherWith(
                            slideOutHorizontally(
                                animationSpec = tween(Motion.SECTION, easing = Motion.Smooth),
                                targetOffsetX = { -it / 4 * dir }
                            ) + fadeOut(tween(Motion.NORMAL, easing = Motion.ExitSoft))
                        )
                },
                label = "config"
            ) { m ->
                when (m) {
                    AUTO -> PanelAutomatica(
                        promesa = config.promesa,
                        plan = if (pro) config.pro else config.gratis,
                        // En imagen, el formato de salida en automatico es el
                        // mismo del archivo. Decirlo aqui evita tener que
                        // entrar en personalizar solo para comprobarlo.
                        extra = if (!config.esVideo && medio != null && medio!!.formato.isNotEmpty()) {
                            "Formato: ${medio!!.formato}"
                        } else null
                    )
                    else -> PanelPersonalizada(
                        config, activas, elegidas,
                        lado = medio?.let { minOf(it.ancho, it.alto) } ?: 0,
                        onAjustes = { editandoTramos = true }
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        NeonButton(
            // Con la cola encendida el boton no arranca nada: apila. Decirlo
            // en el propio boton evita la sorpresa de pulsarlo y que no pase
            // nada aparente.
            text = if (Procesador.enLote) "Anadir a la cola" else "Procesar",
            onClick = {
                medio?.let { m ->
                    val t = armarTrabajo(config, m, modo == AUTO, elegidas, activas, tramos)
                    if (Procesador.enLote) {
                        Procesador.encolar(t)
                        encolado = true
                        medio = null
                    } else {
                        onProcesar(t)
                    }
                }
            },
            enabled = medio != null,
            height = 54.dp
        )

        Spacer(Modifier.height(14.dp))
    }

    AvisoFlotante(
        texto = "Anadido a la cola  ·  ${Procesador.cola.size} en espera",
        visible = encolado,
        onFin = { encolado = false },
        modifier = Modifier.padding(top = 22.dp)
    )
}

/** Cabecera: flecha de vuelta, titulo y el logo pequeno a la derecha. */
@Composable
private fun Barra(titulo: String, onVolver: () -> Unit, onConfig: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val escala by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = tween(if (pressed) Motion.TAP else Motion.NORMAL, easing = Motion.Smooth),
        label = "backScale"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .graphicsLayer {
                    scaleX = escala
                    scaleY = escala
                }
                .clip(CircleShape)
                .drawBehind {
                    drawCircle(Ink.White, alpha = 0.05f)
                    drawCircle(Ink.White, alpha = 0.14f, style = Stroke(width = 1.dp.toPx()))
                }
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onVolver
                ),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(13.dp)) {
                val g = 1.6.dp.toPx()
                drawLine(
                    Ink.White, alpha = 0.9f,
                    start = Offset(size.width * 0.62f, 0f),
                    end = Offset(size.width * 0.22f, size.height / 2),
                    strokeWidth = g, cap = StrokeCap.Round
                )
                drawLine(
                    Ink.White, alpha = 0.9f,
                    start = Offset(size.width * 0.22f, size.height / 2),
                    end = Offset(size.width * 0.62f, size.height),
                    strokeWidth = g, cap = StrokeCap.Round
                )
            }
        }

        Spacer(Modifier.width(14.dp))

        Text(
            text = titulo.uppercase(),
            style = MaterialTheme.typography.titleMedium,
            color = Ink.White
        )

        Spacer(Modifier.weight(1f))

        // Rueda dentada: aqui vive lo de procesar varios seguidos, que no es
        // un ajuste del archivo sino de como trabaja la app.
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onConfig
                )
                .drawBehind {
                    val c = center
                    val r = size.minDimension * 0.24f
                    drawCircle(Ink.White, alpha = 0.7f, radius = r, center = c, style = Stroke(1.5.dp.toPx()))
                    drawCircle(Ink.White, alpha = 0.7f, radius = r * 0.34f, center = c)
                    for (n in 0 until 6) {
                        val a = Math.toRadians(n * 60.0)
                        val dx = (kotlin.math.cos(a) * r).toFloat()
                        val dy = (kotlin.math.sin(a) * r).toFloat()
                        drawLine(
                            Ink.White, alpha = 0.7f,
                            start = Offset(c.x + dx, c.y + dy),
                            end = Offset(c.x + dx * 1.45f, c.y + dy * 1.45f),
                            strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round
                        )
                    }
                }
        )
    }
}

/**
 * Zona de seleccion del archivo. Es el elemento mas grande de la pantalla.
 *
 * Vacia funciona como boton y abre el selector del sistema. Con algo dentro
 * ensena el archivo de verdad, no un icono: en un video se puede darle al
 * play y adelantarlo desde aqui mismo, y el boton de ampliar lo abre a
 * pantalla completa, que es donde de verdad se puede juzgar si se ve bien.
 */
@Composable
private fun ZonaMedio(
    vacia: String,
    medio: Medio?,
    leyendo: Boolean,
    enPausa: Boolean,
    compacto: Boolean,
    onElegir: () -> Unit,
    onVer: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val breath = rememberBreath(0.45f, 0.85f)
    var sonido by remember(medio) { mutableStateOf(false) }

    // El marco arranca con la forma del archivo, que es lo que acierta casi
    // siempre, y el boton de girar permite cambiarlo. Un video vertical en un
    // marco tumbado solo ensena la franja del centro.
    var dePie by remember(medio) {
        mutableStateOf(medio != null && medio.alto > medio.ancho)
    }

    val escalaA = animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = tween(if (pressed) Motion.TAP else Motion.NORMAL, easing = Motion.Smooth),
        label = "zonaEscala"
    )

    val cargado = medio != null

    // El marco no cambia al meter un archivo: es el mismo rectangulo, y lo
    // que va dentro se amplia hasta llenarlo. Probe a que el marco tomara la
    // forma del video, y con uno vertical quedaba una tira estrecha en medio
    // de la pantalla con hueco a los lados; el marco fijo es lo que hace que
    // la seccion no baile al cargar el archivo.
    val estado = if (medio != null && medio.esVideo && !enPausa) {
        recordarVideo(medio.uri, arrancar = true, bucle = true, sonido = sonido)
    } else null

    // Misma altura de pie que tumbado: lo unico que cambia es el ancho. Asi la
    // seccion mide siempre igual y nada de lo de abajo se mueve al girar.
    // El marco mide siempre igual. Se probo a encogerlo al pasar a
    // personalizar para hacer sitio a las opciones, y quedaba mal: la seccion
    // entera pegaba un salto justo al tocar el selector.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(196.dp),
        contentAlignment = Alignment.Center
    ) {
    Box(
        modifier = Modifier
            .then(
                if (dePie) Modifier.fillMaxHeight().aspectRatio(9f / 16f)
                else Modifier.fillMaxSize()
            )
            .graphicsLayer {
                val s = escalaA.value
                scaleX = s
                scaleY = s
            }
            .clip(RoundedCornerShape(radio(22.dp)))
            .drawBehind {
                drawRoundRect(
                    color = Ink.White,
                    alpha = 0.025f,
                    cornerRadius = CornerRadius(radio(22.dp).toPx())
                )
                drawRoundRect(
                    color = Ink.White,
                    alpha = if (cargado) 0.28f else breath.value * 0.38f,
                    cornerRadius = CornerRadius(radio(22.dp).toPx()),
                    size = Size(size.width, size.height),
                    style = Stroke(
                        width = 1.4f,
                        pathEffect = if (cargado) null
                        else PathEffect.dashPathEffect(floatArrayOf(14f, 12f), 0f)
                    )
                )
            }
            // Vacio, todo el marco abre el selector. Con algo dentro no: ahi
            // un toque suelto es para parar el video, no para cambiarlo.
            .then(
                if (cargado) Modifier
                else Modifier.clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onElegir
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        when {
            medio == null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                RoyerLogo(
                    size = 68.dp,
                    glow = 0.9f,
                    modifier = Modifier.graphicsLayer { alpha = breath.value }
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = (if (leyendo) "Leyendo..." else vacia).uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = Ink.White50
                )
            }

            estado != null -> LienzoVideo(
                estado = estado,
                recortar = true,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { estado.alternar() }
            )

            else -> medio.miniatura?.let { bmp ->
                Image(
                    bitmap = bmp,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Solo estos dos dentro del marco, uno en cada esquina de arriba. De
        // pie el marco mide poco mas de un dedo de ancho y no cabe nada mas.
        if (cargado) {
            if (estado != null) {
                BotonRedondo(
                    glifo = if (sonido) Simbolo.Sonido else Simbolo.Silencio,
                    onClick = { sonido = !sonido },
                    encendido = sonido,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .size(30.dp)
                )
            }
            BotonRedondo(
                glifo = Simbolo.Lupa,
                onClick = onVer,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(30.dp)
            )
        }
    }
    }

    if (medio == null) return

    // La barra de tiempo va debajo y a todo lo ancho, no dentro del marco: en
    // vertical el marco es demasiado estrecho para que quepa.
    if (estado != null) {
        Transporte(estado = estado, modifier = Modifier.padding(top = 6.dp))
    }

    Spacer(Modifier.height(4.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = medio.ficha(),
            style = MaterialTheme.typography.labelSmall,
            color = Ink.White30,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(6.dp))
        AccionCaja(texto = "Orientacion", onClick = { dePie = !dePie })
        Spacer(Modifier.width(6.dp))
        AccionCaja(texto = "Cambiar", onClick = onElegir)
    }
}

/** Boton chico sobre la vista previa. */
@Composable
private fun AccionCaja(texto: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(radio(9.dp)))
            .drawBehind {
                drawRoundRect(
                    color = Ink.Black,
                    alpha = 0.55f,
                    cornerRadius = CornerRadius(radio(9.dp).toPx())
                )
                drawRoundRect(
                    color = Ink.White,
                    alpha = 0.28f,
                    cornerRadius = CornerRadius(radio(9.dp).toPx()),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = texto.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = Ink.White70
        )
    }
}

/** Modo automatico: la promesa y nada mas. */
@Composable
private fun PanelAutomatica(promesa: String, plan: Plan, extra: String? = null) {
    Tarjeta(estirar = false) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = plan.nombre.uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = Ink.White
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = plan.donde.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = Ink.White50
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = promesa,
            style = MaterialTheme.typography.bodyMedium,
            color = Ink.White90
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = if (extra == null) plan.detalle else "${plan.detalle}  ·  $extra",
            style = MaterialTheme.typography.labelSmall,
            color = Ink.White50
        )
    }
}

/**
 * Traduce lo elegido en pantalla a un encargo.
 *
 * En modo automatico se ignora lo que se haya tocado en personalizada y se
 * usan los valores por defecto: si alguien cambia cosas y luego vuelve a
 * automatica, esta pidiendo justamente que se olviden.
 *
 * Solo se pasan las etapas que existen. Una etapa marcada pero no implementada
 * no puede llegar al procesado como si fuera a hacer algo.
 */
private fun armarTrabajo(
    config: ConfigMejora,
    medio: Medio,
    automatica: Boolean,
    elegidas: SnapshotStateList<Int>,
    activas: SnapshotStateList<Boolean>,
    tramos: List<Tramo>
): Trabajo {
    fun grupo(i: Int): String {
        val g = config.grupos.getOrNull(i) ?: return ""
        return g.opciones[if (automatica) g.porDefecto else elegidas[i]]
    }

    /** Busca un grupo por su etiqueta en vez de por posicion: los grupos
     *  aparecen y desaparecen segun lo que este encendido. */
    fun porNombre(etiqueta: String): String {
        val i = config.grupos.indexOfFirst { it.etiqueta == etiqueta }
        if (i < 0) return ""
        val g = config.grupos[i]
        return g.opciones[if (automatica) g.porDefecto else elegidas[i]]
    }

    val etapas = config.etapas
        .filterIndexed { i, e -> e.disponible && (if (automatica) e.porDefecto else activas[i]) }
        .map { it.clave }
        .toSet()

    // "Nada" -> -1, que en el modelo significa reconstruir sin limpiar.
    val limpieza = when (porNombre("Limpieza")) {
        "Nada" -> -1
        "Media" -> 2
        "Fuerte" -> 3
        else -> 1
    }

    val generados = porNombre("Frames a generar").toIntOrNull() ?: 0

    // Sin comprimir no se recorta nada: el video sale a los fotogramas que se
    // hayan generado. Con comprimir, a los que se pidan.
    val fpsSalida = if ("peso" in etapas) {
        porNombre("Dejarlo en").filter { it.isDigit() }.toIntOrNull() ?: 0
    } else if ("frames" in etapas) {
        generados
    } else 0

    // Nadie va a tocar la resolucion si no esta ni comprimir ni mejorar, asi
    // que sale tal cual venia aunque el selector tenga otra cosa guardada de
    // antes.
    val tocaResolucion = "peso" in etapas || "imagen" in etapas

    return Trabajo(
        medio = medio,
        ladoCorto = if (tocaResolucion) ladoDe(porNombre("Resolucion")) else 0,
        fpsSalida = fpsSalida,
        // Sin comprimir activado no se aprieta: se guarda holgado. Comprimir
        // igualmente seria hacer justo lo que el usuario acaba de apagar.
        bpp = if ("peso" in etapas) bppDe(porNombre("Compresion").ifEmpty { "Alta" }) else 0.45f,
        etapas = etapas,
        fpsGenerados = generados,
        limpieza = limpieza,
        // Solo si la etapa esta encendida: si no, los tramos que hubiera
        // guardados de antes no deben aplicarse.
        tramos = if ("lento" in etapas) {
            if (tramos.isEmpty() && medio.duracionMs > 0) {
                // Por defecto, el clip entero al doble de lento.
                listOf(Tramo(0, medio.duracionMs, 2))
            } else tramos
        } else emptyList(),
        // "Original" en el selector significa dejar el del archivo de partida,
        // que la app ya ha detectado al leerlo.
        formato = if (config.esVideo) "" else {
            porNombre("Formato").takeIf { it != "Original" && it.isNotEmpty() }
                ?: medio.formato.ifEmpty { "JPG" }
        }
    )
}

/**
 * Personalizada: resolucion, fps de salida y cuantos frames generar.
 *
 * "Frames a generar" solo tiene sentido si la etapa de generar frames esta
 * encendida, y 60 fps de salida solo se puede si hay frames que generar: un
 * video de 30 no tiene material para llenar 60 sin inventarselo. Las dos
 * dependencias se muestran atenuando la opcion, no escondiendola, para que
 * se entienda que existe y por que no esta disponible.
 */
@Composable
private fun PanelPersonalizada(
    config: ConfigMejora,
    activas: SnapshotStateList<Boolean>,
    elegidas: SnapshotStateList<Int>,
    /** Lado corto del archivo, para saber que opciones lo superan. */
    lado: Int,
    onAjustes: () -> Unit
) {
    val scroll = rememberScrollState()
    // Eleccion que sube por encima del original y espera confirmacion.
    var pendiente by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    val espera = pendiente
    if (espera != null) {
        SubirDialog(
            onSeguir = {
                elegidas[espera.second] = espera.first
                pendiente = null
            },
            onCancelar = { pendiente = null }
        )
    }

    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            // Sitio para la barra de la izquierda, que va fuera del contenido
            // para no moverlo cuando aparece.
            .padding(start = 12.dp)
    ) {
        Text(
            text = "QUE APLICAR",
            style = MaterialTheme.typography.labelSmall,
            color = Ink.White50
        )
        Spacer(Modifier.height(7.dp))

        config.etapas.chunked(2).forEachIndexed { fila, par ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                par.forEachIndexed { col, e ->
                    val i = fila * 2 + col
                    // Estabilizar y camara lenta necesitan fotogramas
                    // generados: la primera para tener algo que mezclar, la
                    // segunda para tener algo que repartir.
                    val dependeDeFrames =
                        (e.clave == "estabilizar" || e.clave == "lento") && !activas[E_FRAMES]
                    val puede = e.disponible && !dependeDeFrames
                    ToggleChip(
                        texto = e.nombre,
                        activo = activas[i] && puede,
                        onToggle = {
                            if (puede) {
                                activas[i] = !activas[i]
                                if (e.clave == "frames" && !activas[i]) {
                                    // Apagar la generacion arrastra a las dos
                                    // que dependen de ella.
                                    config.etapas.forEachIndexed { j, otra ->
                                        if (otra.clave == "estabilizar" || otra.clave == "lento") {
                                            activas[j] = false
                                        }
                                    }
                                }
                            }
                        },
                        habilitado = puede,
                        onEditar = if (e.conAjustes) onAjustes else null,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (par.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(4.dp))

        val mejora = activo(config, activas, "imagen")
        val comprime = activo(config, activas, "peso")

        // Cada grupo sale solo si la etapa de la que depende esta encendida.
        config.grupos.forEachIndexed { i, g ->
            val visible = g.dependeDe.isEmpty() ||
                g.dependeDe.any { activo(config, activas, it) }
            if (!visible) return@forEachIndexed

            val esResolucion = g.etiqueta == "Resolucion"
            // Comprimir solo baja. Subir por encima de lo que trae el archivo
            // es cosa de Mejorar, que es quien puede reconstruir ese detalle.
            val tope = if (esResolucion && !mejora) {
                primeraPorEncima(g.opciones, lado)
            } else -1

            OptionSelector(
                label = g.etiqueta,
                options = g.opciones,
                selectedIndex = elegidas[i],
                onSelect = { elegido ->
                    if (esResolucion && porEncima(g.opciones[elegido], lado)) {
                        pendiente = elegido to i
                    } else {
                        elegidas[i] = elegido
                    }
                },
                compacto = true,
                bloqueadasDesde = tope,
                marcada = if (esResolucion) indiceDelOriginal(g.opciones, lado) else -1,
                // Comprimir y Mejorar comparten esta resolucion cuando los dos
                // estan encendidos: es un solo ajuste, no dos que se pisen.
                enlazada = esResolucion && mejora && comprime
            )
            Spacer(Modifier.height(10.dp))
        }

        Spacer(Modifier.height(6.dp))
    }

        BarraScroll(
            scroll = scroll,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(3.dp)
        )
    }
}

/**
 * Marca de desplazamiento, a la izquierda.
 *
 * Sale solo si hay algo que desplazar, y de un gris muy apagado: es una pista
 * de que abajo queda mas, no un control. Se enciende mientras se arrastra y
 * vuelve a apagarse sola al soltar, para no quedarse como un elemento fijo mas
 * en una pantalla que va de estar limpia.
 */
@Composable
private fun BarraScroll(scroll: ScrollState, modifier: Modifier = Modifier) {
    if (scroll.maxValue <= 0) return

    val encendida by animateFloatAsState(
        targetValue = if (scroll.isScrollInProgress) 1f else 0.30f,
        animationSpec = tween(
            if (scroll.isScrollInProgress) Motion.TAP else Motion.SECTION,
            easing = Motion.Smooth
        ),
        label = "barraScroll"
    )

    Box(
        modifier = modifier.drawBehind {
            val radio = size.width / 2
            drawRoundRect(
                color = Ink.White,
                alpha = 0.10f * encendida,
                cornerRadius = CornerRadius(radio)
            )
            // El tamano del pulgar dice cuanto se ve del total, y su posicion
            // por donde vas: la misma informacion que da una barra de siempre.
            val visible = size.height / (size.height + scroll.maxValue)
            val alto = (size.height * visible).coerceAtLeast(radio * 8)
            val y = (size.height - alto) * (scroll.value.toFloat() / scroll.maxValue)
            drawRoundRect(
                color = Ink.White,
                alpha = encendida,
                topLeft = Offset(0f, y),
                size = Size(size.width, alto),
                cornerRadius = CornerRadius(radio)
            )
        }
    )
}

/**
 * ¿Esta opcion deja el archivo mas grande de lo que ya era?
 *
 * "Original" nunca lo es: es exactamente lo que hay. Solo cuentan los numeros.
 */
private fun porEncima(opcion: String, lado: Int): Boolean {
    if (lado <= 0) return false
    val n = opcion.trimEnd('p').toIntOrNull() ?: return false
    return n > lado
}

/** Primera opcion que se pasa del tamano del archivo. -1 si ninguna. */
private fun primeraPorEncima(opciones: List<String>, lado: Int): Int {
    val i = opciones.indexOfFirst { porEncima(it, lado) }
    return i
}

/**
 * Opcion que coincide con lo que ya trae el archivo.
 *
 * Si hay una con ese numero exacto, esa; si no, "Original", que significa lo
 * mismo. Es la referencia para saber que baja y que sube.
 */
private fun indiceDelOriginal(opciones: List<String>, lado: Int): Int {
    val exacta = opciones.indexOfFirst { it.trimEnd('p').toIntOrNull() == lado }
    if (exacta >= 0) return exacta
    return opciones.indexOf("Original")
}

/** ¿Esta encendida la etapa con esa clave? */
private fun activo(
    config: ConfigMejora,
    activas: SnapshotStateList<Boolean>,
    clave: String
): Boolean {
    val i = config.etapas.indexOfFirst { it.clave == clave }
    return i >= 0 && activas.getOrElse(i) { false }
}

/** Avanzada: que etapas aplicar y cuanto apretar la compresion. */
/** Contenedor con borde tenue. Con estirar=false se ajusta a su contenido. */
@Composable
private fun Tarjeta(
    estirar: Boolean = true,
    contenido: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = (if (estirar) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
            .clip(RoundedCornerShape(radio(20.dp)))
            .drawBehind {
                drawRoundRect(
                    color = Ink.White,
                    alpha = 0.035f,
                    cornerRadius = CornerRadius(radio(20.dp).toPx())
                )
                drawRoundRect(
                    color = Ink.White,
                    alpha = 0.10f,
                    cornerRadius = CornerRadius(radio(20.dp).toPx()),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
            .padding(16.dp),
        content = contenido
    )
}

/** Enlace de una linea, con una flecha. */
@Composable
private fun BotonTexto(texto: String, flecha: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val fondo by animateFloatAsState(
        targetValue = if (pressed) 0.10f else 0.035f,
        animationSpec = tween(if (pressed) Motion.TAP else Motion.NORMAL, easing = Motion.Smooth),
        label = "linkFondo"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(radio(14.dp)))
            .drawBehind {
                drawRoundRect(
                    color = Ink.White,
                    alpha = fondo,
                    cornerRadius = CornerRadius(radio(14.dp).toPx())
                )
                drawRoundRect(
                    color = Ink.White,
                    alpha = 0.12f,
                    cornerRadius = CornerRadius(radio(14.dp).toPx()),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = texto.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = Ink.White70,
            textAlign = TextAlign.Start
        )
        Spacer(Modifier.weight(1f))
        Canvas(Modifier.size(12.dp)) {
            val g = 1.5.dp.toPx()
            val x0 = if (flecha) size.width * 0.35f else size.width * 0.65f
            val x1 = if (flecha) size.width * 0.7f else size.width * 0.3f
            drawLine(
                Ink.White, alpha = 0.6f,
                start = Offset(x0, 0f), end = Offset(x1, size.height / 2),
                strokeWidth = g, cap = StrokeCap.Round
            )
            drawLine(
                Ink.White, alpha = 0.6f,
                start = Offset(x1, size.height / 2), end = Offset(x0, size.height),
                strokeWidth = g, cap = StrokeCap.Round
            )
        }
    }
}
