package com.royermethod.app.editor.modelo

import kotlin.math.*

/** Coordenadas relativas a la parte visible del elemento, antes de su encuadre. */
data class PuntoLuz(val x: Float, val y: Float)
/**
 * CONTORNO no tiene forma propia: sigue el borde de la figura del elemento (su
 * alfa, su croma y su mascara). Sus [LuzTrazo.puntos] son el camino por el que
 * se enciende ese contorno, o uno solo si se enciende entero.
 */
enum class TipoLuz(val titulo: String) { TRAZO("Trazo"), FOCO("Foco"), ESTRELLA("Estrella"), ANILLO("Anillo"), CONTORNO("Contorno") }

/** Donde se deja ver una luz: en todo el elemento, solo sobre su figura o solo por detras de ella. */
enum class AlcanceLuz(val titulo: String) { TODO("Todo"), DENTRO("Dentro"), DETRAS("Detras") }

data class EstiloLuz(
    val color: Int = 0xFF70E8FF.toInt(),
    val x: Float = 0f, val y: Float = 0f,
    val escala: Float = 1f, val giro: Float = 0f,
    val opacidad: Float = 1f,
    val grosor: Float = .006f, val halo: Float = .025f,
    val inicio: Float = 0f, val fin: Float = 1f,
    /** Luz de contorno: cuanto entra la luz en la figura (misma medida que [halo], que es lo que sale). */
    val dentro: Float = 0f,
    /** Luz de contorno: de 0 a 1, cuanto se enciende la figura entera. */
    val relleno: Float = 0f,
    /** Luz de contorno: hasta que distancia de su camino se enciende el contorno. */
    val zona: Float = .08f,
    /** Luz de contorno: de 0 (todo el contorno) a 1 (solo el que mira hacia [angulo]). */
    val dirigida: Float = 0f,
    /** De donde viene la luz, en grados: 0 arriba, 90 a la derecha. */
    val angulo: Float = 0f
) {
    fun acotado(): EstiloLuz {
        fun n(v: Float, d: Float, a: Float, b: Float) = if(v.isFinite()) v.coerceIn(a,b) else d
        return copy(x=n(x,0f,-2f,2f),y=n(y,0f,-2f,2f),escala=n(escala,1f,.02f,8f),
            giro=n(giro,0f,-1080f,1080f),opacidad=n(opacidad,1f,0f,1f),
            grosor=n(grosor,.006f,0f,.15f),halo=n(halo,.025f,0f,.3f),
            inicio=n(inicio,0f,0f,1f),fin=n(fin,1f,0f,1f),
            dentro=n(dentro,0f,0f,.3f),relleno=n(relleno,0f,0f,1f),zona=n(zona,.08f,.01f,1f),
            dirigida=n(dirigida,0f,0f,1f),angulo=n(angulo,0f,-1080f,1080f))
    }
    fun mezclar(b: EstiloLuz,t: Float): EstiloLuz {
        fun m(a: Float,v: Float)=a+(v-a)*t
        var c=0
        for(s in listOf(0,8,16,24)) c=c or (m(((color ushr s)and 255).toFloat(),((b.color ushr s)and 255).toFloat()).roundToInt().coerceIn(0,255) shl s)
        return EstiloLuz(c,m(x,b.x),m(y,b.y),m(escala,b.escala),m(giro,b.giro),m(opacidad,b.opacidad),
            m(grosor,b.grosor),m(halo,b.halo),m(inicio,b.inicio),m(fin,b.fin),
            m(dentro,b.dentro),m(relleno,b.relleno),m(zona,b.zona),m(dirigida,b.dirigida),m(angulo,b.angulo)).acotado()
    }
}

/**
 * Aspectos hechos de una luz de contorno. Cambian como se ve (linea,
 * resplandor, luz que entra y relleno) y dejan lo demas: su color, su fuerza,
 * su zona y de que lado viene.
 */
enum class AspectoContorno(val titulo: String,val grosor: Float,val halo: Float,val dentro: Float,val relleno: Float) {
    /** Una linea fina con algo de luz a los dos lados: asi nace una luz de contorno. */
    LINEA("Linea",.006f,.03f,.03f,0f),
    NEON("Neon",.012f,.07f,.02f,0f),
    /** La luz de detras: asoma por el borde y entra en la figura. */
    CONTRALUZ("Contraluz",.008f,.02f,.07f,0f),
    /** Sin linea ni resplandor: solo la luz que entra. */
    INTERIOR("Interior",0f,0f,.1f,0f),
    /** La figura entera encendida. */
    SILUETA("Silueta",0f,.03f,0f,1f),
    /** Sin linea: solo el resplandor de fuera. */
    AURA("Aura",0f,.1f,0f,0f);

    fun en(e: EstiloLuz)=e.copy(grosor=grosor,halo=halo,dentro=dentro,relleno=relleno)
    fun es(e: EstiloLuz)=e.grosor==grosor && e.halo==halo && e.dentro==dentro && e.relleno==relleno
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
    val claves: List<ClaveLuz> = emptyList(),
    val alcance: AlcanceLuz = AlcanceLuz.TODO
) {
    /** Una luz de contorno sin camino: enciende el contorno entero. */
    val entera: Boolean get() = tipo==TipoLuz.CONTORNO && puntos.size<2

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

    /**
     * Un destello: en [t] la luz aparece de golpe y hasta [finUs] se apaga
     * mientras se afina. Un fotograma antes esta apagada, asi que se puede
     * repetir en cada golpe de la musica. [inicioUs] es el reloj de la luz al
     * empezar el elemento: antes de el no se pone nada.
     */
    fun conDestello(t: Long,finUs: Long,inicioUs: Long): LuzTrazo {
        if(finUs<=t)return this
        val pico=picoDeDestello(t)
        val apagada=pico.copy(opacidad=0f,grosor=0f,dentro=0f,relleno=0f,halo=pico.halo*.4f)
        val antes=(t-1_000_000L/Linea.FPS).takeIf { it>=inicioUs }
        val nuevas=listOfNotNull(antes?.let { ClaveLuz(it,apagada,Curva.LINEAL) },ClaveLuz(t,pico,Curva.SALIDA),ClaveLuz(finUs,apagada))
        return copy(claves=(claves.filter { it.tiempoUs<(antes ?: t) || it.tiempoUs>finUs }+nuevas).sortedBy { it.tiempoUs })
    }

    /**
     * Como aparece un destello puesto en [t]: como esta la luz ahi. Si ahi se
     * esta apagando otro destello (los golpes van mas juntos de lo que dura),
     * o ya esta apagada, como era el ultimo en su punto mas alto; y sin
     * ninguno, como es sin animar.
     */
    private fun picoDeDestello(t: Long): EstiloLuz {
        val ks=ordenadas
        val antes=ks.lastOrNull { it.tiempoUs<=t }
        val despues=ks.firstOrNull { it.tiempoUs>t }
        if(antes!=null && despues!=null && antes.estilo.opacidad>VISIBLE && despues.estilo.opacidad<=0f)return antes.estilo.acotado()
        val ahora=estiloEn(t)
        if(ahora.opacidad>VISIBLE)return ahora
        val ultimo=ks.lastOrNull { it.tiempoUs<=t && it.estilo.opacidad>VISIBLE } ?: ks.firstOrNull { it.estilo.opacidad>VISIBLE }
        return ultimo?.estilo?.acotado() ?: estilo.acotado().let { if(it.opacidad>VISIBLE)it else it.copy(opacidad=1f) }
    }

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
        if((tipo!=TipoLuz.TRAZO && tipo!=TipoLuz.CONTORNO) || ps.size<2)return ps
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

    /**
     * Por donde se enciende una luz de contorno, en unidades del lado corto del
     * elemento: su camino ya movido, girado, a escala y recortado a su tramo,
     * resumido a [maximo] puntos. Vacio: el contorno entero. null: su tramo no
     * deja nada a la vista.
     */
    fun zonaContorno(e: EstiloLuz,aspecto: Float,maximo: Int): List<PuntoLuz>? {
        if(entera)return emptyList()
        val ar=aspecto.coerceAtLeast(.01f)
        val ax=if(ar>=1f)ar else 1f;val ay=if(ar>=1f)1f else 1f/ar
        val camino=recorrido(e,ar).map { PuntoLuz(it.x*ax,it.y*ay) }
        return if(camino.isEmpty())null else resumir(camino,maximo)
    }

    companion object {
        /** Parte del alcance de la zona en la que la luz de contorno va entera; de ahi al final se afina y se apaga. */
        const val ZONA_LLENA=.35f

        /** Por debajo de esta fuerza una luz cuenta como apagada. */
        private const val VISIBLE=.05f

        /**
         * Como mucho [n] puntos repartidos por igual a lo largo de [camino] (y
         * nunca menos de dos). La zona de una luz de contorno es ancha y suave: no
         * necesita los cien puntos de un trazo hecho con el dedo.
         */
        fun resumir(camino: List<PuntoLuz>,n: Int): List<PuntoLuz> {
            if(camino.isEmpty())return camino
            if(camino.size==1)return listOf(camino[0],camino[0])
            if(camino.size<=n)return camino
            val largos=camino.zipWithNext().map { (a,b)->hypot(b.x-a.x,b.y-a.y) }
            val total=largos.sum()
            if(total<=.000001f)return listOf(camino.first(),camino.last())
            val salida=ArrayList<PuntoLuz>(n)
            var i=0;var acum=0f
            for(k in 0 until n) {
                val d=total*k/(n-1)
                while(i<largos.lastIndex && acum+largos[i]<d) { acum+=largos[i];i++ }
                val t=if(largos[i]>.0000001f)((d-acum)/largos[i]).coerceIn(0f,1f)else 0f
                val a=camino[i];val b=camino[i+1]
                salida.add(PuntoLuz(a.x+(b.x-a.x)*t,a.y+(b.y-a.y)*t))
            }
            return salida
        }
    }
}
