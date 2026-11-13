package com.royermethod.app.editor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.royermethod.app.editor.modelo.*
import com.royermethod.app.editor.motor.MaquetaTexto
import com.royermethod.app.ui.components.*
import com.royermethod.app.ui.theme.Ink
import kotlin.math.*

@Composable
internal fun HojaRecorte(estado: EstadoEditor, cerrar: () -> Unit) {
    val seleccionado = estado.clipElegido ?: return
    var tab by rememberSaveable(seleccionado.id) { mutableIntStateOf(0) }
    HojaPanel("Recorte", cerrar, claveDesplazamiento=seleccionado.id to tab, fijo = {
        Pestanas(listOf("Croma", "Máscaras", "Animar"), tab, { tab = it })
    }) {
        val original = estado.clipElegido ?: return@HojaPanel
        val c = original.propiedadesEn(estado.localElegido ?: 0)
        when(tab) {
            0 -> ControlesCroma(estado,c)
            1 -> ControlesMascara(estado,c)
            else -> {
                TiempoPropiedades(estado,GrupoPropiedad.RECORTE)
                OpcionesPropiedades(estado,GrupoPropiedad.RECORTE)
                Nota("Pon una clave, avanza el tiempo y cambia el croma o la máscara.")
            }
        }
    }
}

private fun cambiarCroma(e: EstadoEditor, arrastre: Boolean = false, f: (Croma) -> Croma) =
    e.cambiarAspecto(arrastre) { it.copy(recorteVisual = it.recorteVisual.copy(croma = f(it.recorteVisual.croma))) }
private fun cambiarMascara(e: EstadoEditor, arrastre: Boolean = false, f: (MascaraForma) -> MascaraForma) =
    e.cambiarAspecto(arrastre) { it.copy(recorteVisual = it.recorteVisual.copy(mascara = f(it.recorteVisual.mascara))) }

@Composable
internal fun fuenteRecorte(e: EstadoEditor, c: Clip, preciso: Boolean = false): ImageBitmap? {
    val t = c.texto
    if (t != null) return remember(t) { MaquetaTexto.pintar(t, 400, 1f).asImageBitmap() }
    val o = c.origen ?: return null
    val local = e.localElegido ?: 0L
    val exacto = preciso && !e.reproduciendo
    val archivo = if(c.esVideo)c.tiempoArchivo(local)else 0L
    val tiempo = if(!c.esVideo)0L else if(exacto) {
        val fps=o.fps.takeIf { it.isFinite() && it in 1f..240f } ?: 30f
        ((archivo.toDouble()*fps/1_000_000).roundToLong()*1_000_000.0/fps).roundToLong()
            .coerceIn(0L,(o.duracionUs-1).coerceAtLeast(0L))
    } else archivo / 250_000L * 250_000L
    val recursos=LocalRecursosEditor.current
    return if(exacto)recursos.fotograma(o,tiempo)else recursos.miniatura(o,tiempo)
}

@Composable
private fun ControlesCroma(e: EstadoEditor, c: Clip) {
    val k = c.recorteVisual.croma
    var elegir by rememberSaveable(c.id) { mutableStateOf(false) }
    Interruptor("Eliminar un color", k.activo, { v -> cambiarCroma(e) { it.copy(activo = v) } },
        detalle=if(k.activo)null else "Elige el color del fondo para dejarlo transparente.")
    if(!k.activo)return
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(Color(k.color)).border(2.dp,Ink.White70,CircleShape))
        BotonBorde(if(elegir) "Ocultar selector" else "Elegir del clip",{ elegir=!elegir },glifo=Glifos.color)
    }
    Muestras(listOf(0xFF00FF00,0xFF0000FF,0xFFFFFFFF,0xFF000000,0xFFFF00FF,0xFFFF0000).map { it.toInt() },k.color,
        { v -> cambiarCroma(e) { it.copy(color=v) } })
    if(elegir) {
        val imagen=fuenteRecorte(e,c,preciso=true)
        if(imagen==null)Nota("Preparando fotograma…")else SelectorColor(e,imagen)
    }
    Deslizador("Intensidad",k.intensidad,0f,1f,{ v -> cambiarCroma(e,true) { it.copy(intensidad=v) } },neutro=1f)
    Deslizador("Tolerancia al color",k.tolerancia,0f,.8f,{ v -> cambiarCroma(e,true) { it.copy(tolerancia=v) } },neutro=.14f)
    SeccionAjustes("Bordes y limpieza") {
        Deslizador("Bordes suaves",k.suavidad,0f,.3f,{ v -> cambiarCroma(e,true) { it.copy(suavidad=v) } },neutro=.05f)
        Deslizador("Limpiar halo de color",k.limpiar,0f,1f,{ v -> cambiarCroma(e,true) { it.copy(limpiar=v) } },neutro=.5f)
    }
    SeccionAjustes("Color personalizado") {
        for((nombre,shift) in listOf("Rojo" to 16,"Verde" to 8,"Azul" to 0))
            Deslizador(nombre,((k.color ushr shift)and 255).toFloat(),0f,255f,{ v ->
                cambiarCroma(e,true) { it.copy(color=(it.color and (255 shl shift).inv()) or (v.roundToInt() shl shift)) }
            },texto={ it.roundToInt().toString() })
    }
    SeccionAjustes("Opciones de croma") {
        Interruptor("Conservar solo ese color",k.invertir,{ v -> cambiarCroma(e) { it.copy(invertir=v) } })
        BotonBorde("Restablecer croma",{ cambiarCroma(e) { Croma() } },glifo=Glifos.reiniciar)
    }
}

private fun rectanguloFuente(w: Float, h: Float, aspecto: Float): Rect {
    val margen = 16f
    val ancho = min(w-2*margen,(h-2*margen)*aspecto).coerceAtLeast(1f)
    val alto = ancho / aspecto.coerceAtLeast(.01f)
    return Rect((w-ancho)/2,(h-alto)/2,(w+ancho)/2,(h+alto)/2)
}

@Composable
private fun SelectorColor(e: EstadoEditor, imagen: ImageBitmap) {
    val mapa = remember(imagen) { imagen.toPixelMap() }
    var marca by remember(imagen) { mutableStateOf<Offset?>(null) }
    fun elegir(p: Offset, w: Float, h: Float, arrastre: Boolean) {
        val r = rectanguloFuente(w,h,imagen.width.toFloat()/imagen.height)
        if (!r.contains(p)) return
        marca = p
        val x = ((p.x-r.left)/r.width*mapa.width).toInt().coerceIn(0,mapa.width-1)
        val y = ((p.y-r.top)/r.height*mapa.height).toInt().coerceIn(0,mapa.height-1)
        val color = mapa[x,y].toArgb() or (0xFF shl 24)
        cambiarCroma(e,arrastre) { it.copy(color = color) }
    }
    val seleccionar by rememberUpdatedState<(Offset,Float,Float,Boolean)->Unit>(::elegir)
    Nota("Toca o arrastra sobre el color que quieres quitar.")
    Canvas(Modifier.fillMaxWidth().height(180.dp).clip(esquinas(12.dp)).background(Ink.White04)
        .pointerInput(imagen) { detectTapGestures { seleccionar(it,size.width.toFloat(),size.height.toFloat(),false) } }
        .pointerInput(imagen) { detectDragGestures(onDragStart = { e.empezarGesto(); seleccionar(it,size.width.toFloat(),size.height.toFloat(),true) },
            onDragEnd = { e.terminarGesto() }, onDragCancel = { e.terminarGesto() }) { cambio,_ ->
            cambio.consume(); seleccionar(cambio.position,size.width.toFloat(),size.height.toFloat(),true)
        } }) {
        val r = rectanguloFuente(size.width,size.height,imagen.width.toFloat()/imagen.height)
        drawImage(imagen, dstOffset = IntOffset(r.left.roundToInt(),r.top.roundToInt()), dstSize = IntSize(r.width.roundToInt(),r.height.roundToInt()))
        marca?.let { p ->
            drawCircle(Color.Black,9.dp.toPx(),p,style = Stroke(4.dp.toPx()))
            drawCircle(Color.White,9.dp.toPx(),p,style = Stroke(2.dp.toPx()))
            drawLine(Color.White,p-Offset(14.dp.toPx(),0f),p+Offset(14.dp.toPx(),0f),1.dp.toPx())
            drawLine(Color.White,p-Offset(0f,14.dp.toPx()),p+Offset(0f,14.dp.toPx()),1.dp.toPx())
        }
    }
}

@Composable
private fun ControlesMascara(e: EstadoEditor, c: Clip) {
    val m = c.recorteVisual.mascara
    LazyRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        items(FormaRecorte.values().toList(),key={ it.name }) { tipo ->
            Mosaico(tipo.titulo,m.tipo==tipo,{ cambiarMascara(e) { it.copy(tipo=tipo) } },Modifier.width(76.dp)) {
                if(tipo==FormaRecorte.NINGUNA) {
                    drawLine(Ink.White70,Offset(size.width*.2f,size.height*.2f),Offset(size.width*.8f,size.height*.8f),2.dp.toPx())
                    drawLine(Ink.White70,Offset(size.width*.8f,size.height*.2f),Offset(size.width*.2f,size.height*.8f),2.dp.toPx())
                } else {
                    val puntos=MascaraForma(tipo=tipo,ancho=.75f,alto=.75f).contorno(1f to 1f)
                    val p=Path().apply { puntos.forEachIndexed { i,v -> val x=v.first*size.width;val y=v.second*size.height;if(i==0)moveTo(x,y)else lineTo(x,y) };close() }
                    clipRect { drawPath(p,Ink.White70) }
                }
            }
        }
    }
    if(m.tipo==FormaRecorte.NINGUNA) {
        Spacer(Modifier.height(12.dp))
        Nota("Elige una figura. Lo que quede fuera dejará ver las capas de detrás.")
        return
    }
    Spacer(Modifier.height(8.dp))
    val imagen=fuenteRecorte(e,c,preciso=true)
    if(imagen==null)Nota("Preparando fotograma…")
    PreviewMascara(e,c,imagen)
    Nota("Arrastra para mover · esquinas para tamaño · círculo para girar")
    Deslizador("Borde suave",m.pluma,0f,.3f,{ v -> cambiarMascara(e,true) { it.copy(pluma=v) } },neutro=.015f)
    SeccionAjustes("Tamaño y giro") {
        val fijo=m.tipo==FormaRecorte.CIRCULO || m.tipo==FormaRecorte.CUADRADO
        if(m.tipo!=FormaRecorte.LINEAL) {
            Deslizador(if(fijo) "Tamaño" else "Anchura",m.ancho,.02f,2f,{ v -> cambiarMascara(e,true) { it.copy(ancho=v) } },neutro=.8f)
            if(!fijo)Deslizador("Altura",m.alto,.02f,2f,{ v -> cambiarMascara(e,true) { it.copy(alto=v) } },neutro=.8f)
        }
        Deslizador("Giro",m.giro,-180f,180f,{ v -> cambiarMascara(e,true) { it.copy(giro=v) } },neutro=0f,texto={ "${it.roundToInt()}°" })
        if(m.tipo==FormaRecorte.REDONDEADO)Deslizador("Esquinas",m.radio,0f,.5f,{ v -> cambiarMascara(e,true) { it.copy(radio=v) } },neutro=.12f)
        BotonBorde("Centrar máscara",{ cambiarMascara(e) { it.copy(x=.5f,y=.5f) } })
    }
    SeccionAjustes("Opciones de máscara") {
        Interruptor("Invertir máscara",m.invertir,{ v -> cambiarMascara(e) { it.copy(invertir=v) } })
        BotonBorde("Quitar máscara",{ cambiarMascara(e) { MascaraForma() } },glifo=Glifos.reiniciar)
    }
}

@Composable
private fun PreviewMascara(e: EstadoEditor, c: Clip, imagen: ImageBitmap?) {
    val rc = c.encuadre.recorte
    val o = c.origen
    val razon = if(imagen != null) imagen.width.toFloat()*rc.ancho/(imagen.height*rc.alto)
        else if(o != null && o.altoVisible > 0) o.anchoVisible.toFloat()*rc.ancho/(o.altoVisible*rc.alto) else 1f
    val aspecto = if(razon >= 1f) razon to 1f else 1f to 1f/razon.coerceAtLeast(.01f)
    val actual by rememberUpdatedState(c.recorteVisual.mascara)
    Canvas(Modifier.fillMaxWidth().height(190.dp).clip(esquinas(12.dp)).background(Ink.White04)
        .pointerInput(c.id,razon) {
            awaitEachGesture {
                val abajo = awaitFirstDown()
                val r = rectanguloFuente(size.width.toFloat(),size.height.toFloat(),razon)
                val base = actual
                fun px(p: Pair<Float,Float>) = Offset(r.left+p.first*r.width,r.top+p.second*r.height)
                val centro = px(base.x to base.y)
                val alto = base.semiejes(aspecto).second
                val giro = px(base.aUv(0f to -alto-.12f,aspecto))
                val cercaGiro = (abajo.position-giro).getDistance() <= 22.dp.toPx()
                val cercaAsa = base.tipo != FormaRecorte.LINEAL && base.asas(aspecto).any { (px(it)-abajo.position).getDistance()<=22.dp.toPx() }
                var movido = false
                var trabajo = base
                e.empezarGesto()
                try {
                    while(true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == abajo.id } ?: break
                        if(!ch.pressed) break
                        if((ch.position-abajo.position).getDistance() < 1.dp.toPx() && !movido) continue
                        movido = true
                        trabajo = if(ev.changes.count { it.pressed } >= 2) {
                            val pan=ev.calculatePan(); val zoom=ev.calculateZoom(); val rot=ev.calculateRotation()
                            trabajo.copy(x=(trabajo.x+pan.x/r.width).coerceIn(-.5f,1.5f),y=(trabajo.y+pan.y/r.height).coerceIn(-.5f,1.5f),
                                ancho=(trabajo.ancho*zoom).coerceIn(.02f,2f),alto=(trabajo.alto*zoom).coerceIn(.02f,2f),giro=normalizarGiro(trabajo.giro+rot))
                        } else if(cercaGiro) {
                            val a=atan2(abajo.position.y-centro.y,abajo.position.x-centro.x)
                            val b=atan2(ch.position.y-centro.y,ch.position.x-centro.x)
                            base.copy(giro=normalizarGiro(base.giro+Math.toDegrees((b-a).toDouble()).toFloat()))
                        } else if(cercaAsa) {
                            val d=(ch.position-centro)/min(r.width,r.height)
                            val a=Math.toRadians(base.giro.toDouble()); val co=cos(a).toFloat(); val si=sin(a).toFloat()
                            val x=abs(d.x*co+d.y*si); val y=abs(-d.x*si+d.y*co)
                            if(base.tipo==FormaRecorte.CIRCULO || base.tipo==FormaRecorte.CUADRADO) base.copy(ancho=(2*max(x,y)).coerceIn(.02f,2f))
                            else base.copy(ancho=(2*x/aspecto.first).coerceIn(.02f,2f),alto=(2*y/aspecto.second).coerceIn(.02f,2f))
                        } else base.copy(x=(base.x+(ch.position.x-abajo.position.x)/r.width).coerceIn(-.5f,1.5f),y=(base.y+(ch.position.y-abajo.position.y)/r.height).coerceIn(-.5f,1.5f))
                        cambiarMascara(e,true) { trabajo }
                        ev.changes.forEach { it.consume() }
                    }
                    if(!movido && r.contains(abajo.position) && !cercaAsa && !cercaGiro)
                        cambiarMascara(e,true) { it.copy(x=(abajo.position.x-r.left)/r.width,y=(abajo.position.y-r.top)/r.height) }
                } finally { e.terminarGesto() }
            }
        }) {
        val r=rectanguloFuente(size.width,size.height,razon)
        val m=actual
        fun px(p:Pair<Float,Float>)=Offset(r.left+p.first*r.width,r.top+p.second*r.height)
        clipRect(r.left,r.top,r.right,r.bottom) {
            val celda=10.dp.toPx()
            for(y in 0..ceil(r.height/celda).toInt()) for(x in 0..ceil(r.width/celda).toInt())
                drawRect(if((x+y)%2==0)Color(0xFF41464C)else Color(0xFF666B70),Offset(r.left+x*celda,r.top+y*celda),androidx.compose.ui.geometry.Size(celda,celda))
            val puntos=m.contorno(aspecto)
            val forma=Path().apply {
                puntos.forEachIndexed { i,v -> val p=px(v); if(i==0)moveTo(p.x,p.y)else lineTo(p.x,p.y) };close()
            }
            val visible=if(m.invertir)Path().apply { fillType=PathFillType.EvenOdd; addRect(r);addPath(forma) }else forma
            clipPath(visible) {
                if(imagen!=null) drawImage(imagen,
                    srcOffset=IntOffset((rc.izquierda*imagen.width).toInt(),(rc.arriba*imagen.height).toInt()),
                    srcSize=IntSize((rc.ancho*imagen.width).toInt().coerceAtLeast(1),(rc.alto*imagen.height).toInt().coerceAtLeast(1)),
                    dstOffset=IntOffset(r.left.roundToInt(),r.top.roundToInt()),dstSize=IntSize(r.width.roundToInt(),r.height.roundToInt()))
                else drawRect(Ink.White30,r.topLeft,r.size)
            }
            drawPath(forma,Ink.White,style=Stroke(1.5.dp.toPx()))
        }
        if(m.tipo!=FormaRecorte.LINEAL) for(p in m.asas(aspecto)) {
            val q=px(p);drawRect(Ink.Black,q-Offset(5.dp.toPx(),5.dp.toPx()),androidx.compose.ui.geometry.Size(10.dp.toPx(),10.dp.toPx()))
            drawRect(Ink.White,q-Offset(3.dp.toPx(),3.dp.toPx()),androidx.compose.ui.geometry.Size(6.dp.toPx(),6.dp.toPx()))
        }
        val g=px(m.aUv(0f to -m.semiejes(aspecto).second-.12f,aspecto))
        val top=px(m.aUv(0f to -m.semiejes(aspecto).second,aspecto))
        drawLine(Ink.White70,top,g,1.dp.toPx());drawCircle(Ink.Black,7.dp.toPx(),g);drawCircle(Ink.White,5.dp.toPx(),g)
        val centro=px(m.x to m.y);drawCircle(Ink.White,3.dp.toPx(),centro)
    }
}

private fun normalizarGiro(a:Float):Float = ((a+180f)%360f+360f)%360f-180f
