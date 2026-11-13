package com.royermethod.app.editor

import com.royermethod.app.editor.modelo.*
import org.json.JSONArray
import org.json.JSONObject

/** Datos opcionales: los proyectos sin luces no cambian al cargarlos. */
internal object PersistenciaLuces {
    fun escribir(luces: List<LuzTrazo>)=JSONArray().apply {
        luces.forEach { luz -> put(JSONObject().apply {
            put("id",luz.id);put("nombre",luz.nombre);put("tipo",luz.tipo.name)
            put("puntos",JSONArray().apply { luz.puntos.forEach { p -> put(p.x.toDouble());put(p.y.toDouble()) } })
            put("estilo",estilo(luz.estilo))
            put("claves",JSONArray().apply { luz.claves.forEach { k -> put(JSONObject().apply {
                put("t",k.tiempoUs);put("estilo",estilo(k.estilo));put("curva",k.curva.name)
                k.bezier?.let { b -> put("bezier",JSONArray(listOf(b.x1,b.y1,b.x2,b.y2).map { it.toDouble() })) }
                k.velocidad?.let { put("velocidad",Persistencia.velocidad(it)) }
            }) } })
        }) }
    }
    private fun estilo(e: EstiloLuz)=JSONObject().apply {
        val a=e.acotado()
        put("color",a.color);put("x",a.x.toDouble());put("y",a.y.toDouble());put("escala",a.escala.toDouble());put("giro",a.giro.toDouble())
        put("opacidad",a.opacidad.toDouble());put("grosor",a.grosor.toDouble());put("halo",a.halo.toDouble());put("inicio",a.inicio.toDouble());put("fin",a.fin.toDouble())
    }
    private fun leerEstilo(o: JSONObject?): EstiloLuz {
        if(o==null)return EstiloLuz()
        val d=EstiloLuz()
        fun n(k:String,v:Float)=o.optDouble(k,v.toDouble()).toFloat()
        return EstiloLuz(o.optInt("color",d.color),n("x",d.x),n("y",d.y),n("escala",d.escala),n("giro",d.giro),
            n("opacidad",d.opacidad),n("grosor",d.grosor),n("halo",d.halo),n("inicio",d.inicio),n("fin",d.fin)).acotado()
    }
    fun leer(a: JSONArray?): List<LuzTrazo> {
        if(a==null)return emptyList()
        return (0 until minOf(a.length(),128)).mapNotNull { i ->
            val o=a.optJSONObject(i) ?: return@mapNotNull null
            val tipo=TipoLuz.values().firstOrNull { it.name==o.optString("tipo") } ?: return@mapNotNull null
            val p=o.optJSONArray("puntos") ?: return@mapNotNull null
            val puntos=(0 until minOf(p.length()/2,512)).mapNotNull { j ->
                val x=p.optDouble(j*2,Double.NaN).toFloat();val y=p.optDouble(j*2+1,Double.NaN).toFloat()
                if(x.isFinite() && y.isFinite())PuntoLuz(x.coerceIn(-2f,3f),y.coerceIn(-2f,3f))else null
            }
            if(puntos.isEmpty())return@mapNotNull null
            val ks=o.optJSONArray("claves")
            val claves=if(ks==null)emptyList()else (0 until minOf(ks.length(),1024)).mapNotNull { j ->
                val k=ks.optJSONObject(j) ?: return@mapNotNull null
                val b=k.optJSONArray("bezier")?.takeIf { it.length()==4 }?.let { ar ->
                    val v=FloatArray(4) { ar.optDouble(it,Double.NaN).toFloat() }
                    if(v.all { it.isFinite() })Bezier(v[0],v[1],v[2],v[3]) else null
                }
                ClaveLuz(k.optLong("t"),leerEstilo(k.optJSONObject("estilo")),Curva.values().firstOrNull { it.name==k.optString("curva") } ?: Curva.SUAVE,
                    b,Persistencia.velocidadDe(k.optJSONArray("velocidad")))
            }.sortedBy { it.tiempoUs }.distinctBy { it.tiempoUs }
            LuzTrazo(o.optLong("id",i+1L),o.optString("nombre",tipo.titulo).take(80),tipo,puntos,leerEstilo(o.optJSONObject("estilo")),claves)
        }
    }
}
