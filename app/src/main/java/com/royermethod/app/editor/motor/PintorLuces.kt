package com.royermethod.app.editor.motor

import android.opengl.GLES20
import com.royermethod.app.editor.modelo.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.*

/**
 * Luz vectorial: solo rasteriza las cajas de sus segmentos, nunca el lienzo completo.
 *
 * Tres sombreadores. El de siempre, para las luces que se ven en todo el
 * elemento. El mismo con FIGURA definido, que ademas mira lo que se ve del
 * elemento (ver [FiguraShader]): para las luces que se quedan solo sobre la
 * figura o solo por detras. Y el de la luz de contorno, que no tiene forma propia
 * y sigue el contorno de la figura. Los dos ultimos se compilan al primer uso
 * y, si un telefono no los acepta, esas luces se dibujan sin ese extra (o no
 * se dibujan, las de contorno) y todo lo demas sigue igual.
 */
internal class PintorLuces {
    private val programa=Programa(VERTICE,FRAGMENTO)
    private var conFigura: Programa?=null
    private var contorno: Programa?=null
    private var sinFigura=false
    private var sinContorno=false
    private var datos=ByteBuffer.allocateDirect(4096*4).order(ByteOrder.nativeOrder()).asFloatBuffer()

    private fun aparte(falla: Boolean,hecho: Programa?,vertice: String,fragmento: String,anotar: (Programa?)->Unit): Programa? {
        if(falla)return null
        if(hecho!=null)return hecho
        val nuevo=try { Programa(vertice,fragmento) } catch(e: RuntimeException) {
            runCatching { android.util.Log.w("RoyerLuces","sombreador de luces: ${e.message}") };null
        }
        anotar(nuevo)
        return nuevo
    }
    private fun programaConFigura()=aparte(sinFigura,conFigura,"#define FIGURA\n$VERTICE","#define FIGURA\n$FRAGMENTO") { conFigura=it;sinFigura=it==null }
    private fun programaContorno()=aparte(sinContorno,contorno,FiguraShader.VERTICE_CONTORNO,FiguraShader.CONTORNO) { contorno=it;sinContorno=it==null }

    /** Lo que es igual para todas las luces del elemento, en el sombreador que sea. */
    private fun comunes(prog: Programa,p: PasoClip,ancho: Int,alto: Int,voltear: Boolean) {
        GLES20.glUniform2f(prog.u("uLienzo"),ancho.toFloat(),alto.toFloat())
        GLES20.glUniform1f(prog.u("uVoltear"),if(voltear)1f else 0f)
        val m=p.mascara
        GLES20.glUniform1f(prog.u("uConMascara"),if(m==null)0f else 1f)
        if(m!=null) {
            GLES20.glUniform4f(prog.u("uMasc"),m.dirX,m.dirY,m.suave.coerceAtLeast(.5f),m.grosor)
            GLES20.glUniform4f(prog.u("uMascDatos"),m.centroX,m.centroY,if(m.radial)1f else 0f,if(m.invertida)1f else 0f)
        }
    }

    /**
     * @param textura la imagen del elemento, de [texAncho] por [texAlto]
     * @param campo el de su figura si tiene luces de contorno (ver [CampoFigura])
     */
    fun dibujar(p: PasoClip,ancho: Int,alto: Int,textura: Int,texAncho: Int,texAlto: Int,voltear: Boolean,campo: CampoFigura.Campo?) {
        if(p.esFondo || p.clip.luces.isEmpty())return
        val rc=p.clip.encuadre.recorte
        val fw=texAncho*rc.ancho;val fh=texAlto*rc.alto
        val ar=fw/fh.coerceAtLeast(1f)
        val ax=if(ar>=1f)ar else 1f;val ay=if(ar>=1f)1f else 1f/ar
        // Pantalla mantiene detalle del iris y permite cruzar halos sin bordes oscuros.
        GLES20.glBlendFunc(GLES20.GL_ONE,GLES20.GL_ONE_MINUS_SRC_COLOR)
        val region=p.recorteFuente
        val corte=floatArrayOf(max(rc.izquierda,region?.izquierda ?: 0f),max(rc.arriba,region?.arriba ?: 0f),
            min(1f-rc.derecha,region?.derecha ?: 1f),min(1f-rc.abajo,region?.abajo ?: 1f))
        val forma=p.clip.recorteVisual.mascara
        var puesto: Programa?=null
        val reloj=p.tiempoLocalUs+p.clip.desfasePropiedadesUs
        for(luz in p.clip.luces) {
            val e=luz.estiloEn(reloj)
            if(e.opacidad<=.0001f || luz.puntos.isEmpty())continue
            if(luz.tipo==TipoLuz.CONTORNO) {
                if(campo!=null)dibujarContorno(p,luz,e,campo,ancho,alto,textura,texAncho,texAlto,voltear,corte)
                puesto=null
                continue
            }
            if(e.fin<=e.inicio)continue
            val figura=if(luz.alcance==AlcanceLuz.TODO)null else programaConFigura()
            val prog=figura ?: programa
            if(prog!==puesto) {
                puesto=prog
                prog.usar()
                comunes(prog,p,ancho,alto,voltear)
                if(figura!=null) {
                    GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,textura)
                    FiguraShader.cargar(figura,p.clip,texAncho,texAlto)
                } else {
                    GLES20.glUniform4f(prog.u("uCorte"),corte[0],corte[1],corte[2],corte[3])
                    GLES20.glUniform1f(prog.u("uFormaTipo"),forma.tipo.ordinal.toFloat())
                    if(forma.tipo!=FormaRecorte.NINGUNA) {
                        GLES20.glUniform2f(prog.u("uFormaTam"),ax,ay)
                        GLES20.glUniform4f(prog.u("uRecorte"),rc.izquierda,rc.arriba,1f-rc.derecha,1f-rc.abajo)
                        GLES20.glUniform2f(prog.u("uFormaCentro"),forma.x,forma.y)
                        GLES20.glUniform4f(prog.u("uFormaDatos"),forma.ancho,forma.alto,Math.toRadians(forma.giro.toDouble()).toFloat(),forma.pluma)
                        GLES20.glUniform1f(prog.u("uFormaRadio"),forma.radio)
                        GLES20.glUniform1f(prog.u("uFormaInvertir"),if(forma.invertir)1f else 0f)
                        val vs=forma.poligono()
                        GLES20.glUniform1f(prog.u("uPoligonoN"),vs.size.toFloat())
                        if(vs.isNotEmpty())GLES20.glUniform2fv(prog.u("uPoligono[0]"),vs.size,vs.flatMap { listOf(it.first,it.second) }.toFloatArray(),0)
                    }
                }
            }
            if(figura!=null) {
                // Por detras la luz puede salirse de la imagen (un halo tras la cabeza); una parte nunca sale de su region
                val detras=luz.alcance==AlcanceLuz.DETRAS
                GLES20.glUniform1f(prog.u("uAlcance"),if(detras)2f else 1f)
                if(detras && region==null)GLES20.glUniform4f(prog.u("uCorte"),-10f,-10f,10f,10f)
                else GLES20.glUniform4f(prog.u("uCorte"),corte[0],corte[1],corte[2],corte[3])
            }
            val color=e.color
            GLES20.glUniform4f(prog.u("uColor"),((color ushr 16)and 255)/255f,((color ushr 8)and 255)/255f,(color and 255)/255f,((color ushr 24)and 255)/255f)
            GLES20.glUniform2f(prog.u("uLuz"),e.grosor*.5f,e.halo)
            val foco=luz.tipo==TipoLuz.FOCO
            GLES20.glUniform1f(prog.u("uFoco"),if(foco)1f else 0f)
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
                GLES20.glUniform2f(prog.u("uRadio"),rx,ry)
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
            val pos=GLES20.glGetAttribLocation(prog.id,"aUv");val dist=GLES20.glGetAttribLocation(prog.id,"aDatos")
            GLES20.glEnableVertexAttribArray(pos);GLES20.glEnableVertexAttribArray(dist)
            datos.position(0);GLES20.glVertexAttribPointer(pos,2,GLES20.GL_FLOAT,false,20,datos)
            datos.position(2);GLES20.glVertexAttribPointer(dist,3,GLES20.GL_FLOAT,false,20,datos)
            // El obturador no debe oscurecer una luz inmóvil al repetir OVER/Pantalla.
            val quieta=p.matrices.all { it.contentEquals(p.matrices.first()) } && p.bordes.all { it==p.bordes.first() }
            val indices=if(quieta)listOf(0)else p.matrices.indices.toList()
            for(i in indices) {
                val directa=inversa(p.matrices[i]) ?: continue
                GLES20.glUniformMatrix3fv(prog.u("uDirecta"),1,false,directa,0)
                GLES20.glUniform1f(prog.u("uOpacidad"),e.opacidad*(if(quieta)p.opacidades.average().toFloat()else p.opacidades[i]/p.matrices.size))
                GLES20.glUniform1f(prog.u("uBorde"),p.bordes.getOrElse(i) { 20000f })
                val medida=(min(p.anchoEnLienzo,p.altoEnLienzo)*e.escala).coerceAtLeast(1f)
                GLES20.glUniform1f(prog.u("uPixel"),1f/medida)
                GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,vertices.size/5)
            }
            GLES20.glDisableVertexAttribArray(pos);GLES20.glDisableVertexAttribArray(dist)
        }
    }
    /**
     * La luz de contorno: un rectangulo sobre lo que puede llegar a encenderse y
     * el sombreador hace el resto con la imagen y el campo de la figura.
     */
    private fun dibujarContorno(p: PasoClip,luz: LuzTrazo,e: EstiloLuz,campo: CampoFigura.Campo,ancho: Int,alto: Int,
                             textura: Int,texAncho: Int,texAlto: Int,voltear: Boolean,corte: FloatArray) {
        val rc=p.clip.encuadre.recorte
        val (ax,ay)=FiguraShader.unidad(p.clip,texAncho,texAlto)
        // Por donde se enciende, en unidades del lado corto. null: su tramo no deja nada a la vista.
        val camino=luz.zonaContorno(e,ax/ay,MAX_CAMINO) ?: return
        val m=CampoFigura.MARGEN
        // Sin resplandor nada sale de la imagen; con camino, nada pasa de su alcance
        var x0=-m;var y0=-m;var x1=ax+m;var y1=ay+m
        if(e.halo<=0f) { x0=0f;y0=0f;x1=ax;y1=ay }
        if(camino.isNotEmpty()) {
            x0=max(x0,camino.minOf { it.x }-e.zona);y0=max(y0,camino.minOf { it.y }-e.zona)
            x1=min(x1,camino.maxOf { it.x }+e.zona);y1=min(y1,camino.maxOf { it.y }+e.zona)
        }
        if(x1<=x0 || y1<=y0)return
        val prog=programaContorno() ?: return
        prog.usar()
        comunes(prog,p,ancho,alto,voltear)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,campo.fino)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE2);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,campo.medio)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE3);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,campo.ancha)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,textura)
        FiguraShader.cargar(prog,p.clip,texAncho,texAlto)
        GLES20.glUniform1i(prog.u("sCampoA"),1);GLES20.glUniform1i(prog.u("sCampoB"),2);GLES20.glUniform1i(prog.u("sCampoC"),3)
        GLES20.glUniform4f(prog.u("uCampo"),-m,-m,1f/campo.ladoX,1f/campo.ladoY)
        GLES20.glUniform1f(prog.u("uSigma0"),campo.sigma0)
        GLES20.glUniform2f(prog.u("uUnidad"),ax,ay)
        GLES20.glUniform4f(prog.u("uCorte"),corte[0],corte[1],corte[2],corte[3])
        // El resplandor puede salirse de la imagen; una parte nunca sale de su region
        if(p.recorteFuente==null)GLES20.glUniform4f(prog.u("uLimite"),-10f,-10f,10f,10f)
        else GLES20.glUniform4f(prog.u("uLimite"),corte[0],corte[1],corte[2],corte[3])
        val color=e.color
        GLES20.glUniform4f(prog.u("uColor"),((color ushr 16)and 255)/255f,((color ushr 8)and 255)/255f,(color and 255)/255f,((color ushr 24)and 255)/255f)
        GLES20.glUniform4f(prog.u("uLuz"),e.grosor,e.halo,e.dentro,e.relleno)
        val angulo=Math.toRadians(e.angulo.toDouble())
        GLES20.glUniform3f(prog.u("uDireccion"),e.dirigida,sin(angulo).toFloat(),-cos(angulo).toFloat())
        GLES20.glUniform1f(prog.u("uCaminoN"),camino.size.toFloat())
        if(camino.isNotEmpty())GLES20.glUniform2fv(prog.u("uCamino[0]"),camino.size,FloatArray(camino.size*2) { i -> if(i%2==0)camino[i/2].x else camino[i/2].y },0)
        GLES20.glUniform2f(prog.u("uZona"),e.zona,e.zona*LuzTrazo.ZONA_LLENA)
        fun u(x: Float)=rc.izquierda+x/ax*rc.ancho
        fun v(y: Float)=rc.arriba+y/ay*rc.alto
        datos.clear()
        for(q in floatArrayOf(u(x0),v(y0),u(x1),v(y0),u(x0),v(y1),u(x0),v(y1),u(x1),v(y0),u(x1),v(y1)))datos.put(q)
        datos.flip()
        val pos=GLES20.glGetAttribLocation(prog.id,"aUv")
        GLES20.glEnableVertexAttribArray(pos)
        datos.position(0);GLES20.glVertexAttribPointer(pos,2,GLES20.GL_FLOAT,false,8,datos)
        val quieta=p.matrices.all { it.contentEquals(p.matrices.first()) } && p.bordes.all { it==p.bordes.first() }
        val indices=if(quieta)listOf(0)else p.matrices.indices.toList()
        GLES20.glUniform1f(prog.u("uPixel"),1f/min(p.anchoEnLienzo,p.altoEnLienzo).coerceAtLeast(1f))
        for(i in indices) {
            val directa=inversa(p.matrices[i]) ?: continue
            GLES20.glUniformMatrix3fv(prog.u("uDirecta"),1,false,directa,0)
            GLES20.glUniform1f(prog.u("uOpacidad"),e.opacidad*(if(quieta)p.opacidades.average().toFloat()else p.opacidades[i]/p.matrices.size))
            GLES20.glUniform1f(prog.u("uBorde"),p.bordes.getOrElse(i) { 20000f })
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,6)
        }
        GLES20.glDisableVertexAttribArray(pos)
        // El campo no se queda puesto: en el fotograma siguiente se pinta encima de esas mismas laminas
        for(unidad in intArrayOf(GLES20.GL_TEXTURE3,GLES20.GL_TEXTURE2,GLES20.GL_TEXTURE1)) {
            GLES20.glActiveTexture(unidad);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,0)
        }
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
    }

    fun soltar() {
        programa.soltar()
        conFigura?.soltar();conFigura=null
        contorno?.soltar();contorno=null
    }


    companion object {
        /** Puntos del camino de una luz de contorno que caben en su sombreador. */
        const val MAX_CAMINO=32

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
#ifdef FIGURA
// Con esta uv se lee la imagen del elemento: en media precision bailaria un texel
varying vec2 vUv;
#else
varying mediump vec2 vUv;
#endif
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
#ifdef FIGURA
varying vec2 vUv;
#else
varying mediump vec2 vUv;
#endif
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
#ifdef FIGURA
uniform float uAlcance; // 1: solo sobre la figura; 2: solo por detras de ella
${FiguraShader.FIGURA}
#else
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
#endif

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
#ifdef FIGURA
    // La figura ya lleva su forma. Fuera de la imagen sigue su orilla (ver figura()).
    float f=figura(vUv,vec2(uPixel)*max(uRecorte.zw-uRecorte.xy,vec2(0.0001))/max(uFormaTam,vec2(0.0001)));
    mascara*=uAlcance<1.5 ? f : 1.0-f;
#else
    if(uFormaTipo>0.5)mascara*=formaEn(vUv,vec2(uPixel)*max(uRecorte.zw-uRecorte.xy,vec2(0.0001))/max(uFormaTam,vec2(0.0001)));
#endif
    float a=clamp(core+halo,0.0,1.0)*uOpacidad*uColor.a*mascara;
    vec3 rgb=mix(uColor.rgb,vec3(1.0),core*0.8)*a;
    gl_FragColor=vec4(rgb,a);
}
"""
    }
}
