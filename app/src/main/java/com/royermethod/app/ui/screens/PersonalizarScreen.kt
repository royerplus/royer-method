package com.royermethod.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.royermethod.app.editor.modelo.AjustesSalida
import com.royermethod.app.editor.modelo.Calidad
import com.royermethod.app.editor.modelo.Fondo
import com.royermethod.app.editor.modelo.Formato
import com.royermethod.app.editor.ui.AjustesEditor
import com.royermethod.app.editor.ui.RESOLUCIONES
import com.royermethod.app.ui.components.ActionCard
import com.royermethod.app.ui.components.BotonBorde
import com.royermethod.app.ui.components.BotonIcono
import com.royermethod.app.ui.components.Deslizador
import com.royermethod.app.ui.components.Etiqueta
import com.royermethod.app.ui.components.Glifos
import com.royermethod.app.ui.components.Interruptor
import com.royermethod.app.ui.components.Marca
import com.royermethod.app.ui.components.Muestras
import com.royermethod.app.ui.components.Nota
import com.royermethod.app.ui.components.Opciones
import com.royermethod.app.ui.components.Pestanas
import com.royermethod.app.ui.components.esquinas
import com.royermethod.app.ui.theme.ACENTOS
import com.royermethod.app.ui.theme.Estilo
import com.royermethod.app.ui.theme.FondoApp
import com.royermethod.app.ui.theme.Ink
import com.royermethod.app.ui.theme.Velocidad
import kotlin.math.max
import kotlin.math.min

/**
 * Todo lo que se puede cambiar del aspecto de la app y de como empieza el
 * editor. Cada cambio se ve al momento (la tarjeta de arriba es una de las
 * de verdad) y se guarda solo.
 */
@Composable
fun PersonalizarScreen(onVolver: () -> Unit, pestanaInicial: Int = 0) {
    var pestana by remember { mutableIntStateOf(pestanaInicial) }
    Column(
        Modifier
            .fillMaxSize()
            .background(Ink.Black)
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 6.dp, end = 16.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            BotonIcono(Glifos.atras, onVolver)
            Text(
                "PERSONALIZAR",
                style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 3.sp),
                color = Ink.White,
                modifier = Modifier.weight(1f)
            )
        }
        Pestanas(listOf("Aspecto", "Editor"), pestana, { pestana = it }, Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            if (pestana == 0) Aspecto() else Editor()
        }
    }
}

private fun cambio(f: () -> Unit) {
    f()
    Estilo.alCambiar()
}

@Composable
private fun ColumnScope.Aspecto() {
    // Una tarjeta de verdad: se ve el color, el halo, el latido y las esquinas a la vez
    ActionCard(titulo = "Asi se ve", subtitulo = "Cambia algo y mira", marca = Marca.Video, onClick = {}, desfase = 0f)
    Spacer(Modifier.height(18.dp))

    Etiqueta("Color del neon")
    val acento = Estilo.acento.toArgb()
    Muestras(ACENTOS.map { it.second.toArgb() }, acento, { c -> cambio { Estilo.acento = Color(c) } })
    val (tono, viveza) = remember(acento) { tonoYViveza(Estilo.acento) }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .height(6.dp)
            .background(Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f % 360f, 0.6f, 1f) }), esquinas(3.dp))
    )
    Deslizador("Tono a mano", tono, 0f, 359f, { t -> cambio { Estilo.acento = Color.hsv(t, viveza.coerceAtLeast(0.35f), 1f) } },
        texto = { "${it.toInt()}°" })
    Deslizador("Viveza", viveza, 0f, 1f, { v -> cambio { Estilo.acento = Color.hsv(tono, v, 1f) } },
        texto = { if (it < 0.02f) "Blanco" else "${(it * 100).toInt()}%" })

    Spacer(Modifier.height(6.dp))
    Etiqueta("Fondo")
    Opciones(FondoApp.values().toList(), Estilo.fondo, { it.nombre }, { f -> cambio { Estilo.fondo = f } })

    Spacer(Modifier.height(6.dp))
    Deslizador("Brillo del halo", Estilo.halo, 0f, 1.6f, { v -> cambio { Estilo.halo = v } }, neutro = 1f,
        texto = { if (it < 0.02f) "Sin halo" else "${(it * 100).toInt()}%" })
    Interruptor("Neon que respira", Estilo.latido, { v -> cambio { Estilo.latido = v } }, "Un brillo que sube y baja muy despacio")

    Spacer(Modifier.height(6.dp))
    Etiqueta("Movimiento de la app")
    Opciones(Velocidad.values().toList(), Estilo.velocidad, { it.nombre }, { v -> cambio { Estilo.velocidad = v } })

    Spacer(Modifier.height(6.dp))
    Deslizador("Tamano de letra", Estilo.letra, 0.85f, 1.25f, { v -> cambio { Estilo.letra = v } }, neutro = 1f, paso = 0.05f,
        texto = { "${(it * 100).toInt()}%" })
    Deslizador("Esquinas", Estilo.redondez, 0f, 1.5f, { v -> cambio { Estilo.redondez = v } }, neutro = 1f,
        texto = { if (it < 0.02f) "Rectas" else "${(it * 100).toInt()}%" })

    Spacer(Modifier.height(16.dp))
    BotonBorde("Volver al aspecto original", { Estilo.restablecer() }, glifo = Glifos.reiniciar)
}

@Composable
private fun ColumnScope.Editor() {
    fun guardar(f: () -> Unit) {
        f()
        AjustesEditor.alCambiar()
    }
    fun salida(f: (AjustesSalida) -> AjustesSalida) = guardar { AjustesEditor.salida = f(AjustesEditor.salida) }

    Nota("Con esto empieza cada proyecto nuevo. Dentro de un proyecto se puede cambiar todo.")
    Spacer(Modifier.height(10.dp))
    Etiqueta("Proporcion")
    Opciones(Formato.values().toList(), AjustesEditor.formato, { it.nombre }, { f -> guardar { AjustesEditor.formato = f } })
    Etiqueta("Fondo donde el video no llega")
    Opciones(Fondo.values().toList(), AjustesEditor.fondo, { it.nombre }, { f -> guardar { AjustesEditor.fondo = f } })

    Spacer(Modifier.height(6.dp))
    Deslizador("Transicion nueva", AjustesEditor.duracionTransicionUs / 1e6f, 0.1f, 2f,
        { v -> guardar { AjustesEditor.duracionTransicionUs = (v * 1e6f).toLong() } }, neutro = 0.4f, paso = 0.1f,
        texto = { "%.1f s".format(it) })
    Deslizador("Animacion nueva", AjustesEditor.duracionAnimacionUs / 1e6f, 0.1f, 2f,
        { v -> guardar { AjustesEditor.duracionAnimacionUs = (v * 1e6f).toLong() } }, neutro = 0.4f, paso = 0.1f,
        texto = { "%.1f s".format(it) })
    Interruptor("Imanes", AjustesEditor.imanes, { v -> guardar { AjustesEditor.imanes = v } },
        "Al mover o recortar, los bloques se pegan al cabezal y a los cortes")
    Interruptor("Ajustar a fotogramas", AjustesEditor.ajustarFotogramas, { v -> guardar { AjustesEditor.ajustarFotogramas = v } },
        "Los tiempos de claves y efectos se pegan a los fotogramas de 30 fps. Apagado se colocan a la milesima: 0,005 s, 0,001 s")

    Spacer(Modifier.height(10.dp))
    Etiqueta("Exportar")
    val s = AjustesEditor.salida
    Opciones(Calidad.values().toList(), s.calidad, { it.nombre }, { c -> salida { it.copy(calidad = c) } })
    Opciones(RESOLUCIONES, s.ladoCorto, { "${it}p" }, { l -> salida { it.copy(ladoCorto = l) } })
    Interruptor("H.265", s.hevc, { v -> salida { it.copy(hevc = v) } }, "Misma calidad con bastante menos peso. Si el telefono no lo tiene, sale en H.264")
    Interruptor("Desenfoque de movimiento", s.desenfoqueMovimiento, { v -> salida { it.copy(desenfoqueMovimiento = v) } },
        "El rastro que hace que las transiciones a 30 fps se vean fluidas")

    Spacer(Modifier.height(10.dp))
    Etiqueta("Mis animaciones")
    val mias = AjustesEditor.mias.map { it.nombre to { guardar { AjustesEditor.mias = AjustesEditor.mias.filter { m -> m !== it } } } } +
        AjustesEditor.continuasMias.map { it.first to { guardar { AjustesEditor.continuasMias = AjustesEditor.continuasMias.filter { m -> m !== it } } } }
    if (mias.isEmpty()) {
        Nota("Todavia no guardaste ninguna. En el editor, combina piezas en Animar y toca \"Guardar como mia\".")
    }
    mias.forEach { (nombre, quitar) ->
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(nombre, style = MaterialTheme.typography.bodyMedium, color = Ink.White90, modifier = Modifier.weight(1f))
            BotonIcono(Glifos.papelera, quitar, color = Ink.White50)
        }
    }
}

/** Tono (0-360) y viveza (0-1) de un color, para colocar los deslizadores. */
private fun tonoYViveza(c: Color): Pair<Float, Float> {
    val mx = max(c.red, max(c.green, c.blue))
    val mn = min(c.red, min(c.green, c.blue))
    val d = mx - mn
    if (mx <= 0f || d < 1e-4f) return 0f to 0f
    val h = when (mx) {
        c.red -> 60f * (((c.green - c.blue) / d) % 6f)
        c.green -> 60f * ((c.blue - c.red) / d + 2f)
        else -> 60f * ((c.red - c.green) / d + 4f)
    }
    return ((h + 360f) % 360f) to (d / mx).coerceIn(0f, 1f)
}
