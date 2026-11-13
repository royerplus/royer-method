package com.royermethod.app.editor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.royermethod.app.editor.modelo.CatalogoEfectos
import com.royermethod.app.editor.modelo.EfectoVideo
import com.royermethod.app.editor.modelo.Parametro
import com.royermethod.app.editor.modelo.ProgresoEfecto
import com.royermethod.app.editor.modelo.TipoEfectoVideo
import com.royermethod.app.editor.modelo.vfxPantallaEn
import com.royermethod.app.ui.components.*
import com.royermethod.app.ui.theme.Ink
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin

/** Un efecto acelerado puede terminar con menos de un fotograma disponible. */
internal fun minimoFinEfecto(inicioUs: Long, pasoUs: Long, duracionUs: Long): Long =
    (inicioUs + pasoUs).coerceAtMost(duracionUs)

/** Mantiene validos los limites de deslizador, botones y cabezal. */
internal fun acotarBordeEfecto(valorUs: Long, minimoUs: Long, maximoUs: Long): Long =
    valorUs.coerceIn(minimoUs, maxOf(minimoUs, maximoUs))

private fun EfectoVideo.ritmoControl(): Double =
    (rapidez.takeIf { it.isFinite() }?.coerceIn(.1f, 10f) ?: 1f).toDouble()

internal fun EfectoVideo.faseControl(localUs: Long): Long {
    val base = duracionBaseUs.takeIf { it > 0 } ?: duracionUs
    return ((localUs - inicioUs + desfaseUs).toDouble() * ritmoControl())
        .roundToLong().coerceIn(0L, base)
}

/** Redondea hacia el primer microsegundo que alcanza el punto medido. */
internal fun EfectoVideo.localDelPunto(punto: ProgresoEfecto): Long =
    ceil(punto.tiempoUs.toDouble() / ritmoControl() - desfaseUs.toDouble()).toLong() + inicioUs

/** La vuelta por el reloj entero puede diferir hasta ceil(rapidez) microsegundos. */
internal fun EfectoVideo.puntoEnFase(faseUs: Long): ProgresoEfecto? =
    progresos.minByOrNull { abs(it.tiempoUs - faseUs) }
        ?.takeIf { abs(it.tiempoUs - faseUs) <= ceil(ritmoControl()).toLong() }

/** Controles propios de luz: las herramientas anteriores no cambian de aspecto. */
@Composable
internal fun ControlesVfxPantalla(p: PanelFx, e: EfectoVideo) {
    val parametros = CatalogoEfectos.parametros(e.tipo)
    val a = e.ajustes
    val d = CatalogoEfectos.ajustesDe(e.tipo)
    if(e.tipo == TipoEfectoVideo.PULSO_MITADES) PlanoMitades(p,e)
    if(Parametro.COLOR in parametros) {
        Etiqueta(if(e.tipo==TipoEfectoVideo.SEMITONO) "Color de la trama" else "Color de la luz")
        val colores=listOf(0xFFFFFFFF,0xFFAB66FF,0xFFFF66C4,0xFF36D8FF,0xFF4CFFB0,0xFFFFCC66,0xFFFF5C64,0xFF181020).map { it.toInt() }
        Muestras(colores,a.color,{ v -> p.cambiar { it.copy(ajustes=it.ajustes.copy(color=v)) } })
        var rgb by remember(e.id) { mutableStateOf(false) }
        BotonBorde(if(rgb) "Cerrar RGB" else "Color personalizado",{ rgb=!rgb })
        if(rgb) for((nombre,shift) in listOf("Rojo" to 16,"Verde" to 8,"Azul" to 0)) {
            Deslizador(nombre,((a.color ushr shift) and 255).toFloat(),0f,255f,{ v ->
                p.cambiar(true) { it.copy(ajustes=it.ajustes.copy(color=(it.ajustes.color and (255 shl shift).inv()) or (v.roundToInt() shl shift))) }
            },texto={ it.roundToInt().toString() })
        }
    }
    if(Parametro.HALO in parametros) Deslizador(
        if(e.tipo==TipoEfectoVideo.PULSO_MITADES) "Luz plana → contornos neón" else "Difusión del resplandor",
        a.halo,0f,1f,{ v -> p.cambiar(true) { it.copy(ajustes=it.ajustes.copy(halo=v)) } },neutro=d.halo)
    if(Parametro.UMBRAL in parametros) Deslizador(
        if(e.tipo==TipoEfectoVideo.ARCOIRIS_BORDES) "Selección de contornos" else "Umbral de luces",
        a.umbral,0f,.99f,{ v -> p.cambiar(true) { it.copy(ajustes=it.ajustes.copy(umbral=v)) } },neutro=d.umbral)
    if(Parametro.LINEAS in parametros) {
        Pestanas(listOf("Puntos","Líneas"),if(a.vertical)1 else 0,{ i ->
            p.cambiar { it.copy(ajustes=it.ajustes.copy(vertical=i==1)) }
        })
    }
}

/** Diagrama del corte orientable. El resultado sobre el video se ve en el lienzo principal. */
@Composable
private fun PlanoMitades(p: PanelFx, e: EfectoVideo) {
    val actual by rememberUpdatedState(p)
    fun mover(pos: Offset,w: Float,h: Float,arrastre: Boolean) {
        actual.cambiar(arrastre) { it.copy(ajustes=it.ajustes.copy(
            centroX=(pos.x/w-.5f).coerceIn(-.5f,.5f),centroY=(pos.y/h-.5f).coerceIn(-.5f,.5f))) }
    }
    val accion by rememberUpdatedState<(Offset,Float,Float,Boolean)->Unit>(::mover)
    Canvas(Modifier.fillMaxWidth().height(106.dp).clip(esquinas(12.dp)).background(Ink.White08)
        .pointerInput(e.id) { detectTapGestures { accion(it,size.width.toFloat(),size.height.toFloat(),false) } }
        .pointerInput(e.id) { detectDragGestures(
            onDragStart={ actual.estado.empezarGesto();accion(it,size.width.toFloat(),size.height.toFloat(),true) },
            onDragEnd={ actual.estado.terminarGesto() },onDragCancel={ actual.estado.terminarGesto() }) { ch,_ ->
            ch.consume();accion(ch.position,size.width.toFloat(),size.height.toFloat(),true)
        } }) {
        val ef=actual.elegido ?: e
        val a=ef.ajustes
        val rad=Math.toRadians(a.angulo.toDouble())
        val dir=Offset(cos(rad).toFloat(),sin(rad).toFloat())
        val perpendicular=Offset(-dir.y,dir.x)
        val centro=Offset((.5f+a.centroX)*size.width,(.5f+a.centroY)*size.height)
        val alcance=maxOf(size.width,size.height)*2
        val t=(actual.estado.tiempo-actual.inicioBloque).coerceIn(0,actual.dur)
        val luz=actual.vista.vfxPantallaEn(t).mitades
        val lado=luz?.lado ?: -1f
        val a1=centro-perpendicular*alcance;val b=centro+perpendicular*alcance
        val c=b+dir*alcance*lado;val d=a1+dir*alcance*lado
        val region=Path().apply { moveTo(a1.x,a1.y);lineTo(b.x,b.y);lineTo(c.x,c.y);lineTo(d.x,d.y);close() }
        clipPath(region) { drawRect(Color(a.color).copy(alpha=.15f+(luz?.fuerza?:.5f).coerceIn(0f,1f)*.4f)) }
        drawCircle(Ink.White50,size.height*.19f,Offset(size.width*.5f,size.height*.34f),style=Stroke(2.dp.toPx()))
        drawArc(Ink.White50,180f,180f,false,Offset(size.width*.5f-size.height*.34f,size.height*.53f),
            androidx.compose.ui.geometry.Size(size.height*.68f,size.height*.65f),style=Stroke(2.dp.toPx()))
        drawLine(Color(a.color),a1,b,2.dp.toPx())
        drawCircle(Ink.Black,7.dp.toPx(),centro);drawCircle(Ink.White,5.dp.toPx(),centro)
    }
    Nota("Toca o arrastra para colocar el corte.")
}
