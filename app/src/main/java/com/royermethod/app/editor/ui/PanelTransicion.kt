package com.royermethod.app.editor.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.royermethod.app.editor.modelo.AjusteTransicion
import com.royermethod.app.editor.modelo.Clip
import com.royermethod.app.editor.modelo.Curva
import com.royermethod.app.editor.modelo.Edicion
import com.royermethod.app.editor.modelo.Linea
import com.royermethod.app.editor.modelo.LuzTransicion
import com.royermethod.app.editor.modelo.Proyecto
import com.royermethod.app.editor.modelo.Transicion
import com.royermethod.app.ui.components.BotonTexto
import com.royermethod.app.ui.components.Chip
import com.royermethod.app.ui.components.ChipInterruptor
import com.royermethod.app.ui.components.Deslizador
import com.royermethod.app.ui.components.Fila
import com.royermethod.app.ui.components.Mosaico
import com.royermethod.app.ui.components.Muestras
import com.royermethod.app.ui.components.Nota
import com.royermethod.app.ui.components.SeccionAjustes
import com.royermethod.app.ui.components.Tira
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private val CON_DIRECCION = setOf(Transicion.EMPUJAR, Transicion.DESLIZAR, Transicion.LINEA, Transicion.BARRIDO, Transicion.APLASTAR)
private val CON_COLOR = setOf(Transicion.LINEA, Transicion.DESTELLO, Transicion.IMPACTO)
private val CON_FUERZA = setOf(
    Transicion.LINEA, Transicion.DESLIZAR, Transicion.ZOOM, Transicion.DESENFOQUE,
    Transicion.GIRO, Transicion.ZOOM_GIRO, Transicion.IMPACTO, Transicion.APLASTAR
)

private val COLORES_LUZ = listOf(0xFFFFFFFF, 0xFFFFE9B0, 0xFFFF3B5C, 0xFFFF9F1C, 0xFF5BFF8F, 0xFF2EC4FF, 0xFF7B61FF, 0xFFFF6AD5).map { it.toInt() }

/** Los estilos hechos de la linea de luz. */
private val LUCES_HECHAS = listOf(
    "Clásico" to LuzTransicion(),
    "Neón" to LuzTransicion(halo = 2.3f, nucleo = .85f),
    "Giro violeta" to LuzTransicion(girar = true, anguloMedio = 0f, anguloFinal = 90f, halo = 2.6f, textura = .6f),
    "Aurora" to LuzTransicion(girar = true, anguloMedio = 0f, anguloFinal = 90f, halo = 3.5f, nucleo = .6f, textura = .85f, arcoiris = true)
)

/**
 * La transicion que sale del bloque elegido hacia el siguiente. Arriba, todas
 * en una tira que no se va al deslizar; debajo, lo que se puede afinar de la
 * elegida. Con "Ninguna" la hoja es solo la tira.
 */
@Composable
internal fun HojaTransicion(estado: EstadoEditor, cerrar: () -> Unit) {
    val p = estado.proyecto
    val i = p.principal.indexOfFirst { it.id == estado.idElegido }
    if (i < 0 || i + 1 >= p.principal.size) {
        HojaPanel(Panel.TRANSICION.titulo, cerrar, corta = true) { SinNada("El último bloque no tiene transición") }
        return
    }
    val a = p.principal[i]
    val b = p.principal[i + 1]
    val id = a.id
    fun cambiar(arrastre: Boolean = false, f: (Clip) -> Clip) {
        val g: (Proyecto) -> Proyecto = { Edicion.cambiar(it, id, f) }
        if (arrastre) estado.ajustar(g) else estado.hacer(g)
    }
    // Una transicion no puede comerse mas de la mitad de ninguno de los dos
    val minimo = Linea.tiempoDe(1)
    val maximo = Linea.tiempoDe(Linea.fotogramaEn(min(a.duracionUs, b.duracionUs) / 2)).coerceAtLeast(0)
    fun elegir(t: Transicion) {
        if (t != Transicion.NINGUNA && maximo < minimo) {
            estado.avisar("Alarga los dos bloques para que quepa la transición")
            return
        }
        cambiar {
            val dur = if (it.transicion == Transicion.NINGUNA) AjustesEditor.duracionTransicionUs else it.duracionTransicionUs
            // Los barridos empiezan algo inclinados; si ya se giro a mano, se respeta
            val aj = it.ajusteTransicion
            val barrido = t == Transicion.LINEA || t == Transicion.BARRIDO
            val recto = aj.angulo % 90f == 0f
            val angulo = when {
                t == Transicion.APLASTAR && it.transicion != Transicion.APLASTAR -> 90f
                barrido && recto -> AjusteTransicion.ANGULO_BARRIDO
                else -> aj.angulo
            }
            val tiempo = if (maximo >= minimo) Linea.tiempoDe((dur * Linea.FPS / 1_000_000.0).roundToLong()).coerceIn(minimo, maximo) else 0
            it.copy(transicion = t, duracionTransicionUs = tiempo, ajusteTransicion = aj.copy(angulo = angulo))
        }
        // Que se vea entera: el cabezal salta a un poco antes
        val ini = Linea.inicios(estado.proyecto.principal)
        estado.irA((ini[i + 1] - 200_000).coerceAtLeast(0))
    }
    // Al abrir, la tira se va hasta la que ya esta puesta
    val desliza = rememberScrollState()
    val densidad = LocalDensity.current
    LaunchedEffect(id) {
        val n = Transicion.values().indexOf(a.transicion)
        if (n > 3) desliza.scrollTo(with(densidad) { (76.dp * (n - 2)).roundToPx() })
    }
    HojaPanel(Panel.TRANSICION.titulo, cerrar, claveDesplazamiento = id to a.transicion,
        acciones = if (p.principal.size > 2) ({
            BotonTexto("Usar en todas", {
                estado.hacer { q ->
                    q.copy(principal = q.principal.mapIndexed { j, c ->
                        if (j == q.principal.lastIndex) c else c.copy(
                            transicion = a.transicion,
                            duracionTransicionUs = a.duracionTransicionUs,
                            ajusteTransicion = a.ajusteTransicion
                        )
                    })
                }
                estado.avisar(if (a.transicion == Transicion.NINGUNA) "Todas las uniones sin transición" else "${Rotulos.de(a.transicion.nombre)} en todas las uniones")
            })
        }) else null,
        fijo = {
            Tira(Modifier.padding(top = 2.dp, bottom = 4.dp), hueco = 8.dp, desliza = desliza) {
                for (t in Transicion.values()) {
                    // Algo mas anchas que las de siempre: "Línea de luz" y "Zoom y giro" en una linea
                    Mosaico(if (t == Transicion.NINGUNA) "Ninguna" else Rotulos.de(t.nombre), a.transicion == t, { elegir(t) }, Modifier.width(68.dp)) { col ->
                        if (t == Transicion.NINGUNA) dibujoNinguna(col) else dibujoTransicion(t, col)
                    }
                }
            }
        },
        corta = a.transicion == Transicion.NINGUNA
    ) {
        if (a.transicion == Transicion.NINGUNA) return@HojaPanel
        val aj = a.ajusteTransicion
        if (maximo >= minimo) {
            TiempoFotogramas("Duración", Linea.solape(a, b), minimo, maximo) { us, arrastre ->
                cambiar(arrastre) { it.copy(duracionTransicionUs = us) }
                estado.reproduciendo = false
                val inicios = Linea.inicios(estado.proyecto.principal)
                estado.irA(inicios[i + 1] + us / 2)
            }
        } else Nota("Los bloques son demasiado cortos para una transición.")
        Fila("Ritmo") {
            Tira(Modifier.weight(1f)) {
                for (c in Curva.values()) Chip(Rotulos.de(c.nombre), aj.curva == c, { cambiar { it.copy(ajusteTransicion = it.ajusteTransicion.copy(curva = c)) } })
            }
        }
        val linea = a.transicion == Transicion.LINEA
        if (linea) Fila("Luz") {
            Tira(Modifier.weight(1f)) {
                for ((nombre, luz) in LUCES_HECHAS) Chip(nombre, aj.luz == luz, {
                    cambiar { c -> c.copy(ajusteTransicion = c.ajusteTransicion.copy(
                        luz = luz,
                        angulo = if (luz.girar) 135f else c.ajusteTransicion.angulo,
                        color = if (nombre == "Giro violeta") 0xFF9B5CFF.toInt() else c.ajusteTransicion.color
                    )) }
                })
            }
        }
        if (a.transicion in CON_DIRECCION) {
            var fase by rememberSaveable(a.id) { mutableIntStateOf(0) }
            val gira = linea && aj.luz.girar
            // La luz que gira tiene tres direcciones: como sale, como cruza y como llega
            if (linea) Tira(Modifier.padding(vertical = 3.dp)) {
                ChipInterruptor("Girar al cruzar", aj.luz.girar, { v -> cambiar { it.copy(ajusteTransicion = it.ajusteTransicion.copy(luz = it.ajusteTransicion.luz.copy(girar = v))) } })
                if (gira) listOf("Inicio", "Mitad", "Final").forEachIndexed { n, nombre -> Chip(nombre, fase == n, { fase = n }) }
            }
            val angulo = if (!gira || fase == 0) aj.angulo else if (fase == 1) aj.luz.anguloMedio else aj.luz.anguloFinal
            fun girar(g: Float, arrastre: Boolean) = cambiar(arrastre) { c ->
                val ajuste = c.ajusteTransicion
                c.copy(ajusteTransicion = when {
                    !gira || fase == 0 -> ajuste.copy(angulo = g)
                    fase == 1 -> ajuste.copy(luz = ajuste.luz.copy(anguloMedio = g))
                    else -> ajuste.copy(luz = ajuste.luz.copy(anguloFinal = g))
                })
            }
            val grados = ((angulo % 360f) + 360f) % 360f
            Deslizador("Dirección", grados, 0f, 359f, { g -> girar(g, true) }, paso = 1f, texto = { "${it.roundToInt()}°" })
            Direcciones(grados) { g -> girar(g, false) }
        }
        if (a.transicion in CON_COLOR) Fila("Color") {
            Muestras(COLORES_LUZ, aj.color, { v -> cambiar { it.copy(ajusteTransicion = it.ajusteTransicion.copy(color = v)) } }, Modifier.weight(1f))
        }
        if (a.transicion in CON_FUERZA) {
            Deslizador(
                if (linea) "Grosor" else "Fuerza", aj.intensidad, 0.2f, 2.5f,
                { v -> cambiar(true) { it.copy(ajusteTransicion = it.ajusteTransicion.copy(intensidad = v)) } },
                neutro = 1f, texto = { "${(it * 100).toInt()}%" }
            )
        }
        if (linea) SeccionAjustes("Personalizar la luz") {
            Deslizador("Halo", aj.luz.halo, 0f, 4f, { v -> cambiar(true) { it.copy(ajusteTransicion = it.ajusteTransicion.copy(luz = it.ajusteTransicion.luz.copy(halo = v))) } }, neutro = 1f)
            Deslizador("Núcleo", aj.luz.nucleo, 0f, 2f, { v -> cambiar(true) { it.copy(ajusteTransicion = it.ajusteTransicion.copy(luz = it.ajusteTransicion.luz.copy(nucleo = v))) } }, neutro = 1f)
            Deslizador("Textura", aj.luz.textura, 0f, 1f, { v -> cambiar(true) { it.copy(ajusteTransicion = it.ajusteTransicion.copy(luz = it.ajusteTransicion.luz.copy(textura = v))) } }, neutro = 0f)
            ChipInterruptor("Arcoíris", aj.luz.arcoiris, { v -> cambiar { it.copy(ajusteTransicion = it.ajusteTransicion.copy(luz = it.ajusteTransicion.luz.copy(arcoiris = v))) } })
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
        Transicion.APLASTAR -> {
            drawRoundRect(tenue, Offset(0f, y), Size(w, h), radio)
            drawRoundRect(col, Offset(0f, y + h * 0.38f), Size(w, h * 0.24f), radio)
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
