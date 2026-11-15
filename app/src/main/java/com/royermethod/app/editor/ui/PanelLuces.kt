package com.royermethod.app.editor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.royermethod.app.editor.modelo.*
import com.royermethod.app.ui.components.*
import com.royermethod.app.ui.theme.Ink
import kotlin.math.*

private fun luzElegida(e: EstadoEditor): LuzTrazo? = e.clipElegido?.luces?.let { ls ->
    ls.firstOrNull { it.id == e.luzElegida } ?: ls.lastOrNull()
}
private fun relojLuz(e: EstadoEditor): Long {
    val c=e.clipElegido ?: return 0L
    val local=(e.tiempo-(e.inicioElegido ?: 0L)).coerceIn(0,c.duracionUs)
    return local+c.desfasePropiedadesUs
}
private fun cambiarLuz(e: EstadoEditor, arrastre: Boolean = false, f: (LuzTrazo) -> LuzTrazo) {
    val id = luzElegida(e)?.id ?: return
    e.cambiarClip(arrastre) { c -> c.copy(luces = c.luces.map { if(it.id == id) f(it) else it }) }
}
private fun estiloLuz(e: EstadoEditor, arrastre: Boolean = false, f: (EstiloLuz) -> EstiloLuz) {
    val t = relojLuz(e)
    cambiarLuz(e,arrastre) { it.editar(t,f) }
}
internal fun ponerClaveLuz(e: EstadoEditor, quitar: Boolean) {
    if(e.localElegido == null)return
    val luz = luzElegida(e) ?: return
    val t = relojLuz(e)
    val cerca = luz.claves.minByOrNull { abs(it.tiempoUs-t) }?.takeIf { abs(it.tiempoUs-t) <= toleranciaClaves() }
    if(quitar && cerca == null) return
    val tiempo = cerca?.tiempoUs ?: t
    cambiarLuz(e) { if(quitar) it.quitarClave(tiempo) else it.ponerClave(tiempo) }
    e.inicioElegido?.let { e.pausarEn(it + tiempo - (e.clipElegido?.desfasePropiedadesUs ?: 0L)) }
}

/** El consejo de como se mueve una luz sale una vez por sesion. */
private var consejoLuzVisto = false

private val COLORES_DE_LUZ = listOf(0xFF70E8FF, 0xFFBD7BFF, 0xFFFF61CB, 0xFFFFE37A, 0xFF91FF9E, 0xFFFFFFFF).map { it.toInt() }

/**
 * Luces: arriba, las que tiene el bloque; debajo, la elegida en dos pestanas
 * (donde esta y como se ve). "Animar" saca sus keyframes sin cambiar de
 * pestana y "Agregar" ensena las cuatro clases. Sin ninguna, la hoja es solo
 * esas cuatro casillas.
 */
@Composable
internal fun HojaLuces(e: EstadoEditor, cerrar: () -> Unit) {
    val c = e.clipElegido ?: return
    val luz = luzElegida(e)
    var dibujar by rememberSaveable(c.id) { mutableStateOf(false) }
    var agregarAbierto by rememberSaveable(c.id) { mutableStateOf(false) }
    var tab by rememberSaveable(c.id) { mutableIntStateOf(0) }
    var animar by rememberSaveable(c.id) { mutableStateOf(false) }
    var curvas by rememberSaveable(c.id) { mutableStateOf(false) }
    val catalogo = !dibujar && (agregarAbierto || c.luces.isEmpty())
    fun agregar(tipo: TipoLuz, puntos: List<PuntoLuz>? = null) {
        var nuevo = 0L
        e.cambiarClip { actual ->
            nuevo = (actual.luces.maxOfOrNull { it.id } ?: 0L) + 1L
            val pts = puntos ?: listOf(PuntoLuz(.42f,.44f),PuntoLuz(.58f,.56f))
            actual.copy(luces = actual.luces + LuzTrazo(nuevo,"${tipo.titulo} $nuevo",tipo,pts))
        }
        e.luzElegida = nuevo
        dibujar = false
        agregarAbierto = false
        tab = 0
        if (!consejoLuzVisto) {
            consejoLuzVisto = true
            e.avisar("Mueve la luz en el recuadro")
        }
    }
    val volver = agregarAbierto || dibujar
    HojaPanel(Panel.LUCES.titulo, cerrar, claveDesplazamiento = listOf(c.id, luz?.id, tab, catalogo, dibujar),
        acciones = if (c.luces.isNotEmpty() || dibujar) ({
            if (luz != null && !volver) Chip("Animar", animar, { animar = !animar }, marca = luz.claves.isNotEmpty())
            BotonTexto(if (volver) "Volver" else "Agregar", { if (volver) { agregarAbierto = false; dibujar = false } else agregarAbierto = true },
                glifo = if (volver) Glifos.atras else Glifos.mas)
        }) else null,
        fijo = {
            if (c.luces.isNotEmpty() && !dibujar) Tira(Modifier.padding(bottom = 2.dp)) {
                for (l in c.luces) {
                    val color = Color(l.estiloEn(relojLuz(e)).color)
                    PastillaElemento(l.nombre, l.id == luz?.id && !catalogo, { e.luzElegida = l.id; dibujar = false; agregarAbierto = false },
                        conClaves = l.claves.isNotEmpty()) { iconoLuz(l.tipo, color) }
                }
            }
            if (luz != null && !catalogo && !dibujar) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Pestanas(listOf("Colocar", "Estilo"), tab, { tab = it }, Modifier.weight(1f))
                    BotonIcono(Glifos.duplicar, {
                        e.cambiarClip { a ->
                            val id = (a.luces.maxOfOrNull { it.id } ?: 0L) + 1L
                            e.luzElegida = id; a.copy(luces = a.luces + luz.copy(id = id, nombre = "${luz.nombre} copia"))
                        }
                    }, tam = 38.dp, descripcion = "Duplicar luz")
                    BotonIcono(Glifos.papelera, { e.cambiarClip { a -> a.copy(luces = a.luces.filter { it.id != luz.id }) } },
                        tam = 38.dp, descripcion = "Eliminar luz")
                }
                if (animar) TiempoLuces(e, luz, curvas) {
                    if (!curvas && luz.claves.size < 2) e.avisar("Pon dos keyframes para elegir la curva") else curvas = !curvas
                }
            }
        },
        // Elegir la clase de luz son cuatro casillas: la hoja se queda en eso
        corta = catalogo
    ) {
        when {
            catalogo -> Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (tipo in TipoLuz.values()) Mosaico(tipo.titulo, false, {
                    if (tipo == TipoLuz.TRAZO) { dibujar = true; agregarAbierto = false } else agregar(tipo)
                }, Modifier.weight(1f)) { color -> iconoLuz(tipo, color) }
            }
            dibujar -> {
                LienzoLuces(e, c, luz, true, fuenteRecorte(e, c, preciso = true), 190.dp) { agregar(TipoLuz.TRAZO, it) }
                Nota("Dibuja el trazo con el dedo sobre la imagen.", Modifier.padding(top = 6.dp))
            }
            luz != null -> {
                if (animar) CurvaLuz(e, luz, curvas)
                if (tab == 0) {
                    // Bajo, para que la primera medida asome; con la hoja ampliada, comodo
                    val alto = if (LocalTamanoPanelEditor.current?.ampliado == true) 200.dp else 124.dp
                    LienzoLuces(e, c, luz, false, fuenteRecorte(e, c, preciso = true), alto) { }
                    Spacer(Modifier.height(4.dp))
                    ControlesColocarLuz(e, luz)
                } else ControlesEstiloLuz(e, luz, animar)
            }
        }
    }
}

/** Colocar: tamano y giro; en las que no son un trazo, tambien el ancho y el alto de su caja. */
@Composable
private fun ControlesColocarLuz(e:EstadoEditor,luz:LuzTrazo) {
    val v=luz.estiloEn(relojLuz(e))
    Deslizador("Tamaño",v.escala,.1f,4f,{ x -> estiloLuz(e,true) { it.copy(escala=x) } },neutro=1f,texto={ "%.2f×".format(it) })
    Deslizador("Giro",v.giro,-180f,180f,{ x -> estiloLuz(e,true) { it.copy(giro=x) } },neutro=0f,texto={ "${it.roundToInt()}°" })
    if(luz.tipo!=TipoLuz.TRAZO && luz.puntos.size>=2) {
        val ancho=luz.puntos.maxOf { it.x }-luz.puntos.minOf { it.x }
        val alto=luz.puntos.maxOf { it.y }-luz.puntos.minOf { it.y }
        Deslizador("Anchura",ancho,.01f,1f,{ x -> cajaLuz(e,x,null) })
        Deslizador("Altura",alto,.01f,1f,{ y -> cajaLuz(e,null,y) })
    }
}

/**
 * Estilo: color, fuerza, grosor y resplandor; en trazos y anillos, que tramo se
 * ve. Con "Animar", delante van las animaciones hechas: ponen dos keyframes
 * desde el cabezal y luego se afinan como cualquier otro.
 */
@Composable
private fun ControlesEstiloLuz(e: EstadoEditor, luz: LuzTrazo, animar: Boolean) {
    val v=luz.estiloEn(relojLuz(e))
    val porTramo = luz.tipo==TipoLuz.TRAZO || luz.tipo==TipoLuz.ANILLO
    if (animar) Fila("Rápidas") {
        Tira(Modifier.weight(1f)) {
            val activo = e.localElegido != null
            if (porTramo) {
                BotonBorde("Revelar", { animacionLuz(e, 0) }, activo = activo)
                BotonBorde("Recorrer", { animacionLuz(e, 1) }, activo = activo)
                BotonBorde("Desvanecer", { animacionLuz(e, 2) }, activo = activo)
            } else BotonBorde("Encender", { animacionLuz(e, 3) }, activo = activo)
        }
    }
    FilaColor(COLORES_DE_LUZ, v.color) { color, arrastre -> estiloLuz(e, arrastre) { it.copy(color = color) } }
    Deslizador("Intensidad",v.opacidad,0f,1f,{ x -> estiloLuz(e,true) { it.copy(opacidad=x) } },neutro=1f)
    Deslizador(if(luz.tipo==TipoLuz.FOCO) "Núcleo" else "Grosor",v.grosor,.001f,.06f,{ x -> estiloLuz(e,true) { it.copy(grosor=x) } },neutro=.006f)
    Deslizador("Resplandor",v.halo,0f,.15f,{ x -> estiloLuz(e,true) { it.copy(halo=x) } },neutro=.025f)
    if(porTramo) {
        Etiqueta("Tramo que se ve")
        Deslizador("Desde",v.inicio,0f,1f,{ x -> estiloLuz(e,true) { it.copy(inicio=x.coerceAtMost(it.fin)) } },neutro=0f)
        Deslizador("Hasta",v.fin,0f,1f,{ x -> estiloLuz(e,true) { it.copy(fin=x.coerceAtLeast(it.inicio)) } },neutro=1f)
    }
}

/** La curva del tramo de keyframes en el que esta el cabezal; se abre con su boton de la franja. */
@Composable
private fun CurvaLuz(e: EstadoEditor, luz: LuzTrazo, curvas: Boolean) {
    if (!curvas) return
    val orden = luz.claves.sortedBy { it.tiempoUs }
    val k = if (orden.size < 2) null else orden[orden.indexOfLast { it.tiempoUs <= relojLuz(e) }.coerceIn(0, orden.size - 2)]
    if (k != null) SelectorCurva(k.curva, k.bezier, k.velocidad) { cu, b, vel, arr ->
        cambiarLuz(e, arr) { l -> l.copy(claves = l.claves.map { if (it.tiempoUs == k.tiempoUs) it.copy(curva = cu, bezier = b, velocidad = vel) else it }) }
    }
}

private fun cajaLuz(e:EstadoEditor,ancho:Float?,alto:Float?) {
    cambiarLuz(e,true) { l ->
        val cx=(l.puntos.minOf { it.x }+l.puntos.maxOf { it.x })/2
        val cy=(l.puntos.minOf { it.y }+l.puntos.maxOf { it.y })/2
        val w=ancho ?: (l.puntos.maxOf { it.x }-l.puntos.minOf { it.x })
        val h=alto ?: (l.puntos.maxOf { it.y }-l.puntos.minOf { it.y })
        l.copy(puntos=listOf(PuntoLuz(cx-w/2,cy-h/2),PuntoLuz(cx+w/2,cy+h/2)))
    }
}

/** Una animación inicial que se puede afinar después en sus claves y curvas. */
internal fun animacionLuz(e:EstadoEditor,modo:Int) {
    val c=e.clipElegido?:return
    val local=e.localElegido ?: run { e.avisar("Mueve el cabezal dentro del elemento");return }
    val a=local+c.desfasePropiedadesUs
    val b=(local+600_000L).coerceAtMost(c.duracionUs)+c.desfasePropiedadesUs
    if(b<=a)return
    cambiarLuz(e) { l ->
        val v=l.estiloEn(a)
        val ini=when(modo) { 0 -> v.copy(inicio=0f,fin=0f);1 -> v.copy(inicio=0f,fin=.18f);3 -> v.copy(opacidad=0f);else -> v.copy(inicio=0f,fin=1f) }
        val fin=when(modo) { 0 -> v.copy(inicio=0f,fin=1f);1 -> v.copy(inicio=.82f,fin=1f);3 -> v.copy(opacidad=1f);else -> v.copy(inicio=1f,fin=1f) }
        l.copy(claves=(l.claves.filter { it.tiempoUs !in a..b }+ClaveLuz(a,ini)+ClaveLuz(b,fin)).sortedBy { it.tiempoUs })
    }
}

/** El tramo del bloque con los keyframes de la luz, igual que el de las demas hojas. */
@Composable
private fun TiempoLuces(e: EstadoEditor, l: LuzTrazo, curvas: Boolean, onCurvas: () -> Unit) {
    val c = e.clipElegido ?: return
    val ini = e.inicioElegido ?: return
    val local = (e.tiempo - ini).coerceIn(0, c.duracionUs)
    val ts = l.claves.map { it.tiempoUs - c.desfasePropiedadesUs }.filter { it in 0..c.duracionUs }.sorted()
    FranjaClaves(c.id, c.duracionUs, local, ts, toleranciaClaves(),
        onIr = { e.pausarEn(ini + it) },
        onPoner = { ponerClaveLuz(e, false) }, onQuitar = { ponerClaveLuz(e, true) },
        puedePoner = e.localElegido != null, de = " de la luz", curva = curvas, onCurva = onCurvas)
}

private fun DrawScope.iconoLuz(tipo:TipoLuz,color:Color) {
    val w=size.width;val h=size.height
    when(tipo) {
        TipoLuz.TRAZO -> drawPath(Path().apply { moveTo(w*.12f,h*.8f);cubicTo(w*.2f,-h*.2f,w*.8f,h*1.2f,w*.9f,h*.2f) },color,style=Stroke(2.dp.toPx(),cap=StrokeCap.Round))
        TipoLuz.FOCO -> { for(i in 5 downTo 1) drawCircle(color.copy(alpha=.12f),size.minDimension*.08f*i);drawCircle(Color.White,size.minDimension*.08f) }
        TipoLuz.ESTRELLA -> { drawLine(color,Offset(w/2,0f),Offset(w/2,h),2.dp.toPx());drawLine(color,Offset(0f,h/2),Offset(w,h/2),2.dp.toPx()) }
        TipoLuz.ANILLO -> drawCircle(color,size.minDimension*.35f,style=Stroke(2.dp.toPx()))
    }
}

private fun marcoLuz(w:Float,h:Float,razon:Float):Rect {
    val a=min(w-24f,(h-24f)*razon).coerceAtLeast(1f);val b=a/razon
    return Rect((w-a)/2,(h-b)/2,(w+a)/2,(h+b)/2)
}

@Composable
private fun LienzoLuces(e:EstadoEditor,c:Clip,luz:LuzTrazo?,dibujar:Boolean,imagen:ImageBitmap?,alto:Dp,agregar:(List<PuntoLuz>)->Unit) {
    val rc=c.encuadre.recorte
    val o=c.origen
    val razon=if(imagen!=null)imagen.width.toFloat()*rc.ancho/(imagen.height*rc.alto)
        else if(o!=null && o.altoVisible>0)o.anchoVisible.toFloat()*rc.ancho/(o.altoVisible*rc.alto)else 1f
    var borrador by remember(c.id) { mutableStateOf<List<PuntoLuz>>(emptyList()) }
    val añadir by rememberUpdatedState(agregar)
    val actual by rememberUpdatedState(luz)
    Canvas(Modifier.fillMaxWidth().height(alto).clip(esquinas(12.dp)).background(Ink.White04)
        .semantics { contentDescription = if (dibujar) "Dibujar el trazo de luz" else "Mover, agrandar o girar la luz" }
        .pointerInput(c.id,dibujar,razon,luz?.id,imagen) {
            if(imagen==null)return@pointerInput
            if(dibujar) detectDragGestures(onDragStart={ p ->
                val r=marcoLuz(size.width.toFloat(),size.height.toFloat(),razon)
                borrador=if(r.contains(p))listOf(PuntoLuz((p.x-r.left)/r.width,(p.y-r.top)/r.height))else emptyList()
            },onDragEnd={ if(borrador.size>=2)añadir(borrador);borrador=emptyList() },onDragCancel={ borrador=emptyList() }) { ch,_ ->
                ch.consume();val r=marcoLuz(size.width.toFloat(),size.height.toFloat(),razon)
                if(borrador.isNotEmpty() && r.contains(ch.position)) {
                    val p=PuntoLuz((ch.position.x-r.left)/r.width,(ch.position.y-r.top)/r.height)
                    val last=borrador.last()
                    if(hypot((p.x-last.x)*r.width,(p.y-last.y)*r.height)>2.dp.toPx()) {
                        val pts=borrador+p
                        borrador=if(pts.size>128)pts.filterIndexed { i,_ -> i%2==0 || i==pts.lastIndex }else pts
                    }
                }
            } else awaitEachGesture {
                val down=awaitFirstDown()
                val base=actual?.estiloEn(relojLuz(e))?:return@awaitEachGesture
                val r=marcoLuz(size.width.toFloat(),size.height.toFloat(),razon)
                if(!r.contains(down.position))return@awaitEachGesture
                var v=base;e.empezarGesto()
                try {
                    while(true) {
                        val ev=awaitPointerEvent()
                        if(ev.changes.none { it.pressed })break
                        val pan=ev.calculatePan()
                        v=v.copy(x=(v.x+pan.x/r.width).coerceIn(-2f,2f),y=(v.y+pan.y/r.height).coerceIn(-2f,2f),
                            escala=(v.escala*ev.calculateZoom()).coerceIn(.1f,4f),giro=((v.giro+ev.calculateRotation()+540f)%360f)-180f)
                        estiloLuz(e,true) { v };ev.changes.forEach { it.consume() }
                    }
                }finally { e.terminarGesto() }
            }
        }) {
        val r=marcoLuz(size.width,size.height,razon)
        clipRect(r.left,r.top,r.right,r.bottom) {
            if(imagen!=null) drawImage(imagen,
                srcOffset=IntOffset((rc.izquierda*imagen.width).toInt(),(rc.arriba*imagen.height).toInt()),
                srcSize=IntSize((rc.ancho*imagen.width).toInt().coerceAtLeast(1),(rc.alto*imagen.height).toInt().coerceAtLeast(1)),
                dstOffset=IntOffset(r.left.roundToInt(),r.top.roundToInt()),dstSize=IntSize(r.width.roundToInt(),r.height.roundToInt()))
            for(l in c.luces) guiaLuz(l,l.estiloEn(relojLuz(e)),r,l.id==luz?.id)
            if(borrador.isNotEmpty())guiaLuz(LuzTrazo(-1,"",TipoLuz.TRAZO,borrador),EstiloLuz(),r,true)
        }
    }
}

/** Guía de colocación: el resultado de luz completo se ve en el lienzo principal. */
private fun DrawScope.guiaLuz(l:LuzTrazo,v:EstiloLuz,r:Rect,elegida:Boolean) {
    if(l.puntos.isEmpty())return
    val cx=(l.puntos.minOf { it.x }+l.puntos.maxOf { it.x })/2
    val cy=(l.puntos.minOf { it.y }+l.puntos.maxOf { it.y })/2
    val a=Math.toRadians(v.giro.toDouble());val co=cos(a).toFloat();val si=sin(a).toFloat()
    fun px(p:PuntoLuz):Offset { val x=(p.x-cx)*r.width*v.escala;val y=(p.y-cy)*r.height*v.escala
        return Offset(r.left+(cx+v.x)*r.width+x*co-y*si,r.top+(cy+v.y)*r.height+x*si+y*co) }
    val color=Color(v.color)
    val path=Path()
    if(l.tipo==TipoLuz.TRAZO) l.recorrido(v,r.width/r.height).forEachIndexed { i,p ->
        val q=Offset(r.left+p.x*r.width,r.top+p.y*r.height)
        if(i==0)path.moveTo(q.x,q.y)else path.lineTo(q.x,q.y)
    }
    else {
        val w=(l.puntos.maxOf { it.x }-l.puntos.minOf { it.x })/2
        val h=(l.puntos.maxOf { it.y }-l.puntos.minOf { it.y })/2
        val n=if(l.tipo==TipoLuz.ESTRELLA)8 else 64
        for(i in 0 until n) {
            val avance=if(l.tipo==TipoLuz.ANILLO)v.inicio+(v.fin-v.inicio)*i/(n-1f)else i.toFloat()/n
            val ang=-PI/2+avance*2*PI;val radio=if(l.tipo==TipoLuz.ESTRELLA && i%2==1).12f else 1f
            val q=px(PuntoLuz(cx+cos(ang).toFloat()*w*radio,cy+sin(ang).toFloat()*h*radio));if(i==0)path.moveTo(q.x,q.y)else path.lineTo(q.x,q.y)
        }
        if(l.tipo!=TipoLuz.ANILLO)path.close()
    }
    val g=max(1.dp.toPx(),v.grosor*min(r.width,r.height))
    for(i in 4 downTo 1)drawPath(path,color.copy(alpha=.07f*v.opacidad),style=Stroke(g+v.halo*min(r.width,r.height)*i/2,cap=StrokeCap.Round,join=StrokeJoin.Round))
    if(l.tipo==TipoLuz.FOCO || l.tipo==TipoLuz.ESTRELLA)drawPath(path,color.copy(alpha=.3f*v.opacidad))
    drawPath(path,color.copy(alpha=.9f*v.opacidad),style=Stroke(g,cap=StrokeCap.Round,join=StrokeJoin.Round))
    if(elegida)drawCircle(Color.White,3.dp.toPx(),px(PuntoLuz(cx,cy)))
}
