package com.royermethod.app.editor.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.royermethod.app.editor.modelo.Edicion
import com.royermethod.app.editor.modelo.Linea
import com.royermethod.app.editor.modelo.PlanExportacion
import com.royermethod.app.editor.modelo.Proyecto
import com.royermethod.app.ui.components.BotonBorde
import com.royermethod.app.ui.components.Deslizador
import com.royermethod.app.ui.components.Etiqueta
import com.royermethod.app.ui.components.Interruptor
import com.royermethod.app.ui.components.Nota
import com.royermethod.app.ui.components.Opciones
import com.royermethod.app.ui.theme.Ink

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

@Composable
fun PanelFluidez(estado: EstadoEditor) {
    val c = estado.clipElegido ?: return
    val fps = c.origen?.fps ?: 0f
    if (fps > 0f) {
        val texto = if (fps < 29f) {
            "Este video va a ${"%.0f".format(fps)} fps. Le faltan fotogramas para llegar a 30: con Fluido se generan y no hay tirones."
        } else {
            "Este video ya va a ${"%.0f".format(fps)} fps. Fluido sirve sobre todo para la camara lenta."
        }
        Text(texto, style = MaterialTheme.typography.bodyMedium, color = Ink.White70)
        Spacer(Modifier.height(6.dp))
    }
    Interruptor(
        "Generar fotogramas", c.fluido, { v -> estado.cambiarClip { it.copy(fluido = v) } },
        "El modelo dibuja los que faltan entre cada par: 30 fps exactos y camara lenta de verdad. Exporta mas despacio"
    )
    Spacer(Modifier.height(6.dp))
    Etiqueta("Camara lenta fluida")
    Opciones(LENTAS, LENTAS.firstOrNull { kotlin.math.abs(it - c.velocidad) < 0.01f }, { NOMBRES_LENTAS[LENTAS.indexOf(it)] }, { v ->
        // Ir mas lento sin generar fotogramas repite los mismos: aqui va siempre con Fluido
        estado.cambiarClip { it.conVelocidad(v).copy(fluido = it.fluido || v < 1f) }
    })
    Nota("Cuanto mas lenta, mas fotogramas tiene que inventar. Para movimientos muy rapidos, 0.5x es lo que mejor sale.")
}

// ----------------------------------------------------------------------
// Compresion
// ----------------------------------------------------------------------

private val PESOS = listOf(0.5f to "Mucho menos peso", 0.75f to "Menos peso", 1f to "Normal", 1.5f to "Mas calidad")

@Composable
fun PanelCompresion(estado: EstadoEditor) {
    val c = estado.clipElegido ?: return
    val p = estado.proyecto
    Opciones(PESOS, PESOS.firstOrNull { kotlin.math.abs(it.first - c.calidad) < 0.01f }, { it.second }, { (v, _) ->
        estado.cambiarClip { it.copy(calidad = v) }
    })
    Deslizador(
        "Bits para este bloque", c.calidad, 0.3f, 2f,
        { v -> estado.cambiarClip(true) { it.copy(calidad = v) } },
        neutro = 1f, paso = 0.05f, texto = { "${(it * 100).toInt()}%" }
    )
    val (bloque, total) = remember(p, c.id) { pesoDe(p, c.id) }
    Text(
        "Este bloque: unos ${mb(bloque)}  ·  Video entero: unos ${mb(total)}",
        style = MaterialTheme.typography.bodyMedium, color = Ink.White90
    )
    Spacer(Modifier.height(4.dp))
    Nota(
        "Solo cambia este bloque. Lo que se mueve (transiciones, animaciones) ya se lleva mas bits solo; " +
            "esto es para bajar un fondo o subir el plano que tiene que verse perfecto."
    )
    Spacer(Modifier.height(8.dp))
    BotonBorde("Usar en todos los bloques", {
        val v = c.calidad
        estado.hacer { q ->
            q.copy(
                principal = q.principal.map { it.copy(calidad = v) },
                capas = q.capas.map { it.copy(clip = it.clip.copy(calidad = v)) }
            )
        }
        estado.avisar("Todos los bloques al ${(v * 100).toInt()}%")
    })
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

@Composable
fun PanelResolucion(estado: EstadoEditor) {
    val p = estado.proyecto
    val lado = p.salida.ladoCorto
    Etiqueta("Todo el video sale a")
    Opciones(RESOLUCIONES, lado, { "${it}p" }, { l -> estado.hacer { it.copy(salida = it.salida.copy(ladoCorto = l)) } })
    val (w, h) = p.formato.medidas(lado)
    Text("$w × $h pixeles", style = MaterialTheme.typography.bodyMedium, color = Ink.White90)
    Spacer(Modifier.height(8.dp))

    val archivos = (p.principal + p.capas.map { it.clip }).mapNotNull { it.origen }.filter { it.ancho > 0 && it.alto > 0 }.distinctBy { it.uri }
    val grandes = archivos.count { minOf(it.ancho, it.alto) > lado }
    val chicos = archivos.count { minOf(it.ancho, it.alto) < lado }
    if (grandes > 0) {
        Nota(
            (if (grandes == 1) "Un archivo se baja" else "$grandes archivos se bajan") +
                " a esta resolucion con un filtro que mira todos sus pixeles, asi las lineas finas no titilan ni salen con dientes."
        )
    }
    if (chicos > 0) {
        Spacer(Modifier.height(4.dp))
        Nota(
            (if (chicos == 1) "Un archivo es mas chico" else "$chicos archivos son mas chicos") +
                " que esto y se van a agrandar: se veran algo mas blandos. Elegir una resolucion menor no les quita nada."
        )
    }
    if (archivos.isEmpty()) Nota("Todos los videos y fotos que agregues se ajustan solos a esta resolucion.")
    Spacer(Modifier.height(4.dp))
    Nota("En el telefono 720p casi no se distingue de 1080p y pesa como la mitad.")
}
