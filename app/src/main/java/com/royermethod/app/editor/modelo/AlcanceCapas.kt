package com.royermethod.app.editor.modelo

/** null significa todas, incluidas las capas que se agreguen después; vacío, ninguna. */
object AlcanceCapas {
    fun acepta(ajuste: Clip, id: Long): Boolean = ajuste.afectaA?.contains(id) ?: true

    fun ajustesPara(p: Proyecto, id: Long, t: Long): List<AjustesColor> = ajustesPara(
        p.capas.filter { it.clip.esAjuste && it.clip.afectaA != null }.sortedBy { it.fila }, id, t)

    internal fun ajustesPara(capas: List<Capa>, id: Long, t: Long): List<AjustesColor> =
        capas.filter { t >= it.inicioUs && t < it.finUs && acepta(it.clip, id) }
            .map { it.clip.propiedadesEn(t - it.inicioUs).colorFinal() }.filterNot { it.neutro }

    /** Traslada el reloj del efecto global al reloj del elemento, sin reiniciarlo en los cortes. */
    fun conEfectos(p: Proyecto, clip: Clip, inicio: Long): Clip = conEfectos(
        p.capas.filter { it.clip.esAjuste && it.clip.efectos.isNotEmpty() }.sortedBy { it.fila }, clip, inicio)

    internal fun conEfectos(capas: List<Capa>, clip: Clip, inicio: Long): Clip {
        if (capas.isEmpty()) return clip
        val compartidos = capas.filter { acepta(it.clip, clip.id) }
            .flatMap { capa ->
                capa.clip.efectos.mapNotNull { it.recortado(0, capa.clip.duracionUs) }.mapNotNull { e ->
                    val desplazamiento = capa.inicioUs - inicio
                    val desde = e.inicioUs + desplazamiento
                    val hasta = e.finUs + desplazamiento
                    if (hasta <= 0 || desde >= clip.duracionUs) null
                    else e.copy(inicioUs = desde, finUs = hasta)
                }
            }
        return if (compartidos.isEmpty()) clip else clip.copy(efectos = clip.efectos + compartidos)
    }

    /** Partir o duplicar un elemento conserva su pertenencia a una selección explícita. */
    fun heredarDestino(p: Proyecto, anterior: Long, nuevo: Long): Proyecto = p.copy(capas = p.capas.map { capa ->
        val ids = capa.clip.afectaA
        if (capa.clip.esAjuste && ids != null && anterior in ids)
            capa.copy(clip = capa.clip.copy(afectaA = ids + nuevo)) else capa
    })

    /** Intercambia pistas completas; ni los tiempos ni las identidades de los destinos cambian. */
    fun cambiarOrden(p: Proyecto, fila: Int, delante: Boolean): Proyecto {
        val filas = p.capas.filterNot { it.clip.esAjuste }.map { it.fila }.distinct().sorted()
        val indice = filas.indexOf(fila)
        if (indice < 0) return p
        val vecina = filas.getOrNull(indice + if (delante) 1 else -1) ?: return p
        return p.copy(capas = p.capas.map { c -> when (c.fila) {
            fila -> c.copy(fila = vecina)
            vecina -> c.copy(fila = fila)
            else -> c
        } })
    }
}

/** Un efecto que estaba ligado a un clip pasa a un tramo compartido sin cambiar su reloj. */
fun compartirEfecto(p: Proyecto, id: Long, efectoId: Long): Pair<Proyecto, Long>? {
    val c = Edicion.clip(p, id)?.conIdsEfectos() ?: return null
    val efecto = c.efectos.firstOrNull { it.id == efectoId } ?: return null
    val inicio = Edicion.inicio(p, id) ?: return null
    val (nuevo, q) = p.conId()
    val sin = Edicion.cambiar(q, id) { c.copy(efectos = c.efectos.filterNot { it.id == efectoId }) }
    return sin.copy(capas = sin.capas + Capa(inicio, (sin.capas.maxOfOrNull { it.fila } ?: -1) + 1,
        Clip(nuevo, esAjuste = true, hastaUs = c.duracionUs, efectos = listOf(efecto)))) to nuevo
}
