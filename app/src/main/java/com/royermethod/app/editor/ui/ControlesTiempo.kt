package com.royermethod.app.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.royermethod.app.editor.modelo.Edicion
import com.royermethod.app.editor.modelo.Linea
import com.royermethod.app.editor.modelo.TiempoPreciso
import com.royermethod.app.ui.components.Chip
import com.royermethod.app.ui.components.Deslizador
import com.royermethod.app.ui.components.Nota
import com.royermethod.app.ui.components.esquinas
import com.royermethod.app.ui.theme.Ink
import kotlin.math.roundToLong

/*
 * Controles de tiempo de los paneles.
 *
 * CampoTiempo es el de precision: se escribe el tiempo (0,005 s), se arrastra
 * o se mueve con -/+; se pega a los fotogramas solo si el usuario lo tiene
 * pedido (AjustesEditor.ajustarFotogramas). TiempoFotogramas es el de
 * siempre, en fotogramas enteros: lo siguen usando las transiciones, que la
 * linea cuantiza igual (Linea.solape).
 */

/**
 * Hasta donde dos claves son "el mismo instante" para los botones de claves:
 * medio fotograma con el ajuste a fotogramas, 0,0002 s sin el (asi 0,001 s y
 * 0,005 s son claves distintas).
 */
fun toleranciaClaves(): Long = TiempoCampo.toleranciaClaves(AjustesEditor.ajustarFotogramas)

/**
 * Las cuentas de [CampoTiempo], sin Compose (se prueban en el ordenador).
 * Todo en microsegundos; nada sale negativo.
 */
internal object TiempoCampo {

    /** El de Edicion.TOLERANCIA_FINA (0,0002 s); cuando exista alli, usar esa. */
    const val TOLERANCIA_FINA_US = 200L

    fun toleranciaClaves(ajustarFotogramas: Boolean): Long =
        if (ajustarFotogramas) Edicion.TOLERANCIA_CLAVE else TOLERANCIA_FINA_US

    /**
     * Segundos de un deslizador a microsegundos, redondeando: con Float,
     * `(0.005f * 1e6).toLong()` da 4 999 y aqui da 5 000.
     */
    fun usDeSegundos(s: Float): Long {
        if (s.isNaN()) return 0L
        return (s.toDouble() * 1_000_000.0).roundToLong().coerceAtLeast(0)
    }

    /**
     * Al fotograma de 30 fps mas cercano contando desde [origenUs] (el inicio
     * del bloque en la linea): asi el tiempo cae en un cuadro que de verdad se
     * exporta aunque el bloque empiece a mitad de fotograma. Con origen 0 es
     * [TiempoPreciso.aFotograma]. Nunca antes del origen.
     */
    fun alFotograma(us: Long, origenUs: Long = 0L): Long {
        val o = origenUs.coerceAtLeast(0)
        return (TiempoPreciso.aFotograma(o + us.coerceAtLeast(0)) - o).coerceAtLeast(0)
    }

    /** Lo que se guarda: pegado a fotograma si se pide y dentro de [minimoUs]..[maximoUs]. */
    fun ajustar(us: Long, minimoUs: Long, maximoUs: Long, fotogramas: Boolean, origenUs: Long = 0L): Long {
        val min = minimoUs.coerceAtLeast(0)
        val v = if (fotogramas) alFotograma(us, origenUs) else us.coerceAtLeast(0)
        return v.coerceIn(min, maxOf(min, maximoUs))
    }

    /**
     * Resolucion del deslizador sin ajuste a fotogramas: en la barra un pixel
     * son varios milisegundos, asi que se redondea a 1 ms (o menos en tramos
     * cortos) en vez de dejar microsegundos al azar. Lo exacto se escribe.
     */
    fun pasoDeslizador(rangoUs: Long): Long = when {
        rangoUs >= 300_000L -> 1_000L
        rangoUs >= 30_000L -> 100L
        rangoUs >= 3_000L -> 10L
        else -> 1L
    }

    /** El valor de un arrastre del deslizador, en segundos, ya redondeado y acotado. */
    fun deDeslizador(segundos: Float, minimoUs: Long, maximoUs: Long, fotogramas: Boolean, origenUs: Long = 0L): Long {
        val min = minimoUs.coerceAtLeast(0)
        val max = maxOf(min, maximoUs)
        val us = usDeSegundos(segundos)
        val v = if (fotogramas) alFotograma(us, origenUs)
        else {
            val paso = pasoDeslizador(max - min)
            (us + paso / 2) / paso * paso
        }
        return v.coerceIn(min, max)
    }

    /**
     * Un toque de -/+. Con fotogramas va al cuadro anterior o siguiente (si el
     * valor esta entre dos, al de ese lado); si no, suma o resta [pasoUs].
     */
    fun paso(valorUs: Long, sentido: Int, fotogramas: Boolean, pasoUs: Long, minimoUs: Long, maximoUs: Long, origenUs: Long = 0L): Long {
        val min = minimoUs.coerceAtLeast(0)
        val max = maxOf(min, maximoUs)
        val v = valorUs.coerceIn(min, max)
        val destino = if (fotogramas) {
            val o = origenUs.coerceAtLeast(0)
            val g = o + v
            val cuadro = if (sentido > 0) Linea.fotogramaEn(g) + 1 else (Linea.fotogramaDe(g) - 1).coerceAtLeast(0)
            Linea.tiempoDe(cuadro) - o
        } else if (sentido > 0) v + pasoUs else v - pasoUs
        return destino.coerceIn(min, max)
    }

    /** Lo escrito en el campo: el tiempo que queda, o por que no vale. */
    data class Lectura(val us: Long?, val error: String? = null, val nota: String? = null)

    fun leer(texto: String, minimoUs: Long, maximoUs: Long, fotogramas: Boolean, origenUs: Long = 0L): Lectura {
        val bruto = TiempoPreciso.leer(texto)
            ?: return Lectura(null, error = if ('-' in texto) "Sin tiempos negativos" else "Escribe un tiempo: 0,005 o 5 ms")
        val min = minimoUs.coerceAtLeast(0)
        val max = maxOf(min, maximoUs)
        val pegado = if (fotogramas) alFotograma(bruto, origenUs) else bruto
        val us = pegado.coerceIn(min, max)
        val nota = when {
            us != pegado -> "Va de ${TiempoPreciso.texto(min)} a ${TiempoPreciso.texto(max)}"
            us != bruto -> "Pegado al fotograma mas cercano"
            else -> null
        }
        return Lectura(us, nota = nota)
    }

    /** Lo que se ofrece para editar: "0.005", sin la unidad. */
    fun textoEditable(us: Long): String = TiempoPreciso.texto(us).removeSuffix(" s")
}

/**
 * Un tiempo con precision de microsegundos, pensado para el pulgar: el valor
 * se toca para escribirlo (acepta "0,005", "0.005" o "5 ms"; nunca negativo),
 * -/+ lo mueven un fotograma (sin ajuste a fotogramas, 1 ms o 10 ms segun la
 * pastilla "Paso" de debajo) y el deslizador lo arrastra (redondeado, nunca
 * truncado; en tramos largos al milisegundo, lo exacto se escribe). Escribir
 * o tocar -/+ es un paso de deshacer (`arrastre = false`); un arrastre
 * entero, otro (`arrastre = true`). Botones de 44 dp.
 *
 * [origenUs] es donde empieza el bloque en la linea, para que el ajuste a
 * fotogramas caiga en cuadros que se exportan; con 0 se cuenta desde el
 * propio tiempo (como hacia TiempoFotogramas).
 */
@Composable
fun CampoTiempo(
    titulo: String,
    valorUs: Long,
    minimoUs: Long,
    maximoUs: Long,
    onCambio: (us: Long, arrastre: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    fotogramas: Boolean = AjustesEditor.ajustarFotogramas,
    deslizador: Boolean = true,
    origenUs: Long = 0L
) {
    val minimo = minimoUs.coerceAtLeast(0)
    val maximo = maxOf(minimo, maximoUs)
    val valor = valorUs.coerceIn(minimo, maximo)
    var editando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // La nota se refiere a un valor: si cambia por otro lado, deja de verse
    var nota by remember { mutableStateOf<Pair<Long, String>?>(null) }
    var grueso by rememberSaveable { mutableStateOf(false) }
    val pasoUs = if (grueso) 10_000L else 1_000L
    fun mover(sentido: Int) {
        nota = null
        onCambio(TiempoCampo.paso(valor, sentido, fotogramas, pasoUs, minimo, maximo, origenUs), false)
    }

    // Titulo y -/valor/+ en una fila; debajo el deslizador y el paso de los
    // botones. Asi el titulo tiene sitio en 360 dp sin partirse letra a letra
    Column(modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                titulo,
                style = MaterialTheme.typography.bodyMedium,
                color = Ink.White70,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            BotonPaso("−", valor > minimo) { mover(-1) }
            if (editando) {
                EntradaTiempo(valor, error != null, onListo = { texto, explicito ->
                    val l = TiempoCampo.leer(texto, minimo, maximo, fotogramas, origenUs)
                    val us = l.us
                    when {
                        us != null -> {
                            editando = false
                            error = null
                            nota = l.nota?.let { us to it }
                            onCambio(us, false)
                            true
                        }
                        // Con "Listo" se queda abierto para corregirlo; al irse, no cambia nada
                        explicito -> { error = l.error; false }
                        else -> { editando = false; error = null; true }
                    }
                })
            } else {
                Text(
                    TiempoPreciso.texto(valor),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
                    color = Ink.White,
                    maxLines = 1,
                    modifier = Modifier
                        .widthIn(min = 96.dp)
                        .clip(esquinas(10.dp))
                        .border(1.dp, Ink.White15, esquinas(10.dp))
                        .clickable { editando = true; error = null; nota = null }
                        .padding(horizontal = 8.dp, vertical = 12.dp)
                )
            }
            BotonPaso("+", valor < maximo) { mover(1) }
        }
        val conDeslizador = deslizador && maximo > minimo
        // Sin deslizador y con fotogramas no hay nada que elegir: no hace falta la fila
        if (conDeslizador || !fotogramas) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (conDeslizador) {
                    Deslizador(
                        titulo, valor / 1e6f, minimo / 1e6f, maximo / 1e6f,
                        { v ->
                            nota = null
                            onCambio(TiempoCampo.deDeslizador(v, minimo, maximo, fotogramas, origenUs), true)
                        },
                        modifier = Modifier.weight(1f),
                        texto = { TiempoPreciso.texto(TiempoCampo.usDeSegundos(it)) },
                        iman = false,
                        cabecera = false
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                // Lo que mueven -/+
                if (fotogramas) Nota("Paso 1 f", Modifier.padding(horizontal = 6.dp))
                else Chip(if (grueso) "Paso 10 ms" else "Paso 1 ms", grueso, { grueso = !grueso })
            }
        }
        val aviso = error ?: nota?.takeIf { it.first == valor }?.second
        if (aviso != null) Nota(aviso, Modifier.padding(bottom = 4.dp), color = if (error != null) Ink.Peligro else Ink.White50)
    }
}

/** Boton redondo de -/+ de un fotograma o un paso: 44 dp para el pulgar. */
@Composable
private fun BotonPaso(texto: String, activo: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Ink.White08)
            .clickable(enabled = activo, onClick = onClick)
            .alpha(if (activo) 1f else 0.3f),
        contentAlignment = Alignment.Center
    ) {
        Text(texto, style = MaterialTheme.typography.titleMedium, color = Ink.White90)
    }
}

/**
 * El campo para escribir un tiempo. Entra con todo elegido para escribir
 * encima. [onListo] recibe el texto y si fue con "Listo" (true) o al perder
 * el foco (false); devuelve si se cierra.
 */
@Composable
private fun EntradaTiempo(valorUs: Long, mal: Boolean, onListo: (String, Boolean) -> Boolean) {
    val inicial = TiempoCampo.textoEditable(valorUs)
    var texto by remember { mutableStateOf(TextFieldValue(inicial, TextRange(0, inicial.length))) }
    val foco = remember { FocusRequester() }
    val focos = LocalFocusManager.current
    var tuvoFoco by remember { mutableStateOf(false) }
    var cerrado by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { runCatching { foco.requestFocus() } }
    fun listo(explicito: Boolean) {
        if (cerrado) return
        if (onListo(texto.text, explicito)) {
            cerrado = true
            if (explicito) focos.clearFocus()
        }
    }
    BasicTextField(
        value = texto,
        onValueChange = { texto = it },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Ink.White, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
        cursorBrush = SolidColor(Ink.White),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { listo(true) }),
        modifier = Modifier
            .width(96.dp)
            .heightIn(min = 44.dp)
            .border(1.dp, if (mal) Ink.Peligro else Ink.White, esquinas(10.dp))
            .padding(horizontal = 8.dp, vertical = 12.dp)
            .focusRequester(foco)
            .onFocusChanged {
                if (it.isFocused) tuvoFoco = true
                else if (tuvoFoco) listo(false)
            }
    )
}

/**
 * Pastilla para encender o apagar el ajuste a fotogramas de los controles de
 * tiempo. Se recuerda entre sesiones.
 */
@Composable
fun InterruptorFotogramas(modifier: Modifier = Modifier) {
    Chip("Ajustar a fotogramas", AjustesEditor.ajustarFotogramas, {
        AjustesEditor.ajustarFotogramas = !AjustesEditor.ajustarFotogramas
        AjustesEditor.alCambiar()
    }, modifier)
}

/** Unidades reales y pasos exactos de 30 fps, tambien para tramos menores de 0.1 s. */
@Composable
internal fun TiempoFotogramas(
    titulo: String,
    valorUs: Long,
    minimoUs: Long,
    maximoUs: Long,
    onCambio: (Long, Boolean) -> Unit
) {
    val maximo = maxOf(minimoUs, maximoUs)
    val valor = valorUs.coerceIn(minimoUs, maximo)
    fun cuantizar(us: Long) = Linea.tiempoDe((us.coerceAtLeast(0) * Linea.FPS / 1_000_000.0).roundToLong()).coerceIn(minimoUs, maximo)
    if (maximo > minimoUs) {
        Deslizador(
            titulo, valor / 1e6f, minimoUs / 1e6f, maximo / 1e6f,
            { v -> onCambio(cuantizar((v * 1e6).toLong()), true) },
            texto = { "%.3f s".format(it) }
        )
    } else {
        Text("$titulo  ${"%.3f s".format(valor / 1e6)}", style = MaterialTheme.typography.bodyMedium, color = Ink.White70, modifier = Modifier.padding(vertical = 8.dp))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Chip("−1 f", false, {
            val n = (valor * Linea.FPS / 1_000_000.0).roundToLong()
            onCambio(Linea.tiempoDe((n - 1).coerceAtLeast(0)).coerceIn(minimoUs, maximo), false)
        })
        Chip("+1 f", false, {
            val n = (valor * Linea.FPS / 1_000_000.0).roundToLong()
            onCambio(Linea.tiempoDe(n + 1).coerceIn(minimoUs, maximo), false)
        })
        Text("1 f = 0.033 s", style = MaterialTheme.typography.bodySmall, color = Ink.White50)
    }
}
