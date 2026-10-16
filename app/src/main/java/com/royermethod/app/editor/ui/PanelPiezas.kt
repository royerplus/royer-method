package com.royermethod.app.editor.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.royermethod.app.editor.modelo.Bezier
import com.royermethod.app.editor.modelo.Clave
import com.royermethod.app.editor.modelo.Curva
import com.royermethod.app.editor.modelo.Edicion
import com.royermethod.app.editor.modelo.Encuadre
import com.royermethod.app.editor.modelo.PiezaVideo
import com.royermethod.app.editor.modelo.RegionUv
import com.royermethod.app.ui.components.BotonBorde
import com.royermethod.app.ui.components.Cuadricula
import com.royermethod.app.ui.components.Deslizador
import com.royermethod.app.ui.components.Etiqueta
import com.royermethod.app.ui.components.Glifos
import com.royermethod.app.ui.components.Mosaico
import com.royermethod.app.ui.components.Nota
import com.royermethod.app.ui.components.Opciones
import com.royermethod.app.ui.components.Pestanas
import com.royermethod.app.ui.theme.Ink
import kotlin.math.abs

/** Regiones independientes de una fuente; ninguna vuelve a llenar el lienzo. */
@Composable
fun PanelPiezas(estado: EstadoEditor) {
    val clip = estado.clipElegido ?: return
    var elegida by rememberSaveable(clip.id) { mutableIntStateOf(0) }
    var pestana by rememberSaveable(clip.id) { mutableIntStateOf(0) }
    val indice = elegida.coerceIn(0, clip.piezas.lastIndex.coerceAtLeast(0))

    Etiqueta("Separar la imagen")
    Opciones(listOf(0, 1, 2), null, {
        when (it) { 0 -> "Superior / inferior"; 1 -> "Izquierda / derecha"; else -> "Entera" }
    }, { division ->
        estado.cambiarClip { c ->
            c.copy(piezas = when (division) {
                0 -> listOf(
                    PiezaVideo("Superior", RegionUv(abajo = 0.5f)),
                    PiezaVideo("Inferior", RegionUv(arriba = 0.5f))
                )
                1 -> listOf(
                    PiezaVideo("Izquierda", RegionUv(derecha = 0.5f)),
                    PiezaVideo("Derecha", RegionUv(izquierda = 0.5f))
                )
                else -> emptyList()
            })
        }
        elegida = 0
    })
    BotonBorde("Unir dos mitades verticales", {
        estado.cambiarClip { c ->
            val fin = minOf(300_000L, c.duracionUs.coerceAtLeast(1L))
            c.copy(piezas = listOf(
                PiezaVideo("Superior", RegionUv(abajo = 0.5f), claves = listOf(
                    Clave(0, Encuadre(y = -0.5f), Curva.SALIDA), Clave(fin, Encuadre())
                )),
                PiezaVideo("Inferior", RegionUv(arriba = 0.5f), claves = listOf(
                    Clave(0, Encuadre(y = 0.5f), Curva.SALIDA), Clave(fin, Encuadre())
                ))
            ))
        }
        elegida = 0
        pestana = 1
        estado.reproduciendo = false
        estado.inicioElegido?.let { estado.irA(it) }
    }, glifo = Glifos.animacion)
    Spacer(Modifier.height(6.dp))
    Nota("Cada parte conserva su lugar en la imagen. El encuadre y las animaciones del bloque se aplican al conjunto.", color = Ink.White50)
    if (clip.piezas.isEmpty()) return

    fun cambiar(arrastre: Boolean = false, f: (PiezaVideo) -> PiezaVideo) {
        estado.cambiarClip(arrastre) { actual ->
            actual.copy(piezas = actual.piezas.mapIndexed { i, p -> if (i == indice) f(p) else p })
        }
    }

    Spacer(Modifier.height(8.dp))
    Etiqueta("Parte seleccionada")
    Cuadricula(clip.piezas.indices.toList()) { i, m ->
        val pieza = clip.piezas[i]
        Mosaico(pieza.nombre.ifBlank { "Parte ${i + 1}" }, i == indice, { elegida = i }, m) { color ->
            val r = pieza.region.acotada()
            val ancho = size.width * 0.7f
            val alto = size.height * 0.9f
            val origen = Offset((size.width - ancho) / 2f, (size.height - alto) / 2f)
            drawRect(color.copy(alpha = 0.18f), origen, Size(ancho, alto))
            drawRect(color, origen + Offset(r.izquierda * ancho, r.arriba * alto), Size(r.ancho * ancho, r.alto * alto))
            drawRect(color, origen, Size(ancho, alto), style = Stroke(1.dp.toPx()))
        }
    }
    Spacer(Modifier.height(8.dp))
    val pieza = clip.piezas[indice]
    Pestanas(listOf("Region", "Movimiento", "Curva"), pestana, { pestana = it })
    Spacer(Modifier.height(6.dp))

    if (pestana == 0) {
        RegionDePieza(pieza.region, { region -> cambiar(true) { it.copy(region = region.acotada()) } })
        Spacer(Modifier.height(6.dp))
        BotonBorde("Duplicar esta parte", {
            estado.cambiarClip { c ->
                val original = c.piezas.getOrNull(indice) ?: return@cambiarClip c
                c.copy(piezas = c.piezas + original.copy(nombre = "${original.nombre} copia"))
            }
            elegida = clip.piezas.size
        }, glifo = Glifos.duplicar)
        Spacer(Modifier.height(6.dp))
        BotonBorde("Quitar esta parte", {
            estado.cambiarClip { c -> c.copy(piezas = c.piezas.filterIndexed { i, _ -> i != indice }) }
            elegida = (indice - 1).coerceAtLeast(0)
        }, glifo = Glifos.papelera)
        return
    }

    val inicio = estado.inicioElegido ?: return
    val local = (estado.tiempo - inicio).coerceIn(0L, clip.duracionUs)
    TiempoFotogramas("Vista previa", local, 0L, clip.duracionUs) { us, _ ->
        estado.reproduciendo = false
        estado.irA(inicio + us)
    }
    val clavesVisibles = pieza.claves.filter { it.tiempoUs in 0L..clip.duracionUs }.sortedBy { it.tiempoUs }
    val clave = clavesVisibles.firstOrNull { abs(it.tiempoUs - local) <= Edicion.TOLERANCIA_CLAVE }
    if (clavesVisibles.isNotEmpty()) {
        Opciones(clavesVisibles, clave, { "%.3f s".format(it.tiempoUs / 1e6) }, {
            estado.reproduciendo = false
            estado.irA(inicio + it.tiempoUs)
        })
    }
    BotonBorde(if (clave == null) "Poner clave de esta parte" else "Quitar clave de esta parte", {
        estado.reproduciendo = false
        cambiar { p ->
            if (clave != null) p.copy(claves = p.claves.filter { it.tiempoUs != clave.tiempoUs })
            else p.ponerPose(local, clip.duracionUs) { it }
        }
    }, glifo = if (clave == null) Glifos.rombo else Glifos.romboLleno)
    Spacer(Modifier.height(6.dp))

    if (pestana == 1) {
        Nota("Cambiar un valor crea o actualiza la clave de esta parte en el instante mostrado.", color = Ink.White50)
        val pose = pieza.encuadreEn(clave?.tiempoUs ?: local)
        fun transformar(f: (Encuadre) -> Encuadre) {
            estado.reproduciendo = false
            estado.irA(inicio + local)
            cambiar(true) { it.ponerPose(local, clip.duracionUs, f) }
        }
        Deslizador("Desplazamiento X", pose.x, -2f, 2f, { v -> transformar { it.copy(x = v) } }, neutro = 0f, texto = { "%.3f anchos".format(it) })
        Deslizador("Desplazamiento Y", pose.y, -2f, 2f, { v -> transformar { it.copy(y = v) } }, neutro = 0f, texto = { "%.3f altos".format(it) })
        Deslizador("Escala", pose.escala, 0f, 5f, { v -> transformar { it.copy(escala = v) } }, neutro = 1f, texto = { "%.3fx".format(it) })
        Deslizador("Giro", pose.rotacion, -360f, 360f, { v -> transformar { it.copy(rotacion = v) } }, neutro = 0f, texto = { "%.1f°".format(it) })
        Deslizador("Opacidad", pose.opacidad, 0f, 1f, { v -> transformar { it.copy(opacidad = v) } }, neutro = 1f, texto = { "%.1f%%".format(it * 100) })
        BotonBorde("Restablecer esta pose", {
            cambiar { it.ponerPose(local, clip.duracionUs) { Encuadre() } }
        })
        if (clave != null) {
            Spacer(Modifier.height(8.dp))
            Etiqueta("Momento de esta clave")
            val minimo = (pieza.claves.filter { it.tiempoUs < clave.tiempoUs }.maxOfOrNull { it.tiempoUs }?.plus(1L) ?: 0L).coerceAtLeast(0L)
            val maximo = (pieza.claves.filter { it.tiempoUs > clave.tiempoUs }.minOfOrNull { it.tiempoUs }?.minus(1L) ?: clip.duracionUs).coerceAtMost(clip.duracionUs)
            TiempoFotogramas("Tiempo", clave.tiempoUs, minimo, maximo) { us, arrastre ->
                cambiar(arrastre) { p ->
                    p.copy(claves = p.claves.map { if (it.tiempoUs == clave.tiempoUs) it.copy(tiempoUs = us) else it }.sortedBy { it.tiempoUs })
                }
                estado.reproduciendo = false
                estado.irA(inicio + us)
            }
        }
        return
    }

    if (clave == null) {
        Nota("Elige una clave o pon una aqui para editar el movimiento hasta la siguiente.", color = Ink.White70)
        return
    }
    fun cambiarClave(arrastre: Boolean = false, f: (Clave) -> Clave) = cambiar(arrastre) { p ->
        p.copy(claves = p.claves.map { if (it.tiempoUs == clave.tiempoUs) f(it) else it })
    }
    Nota("Curva desde esta clave hasta la siguiente de ${pieza.nombre.ifBlank { "esta parte" }}.", color = Ink.White50)
    Opciones(Curva.values().toList(), if (clave.bezier == null) clave.curva else null, { it.nombre }, { curva ->
        cambiarClave { it.copy(curva = curva, bezier = null) }
    })
    BotonBorde(if (clave.bezier == null) "Editar Bezier a mano" else "Bezier personalizada", {
        cambiarClave { it.copy(bezier = it.bezier ?: Bezier.SUAVE) }
    }, glifo = Glifos.curva)
    val bezier = clave.bezier
    if (bezier != null) {
        Spacer(Modifier.height(8.dp))
        EditorBezier(bezier) { b -> cambiarClave(true) { it.copy(bezier = b) } }
        Deslizador("Punto 1 · tiempo", bezier.x1, 0f, 1f, { v -> cambiarClave(true) { it.copy(bezier = (it.bezier ?: bezier).copy(x1 = v)) } }, texto = { "%.3f".format(it) })
        Deslizador("Punto 1 · avance", bezier.y1, -0.3f, 1.3f, { v -> cambiarClave(true) { it.copy(bezier = (it.bezier ?: bezier).copy(y1 = v)) } }, neutro = 0f, texto = { "%.3f".format(it) })
        Deslizador("Punto 2 · tiempo", bezier.x2, 0f, 1f, { v -> cambiarClave(true) { it.copy(bezier = (it.bezier ?: bezier).copy(x2 = v)) } }, neutro = 1f, texto = { "%.3f".format(it) })
        Deslizador("Punto 2 · avance", bezier.y2, -0.3f, 1.3f, { v -> cambiarClave(true) { it.copy(bezier = (it.bezier ?: bezier).copy(y2 = v)) } }, neutro = 1f, texto = { "%.3f".format(it) })
    }
}

/** Al editar lejos de la primera clave, se conserva una pose inicial. */
private fun PiezaVideo.ponerPose(local: Long, duracion: Long, cambio: (Encuadre) -> Encuadre): PiezaVideo {
    val previa = claves.firstOrNull { it.tiempoUs in 0L..duracion && abs(it.tiempoUs - local) <= Edicion.TOLERANCIA_CLAVE }
    val t = previa?.tiempoUs ?: local.coerceIn(0L, duracion)
    val base = if (claves.isEmpty() && t > Edicion.TOLERANCIA_CLAVE) listOf(Clave(0, encuadre)) else emptyList()
    val nueva = (previa ?: Clave(t, encuadreEn(t))).copy(encuadre = cambio(encuadreEn(t)))
    return copy(claves = (claves.filter { it.tiempoUs != t } + base + nueva).sortedBy { it.tiempoUs })
}

@Composable
private fun RegionDePieza(region: RegionUv, cambiar: (RegionUv) -> Unit) {
    val r = region.acotada()
    Nota("Limites de la parte en la fuente: moverlos corta la imagen y conserva su encuadre.", color = Ink.White50)
    // Deja recorrido util incluso si una region importada mide una fraccion de pixel.
    val margenX = minOf(0.00001f, r.ancho / 2f)
    val margenY = minOf(0.00001f, r.alto / 2f)
    Deslizador("Borde izquierdo", r.izquierda, 0f, r.derecha - margenX, { cambiar(r.copy(izquierda = it)) }, texto = { "%.2f%%".format(it * 100) })
    Deslizador("Borde derecho", r.derecha, r.izquierda + margenX, 1f, { cambiar(r.copy(derecha = it)) }, neutro = 1f, texto = { "%.2f%%".format(it * 100) })
    Deslizador("Borde superior", r.arriba, 0f, r.abajo - margenY, { cambiar(r.copy(arriba = it)) }, texto = { "%.2f%%".format(it * 100) })
    Deslizador("Borde inferior", r.abajo, r.arriba + margenY, 1f, { cambiar(r.copy(abajo = it)) }, neutro = 1f, texto = { "%.2f%%".format(it * 100) })
}
