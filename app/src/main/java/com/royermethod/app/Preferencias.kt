package com.royermethod.app

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.royermethod.app.editor.Persistencia
import com.royermethod.app.editor.Persistencia.enumeracion
import com.royermethod.app.editor.modelo.Combinaciones
import com.royermethod.app.editor.modelo.Fondo
import com.royermethod.app.editor.modelo.Formato
import com.royermethod.app.editor.ui.AjustesEditor
import com.royermethod.app.ui.theme.Estilo
import com.royermethod.app.ui.theme.FondoApp
import com.royermethod.app.ui.theme.Velocidad
import org.json.JSONArray
import org.json.JSONObject

/**
 * Guarda en el telefono lo que el usuario personaliza: el aspecto de la app
 * ([Estilo]) y las preferencias del editor ([AjustesEditor]), incluidas sus
 * animaciones combinadas.
 *
 * Todo va en un solo texto JSON: son pocos datos y asi se lee y se escribe
 * de una vez. Lo que falte o no se entienda se queda con su valor de fabrica.
 */
object Preferencias {

    private const val ARCHIVO = "ajustes"
    private const val CLAVE = "todo"
    private const val VERSION_EDITOR = 2

    /** Lee lo guardado y deja preparado que cada cambio se guarde solo. */
    fun cargar(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE)
        runCatching { prefs.getString(CLAVE, null)?.let { leer(JSONObject(it)) } }
        val guardar = { prefs.edit().putString(CLAVE, escribir().toString()).apply() }
        Estilo.alCambiar = guardar
        AjustesEditor.alCambiar = guardar
    }

    private fun escribir() = JSONObject().apply {
        put("estilo", JSONObject().apply {
            put("acento", Estilo.acento.toArgb())
            put("fondo", Estilo.fondo.name)
            put("halo", Estilo.halo.toDouble())
            put("latido", Estilo.latido)
            put("velocidad", Estilo.velocidad.name)
            put("letra", Estilo.letra.toDouble())
            put("redondez", Estilo.redondez.toDouble())
        })
        put("editor", JSONObject().apply {
            put("version", VERSION_EDITOR)
            put("formato", AjustesEditor.formato.name)
            put("fondo", AjustesEditor.fondo.name)
            put("salida", Persistencia.salida(AjustesEditor.salida))
            put("duracionTransicion", AjustesEditor.duracionTransicionUs)
            put("duracionAnimacion", AjustesEditor.duracionAnimacionUs)
            put("imanes", AjustesEditor.imanes)
            put("ajustarFotogramas", AjustesEditor.ajustarFotogramas)
            put("mias", JSONArray().apply {
                AjustesEditor.mias.forEach { c ->
                    put(JSONObject().put("nombre", c.nombre).put("piezas", Persistencia.movimientos(c.piezas)))
                }
            })
            put("continuasMias", JSONArray().apply {
                AjustesEditor.continuasMias.forEach { (nombre, l) ->
                    put(JSONObject().put("nombre", nombre).put("piezas", Persistencia.continuas(l)))
                }
            })
        })
    }

    private fun leer(j: JSONObject) {
        j.optJSONObject("estilo")?.let { e ->
            Estilo.acento = Color(e.optInt("acento", Color.White.toArgb()))
            Estilo.fondo = e.enumeracion("fondo", FondoApp.NEGRO)
            Estilo.halo = e.optDouble("halo", 1.0).toFloat().coerceIn(0f, 1.6f)
            Estilo.latido = e.optBoolean("latido", true)
            Estilo.velocidad = e.enumeracion("velocidad", Velocidad.NORMAL)
            Estilo.letra = e.optDouble("letra", 1.0).toFloat().coerceIn(0.85f, 1.25f)
            Estilo.redondez = e.optDouble("redondez", 1.0).toFloat().coerceIn(0f, 1.5f)
        }
        j.optJSONObject("editor")?.let { e ->
            // Antes de la version 2 el formato se guardaba aunque nadie lo eligiera
            // (vertical): ahi manda el nuevo por defecto, el cuadrado.
            if (e.optInt("version", 1) >= VERSION_EDITOR) AjustesEditor.formato = e.enumeracion("formato", Formato.CUADRADO)
            AjustesEditor.fondo = e.enumeracion("fondo", Fondo.DESENFOCADO)
            AjustesEditor.salida = Persistencia.salidaDe(e.optJSONObject("salida"))
            AjustesEditor.duracionTransicionUs = e.optLong("duracionTransicion", 400_000L).coerceIn(100_000L, 3_000_000L)
            AjustesEditor.duracionAnimacionUs = e.optLong("duracionAnimacion", 400_000L).coerceIn(100_000L, 3_000_000L)
            AjustesEditor.imanes = e.optBoolean("imanes", true)
            AjustesEditor.ajustarFotogramas = e.optBoolean("ajustarFotogramas", true)
            AjustesEditor.mias = lista(e.optJSONArray("mias")) { nombre, a ->
                Persistencia.movimientosDe(a).takeIf { it.isNotEmpty() }?.let { Combinaciones.Combinacion(nombre, it) }
            }
            AjustesEditor.continuasMias = lista(e.optJSONArray("continuasMias")) { nombre, a ->
                Persistencia.continuasDe(a).takeIf { it.isNotEmpty() }?.let { nombre to it }
            }
        }
    }

    private fun <T> lista(a: JSONArray?, f: (String, JSONArray?) -> T?): List<T> {
        if (a == null) return emptyList()
        return (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            f(o.optString("nombre", "Mia"), o.optJSONArray("piezas"))
        }
    }
}
