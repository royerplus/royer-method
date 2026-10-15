package com.royermethod.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.royermethod.app.ui.theme.Estilo
import com.royermethod.app.ui.theme.Ink
import com.royermethod.app.ui.theme.Motion
import com.royermethod.app.ui.theme.Velocidad

/**
 * Latido del neon.
 *
 * Devuelve el State y no el Float a proposito. Si se lee el valor durante la
 * composicion, el componente que lo lee se recompone en cada frame: a 144Hz
 * son 144 recomposiciones por segundo de su subarbol entero. Leyendolo dentro
 * de una lambda de dibujo o de graphicsLayer, solo se repite esa fase, que es
 * lo unico que de verdad cambia.
 */
@Composable
fun rememberBreath(
    min: Float = 0.72f,
    max: Float = 1f,
    durationMs: Int = Motion.BREATH
): State<Float> {
    // Con el latido apagado en Personalizar, el neon se queda en su punto alto
    if (!Estilo.latido || Estilo.velocidad == Velocidad.INSTANTANEA) {
        return remember(max) { mutableFloatStateOf(max) }
    }
    val t = rememberInfiniteTransition(label = "breath")
    return t.animateFloat(
        initialValue = min,
        targetValue = max,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs, easing = Motion.Breathe),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathValue"
    )
}

/** Halo rectangular detras de un contenedor. La intensidad llega como lambda
 *  para que se lea en fase de dibujo y no fuerce recomposicion. */
private fun Modifier.neonHalo(
    radius: Dp,
    intensity: () -> Float
): Modifier = this.drawBehind {
    val v = intensity() * Estilo.halo
    if (v <= 0f) return@drawBehind
    val r = radius.toPx()
    val spread = floatArrayOf(
        14f, 0.05f,
        9f, 0.08f,
        5f, 0.12f,
        2f, 0.18f
    )
    var i = 0
    while (i < spread.size) {
        val grow = spread[i]
        drawRoundRect(
            color = Ink.White,
            alpha = (spread[i + 1] * v).coerceIn(0f, 1f),
            topLeft = Offset(-grow, -grow),
            size = Size(size.width + grow * 2, size.height + grow * 2),
            cornerRadius = CornerRadius(r + grow),
            style = Stroke(width = 1.2f)
        )
        i += 2
    }
}

/**
 * Boton principal: relleno blanco, texto negro, halo alrededor.
 * Al presionar se encoge y el halo se apaga; al soltar vuelve despacio.
 */
@Composable
fun NeonButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 62.dp
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val breath = rememberBreath(0.75f, 1f)

    val scaleA = animateFloatAsState(
        targetValue = if (pressed) 0.965f else 1f,
        animationSpec = tween(
            durationMillis = if (pressed) Motion.TAP else Motion.NORMAL,
            easing = Motion.Smooth
        ),
        label = "btnScale"
    )
    val haloA = animateFloatAsState(
        targetValue = if (pressed) 0.15f else 1f,
        animationSpec = tween(if (pressed) Motion.TAP else Motion.NORMAL, easing = Motion.Smooth),
        label = "btnHalo"
    )

    val shape = RoundedCornerShape(height / 2)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .graphicsLayer {
                val s = scaleA.value
                scaleX = s
                scaleY = s
            }
            .alpha(if (enabled) 1f else 0.35f)
            .neonHalo(height / 2) { if (enabled) haloA.value * breath.value else 0f }
            .clip(shape)
            .background(Ink.White)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = Ink.Black
        )
    }
}

/** Boton secundario: solo contorno. */
@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 56.dp
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val scaleA = animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(if (pressed) Motion.TAP else Motion.NORMAL, easing = Motion.Smooth),
        label = "ghostScale"
    )
    val fillA = animateFloatAsState(
        targetValue = if (pressed) 0.14f else 0.04f,
        animationSpec = tween(if (pressed) Motion.TAP else Motion.NORMAL, easing = Motion.Smooth),
        label = "ghostFill"
    )
    val shape = RoundedCornerShape(height / 2)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .graphicsLayer {
                val s = scaleA.value
                scaleX = s
                scaleY = s
            }
            .clip(shape)
            .drawBehind { drawRect(Ink.White, alpha = fillA.value) }
            .border(BorderStroke(1.dp, Ink.White30), shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = Ink.White90
        )
    }
}

/**
 * Selector de dos opciones. Una sola pastilla blanca que se desliza, y el
 * texto de debajo se invierte a negro conforme pasa por encima.
 *
 * Sirve tanto para gratis/pro como para automatica/personalizada; son el
 * mismo control con distintas etiquetas.
 */
@Composable
fun SegmentedToggle(
    izquierda: String,
    derecha: String,
    derechaActiva: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 54.dp
) {
    val shape = RoundedCornerShape(height / 2)
    val breath = rememberBreath(0.6f, 0.95f)

    val slide by animateFloatAsState(
        targetValue = if (derechaActiva) 1f else 0f,
        animationSpec = tween(Motion.SECTION / 2, easing = Motion.Smooth),
        label = "toggleSlide"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .neonHalo(height / 2) { breath.value * 0.5f }
            .clip(shape)
            .background(Ink.White04)
            .border(BorderStroke(1.dp, Ink.White15), shape)
    ) {
        val halfWidth = maxWidth / 2

        Box(
            modifier = Modifier
                .offset(x = halfWidth * slide)
                .padding(4.dp)
                .width(halfWidth - 8.dp)
                .height(height - 8.dp)
                .clip(RoundedCornerShape((height - 8.dp) / 2))
                .background(Ink.White)
        )

        // La pastilla mide media anchura y las mayusculas van muy espaciadas,
        // asi que una palabra larga como PERSONALIZADA llegaba a tocar la
        // curva de los extremos. El tamano lo marca la mas larga de las dos, y
        // se aplica a las dos por igual: con estilos distintos a cada lado
        // saltaria a la vista que una es mas chica que la otra.
        val estilo = when (maxOf(izquierda.length, derecha.length)) {
            in 0..8 -> MaterialTheme.typography.labelLarge
            in 9..11 -> MaterialTheme.typography.labelLarge.copy(
                fontSize = 12.sp, letterSpacing = 2.4.sp
            )

            else -> MaterialTheme.typography.labelLarge.copy(
                fontSize = 11.sp, letterSpacing = 1.6.sp
            )
        }

        Row(Modifier.fillMaxSize()) {
            ToggleLabel(
                text = izquierda,
                estilo = estilo,
                selectedAmount = 1f - slide,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onChange(false) }
            )
            ToggleLabel(
                text = derecha,
                estilo = estilo,
                selectedAmount = slide,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onChange(true) }
            )
        }
    }
}

@Composable
private fun ToggleLabel(
    text: String,
    estilo: TextStyle,
    selectedAmount: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.uppercase(),
            style = estilo,
            maxLines = 1,
            color = lerpColor(Ink.White50, Ink.Black, selectedAmount)
        )
    }
}

private fun lerpColor(a: Color, b: Color, t: Float): Color {
    val k = t.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * k,
        green = a.green + (b.green - a.green) * k,
        blue = a.blue + (b.blue - a.blue) * k,
        alpha = a.alpha + (b.alpha - a.alpha) * k
    )
}

/**
 * Etiqueta de seccion: texto chico, muy espaciado, con una linea al lado.
 *
 * El hueco de la derecha admite un dato suelto (el nombre del archivo, por
 * ejemplo). Es espacio que ya estaba vacio y ahorra una fila entera en una
 * pantalla que no puede crecer.
 */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    derecha: String? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = Ink.White50
        )
        Box(
            modifier = Modifier
                // El texto de la derecha se mide antes que esto por no llevar
                // peso, asi que la linea se queda con lo que sobre y nunca lo
                // aplasta.
                .padding(horizontal = 14.dp)
                .weight(1f)
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Ink.White15, Color.Transparent)
                    )
                )
        )
        if (derecha != null) {
            Text(
                text = derecha,
                style = MaterialTheme.typography.labelSmall,
                color = Ink.White30,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

/** Tarjeta contenedora con borde tenue. */
@Composable
fun NeonCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Ink.White04)
            .border(BorderStroke(1.dp, Ink.White08), shape)
            .padding(20.dp)
    ) {
        content()
    }
}

/** Selector de opciones en linea. Solo visual. */
@Composable
fun OptionSelector(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    compacto: Boolean = false,
    /** Desde este indice las opciones son de pago. -1 las deja todas libres. */
    bloqueadasDesde: Int = -1,
    /**
     * Opcion que corresponde a lo que ya trae el archivo.
     *
     * Va con un borde fino alrededor, este elegida o no. Sirve de referencia:
     * lo que quede a su izquierda baja calidad y lo que quede a su derecha la
     * sube por encima de lo que hay, que casi nunca es buena idea.
     */
    marcada: Int = -1,
    /** Se ensena un candado abierto al lado de la etiqueta. */
    enlazada: Boolean = false
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = Ink.White50
            )
            if (enlazada) {
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .size(width = 16.dp, height = 10.dp)
                        .drawBehind { cadena(this) }
                )
            }
        }
        Row(
            modifier = Modifier
                .padding(top = if (compacto) 7.dp else 12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(if (compacto) 6.dp else 8.dp)
        ) {
            options.forEachIndexed { i, opt ->
                val bloqueada = bloqueadasDesde >= 0 && i >= bloqueadasDesde
                OptionChip(
                    text = opt,
                    selected = i == selectedIndex,
                    onClick = { if (!bloqueada) onSelect(i) },
                    alto = if (compacto) 38.dp else 46.dp,
                    bloqueada = bloqueada,
                    marcada = i == marcada,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** Dos eslabones: dice que ese ajuste va pegado al de al lado. */
private fun cadena(scope: androidx.compose.ui.graphics.drawscope.DrawScope) = with(scope) {
    val g = 1.3.dp.toPx()
    val r = size.height / 2 - g
    drawRoundRect(
        color = Ink.White, alpha = 0.55f,
        topLeft = Offset(0f, size.height / 2 - r),
        size = Size(r * 2.4f, r * 2),
        cornerRadius = CornerRadius(r),
        style = Stroke(g)
    )
    drawRoundRect(
        color = Ink.White, alpha = 0.55f,
        topLeft = Offset(size.width - r * 2.4f, size.height / 2 - r),
        size = Size(r * 2.4f, r * 2),
        cornerRadius = CornerRadius(r),
        style = Stroke(g)
    )
    drawLine(
        Ink.White, alpha = 0.55f,
        start = Offset(size.width * 0.34f, size.height / 2),
        end = Offset(size.width * 0.66f, size.height / 2),
        strokeWidth = g
    )
}

/**
 * Chip que se enciende y se apaga, para elegir que mejoras aplicar. A
 * diferencia del selector de arriba, aqui las opciones no son excluyentes.
 */
@Composable
fun ToggleChip(
    texto: String,
    activo: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
    /**
     * Lapiz dentro del chip, para las etapas que tienen algo que ajustar.
     *
     * Va dentro y no como opcion aparte porque lo que se ajusta pertenece a
     * esa etapa: sacarlo fuera lo convertiria en un ajuste suelto que hay que
     * relacionar mentalmente con el boton de al lado.
     */
    onEditar: (() -> Unit)? = null
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val bordeA = animateFloatAsState(
        targetValue = if (activo) 0.55f else 0.12f,
        animationSpec = tween(Motion.QUICK, easing = Motion.Smooth),
        label = "tcBorde"
    )
    val fondoA = animateFloatAsState(
        targetValue = when {
            activo -> 0.10f
            pressed -> 0.07f
            else -> 0.025f
        },
        animationSpec = tween(Motion.QUICK, easing = Motion.Smooth),
        label = "tcFondo"
    )
    val puntoA = animateFloatAsState(
        targetValue = if (activo) 1f else 0f,
        animationSpec = tween(Motion.NORMAL, easing = Motion.Smooth),
        label = "tcPunto"
    )
    val escalaA = animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = tween(if (pressed) Motion.TAP else Motion.NORMAL, easing = Motion.Smooth),
        label = "tcEscala"
    )

    val radio = with(androidx.compose.ui.platform.LocalDensity.current) { 12.dp.toPx() }

    Box(
        modifier = modifier
            .height(42.dp)
            .alpha(if (habilitado) 1f else 0.35f)
            .graphicsLayer {
                val s = escalaA.value
                scaleX = s
                scaleY = s
            }
            .clip(RoundedCornerShape(12.dp))
            .drawBehind {
                drawRoundRect(
                    color = Ink.White,
                    alpha = fondoA.value,
                    cornerRadius = CornerRadius(radio)
                )
                drawRoundRect(
                    color = Ink.White,
                    alpha = bordeA.value,
                    cornerRadius = CornerRadius(radio),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = habilitado,
                onClick = onToggle
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            // Punto que se enciende: sustituye a una casilla de verificacion,
            // que rompería el lenguaje geometrico del resto.
            Box(
                Modifier
                    .size(6.dp)
                    .drawBehind {
                        drawCircle(Ink.White, alpha = 0.20f + 0.80f * puntoA.value)
                    }
            )
            Spacer(Modifier.width(9.dp))
            Text(
                text = texto,
                style = MaterialTheme.typography.bodyMedium,
                color = if (activo) Ink.White else Ink.White50,
                maxLines = 1,
                modifier = if (onEditar != null) Modifier.weight(1f, fill = false) else Modifier
            )
            if (onEditar != null && activo) {
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .drawBehind {
                            drawRoundRect(
                                color = Ink.White, alpha = 0.14f,
                                cornerRadius = CornerRadius(7.dp.toPx())
                            )
                            lapiz(this)
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onEditar
                        )
                )
            }
        }
    }
}

/** Lapiz: hay algo que ajustar aqui dentro. */
private fun lapiz(scope: androidx.compose.ui.graphics.drawscope.DrawScope) = with(scope) {
    val g = 1.4.dp.toPx()
    val a = size.width * 0.28f
    val b = size.width * 0.72f
    drawLine(
        Ink.White, start = Offset(a, b), end = Offset(b, a),
        strokeWidth = g, cap = StrokeCap.Round
    )
    drawLine(
        Ink.White, alpha = 0.8f,
        start = Offset(a, b), end = Offset(a + g * 1.6f, b - g * 1.6f),
        strokeWidth = g * 2.2f, cap = StrokeCap.Round
    )
}

@Composable
private fun OptionChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    alto: Dp = 46.dp,
    bloqueada: Boolean = false,
    marcada: Boolean = false
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val bgA = animateFloatAsState(
        targetValue = if (selected) 1f else if (pressed) 0.16f else 0.05f,
        animationSpec = tween(Motion.QUICK, easing = Motion.Smooth),
        label = "chipBg"
    )
    val borderA = animateFloatAsState(
        targetValue = if (selected) 0f else 0.14f,
        animationSpec = tween(Motion.QUICK, easing = Motion.Smooth),
        label = "chipBorder"
    )
    val scaleA = animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = tween(if (pressed) Motion.TAP else Motion.NORMAL, easing = Motion.Smooth),
        label = "chipScale"
    )
    val textA = animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(Motion.QUICK, easing = Motion.Smooth),
        label = "chipText"
    )

    val shape = RoundedCornerShape(14.dp)
    val radius = with(androidx.compose.ui.platform.LocalDensity.current) { 14.dp.toPx() }

    Box(
        modifier = modifier
            .height(alto)
            .alpha(if (bloqueada) 0.30f else 1f)
            .graphicsLayer {
                val s = scaleA.value
                scaleX = s
                scaleY = s
            }
            .clip(shape)
            .drawBehind {
                drawRoundRect(
                    color = Ink.White,
                    alpha = bgA.value,
                    cornerRadius = CornerRadius(radius)
                )
                if (borderA.value > 0f) {
                    drawRoundRect(
                        color = Ink.White,
                        alpha = borderA.value,
                        cornerRadius = CornerRadius(radius),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
                // El borde de la opcion que ya trae el archivo, tambien cuando
                // esta elegida: por dentro, para que se vea sobre el relleno
                // blanco del chip seleccionado.
                if (marcada) {
                    val d = 3.dp.toPx()
                    drawRoundRect(
                        color = if (selected) Ink.Black else Ink.White,
                        alpha = if (selected) 0.35f else 0.85f,
                        topLeft = Offset(d, d),
                        size = Size(size.width - d * 2, size.height - d * 2),
                        cornerRadius = CornerRadius(radius - d),
                        style = Stroke(width = 1.2.dp.toPx())
                    )
                }
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = lerpColor(Ink.White70, Ink.Black, textA.value)
        )
    }
}

/** Punto luminoso, para indicadores de estado. */
@Composable
fun GlowDot(
    modifier: Modifier = Modifier,
    size: Dp = 6.dp
) {
    val breath = rememberBreath(0.35f, 1f, 2400)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .drawBehind { drawRect(Ink.White, alpha = breath.value) }
    )
}

