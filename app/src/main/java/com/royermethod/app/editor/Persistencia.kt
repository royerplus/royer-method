package com.royermethod.app.editor

import com.royermethod.app.editor.modelo.Ajuste
import com.royermethod.app.editor.modelo.AjustesColor
import com.royermethod.app.editor.modelo.AjustesSalida
import com.royermethod.app.editor.modelo.Alineacion
import com.royermethod.app.editor.modelo.Animacion
import com.royermethod.app.editor.modelo.AnimacionContinua
import com.royermethod.app.editor.modelo.Calidad
import com.royermethod.app.editor.modelo.Capa
import com.royermethod.app.editor.modelo.Clave
import com.royermethod.app.editor.modelo.Clip
import com.royermethod.app.editor.modelo.Curva
import com.royermethod.app.editor.modelo.Encuadre
import com.royermethod.app.editor.modelo.Filtro
import com.royermethod.app.editor.modelo.Fondo
import com.royermethod.app.editor.modelo.Formato
import com.royermethod.app.editor.modelo.Fuente
import com.royermethod.app.editor.modelo.Mezcla
import com.royermethod.app.editor.modelo.Origen
import com.royermethod.app.editor.modelo.PistaAudio
import com.royermethod.app.editor.modelo.Proyecto
import com.royermethod.app.editor.modelo.Recorte
import com.royermethod.app.editor.modelo.Texto
import com.royermethod.app.editor.modelo.TipoOrigen
import com.royermethod.app.editor.modelo.Transicion
import org.json.JSONArray
import org.json.JSONObject

/**
 * El proyecto a texto y de vuelta, para guardarlo solo y no perder nada si
 * la app se cierra.
 *
 * A mano y no con una libreria de serializacion: son pocas clases, y asi
 * leer un proyecto guardado por una version anterior no depende de que los
 * nombres internos no cambien. Lo que falte se queda con su valor por
 * defecto; lo que no se entienda se ignora.
 */
object Persistencia {

    private const val VERSION = 1

    fun aTexto(p: Proyecto): String = proyecto(p).toString()

    fun deTexto(s: String): Proyecto? = runCatching { proyecto(JSONObject(s)) }.getOrNull()

    // ---- Escribir ----

    private fun proyecto(p: Proyecto) = JSONObject().apply {
        put("version", VERSION)
        put("formato", p.formato.name)
        put("fondo", p.fondo.name)
        put("salida", JSONObject().apply {
            put("ladoCorto", p.salida.ladoCorto)
            put("calidad", p.salida.calidad.name)
            put("hevc", p.salida.hevc)
            put("desenfoque", p.salida.desenfoqueMovimiento)
            put("muestras", p.salida.muestras)
        })
        put("principal", JSONArray().apply { p.principal.forEach { put(clip(it)) } })
        put("capas", JSONArray().apply {
            p.capas.forEach { c ->
                put(JSONObject().apply {
                    put("inicio", c.inicioUs)
                    put("fila", c.fila)
                    put("mezcla", c.mezcla.name)
                    put("clip", clip(c.clip))
                })
            }
        })
        put("audios", JSONArray().apply {
            p.audios.forEach { a ->
                put(JSONObject().apply {
                    put("id", a.id)
                    put("origen", origen(a.origen))
                    put("inicio", a.inicioUs)
                    put("desde", a.desdeUs)
                    put("hasta", a.hastaUs)
                    put("volumen", a.volumen.toDouble())
                    put("fundidoEntrada", a.fundidoEntradaUs)
                    put("fundidoSalida", a.fundidoSalidaUs)
                })
            }
        })
        put("siguienteId", p.siguienteId)
    }

    private fun origen(o: Origen) = JSONObject().apply {
        put("uri", o.uri)
        put("nombre", o.nombre)
        put("tipo", o.tipo.name)
        put("ancho", o.ancho)
        put("alto", o.alto)
        put("duracion", o.duracionUs)
        put("fps", o.fps.toDouble())
        put("conAudio", o.conAudio)
        put("giro", o.giro)
        put("bytes", o.bytes)
    }

    private fun clip(c: Clip): JSONObject = JSONObject().apply {
        put("id", c.id)
        c.origen?.let { put("origen", origen(it)) }
        c.texto?.let { put("texto", texto(it)) }
        put("desde", c.desdeUs)
        put("hasta", c.hastaUs)
        put("velocidad", c.velocidad.toDouble())
        put("volumen", c.volumen.toDouble())
        put("mantenerTono", c.mantenerTono)
        put("fundidoEntrada", c.fundidoEntradaUs)
        put("fundidoSalida", c.fundidoSalidaUs)
        put("color", color(c.color))
        put("filtro", c.filtro.name)
        put("intensidadFiltro", c.intensidadFiltro.toDouble())
        put("encuadre", encuadre(c.encuadre))
        put("claves", JSONArray().apply {
            c.claves.forEach { k ->
                put(JSONObject().apply {
                    put("t", k.tiempoUs)
                    put("encuadre", encuadre(k.encuadre))
                    put("curva", k.curva.name)
                })
            }
        })
        put("entrada", c.entrada.name)
        put("salida", c.salida.name)
        put("duracionEntrada", c.duracionEntradaUs)
        put("duracionSalida", c.duracionSalidaUs)
        put("continua", c.continua.name)
        put("transicion", c.transicion.name)
        put("duracionTransicion", c.duracionTransicionUs)
        put("fluido", c.fluido)
    }

    private fun texto(t: Texto) = JSONObject().apply {
        put("contenido", t.contenido)
        put("tamano", t.tamano.toDouble())
        put("color", t.color)
        put("fuente", t.fuente.name)
        put("negrita", t.negrita)
        put("contorno", t.contorno.toDouble())
        put("colorContorno", t.colorContorno)
        put("sombra", t.sombra)
        put("fondo", t.fondo)
        put("alineacion", t.alineacion.name)
        put("espaciado", t.espaciado.toDouble())
    }

    private fun encuadre(e: Encuadre) = JSONObject().apply {
        put("ajuste", e.ajuste.name)
        put("escala", e.escala.toDouble())
        put("x", e.x.toDouble())
        put("y", e.y.toDouble())
        put("rotacion", e.rotacion.toDouble())
        put("opacidad", e.opacidad.toDouble())
        put("espejo", e.espejo)
        put("recorte", JSONArray().apply {
            put(e.recorte.izquierda.toDouble()); put(e.recorte.arriba.toDouble())
            put(e.recorte.derecha.toDouble()); put(e.recorte.abajo.toDouble())
        })
    }

    private fun color(a: AjustesColor) = JSONArray().apply {
        listOf(a.brillo, a.contraste, a.saturacion, a.exposicion, a.calidez, a.tinte, a.sombras, a.luces, a.nitidez, a.vineta)
            .forEach { put(it.toDouble()) }
    }

    // ---- Leer ----

    private inline fun <reified E : Enum<E>> JSONObject.enumeracion(clave: String, defecto: E): E =
        optString(clave, "").let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: defecto

    private fun JSONObject.f(clave: String, defecto: Float) = optDouble(clave, defecto.toDouble()).toFloat()

    private fun proyecto(j: JSONObject): Proyecto {
        val s = j.optJSONObject("salida") ?: JSONObject()
        val d = AjustesSalida()
        val principal = j.optJSONArray("principal")?.let { a -> (0 until a.length()).mapNotNull { clipDe(a.getJSONObject(it)) } } ?: emptyList()
        val capas = j.optJSONArray("capas")?.let { a ->
            (0 until a.length()).mapNotNull { i ->
                val o = a.getJSONObject(i)
                val c = o.optJSONObject("clip")?.let { clipDe(it) } ?: return@mapNotNull null
                Capa(o.optLong("inicio"), o.optInt("fila"), c, o.enumeracion("mezcla", Mezcla.NORMAL))
            }
        } ?: emptyList()
        val audios = j.optJSONArray("audios")?.let { a ->
            (0 until a.length()).mapNotNull { i ->
                val o = a.getJSONObject(i)
                val origen = o.optJSONObject("origen")?.let { origenDe(it) } ?: return@mapNotNull null
                PistaAudio(
                    id = o.optLong("id"),
                    origen = origen,
                    inicioUs = o.optLong("inicio"),
                    desdeUs = o.optLong("desde"),
                    hastaUs = o.optLong("hasta", origen.duracionUs),
                    volumen = o.f("volumen", 1f),
                    fundidoEntradaUs = o.optLong("fundidoEntrada"),
                    fundidoSalidaUs = o.optLong("fundidoSalida")
                )
            }
        } ?: emptyList()
        // El contador nunca por debajo de los ids que ya hay: dos bloques con
        // el mismo id se editarian a la vez.
        val maxId = (principal.map { it.id } + capas.map { it.id } + audios.map { it.id }).maxOrNull() ?: 0
        return Proyecto(
            formato = j.enumeracion("formato", Formato.VERTICAL),
            fondo = j.enumeracion("fondo", Fondo.DESENFOCADO),
            salida = AjustesSalida(
                ladoCorto = s.optInt("ladoCorto", d.ladoCorto),
                calidad = s.enumeracion("calidad", d.calidad),
                hevc = s.optBoolean("hevc", d.hevc),
                desenfoqueMovimiento = s.optBoolean("desenfoque", d.desenfoqueMovimiento),
                muestras = s.optInt("muestras", d.muestras)
            ),
            principal = principal,
            capas = capas,
            audios = audios,
            siguienteId = maxOf(j.optLong("siguienteId", 1), maxId + 1)
        )
    }

    private fun origenDe(o: JSONObject): Origen? {
        val uri = o.optString("uri", "")
        if (uri.isEmpty()) return null
        return Origen(
            uri = uri,
            nombre = o.optString("nombre", ""),
            tipo = o.enumeracion("tipo", TipoOrigen.VIDEO),
            ancho = o.optInt("ancho"),
            alto = o.optInt("alto"),
            duracionUs = o.optLong("duracion"),
            fps = o.f("fps", 0f),
            conAudio = o.optBoolean("conAudio"),
            giro = o.optInt("giro"),
            bytes = o.optLong("bytes")
        )
    }

    private fun clipDe(o: JSONObject): Clip? {
        val origen = o.optJSONObject("origen")?.let { origenDe(it) }
        val texto = o.optJSONObject("texto")?.let { textoDe(it) }
        if ((origen == null) == (texto == null)) return null
        val d = Clip(id = 0, origen = origen, texto = texto)
        return Clip(
            id = o.optLong("id"),
            origen = origen,
            texto = texto,
            desdeUs = o.optLong("desde", d.desdeUs),
            hastaUs = o.optLong("hasta", d.hastaUs),
            velocidad = o.f("velocidad", 1f).coerceIn(0.1f, 10f),
            volumen = o.f("volumen", 1f),
            mantenerTono = o.optBoolean("mantenerTono", true),
            fundidoEntradaUs = o.optLong("fundidoEntrada"),
            fundidoSalidaUs = o.optLong("fundidoSalida"),
            color = o.optJSONArray("color")?.let { colorDe(it) } ?: AjustesColor(),
            filtro = o.enumeracion("filtro", Filtro.NINGUNO),
            intensidadFiltro = o.f("intensidadFiltro", 1f),
            encuadre = o.optJSONObject("encuadre")?.let { encuadreDe(it) } ?: Encuadre(),
            claves = o.optJSONArray("claves")?.let { a ->
                (0 until a.length()).map { i ->
                    val k = a.getJSONObject(i)
                    Clave(
                        k.optLong("t"),
                        k.optJSONObject("encuadre")?.let { encuadreDe(it) } ?: Encuadre(),
                        k.enumeracion("curva", Curva.SUAVE)
                    )
                }
            } ?: emptyList(),
            entrada = o.enumeracion("entrada", Animacion.NINGUNA),
            salida = o.enumeracion("salida", Animacion.NINGUNA),
            duracionEntradaUs = o.optLong("duracionEntrada", d.duracionEntradaUs),
            duracionSalidaUs = o.optLong("duracionSalida", d.duracionSalidaUs),
            continua = o.enumeracion("continua", AnimacionContinua.NINGUNA),
            transicion = o.enumeracion("transicion", Transicion.NINGUNA),
            duracionTransicionUs = o.optLong("duracionTransicion", d.duracionTransicionUs),
            fluido = o.optBoolean("fluido")
        )
    }

    private fun textoDe(o: JSONObject): Texto {
        val d = Texto("")
        return Texto(
            contenido = o.optString("contenido", ""),
            tamano = o.f("tamano", d.tamano),
            color = o.optInt("color", d.color),
            fuente = o.enumeracion("fuente", Fuente.SANS),
            negrita = o.optBoolean("negrita", d.negrita),
            contorno = o.f("contorno", 0f),
            colorContorno = o.optInt("colorContorno", d.colorContorno),
            sombra = o.optBoolean("sombra", d.sombra),
            fondo = o.optInt("fondo", 0),
            alineacion = o.enumeracion("alineacion", Alineacion.CENTRO),
            espaciado = o.f("espaciado", 0f)
        )
    }

    private fun encuadreDe(o: JSONObject): Encuadre {
        val r = o.optJSONArray("recorte")
        fun rc(i: Int) = r?.optDouble(i, 0.0)?.toFloat() ?: 0f
        return Encuadre(
            ajuste = o.enumeracion("ajuste", Ajuste.CUBRIR),
            escala = o.f("escala", 1f),
            x = o.f("x", 0f),
            y = o.f("y", 0f),
            rotacion = o.f("rotacion", 0f),
            opacidad = o.f("opacidad", 1f),
            espejo = o.optBoolean("espejo"),
            recorte = Recorte(rc(0), rc(1), rc(2), rc(3))
        )
    }

    private fun colorDe(a: JSONArray): AjustesColor {
        fun v(i: Int) = a.optDouble(i, 0.0).toFloat()
        return AjustesColor(v(0), v(1), v(2), v(3), v(4), v(5), v(6), v(7), v(8), v(9))
    }
}
