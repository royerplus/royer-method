package com.royermethod.app.editor.modelo

import kotlin.math.*

/** Coordenadas relativas a la parte visible del elemento, antes de su encuadre. */
data class PuntoLuz(val x: Float, val y: Float)
enum class TipoLuz(val titulo: String) { TRAZO("Trazo"), FOCO("Foco"), ESTRELLA("Estrella"), ANILLO("Anillo") }

data class EstiloLuz(
    val color: Int = 0xFF70E8FF.toInt(),
    val x: Float = 0f, val y: Float = 0f,
    val escala: Float = 1f, val giro: Float = 0f,
    val opacidad: Float = 1f,
    val grosor: Float = .006f, val halo: Float = .025f,
    val inicio: Float = 0f, val fin: Float = 1f
) {
    fun acotado(): EstiloLuz {
        fun n(v: Float, d: Float, a: Float, b: Float) = if(v.isFinite()) v.coerceIn(a,b) else d
        return copy(x=n(x,0f,-2f,2f),y=n(y,0f,-2f,2f),escala=n(escala,1f,.02f,8f),
            giro=n(giro,0f,-1080f,1080f),opacidad=n(opacidad,1f,0f,1f),
            grosor=n(grosor,.006f,.0005f,.15f),halo=n(halo,.025f,0f,.3f),
            inicio=n(inicio,0f,0f,1f),fin=n(fin,1f,0f,1f))
    }
    fun mezclar(b: EstiloLuz,t: Float): EstiloLuz {
        fun m(a: Float,v: Float)=a+(v-a)*t
        var c=0
        for(s in listOf(0,8,16,24)) c=c or (m(((color ushr s)and 255).toFloat(),((b.color ushr s)and 255).toFloat()).roundToInt().coerceIn(0,255) shl s)
        return EstiloLuz(c,m(x,b.x),m(y,b.y),m(escala,b.escala),m(giro,b.giro),m(opacidad,b.opacidad),
            m(grosor,b.grosor),m(halo,b.halo),m(inicio,b.inicio),m(fin,b.fin)).acotado()
    }
}

data class ClaveLuz(val tiempoUs: Long,val estilo: EstiloLuz,val curva: Curva = Curva.SUAVE,
    val bezier: Bezier? = null,val velocidad: CurvaVelocidad? = null) {
    fun avance(t: Float)=velocidad?.en(t) ?: bezier?.en(t) ?: Escena.curva(curva,t)
}

/** Cada luz tiene su reloj y curva propios; el reloj usa local + desfasePropiedadesUs del clip. */
data class LuzTrazo(
    val id: Long,
    val nombre: String,
    val tipo: TipoLuz,
    val puntos: List<PuntoLuz>,
    val estilo: EstiloLuz = EstiloLuz(),
    val claves: List<ClaveLuz> = emptyList()
) {
    private val ordenadas by lazy { claves.sortedBy { it.tiempoUs }.distinctBy { it.tiempoUs } }
    fun estiloEn(t: Long): EstiloLuz {
        val ks=ordenadas
        if(ks.isEmpty())return estilo.acotado()
        if(t<=ks.first().tiempoUs)return ks.first().estilo.acotado()
        if(t>=ks.last().tiempoUs)return ks.last().estilo.acotado()
        val i=ks.indexOfLast { it.tiempoUs<=t };val a=ks[i];val b=ks[i+1]
        return a.estilo.mezclar(b.estilo,a.avance((t-a.tiempoUs).toFloat()/(b.tiempoUs-a.tiempoUs)))
    }
    fun ponerClave(t: Long): LuzTrazo {
        val vieja=claves.firstOrNull { it.tiempoUs==t }
        return copy(claves=(claves.filter { it.tiempoUs!=t }+(vieja?.copy(estilo=estiloEn(t)) ?: ClaveLuz(t,estiloEn(t)))).sortedBy { it.tiempoUs })
    }
    fun quitarClave(t: Long): LuzTrazo {
        val actual=estiloEn(t)
        return copy(estilo=actual,claves=claves.filter { it.tiempoUs!=t })
    }
    fun editar(t: Long,f: (EstiloLuz)->EstiloLuz): LuzTrazo {
        val nuevo=f(estiloEn(t)).acotado()
        if(claves.isEmpty())return copy(estilo=nuevo)
        val vieja=claves.firstOrNull { it.tiempoUs==t }
        return copy(claves=(claves.filter { it.tiempoUs!=t }+(vieja?.copy(estilo=nuevo) ?: ClaveLuz(t,nuevo))).sortedBy { it.tiempoUs })
    }
    fun tiempoEscalado(f: Double)=copy(claves=claves.map { it.copy(tiempoUs=(it.tiempoUs*f).toLong()) })

    /** Puntos visibles después del recorte por longitud, giro, escala y desplazamiento. */
    fun recorrido(e: EstiloLuz,aspecto: Float): List<PuntoLuz> {
        if(puntos.isEmpty())return emptyList()
        val ar=aspecto.coerceAtLeast(.01f)
        val ax=if(ar>=1f)ar else 1f;val ay=if(ar>=1f)1f else 1f/ar
        val loX=puntos.minOf { it.x };val hiX=puntos.maxOf { it.x };val loY=puntos.minOf { it.y };val hiY=puntos.maxOf { it.y }
        val cx=(loX+hiX)/2;val cy=(loY+hiY)/2
        val r=Math.toRadians(e.giro.toDouble());val co=cos(r).toFloat();val si=sin(r).toFloat()
        val ps=puntos.map { p ->
            val x=(p.x-cx)*ax*e.escala;val y=(p.y-cy)*ay*e.escala
            PuntoLuz(cx+e.x+(x*co-y*si)/ax,cy+e.y+(x*si+y*co)/ay)
        }
        if(tipo!=TipoLuz.TRAZO || ps.size<2)return ps
        if(e.fin<=e.inicio)return emptyList()
        val longitudes=ps.zipWithNext().map { (a,b)->hypot((b.x-a.x)*ax,(b.y-a.y)*ay) }
        val total=longitudes.sum()
        if(total<=.00001f)return if(e.inicio==0f && e.fin>0f)ps.take(1)else emptyList()
        val desde=e.inicio*total;val hasta=e.fin*total
        var acum=0f
        val salida=ArrayList<PuntoLuz>()
        for(i in longitudes.indices) {
            val l=longitudes[i];val fin=acum+l
            if(l>.000001f && fin>=desde && acum<=hasta) {
                val a=ps[i];val b=ps[i+1]
                fun p(t: Float)=PuntoLuz(a.x+(b.x-a.x)*t,a.y+(b.y-a.y)*t)
                val primero=p(((desde-acum)/l).coerceIn(0f,1f));val ultimo=p(((hasta-acum)/l).coerceIn(0f,1f))
                if(salida.isEmpty() || salida.last()!=primero)salida.add(primero)
                if(salida.last()!=ultimo)salida.add(ultimo)
            }
            acum=fin
        }
        return salida
    }
}
