package com.royermethod.app.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.royermethod.app.editor.modelo.Curva
import com.royermethod.app.editor.modelo.AjusteTransicion
import com.royermethod.app.editor.modelo.Edicion
import com.royermethod.app.editor.modelo.Transicion
import com.royermethod.app.ui.components.BotonBorde
import com.royermethod.app.ui.components.Cuadricula
import com.royermethod.app.ui.components.LocalGestos
import com.royermethod.app.ui.components.Deslizador
import com.royermethod.app.ui.components.Etiqueta
import com.royermethod.app.ui.components.Glifos
import com.royermethod.app.ui.components.Icono
import com.royermethod.app.ui.components.Mosaico
import com.royermethod.app.ui.components.Muestras
import com.royermethod.app.ui.components.Opciones
import com.royermethod.app.ui.components.esquinas
import com.royermethod.app.ui.theme.Ink
import kotlin.math.min

private val CON_DIRECCION = setOf(Transicion.EMPUJAR, Transicion.DESLIZAR, Transicion.LINEA, Transicion.BARRIDO)
private val CON_COLOR = setOf(Transicion.LINEA, Transicion.DESTELLO, Transicion.IMPACTO)
private val CON_FUERZA = setOf(
    Transicion.LINEA, Transicion.DESLIZAR, Transicion.ZOOM, Transicion.DESENFOQUE,
    Transicion.GIRO, Transicion.ZOOM_GIRO, Transicion.IMPACTO
)

private val COLORES_LUZ = listOf(0xFFFFFFFF, 0xFFFFE9B0, 0xFFFF3B5C, 0xFFFF9F1C, 0xFF5BFF8F, 0xFF2EC4FF, 0xFF7B61FF, 0xFFFF6AD5).map { it.toInt() }

/** La transicion que sale del bloque elegido hacia el siguiente. */
@Composable
fun PanelTransicion(estado: EstadoEditor) {
    val p = estado.proyecto
    val i = p.principal.indexOfFirst { it.id == estado.idElegido }
    if (i < 0 || i + 1 >= p.principal.size) return SinNada("El ultimo bloque no tiene transicion")
    val a = p.principal[i]
    val b = p.principal[i + 1]
    val id = a.id
    fun cambiar(arrastre: Boolean = false, f: (com.royermethod.app.editor.modelo.Clip) -> com.royermethod.app.editor.modelo.Clip) {
        val g: (com.royermethod.app.editor.modelo.Proyecto) -> com.royermethod.app.editor.modelo.Proyecto = { Edicion.cambiar(it, id, f) }
        if (arrastre) estado.ajustar(g) else estado.hacer(g)
    }
    // Una transicion no puede comerse mas de la mitad de ninguno de los dos
    val maximo = (min(a.duracionUs, b.duracionUs) / 2).coerceAtLeast(100_000L)

    Cuadricula(Transicion.values().toList()) { t, mod ->
        Mosaico(t.nombre, a.transicion == t, {
            cambiar {
                val dur = if (it.transicion == Transicion.NINGUNA) AjustesEditor.duracionTransicionUs else it.duracionTransicionUs
                // Los barridos empiezan algo inclinados; si ya se giro a mano, se respeta
                val aj = it.ajusteTransicion
                val barrido = t == Transicion.LINEA || t == Transicion.BARRIDO
                val recto = aj.angulo % 90f == 0f
                val angulo = if (barrido && recto) AjusteTransicion.ANGULO_BARRIDO else aj.angulo
                it.copy(transicion = t, duracionTransicionUs = dur.coerceAtMost(maximo), ajusteTransicion = aj.copy(angulo = angulo))
            }
            // Que se vea entera: el cabezal salta a un poco antes
            val ini = com.royermethod.app.editor.modelo.Linea.inicios(estado.proyecto.principal)
            estado.irA((ini[i + 1] - 200_000).coerceAtLeast(0))
        }, mod) { col -> dibujoTransicion(t, col) }
    }
    if (a.transicion == Transicion.NINGUNA) {
        Spacer(Modifier.height(10.dp))
        Text("Corte seco: el siguiente bloque entra de golpe.", style = MaterialTheme.typography.bodyMedium, color = Ink.White50)
        return
    }
    val aj = a.ajusteTransicion
    Spacer(Modifier.height(4.dp))
    Deslizador(
        "Duracion", a.duracionTransicionUs.coerceAtMost(maximo) / 1e6f, 0.1f, maximo / 1e6f,
        { v -> cambiar(true) { it.copy(duracionTransicionUs = (v * 1e6).toLong()) } },
        neutro = 0.1f, paso = 0.1f, texto = { "%.1f s".format(it) }
    )
    Etiqueta("Ritmo")
    Opciones(Curva.values().toList(), aj.curva, { it.nombre }, { c -> cambiar { it.copy(ajusteTransicion = it.ajusteTransicion.copy(curva = c)) } })
    if (a.transicion in CON_DIRECCION) {
        Spacer(Modifier.height(6.dp))
        Etiqueta("Hacia donde")
        RuedaDireccion(
            aj.angulo,
            barrido = a.transicion == Transicion.LINEA || a.transicion == Transicion.BARRIDO,
            color = if (a.transicion in CON_COLOR) Color(aj.color) else Ink.White,
            onCambio = { g -> cambiar(true) { it.copy(ajusteTransicion = it.ajusteTransicion.copy(angulo = g)) } }
        )
    }
    if (a.transicion in CON_COLOR) {
        Etiqueta("Color de la luz")
        Muestras(COLORES_LUZ, aj.color, { v -> cambiar { it.copy(ajusteTransicion = it.ajusteTransicion.copy(color = v)) } })
    }
    if (a.transicion in CON_FUERZA) {
        Deslizador(
            if (a.transicion == Transicion.LINEA) "Grosor" else "Fuerza", aj.intensidad, 0.2f, 2.5f,
            { v -> cambiar(true) { it.copy(ajusteTransicion = it.ajusteTransicion.copy(intensidad = v)) } },
            neutro = 1f, texto = { "${(it * 100).toInt()}%" }
        )
    }
    Spacer(Modifier.height(8.dp))
    BotonBorde("Usar en todas las uniones", {
        estado.hacer { q ->
            q.copy(principal = q.principal.mapIndexed { j, c ->
                if (j == q.principal.lastIndex) c else c.copy(
                    transicion = a.transicion,
                    duracionTransicionUs = a.duracionTransicionUs,
                    ajusteTransicion = a.ajusteTransicion
                )
            })
        }
        estado.avisar("${a.transicion.nombre} en todas las uniones")
    })
}

/**
 * La direccion a cualquier angulo: una linea desde el centro con un punto en
 * la punta. Arrastrando el punto (o tocando el aro) la linea gira entera, y
 * cerca de las ocho direcciones rectas se queda pegada para acertarlas sin
 * esfuerzo.
 *
 * En los barridos se dibuja tambien la linea de corte, perpendicular, que es
 * la que se ve cruzar la imagen.
 */
@Composable
private fun RuedaDireccion(angulo: Float, barrido: Boolean, color: Color, onCambio: (Float) -> Unit) {
    val gestos = LocalGestos.current
    val cambio by rememberUpdatedState(onCambio)
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(
            Modifier
                .size(132.dp)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val abajo = awaitFirstDown()
                        gestos.empezar()
                        fun anguloEn(p: Offset): Float {
                            val c = Offset(size.width / 2f, size.height / 2f)
                            var g = Math.toDegrees(kotlin.math.atan2((p.y - c.y).toDouble(), (p.x - c.x).toDouble())).toFloat()
                            g = (g + 360f) % 360f
                            val recto = Math.round(g / 45f) * 45f
                            if (kotlin.math.abs(g - recto) < 4f) g = recto % 360f
                            return Math.round(g).toFloat() % 360f
                        }
                        try {
                            cambio(anguloEn(abajo.position))
                            abajo.consume()
                            while (true) {
                                val ev = awaitPointerEvent()
                                val c = ev.changes.firstOrNull { it.id == abajo.id } ?: break
                                if (!c.pressed) break
                                cambio(anguloEn(c.position))
                                c.consume()
                            }
                        } finally {
                            gestos.terminar()
                        }
                    }
                }
        ) {
            val c = center
            val r = size.minDimension / 2f - 12.dp.toPx()
            drawCircle(Ink.White08, r + 10.dp.toPx(), c)
            drawCircle(Ink.White15, r, c, style = Stroke(1.dp.toPx()))
            // Marcas de las ocho direcciones rectas
            for (k in 0 until 8) {
                val g = Math.toRadians(k * 45.0)
                val d = Offset(kotlin.math.cos(g).toFloat(), kotlin.math.sin(g).toFloat())
                val largo = if (k % 2 == 0) 6.dp.toPx() else 3.dp.toPx()
                drawLine(Ink.White50, c + d * (r - largo), c + d * r, 1.dp.toPx(), StrokeCap.Round)
            }
            val g = Math.toRadians(angulo.toDouble())
            val d = Offset(kotlin.math.cos(g).toFloat(), kotlin.math.sin(g).toFloat())
            if (barrido) {
                // El borde del barrido: perpendicular a hacia donde avanza
                val n = Offset(-d.y, d.x)
                drawLine(color.copy(alpha = 0.55f), c - n * (r * 0.8f), c + n * (r * 0.8f), 2.dp.toPx(), StrokeCap.Round)
            }
            drawLine(Ink.White30, c - d * r, c, 1.5.dp.toPx(), StrokeCap.Round)
            drawLine(Ink.White, c, c + d * r, 2.5.dp.toPx(), StrokeCap.Round)
            drawCircle(Ink.White, 3.dp.toPx(), c)
            drawCircle(Ink.White, 11.dp.toPx(), c + d * r)
            drawCircle(Ink.Black, 4.dp.toPx(), c + d * r)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text("${angulo.toInt()}°", style = MaterialTheme.typography.headlineSmall, color = Ink.White)
            Text(
                "Arrastra el punto para girar la direccion",
                style = MaterialTheme.typography.bodyMedium, color = Ink.White50
            )
        }
    }
}

/** Dos rectangulos (lo que sale y lo que entra) y como pasan. */
private fun DrawScope.dibujoTransicion(t: Transicion, col: Color) {
    val w = size.width
    val h = size.height * 0.72f
    val y = (size.height - h) / 2
    val radio = CornerRadius(3.dp.toPx())
    val tenue = col.copy(alpha = 0.3f)
    val trazo = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round)
    when (t) {
        Transicion.NINGUNA -> {
            drawRoundRect(tenue, Offset(0f, y), Size(w * 0.47f, h), radio)
            drawRoundRect(col, Offset(w * 0.53f, y), Size(w * 0.47f, h), radio, style = trazo)
        }
        Transicion.FUNDIDO -> drawRoundRect(Brush.horizontalGradient(listOf(tenue, col)), Offset(0f, y), Size(w, h), radio)
        Transicion.NEGRO -> drawRoundRect(Brush.horizontalGradient(listOf(tenue, Color.Black, col)), Offset(0f, y), Size(w, h), radio)
        Transicion.DESTELLO -> {
            drawRoundRect(Brush.horizontalGradient(listOf(tenue, Color.White, col)), Offset(0f, y), Size(w, h), radio)
        }
        Transicion.LINEA, Transicion.BARRIDO -> {
            clipRect(top = y, bottom = y + h) {
                val path = Path().apply { moveTo(0f, y); lineTo(w * 0.62f, y); lineTo(w * 0.42f, y + h); lineTo(0f, y + h); close() }
                drawRoundRect(tenue, Offset(0f, y), Size(w, h), radio)
                drawPath(path, col)
                if (t == Transicion.LINEA) drawLine(Color.White, Offset(w * 0.62f, y), Offset(w * 0.42f, y + h), 2.5.dp.toPx())
            }
        }
        Transicion.EMPUJAR -> {
            drawRoundRect(tenue, Offset(-w * 0.35f, y), Size(w * 0.8f, h), radio)
            drawRoundRect(col, Offset(w * 0.5f, y), Size(w * 0.8f, h), radio)
        }
        Transicion.DESLIZAR -> {
            drawRoundRect(tenue, Offset(w * 0.06f, y + h * 0.06f), Size(w * 0.88f, h * 0.88f), radio)
            drawRoundRect(col, Offset(w * 0.45f, y), Size(w * 0.8f, h), radio)
        }
        Transicion.ZOOM -> {
            drawRoundRect(tenue, Offset(0f, y), Size(w, h), radio)
            drawRoundRect(col, Offset(w * 0.25f, y + h * 0.25f), Size(w * 0.5f, h * 0.5f), radio)
        }
        Transicion.DESENFOQUE -> {
            for (k in 4 downTo 0) drawRoundRect(col.copy(alpha = 0.12f + 0.1f * (4 - k)), Offset(k * 2f, y + k * 2f), Size(w - k * 4f, h - k * 4f), radio)
        }
        Transicion.GIRO -> rotate(-20f) {
            drawRoundRect(tenue, Offset(0f, y), Size(w, h), radio)
            drawRoundRect(col, Offset(w * 0.2f, y + h * 0.2f), Size(w * 0.6f, h * 0.6f), radio, style = trazo)
        }
        Transicion.CIRCULO -> {
            drawRoundRect(tenue, Offset(0f, y), Size(w, h), radio)
            drawCircle(col, h * 0.38f, center)
        }
        Transicion.ZOOM_GIRO -> {
            drawRoundRect(tenue, Offset(0f, y), Size(w, h), radio)
            rotate(25f) { drawRoundRect(col, Offset(w * 0.3f, y + h * 0.3f), Size(w * 0.4f, h * 0.4f), radio) }
        }
        Transicion.IMPACTO -> {
            drawRoundRect(tenue, Offset(0f, y), Size(w, h), radio)
            val c = center
            for (k in 0 until 8) {
                val ang = Math.toRadians(k * 45.0)
                val d = Offset(kotlin.math.cos(ang).toFloat(), kotlin.math.sin(ang).toFloat())
                drawLine(col, c + d * (h * 0.15f), c + d * (h * 0.42f), 1.6.dp.toPx(), StrokeCap.Round)
            }
        }
    }
    if (t == Transicion.NINGUNA) return
    drawRoundRect(col.copy(alpha = 0.5f), Offset(0f, y), Size(w, h), radio, style = Stroke(1.dp.toPx()))
}
