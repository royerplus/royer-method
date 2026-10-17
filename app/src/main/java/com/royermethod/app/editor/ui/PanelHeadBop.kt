package com.royermethod.app.editor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.royermethod.app.editor.modelo.Clip
import com.royermethod.app.editor.modelo.EdicionHeadBop
import com.royermethod.app.editor.modelo.HeadBop
import com.royermethod.app.editor.modelo.RolHeadBop
import com.royermethod.app.ui.components.BotonBorde
import com.royermethod.app.ui.components.Chip
import com.royermethod.app.ui.components.Deslizador
import com.royermethod.app.ui.components.Etiqueta
import kotlin.math.roundToLong

/** Controles de un rig de recortes: cada capa tiene su rol y pivote editable. */
@Composable
internal fun PanelHeadBop(estado: EstadoEditor, clip: Clip) {
    val h = clip.headBop
    Text("Head bop por capas", style = MaterialTheme.typography.titleSmall)
    Text("Alinea las partes PNG en capas. El cuello de cabeza y cabello debe coincidir en pantalla; cada parte tiene su rol y pivote.",
        style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(8.dp))
    if (h == null) {
        BotonBorde("Añadir head bop", {
            estado.reproduciendo = false
            estado.cambiarClip { it.copy(headBop = HeadBop(finUs = it.duracionUs)) }
        })
        return
    }
    fun cambiar(arrastre: Boolean = true, f: (HeadBop) -> HeadBop) =
        estado.cambiarClip(arrastre) { c -> c.copy(headBop = c.headBop?.let(f)) }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip(if (h.activo) "Activado" else "Desactivado", h.activo, { cambiar(false) { it.copy(activo = !it.activo) } })
        BotonBorde("Quitar", { estado.cambiarClip { it.copy(headBop = null) } })
    }
    Etiqueta("Parte del personaje")
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        RolHeadBop.entries.forEach { rol -> Chip(rol.nombre, h.rol == rol, { cambiar(false) { it.copy(rol = rol) } }) }
    }
    Deslizador("Tempo", h.bpm.coerceIn(30f, 240f), 30f, 240f,
        { v -> cambiar { it.copy(bpm = v) } }, texto = { "%.1f BPM".format(it) })
    Deslizador("Intensidad", h.intensidad, 0f, 3f,
        { v -> cambiar { it.copy(intensidad = v) } }, neutro = 1f, texto = { "%.2f×".format(it) })
    Deslizador("Giro", h.giro, 0f, 30f,
        { v -> cambiar { it.copy(giro = v) } }, neutro = 7f, texto = { "%.1f°".format(it) })
    Deslizador("Rebote vertical", h.rebote, 0f, 0.15f,
        { v -> cambiar { it.copy(rebote = v) } }, neutro = 0.025f, texto = { "%.1f %%".format(it * 100) })
    Deslizador("Naturalidad", h.naturalidad, 0f, 1f,
        { v -> cambiar { it.copy(naturalidad = v) } }, neutro = 0f, texto = { "%.0f %%".format(it * 100) })
    Text("Añade anticipación y acentos al movimiento. Copia el ritmo para mantener unidos cabeza y cabello.",
        style = MaterialTheme.typography.bodySmall)
    Deslizador("Desfase musical", h.desfaseUs / 1_000_000f, -2f, 2f,
        { v -> cambiar { it.copy(desfaseUs = (v * 1_000_000).roundToLong()) } },
        neutro = 0f, texto = { "%+.3f s".format(it) })
    BotonBorde("Copiar ritmo a todas las capas head bop", {
        estado.hacer { EdicionHeadBop.sincronizar(it, clip.id) }
        estado.avisar("Ritmo sincronizado; cada parte conserva su pivote e intensidad")
    })

    if (h.rol == RolHeadBop.CABEZA || h.rol == RolHeadBop.CABELLO) {
        Etiqueta("Cuello · mismo punto en cabeza y cabello")
        PivoteHeadBop(h.cuelloX, h.cuelloY, { x -> cambiar { it.copy(cuelloX = x) } }, { y -> cambiar { it.copy(cuelloY = y) } })
    }
    if (h.rol != RolHeadBop.CABEZA) {
        Etiqueta(if (h.rol == RolHeadBop.CABELLO) "Raíz del cabello" else "Pivote de la parte")
        PivoteHeadBop(h.pivoteX, h.pivoteY, { x -> cambiar { it.copy(pivoteX = x) } }, { y -> cambiar { it.copy(pivoteY = y) } })
    }
    if (h.rol == RolHeadBop.CABELLO) {
        Deslizador("Balanceo secundario", h.arrastre, 0f, 3f,
            { v -> cambiar { it.copy(arrastre = v) } }, neutro = 1f, texto = { "%.2f×".format(it) })
        Deslizador("Retardo del cabello", h.retardoUs / 1_000f, 0f, 500f,
            { v -> cambiar { it.copy(retardoUs = (v * 1_000).roundToLong()) } }, texto = { "${it.toInt()} ms" })
    }
    Etiqueta("Tramo dentro del clip")
    val duracion = clip.duracionUs.coerceAtLeast(1)
    val inicio = h.inicioUs.coerceIn(0, duracion - 1)
    val fin = h.finUs.coerceIn(inicio + 1, duracion)
    TiempoFotogramas("Inicio", inicio, 0, duracion - 1) { t, arrastre ->
        cambiar(arrastre) { it.conRango(t, maxOf(fin, t + 1)) }
    }
    TiempoFotogramas("Fin", fin, inicio + 1, duracion) { t, arrastre ->
        cambiar(arrastre) { it.conRango(inicio, t) }
    }
    val largo = fin - inicio
    TiempoFotogramas("Mover tramo", inicio, 0, duracion - largo) { t, arrastre ->
        cambiar(arrastre) { it.copy(inicioUs = inicio, finUs = fin).movido(t) }
    }
    Deslizador("Entrada y salida suaves", h.fundidoUs / 1_000f, 0f, 500f,
        { v -> cambiar { it.copy(fundidoUs = (v * 1_000).roundToLong()) } }, texto = { "${it.toInt()} ms" })
}

@Composable
private fun PivoteHeadBop(x: Float, y: Float, cambiarX: (Float) -> Unit, cambiarY: (Float) -> Unit) {
    Text("0–100 %: dentro del PNG. También puedes situarlo fuera de la pieza.", style = MaterialTheme.typography.bodySmall)
    Deslizador("Horizontal", x, -1f, 2f, cambiarX, neutro = 0.5f, texto = { "%.1f %%".format(it * 100) })
    Deslizador("Vertical", y, -1f, 2f, cambiarY, neutro = 0.5f, texto = { "%.1f %%".format(it * 100) })
}
