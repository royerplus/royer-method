package com.royermethod.app.editor.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.royermethod.app.editor.modelo.Edicion
import com.royermethod.app.editor.modelo.Linea
import com.royermethod.app.editor.modelo.PlanExportacion
import com.royermethod.app.editor.modelo.Proyecto
import com.royermethod.app.ui.components.BotonTexto
import com.royermethod.app.ui.components.Deslizador
import com.royermethod.app.ui.components.Etiqueta
import com.royermethod.app.ui.components.Glifos
import com.royermethod.app.ui.components.Interruptor
import com.royermethod.app.ui.components.Nota
import com.royermethod.app.ui.components.Segmentos

/*
 * Lo que hace la pantalla de Mejorar, pero dentro del editor y bloque a
 * bloque: generar los fotogramas que faltan, cuanto pesa cada bloque y a que
 * resolucion sale todo.
 */

// ----------------------------------------------------------------------
// Fluidez
// ----------------------------------------------------------------------

private val LENTAS = listOf(1f, 0.5f, 1f / 3f, 0.25f, 0.125f)
private val NOMBRES_LENTAS = listOf("Normal", "0.5x", "0.33x", "0.25x", "0.125x")

/** El consejo de la camara muy lenta sale una vez por sesion, no cada vez que se toca. */
private var consejoLentaVisto = false

/**
 * Generar los fotogramas que faltan y, con ellos, camara lenta de verdad. A
 * cuantos fps va el video se dice en la barra de la hoja.
 */
@Composable
fun PanelFluidez(estado: EstadoEditor) {
    val c = estado.clipElegido ?: return
    Interruptor("Generar fotogramas", c.fluido, { v ->
        estado.cambiarClip { it.copy(fluido = v) }
        if (v) estado.avisar("Se dibujan los fotogramas que faltan: 30 fps sin tirones y cámara lenta de verdad. Exporta más despacio")
    })
    Etiqueta("Cámara lenta")
    Segmentos(LENTAS, LENTAS.firstOrNull { kotlin.math.abs(it - c.velocidad) < 0.01f }, { NOMBRES_LENTAS[LENTAS.indexOf(it)] }, { v ->
        // Ir mas lento sin generar fotogramas repite los mismos: aqui va siempre con Fluido
        estado.cambiarClip { it.conVelocidad(v).copy(fluido = it.fluido || v < 1f) }
        if (v < 0.4f && !consejoLentaVisto) {
            consejoLentaVisto = true
            estado.avisar("Cuanto más lenta, más fotogramas hay que inventar. Con movimientos muy rápidos, 0.5x es lo que mejor sale")
        }
    })
}

/** A cuantos fotogramas por segundo va el archivo del bloque elegido, para la barra de la hoja. */
internal fun fpsElegido(estado: EstadoEditor): String? =
    estado.clipElegido?.origen?.fps?.takeIf { it > 0f }?.let { "Video a ${"%.0f".format(it)} fps" }

// ----------------------------------------------------------------------
// Compresion
// ----------------------------------------------------------------------

private val PESOS = listOf(0.5f to "Mínimo", 0.75f to "Ligero", 1f to "Normal", 1.5f to "Alta")

/**
 * Cuantos bits se lleva este bloque: bajar un fondo, subir el plano que tiene
 * que verse perfecto. Lo que se mueve ya se lleva mas bits solo.
 */
@Composable
fun PanelCompresion(estado: EstadoEditor) {
    val c = estado.clipElegido ?: return
    val p = estado.proyecto
    Deslizador(
        "Calidad", c.calidad, 0.3f, 2f,
        { v -> estado.cambiarClip(true) { it.copy(calidad = v) } },
        neutro = 1f, paso = 0.05f, texto = { "${(it * 100).toInt()}%" }
    )
    Segmentos(PESOS, PESOS.firstOrNull { kotlin.math.abs(it.first - c.calidad) < 0.01f }, { it.second }, { (v, _) ->
        estado.cambiarClip { it.copy(calidad = v) }
    })
    val (bloque, total) = remember(p, c.id) { pesoDe(p, c.id) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Nota("Este bloque ≈ ${mb(bloque)}  ·  Video ≈ ${mb(total)}", Modifier.weight(1f))
        BotonTexto("Usar en todos", {
            val v = c.calidad
            estado.hacer { q ->
                q.copy(
                    principal = q.principal.map { it.copy(calidad = v) },
                    capas = q.capas.map { it.copy(clip = it.clip.copy(calidad = v)) }
                )
            }
            estado.avisar("Todos los bloques al ${(v * 100).toInt()}%")
        }, glifo = Glifos.duplicar)
    }
}

/** Bytes que se lleva el tramo del bloque [id] y el video entero, segun el plan de exportacion. */
private fun pesoDe(p: Proyecto, id: Long): Pair<Long, Long> {
    val plan = PlanExportacion.de(p)
    val c = Edicion.clip(p, id) ?: return 0L to plan.bytesEstimados
    val ini = Edicion.inicio(p, id) ?: return 0L to plan.bytesEstimados
    var bits = 0.0
    var n = Linea.fotogramaDe(ini)
    val fin = ini + c.duracionUs
    while (Linea.tiempoDe(n) < fin && n < plan.fotogramas) {
        bits += plan.bitrateEn(n).toDouble() / Linea.FPS
        n++
    }
    return (bits / 8).toLong() to plan.bytesEstimados
}

private fun mb(bytes: Long): String = if (bytes < 1_000_000) "${(bytes / 1000).coerceAtLeast(1)} KB" else "%.1f MB".format(bytes / 1e6)

// ----------------------------------------------------------------------
// Resolucion
// ----------------------------------------------------------------------

/** A que resolucion sale todo el video. Lo que mide en pixeles va en la barra de la hoja. */
@Composable
fun PanelResolucion(estado: EstadoEditor) {
    val p = estado.proyecto
    val lado = p.salida.ladoCorto
    Segmentos(RESOLUCIONES, lado, { "${it}p" }, { l -> estado.hacer { it.copy(salida = it.salida.copy(ladoCorto = l)) } })
    // Lo unico que hay que saber antes de subirla: lo que es mas chico se agranda y pierde filo
    val archivos = (p.principal + p.capas.map { it.clip }).mapNotNull { it.origen }.filter { it.ancho > 0 && it.alto > 0 }.distinctBy { it.uri }
    val chicos = archivos.count { minOf(it.ancho, it.alto) < lado }
    if (chicos > 0) Nota(
        if (chicos == 1) "Un archivo es más chico que esto: se agranda y se verá algo más blando."
        else "$chicos archivos son más chicos que esto: se agrandan y se verán algo más blandos.",
        Modifier.padding(top = 2.dp)
    )
}

/** Lo que mide el video que sale, para la barra de la hoja. */
internal fun medidasSalida(p: Proyecto): String = p.formato.medidas(p.salida.ladoCorto).let { (w, h) -> "$w × $h px" }
