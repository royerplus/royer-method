package com.royermethod.app.editor.modelo

/**
 * Las operaciones del editor sobre un proyecto.
 *
 * Todas devuelven un proyecto nuevo y no tocan el de entrada: deshacer es
 * quedarse con el anterior. Ninguna depende de la interfaz, asi que se
 * prueban sin telefono.
 */
object Edicion {

    /** Donde esta un bloque. */
    sealed class Lugar {
        data class Principal(val indice: Int) : Lugar()
        data class EnCapa(val indice: Int) : Lugar()
        data class Audio(val indice: Int) : Lugar()
    }

    fun lugar(p: Proyecto, id: Long): Lugar? {
        p.principal.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let { return Lugar.Principal(it) }
        p.capas.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let { return Lugar.EnCapa(it) }
        p.audios.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let { return Lugar.Audio(it) }
        return null
    }

    fun clip(p: Proyecto, id: Long): Clip? =
        p.principal.firstOrNull { it.id == id } ?: p.capas.firstOrNull { it.id == id }?.clip

    /** Donde empieza un bloque en la linea de tiempo. */
    fun inicio(p: Proyecto, id: Long): Long? = when (val l = lugar(p, id)) {
        is Lugar.Principal -> Linea.inicios(p.principal)[l.indice]
        is Lugar.EnCapa -> p.capas[l.indice].inicioUs
        is Lugar.Audio -> p.audios[l.indice].inicioUs
        null -> null
    }

    /** Cambia un bloque, este donde este. */
    fun cambiar(p: Proyecto, id: Long, f: (Clip) -> Clip): Proyecto = when (val l = lugar(p, id)) {
        is Lugar.Principal -> p.copy(principal = p.principal.toMutableList().also { it[l.indice] = f(it[l.indice]) })
        is Lugar.EnCapa -> p.copy(capas = p.capas.toMutableList().also {
            it[l.indice] = it[l.indice].copy(clip = f(it[l.indice].clip))
        })
        else -> p
    }

    fun cambiarCapa(p: Proyecto, id: Long, f: (Capa) -> Capa): Proyecto {
        val i = p.capas.indexOfFirst { it.id == id }
        if (i < 0) return p
        return p.copy(capas = p.capas.toMutableList().also { it[i] = f(it[i]) })
    }

    fun cambiarAudio(p: Proyecto, id: Long, f: (PistaAudio) -> PistaAudio): Proyecto {
        val i = p.audios.indexOfFirst { it.id == id }
        if (i < 0) return p
        return p.copy(audios = p.audios.toMutableList().also { it[i] = f(it[i]) })
    }

    // ------------------------------------------------------------------
    // Anadir
    // ------------------------------------------------------------------

    /**
     * Mete archivos en la pista principal, en el punto de corte mas cercano
     * a [enUs]: si el cursor esta en la primera mitad de un bloque, delante;
     * si esta en la segunda, detras. Al final si no hay nada.
     *
     * @return el proyecto y los ids nuevos
     */
    fun agregarPrincipal(p: Proyecto, origenes: List<Origen>, enUs: Long = Long.MAX_VALUE): Pair<Proyecto, List<Long>> {
        var q = p
        val nuevos = ArrayList<Clip>()
        for (o in origenes) {
            val (id, r) = q.conId()
            q = r
            nuevos.add(Clip(id = id, origen = o, fluido = o.pideFluidez))
        }
        val donde = puntoDeInsercion(p, enUs)
        val lista = q.principal.toMutableList()
        lista.addAll(donde, nuevos)
        return q.copy(principal = lista) to nuevos.map { it.id }
    }

    /** Indice de la pista principal donde caeria algo soltado en [t]. */
    fun puntoDeInsercion(p: Proyecto, t: Long): Int {
        if (p.principal.isEmpty() || t == Long.MAX_VALUE) return p.principal.size
        val ini = Linea.inicios(p.principal)
        for (i in p.principal.indices) {
            val mitad = ini[i] + p.principal[i].duracionUs / 2
            if (t < mitad) return i
        }
        return p.principal.size
    }

    /** Un bloque encima, desde [inicioUs], en la primera fila libre. */
    fun agregarCapa(p: Proyecto, hacer: (Long) -> Clip, inicioUs: Long): Pair<Proyecto, Long> {
        val (id, q) = p.conId()
        val c = hacer(id)
        val fila = Linea.filaLibre(q.capas, inicioUs, inicioUs + c.duracionUs)
        return q.copy(capas = q.capas + Capa(inicioUs, fila, c)) to id
    }

    fun agregarTexto(p: Proyecto, contenido: String, inicioUs: Long): Pair<Proyecto, Long> =
        agregarCapa(p, { id ->
            Clip(
                id = id,
                texto = Texto(contenido),
                encuadre = Encuadre(ajuste = Ajuste.ENCAJAR),
                entrada = listOf(Movimiento(Animacion.APARECER)),
                duracionEntradaUs = 300_000
            )
        }, inicioUs)

    /** Un video o imagen encima, mas pequeno para que se vea que esta encima. */
    fun agregarSuperpuesto(p: Proyecto, o: Origen, inicioUs: Long): Pair<Proyecto, Long> =
        agregarCapa(p, { id -> Clip(id = id, origen = o, encuadre = Encuadre(ajuste = Ajuste.ENCAJAR, escala = 0.6f), fluido = o.pideFluidez) }, inicioUs)

    fun agregarAudio(p: Proyecto, o: Origen, inicioUs: Long): Pair<Proyecto, Long> {
        val (id, q) = p.conId()
        return q.copy(audios = q.audios + PistaAudio(id, o, inicioUs = inicioUs)) to id
    }

    // ------------------------------------------------------------------
    // Cortar, recortar, mover
    // ------------------------------------------------------------------

    /**
     * Parte un bloque en dos por [tUs] (tiempo de la linea).
     *
     * Las dos mitades se quedan con lo suyo: la entrada y los fotogramas
     * clave de antes del corte en la primera, la salida, la transicion y los
     * de despues en la segunda. Lo que se mueve todo el bloque va en las dos.
     * Con las animaciones aplicadas igual: la primera conserva las de ENTRADA
     * y SIEMPRE, la segunda las de SALIDA y SIEMPRE.
     *
     * @return null si el corte deja un trozo demasiado corto
     */
    fun dividir(p: Proyecto, id: Long, tUs: Long): Pair<Proyecto, Long>? {
        val ini = inicio(p, id) ?: return null
        val local = tUs - ini
        return when (val l = lugar(p, id)) {
            is Lugar.Principal -> {
                val c = p.principal[l.indice]
                val (a, b, q) = partir(p, c, local) ?: return null
                q.copy(principal = q.principal.toMutableList().also {
                    it[l.indice] = a
                    it.add(l.indice + 1, b)
                }) to b.id
            }
            is Lugar.EnCapa -> {
                val capa = p.capas[l.indice]
                val (a, b, q) = partir(p, capa.clip, local) ?: return null
                q.copy(capas = q.capas.toMutableList().also {
                    it[l.indice] = capa.copy(clip = a)
                    it.add(l.indice + 1, capa.copy(inicioUs = tUs, clip = b))
                }) to b.id
            }
            is Lugar.Audio -> {
                val a = p.audios[l.indice]
                if (local < Clip.MINIMO_US || a.duracionUs - local < Clip.MINIMO_US) return null
                val (nid, q) = p.conId()
                val primera = a.copy(hastaUs = a.desdeUs + local, fundidoSalidaUs = 0)
                val segunda = a.copy(id = nid, inicioUs = tUs, desdeUs = a.desdeUs + local, fundidoEntradaUs = 0)
                q.copy(audios = q.audios.toMutableList().also {
                    it[l.indice] = primera
                    it.add(l.indice + 1, segunda)
                }) to nid
            }
            null -> null
        }
    }

    private fun partir(p: Proyecto, c: Clip, local: Long): Triple<Clip, Clip, Proyecto>? {
        if (local < Clip.MINIMO_US || c.duracionUs - local < Clip.MINIMO_US) return null
        val (nid, q) = p.conId()
        // En el archivo, para video; en duracion, para imagen y texto
        val corte = if (c.esVideo) c.desdeUs + (local * c.velocidad.toDouble()).toLong() else c.desdeUs + local
        val (clavesA, clavesB) = partirClaves(c, c.claves, local)
        // Las de antes de simplificar se parten igual, para poder volver a ellas en cada mitad
        val originales = c.clavesOriginales?.let { partirClaves(c, it, local) }
        val a = c.copy(
            hastaUs = corte,
            claves = clavesA,
            clavesOriginales = originales?.first,
            efectos = c.efectos.mapNotNull { it.recortado(0, local) },
            piezas = c.piezas.map { it.recortada(0, local) },
            salida = emptyList(),
            animaciones = c.animaciones.filter { it.momento != MomentoAnimacion.SALIDA },
            fundidoSalidaUs = 0,
            transicion = Transicion.NINGUNA
        )
        val b = c.copy(
            id = nid,
            desdeUs = corte,
            claves = clavesB,
            clavesOriginales = originales?.second,
            efectos = c.efectos.mapNotNull { it.recortado(local, c.duracionUs) },
            piezas = c.piezas.map { it.recortada(local, c.duracionUs) },
            entrada = emptyList(),
            animaciones = c.animaciones.filter { it.momento != MomentoAnimacion.ENTRADA },
            fundidoEntradaUs = 0
        )
        return Triple(a, b, q)
    }

    /**
     * Las [claves] de [c] a cada lado de [local]. Las dos mitades llevan un
     * fotograma clave en el corte con el encuadre que habia ahi: la primera
     * acaba y la segunda empieza exactamente donde estaba, sin salto.
     */
    private fun partirClaves(c: Clip, claves: List<Clave>, local: Long): Pair<List<Clave>, List<Clave>> {
        val enCorte = claveEn(if (claves === c.claves) c else c.copy(claves = claves), local)
        val a = (claves.filter { it.tiempoUs < local } + listOfNotNull(enCorte))
        val b = (listOfNotNull(enCorte?.copy(tiempoUs = 0)) +
            claves.filter { it.tiempoUs > local }.map { it.copy(tiempoUs = it.tiempoUs - local) })
        return a to b
    }

    /**
     * Recorta los bordes de un bloque. [desdeUs] y [hastaUs] en el archivo
     * para video; para imagen y texto, [hastaUs] - [desdeUs] es lo que dura.
     */
    fun recortar(p: Proyecto, id: Long, desdeUs: Long, hastaUs: Long): Proyecto {
        val c = clip(p, id) ?: return p
        val maximo = if (c.esVideo) c.origen!!.duracionUs else Long.MAX_VALUE
        val minimo = (Clip.MINIMO_US * (if (c.esVideo) c.velocidad else 1f)).toLong()
        val d = desdeUs.coerceIn(0, (maximo - minimo).coerceAtLeast(0))
        val h = hastaUs.coerceIn(d + minimo, maximo)
        val desplazado = d - c.desdeUs
        // Los fotogramas clave van en tiempo del clip: si se quita del
        // principio, se mueven con el contenido.
        val corrimiento = if (c.esVideo) (desplazado / c.velocidad.toDouble()).toLong() else desplazado
        return cambiar(p, id) {
            it.copy(
                desdeUs = d,
                hastaUs = h,
                claves = clavesDesde(it, it.claves, corrimiento),
                clavesOriginales = it.clavesOriginales?.let { o -> clavesDesde(it, o, corrimiento) },
                efectos = it.efectos.mapNotNull { ef ->
                    val duracion = if (it.esVideo) ((h - d) / it.velocidad.toDouble()).toLong() else h - d
                    ef.recortado(corrimiento, corrimiento + duracion)
                },
                piezas = it.piezas.map { pieza ->
                    val duracion = if (it.esVideo) ((h - d) / it.velocidad.toDouble()).toLong() else h - d
                    pieza.recortada(corrimiento, corrimiento + duracion)
                }
            )
        }
    }

    /**
     * Las [claves] de [c] con el principio movido [corrimiento] hacia delante
     * (o hacia atras si es negativo). Lo que queda antes del nuevo principio
     * se sustituye por una clave en el 0 con el encuadre que habia ahi, para
     * que no salte.
     */
    private fun clavesDesde(c: Clip, claves: List<Clave>, corrimiento: Long): List<Clave> {
        val inicio = if (corrimiento > 0 && claves.any { k -> k.tiempoUs < corrimiento })
            claveEn(if (claves === c.claves) c else c.copy(claves = claves), corrimiento) else null
        val quedan = listOfNotNull(inicio) +
            claves.filter { k -> k.tiempoUs >= corrimiento && (inicio == null || k.tiempoUs > corrimiento) }
        return quedan.map { k -> k.copy(tiempoUs = k.tiempoUs - corrimiento) }
    }

    /**
     * Un fotograma clave en [local] con el encuadre que el bloque tiene ahi,
     * y la curva del tramo en el que cae. Null si el bloque no tiene claves.
     */
    private fun claveEn(c: Clip, local: Long): Clave? {
        if (c.claves.isEmpty()) return null
        c.claves.firstOrNull { it.tiempoUs == local }?.let { return it }
        val previa = c.claves.filter { it.tiempoUs < local }.maxByOrNull { it.tiempoUs }
        return Clave(local, c.encuadreEn(local), previa?.curva ?: Curva.SUAVE, previa?.bezier)
    }

    fun recortarAudio(p: Proyecto, id: Long, inicioUs: Long, desdeUs: Long, hastaUs: Long): Proyecto =
        cambiarAudio(p, id) {
            val d = desdeUs.coerceIn(0, it.origen.duracionUs - Clip.MINIMO_US)
            val h = hastaUs.coerceIn(d + Clip.MINIMO_US, it.origen.duracionUs)
            it.copy(inicioUs = inicioUs.coerceAtLeast(0), desdeUs = d, hastaUs = h)
        }

    /** Cambia de sitio un bloque de la pista principal. */
    fun moverPrincipal(p: Proyecto, id: Long, aIndice: Int): Proyecto {
        val i = p.principal.indexOfFirst { it.id == id }
        if (i < 0) return p
        val lista = p.principal.toMutableList()
        val c = lista.removeAt(i)
        lista.add(aIndice.coerceIn(0, lista.size), c)
        return p.copy(principal = lista)
    }

    /**
     * Mueve un bloque de encima a [inicioUs], en [fila] si cabe. Si pisa a
     * otro en esa fila, busca la siguiente libre.
     */
    fun moverCapa(p: Proyecto, id: Long, inicioUs: Long, fila: Int): Proyecto {
        val capa = p.capas.firstOrNull { it.id == id } ?: return p
        val ini = inicioUs.coerceAtLeast(0)
        val fin = ini + capa.clip.duracionUs
        val ocupada = p.capas.any { it.id != id && it.fila == fila && it.inicioUs < fin && it.finUs > ini }
        val f = if (!ocupada && fila >= 0) fila else Linea.filaLibre(p.capas, ini, fin, ignorar = id)
        return cambiarCapa(p, id) { it.copy(inicioUs = ini, fila = f) }
    }

    /**
     * Pasa un bloque de encima a la pista principal o al reves. La pista
     * principal no admite huecos: lo que entra se coloca en el punto de
     * corte mas cercano.
     */
    fun aPrincipal(p: Proyecto, id: Long): Proyecto {
        val capa = p.capas.firstOrNull { it.id == id } ?: return p
        if (capa.clip.esTexto) return p
        val donde = puntoDeInsercion(p, capa.inicioUs)
        // Abajo ocupa toda la pantalla: fuera el encuadre de capa y sus claves
        // (tambien las de antes de simplificar), que lo dejarian pequeno o movido
        val clip = capa.clip.copy(
            encuadre = capa.clip.encuadre.copy(escala = 1f, x = 0f, y = 0f, ajuste = Ajuste.CUBRIR),
            claves = emptyList(),
            clavesOriginales = null
        )
        return p.copy(
            capas = p.capas.filter { it.id != id },
            principal = p.principal.toMutableList().also { it.add(donde, clip) }
        )
    }

    fun aCapa(p: Proyecto, id: Long): Proyecto {
        val i = p.principal.indexOfFirst { it.id == id }
        if (i < 0) return p
        val ini = Linea.inicios(p.principal)[i]
        val c = p.principal[i].copy(transicion = Transicion.NINGUNA)
        val q = p.copy(principal = p.principal.filterIndexed { j, _ -> j != i })
        val fila = Linea.filaLibre(q.capas, ini, ini + c.duracionUs)
        return q.copy(capas = q.capas + Capa(ini, fila, c))
    }

    // ------------------------------------------------------------------
    // Borrar y duplicar
    // ------------------------------------------------------------------

    fun eliminar(p: Proyecto, id: Long): Proyecto = p.copy(
        principal = p.principal.filter { it.id != id },
        capas = p.capas.filter { it.id != id },
        audios = p.audios.filter { it.id != id }
    )

    /** Una copia justo detras del original. */
    fun duplicar(p: Proyecto, id: Long): Pair<Proyecto, Long>? {
        val (nid, q) = p.conId()
        return when (val l = lugar(p, id)) {
            is Lugar.Principal -> q.copy(principal = q.principal.toMutableList().also {
                it.add(l.indice + 1, it[l.indice].copy(id = nid))
            }) to nid
            is Lugar.EnCapa -> {
                val c = p.capas[l.indice]
                val ini = c.finUs
                val fila = Linea.filaLibre(q.capas, ini, ini + c.clip.duracionUs)
                q.copy(capas = q.capas + c.copy(inicioUs = ini, fila = fila, clip = c.clip.copy(id = nid))) to nid
            }
            is Lugar.Audio -> {
                val a = p.audios[l.indice]
                q.copy(audios = q.audios + a.copy(id = nid, inicioUs = a.finUs)) to nid
            }
            null -> null
        }
    }

    // ------------------------------------------------------------------
    // Fotogramas clave
    // ------------------------------------------------------------------

    /**
     * Pone (o sustituye) un fotograma clave con el encuadre de ahora en el
     * instante [tUs] de la linea.
     */
    fun ponerClave(p: Proyecto, id: Long, tUs: Long, encuadre: Encuadre): Proyecto {
        val ini = inicio(p, id) ?: return p
        val local = (tUs - ini).coerceAtLeast(0)
        return cambiar(p, id) { c ->
            val previa = c.claves.firstOrNull { kotlin.math.abs(it.tiempoUs - local) <= TOLERANCIA_CLAVE }
            val sin = c.claves.filter { it !== previa }
            // El primer fotograma clave fija tambien el de partida: sin esto,
            // el bloque saltaria de su encuadre base al primer clave.
            val base = if (sin.isEmpty() && previa == null && local > TOLERANCIA_CLAVE) listOf(Clave(0, c.encuadre)) else emptyList()
            // Si ya habia uno ahi se cambia el encuadre y se le deja su curva
            val nueva = previa?.copy(encuadre = encuadre) ?: Clave(local, encuadre)
            c.copy(claves = (base + sin + nueva).sortedBy { it.tiempoUs })
        }
    }

    fun quitarClave(p: Proyecto, id: Long, tUs: Long): Proyecto {
        val ini = inicio(p, id) ?: return p
        val local = tUs - ini
        return cambiar(p, id) { c -> c.copy(claves = c.claves.filter { kotlin.math.abs(it.tiempoUs - local) > TOLERANCIA_CLAVE }) }
    }

    /** Cambia el fotograma clave que cae en [tUs] (su curva, por ejemplo). */
    fun cambiarClave(p: Proyecto, id: Long, tUs: Long, f: (Clave) -> Clave): Proyecto {
        val ini = inicio(p, id) ?: return p
        val local = tUs - ini
        return cambiar(p, id) { c ->
            c.copy(claves = c.claves.map { if (kotlin.math.abs(it.tiempoUs - local) <= TOLERANCIA_CLAVE) f(it) else it })
        }
    }

    /**
     * Cambia el encuadre de un bloque tal y como se ve en [tUs].
     *
     * Sin fotogramas clave cambia el del bloque entero. Con ellos, lo que se
     * anima (posicion, escala, giro, opacidad) va a un fotograma clave en
     * ese instante, nuevo o el que ya habia: es como funcionan los editores
     * de movil, mover algo con claves crea una clave.
     */
    fun ajustarEncuadre(p: Proyecto, id: Long, tUs: Long, f: (Encuadre) -> Encuadre): Proyecto {
        val c = clip(p, id) ?: return p
        if (c.claves.isEmpty()) return cambiar(p, id) { it.copy(encuadre = f(it.encuadre)) }
        val ini = inicio(p, id) ?: return p
        val local = (tUs - ini).coerceIn(0, c.duracionUs)
        val ahora = c.encuadreEn(local)
        val nuevo = f(ahora)
        val q = cambiar(p, id) {
            it.copy(encuadre = it.encuadre.copy(ajuste = nuevo.ajuste, recorte = nuevo.recorte, espejo = nuevo.espejo))
        }
        val animado = { e: Encuadre -> listOf(e.escala, e.x, e.y, e.rotacion, e.opacidad) }
        return if (animado(ahora) == animado(nuevo)) q else ponerClave(q, id, ini + local, nuevo)
    }

    /** Medio fotograma: lo que se considera "el mismo instante" para un clave. */
    const val TOLERANCIA_CLAVE = 16_000L
}
