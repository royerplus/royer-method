package com.royermethod.app.editor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.royermethod.app.editor.modelo.Clip
import com.royermethod.app.editor.modelo.EfectoVideo
import com.royermethod.app.editor.modelo.Linea
import com.royermethod.app.editor.modelo.TipoEfectoVideo
import com.royermethod.app.editor.modelo.ProgresoEfecto
import com.royermethod.app.ui.components.BotonBorde
import com.royermethod.app.ui.components.BotonIcono
import com.royermethod.app.ui.components.BotonLleno
import com.royermethod.app.ui.components.Chip
import com.royermethod.app.ui.components.Deslizador
import com.royermethod.app.ui.components.Escala
import com.royermethod.app.ui.components.Etiqueta
import com.royermethod.app.ui.components.Glifos
import com.royermethod.app.ui.components.Interruptor
import com.royermethod.app.ui.components.Nota
import com.royermethod.app.ui.components.Opciones
import com.royermethod.app.ui.components.esquinas
import com.royermethod.app.ui.theme.Ink
import kotlin.math.roundToLong

/** Efectos acumulables, cada uno con su propio intervalo en el reloj del bloque. */
@Composable
fun PanelEfectos(estado: EstadoEditor) {
    val c = estado.clipElegido ?: return
    if (c.duracionUs <= 0) {
        SinNada("Este bloque necesita una duracion para agregar efectos")
        return
    }
    var indice by rememberSaveable(c.id) { mutableIntStateOf(0) }
    var catalogo by rememberSaveable(c.id) { mutableStateOf(c.efectos.isEmpty()) }
    var repetirHasta by remember(c.id) { mutableStateOf<Long?>(null) }
    val elegido = indice.coerceIn(0, (c.efectos.size - 1).coerceAtLeast(0))
    val efecto = c.efectos.getOrNull(elegido)
    val inicioBloque = estado.inicioElegido ?: 0L
    val minimo = minOf(Linea.tiempoDe(1), c.duracionUs)

    // El boton de prueba reproduce unicamente el tramo del efecto seleccionado.
    LaunchedEffect(estado.tiempo, estado.reproduciendo, repetirHasta) {
        val hasta = repetirHasta ?: return@LaunchedEffect
        if (!estado.reproduciendo) repetirHasta = null
        else if (estado.tiempo >= hasta) {
            estado.reproduciendo = false
            repetirHasta = null
        }
    }

    fun ver(local: Long) {
        repetirHasta = null
        estado.reproduciendo = false
        estado.irA(inicioBloque + local.coerceIn(0, (c.duracionUs - 1).coerceAtLeast(0)))
    }

    fun cambiar(arrastre: Boolean = false, f: (EfectoVideo) -> EfectoVideo) {
        estado.cambiarClip(arrastre) { actual ->
            actual.copy(efectos = actual.efectos.mapIndexed { i, e -> if (i == elegido) f(e) else e })
        }
    }

    fun rango(desde: Long, hasta: Long, arrastre: Boolean = false) {
        val ini = desde.coerceIn(0, (c.duracionUs - minimo).coerceAtLeast(0))
        val fin = hasta.coerceIn(ini + minimo, c.duracionUs)
        cambiar(arrastre) { e ->
            e.copy(
                inicioUs = ini,
                finUs = fin,
                // Recortar conserva el reloj y los puntos de la trayectoria.
                // La rapidez es el control para comprimir el movimiento.
                desfaseUs = (e.desfaseUs + ini - e.inicioUs).coerceAtLeast(0),
                duracionBaseUs = e.duracionBaseUs.takeIf { it > 0 } ?: e.duracionUs
            )
        }
    }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "${c.efectos.size} ${if (c.efectos.size == 1) "efecto" else "efectos"}",
            style = MaterialTheme.typography.bodyMedium,
            color = Ink.White70,
            modifier = Modifier.weight(1f)
        )
        if (c.efectos.isNotEmpty()) BotonBorde(if (catalogo) "Cerrar lista" else "Agregar efecto", { catalogo = !catalogo }, glifo = Glifos.mas)
    }

    if (catalogo || c.efectos.isEmpty()) {
        Nota("Combina varios efectos sobre fotos, videos o textos. Cada uno tiene su propio tiempo.", Modifier.padding(vertical = 8.dp))
        CatalogoEfectos { tipo ->
            val local = estado.localElegido ?: 0L
            val desde = aFotograma(local).coerceIn(0, (c.duracionUs - minimo).coerceAtLeast(0))
            val hasta = minOf(desde + 500_000L, c.duracionUs)
            val nuevo = EfectoVideo(tipo = tipo, inicioUs = desde, finUs = hasta)
            estado.cambiarClip { it.copy(efectos = it.efectos + nuevo) }
            indice = c.efectos.size
            catalogo = false
            ver(desde + (hasta - desde) / 3)
        }
        if (c.efectos.isEmpty()) return
        Spacer(Modifier.height(8.dp))
    }

    Etiqueta("Tramos en este bloque · ${tiempoEfecto(c.duracionUs)}")
    c.efectos.forEachIndexed { i, e ->
        TramoEfecto(c, e, i + 1, i == elegido, estado.localElegido) {
            indice = i
            ver(e.inicioUs + e.duracionUs / 3)
        }
    }
    if (efecto == null) return

    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(efecto.tipo.nombre, style = MaterialTheme.typography.titleSmall, color = Ink.White, modifier = Modifier.weight(1f))
        BotonIcono(Glifos.duplicar, {
            estado.cambiarClip { it.copy(efectos = it.efectos + efecto.copy()) }
            indice = c.efectos.size
        })
        BotonIcono(Glifos.papelera, {
            estado.cambiarClip { actual -> actual.copy(efectos = actual.efectos.filterIndexed { i, _ -> i != elegido }) }
            indice = (elegido - 1).coerceAtLeast(0)
        })
    }
    Nota(descripcionEfecto(efecto.tipo))
    Interruptor("Efecto activo", efecto.activo, { activo -> cambiar { it.copy(activo = activo) } }, "Apagalo para comparar conservando todos sus ajustes")

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BotonBorde("Ver inicio", { ver(efecto.inicioUs) })
        BotonLleno(if (estado.reproduciendo && repetirHasta != null) "Pausar" else "Probar", {
            if (estado.reproduciendo && repetirHasta != null) {
                estado.reproduciendo = false
                repetirHasta = null
            } else {
                estado.irA(inicioBloque + efecto.inicioUs)
                repetirHasta = inicioBloque + minOf(efecto.finUs, c.duracionUs)
                estado.reproduciendo = true
            }
        })
    }

    Etiqueta("Recorte del efecto")
    Nota("Tiempos desde el inicio del bloque · precision de 1 fotograma (30 fps)")
    BordeEfecto(
        "Inicio", efecto.inicioUs, 0L, (efecto.finUs - minimo).coerceAtLeast(0), c.duracionUs - minimo,
        onCambio = { t, arrastre -> rango(t, efecto.finUs, arrastre); ver(t) },
        onCabezal = { rango((estado.tiempo - inicioBloque).coerceIn(0, (efecto.finUs - minimo).coerceAtLeast(0)), efecto.finUs) }
    )
    BordeEfecto(
        "Fin", efecto.finUs, efecto.inicioUs + minimo, c.duracionUs, c.duracionUs,
        onCambio = { t, arrastre -> rango(efecto.inicioUs, t, arrastre); ver((t - minimo).coerceAtLeast(efecto.inicioUs)) },
        onCabezal = { rango(efecto.inicioUs, (estado.tiempo - inicioBloque).coerceIn(efecto.inicioUs + minimo, c.duracionUs)) }
    )
    BotonBorde("Usar todo el bloque", { rango(0, c.duracionUs) })

    Etiqueta("Ajustes")
    Deslizador("Intensidad", efecto.intensidad, 0f, 3f, { v -> cambiar(true) { it.copy(intensidad = v) } }, neutro = 1f, paso = 0.01f, texto = { "${(it * 100).roundToLong()}%" })
    Deslizador("Rapidez", efecto.rapidez, 0.1f, 10f, { v -> cambiar(true) { it.copy(rapidez = v) } }, neutro = 1f, escala = Escala.LOGARITMICA, texto = { "%.2fx".format(it) })
    if (efecto.tipo in setOf(TipoEfectoVideo.CIRCULO_CERRAR, TipoEfectoVideo.CIRCULO_ABRIR,
            TipoEfectoVideo.CERRAR_VERTICAL, TipoEfectoVideo.CERRAR_HORIZONTAL, TipoEfectoVideo.REVELAR_HORIZONTAL)) {
        Etiqueta("Progreso por fotograma")
        Nota("Registra puntos para reproducir una trayectoria medida. El tiempo pertenece a este efecto.")
        val base = efecto.duracionBaseUs.takeIf { it > 0 } ?: efecto.duracionUs
        val localFx = (estado.tiempo - inicioBloque).coerceIn(efecto.inicioUs, efecto.finUs)
        val fase = (((localFx - efecto.inicioUs + efecto.desfaseUs) * efecto.rapidez).toLong()).coerceIn(0, base)
        fun progreso(valor: Float, arrastre: Boolean) = cambiar(arrastre) { actual ->
            val puntos = actual.progresos.ifEmpty { listOf(ProgresoEfecto(0, 0f), ProgresoEfecto(base, 1f)) }
            actual.copy(progresos = (puntos.filter { it.tiempoUs != fase } + ProgresoEfecto(fase, valor)).sortedBy { it.tiempoUs })
        }
        Deslizador("Progreso en ${tiempoEfecto(fase)}", efecto.progresoEn(localFx), 0f, 1f,
            { v -> progreso(v, true) }, texto = { "${(it * 100).roundToLong()}%" })
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BotonBorde("Registrar punto", { progreso(efecto.progresoEn(localFx), false) })
            if (efecto.progresos.isNotEmpty()) BotonBorde("Restablecer curva", { cambiar { it.copy(progresos = emptyList()) } })
        }
        efecto.progresos.chunked(3).forEach { fila ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                fila.forEach { punto ->
                    Chip("${tiempoEfecto(punto.tiempoUs)} · ${(punto.valor * 100).roundToLong()}%", punto.tiempoUs == fase, {
                        val reloj = ((punto.tiempoUs / efecto.rapidez) - efecto.desfaseUs).toLong() + efecto.inicioUs
                        ver(reloj)
                    })
                }
            }
        }
        if (efecto.progresos.any { it.tiempoUs == fase }) BotonBorde("Quitar punto actual", {
            cambiar { it.copy(progresos = it.progresos.filter { p -> p.tiempoUs != fase }) }
        })
    }
    if (efecto.duracionUs > 0) {
        Deslizador("Entrada suave", efecto.fundidoEntradaUs / 1e6f, 0f, efecto.duracionUs / 1e6f, { v ->
            cambiar(true) { it.copy(fundidoEntradaUs = aFotograma((v * 1e6).toLong()).coerceAtMost(it.duracionUs)) }
        }, texto = { tiempoEfecto((it * 1e6).toLong()) })
        Deslizador("Salida suave", efecto.fundidoSalidaUs / 1e6f, 0f, efecto.duracionUs / 1e6f, { v ->
            cambiar(true) { it.copy(fundidoSalidaUs = aFotograma((v * 1e6).toLong()).coerceAtMost(it.duracionUs)) }
        }, texto = { tiempoEfecto((it * 1e6).toLong()) })
    }

    val local = (estado.tiempo - inicioBloque).coerceIn(0, c.duracionUs)
    Deslizador("Vista previa del bloque", local / 1e6f, 0f, c.duracionUs / 1e6f, { v -> ver(aFotograma((v * 1e6).toLong())) }, texto = { tiempoEfecto((it * 1e6).toLong()) })
    if (!efecto.activo) Nota("Este efecto esta apagado. Activalo para verlo en la vista previa y en la exportacion.")
    else if (local < efecto.inicioUs || local >= efecto.finUs) Nota("El cabezal esta fuera de este efecto. Toca Probar para verlo.")
}

@Composable
private fun BordeEfecto(
    titulo: String, valor: Long, minimo: Long, maximo: Long, alcance: Long,
    onCambio: (Long, Boolean) -> Unit, onCabezal: () -> Unit
) {
    if (alcance > 0) {
        Deslizador(titulo, valor / 1e6f, 0f, alcance / 1e6f, { v ->
            onCambio(aFotograma((v * 1e6).toLong()).coerceIn(minimo, maximo), true)
        }, texto = { tiempoEfecto((it * 1e6).toLong()) })
    } else {
        Text("$titulo  ${tiempoEfecto(valor)}", style = MaterialTheme.typography.bodyMedium, color = Ink.White70)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Chip("−1 f", false, { onCambio(Linea.tiempoDe((Linea.fotogramaEn(valor) - 1).coerceAtLeast(0)).coerceIn(minimo, maximo), false) })
        Chip("+1 f", false, { onCambio(Linea.tiempoDe(Linea.fotogramaEn(valor) + 1).coerceIn(minimo, maximo), false) })
        Chip("Al cabezal", false, onCabezal)
    }
}

@Composable
private fun TramoEfecto(c: Clip, efecto: EfectoVideo, numero: Int, elegido: Boolean, local: Long?, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(esquinas(8.dp))
            .background(if (elegido) Ink.White08 else Ink.Black)
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("$numero · ${efecto.tipo.nombre}${if (!efecto.activo) " · Apagado" else ""}", style = MaterialTheme.typography.bodySmall.copy(fontWeight = if (elegido) FontWeight.Medium else FontWeight.Normal), color = if (efecto.activo) Ink.White90 else Ink.White50, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text("${tiempoEfecto(efecto.inicioUs)} — ${tiempoEfecto(efecto.finUs)}", style = MaterialTheme.typography.bodySmall, color = Ink.White50)
        }
        Canvas(Modifier.fillMaxWidth().height(14.dp).padding(top = 5.dp)) {
            val ancho = size.width
            val h = 4.dp.toPx()
            val y = (size.height - h) / 2
            drawRoundRect(Ink.White08, Offset(0f, y), Size(ancho, h), CornerRadius(h))
            val x0 = ancho * (efecto.inicioUs.toFloat() / c.duracionUs).coerceIn(0f, 1f)
            val x1 = ancho * (efecto.finUs.toFloat() / c.duracionUs).coerceIn(0f, 1f)
            val tinta = if (!efecto.activo) Ink.White30 else if (elegido) Ink.White else Ink.White70
            drawRoundRect(tinta, Offset(x0, y), Size((x1 - x0).coerceAtLeast(1f), h), CornerRadius(h))
            if (elegido) {
                drawCircle(tinta, 3.dp.toPx(), Offset(x0, y + h / 2))
                drawCircle(tinta, 3.dp.toPx(), Offset(x1, y + h / 2))
            }
            if (local != null) {
                val x = ancho * local.toFloat() / c.duracionUs
                drawLine(Ink.White, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
            }
        }
    }
}

@Composable
private fun CatalogoEfectos(onAgregar: (TipoEfectoVideo) -> Unit) {
    val grupos = listOf(
        "Movimiento" to listOf(TipoEfectoVideo.SACUDIDA, TipoEfectoVideo.SACUDIDA_VERTICAL, TipoEfectoVideo.ZOOM_RITMO, TipoEfectoVideo.TWITCH),
        "Luz" to listOf(TipoEfectoVideo.FLASH, TipoEfectoVideo.FLASH_NEGRO, TipoEfectoVideo.ESTROBO),
        "Imagen" to listOf(TipoEfectoVideo.RGB, TipoEfectoVideo.NEGATIVO, TipoEfectoVideo.DESENFOQUE, TipoEfectoVideo.DESENFOQUE_HORIZONTAL, TipoEfectoVideo.DESENFOQUE_VERTICAL, TipoEfectoVideo.BORDES_ESPEJO, TipoEfectoVideo.GLITCH),
        "Cierres y mascaras" to listOf(TipoEfectoVideo.CIRCULO_CERRAR, TipoEfectoVideo.CIRCULO_ABRIR, TipoEfectoVideo.REVELAR_HORIZONTAL, TipoEfectoVideo.CERRAR_VERTICAL, TipoEfectoVideo.CERRAR_HORIZONTAL)
    )
    grupos.forEach { (nombre, tipos) ->
        Etiqueta(nombre)
        Opciones(tipos, null, { it.nombre }, onAgregar)
    }
}

private fun aFotograma(us: Long): Long = Linea.tiempoDe((us.coerceAtLeast(0) * Linea.FPS / 1_000_000.0).roundToLong())

private fun tiempoEfecto(us: Long): String = "%.3f s".format(us / 1_000_000.0)

private fun descripcionEfecto(tipo: TipoEfectoVideo): String = when (tipo) {
    TipoEfectoVideo.SACUDIDA -> "Vibracion de camara con desplazamiento y giro."
    TipoEfectoVideo.SACUDIDA_VERTICAL -> "Golpes de camara de arriba abajo."
    TipoEfectoVideo.ZOOM_RITMO -> "Pulsos de acercamiento. Ajusta la rapidez para acompasarlos con la musica."
    TipoEfectoVideo.FLASH -> "Destello blanco que se desvanece durante el tramo."
    TipoEfectoVideo.FLASH_NEGRO -> "Destello oscuro que se desvanece durante el tramo."
    TipoEfectoVideo.ESTROBO -> "Destellos blancos repetidos. La rapidez controla su frecuencia."
    TipoEfectoVideo.RGB -> "Separa los canales de color en los bordes de la imagen."
    TipoEfectoVideo.NEGATIVO -> "Invierte los colores con intensidad regulable."
    TipoEfectoVideo.DESENFOQUE -> "Suaviza la imagen. Combinalo con entrada y salida suaves."
    TipoEfectoVideo.DESENFOQUE_HORIZONTAL -> "Estela lateral para barridos rapidos. Conserva el desenfoque de movimiento."
    TipoEfectoVideo.DESENFOQUE_VERTICAL -> "Estela vertical para entradas y salidas rapidas."
    TipoEfectoVideo.BORDES_ESPEJO -> "Repite los bordes en espejo durante el movimiento para cubrir el lienzo."
    TipoEfectoVideo.CIRCULO_CERRAR -> "Cierra la imagen en un circulo y descubre la capa que ya se mueve debajo."
    TipoEfectoVideo.CIRCULO_ABRIR -> "Un circulo central se encoge para descubrir la imagen. Usa otra capa o fondo debajo."
    TipoEfectoVideo.REVELAR_HORIZONTAL -> "Descubre la imagen de izquierda a derecha. Registra puntos de progreso para controlar la velocidad del borde."
    TipoEfectoVideo.CERRAR_VERTICAL -> "Aplasta la imagen en una banda horizontal y revela la capa inferior."
    TipoEfectoVideo.CERRAR_HORIZONTAL -> "Aplasta la imagen en una banda vertical y revela la capa inferior."
    TipoEfectoVideo.GLITCH -> "Distorsion digital con cortes y separacion de color."
    TipoEfectoVideo.TWITCH -> "Tirones cortos y rapidos para remarcar un golpe."
}
