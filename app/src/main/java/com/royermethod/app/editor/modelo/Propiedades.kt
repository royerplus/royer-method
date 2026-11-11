package com.royermethod.app.editor.modelo

import kotlin.math.roundToInt

enum class GrupoPropiedad { AJUSTES, MARCO, TEXTO }
enum class TipoValor { NUMERO, COLOR, PASO }

/** Canales independientes. Cada intervalo conserva su curva propia. */
enum class Propiedad(
    val titulo: String, val grupo: GrupoPropiedad, val minimo: Double, val maximo: Double,
    val tipo: TipoValor, val leer: (Clip) -> Double, val poner: (Clip, Double) -> Clip
) {
    BRILLO("Brillo", GrupoPropiedad.AJUSTES, -1.0, 1.0, TipoValor.NUMERO, { (it.color.brillo).toDouble() }, { it, v -> it.copy(color = it.color.copy(brillo = v.toFloat())) }),
    CONTRASTE("Contraste", GrupoPropiedad.AJUSTES, -1.0, 1.0, TipoValor.NUMERO, { (it.color.contraste).toDouble() }, { it, v -> it.copy(color = it.color.copy(contraste = v.toFloat())) }),
    SATURACION("Saturación", GrupoPropiedad.AJUSTES, -1.0, 1.0, TipoValor.NUMERO, { (it.color.saturacion).toDouble() }, { it, v -> it.copy(color = it.color.copy(saturacion = v.toFloat())) }),
    EXPOSICION("Exposición", GrupoPropiedad.AJUSTES, -1.0, 1.0, TipoValor.NUMERO, { (it.color.exposicion).toDouble() }, { it, v -> it.copy(color = it.color.copy(exposicion = v.toFloat())) }),
    CALIDEZ("Calidez", GrupoPropiedad.AJUSTES, -1.0, 1.0, TipoValor.NUMERO, { (it.color.calidez).toDouble() }, { it, v -> it.copy(color = it.color.copy(calidez = v.toFloat())) }),
    TINTE("Tinte", GrupoPropiedad.AJUSTES, -1.0, 1.0, TipoValor.NUMERO, { (it.color.tinte).toDouble() }, { it, v -> it.copy(color = it.color.copy(tinte = v.toFloat())) }),
    SOMBRAS("Sombras", GrupoPropiedad.AJUSTES, -1.0, 1.0, TipoValor.NUMERO, { (it.color.sombras).toDouble() }, { it, v -> it.copy(color = it.color.copy(sombras = v.toFloat())) }),
    LUCES("Luces", GrupoPropiedad.AJUSTES, -1.0, 1.0, TipoValor.NUMERO, { (it.color.luces).toDouble() }, { it, v -> it.copy(color = it.color.copy(luces = v.toFloat())) }),
    NITIDEZ("Nitidez", GrupoPropiedad.AJUSTES, 0.0, 1.0, TipoValor.NUMERO, { (it.color.nitidez).toDouble() }, { it, v -> it.copy(color = it.color.copy(nitidez = v.toFloat())) }),
    VINETA("Viñeta", GrupoPropiedad.AJUSTES, 0.0, 1.0, TipoValor.NUMERO, { (it.color.vineta).toDouble() }, { it, v -> it.copy(color = it.color.copy(vineta = v.toFloat())) }),
    CLARIDAD("Claridad", GrupoPropiedad.AJUSTES, -1.0, 1.0, TipoValor.NUMERO, { (it.color.claridad).toDouble() }, { it, v -> it.copy(color = it.color.copy(claridad = v.toFloat())) }),
    VIVACIDAD("Vivacidad", GrupoPropiedad.AJUSTES, -1.0, 1.0, TipoValor.NUMERO, { (it.color.vivacidad).toDouble() }, { it, v -> it.copy(color = it.color.copy(vivacidad = v.toFloat())) }),
    DESVANECER("Desvanecer", GrupoPropiedad.AJUSTES, 0.0, 1.0, TipoValor.NUMERO, { (it.color.desvanecer).toDouble() }, { it, v -> it.copy(color = it.color.copy(desvanecer = v.toFloat())) }),
    GRANO("Grano", GrupoPropiedad.AJUSTES, 0.0, 1.0, TipoValor.NUMERO, { (it.color.grano).toDouble() }, { it, v -> it.copy(color = it.color.copy(grano = v.toFloat())) }),
    BORDE_ANCHO("Anchura", GrupoPropiedad.MARCO, 0.0, 0.15, TipoValor.NUMERO, { (it.marco.ancho).toDouble() }, { it, v -> it.copy(marco = it.marco.copy(ancho = v.toFloat())) }),
    ESQUINA("Esquinas", GrupoPropiedad.MARCO, 0.0, 0.5, TipoValor.NUMERO, { (it.marco.radio).toDouble() }, { it, v -> it.copy(marco = it.marco.copy(radio = v.toFloat())) }),
    BORDE_COLOR("Color del borde", GrupoPropiedad.MARCO, -2147483648.0, 2147483647.0, TipoValor.COLOR, { (it.marco.color).toDouble() }, { it, v -> it.copy(marco = it.marco.copy(color = v.toLong().toInt())) }),
    NEON("Neón del borde", GrupoPropiedad.MARCO, 0.0, 1.0, TipoValor.NUMERO, { (it.marco.neon).toDouble() }, { it, v -> it.copy(marco = it.marco.copy(neon = v.toFloat())) }),
    SOMBRA("Opacidad de sombra", GrupoPropiedad.MARCO, 0.0, 1.0, TipoValor.NUMERO, { (it.marco.sombra).toDouble() }, { it, v -> it.copy(marco = it.marco.copy(sombra = v.toFloat())) }),
    SOMBRA_COLOR("Color de sombra", GrupoPropiedad.MARCO, -2147483648.0, 2147483647.0, TipoValor.COLOR, { (it.marco.colorSombra).toDouble() }, { it, v -> it.copy(marco = it.marco.copy(colorSombra = v.toLong().toInt())) }),
    SUAVIDAD("Difusión de sombra", GrupoPropiedad.MARCO, 0.0, 0.2, TipoValor.NUMERO, { (it.marco.suavidad).toDouble() }, { it, v -> it.copy(marco = it.marco.copy(suavidad = v.toFloat())) }),
    DISTANCIA("Distancia de sombra", GrupoPropiedad.MARCO, 0.0, 0.3, TipoValor.NUMERO, { (it.marco.distancia).toDouble() }, { it, v -> it.copy(marco = it.marco.copy(distancia = v.toFloat())) }),
    ANGULO("Dirección de sombra", GrupoPropiedad.MARCO, 0.0, 360.0, TipoValor.NUMERO, { (it.marco.angulo).toDouble() }, { it, v -> it.copy(marco = it.marco.copy(angulo = v.toFloat())) }),
    TAMANO("Tamaño de letra", GrupoPropiedad.TEXTO, 0.02, 0.2, TipoValor.NUMERO, { (it.texto!!.tamano).toDouble() }, { it, v -> it.copy(texto = it.texto!!.copy(tamano = v.toFloat())) }),
    COLOR_TEXTO("Color de letra", GrupoPropiedad.TEXTO, -2147483648.0, 2147483647.0, TipoValor.COLOR, { (it.texto!!.color).toDouble() }, { it, v -> it.copy(texto = it.texto!!.copy(color = v.toLong().toInt())) }),
    FUENTE("Fuente", GrupoPropiedad.TEXTO, 0.0, 6.0, TipoValor.PASO, { (it.texto!!.fuente.ordinal).toDouble() }, { it, v -> it.copy(texto = it.texto!!.copy(fuente = Fuente.values()[v.toInt().coerceIn(0, Fuente.values().lastIndex)])) }),
    NEGRITA("Negrita", GrupoPropiedad.TEXTO, 0.0, 1.0, TipoValor.PASO, { (if (it.texto!!.negrita) 1 else 0).toDouble() }, { it, v -> it.copy(texto = it.texto!!.copy(negrita = v >= 0.5)) }),
    CURSIVA("Cursiva", GrupoPropiedad.TEXTO, 0.0, 1.0, TipoValor.PASO, { (if (it.texto!!.cursiva) 1 else 0).toDouble() }, { it, v -> it.copy(texto = it.texto!!.copy(cursiva = v >= 0.5)) }),
    SUBRAYADO("Subrayado", GrupoPropiedad.TEXTO, 0.0, 1.0, TipoValor.PASO, { (if (it.texto!!.subrayado) 1 else 0).toDouble() }, { it, v -> it.copy(texto = it.texto!!.copy(subrayado = v >= 0.5)) }),
    CONTORNO("Contorno", GrupoPropiedad.TEXTO, 0.0, 0.3, TipoValor.NUMERO, { (it.texto!!.contorno).toDouble() }, { it, v -> it.copy(texto = it.texto!!.copy(contorno = v.toFloat())) }),
    COLOR_CONTORNO("Color del contorno", GrupoPropiedad.TEXTO, -2147483648.0, 2147483647.0, TipoValor.COLOR, { (it.texto!!.colorContorno).toDouble() }, { it, v -> it.copy(texto = it.texto!!.copy(colorContorno = v.toLong().toInt())) }),
    SOMBRA_TEXTO("Sombra de letra", GrupoPropiedad.TEXTO, 0.0, 1.0, TipoValor.PASO, { (if (it.texto!!.sombra) 1 else 0).toDouble() }, { it, v -> it.copy(texto = it.texto!!.copy(sombra = v >= 0.5)) }),
    OPACIDAD_SOMBRA_TEXTO("Opacidad de sombra", GrupoPropiedad.TEXTO, 0.0, 1.0, TipoValor.NUMERO, { (it.texto!!.sombraOpacidad).toDouble() }, { it, v -> it.copy(texto = it.texto!!.copy(sombraOpacidad = v.toFloat())) }),
    COLOR_SOMBRA_TEXTO("Color de sombra", GrupoPropiedad.TEXTO, -2147483648.0, 2147483647.0, TipoValor.COLOR, { (it.texto!!.sombraColor).toDouble() }, { it, v -> it.copy(texto = it.texto!!.copy(sombraColor = v.toLong().toInt())) }),
    BLUR_TEXTO("Difusión de sombra", GrupoPropiedad.TEXTO, 0.0, 0.4, TipoValor.NUMERO, { (it.texto!!.sombraDesenfoque).toDouble() }, { it, v -> it.copy(texto = it.texto!!.copy(sombraDesenfoque = v.toFloat())) }),
    DISTANCIA_TEXTO("Distancia de sombra", GrupoPropiedad.TEXTO, 0.0, 0.2, TipoValor.NUMERO, { (it.texto!!.sombraDistancia).toDouble() }, { it, v -> it.copy(texto = it.texto!!.copy(sombraDistancia = v.toFloat())) }),
    ANGULO_TEXTO("Dirección de sombra", GrupoPropiedad.TEXTO, 0.0, 360.0, TipoValor.NUMERO, { (it.texto!!.sombraAngulo).toDouble() }, { it, v -> it.copy(texto = it.texto!!.copy(sombraAngulo = v.toFloat())) }),
    FONDO_TEXTO("Fondo del texto", GrupoPropiedad.TEXTO, -2147483648.0, 2147483647.0, TipoValor.COLOR, { (it.texto!!.fondo).toDouble() }, { it, v -> it.copy(texto = it.texto!!.copy(fondo = v.toLong().toInt())) }),
    ESPACIADO("Espacio entre letras", GrupoPropiedad.TEXTO, -0.1, 0.5, TipoValor.NUMERO, { (it.texto!!.espaciado).toDouble() }, { it, v -> it.copy(texto = it.texto!!.copy(espaciado = v.toFloat())) }),
    ALINEACION("Alineación", GrupoPropiedad.TEXTO, 0.0, 2.0, TipoValor.PASO, { it.texto!!.alineacion.ordinal.toDouble() }, { it, v -> it.copy(texto = it.texto!!.copy(alineacion = Alineacion.values()[v.toInt().coerceIn(0,2)])) }),
    FILTRO("Filtro", GrupoPropiedad.AJUSTES, 0.0, Filtro.values().lastIndex.toDouble(), TipoValor.PASO, { it.filtro.ordinal.toDouble() }, { it, v -> it.copy(filtro = Filtro.values()[v.toInt().coerceIn(0, Filtro.values().lastIndex)]) }),
    INTENSIDAD_FILTRO("Intensidad del filtro", GrupoPropiedad.AJUSTES, 0.0, 1.0, TipoValor.NUMERO, { it.intensidadFiltro.toDouble() }, { it, v -> it.copy(intensidadFiltro = v.toFloat()) }),
    INTERLINEADO("Espacio entre líneas", GrupoPropiedad.TEXTO, 0.6, 2.5, TipoValor.NUMERO, { (it.texto!!.interlineado).toDouble() }, { it, v -> it.copy(texto = it.texto!!.copy(interlineado = v.toFloat())) });

    fun acotar(v: Double): Double = if (v.isFinite()) v.coerceIn(minimo, maximo) else minimo
    fun mezclar(a: Double, b: Double, t: Float): Double {
        if (tipo == TipoValor.PASO) return a
        if (tipo != TipoValor.COLOR) return acotar(a + (b - a) * t)
        val ca = a.toLong().toInt(); val cb = b.toLong().toInt()
        var c = 0
        for (s in listOf(0, 8, 16, 24)) {
            val x = (ca ushr s) and 255; val y = (cb ushr s) and 255
            c = c or ((x + (y - x) * t).roundToInt().coerceIn(0, 255) shl s)
        }
        return c.toDouble()
    }
}

data class ClaveValor(
    val tiempoUs: Long, val valor: Double, val curva: Curva = Curva.SUAVE,
    val bezier: Bezier? = null, val velocidad: CurvaVelocidad? = null
) {
    fun avance(t: Float) = velocidad?.en(t) ?: bezier?.en(t) ?: Escena.curva(curva, t)
}

fun List<ClaveValor>.valorEn(t: Long, propiedad: Propiedad): Double {
    val ks = this
    if (t <= ks.first().tiempoUs) return propiedad.acotar(ks.first().valor)
    if (t >= ks.last().tiempoUs) return propiedad.acotar(ks.last().valor)
    val i = ks.indexOfLast { it.tiempoUs <= t }
    val a = ks[i]; val b = ks[i + 1]
    return propiedad.mezclar(a.valor, b.valor, a.avance((t - a.tiempoUs).toFloat() / (b.tiempoUs - a.tiempoUs)))
}

fun Clip.propiedadesEn(localUs: Long): Clip {
    var c = this
    val t = localUs + desfasePropiedadesUs
    for ((p, ks) in propiedadesOrdenadas) if (ks.isNotEmpty() && (p.grupo != GrupoPropiedad.TEXTO || esTexto)) {
        val valor = ks.valorEn(t, p)
        if (p.leer(c) != valor) c = p.poner(c, valor)
    }
    return c
}

fun Clip.ponerPropiedades(localUs: Long, canales: List<Propiedad>): Clip {
    val actual = propiedadesEn(localUs)
    val t = localUs + desfasePropiedadesUs
    val mapa = propiedades.toMutableMap()
    for (p in canales) {
        val ks = mapa[p].orEmpty()
        val base = if (ks.isEmpty() && localUs > 0) listOf(ClaveValor(desfasePropiedadesUs, p.leer(this))) else emptyList()
        val vieja = ks.firstOrNull { it.tiempoUs == t }
        mapa[p] = (ks.filter { it.tiempoUs != t } + base + (vieja?.copy(valor = p.leer(actual)) ?: ClaveValor(t, p.leer(actual)))).sortedBy { it.tiempoUs }
    }
    return copy(propiedades = mapa)
}

/** Un control cambia solo sus canales; el contenido escrito nunca se anima. */
fun Clip.editarPropiedades(localUs: Long, f: (Clip) -> Clip): Clip {
    val antes = propiedadesEn(localUs)
    val despues = f(antes)
    var c = this
    val canales = Propiedad.values().filter { it.grupo != GrupoPropiedad.TEXTO || esTexto }
    for (p in canales) if (p.leer(antes) != p.leer(despues)) {
        if (p in propiedades) {
            val t = localUs + desfasePropiedadesUs
            val ks = c.propiedades.getValue(p)
            val previa = ks.firstOrNull { it.tiempoUs == t }
            val nueva = previa?.copy(valor = p.leer(despues)) ?: ClaveValor(t, p.leer(despues))
            c = c.copy(propiedades = c.propiedades + (p to (ks.filter { it.tiempoUs != t } + nueva).sortedBy { it.tiempoUs }))
        } else c = p.poner(c, p.leer(despues))
    }
    // Los campos ajenos a los canales se actualizan directamente.
    return c.copy(texto = c.texto?.copy(contenido = despues.texto!!.contenido))
}
