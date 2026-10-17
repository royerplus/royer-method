package com.royermethod.app.editor.modelo

/** Comparte el reloj musical sin tocar roles, pivotes ni intensidad de cada capa. */
object EdicionHeadBop {
    fun sincronizar(p: Proyecto, referenciaId: Long): Proyecto {
        val referencia = Edicion.clip(p, referenciaId)?.headBop ?: return p
        val inicioReferencia = Edicion.inicio(p, referenciaId) ?: return p
        fun alinear(c: Clip, inicio: Long): Clip {
            val h = c.headBop ?: return c
            return c.copy(headBop = h.copy(bpm = referencia.bpm, rapidez = referencia.rapidez,
                desfaseUs = referencia.desfaseUs + inicio + h.inicioUs - inicioReferencia - referencia.inicioUs))
        }
        val inicios = Linea.inicios(p.principal)
        return p.copy(principal = p.principal.mapIndexed { i, c -> alinear(c, inicios[i]) },
            capas = p.capas.map { it.copy(clip = alinear(it.clip, it.inicioUs)) })
    }
}
