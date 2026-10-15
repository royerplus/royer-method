package com.royermethod.app.editor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.royermethod.app.editor.modelo.Calidad
import com.royermethod.app.editor.modelo.Linea
import com.royermethod.app.editor.modelo.PlanExportacion
import com.royermethod.app.ui.theme.Ink

/** Como va la exportacion, contado por quien la hace. */
data class EstadoExportar(
    val corriendo: Boolean = false,
    val fraccion: Float = 0f,
    /** null mientras no se pueda calcular. */
    val restanteMs: Long? = null,
    val resultado: ResultadoExportar? = null,
    val fallo: String? = null
)

data class ResultadoExportar(
    val bytes: Long,
    val ancho: Int,
    val alto: Int,
    val codec: String,
    /** Lo que se midio en el archivo: fotogramas, fps, saltos. */
    val verificacion: String,
    val perfecta: Boolean
)

private val LADOS = listOf(480, 576, 720, 1080)

fun peso(bytes: Long): String = when {
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
    else -> "%d KB".format((bytes / 1000).coerceAtLeast(1))
}

/**
 * Exportar: elegir calidad, ver cuanto va a pesar antes de empezar, seguir
 * el avance y ver lo que de verdad salio (fotogramas medidos en el archivo).
 */
@Composable
fun PanelExportar(
    estado: EstadoEditor,
    exportar: EstadoExportar,
    onExportar: () -> Unit,
    onCancelar: () -> Unit,
    onCompartir: () -> Unit,
    onCerrar: () -> Unit
) {
    val p = estado.proyecto
    when {
        exportar.corriendo -> HojaPanel("Exportando", onCerrar) {
            Spacer(Modifier.height(18.dp))
            Text("${(exportar.fraccion * 100).toInt()}%", style = MaterialTheme.typography.displayLarge.copy(letterSpacing = 2.sp), color = Ink.White)
            Spacer(Modifier.height(14.dp))
            Barra(exportar.fraccion)
            Spacer(Modifier.height(10.dp))
            Text(
                exportar.restanteMs?.let { "Quedan unos ${(it / 1000).coerceAtLeast(1)} s" } ?: "Calculando…",
                style = MaterialTheme.typography.bodyMedium, color = Ink.White50
            )
            Spacer(Modifier.height(6.dp))
            Nota("Puedes salir de la app: sigue en segundo plano.", color = Ink.White30)
            Spacer(Modifier.height(18.dp))
            BotonBorde("Cancelar", onCancelar, glifo = Glifos.cerrar)
        }

        exportar.resultado != null -> HojaPanel("Listo", onCerrar) {
            val r = exportar.resultado
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icono(Glifos.listo, tam = 34.dp)
                Spacer(Modifier.size(12.dp))
                Column {
                    Text("Guardado en la galeria", style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 1.sp), color = Ink.White)
                    Text("${peso(r.bytes)}  ·  ${r.ancho}×${r.alto}  ·  ${r.codec}", style = MaterialTheme.typography.bodyMedium, color = Ink.White70)
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                (if (r.perfecta) "Comprobado: " else "Ojo: ") + r.verificacion,
                style = MaterialTheme.typography.bodyMedium,
                color = if (r.perfecta) Ink.White70 else Ink.Peligro
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BotonLleno("Compartir", onCompartir)
                BotonBorde("Seguir editando", onCerrar)
            }
        }

        exportar.fallo != null -> HojaPanel("No se pudo exportar", onCerrar) {
            Spacer(Modifier.height(10.dp))
            Text(exportar.fallo, style = MaterialTheme.typography.bodyMedium, color = Ink.White70)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BotonLleno("Intentar otra vez", onExportar)
                BotonBorde("Cerrar", onCerrar)
            }
        }

        else -> HojaPanel("Exportar", onCerrar) {
            val s = p.salida
            fun salida(f: (com.royermethod.app.editor.modelo.AjustesSalida) -> com.royermethod.app.editor.modelo.AjustesSalida) {
                estado.hacer { it.copy(salida = f(it.salida)) }
                AjustesEditor.salida = f(AjustesEditor.salida)
                AjustesEditor.alCambiar()
            }
            val plan = remember(p) { PlanExportacion.de(p, conAudio = p.audios.isNotEmpty() || p.principal.any { it.esVideo && it.origen?.conAudio == true && it.volumen > 0f }) }
            Etiqueta("Calidad")
            Opciones(Calidad.values().toList(), s.calidad, { it.nombre }, { c -> salida { it.copy(calidad = c) } })
            Spacer(Modifier.height(6.dp))
            Etiqueta("Resolucion")
            Opciones(LADOS, s.ladoCorto, { "${it}p" }, { l -> salida { it.copy(ladoCorto = l) } })
            Interruptor("H.265", s.hevc, { v -> salida { it.copy(hevc = v) } }, "Misma calidad con bastante menos peso. Si el telefono no lo tiene, sale en H.264")
            Interruptor("Desenfoque de movimiento", s.desenfoqueMovimiento, { v -> salida { it.copy(desenfoqueMovimiento = v) } },
                "El rastro que hace que las transiciones a 30 fps se vean fluidas")
            Spacer(Modifier.height(8.dp))
            Text(
                "≈ ${peso(plan.bytesEstimados)}",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Medium, letterSpacing = 1.sp),
                color = Ink.White
            )
            Nota("${plan.ancho}×${plan.alto}  ·  30 fps exactos  ·  ${reloj(plan.duracionUs)}  ·  ${Linea.fotogramas(p)} fotogramas", color = Ink.White50)
            Spacer(Modifier.height(14.dp))
            BotonLleno("Exportar", onExportar, Modifier.fillMaxWidth(), activo = p.principal.isNotEmpty() || p.capas.isNotEmpty())
        }
    }
}

@Composable
private fun Barra(f: Float) {
    Canvas(Modifier.fillMaxWidth().height(6.dp).padding(end = 2.dp)) {
        val r = CornerRadius(size.height)
        drawRoundRect(Ink.White15, Offset.Zero, size, r)
        drawRoundRect(Ink.Glow35, Offset(0f, -3f), Size(size.width * f, size.height + 6f), r)
        drawRoundRect(Ink.White, Offset.Zero, Size(size.width * f, size.height), r)
    }
}
