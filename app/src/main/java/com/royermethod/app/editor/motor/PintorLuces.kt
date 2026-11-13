package com.royermethod.app.editor.motor

import android.opengl.GLES20
import com.royermethod.app.editor.modelo.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.*

/** Luz vectorial: solo rasteriza las cajas de sus segmentos, nunca el lienzo completo. */
internal class PintorLuces {
    private val programa=Programa(VERTICE,FRAGMENTO)
    private var datos=ByteBuffer.allocateDirect(4096*4).order(ByteOrder.nativeOrder()).asFloatBuffer()

    fun dibujar(p: PasoClip,ancho: Int,alto: Int,texAncho: Int,texAlto: Int,voltear: Boolean) {
        if(p.esFondo || p.clip.luces.isEmpty())return
        val rc=p.clip.encuadre.recorte
        val fw=texAncho*rc.ancho;val fh=texAlto*rc.alto
        val ar=fw/fh.coerceAtLeast(1f)
        val ax=if(ar>=1f)ar else 1f;val ay=if(ar>=1f)1f else 1f/ar
        programa.usar()
        // Pantalla mantiene detalle del iris y permite cruzar halos sin bordes oscuros.
        GLES20.glBlendFunc(GLES20.GL_ONE,GLES20.GL_ONE_MINUS_SRC_COLOR)
        GLES20.glUniform2f(programa.u("uLienzo"),ancho.toFloat(),alto.toFloat())
        GLES20.glUniform1f(programa.u("uVoltear"),if(voltear)1f else 0f)
        val region=p.recorteFuente
        GLES20.glUniform4f(programa.u("uCorte"),max(rc.izquierda,region?.izquierda ?: 0f),max(rc.arriba,region?.arriba ?: 0f),
            min(1f-rc.derecha,region?.derecha ?: 1f),min(1f-rc.abajo,region?.abajo ?: 1f))
        val forma=p.clip.recorteVisual.mascara
        GLES20.glUniform1f(programa.u("uFormaTipo"),forma.tipo.ordinal.toFloat())
        if(forma.tipo!=FormaRecorte.NINGUNA) {
            GLES20.glUniform2f(programa.u("uFormaTam"),ax,ay)
            GLES20.glUniform4f(programa.u("uRecorte"),rc.izquierda,rc.arriba,1f-rc.derecha,1f-rc.abajo)
            GLES20.glUniform2f(programa.u("uFormaCentro"),forma.x,forma.y)
            GLES20.glUniform4f(programa.u("uFormaDatos"),forma.ancho,forma.alto,Math.toRadians(forma.giro.toDouble()).toFloat(),forma.pluma)
            GLES20.glUniform1f(programa.u("uFormaRadio"),forma.radio)
            GLES20.glUniform1f(programa.u("uFormaInvertir"),if(forma.invertir)1f else 0f)
            val ps=forma.poligono()
            GLES20.glUniform1f(programa.u("uPoligonoN"),ps.size.toFloat())
            if(ps.isNotEmpty())GLES20.glUniform2fv(programa.u("uPoligono[0]"),ps.size,ps.flatMap { listOf(it.first,it.second) }.toFloatArray(),0)
        }
        val m=p.mascara
        GLES20.glUniform1f(programa.u("uConMascara"),if(m==null)0f else 1f)
        if(m!=null) {
            GLES20.glUniform4f(programa.u("uMasc"),m.dirX,m.dirY,m.suave.coerceAtLeast(.5f),m.grosor)
            GLES20.glUniform4f(programa.u("uMascDatos"),m.centroX,m.centroY,if(m.radial)1f else 0f,if(m.invertida)1f else 0f)
        }
        val reloj=p.tiempoLocalUs+p.clip.desfasePropiedadesUs
        for(luz in p.clip.luces) {
            val e=luz.estiloEn(reloj)
            if(e.opacidad<=.0001f || e.fin<=e.inicio || luz.puntos.isEmpty())continue
            val color=e.color
            GLES20.glUniform4f(programa.u("uColor"),((color ushr 16)and 255)/255f,((color ushr 8)and 255)/255f,(color and 255)/255f,((color ushr 24)and 255)/255f)
            GLES20.glUniform2f(programa.u("uLuz"),e.grosor*.5f,e.halo)
            val foco=luz.tipo==TipoLuz.FOCO
            GLES20.glUniform1f(programa.u("uFoco"),if(foco)1f else 0f)
            val ps=luz.puntos
            val cx=(ps.minOf { it.x }+ps.maxOf { it.x })/2
            val cy=(ps.minOf { it.y }+ps.maxOf { it.y })/2
            val coseno=cos(Math.toRadians(e.giro.toDouble())).toFloat();val seno=sin(Math.toRadians(e.giro.toDouble())).toFloat()
            val vertices=ArrayList<Float>()
            fun vert(x: Float,y: Float,dx: Float,dy: Float,l: Float) {
                val xx=(x*coseno-y*seno)*e.escala;val yy=(x*seno+y*coseno)*e.escala
                vertices.add(rc.izquierda+(cx+e.x+xx/ax)*rc.ancho)
                vertices.add(rc.arriba+(cy+e.y+yy/ay)*rc.alto)
                vertices.add(dx);vertices.add(dy);vertices.add(l)
            }
            fun caminoUnido(camino: List<PuntoLuz>) {
                if(camino.isEmpty())return
                val metricos=camino.map { PuntoLuz((it.x-cx)*ax,(it.y-cy)*ay) }.filterIndexed { i,p ->
                    i==0 || hypot((camino[i].x-camino[i-1].x)*ax,(camino[i].y-camino[i-1].y)*ay)>.00001f
                }
                if(metricos.isEmpty())return
                val margen=e.grosor*.5f+e.halo*3.5f+.003f
                val ns=metricos.zipWithNext().map { (a,b) ->
                    val l=hypot(b.x-a.x,b.y-a.y).coerceAtLeast(.00001f)
                    PuntoLuz(-(b.y-a.y)/l,(b.x-a.x)/l)
                }
                if(ns.isEmpty()) {
                    val p=metricos.first()
                    fun v(x:Float,y:Float)=vert(p.x+x,p.y+y,x,y,0f)
                    v(-margen,-margen);v(margen,-margen);v(-margen,margen)
                    v(-margen,margen);v(margen,-margen);v(margen,margen)
                    return
                }
                fun union(i:Int):PuntoLuz {
                    if(i==0)return PuntoLuz(ns.first().x*margen,ns.first().y*margen)
                    if(i==metricos.lastIndex)return PuntoLuz(ns.last().x*margen,ns.last().y*margen)
                    val a=ns[i-1];val b=ns[i]
                    val l=hypot(a.x+b.x,a.y+b.y)
                    if(l<.01f)return PuntoLuz(b.x*margen,b.y*margen)
                    val x=(a.x+b.x)/l;val y=(a.y+b.y)/l
                    val den=(x*b.x+y*b.y).coerceAtLeast(.25f)
                    return PuntoLuz(x*margen/den,y*margen/den)
                }
                for(i in ns.indices) {
                    val a=metricos[i];val b=metricos[i+1];val n=ns[i]
                    val ux=n.y;val uy=-n.x;val len=hypot(b.x-a.x,b.y-a.y)
                    val u=union(i);val v=union(i+1)
                    val extA=if(i==0)margen else 0f;val extB=if(i==ns.lastIndex)margen else 0f
                    val corners=listOf(PuntoLuz(a.x-u.x-ux*extA,a.y-u.y-uy*extA),
                        PuntoLuz(b.x-v.x+ux*extB,b.y-v.y+uy*extB),
                        PuntoLuz(a.x+u.x-ux*extA,a.y+u.y-uy*extA),
                        PuntoLuz(b.x+v.x+ux*extB,b.y+v.y+uy*extB))
                    fun c(j:Int) { val q=corners[j];val dx=q.x-a.x;val dy=q.y-a.y
                        vert(q.x,q.y,dx*ux+dy*uy,dx*n.x+dy*n.y,len) }
                    c(0);c(1);c(2);c(2);c(1);c(3)
                }
            }
            if(foco) {
                val rx=((ps.maxOf { it.x }-ps.minOf { it.x })*ax*.5f).coerceAtLeast(.002f)
                val ry=((ps.maxOf { it.y }-ps.minOf { it.y })*ay*.5f).coerceAtLeast(.002f)
                GLES20.glUniform2f(programa.u("uRadio"),rx,ry)
                val mx=rx+e.halo*3.5f;val my=ry+e.halo*3.5f
                fun v(x: Float,y: Float)=vert(x,y,x,y,0f)
                v(-mx,-my);v(mx,-my);v(-mx,my);v(-mx,my);v(mx,-my);v(mx,my)
            } else {
                val camino=when(luz.tipo) {
                    TipoLuz.TRAZO -> luz.copy(estilo=e.copy(x=0f,y=0f,escala=1f,giro=0f)).recorrido(e.copy(x=0f,y=0f,escala=1f,giro=0f),ar)
                    TipoLuz.ANILLO -> {
                        val rx=(ps.maxOf { it.x }-ps.minOf { it.x })/2;val ry=(ps.maxOf { it.y }-ps.minOf { it.y })/2
                        List(97) { i -> val a=(e.inicio+(e.fin-e.inicio)*i/96f)*PI*2-PI/2;PuntoLuz(cx+cos(a).toFloat()*rx,cy+sin(a).toFloat()*ry) }
                    }
                    TipoLuz.ESTRELLA -> {
                        val rx=(ps.maxOf { it.x }-ps.minOf { it.x })/2;val ry=(ps.maxOf { it.y }-ps.minOf { it.y })/2
                        // Cruz principal y diagonales cortas, todas unidas por el centro.
                        listOf(PuntoLuz(cx-rx,cy),PuntoLuz(cx,cy),PuntoLuz(cx+rx,cy),PuntoLuz(cx,cy),PuntoLuz(cx,cy-ry),PuntoLuz(cx,cy),PuntoLuz(cx,cy+ry),PuntoLuz(cx,cy),
                            PuntoLuz(cx-rx*.48f,cy-ry*.48f),PuntoLuz(cx,cy),PuntoLuz(cx+rx*.48f,cy+ry*.48f),PuntoLuz(cx,cy),PuntoLuz(cx+rx*.48f,cy-ry*.48f),PuntoLuz(cx,cy),PuntoLuz(cx-rx*.48f,cy+ry*.48f))
                    }
                    else -> emptyList()
                }
                caminoUnido(camino)
            }
            if(vertices.isEmpty())continue
            if(datos.capacity()<vertices.size)datos=ByteBuffer.allocateDirect(vertices.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer()
            datos.clear();vertices.forEach { datos.put(it) };datos.flip()
            val pos=GLES20.glGetAttribLocation(programa.id,"aUv");val dist=GLES20.glGetAttribLocation(programa.id,"aDatos")
            GLES20.glEnableVertexAttribArray(pos);GLES20.glEnableVertexAttribArray(dist)
            datos.position(0);GLES20.glVertexAttribPointer(pos,2,GLES20.GL_FLOAT,false,20,datos)
            datos.position(2);GLES20.glVertexAttribPointer(dist,3,GLES20.GL_FLOAT,false,20,datos)
            // El obturador no debe oscurecer una luz inmóvil al repetir OVER/Pantalla.
            val quieta=p.matrices.all { it.contentEquals(p.matrices.first()) } && p.bordes.all { it==p.bordes.first() }
            val indices=if(quieta)listOf(0)else p.matrices.indices.toList()
            for(i in indices) {
                val directa=inversa(p.matrices[i]) ?: continue
                GLES20.glUniformMatrix3fv(programa.u("uDirecta"),1,false,directa,0)
                GLES20.glUniform1f(programa.u("uOpacidad"),e.opacidad*(if(quieta)p.opacidades.average().toFloat()else p.opacidades[i]/p.matrices.size))
                GLES20.glUniform1f(programa.u("uBorde"),p.bordes.getOrElse(i) { 20000f })
                val medida=(min(p.anchoEnLienzo,p.altoEnLienzo)*e.escala).coerceAtLeast(1f)
                GLES20.glUniform1f(programa.u("uPixel"),1f/medida)
                GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,vertices.size/5)
            }
            GLES20.glDisableVertexAttribArray(pos);GLES20.glDisableVertexAttribArray(dist)
        }
    }
    fun soltar()=programa.soltar()

    companion object {
        internal fun inversa(m: FloatArray): FloatArray? {
            val a=m[0];val b=m[3];val c=m[6];val d=m[1];val e=m[4];val f=m[7];val g=m[2];val h=m[5];val i=m[8]
            val aa=e*i-f*h;val bb=c*h-b*i;val cc=b*f-c*e
            val dd=f*g-d*i;val ee=a*i-c*g;val ff=c*d-a*f
            val gg=d*h-e*g;val hh=b*g-a*h;val ii=a*e-b*d
            val det=a*aa+b*dd+c*gg
            if(!det.isFinite() || abs(det)<1e-14f)return null
            return floatArrayOf(aa,dd,gg,bb,ee,hh,cc,ff,ii).map { it/det }.toFloatArray()
        }
        const val VERTICE="""
attribute vec2 aUv;
attribute vec3 aDatos;
uniform mat3 uDirecta;
uniform mediump vec2 uLienzo;
uniform lowp float uVoltear;
varying mediump vec2 vUv;
varying mediump vec3 vDatos;
void main() {
    vec3 p=uDirecta*vec3(aUv,1.0);
    float y=p.z-2.0*p.y/uLienzo.y;
    if(uVoltear>0.5)y=-y;
    gl_Position=vec4(2.0*p.x/uLienzo.x-p.z,y,0.0,p.z);
    vUv=aUv;vDatos=aDatos;
}
"""
        const val FRAGMENTO="""
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
varying mediump vec2 vUv;
varying mediump vec3 vDatos;
uniform mediump vec2 uLienzo;
uniform lowp float uVoltear;
uniform vec4 uCorte;
uniform vec4 uColor;
uniform vec2 uLuz;
uniform float uOpacidad;
uniform float uPixel;
uniform float uFoco;
uniform vec2 uRadio;
uniform float uConMascara;
uniform vec4 uMasc;
uniform vec4 uMascDatos;
uniform float uBorde;
uniform float uFormaTipo;
uniform vec2 uFormaCentro;
uniform vec4 uFormaDatos;
uniform float uFormaRadio;
uniform float uFormaInvertir;
uniform vec2 uFormaTam;
uniform vec4 uRecorte;
uniform vec2 uPoligono[24];
uniform float uPoligonoN;
float cajaRedonda(vec2 p, vec2 medio, float r) {
    vec2 q=abs(p)-medio+vec2(r);
    return length(max(q,0.0))+min(max(q.x,q.y),0.0)-r;
}
float distanciaPoligono(vec2 p, vec2 escala) {
    vec2 a = uPoligono[0] * escala;
    // El borde de cierre se toma desde el último vértice, sin índices dinámicos de ES2.
    for (int j=0; j<24; j++) { if (float(j) < uPoligonoN) a = uPoligono[j] * escala; }
    float distancia = 100.0;
    float signo = 1.0;
    for (int j=0; j<24; j++) {
        if (float(j) >= uPoligonoN) break;
        vec2 b = uPoligono[j] * escala;
        vec2 arista = b - a;
        vec2 v = p - a;
        vec2 cerca = v - arista * clamp(dot(v,arista) / max(dot(arista,arista),0.000001),0.0,1.0);
        distancia = min(distancia, length(cerca));
        if ((a.y > p.y) != (b.y > p.y)) {
            float cruce = (b.x-a.x) * (p.y-a.y) / (b.y-a.y) + a.x;
            if (p.x < cruce) signo = -signo;
        }
        a = b;
    }
    return distancia * signo;
}

float formaEn(vec2 uv, vec2 pixel) {
    if (uFormaTipo < 0.5) return 1.0;
    vec2 tam = max(uRecorte.zw - uRecorte.xy,vec2(0.0001));
    vec2 q = ((uv - uRecorte.xy) / tam - uFormaCentro) * uFormaTam;
    float co = cos(uFormaDatos.z); float si = sin(uFormaDatos.z);
    q = mat2(co,-si,si,co) * q;
    vec2 medio = max(uFormaDatos.xy * uFormaTam * 0.5,vec2(0.0001));
    if (uFormaTipo < 2.5) medio = vec2(max(uFormaDatos.x * 0.5,0.0001));
    float d;
    if (uFormaTipo < 1.5) d = length(q) - medio.x;
    else if (uFormaTipo < 4.5) d = cajaRedonda(q,medio,uFormaTipo > 3.5 ? min(uFormaRadio,min(medio.x,medio.y)) : 0.0);
    else if (uFormaTipo < 5.5) d = (length(q / medio) - 1.0) * min(medio.x,medio.y);
    else if (uFormaTipo < 6.5) d = (abs(q.x)/medio.x + abs(q.y)/medio.y - 1.0) * min(medio.x,medio.y) * 0.7071;
    else if (uFormaTipo < 8.5) d = distanciaPoligono(q,medio);
    else d = -q.y;
    float aa = max(max(pixel.x * uFormaTam.x / tam.x,pixel.y * uFormaTam.y / tam.y),0.00001);
    float alfa = clamp(0.5 - d / max(uFormaDatos.w,aa),0.0,1.0);
    return uFormaInvertir > 0.5 ? 1.0 - alfa : alfa;
}


void main() {
    if(vUv.x<uCorte.x || vUv.y<uCorte.y || vUv.x>uCorte.z || vUv.y>uCorte.w)discard;
    float core;float halo;
    if(uFoco>0.5) {
        float q=length(vDatos.xy/max(uRadio,vec2(0.0001)));
        float concentracion=2.5/(1.0+6.0*uLuz.x/max(min(uRadio.x,uRadio.y),0.0001));
        core=exp(-q*q*concentracion);
        float d=max(q-0.65,0.0)*min(uRadio.x,uRadio.y);
        halo=uLuz.y>0.0 ? exp(-d/max(uLuz.y,0.0001))*0.5 : 0.0;
    } else {
        float d=length(vec2(max(max(-vDatos.x,vDatos.x-vDatos.z),0.0),vDatos.y));
        core=1.0-smoothstep(max(uLuz.x-uPixel,0.0),uLuz.x+uPixel,d);
        halo=uLuz.y>0.0 ? exp(-max(d-uLuz.x,0.0)/max(uLuz.y,0.0001))*0.55 : 0.0;
    }
    float mascara=1.0;
    if(uConMascara>0.5) {
        vec2 pixel=vec2(gl_FragCoord.x,uVoltear>0.5 ? gl_FragCoord.y : uLienzo.y-gl_FragCoord.y);
        vec2 q=pixel-(0.5*uLienzo+uMascDatos.xy);
        float d=uMascDatos.z>0.5 ? length(q) : dot(q,uMasc.xy);
        mascara=clamp((uBorde-d)/uMasc.z+0.5,0.0,1.0);
        if(uMasc.w>0.0 && abs(uBorde)<15000.0)mascara*=clamp((d-(uBorde-uMasc.w))/uMasc.z+0.5,0.0,1.0);
        if(uMascDatos.w>0.5)mascara=1.0-mascara;
    }
    if(uFormaTipo>0.5)mascara*=formaEn(vUv,vec2(uPixel)*max(uRecorte.zw-uRecorte.xy,vec2(0.0001))/max(uFormaTam,vec2(0.0001)));
    float a=clamp(core+halo,0.0,1.0)*uOpacidad*uColor.a*mascara;
    vec3 rgb=mix(uColor.rgb,vec3(1.0),core*0.8)*a;
    gl_FragColor=vec4(rgb,a);
}
"""
    }
}
