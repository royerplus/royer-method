package com.royermethod.app.editor.modelo

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Que hay que dibujar en un fotograma, sin dibujar nada.
 *
 * Esta separado del motor de OpenGL a proposito: aqui se decide todo lo que
 * puede salir mal (que bloque toca, en que instante del archivo, donde cae,
 * cuanto se ha movido durante el fotograma) y eso se puede probar en el
 * ordenador sin telefono. El motor solo obedece.
 *
 * El desenfoque de movimiento sale de aqui. Un fotograma de 30 fps no es un
 * instante: es un trozo de 33 ms. Si algo se mueve durante ese trozo, una
 * camara real lo veria corrido. Por eso cada bloque no se describe con una
 * posicion sino con varias, repartidas por el tiempo de exposicion, y el
 * motor las promedia. Es lo que hace que una transicion a 30 fps se vea tan
 * fluida como a 60.
 */
class Escena(
    private val proyecto: Proyecto,
    /** Pixeles del lienzo de salida. */
    private val ancho: Int,
    private val alto: Int,
    /** Medidas propias del contenido de un bloque, en pixeles. */
    private val medidor: Medidor
) {
    private val inicios = Linea.inicios(proyecto.principal)
    private val duracionFotograma = 1_000_000.0 / Linea.FPS

    /**
     * Fraccion del fotograma que se "expone". 0.5 es el obturador de 180
     * grados del cine: rastro suficiente para que el movimiento sea continuo
     * sin que se vuelva una mancha.
     */
    private val obturador = 0.5

    fun cuadro(n: Long): Cuadro = cuadroEn(Linea.tiempoDe(n), n)

    /** Para la vista previa al arrastrar, que no cae en fotogramas exactos. */
    fun cuadroEn(t: Long, n: Long = Linea.fotogramaEn(t)): Cuadro {
        val k = if (proyecto.salida.desenfoqueMovimiento) proyecto.salida.muestras.coerceIn(1, MAX_MUESTRAS) else 1
        val pasos = ArrayList<Paso>()
        val en = Linea.principalEn(proyecto.principal, inicios, t)

        // 1. fondo, 2. pista principal
        pasos.add(PasoSolido(colorFondo(), FloatArray(1) { 1f }))
        if (en != null) {
            if (en.segundo == null) {
                escena(pasos, en.primero, t, t - en.localPrimero, k, Lado())
            } else {
                transicion(pasos, en, t, k)
            }
        }

        // 3. capas, de abajo arriba
        for (c in Linea.capasEn(proyecto.capas, t)) {
            pasos.addAll(dibujos(c.clip, t, c.inicioUs, k, c.mezcla))
        }

        // Las escenas que al final no se mueven ni se mezclan van directas
        val planos = pasos.map { it.simplificado() }.flatMap {
            if (it is PasoGrupo && it.directo) it.pasos else listOf(it)
        }
        // Lo que queda debajo de algo opaco que cubre todo no se ve: ni se
        // dibuja ni se decodifica (el lado A cuando la escena B ya esta entera
        // encima, todo en el momento negro de un fundido a negro...).
        val tapa = planos.indexOfLast { it is PasoSolido && it.opacidadMedia >= 0.9999f }
        val pasosFinales = if (tapa > 0) planos.subList(tapa, planos.size) else planos
        return Cuadro(n, t, pasosFinales)
    }

    private fun colorFondo(): Int = when (proyecto.fondo) {
        Fondo.BLANCO -> 0xFFFFFFFF.toInt()
        else -> 0xFF000000.toInt()
    }

    /** Instantes de las muestras alrededor de [t], dentro de la exposicion. */
    private fun instantes(t: Long, k: Int): LongArray {
        if (k <= 1) return longArrayOf(t)
        val exp = duracionFotograma * obturador
        return LongArray(k) { i -> (t + ((i + 0.5) / k - 0.5) * exp).toLong() }
    }

    /**
     * Un bloque en [t], con todas sus muestras.
     *
     * @param inicio donde empieza el bloque en la linea de tiempo
     */
    private fun dibujos(
        clip: Clip,
        t: Long,
        inicio: Long,
        k: Int,
        mezcla: Mezcla,
        transicion: ((Int) -> Efecto)? = null
    ): List<Paso> {
        // Las mitades de un efecto se dibujan como dos piezas, por el mismo
        // camino que las partes hechas a mano. Si ya hay partes, mandan ellas.
        val mitades = if (clip.piezas.isEmpty()) mitadesEn(clip, t, inicio, k) else null
        if (clip.piezas.isEmpty() && mitades == null) return listOf(dibujo(clip, t, inicio, k, mezcla, transicion))
        val partes = if (mitades != null) mitades.piezas.mapIndexed { n, it ->
            dibujo(clip, t, inicio, k, mezcla, transicion, it, mitades.pose(n))
        } else clip.piezas.map { dibujo(clip, t, inicio, k, mezcla, transicion, it) }
        // Una sola textura completa sirve a todas las partes; no se copia de
        // nuevo a otro tamano al pasar de la mitad superior a la inferior.
        val w = partes.maxOf { it.anchoEnLienzo }
        val h = partes.maxOf { it.altoEnLienzo }
        val texto = partes.maxOf { it.escalaTexto }
        val cubren = mitades != null || clip.piezasCubrenFuente
        if (cubren && partes.drop(1).all { mismaTrayectoria(partes.first(), it) }) {
            // Promediar dos particiones y mezclarlas despues por OVER daria
            // alfa0.75 en un pixel cubierto a medias por cada una. Reunidas,
            // la fuente completa se muestrea una sola vez y no deja costura.
            return listOf(partes.first().copy(recorteFuente = null, anchoEnLienzo = w, altoEnLienzo = h, escalaTexto = texto))
        }
        val comunes = partes.map { it.copy(anchoEnLienzo = w, altoEnLienzo = h, escalaTexto = texto, mezcla = Mezcla.NORMAL) }
        return listOf(PasoPartes(comunes, mezcla))
    }

    private fun mismaTrayectoria(a: PasoClip, b: PasoClip): Boolean =
        a.opacidades.contentEquals(b.opacidades) && a.matrices.size == b.matrices.size &&
            a.matrices.indices.all { a.matrices[it].contentEquals(b.matrices[it]) }

    /** Las dos mitades de un efecto MITADES y como se mueve cada una en cada muestra. */
    private class Mitades(val piezas: List<PiezaVideo>, val pose: (Int) -> (Long) -> Encuadre)

    /**
     * Si un efecto MITADES actua en alguna muestra de este fotograma, el bloque
     * se parte en dos. La pose se calcula en cada muestra con el instante
     * acotado al tramo: en el fotograma del borde todas las muestras ven las
     * mitades (separadas al empezar, juntas al acabar) y el rastro no parpadea.
     * Con varios, manda el ultimo de la lista.
     */
    private fun mitadesEn(clip: Clip, t: Long, inicio: Long, k: Int): Mitades? {
        if (clip.efectos.none { it.tipo == TipoEfectoVideo.MITADES }) return null
        val ultimo = (clip.duracionUs - 1).coerceAtLeast(0)
        val ts = instantes(t, k)
        val ef = clip.efectos.lastOrNull { e ->
            e.tipo == TipoEfectoVideo.MITADES && ts.any { e.activoEn((it - inicio).coerceIn(0, ultimo)) }
        } ?: return null
        val a = ef.ajustes
        val r = clip.encuadre.recorte
        // El corte, en medio de lo que se ve del bloque. Las dos regiones salen
        // del mismo numero: comparten el borde exacto y juntas cubren la fuente.
        val piezas = if (a.vertical) {
            val c = r.izquierda + (1f - r.izquierda - r.derecha) / 2f
            val izquierda = PiezaVideo("Izquierda", RegionUv(0f, 0f, c, 1f))
            val derecha = PiezaVideo("Derecha", RegionUv(c, 0f, 1f, 1f))
            // En espejo la izquierda de la fuente queda a la derecha del lienzo
            if (clip.encuadre.espejo) listOf(derecha, izquierda) else listOf(izquierda, derecha)
        } else {
            val c = r.arriba + (1f - r.arriba - r.abajo) / 2f
            listOf(PiezaVideo("Arriba", RegionUv(0f, 0f, 1f, c)), PiezaVideo("Abajo", RegionUv(0f, c, 1f, 1f)))
        }
        val (ux, uy) = direccion(a.angulo)
        val desde = ef.inicioUs
        val hasta = (ef.finUs - 1).coerceAtLeast(desde)
        // La A llega desde el lado contrario al angulo y avanza hacia el; la B al reves
        return Mitades(piezas) { n ->
            { local ->
                val pl = local.coerceIn(desde, hasta)
                val off = ef.valorEn(pl) * ef.fuerzaEn(pl)
                val s = if (n == 0) -off else off
                Encuadre(x = s * ux + 0f, y = s * uy + 0f)
            }
        }
    }

    /**
     * Un paso lleva una sola mascara para todas las muestras del obturador,
     * pero dentro de un fotograma puede acabar una y empezar otra (una linea
     * que revela y enseguida un iris). Sus bordes no se miden igual: la
     * posicion de la linea leida como radio daria un destello. Manda la de la
     * muestra del centro; las muestras con otra clase de mascara copian el
     * borde de la muestra mas cercana que tiene la que manda. Las que no
     * tienen mascara se quedan con el borde que no recorta nada.
     */
    private fun unaMascara(mascaras: Array<Mascara?>, bordes: FloatArray): Mascara? {
        val n = mascaras.size
        var manda: Mascara? = null
        var mejor = Int.MAX_VALUE
        for (i in 0 until n) {
            val m = mascaras[i] ?: continue
            val d = abs(2 * i - (n - 1))
            if (d < mejor) { mejor = d; manda = m }
        }
        if (manda == null) return null
        val original = bordes.copyOf()
        for (i in 0 until n) {
            val m = mascaras[i] ?: continue
            if (mismaClase(m, manda)) continue
            var j = -1
            for (q in 0 until n) {
                val mq = mascaras[q] ?: continue
                if (mismaClase(mq, manda) && (j < 0 || abs(q - i) < abs(j - i))) j = q
            }
            bordes[i] = original[j]
        }
        // Fuera de un iris invertido tiene que verse entero. Su borde neutro
        // esta al otro lado que el de las mascaras que dejan ver el interior.
        if (manda.invertida) for (i in bordes.indices) {
            if (bordes[i] == SIN_BORDE) bordes[i] = -SIN_BORDE
        }
        return manda
    }

    /**
     * Si dos mascaras miden el borde igual y se puede pasar de una a otra. El
     * centro y el grosor del anillo cuentan: el mismo borde con otro centro o
     * con otro grosor recorta otra cosa.
     */
    private fun mismaClase(a: Mascara, b: Mascara): Boolean =
        a.radial == b.radial && a.invertida == b.invertida &&
            a.centroX == b.centroX && a.centroY == b.centroY && a.grosor == b.grosor &&
            (a.radial || (a.dirX == b.dirX && a.dirY == b.dirY))

    private fun dibujo(
        clip: Clip,
        t: Long,
        inicio: Long,
        k: Int,
        mezcla: Mezcla,
        transicion: ((Int) -> Efecto)? = null,
        pieza: PiezaVideo? = null,
        /** Pose de la pieza en cada muestra, si no sale de sus claves (las mitades de un efecto). */
        pose: ((Long) -> Encuadre)? = null
    ): PasoClip {
        val ts = instantes(t, k)
        val matrices = ArrayList<FloatArray>(ts.size)
        val opacidades = FloatArray(ts.size)
        // Una muestra sin mascara lleva el borde tan lejos que no recorta nada
        val bordes = FloatArray(ts.size) { SIN_BORDE }
        val mascaras = arrayOfNulls<Mascara>(ts.size)
        var desenfoque = 0f
        var desenfoqueX = 0f
        var desenfoqueY = 0f
        var escalaMax = 0f
        var anchoMax = 0f
        var altoMax = 0f
        var contraido = false

        val (cw, ch) = medidor.medidas(clip, alto)
        for ((i, ti) in ts.withIndex()) {
            val local = (ti - inicio).coerceIn(0, (clip.duracionUs - 1).coerceAtLeast(0))
            var e = encuadreEn(clip, local)
            var op = e.opacidad
            // Entrada y salida
            val anim = animacion(clip, local).y(efectosVideo(clip, local))
            e = anim.aplicar(e)
            op *= anim.opacidad
            if (anim.desenfoque > 0f) desenfoque += anim.desenfoque / ts.size
            desenfoqueX += anim.desenfoqueX / ts.size
            desenfoqueY += anim.desenfoqueY / ts.size
            var escalaX = anim.escalaX
            var escalaY = anim.escalaY
            // Lo que imponga la transicion encima
            var conMascara = anim.mascara != null
            if (anim.mascara != null) {
                mascaras[i] = anim.mascara
                bordes[i] = anim.borde
            }
            if (transicion != null) {
                val a = transicion(i)
                e = a.aplicar(e)
                op *= a.opacidad
                if (a.desenfoque > 0f) desenfoque += a.desenfoque / ts.size
                desenfoqueX += a.desenfoqueX / ts.size
                desenfoqueY += a.desenfoqueY / ts.size
                escalaX *= a.escalaX
                escalaY *= a.escalaY
                if (a.mascara != null) {
                    mascaras[i] = a.mascara
                    bordes[i] = a.borde
                    conMascara = true
                }
            }
            if (escalaX < 0.999f || escalaY < 0.999f) contraido = true
            if (anim.revelar >= 0f && !conMascara) {
                // La mascara de revelar va en el espacio del lienzo, a lo ancho
                // de donde cae el bloque.
                val caja = caja(clip, e, cw, ch)
                mascaras[i] = Mascara(1f, 0f, suave = 6f)
                bordes[i] = caja.first + (caja.second - caja.first) * anim.revelar - ancho / 2f
            }
            val relativa = if (pieza == null) null else pose?.invoke(local) ?: pieza.encuadreEn(local)
            val base = matriz(clip, e, cw, ch, escalaX, escalaY)
            val m = if (pieza != null && relativa != null) moverPieza(base, pieza.region.acotada(), relativa, clip.encuadre.recorte) else base
            if (relativa != null) op *= relativa.opacidad
            matrices.add(m)
            opacidades[i] = op.coerceIn(0f, 1f)
            val escalaPieza = relativa?.escala?.coerceAtLeast(0f) ?: 1f
            escalaMax = max(escalaMax, e.escala * max(escalaX, escalaY) * escalaPieza)
            val (w, h) = tamano(clip, e, cw, ch)
            anchoMax = max(anchoMax, w * escalaX * escalaPieza)
            altoMax = max(altoMax, h * escalaY * escalaPieza)
        }

        val localCentro = t - inicio
        val mascara = unaMascara(mascaras, bordes)
        // Flashes y RGB son luz, no movimiento: van en el instante del
        // fotograma, sin promediar el obturador. Si no, el flash que cae justo
        // en el golpe saldria a medias, mezclado con el final del anterior.
        val luces = animacion(clip, localCentro).y(destellos(clip, localCentro)).y(efectosVideo(clip, localCentro))
        return PasoClip(
            clip = clip,
            tiempoArchivoUs = clip.tiempoArchivo(localCentro),
            exposicionUs = if (ts.size > 1 || clip.fluido) (duracionFotograma * obturador * clip.velocidad).toLong() else 0,
            matrices = matrices,
            opacidades = opacidades,
            mascara = mascara,
            bordes = bordes,
            color = clip.colorFinal(),
            mezcla = mezcla,
            desenfoque = desenfoque,
            esFondo = false,
            anchoEnLienzo = anchoMax,
            altoEnLienzo = altoMax,
            escalaTexto = escalaMax,
            luz = luces.luz.coerceIn(-1f, 1f),
            rgb = luces.rgb.coerceIn(0f, alto * 0.1f),
            negativo = luces.negativo.coerceIn(0f, 1f),
            // Un borde repetido no debe rellenar el hueco de un cierre.
            espejoBordes = if (contraido) 0f else luces.espejoBordes.coerceIn(0f, 1f),
            desenfoqueX = desenfoqueX,
            desenfoqueY = desenfoqueY,
            recorteFuente = pieza?.region?.acotada()
        )
    }

    /** Compone Mbase * inversa(Tpieza), con pivote en su region visible. */
    private fun moverPieza(m: FloatArray, region: RegionUv, e: Encuadre, recorte: Recorte): FloatArray {
        if (e.x == 0f && e.y == 0f && e.escala == 1f && e.rotacion == 0f) return m
        if (!e.x.isFinite() || !e.y.isFinite() || !e.escala.isFinite() || !e.rotacion.isFinite() || e.escala < 1e-6f) return FUERA.copyOf()
        if (e.escala == 1f && e.rotacion == 0f) {
            return m.copyOf().also {
                it[6] = m[6] - m[0] * e.x * ancho - m[3] * e.y * alto
                it[7] = m[7] - m[1] * e.x * ancho - m[4] * e.y * alto
            }
        }
        val visible = region.interseccion(RegionUv(recorte.izquierda, recorte.arriba, 1f - recorte.derecha, 1f - recorte.abajo)) ?: region
        val det = m[0] * m[4] - m[3] * m[1]
        if (abs(det) < 1e-16f) return FUERA.copyOf()
        val u = visible.centroX - m[6]
        val v = visible.centroY - m[7]
        val cx = (u * m[4] - m[3] * v) / det
        val cy = (m[0] * v - u * m[1]) / det
        val r = Math.toRadians(e.rotacion.toDouble())
        val a = cos(r).toFloat() / e.escala
        val b = sin(r).toFloat() / e.escala
        val c = -b
        val d = a
        val dx = e.x * ancho
        val dy = e.y * alto
        val tx = cx - a * (cx + dx) - b * (cy + dy)
        val ty = cy - c * (cx + dx) - d * (cy + dy)
        return floatArrayOf(
            m[0] * a + m[3] * c, m[1] * a + m[4] * c, 0f,
            m[0] * b + m[3] * d, m[1] * b + m[4] * d, 0f,
            m[0] * tx + m[3] * ty + m[6], m[1] * tx + m[4] * ty + m[7], 1f
        )
    }

    /**
     * El clip ampliado y desenfocado detras, para que no queden franjas.
     *
     * Solo cuando hace falta: si el bloque de delante [delante] tapa todo el
     * lienzo en todas sus muestras, el fondo no se veria y no se pide. La
     * cuenta se hace con las matrices reales, asi que tambien sale cuando el
     * bloque deja hueco solo durante una animacion o una transicion.
     */
    private fun fondoDe(delante: PasoClip, local: Long, forzar: Boolean = false): PasoClip? {
        val clip = delante.clip
        if (clip.esTexto) return null
        if (!forzar && cubre(delante)) return null
        val (cw, ch) = medidor.medidas(clip, alto)
        if (cw <= 0f || ch <= 0f) return null
        val e = Encuadre(ajuste = Ajuste.CUBRIR, escala = 1.15f)
        val m = matriz(clip, e, cw, ch)
        return PasoClip(
            clip = clip,
            tiempoArchivoUs = clip.tiempoArchivo(local),
            // La misma exposicion que el clip de delante: si es fluido, los dos
            // piden el mismo fotograma generado y se calcula una sola vez.
            exposicionUs = delante.exposicionUs,
            matrices = listOf(m),
            opacidades = floatArrayOf(1f),
            mascara = null,
            bordes = FloatArray(1),
            color = clip.colorFinal().mas(AjustesColor(brillo = -0.12f, saturacion = 0.1f), 1f),
            mezcla = Mezcla.NORMAL,
            desenfoque = 0f,
            esFondo = true,
            anchoEnLienzo = ancho * 1.15f,
            altoEnLienzo = alto * 1.15f,
            escalaTexto = 1f,
            // El flash es de toda la imagen: el relleno de detras tambien
            luz = delante.luz,
            negativo = delante.negativo
        )
    }

    /** ¿El bloque tapa el lienzo entero en todas sus muestras? */
    private fun cubre(p: PasoClip): Boolean {
        if (p.recorteFuente != null) return false
        if (p.mascara != null) return false
        val r = p.clip.encuadre.recorte
        val margen = 1e-4f
        val esquinas = floatArrayOf(0f, 0f, ancho.toFloat(), 0f, 0f, alto.toFloat(), ancho.toFloat(), alto.toFloat())
        for (m in p.matrices) {
            for (j in 0 until 4) {
                val x = esquinas[j * 2]
                val y = esquinas[j * 2 + 1]
                val u = m[0] * x + m[3] * y + m[6]
                val v = m[1] * x + m[4] * y + m[7]
                if (u < r.izquierda - margen || u > 1f - r.derecha + margen) return false
                if (v < r.arriba - margen || v > 1f - r.abajo + margen) return false
            }
        }
        return true
    }

    // ------------------------------------------------------------------
    // Transiciones
    // ------------------------------------------------------------------

    /**
     * Lo que una transicion le hace a uno de sus dos lados.
     *
     * Cada lado es una escena entera: su fondo y su bloque. Lo que mueve o
     * descubre la transicion (empujar, la linea, el fundido) se aplica a la
     * escena ya montada, no a cada pieza por separado. Si se aplicara a cada
     * pieza, en el borde suave de un barrido o en la estela de un empujon se
     * mezclarian tres cosas (el bloque, su fondo y lo de detras) y saldria una
     * franja oscura moviendose. Montada aparte, la mezcla es exacta.
     */
    private class Lado(
        /** Al bloque, dentro de su escena: zoom, giro, desenfoque. */
        val alClip: ((Int) -> Efecto)? = null,
        /** A la escena entera: opacidad, desplazamiento, mascara. */
        val aEscena: ((Int) -> Efecto)? = null,
        /**
         * La escena se alarga con sus bordes. Para la que queda debajo de otra
         * que entra empujando: asi en la junta no queda ni un pixel de hueco.
         */
        val extender: Boolean = false
    )

    private fun transicion(pasos: MutableList<Paso>, en: EnPrincipal, t: Long, k: Int) {
        val a = en.primero
        val b = en.segundo!!
        val inicioA = t - en.localPrimero
        val inicioB = t - en.localSegundo
        val solape = Linea.solape(a, b)
        val ts = instantes(t, k)
        val ajuste = a.ajusteTransicion
        // Avance de la transicion en cada muestra, con su curva. Por defecto
        // suave: arrancar y frenar poco a poco es lo que hace que no parezca
        // un corte con prisa.
        val ps = FloatArray(ts.size) { i ->
            val p = if (solape > 0) ((ts[i] - inicioB).toDouble() / solape).toFloat() else 1f
            curva(ajuste.curva, p.coerceIn(0f, 1f))
        }
        val tipo = a.transicion
        val fuerza = ajuste.intensidad
        val bx = ajuste.dx
        val by = ajuste.dy
        // Para empujar: lo justo para que salga entero del lienzo en cualquier
        // angulo. En diagonal no basta con moverse un lienzo en cada eje a
        // medias: el lado mas largo del vector tiene que llegar a 1.
        val largo = maxOf(abs(bx), abs(by))
        val ex = bx / largo
        val ey = by / largo
        val extension = (abs(bx) * ancho + abs(by) * alto) / 2f
        val grosorLinea = alto * 0.012f
        fun borde(p: Float): Float {
            // Desde fuera del todo por un lado hasta fuera del todo por el otro,
            // contando el halo de la linea, para que no aparezca ya a medias.
            val margen = grosorLinea * 6f
            return -extension - margen + (2 * extension + 2 * margen) * p
        }
        // Cambio seco en la mitad, para las que lo tapan con algo. El lado A
        // no se apaga: la escena B es opaca y lo tapa, y asi en el fotograma
        // del cambio la mezcla de los dos es exacta y no se oscurece.
        val despues = { i: Int -> Efecto(opacidad = if (ps[i] < 0.5f) 0f else 1f) }
        fun destello(pico: Float, pot: Float) = FloatArray(ts.size) { i ->
            ((1f - abs(ps[i] * 2f - 1f)) * pico).coerceIn(0f, 1f).pow(pot)
        }

        var ladoA = Lado()
        var ladoB = Lado(aEscena = { i -> Efecto(opacidad = ps[i]) })
        val encima = ArrayList<Paso>()

        when (tipo) {
            Transicion.NEGRO -> {
                ladoB = Lado(aEscena = despues)
                // Negro del todo un momento alrededor del cambio: el cambio
                // queda siempre tapado, caiga donde caiga dentro del fotograma.
                encima.add(PasoSolido(0xFF000000.toInt(), destello(1.25f, 1f), debajo = false))
            }

            Transicion.DESTELLO -> {
                ladoB = Lado(aEscena = despues)
                // Pico en el centro, con caida rapida: un destello, no un fundido
                encima.add(PasoSolido(ajuste.color, destello(1.15f, 1.6f), debajo = false))
            }

            Transicion.LINEA, Transicion.BARRIDO -> {
                val suaveBorde = if (tipo == Transicion.LINEA) 1.5f else alto * 0.04f
                val mascara = Mascara(bx, by, suaveBorde)
                ladoB = Lado(aEscena = { i -> Efecto(mascara = mascara, borde = borde(ps[i])) })
                if (tipo == Transicion.LINEA) {
                    encima.add(
                        PasoLinea(
                            dirX = bx, dirY = by,
                            bordes = FloatArray(ts.size) { borde(ps[it]) },
                            grosor = grosorLinea * fuerza.coerceIn(0.3f, 3f),
                            color = ajuste.color
                        )
                    )
                }
            }

            Transicion.EMPUJAR -> {
                ladoA = Lado(aEscena = { i -> Efecto(dx = ex * ps[i], dy = ey * ps[i]) }, extender = true)
                ladoB = Lado(aEscena = { i -> Efecto(dx = -ex * (1f - ps[i]), dy = -ey * (1f - ps[i])) })
            }

            Transicion.DESLIZAR -> {
                // El de debajo se aleja un poco mientras lo tapan: da profundidad
                ladoA = Lado(alClip = { i -> Efecto(escala = 1f - 0.06f * fuerza * ps[i]) })
                ladoB = Lado(aEscena = { i -> Efecto(dx = -ex * (1f - ps[i]), dy = -ey * (1f - ps[i])) })
            }

            Transicion.ZOOM -> {
                ladoA = Lado(alClip = { i -> Efecto(escala = 1f + 0.9f * fuerza * ps[i]) })
                ladoB = Lado(
                    alClip = { i -> Efecto(escala = 1f + 0.9f * fuerza * (1f - ps[i])) },
                    aEscena = { i -> Efecto(opacidad = paso(0.35f, 0.65f, ps[i])) }
                )
            }

            Transicion.DESENFOQUE -> {
                val radio = alto * 0.03f * fuerza
                ladoA = Lado(alClip = { i -> Efecto(desenfoque = radio * min(1f, ps[i] * 2f)) })
                ladoB = Lado(
                    alClip = { i -> Efecto(desenfoque = radio * min(1f, (1f - ps[i]) * 2f)) },
                    aEscena = { i -> Efecto(opacidad = paso(0.3f, 0.7f, ps[i])) }
                )
            }

            Transicion.GIRO -> {
                val g = 90f * fuerza
                ladoA = Lado(alClip = { i -> Efecto(rotacion = g * ps[i], escala = 1f + ps[i] * 0.5f) })
                ladoB = Lado(
                    alClip = { i -> Efecto(rotacion = -g * (1f - ps[i]), escala = 1f + (1f - ps[i]) * 0.5f) },
                    aEscena = despues
                )
            }

            Transicion.CIRCULO -> {
                val radio = kotlin.math.sqrt((ancho * ancho + alto * alto).toFloat()) / 2f + 4f
                val mascara = Mascara(0f, 0f, alto * 0.006f + 1f, radial = true)
                ladoB = Lado(aEscena = { i -> Efecto(mascara = mascara, borde = radio * ps[i]) })
            }

            Transicion.ZOOM_GIRO -> {
                ladoA = Lado(alClip = { i -> Efecto(escala = 1f + 1.2f * fuerza * ps[i], rotacion = 35f * fuerza * ps[i]) })
                ladoB = Lado(
                    alClip = { i -> Efecto(escala = 1f + 1.2f * fuerza * (1f - ps[i]), rotacion = -35f * fuerza * (1f - ps[i])) },
                    aEscena = { i -> Efecto(opacidad = paso(0.4f, 0.6f, ps[i])) }
                )
            }

            Transicion.IMPACTO -> {
                ladoA = Lado(alClip = { i -> Efecto(escala = 1f + 0.6f * fuerza * ps[i] * ps[i]) })
                ladoB = Lado(
                    alClip = { i -> Efecto(escala = 1f + 0.35f * fuerza * (1f - ps[i]) * (1f - ps[i])) },
                    aEscena = despues
                )
                encima.add(PasoSolido(ajuste.color, destello(1.4f, 2.2f), debajo = false))
            }

            Transicion.APLASTAR -> {
                val vertical = abs(by) >= abs(bx)
                ladoA = Lado(aEscena = { i ->
                    val escala = (1f - ps[i]).coerceIn(0f, 1f).pow(fuerza.coerceIn(0.1f, 3f)).coerceAtLeast(0.001f)
                    Efecto(escalaX = if (vertical) 1f else escala, escalaY = if (vertical) escala else 1f)
                })
                ladoB = Lado()
            }

            // Fundido, y cualquier cosa que no tenga dibujo propio
            else -> Unit
        }

        if (tipo == Transicion.APLASTAR) {
            escena(pasos, b, t, inicioB, k, ladoB)
            escena(pasos, a, t, inicioA, k, ladoA)
        } else {
            escena(pasos, a, t, inicioA, k, ladoA)
            escena(pasos, b, t, inicioB, k, ladoB)
        }
        pasos.addAll(encima)
    }

    /** Un lado de la transicion: su fondo y su bloque, aparte si hace falta. */
    private fun escena(pasos: MutableList<Paso>, clip: Clip, t: Long, inicio: Long, k: Int, lado: Lado) {
        val dentro = ArrayList<Paso>(3)
        val delante = dibujos(clip, t, inicio, k, Mezcla.NORMAL, lado.alClip)
        if (proyecto.fondo == Fondo.DESENFOCADO) {
            // Partido en piezas, entre ellas se ve lo de detras aunque el
            // bloque entero tapara el lienzo. Si las piezas se juntaron en un
            // solo dibujo, es el bloque de siempre.
            val unico = delante.singleOrNull() as? PasoClip
            val fondo = if (unico != null) fondoDe(unico, t - inicio)
            else fondoDe(dibujo(clip, t, inicio, k, Mezcla.NORMAL, lado.alClip), t - inicio, forzar = true)
            fondo?.let { dentro.add(it) }
        }
        dentro.addAll(delante)

        val ef = lado.aEscena
        if (ef == null) {
            // Se queda en su sitio y entera: directamente sobre el lienzo
            pasos.addAll(dentro)
            return
        }
        val n = instantes(t, k).size
        val efs = List(n) { ef(it) }
        val bordes = FloatArray(n) { if (efs[it].mascara != null) efs[it].borde else SIN_BORDE }
        val mascara = unaMascara(Array(n) { efs[it].mascara }, bordes)
        pasos.add(
            PasoGrupo(
                // Con su color de fondo: la escena es opaca y tapa la otra
                pasos = listOf(PasoSolido(colorFondo(), floatArrayOf(1f))) + dentro,
                matrices = efs.map { desplazamiento(it.dx, it.dy, it.escalaX, it.escalaY) },
                opacidades = FloatArray(n) { efs[it].opacidad.coerceIn(0f, 1f) },
                mascara = mascara,
                bordes = bordes,
                extender = lado.extender
            )
        )
    }

    /** Del lienzo a la escena movida [dx], [dy] lienzos. */
    private fun desplazamiento(dx: Float, dy: Float, escalaX: Float = 1f, escalaY: Float = 1f): FloatArray {
        val sx = escalaX.coerceAtLeast(0.001f)
        val sy = escalaY.coerceAtLeast(0.001f)
        return floatArrayOf(1f / (ancho * sx), 0f, 0f, 0f, 1f / (alto * sy), 0f,
            0.5f - (0.5f + dx) / sx, 0.5f - (0.5f + dy) / sy, 1f)
    }

    // ------------------------------------------------------------------
    // Encuadre en el tiempo
    // ------------------------------------------------------------------

    /**
     * El encuadre a [local] del inicio, con fotogramas clave y movimiento
     * continuo: el de antes y el de cada animacion SIEMPRE que este puesta.
     */
    fun encuadreEn(clip: Clip, local: Long): Encuadre = continua(clip, local, clip.encuadreEn(local))

    private fun continua(clip: Clip, local: Long, e0: Encuadre): Encuadre {
        if (clip.continua.isEmpty() && clip.animaciones.isEmpty()) return e0
        // Lo que va del bloque, para las que lo recorren entero (acercamiento, paneo)
        val f = (local.toFloat() / clip.duracionUs.coerceAtLeast(1)).coerceIn(0f, 1f)
        var e = e0
        for (c in clip.continua) e = continuo(c, local / 1_000_000f * c.velocidad, f, e)
        for (a in clip.animaciones) if (a.activa && a.momento == MomentoAnimacion.SIEMPRE) {
            for (c in a.continuas) e = continuo(c, local / 1_000_000f * c.velocidad, f, e)
        }
        return e
    }

    /**
     * Una pieza continua sobre el encuadre [e]. [s] son los segundos ya
     * multiplicados por su rapidez y [f] lo que va del bloque (0 a 1). Solo
     * suma a la posicion y al giro y multiplica la escala: lo mismo se puede
     * llevar en un [Efecto] sin que cambie nada.
     */
    private fun continuo(c: Continuo, s: Float, f: Float, e: Encuadre): Encuadre {
        val i = c.intensidad
        return when (c.tipo) {
            AnimacionContinua.ACERCAMIENTO -> e.copy(escala = e.escala * (1f + 0.15f * i * f))
            AnimacionContinua.ALEJAMIENTO -> e.copy(escala = e.escala * (1f + 0.15f * i * (1f - f)))
            AnimacionContinua.PULSO -> {
                val fase = fase(s, GOLPE)
                val golpe = (1f - fase).pow(3)
                e.copy(escala = e.escala * (1f + 0.05f * i * golpe))
            }
            AnimacionContinua.TEMBLOR -> {
                val amp = 0.012f * i
                e.copy(
                    x = e.x + amp * ruido(s * 9f, 1.3f),
                    y = e.y + amp * ruido(s * 9f, 7.1f),
                    rotacion = e.rotacion + 0.8f * i * ruido(s * 7f, 3.7f),
                    escala = e.escala * (1f + 0.03f * i)
                )
            }
            AnimacionContinua.BALANCEO -> e.copy(rotacion = e.rotacion + 3f * i * sin(2f * PI.toFloat() * s / 2f))
            AnimacionContinua.FLOTAR -> e.copy(
                y = e.y + 0.012f * i * sin(2f * PI.toFloat() * s / 2.4f),
                x = e.x + 0.006f * i * sin(2f * PI.toFloat() * s / 3.7f)
            )
            AnimacionContinua.GIRAR -> e.copy(rotacion = e.rotacion + 90f * i * s)
            AnimacionContinua.GLITCH -> {
                // Un golpe de 0.2 s cada 1.2 s; el resto del tiempo, quieto
                val ciclo = s / 1.2f
                val fase = ciclo - floor(ciclo)
                if (fase > 0.17f) e else {
                    val k = floor(s * 30f)
                    val semilla = floor(ciclo) * 3.1f
                    e.copy(
                        x = e.x + 0.03f * i * ruido(k, 1.9f + semilla),
                        y = e.y + 0.012f * i * ruido(k, 4.4f + semilla),
                        escala = e.escala * (1f + 0.04f * i * abs(ruido(k, 8.8f + semilla)))
                    )
                }
            }
            AnimacionContinua.VIBRAR -> {
                val amp = 0.004f * i
                e.copy(
                    x = e.x + amp * ruido(s * 40f, 2.2f),
                    y = e.y + amp * ruido(s * 40f, 6.6f),
                    escala = e.escala * (1f + 0.01f * i)
                )
            }
            AnimacionContinua.RESPIRAR -> e.copy(escala = e.escala * (1f + 0.04f * i * (0.5f - 0.5f * cos(2f * PI.toFloat() * s / 3f))))
            AnimacionContinua.PANEO -> {
                // Con un poco de zoom, para que al moverse no se vea el borde
                e.copy(x = e.x + 0.05f * i * (f * 2f - 1f), escala = e.escala * (1f + 0.1f * i))
            }
            AnimacionContinua.SHAKE_FLASH -> {
                // La sacudida dura el 60% del golpe y se apaga; el flash, en destellos()
                val fase = fase(s, GOLPE)
                val d = (1f - fase / 0.6f).coerceAtLeast(0f)
                val dx = 0.03f * i * d * ruido(s * 40f, 1.7f)
                val dy = 0.02f * i * d * ruido(s * 40f, 4.2f)
                val giro = 2.5f * i * d * ruido(s * 30f, 7.3f)
                e.copy(x = e.x + dx, y = e.y + dy, rotacion = e.rotacion + giro, escala = e.escala * cubrir(dx, dy, giro))
            }
            AnimacionContinua.ZOOM_RITMO -> {
                val fase = fase(s, GOLPE)
                e.copy(escala = e.escala * (1f + 0.15f * i * (1f - fase).pow(4)))
            }
            AnimacionContinua.CAMARA_EN_MANO -> {
                val dx = 0.01f * i * ruido(s * 1.3f, 1.1f) + 0.003f * i * ruido(s * 3.1f, 9.9f)
                val dy = 0.01f * i * ruido(s * 1.1f, 3.3f)
                val giro = 0.8f * i * ruido(s * 0.9f, 5.5f)
                // Zoom fijo, el que pide lo maximo que se puede mover: si
                // cambiara con el movimiento se notaria que respira
                e.copy(x = e.x + dx, y = e.y + dy, rotacion = e.rotacion + giro, escala = e.escala * cubrir(0.013f * i, 0.01f * i, 0.8f * i))
            }
            AnimacionContinua.LATIDO -> {
                // 0.8 s por latido: un golpe, otro mas flojo al poco, y pausa
                val fase = fase(s, 0.8f)
                val uno = exp(-fase * 18f)
                val dos = if (fase >= 0.2f) 0.7f * exp(-(fase - 0.2f) * 18f) else 0f
                e.copy(escala = e.escala * (1f + 0.06f * i * (uno + dos)))
            }
            AnimacionContinua.TWITCH -> {
                // Un tiron de 0.25 s cada segundo; el RGB, en destellos()
                val ciclo = s / 1f
                val fase = ciclo - floor(ciclo)
                if (fase >= 0.25f) e else {
                    val k = floor(s * 24f)
                    val semilla = floor(ciclo) * 3.1f
                    e.copy(
                        x = e.x + 0.03f * i * ruido(k, 2.8f + semilla),
                        y = e.y + 0.015f * i * ruido(k, 5.2f + semilla),
                        escala = e.escala * (1f + 0.1f * i * abs(ruido(k, 1.4f + semilla)))
                    )
                }
            }
            AnimacionContinua.ESTROBO, AnimacionContinua.RGB -> e
        }
    }

    /**
     * Lo que aportan las animaciones de entrada y salida en [local]: primero
     * las de antes, despues cada animacion aplicada con su propia duracion.
     */
    private fun animacion(clip: Clip, local: Long): Efecto {
        var r = Efecto()
        if (clip.entrada.isNotEmpty() && clip.duracionEntradaUs > 0) {
            val q = (local.toFloat() / clip.duracionEntradaUs).coerceIn(0f, 1f)
            if (q < 1f) for (m in clip.entrada) r = r.y(efecto(m, q))
        }
        if (clip.salida.isNotEmpty() && clip.duracionSalidaUs > 0) {
            val restante = clip.duracionUs - local
            val q = (restante.toFloat() / clip.duracionSalidaUs).coerceIn(0f, 1f)
            if (q < 1f) for (m in clip.salida) r = r.y(efecto(m, q))
        }
        for (a in clip.animaciones) {
            if (!a.activa || a.piezas.isEmpty() || a.duracionUs <= 0) continue
            // La misma cuenta que arriba: una aplicada se ve igual que la de antes
            val q = when (a.momento) {
                MomentoAnimacion.ENTRADA -> local.toFloat() / a.duracionUs
                MomentoAnimacion.SALIDA -> (clip.duracionUs - local).toFloat() / a.duracionUs
                MomentoAnimacion.SIEMPRE -> continue
            }
            r = conPiezas(r, a.piezas, q.coerceIn(0f, 1f), 1f)
        }
        return r
    }

    /** Suma a [r] las [piezas] en [q], con su intensidad por [fuerza]; nada si ya acabaron. */
    private fun conPiezas(r: Efecto, piezas: List<Movimiento>, q: Float, fuerza: Float): Efecto {
        if (q >= 1f) return r
        var e = r
        for (m in piezas) e = e.y(efecto(if (fuerza == 1f) m else m.copy(intensidad = m.intensidad * fuerza), q))
        return e
    }

    /**
     * Una pieza de animacion con [q] de 0 (fuera) a 1 (en su sitio). La salida
     * es la misma recorrida al reves: entrar desde la izquierda y salir hacia
     * la izquierda son la misma cuenta.
     */
    private fun efecto(m: Movimiento, q: Float): Efecto {
        // Un golpe (flash, shake) antes de su parte no se ve: no se queda
        // esperando en blanco como se queda fuera un bloque que va a entrar.
        if (q < m.desde && m.tipo in GOLPES) return Efecto()
        val tramo = (m.hasta - m.desde).coerceAtLeast(1e-3f)
        val local = ((q - m.desde) / tramo).coerceIn(0f, 1f)
        val f = curva(m.curva, local)
        val falta = 1f - f
        val i = m.intensidad
        return when (m.tipo) {
            Animacion.APARECER -> Efecto(opacidad = (1f - i.coerceIn(0f, 1f) * falta).coerceIn(0f, 1f))
            Animacion.ZOOM_ENTRA -> Efecto(escala = 1f + 0.35f * i * falta)
            Animacion.ZOOM_SALE -> Efecto(escala = (1f - 0.3f * i * falta).coerceAtLeast(0.02f))
            Animacion.IZQUIERDA -> Efecto(dx = -i * falta)
            Animacion.DERECHA -> Efecto(dx = i * falta)
            Animacion.ARRIBA -> Efecto(dy = -i * falta)
            Animacion.ABAJO -> Efecto(dy = i * falta)
            Animacion.GIRO -> Efecto(rotacion = -120f * i * falta)
            Animacion.REBOTE -> Efecto(escala = 1f + (rebote(local) - 1f) * i, opacidad = min(1f, local * 3f))
            Animacion.REVELAR -> Efecto(revelar = f)
            Animacion.DESENFOCAR -> Efecto(desenfoque = alto * 0.04f * i * falta)
            Animacion.SACUDIR -> {
                // Golpes rapidos que se apagan
                val amp = 0.035f * i * falta
                Efecto(dx = amp * sin(local * 38f), rotacion = 4f * i * falta * sin(local * 31f + 1f))
            }
            Animacion.PARPADEO -> {
                // Tres parpadeos y se queda
                val on = sin(local * PI.toFloat() * 7f) > -0.2f || local >= 1f
                Efecto(opacidad = if (on) 1f else (1f - i).coerceIn(0f, 1f) * 0.3f)
            }
            Animacion.SACUDIR_VERTICAL -> {
                val amp = 0.035f * i * falta
                Efecto(dy = amp * sin(local * 38f), rotacion = 2f * i * falta * sin(local * 29f + 2f))
            }
            Animacion.PENDULO -> Efecto(rotacion = 18f * i * falta * sin(local * PI.toFloat() * 5f))
            Animacion.ZOOM_VIBRA -> Efecto(escala = 1f + 0.12f * i * falta * sin(local * PI.toFloat() * 8f))
            Animacion.GLITCH -> {
                // Catorce saltos secos repartidos por la animacion, cada vez menores
                val k = floor(local * 14f)
                val amp = 0.05f * i * falta
                val corte = ruido(k, 5.3f) > 0.55f && local < 1f
                Efecto(
                    dx = amp * ruido(k, 1.7f),
                    dy = amp * 0.4f * ruido(k, 9.2f),
                    opacidad = if (corte) (1f - 0.7f * i.coerceIn(0f, 1f)) else 1f
                )
            }
            // Blanco del todo en el primer instante y se apaga con la curva
            Animacion.FLASH -> Efecto(luz = (i * falta).coerceIn(0f, 1f))
            Animacion.FLASH_NEGRO -> Efecto(luz = -(i * falta).coerceIn(0f, 1f))
            Animacion.ESTROBO -> {
                // Cuatro destellos, cada uno algo mas flojo
                val on = (local * 4f) % 1f < 0.35f && local < 1f
                Efecto(luz = if (on) (i * (1f - 0.7f * local)).coerceIn(0f, 1f) else 0f)
            }
            Animacion.RGB -> Efecto(rgb = alto * 0.02f * i * falta)
            Animacion.NEGATIVO -> {
                // Golpes de negativo en la primera mitad, a fotogramas sueltos
                val k = floor(local * 10f).toInt()
                Efecto(negativo = if (local < 0.6f && k % 2 == 0) i.coerceIn(0f, 1f) else 0f)
            }
            Animacion.CAMARA -> {
                val amp = i * falta
                val dx = 0.03f * amp * ruido(local * 22f, 1.3f)
                val dy = 0.03f * amp * ruido(local * 22f, 5.9f)
                val giro = 3f * amp * ruido(local * 18f, 8.1f)
                Efecto(dx = dx, dy = dy, rotacion = giro, escala = cubrir(dx, dy, giro))
            }
            Animacion.TWITCH -> {
                // Doce saltos secos; en algunos se queda quieto para que se note el golpe
                val k = floor(local * 12f)
                val amp = if (ruido(k, 3.3f) > -0.2f && local < 1f) i * falta else 0f
                val dx = 0.04f * amp * ruido(k, 2.2f)
                val dy = 0.02f * amp * ruido(k, 6.6f)
                Efecto(
                    dx = dx,
                    dy = dy,
                    escala = cubrir(dx, dy, 0f) + 0.12f * amp * abs(ruido(k, 1.1f)),
                    rgb = alto * 0.015f * amp * abs(ruido(k, 4.4f))
                )
            }
        }
    }

    /**
     * Lo que las continuas hacen al color: flashes, RGB. La parte de moverse
     * esta en [continua]; esto va aparte porque el encuadre no lleva color.
     */
    private fun destellos(clip: Clip, local: Long): Efecto {
        var r = Efecto()
        for (c in clip.continua) r = r.y(destello(c, local / 1_000_000f * c.velocidad))
        for (a in clip.animaciones) if (a.activa && a.momento == MomentoAnimacion.SIEMPRE) {
            for (c in a.continuas) r = r.y(destello(c, local / 1_000_000f * c.velocidad))
        }
        return r
    }

    /** La luz y el color de una pieza continua; [s] son los segundos por su rapidez. */
    private fun destello(c: Continuo, s: Float): Efecto {
        val i = c.intensidad
        return when (c.tipo) {
            AnimacionContinua.SHAKE_FLASH -> {
                val fase = fase(s, GOLPE)
                val d = (1f - fase / 0.25f).coerceAtLeast(0f)
                Efecto(luz = (0.9f * i).coerceAtMost(1f) * d * d)
            }
            AnimacionContinua.ESTROBO -> {
                val fase = fase(s, GOLPE / 2f)
                Efecto(luz = if (fase < 0.18f) (0.85f * i).coerceAtMost(1f) else 0f)
            }
            AnimacionContinua.RGB -> Efecto(rgb = alto * 0.008f * i * (0.6f + 0.4f * abs(ruido(s * 12f, 2.5f))))
            AnimacionContinua.TWITCH -> {
                val ciclo = s / 1f
                val fase = ciclo - floor(ciclo)
                if (fase >= 0.25f) Efecto() else {
                    val k = floor(s * 24f)
                    Efecto(rgb = alto * 0.012f * i * abs(ruido(k, 6.1f + floor(ciclo) * 3.1f)))
                }
            }
            else -> Efecto()
        }
    }

    /**
     * Un efecto ANIMACION: la animacion que llevaba el bloque, ahora en el
     * reloj del efecto. [fuerza] multiplica la intensidad de cada pieza.
     *
     * Recien convertida se ve igual que antes: la entrada recorre su tramo
     * como recorria su duracion, la salida lo recorre al reves y las
     * continuas cuentan los segundos desde el principio del efecto. El
     * movimiento de las continuas va como desplazamiento, giro y escala del
     * [Efecto] en vez de en el encuadre, que es la misma cuenta.
     */
    private fun efectoDeAnimacion(ef: EfectoVideo, local: Long, fuerza: Float): Efecto {
        val a = ef.animacion ?: return Efecto()
        return when (a.momento) {
            MomentoAnimacion.ENTRADA -> conPiezas(Efecto(), a.piezas, avanceAnimacion(ef, local, alReves = false), fuerza)
            MomentoAnimacion.SALIDA -> conPiezas(Efecto(), a.piezas, avanceAnimacion(ef, local, alReves = true), fuerza)
            MomentoAnimacion.SIEMPRE -> {
                val f = avanceAnimacion(ef, local, alReves = false)
                val s = ef.segundosEn(local)
                var e = Encuadre()
                var luz = Efecto()
                for (c0 in a.continuas) {
                    val c = if (fuerza == 1f) c0 else c0.copy(intensidad = c0.intensidad * fuerza)
                    e = continuo(c, s * c.velocidad, f, e)
                    luz = luz.y(destello(c, s * c.velocidad))
                }
                Efecto(dx = e.x, dy = e.y, escala = e.escala, rotacion = e.rotacion).y(luz)
            }
        }
    }

    /**
     * Por donde va la animacion de un efecto ANIMACION, de 0 a 1 (en la
     * salida, de 1 a 0). Sin puntos de progreso ni rapidez se cuenta en
     * microsegundos enteros y se divide en Float como en [animacion]: asi las
     * piezas que van a saltos (glitch, parpadeo, estrobo) caen en el mismo
     * escalon que antes de convertir y no uno antes por un redondeo.
     */
    private fun avanceAnimacion(ef: EfectoVideo, local: Long, alReves: Boolean): Float {
        val base = ef.duracionBaseUs.takeIf { it > 0 } ?: ef.duracionUs
        if (ef.progresos.isNotEmpty() || ef.rapidez != 1f || base <= 0) {
            val p = ef.progresoEn(local)
            return if (alReves) 1f - p else p
        }
        val transcurrido = local - ef.inicioUs + ef.desfaseUs
        return ((if (alReves) base - transcurrido else transcurrido).toFloat() / base).coerceIn(0f, 1f)
    }

    /**
     * Ida y vuelta del latigazo, en lienzos: en la primera mitad se va hacia
     * el angulo con la curva; en la mitad entra por el lado contrario (un
     * lienzo antes o despues, con bordes espejo es la misma imagen y no queda
     * hueco) y vuelve a su sitio con la curva al reves.
     */
    private fun latigazo(ef: EfectoVideo, local: Long): Float {
        val p = ef.progresoEn(local)
        val a = ef.ajustes
        return if (p < 0.5f) a.desde * a.avance(2f * p) else -a.desde * a.avance(2f - 2f * p)
    }

    /** Del centro de la mascara a la esquina mas lejana del lienzo, con margen. */
    private fun radioQueCubre(cx: Float, cy: Float): Float {
        if (cx == 0f && cy == 0f) return kotlin.math.sqrt(ancho.toFloat() * ancho + alto.toFloat() * alto) / 2f + 4f
        val x = ancho / 2f + abs(cx)
        val y = alto / 2f + abs(cy)
        return kotlin.math.sqrt(x * x + y * y) + 4f
    }

    /**
     * Circulo o anillo con su centro. El valor es el radio en fraccion del que
     * cubre todo el lienzo desde ese centro; el grosor, en la misma fraccion.
     */
    private fun circulo(ef: EfectoVideo, local: Long): Efecto {
        val a = ef.ajustes
        val cx = a.centroX * ancho + 0f
        val cy = a.centroY * alto + 0f
        val radio = radioQueCubre(cx, cy)
        val suave = if (a.suavidad > 0f) a.suavidad * alto else alto * 0.003f + 0.5f
        val borde = ef.valorEn(local) * radio
        return Efecto(
            mascara = Mascara(0f, 0f, suave, radial = true, invertida = a.invertir, centroX = cx, centroY = cy,
                grosor = (a.grosor * radio).takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f),
            // Radio cero: nada, sin el punto que dejaria el borde suave
            borde = if (borde <= 0f) -suave / 2f else borde
        )
    }

    /**
     * Un borde recto que avanza hacia el angulo desde fuera del lienzo hasta
     * fuera del otro lado. El valor es cuanto se ha descubierto, de 0 a 1; en
     * 0,5 el borde pasa por el centro. Con el centro movido el recorrido se
     * alarga lo que se aparta en esa direccion: asi 0 sigue tapandolo todo y
     * 1 descubriendolo todo (centrado sale lo mismo de siempre).
     */
    private fun barrido(ef: EfectoVideo, local: Long): Efecto {
        val a = ef.ajustes
        val (ux, uy) = direccion(a.angulo)
        val cx = a.centroX * ancho + 0f
        val cy = a.centroY * alto + 0f
        val suave = if (a.suavidad > 0f) a.suavidad * alto else 2f
        val largo = (abs(ux) * ancho + abs(uy) * alto) / 2f + abs(ux * cx + uy * cy) + suave
        val v = ef.valorEn(local)
        val borde = when {
            v <= 0f -> -largo - 1f
            v >= 1f -> largo + 1f
            else -> -largo + v * 2f * largo
        }
        return Efecto(
            mascara = Mascara(ux, uy, suave, invertida = a.invertir, centroX = cx, centroY = cy),
            borde = borde
        )
    }

    /**
     * El zoom o el giro de [e] alrededor del centro de los ajustes (en
     * fraccion del lienzo, desde el centro del bloque) y no del centro del
     * bloque: el bloque se desplaza lo justo para que ese punto no se mueva.
     * Sin centro devuelve [e] tal cual.
     */
    private fun sobreCentro(a: AjustesEfecto, e: Efecto): Efecto {
        if (a.centroX == 0f && a.centroY == 0f) return e
        val cx = a.centroX * ancho
        val cy = a.centroY * alto
        // Lo que gira y escala el bloque, en pixeles con la y hacia abajo (como matriz())
        val r = Math.toRadians(e.rotacion.toDouble())
        val c = cos(r).toFloat() * e.escala
        val s = sin(r).toFloat() * e.escala
        return e.copy(dx = e.dx + (cx - (c * cx - s * cy)) / ancho, dy = e.dy + (cy - (s * cx + c * cy)) / alto)
    }

    /** Efectos libres del bloque, compartidos por vista previa y exportacion. */
    internal fun efectosVideo(clip: Clip, local: Long): Efecto {
        var r = Efecto()
        for (ef in clip.efectos) {
            val i = ef.fuerzaEn(local)
            // El circulo y el barrido solo usan la intensidad para encenderse:
            // un fundido no debe destapar la imagen entera en su primer instante.
            if (i <= 0f && !(ef.tipo in SIN_FUNDIDOS && ef.activoEn(local))) continue
            val s = ef.segundosEn(local)
            val golpe = (1f - fase(s, GOLPE)).pow(4)
            val e = when (ef.tipo) {
                TipoEfectoVideo.SACUDIDA -> {
                    val dx = 0.03f * i * ruido(s * 24f, 1.3f)
                    val dy = 0.02f * i * ruido(s * 27f, 5.9f)
                    val giro = 3f * i * ruido(s * 20f, 8.1f)
                    Efecto(dx = dx, dy = dy, rotacion = giro, escala = cubrir(0.03f * i, 0.02f * i, 3f * i))
                }
                TipoEfectoVideo.SACUDIDA_VERTICAL -> {
                    val dy = 0.035f * i * sin(s * 76f)
                    Efecto(dy = dy, rotacion = i * sin(s * 59f), escala = cubrir(0f, 0.035f * i, i))
                }
                TipoEfectoVideo.ZOOM_RITMO -> Efecto(escala = 1f + 0.15f * i * golpe)
                TipoEfectoVideo.FLASH -> Efecto(luz = i * golpe)
                TipoEfectoVideo.FLASH_NEGRO -> Efecto(luz = -i * golpe)
                TipoEfectoVideo.ESTROBO -> Efecto(luz = if (fase(s, GOLPE / 2f) < 0.18f) i else 0f)
                TipoEfectoVideo.RGB -> Efecto(rgb = alto * 0.012f * i * (0.6f + 0.4f * abs(ruido(s * 12f, 2.5f))))
                TipoEfectoVideo.NEGATIVO -> Efecto(negativo = if (fase(s, GOLPE) < 0.4f) i.coerceAtMost(1f) else 0f)
                TipoEfectoVideo.DESENFOQUE -> Efecto(desenfoque = alto * 0.025f * i)
                TipoEfectoVideo.GLITCH -> {
                    val k = floor(s * 25f)
                    val dx = 0.035f * i * ruido(k, 1.7f)
                    val dy = 0.015f * i * ruido(k, 9.2f)
                    Efecto(
                        dx = dx, dy = dy, escala = cubrir(0.035f * i, 0.015f * i, 0f),
                        opacidad = if (ruido(k, 5.3f) > 0.55f) (1f - 0.6f * i).coerceAtLeast(0f) else 1f,
                        rgb = alto * 0.009f * i * abs(ruido(k, 4.4f))
                    )
                }
                TipoEfectoVideo.TWITCH -> {
                    val ciclo = s / 1f
                    if (fase(s, 1f) >= 0.25f) Efecto() else {
                        val k = floor(s * 24f)
                        val dx = 0.03f * i * ruido(k, 2.8f + floor(ciclo) * 3.1f)
                        val dy = 0.015f * i * ruido(k, 5.2f + floor(ciclo) * 3.1f)
                        Efecto(
                            dx = dx, dy = dy,
                            escala = cubrir(0.03f * i, 0.015f * i, 0f) + 0.1f * i * abs(ruido(k, 1.4f)),
                            rgb = alto * 0.012f * i * abs(ruido(k, 6.1f + floor(ciclo) * 3.1f))
                        )
                    }
                }
                TipoEfectoVideo.CIRCULO_CERRAR, TipoEfectoVideo.CIRCULO_ABRIR -> {
                    val cx = ef.ajustes.centroX * ancho + 0f
                    val cy = ef.ajustes.centroY * alto + 0f
                    val radio = radioQueCubre(cx, cy)
                    val abrir = ef.tipo == TipoEfectoVideo.CIRCULO_ABRIR
                    val progreso = ef.progresoEn(local)
                    val suave = alto * 0.003f + 0.5f
                    val borde = if (abrir) radio * 0.3f * i.coerceAtMost(1f) * (1f - progreso)
                        else radio * (1f - progreso * i.coerceAtMost(1f))
                    Efecto(
                        mascara = Mascara(0f, 0f, suave, radial = true, invertida = abrir, centroX = cx, centroY = cy),
                        // Radio cero tiene que terminar por completo, sin el
                        // punto semitransparente que dejaria el borde suave.
                        borde = if (borde <= 0f) -suave / 2f else borde
                    )
                }
                TipoEfectoVideo.BORDES_ESPEJO -> Efecto(espejoBordes = i.coerceAtMost(1f))
                TipoEfectoVideo.DESENFOQUE_HORIZONTAL -> Efecto(desenfoqueX = alto * 0.04f * i)
                TipoEfectoVideo.DESENFOQUE_VERTICAL -> Efecto(desenfoqueY = alto * 0.04f * i)
                TipoEfectoVideo.CERRAR_VERTICAL -> Efecto(escalaY = (1f - ef.progresoEn(local) * i.coerceAtMost(1f)).coerceAtLeast(0.001f))
                TipoEfectoVideo.CERRAR_HORIZONTAL -> Efecto(escalaX = (1f - ef.progresoEn(local) * i.coerceAtMost(1f)).coerceAtLeast(0.001f))
                TipoEfectoVideo.REVELAR_HORIZONTAL -> {
                    val p = (ef.progresoEn(local) * i.coerceAtMost(1f)).coerceIn(0f, 1f)
                    val cx = ef.ajustes.centroX * ancho + 0f
                    // Con el centro movido el borde recorre tambien lo que se
                    // aparta: de tapado entero a descubierto entero. Centrado, lo de siempre
                    val largo = ancho + 2f * abs(cx)
                    val borde = when {
                        p <= 0f -> -largo / 2f - 1f
                        p >= 1f -> largo / 2f + 1f
                        else -> largo * (p - 0.5f)
                    }
                    Efecto(mascara = Mascara(1f, 0f, suave = 2f, centroX = cx,
                        centroY = ef.ajustes.centroY * alto + 0f), borde = borde)
                }
                TipoEfectoVideo.MOVER -> {
                    val (ux, uy) = direccion(ef.ajustes.angulo)
                    val v = ef.valorEn(local) * i
                    Efecto(dx = v * ux, dy = v * uy)
                }
                TipoEfectoVideo.ZOOM -> sobreCentro(ef.ajustes, Efecto(escala = 1f + (ef.valorEn(local) - 1f) * i))
                TipoEfectoVideo.GIRAR -> sobreCentro(ef.ajustes, Efecto(rotacion = ef.valorEn(local) * i))
                TipoEfectoVideo.LATIGAZO -> {
                    val (ux, uy) = direccion(ef.ajustes.angulo)
                    val v = latigazo(ef, local) * i
                    Efecto(dx = v * ux, dy = v * uy)
                }
                TipoEfectoVideo.DESENFOQUE_DIRECCIONAL -> {
                    val (ux, uy) = rastro(ef.ajustes.angulo)
                    val radio = alto * 0.04f * i
                    Efecto(desenfoqueX = radio * ux, desenfoqueY = radio * uy)
                }
                TipoEfectoVideo.CIRCULO -> circulo(ef, local)
                TipoEfectoVideo.BARRIDO -> barrido(ef, local)
                // Las mitades no son un Efecto: parten el bloque en dibujos()
                TipoEfectoVideo.MITADES -> Efecto()
                TipoEfectoVideo.ANIMACION -> efectoDeAnimacion(ef, local, i)
            }
            r = r.y(e)
        }
        return r
    }

    // ------------------------------------------------------------------
    // Geometria
    // ------------------------------------------------------------------

    /**
     * Donde queda el bloque sobre el lienzo con el encuadre [e], en pixeles.
     * Es lo que usa la pantalla para saber que bloque se toca y para dibujar
     * el marco del elegido.
     */
    fun marco(clip: Clip, e: Encuadre): Marco {
        val (cw, ch) = medidor.medidas(clip, alto)
        val s = base(clip, e, cw, ch) * e.escala
        return Marco(
            cx = ancho / 2f + e.x * ancho,
            cy = alto / 2f + e.y * alto,
            ancho = cw * e.recorte.ancho * s,
            alto = ch * e.recorte.alto * s,
            rotacion = e.rotacion
        )
    }

    /** Escala base que da el ajuste, antes de la escala del usuario. */
    private fun base(clip: Clip, e: Encuadre, cw: Float, ch: Float): Float {
        if (clip.esTexto) return 1f
        val w = cw * e.recorte.ancho
        val h = ch * e.recorte.alto
        if (w <= 0f || h <= 0f) return 1f
        return if (e.ajuste == Ajuste.CUBRIR) max(ancho / w, alto / h) else min(ancho / w, alto / h)
    }

    private fun tamano(clip: Clip, e: Encuadre, cw: Float, ch: Float): Pair<Float, Float> {
        val s = base(clip, e, cw, ch) * e.escala
        return cw * s to ch * s
    }

    /** Extremos izquierdo y derecho del bloque sobre el lienzo, en pixeles. */
    private fun caja(clip: Clip, e: Encuadre, cw: Float, ch: Float): Pair<Float, Float> {
        val s = base(clip, e, cw, ch) * e.escala
        val w = cw * e.recorte.ancho * s
        val h = ch * e.recorte.alto * s
        val r = Math.toRadians(e.rotacion.toDouble())
        val mitad = (abs(cos(r)) * w + abs(sin(r)) * h).toFloat() / 2f
        val cx = ancho / 2f + e.x * ancho
        return (cx - mitad) to (cx + mitad)
    }

    /**
     * Del pixel del lienzo a la coordenada de textura del bloque.
     *
     * Es la inversa de colocar el bloque: en vez de mover la imagen al
     * lienzo, cada pixel del lienzo pregunta de que parte de la imagen viene.
     * Asi el sombreador puede pedir varias posiciones por pixel, una por
     * muestra del obturador, y promediarlas. Devuelve una matriz 3x3 por
     * columnas, lista para OpenGL.
     */
    fun matriz(clip: Clip, e: Encuadre, cw: Float, ch: Float, escalaX: Float = 1f, escalaY: Float = 1f): FloatArray {
        val s = base(clip, e, cw, ch) * e.escala
        val w = cw * e.recorte.ancho * s * escalaX
        val h = ch * e.recorte.alto * s * escalaY
        // Un bloque encogido a nada (el arranque de un rebote, por ejemplo)
        // dividiria entre cero. Se manda todo fuera de la textura: no se ve.
        if (w < 1e-3f || h < 1e-3f) return FUERA.copyOf()
        val cx = ancho / 2f + e.x * ancho
        val cy = alto / 2f + e.y * alto
        val r = Math.toRadians(e.rotacion.toDouble())
        val c = cos(r).toFloat()
        val sn = sin(r).toFloat()
        // p -> q = p - centro -> girar al reves -> dividir por el tamano -> +0.5
        // Girar al reves en coordenadas con la y hacia abajo: [c s; -s c]
        var a = c / w; var b = sn / w
        var d = -sn / h; var f = c / h
        var tx = -(a * cx + b * cy) + 0.5f
        var ty = -(d * cx + f * cy) + 0.5f
        if (e.espejo) {
            a = -a; b = -b; tx = 1f - tx
        }
        // Del bloque recortado a la textura entera
        val rc = e.recorte
        a *= rc.ancho; b *= rc.ancho; tx = tx * rc.ancho + rc.izquierda
        d *= rc.alto; f *= rc.alto; ty = ty * rc.alto + rc.arriba
        return floatArrayOf(a, d, 0f, b, f, 0f, tx, ty, 1f)
    }

    companion object {
        const val MAX_MUESTRAS = 8

        /** Medio segundo por golpe: 120 por minuto, lo de casi toda la musica de edits. */
        const val GOLPE = 0.5f

        /** Efectos que no se funden: la intensidad solo los enciende o los apaga. */
        private val SIN_FUNDIDOS = setOf(TipoEfectoVideo.CIRCULO, TipoEfectoVideo.BARRIDO)

        /**
         * Hacia donde apunta [grados] (0 derecha, 90 abajo), en vector unitario.
         * Exacto en los multiplos de 90: un movimiento a la izquierda no se
         * desvia una millonesima hacia arriba.
         */
        fun direccion(grados: Float): Pair<Float, Float> {
            if (!grados.isFinite()) return 1f to 0f
            val g = ((grados % 360f) + 360f) % 360f
            return when (g) {
                0f, 360f -> 1f to 0f
                90f -> 0f to 1f
                180f -> -1f to 0f
                270f -> 0f to -1f
                else -> {
                    val r = Math.toRadians(g.toDouble())
                    cos(r).toFloat() to sin(r).toFloat()
                }
            }
        }

        /**
         * La direccion de un rastro: una linea, asi que [grados] y [grados] + 180
         * son el mismo. Siempre hacia la derecha (o hacia abajo si es vertical),
         * para que dos rastros iguales sumen en vez de anularse.
         */
        fun rastro(grados: Float): Pair<Float, Float> {
            val (x, y) = direccion(grados)
            return if (x < -1e-6f || (abs(x) <= 1e-6f && y < 0f)) (-x + 0f) to (-y + 0f) else x to y
        }

        private val GOLPES = setOf(
            Animacion.FLASH, Animacion.FLASH_NEGRO, Animacion.ESTROBO, Animacion.NEGATIVO,
            Animacion.RGB, Animacion.CAMARA, Animacion.TWITCH
        )

        /**
         * Zoom que hace falta para que un bloque que llena el lienzo no
         * ensenie el borde al moverse [dx], [dy] (fracciones del lienzo) y
         * girar [giro] grados. 1.8 por grado cubre hasta el 9:16.
         */
        fun cubrir(dx: Float, dy: Float, giro: Float): Float =
            1f + 2f * max(abs(dx), abs(dy)) + 1.8f * abs(giro) * PI.toFloat() / 180f

        /**
         * Borde de mascara que no recorta nada. Lejos de cualquier lienzo pero
         * dentro de lo que cabe en un float de media precision (65504).
         */
        const val SIN_BORDE = 30_000f

        /** Matriz que lleva cualquier pixel fuera de la imagen. */
        private val FUERA = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, -1f, -1f, 1f)

        fun suave(x: Float): Float = x * x * (3f - 2f * x)

        /**
         * Por donde va el golpe de [periodo] segundos en [s], de 0 a 1. Con un
         * pelin de margen: el fotograma que cae justo en el golpe, con el
         * redondeo, daria 0.9999 (el final del anterior) en vez de 0.
         */
        fun fase(s: Float, periodo: Float): Float {
            val x = s / periodo + 1e-4f
            return x - floor(x)
        }

        fun salidaSuave(x: Float): Float = 1f - (1f - x).pow(3)

        fun paso(a: Float, b: Float, x: Float): Float = suave(((x - a) / (b - a)).coerceIn(0f, 1f))

        fun curva(c: Curva, x: Float): Float = when (c) {
            Curva.LINEAL -> x
            Curva.SUAVE -> if (x < 0.5f) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
            Curva.ENTRADA -> x * x * x
            Curva.SALIDA -> 1 - (1 - x).pow(3)
            Curva.REBOTE -> rebote(x)
            Curva.ELASTICO -> if (x <= 0f) 0f else if (x >= 1f) 1f
            else (2.0.pow(-10.0 * x) * sin((x * 10 - 0.75) * (2 * PI / 3)) + 1).toFloat()
            // Exponencial: casi todo el camino en el primer cuarto
            Curva.RAPIDA -> if (x >= 1f) 1f else (1.0 - 2.0.pow(-10.0 * x)).toFloat()
        }

        /** Sale de pequeno, se pasa un poco y vuelve: el rebote. */
        fun rebote(q: Float): Float {
            val c1 = 1.70158f
            val c3 = c1 + 1f
            val x = q - 1f
            return (1f + c3 * x * x * x + c1 * x * x).coerceAtLeast(0f)
        }

        /** Ruido suave y repetible: el mismo instante da siempre el mismo temblor. */
        fun ruido(x: Float, semilla: Float): Float {
            val i = floor(x)
            val f = x - i
            fun h(n: Float): Float {
                val v = sin(n * 127.1f + semilla * 311.7f) * 43758.547f
                return (v - floor(v)) * 2f - 1f
            }
            return h(i) + (h(i + 1f) - h(i)) * suave(f)
        }
    }
}

/** Mide el contenido de un bloque: el archivo, o el texto ya maquetado. */
/** Rectangulo girado de un bloque sobre el lienzo, en pixeles. */
data class Marco(val cx: Float, val cy: Float, val ancho: Float, val alto: Float, val rotacion: Float) {
    fun contiene(x: Float, y: Float, margen: Float = 0f): Boolean {
        val r = Math.toRadians(-rotacion.toDouble())
        val dx = x - cx
        val dy = y - cy
        val lx = (dx * cos(r) - dy * sin(r)).toFloat()
        val ly = (dx * sin(r) + dy * cos(r)).toFloat()
        return abs(lx) <= ancho / 2f + margen && abs(ly) <= alto / 2f + margen
    }
}

fun interface Medidor {
    /** Ancho y alto del contenido en pixeles, para un lienzo de [altoLienzo]. */
    fun medidas(clip: Clip, altoLienzo: Int): Pair<Float, Float>
}

/**
 * Lo que una animacion o transicion le hace a un bloque en un instante.
 * Se aplica encima del encuadre que ya traiga.
 */
data class Efecto(
    val dx: Float = 0f,
    val dy: Float = 0f,
    val escala: Float = 1f,
    val rotacion: Float = 0f,
    val opacidad: Float = 1f,
    val desenfoque: Float = 0f,
    val mascara: Mascara? = null,
    val borde: Float = 0f,
    /** -1 sin revelar; 0..1 cuanto se ha descubierto. */
    val revelar: Float = -1f,
    /** Flash: hacia blanco si es positivo, hacia negro si es negativo (-1 a 1). */
    val luz: Float = 0f,
    /** Rojo y azul separados, en pixeles del lienzo. */
    val rgb: Float = 0f,
    /** 0 normal, 1 colores invertidos. */
    val negativo: Float = 0f,
    val escalaX: Float = 1f,
    val escalaY: Float = 1f,
    val espejoBordes: Float = 0f,
    val desenfoqueX: Float = 0f,
    val desenfoqueY: Float = 0f
) {
    fun aplicar(e: Encuadre): Encuadre = e.copy(
        x = e.x + dx,
        y = e.y + dy,
        escala = e.escala * escala,
        rotacion = e.rotacion + rotacion
    )

    /** Dos efectos a la vez (entrada y salida en un bloque muy corto). */
    fun y(o: Efecto): Efecto = Efecto(
        dx + o.dx, dy + o.dy, escala * o.escala, rotacion + o.rotacion,
        opacidad * o.opacidad, desenfoque + o.desenfoque, mascara ?: o.mascara,
        if (mascara != null) borde else o.borde,
        if (revelar >= 0f) revelar else o.revelar,
        luz + o.luz, rgb + o.rgb, max(negativo, o.negativo),
        escalaX * o.escalaX, escalaY * o.escalaY, max(espejoBordes, o.espejoBordes),
        desenfoqueX + o.desenfoqueX, desenfoqueY + o.desenfoqueY
    )
}

/**
 * Un corte recto sobre el lienzo: se ve lo que el borde ya ha barrido.
 *
 * Con d la distancia de un pixel al centro del lienzo medida en la direccion
 * del barrido (o sin direccion, si es [radial]), el pixel se ve si d < borde. El borde avanza en esa direccion.
 *
 * @param dirX,dirY hacia donde avanza el borde (vector unitario)
 * @param suave pixeles de transicion del borde, para que no salga dentado
 */
data class Mascara(
    val dirX: Float,
    val dirY: Float,
    val suave: Float,
    /** En circulo: d es la distancia al centro del lienzo y se ve lo de dentro. */
    val radial: Boolean = false,
    val invertida: Boolean = false,
    /** Centro de la mascara respecto al centro del lienzo, en pixeles. */
    val centroX: Float = 0f,
    val centroY: Float = 0f,
    /** Mayor que 0: solo se ve la banda de este ancho (pixeles) por dentro del borde, un anillo. */
    val grosor: Float = 0f
)

/** Un fotograma descrito: que dibujar, en orden de abajo arriba. */
data class Cuadro(val numero: Long, val tiempoUs: Long, val pasos: List<Paso>) {
    /** Bloques que tienen que tener su fotograma listo para dibujar este. */
    val clips: List<PasoClip> get() = pasos.flatMap { clipsDe(it) }

    private fun clipsDe(paso: Paso): List<PasoClip> = when (paso) {
        is PasoClip -> listOf(paso)
        is PasoPartes -> if (paso.visible) paso.partes else emptyList()
        is PasoGrupo -> if (paso.visible) paso.pasos.flatMap { clipsDe(it) } else emptyList()
        else -> emptyList()
    }
}

sealed class Paso {
    /** Quita muestras repetidas: si nada se mueve, una sola posicion basta. */
    open fun simplificado(): Paso = this
}

/** Un color liso que cubre todo el lienzo. */
data class PasoSolido(
    val argb: Int,
    val opacidades: FloatArray,
    /** Solo informativo: los solidos de fondo van debajo de todo. */
    val debajo: Boolean = true
) : Paso() {
    val opacidadMedia: Float get() = opacidades.average().toFloat()
}

/** La linea brillante de la transicion de barrido. */
data class PasoLinea(
    val dirX: Float,
    val dirY: Float,
    /** Posicion del borde en cada muestra, en pixeles desde el centro. */
    val bordes: FloatArray,
    val grosor: Float,
    val color: Int
) : Paso()

data class PasoClip(
    val clip: Clip,
    /** Instante del archivo en el centro del fotograma. */
    val tiempoArchivoUs: Long,
    /**
     * Cuanto tiempo del archivo cubre la exposicion. Con el clip en modo
     * fluido, el motor genera fotogramas repartidos por este tramo y los
     * promedia: desenfoque de movimiento del contenido, no solo del encuadre.
     */
    val exposicionUs: Long,
    /** Una matriz (3x3 por columnas) por muestra: del lienzo a la textura. */
    val matrices: List<FloatArray>,
    val opacidades: FloatArray,
    val mascara: Mascara?,
    /** Posicion del borde de la mascara en cada muestra. */
    val bordes: FloatArray,
    val color: AjustesColor,
    val mezcla: Mezcla,
    /** Radio de desenfoque en pixeles del lienzo. */
    val desenfoque: Float,
    /** El relleno desenfocado de detras. */
    val esFondo: Boolean,
    /** Lo mas grande que llega a verse, para no decodificar a mas resolucion de la que luce. */
    val anchoEnLienzo: Float,
    val altoEnLienzo: Float,
    /** Para maquetar el texto al tamano al que se va a ver, no estirarlo. */
    val escalaTexto: Float,
    /** Flash: hacia blanco (positivo) o negro (negativo), de -1 a 1. */
    val luz: Float = 0f,
    /** Rojo y azul separados, en pixeles del lienzo. */
    val rgb: Float = 0f,
    /** Colores invertidos, de 0 a 1. */
    val negativo: Float = 0f,
    val espejoBordes: Float = 0f,
    val desenfoqueX: Float = 0f,
    val desenfoqueY: Float = 0f,
    /** Mascara en UV de la fuente completa, sin cambiar su geometria. */
    val recorteFuente: RegionUv? = null
) : Paso() {

    val muestras: Int get() = matrices.size

    /** Una sola exposicion, para componer las partes antes de promediarlas. */
    fun muestra(indice: Int): PasoClip {
        val i = indice.coerceIn(0, matrices.lastIndex)
        return copy(matrices = listOf(matrices[i]), opacidades = floatArrayOf(opacidades[i]), bordes = floatArrayOf(bordes[i]))
    }

    override fun simplificado(): Paso =
        if (quieto(matrices, mascara, bordes)) copy(
            matrices = listOf(matrices[0]),
            opacidades = floatArrayOf(opacidades.average().toFloat()),
            bordes = floatArrayOf(bordes[0])
        ) else this

    /** Si no se ve, no hace falta ni decodificarlo. */
    val visible: Boolean get() = opacidades.any { it > 0.002f }
}

/** Primero compone cada instante de las partes; despues promedia el conjunto. */
data class PasoPartes(val partes: List<PasoClip>, val mezcla: Mezcla = Mezcla.NORMAL) : Paso() {
    val muestras: Int get() = partes.maxOfOrNull { it.muestras } ?: 1
    val visible: Boolean get() = partes.any { it.visible }

    override fun simplificado(): Paso {
        // La opacidad tambien debe conservar su correlacion temporal entre
        // piezas: promediarla antes del OVER cambiaria el resultado.
        val quietas = partes.all { p ->
            p.matrices.all { it.contentEquals(p.matrices.first()) } &&
                p.opacidades.all { it == p.opacidades.first() } && p.bordes.all { it == p.bordes.first() }
        }
        return if (quietas) copy(partes = partes.map { it.muestra(0) }) else this
    }
}

/**
 * Varios pasos montados aparte, en una lamina del tamano del lienzo, y puestos
 * encima como una sola imagen: una escena entera que se mueve, se descubre o
 * se funde de una pieza.
 */
data class PasoGrupo(
    val pasos: List<Paso>,
    /** Una por muestra: del lienzo a la lamina del grupo. */
    val matrices: List<FloatArray>,
    val opacidades: FloatArray,
    val mascara: Mascara?,
    val bordes: FloatArray,
    /** Fuera de la lamina se repiten sus bordes en vez de quedar vacio. */
    val extender: Boolean
) : Paso() {

    override fun simplificado(): Paso {
        val dentro = pasos.map { it.simplificado() }
        return if (quieto(matrices, mascara, bordes)) copy(
            pasos = dentro,
            matrices = listOf(matrices[0]),
            opacidades = floatArrayOf(opacidades.average().toFloat()),
            bordes = floatArrayOf(bordes[0])
        ) else copy(pasos = dentro)
    }

    /** Ni se mueve, ni se transparenta, ni se recorta: se puede dibujar sin montarla aparte. */
    val directo: Boolean
        get() = matrices.size == 1 && mascara == null && opacidades[0] >= 0.9999f &&
            esIdentidad(matrices[0])

    val visible: Boolean get() = opacidades.any { it > 0.002f }

    private fun esIdentidad(m: FloatArray): Boolean =
        abs(m[6]) < 1e-6f && abs(m[7]) < 1e-6f && abs(m[1]) < 1e-9f && abs(m[3]) < 1e-9f
}

/**
 * ¿Lo que se dibuja es igual en todas las muestras? Si solo cambia la
 * opacidad, si: mezclar la misma imagen con varias opacidades es lo mismo
 * que dibujarla una vez con la media.
 */
private fun quieto(matrices: List<FloatArray>, mascara: Mascara?, bordes: FloatArray): Boolean {
    if (matrices.size <= 1) return true
    val m0 = matrices[0]
    for (i in 1 until matrices.size) {
        val m = matrices[i]
        for (j in m.indices) if (abs(m[j] - m0[j]) > 1e-6f) return false
        if (mascara != null && abs(bordes[i] - bordes[0]) > 0.01f) return false
    }
    return true
}
