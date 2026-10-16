package com.royermethod.app.editor

import com.royermethod.app.editor.modelo.Ajuste
import com.royermethod.app.editor.modelo.AjusteTransicion
import com.royermethod.app.editor.modelo.AjustesColor
import com.royermethod.app.editor.modelo.AjustesSalida
import com.royermethod.app.editor.modelo.Alineacion
import com.royermethod.app.editor.modelo.Animacion
import com.royermethod.app.editor.modelo.AnimacionContinua
import com.royermethod.app.editor.modelo.Bezier
import com.royermethod.app.editor.modelo.Calidad
import com.royermethod.app.editor.modelo.Capa
import com.royermethod.app.editor.modelo.Clave
import com.royermethod.app.editor.modelo.Clip
import com.royermethod.app.editor.modelo.Continuo
import com.royermethod.app.editor.modelo.Curva
import com.royermethod.app.editor.modelo.Encuadre
import com.royermethod.app.editor.modelo.EfectoVideo
import com.royermethod.app.editor.modelo.Filtro
import com.royermethod.app.editor.modelo.Fondo
import com.royermethod.app.editor.modelo.Formato
import com.royermethod.app.editor.modelo.Fuente
import com.royermethod.app.editor.modelo.Mezcla
import com.royermethod.app.editor.modelo.Movimiento
import com.royermethod.app.editor.modelo.Origen
import com.royermethod.app.editor.modelo.PistaAudio
import com.royermethod.app.editor.modelo.PiezaVideo
import com.royermethod.app.editor.modelo.Proyecto
import com.royermethod.app.editor.modelo.ProgresoEfecto
import com.royermethod.app.editor.modelo.Recorte
import com.royermethod.app.editor.modelo.RegionUv
import com.royermethod.app.editor.modelo.Texto
import com.royermethod.app.editor.modelo.TipoOrigen
import com.royermethod.app.editor.modelo.TipoEfectoVideo
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
        put("salida", salida(p.salida))
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
        put("claves", claves(c.claves))
        put("piezas", JSONArray().apply {
            c.piezas.forEach { p ->
                put(JSONObject().apply {
                    put("nombre", p.nombre)
                    put("region", JSONObject().apply {
                        put("izquierda", p.region.izquierda.toDouble())
                        put("arriba", p.region.arriba.toDouble())
                        put("derecha", p.region.derecha.toDouble())
                        put("abajo", p.region.abajo.toDouble())
                    })
                    put("encuadre", encuadre(p.encuadre))
                    put("claves", claves(p.claves))
                })
            }
        })
        put("entrada", movimientos(c.entrada))
        put("salida", movimientos(c.salida))
        put("duracionEntrada", c.duracionEntradaUs)
        put("duracionSalida", c.duracionSalidaUs)
        put("continua", continuas(c.continua))
        put("efectos", JSONArray().apply {
            c.efectos.forEach { e ->
                put(JSONObject().apply {
                    put("tipo", e.tipo.name)
                    put("inicio", e.inicioUs)
                    put("fin", e.finUs)
                    put("intensidad", e.intensidad.toDouble())
                    put("rapidez", e.rapidez.toDouble())
                    put("desfase", e.desfaseUs)
                    put("fundidoEntrada", e.fundidoEntradaUs)
                    put("fundidoSalida", e.fundidoSalidaUs)
                    put("duracionBase", e.duracionBaseUs)
                    put("activo", e.activo)
                    put("progresos", JSONArray().apply {
                        e.progresos.forEach { p -> put(JSONObject().apply {
                            put("t", p.tiempoUs)
                            put("valor", p.valor.toDouble())
                        }) }
                    })
                })
            }
        })
        put("transicion", c.transicion.name)
        put("duracionTransicion", c.duracionTransicionUs)
        put("ajusteTransicion", JSONObject().apply {
            val a = c.ajusteTransicion
            put("curva", a.curva.name)
            put("angulo", a.angulo.toDouble())
            put("color", a.color)
            put("intensidad", a.intensidad.toDouble())
        })
        put("fluido", c.fluido)
        put("calidad", c.calidad.toDouble())
    }

    private fun claves(l: List<Clave>) = JSONArray().apply {
        l.forEach { k ->
            put(JSONObject().apply {
                put("t", k.tiempoUs)
                put("encuadre", encuadre(k.encuadre))
                put("curva", k.curva.name)
                k.bezier?.let { b -> put("bezier", JSONArray(listOf(b.x1, b.y1, b.x2, b.y2).map { it.toDouble() })) }
            })
        }
    }

    internal fun salida(s: AjustesSalida) = JSONObject().apply {
        put("ladoCorto", s.ladoCorto)
        put("calidad", s.calidad.name)
        put("hevc", s.hevc)
        put("desenfoque", s.desenfoqueMovimiento)
        put("muestras", s.muestras)
    }

    internal fun continuas(l: List<Continuo>) = JSONArray().apply {
        l.forEach { k ->
            put(JSONObject().apply {
                put("tipo", k.tipo.name)
                put("intensidad", k.intensidad.toDouble())
                put("velocidad", k.velocidad.toDouble())
            })
        }
    }

    internal fun movimientos(l: List<Movimiento>) = JSONArray().apply {
        l.forEach { m ->
            put(JSONObject().apply {
                put("tipo", m.tipo.name)
                put("intensidad", m.intensidad.toDouble())
                put("curva", m.curva.name)
                put("desde", m.desde.toDouble())
                put("hasta", m.hasta.toDouble())
            })
        }
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

    internal inline fun <reified E : Enum<E>> JSONObject.enumeracion(clave: String, defecto: E): E =
        optString(clave, "").let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: defecto

    // org.json guarda -0.0 como "-0" y al releerlo sale 0: se normaliza aqui para que
    // un proyecto leido sea igual al mismo proyecto guardado y vuelto a leer.
    private fun JSONObject.f(clave: String, defecto: Float) = optDouble(clave, defecto.toDouble()).toFloat() + 0f

    /**
     * Los proyectos de antes guardaban una de cuatro direcciones, y los
     * barridos se inclinaban solos. Se pasa a angulo para que se vean igual.
     */
    private fun anguloViejo(direccion: String, transicion: String): Float {
        val base = when (direccion) {
            "DERECHA" -> 0f
            "ABAJO" -> 90f
            "ARRIBA" -> 270f
            else -> 180f
        }
        val barrido = transicion == Transicion.LINEA.name || transicion == Transicion.BARRIDO.name
        return if (barrido) (base + AjusteTransicion.ANGULO_BARRIDO - 180f + 360f) % 360f else base
    }

    private fun proyecto(j: JSONObject): Proyecto {
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
            salida = salidaDe(j.optJSONObject("salida")),
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
            claves = clavesDe(o.optJSONArray("claves")),
            piezas = piezasDe(o.optJSONArray("piezas")),
            entrada = movimientosDe(o.optJSONArray("entrada")),
            salida = movimientosDe(o.optJSONArray("salida")),
            duracionEntradaUs = o.optLong("duracionEntrada", d.duracionEntradaUs),
            duracionSalidaUs = o.optLong("duracionSalida", d.duracionSalidaUs),
            continua = continuasDe(o.optJSONArray("continua")),
            efectos = efectosDe(o.optJSONArray("efectos")),
            transicion = o.enumeracion("transicion", Transicion.NINGUNA),
            duracionTransicionUs = o.optLong("duracionTransicion", d.duracionTransicionUs),
            ajusteTransicion = o.optJSONObject("ajusteTransicion")?.let { a ->
                val def = AjusteTransicion()
                AjusteTransicion(
                    curva = a.enumeracion("curva", def.curva),
                    angulo = if (a.has("angulo")) a.f("angulo", def.angulo) else anguloViejo(a.optString("direccion"), o.optString("transicion")),
                    color = a.optInt("color", def.color),
                    intensidad = a.f("intensidad", def.intensidad)
                )
            } ?: AjusteTransicion(),
            fluido = o.optBoolean("fluido"),
            calidad = o.f("calidad", 1f).coerceIn(0.3f, 2f)
        )
    }

    internal fun salidaDe(s: JSONObject?): AjustesSalida {
        val d = AjustesSalida()
        s ?: return d
        return AjustesSalida(
            ladoCorto = s.optInt("ladoCorto", d.ladoCorto),
            calidad = s.enumeracion("calidad", d.calidad),
            hevc = s.optBoolean("hevc", d.hevc),
            desenfoqueMovimiento = s.optBoolean("desenfoque", d.desenfoqueMovimiento),
            muestras = s.optInt("muestras", d.muestras)
        )
    }

    private fun clavesDe(a: JSONArray?): List<Clave> {
        if (a == null) return emptyList()
        return (0 until a.length()).mapNotNull { i ->
            val k = a.optJSONObject(i) ?: return@mapNotNull null
            Clave(
                k.optLong("t"),
                k.optJSONObject("encuadre")?.let { encuadreDe(it) } ?: Encuadre(),
                k.enumeracion("curva", Curva.SUAVE),
                k.optJSONArray("bezier")?.takeIf { it.length() == 4 }?.let { b ->
                    fun v(i: Int) = b.optDouble(i, 0.0).toFloat().takeIf { it.isFinite() } ?: 0f
                    Bezier(v(0).coerceIn(0f, 1f), v(1), v(2).coerceIn(0f, 1f), v(3))
                }
            )
        }
    }

    private fun piezasDe(a: JSONArray?): List<PiezaVideo> {
        if (a == null) return emptyList()
        return (0 until a.length()).mapNotNull { i ->
            val p = a.optJSONObject(i) ?: return@mapNotNull null
            val r = p.optJSONObject("region")
            val region = if (r == null) RegionUv() else RegionUv(
                r.f("izquierda", 0f), r.f("arriba", 0f), r.f("derecha", 1f), r.f("abajo", 1f)
            ).acotada()
            PiezaVideo(
                nombre = p.optString("nombre", "Parte ${i + 1}"),
                region = region,
                encuadre = p.optJSONObject("encuadre")?.let { encuadreDe(it) } ?: Encuadre(),
                claves = clavesDe(p.optJSONArray("claves"))
            )
        }
    }

    internal fun efectosDe(a: JSONArray?): List<EfectoVideo> {
        if (a == null) return emptyList()
        return (0 until a.length()).mapNotNull { i ->
            val e = a.optJSONObject(i) ?: return@mapNotNull null
            val tipo = runCatching { TipoEfectoVideo.valueOf(e.optString("tipo")) }.getOrNull()
                ?: return@mapNotNull null
            val inicio = e.optLong("inicio").coerceAtLeast(0)
            val fin = e.optLong("fin").coerceAtLeast(0)
            if (fin <= inicio) return@mapNotNull null
            fun numero(clave: String, defecto: Float, min: Float, max: Float): Float =
                e.f(clave, defecto).takeIf { it.isFinite() }?.coerceIn(min, max) ?: defecto
            EfectoVideo(
                tipo = tipo,
                inicioUs = inicio,
                finUs = fin,
                intensidad = numero("intensidad", 1f, 0f, 3f),
                rapidez = numero("rapidez", 1f, 0.1f, 10f),
                desfaseUs = e.optLong("desfase"),
                fundidoEntradaUs = e.optLong("fundidoEntrada").coerceAtLeast(0),
                fundidoSalidaUs = e.optLong("fundidoSalida").coerceAtLeast(0),
                duracionBaseUs = e.optLong("duracionBase").coerceAtLeast(0),
                activo = e.optBoolean("activo", true),
                progresos = e.optJSONArray("progresos")?.let { puntos ->
                    (0 until puntos.length()).mapNotNull punto@ { j ->
                        val p = puntos.optJSONObject(j) ?: return@punto null
                        val valor = p.f("valor", Float.NaN).takeIf { it.isFinite() } ?: return@punto null
                        ProgresoEfecto(p.optLong("t").coerceAtLeast(0), valor.coerceIn(0f, 1f))
                    }
                } ?: emptyList()
            )
        }
    }

    internal fun continuasDe(a: JSONArray?): List<Continuo> {
        if (a == null) return emptyList()
        return (0 until a.length()).mapNotNull { i ->
            val k = a.optJSONObject(i) ?: return@mapNotNull null
            val tipo = runCatching { AnimacionContinua.valueOf(k.optString("tipo")) }.getOrNull()
                ?: return@mapNotNull null
            Continuo(tipo, k.f("intensidad", 1f), k.f("velocidad", 1f).coerceIn(0.1f, 10f))
        }
    }

    internal fun movimientosDe(a: JSONArray?): List<Movimiento> {
        if (a == null) return emptyList()
        return (0 until a.length()).mapNotNull { i ->
            val m = a.optJSONObject(i) ?: return@mapNotNull null
            val tipo = runCatching { Animacion.valueOf(m.optString("tipo")) }.getOrNull() ?: return@mapNotNull null
            val desde = m.f("desde", 0f).coerceIn(0f, 1f)
            Movimiento(
                tipo,
                m.f("intensidad", 1f),
                m.enumeracion("curva", Curva.SALIDA),
                desde,
                m.f("hasta", 1f).coerceIn(desde, 1f)
            )
        }
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
        fun rc(i: Int) = (r?.optDouble(i, 0.0)?.toFloat() ?: 0f) + 0f
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
        fun v(i: Int) = a.optDouble(i, 0.0).toFloat() + 0f
        return AjustesColor(v(0), v(1), v(2), v(3), v(4), v(5), v(6), v(7), v(8), v(9))
    }
}
