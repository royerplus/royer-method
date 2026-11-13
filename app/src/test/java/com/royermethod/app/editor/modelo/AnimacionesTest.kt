package com.royermethod.app.editor.modelo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cuadros descritos como numeros, para comparar dos proyectos que tienen que
 * verse igual: la forma de los pasos (lo que no es un numero) y todos los
 * numeros en orden.
 */
internal object Cuadros {
    val video = Origen("content://v", "v", TipoOrigen.VIDEO, 1920, 1080, duracionUs = 8_000_000, fps = 30f)
    val foto = Origen("content://i", "i", TipoOrigen.IMAGEN, 1000, 1000)
    private val medidor = Medidor { c, h ->
        if (c.texto != null) (h * 0.4f) to (h * 0.08f) else c.origen!!.anchoVisible.toFloat() to c.origen.altoVisible.toFloat()
    }

    fun escena(p: Proyecto) = Escena(p, 576, 1024, medidor)
    fun proyecto(c: Clip, fondo: Fondo = Fondo.NEGRO) = Proyecto(principal = listOf(c), fondo = fondo)

    class Volcado(val forma: String, val numeros: FloatArray)

    fun volcado(c: Cuadro): Volcado {
        val forma = StringBuilder()
        val n = ArrayList<Float>()
        c.pasos.forEach { paso(it, forma, n) }
        return Volcado(forma.toString(), n.toFloatArray())
    }

    private fun mascara(m: Mascara?, forma: StringBuilder, n: MutableList<Float>) {
        forma.append(if (m == null) "-" else "m${m.radial}${m.invertida}")
        if (m != null) n.addAll(listOf(m.dirX, m.dirY, m.suave, m.centroX, m.centroY, m.grosor))
    }

    private fun paso(p: Paso, forma: StringBuilder, n: MutableList<Float>) {
        when (p) {
            is PasoSolido -> { forma.append("S${p.argb},${p.debajo},${p.opacidades.size};"); n.addAll(p.opacidades.toList()) }
            is PasoLinea -> {
                forma.append("L${p.color},${p.bordes.size};")
                n.addAll(listOf(p.dirX, p.dirY, p.grosor)); n.addAll(p.bordes.toList())
            }
            is PasoGrupo -> {
                forma.append("G${p.matrices.size},${p.extender}(")
                p.pasos.forEach { paso(it, forma, n) }
                forma.append(")")
                p.matrices.forEach { n.addAll(it.toList()) }
                n.addAll(p.opacidades.toList()); n.addAll(p.bordes.toList())
                mascara(p.mascara, forma, n)
            }
            is PasoPartes -> {
                forma.append("P${p.mezcla}(")
                p.partes.forEach { paso(it, forma, n) }
                forma.append(")")
            }
            is PasoClip -> {
                forma.append("C${p.clip.id},${p.tiempoArchivoUs},${p.exposicionUs},${p.matrices.size},${p.color},${p.mezcla},${p.esFondo},${p.recorteFuente};")
                p.matrices.forEach { n.addAll(it.toList()) }
                n.addAll(p.opacidades.toList()); n.addAll(p.bordes.toList())
                mascara(p.mascara, forma, n)
                n.addAll(listOf(p.desenfoque, p.anchoEnLienzo, p.altoEnLienzo, p.escalaTexto, p.luz, p.rgb, p.negativo,
                    p.espejoBordes, p.desenfoqueX, p.desenfoqueY))
            }
        }
    }

    /** Todos los fotogramas de [a] y [b] iguales bit a bit. */
    fun iguales(a: Proyecto, b: Proyecto, que: String, fotogramas: LongRange = 0 until Linea.fotogramas(a)) {
        val ea = escena(a)
        val eb = escena(b)
        for (n in fotogramas) {
            val va = volcado(ea.cuadro(n))
            val vb = volcado(eb.cuadro(n))
            assertEquals("$que, forma en $n", va.forma, vb.forma)
            assertTrue("$que, numeros en $n", va.numeros.contentEquals(vb.numeros))
        }
    }

    /**
     * Igual con tolerancia: 1e-4, relativa en lo que va en pixeles (bordes,
     * desenfoques, tamanos), que son numeros grandes.
     */
    fun parecidos(ea: Escena, na: Long, eb: Escena, nb: Long, que: String) {
        val va = volcado(ea.cuadro(na))
        val vb = volcado(eb.cuadro(nb))
        assertEquals("$que, forma en $na/$nb", va.forma, vb.forma)
        assertEquals("$que, cuantos numeros en $na/$nb", va.numeros.size, vb.numeros.size)
        for (i in va.numeros.indices) {
            val x = va.numeros[i]
            val y = vb.numeros[i]
            val tol = 1e-4f * maxOf(1f, kotlin.math.abs(x), kotlin.math.abs(y))
            assertTrue("$que, numero $i en $na/$nb: $x frente a $y", x == y || kotlin.math.abs(x - y) <= tol)
        }
    }
}

class AnimacionesTest {

    private val combs = Combinaciones.entrada
    private val conts = Combinaciones.continuas
    private fun foto(dur: Long = 2_000_000) = Clip(id = 1, origen = Cuadros.foto, hastaUs = dur)
    private fun video(dur: Long = 2_000_000) = Clip(id = 1, origen = Cuadros.video, hastaUs = dur)

    private fun igualesConLinea(a: Clip, b: Clip, que: String, fondo: Fondo = Fondo.NEGRO) {
        val pa = Cuadros.proyecto(a, fondo)
        val pb = Cuadros.proyecto(b, fondo)
        Cuadros.iguales(pa, pb, que)
        // El marco del lienzo y el reparto de bits tampoco cambian
        val ea = Cuadros.escena(pa)
        val eb = Cuadros.escena(pb)
        for (l in 0 until a.duracionUs step 25_000) assertEquals("$que, encuadre en $l", ea.encuadreEn(a, l), eb.encuadreEn(b, l))
        assertEquals("$que, bitrate", Linea.tramos(pa, normalizar = false), Linea.tramos(pb, normalizar = false))
    }

    // ------------------------------------------------------------------
    // Evaluacion
    // ------------------------------------------------------------------

    @Test
    fun unaEntradaHeredadaSeVeIgualQueLaMismaAplicada() {
        for (t in Animacion.entries) {
            val piezas = listOf(Movimiento(t, 1.4f, Curva.SUAVE, 0.1f, 0.9f))
            igualesConLinea(
                video().copy(entrada = piezas, duracionEntradaUs = 433_333),
                video().copy(animaciones = listOf(AnimacionAplicada(1, "x", MomentoAnimacion.ENTRADA, piezas, duracionUs = 433_333))),
                "entrada $t"
            )
            igualesConLinea(
                video().copy(salida = piezas, duracionSalidaUs = 600_000),
                video().copy(animaciones = listOf(AnimacionAplicada(1, "x", MomentoAnimacion.SALIDA, piezas, duracionUs = 600_000))),
                "salida $t"
            )
        }
        for (c in combs) {
            igualesConLinea(
                foto().copy(entrada = c.piezas, duracionEntradaUs = 500_000),
                foto().copy(animaciones = listOf(AnimacionAplicada(1, c.nombre, MomentoAnimacion.ENTRADA, c.piezas, duracionUs = 500_000))),
                "entrada ${c.nombre}", Fondo.DESENFOCADO
            )
        }
        for (t in AnimacionContinua.entries) {
            val piezas = listOf(Continuo(t, 1.3f, 1.7f))
            igualesConLinea(
                foto().copy(continua = piezas),
                foto().copy(animaciones = listOf(AnimacionAplicada(1, "x", MomentoAnimacion.SIEMPRE, continuas = piezas))),
                "siempre $t"
            )
        }
    }

    @Test
    fun unificarNoCambiaNiUnBit() {
        // Todas las combinaciones del catalogo, con entrada, salida y siempre a la vez
        for ((k, c) in combs.withIndex()) {
            val clip = video().copy(
                entrada = c.piezas, duracionEntradaUs = 500_000,
                salida = combs[(k + 5) % combs.size].piezas, duracionSalidaUs = 700_000,
                continua = conts[k % conts.size].second
            )
            val u = clip.conAnimacionesUnificadas()
            assertTrue(u.entrada.isEmpty() && u.salida.isEmpty() && u.continua.isEmpty())
            assertEquals(listOf(MomentoAnimacion.ENTRADA, MomentoAnimacion.SALIDA, MomentoAnimacion.SIEMPRE), u.animaciones.map { it.momento })
            igualesConLinea(clip, u, "combinacion ${c.nombre}", if (k % 2 == 0) Fondo.NEGRO else Fondo.DESENFOCADO)
        }
        for (c in conts) {
            val clip = foto().copy(continua = c.second, entrada = combs[3].piezas, duracionEntradaUs = 300_000)
            igualesConLinea(clip, clip.conAnimacionesUnificadas(), "continuas ${c.first}")
        }
        // Con aplicadas que ya estaban: las heredadas van delante, que es como se evaluaban
        val ya = listOf(
            AnimacionAplicada(4, "zoom", MomentoAnimacion.ENTRADA, combs[13].piezas, duracionUs = 900_000),
            AnimacionAplicada(9, "flash", MomentoAnimacion.SALIDA, combs[33].piezas, duracionUs = 300_000),
            AnimacionAplicada(2, "temblor", MomentoAnimacion.SIEMPRE, continuas = conts[18].second)
        )
        val mezcla = video().copy(entrada = combs[7].piezas, salida = combs[24].piezas, continua = conts[6].second, animaciones = ya)
        igualesConLinea(mezcla, mezcla.conAnimacionesUnificadas(), "con aplicadas")
        // Un bloque muy corto, con entrada y salida pisandose, y una transicion y capas
        val corto = foto(500_000).copy(entrada = combs[20].piezas, duracionEntradaUs = 400_000, salida = combs[40].piezas, duracionSalidaUs = 450_000)
        igualesConLinea(corto, corto.conAnimacionesUnificadas(), "corto")
        val a = video().copy(entrada = combs[2].piezas, salida = combs[9].piezas, continua = conts[1].second, transicion = Transicion.LINEA)
        val b = foto().copy(id = 2, entrada = combs[30].piezas, continua = conts[11].second)
        val capa = Capa(300_000, 0, foto().copy(id = 3, entrada = combs[44].piezas, salida = combs[50].piezas, continua = conts[22].second), Mezcla.SUMAR)
        val p = Proyecto(principal = listOf(a, b), capas = listOf(capa))
        val q = p.copy(principal = p.principal.map { it.conAnimacionesUnificadas() }, capas = p.capas.map { it.copy(clip = it.clip.conAnimacionesUnificadas()) })
        Cuadros.iguales(p, q, "proyecto con transicion")
        assertEquals(Linea.tramos(p), Linea.tramos(q))
    }

    @Test
    fun unificarDaNombresEIdsDeterministas() {
        val zoom = combs.first { it.nombre == "Zoom rapido" }
        val latido = conts.first { it.first == "Latido" }
        val propia = AnimacionAplicada(5, "Mia", MomentoAnimacion.ENTRADA, listOf(Movimiento(Animacion.GIRO)), duracionUs = 200_000)
        val c = video().copy(
            entrada = zoom.piezas, duracionEntradaUs = 350_000,
            salida = listOf(Movimiento(Animacion.APARECER, 0.3f)), duracionSalidaUs = 250_000,
            continua = latido.second, animaciones = listOf(propia)
        )
        val u = c.conAnimacionesUnificadas()
        assertEquals(listOf("Zoom rapido", "Salida", "Latido", "Mia"), u.animaciones.map { it.nombre })
        assertEquals(listOf(6L, 7L, 8L, 5L), u.animaciones.map { it.id })
        assertEquals(listOf(350_000L, 250_000L), u.animaciones.take(2).map { it.duracionUs })
        // Dos veces es lo mismo que una, y sin nada heredado no cambia nada
        assertEquals(u, u.conAnimacionesUnificadas())
        assertTrue(u.conAnimacionesUnificadas() === u)
        assertEquals(9L, c.siguienteIdAnimacion())
        assertEquals(9L, u.siguienteIdAnimacion())
        assertEquals(1L, video().siguienteIdAnimacion())
        // Ids que faltan o se repiten (un archivo raro) reciben uno nuevo
        val rara = video().copy(animaciones = listOf(propia.copy(id = 0), propia, propia.copy(nombre = "otra")))
        assertEquals(listOf(6L, 5L, 7L), rara.conAnimacionesUnificadas().animaciones.map { it.id })
    }

    @Test
    fun laQueEstaApagadaNoHaceNada() {
        val piezas = combs.first { it.nombre == "Shake con flash" }.piezas
        val apagadas = listOf(
            AnimacionAplicada(1, "a", MomentoAnimacion.ENTRADA, piezas, duracionUs = 600_000, activa = false),
            AnimacionAplicada(2, "b", MomentoAnimacion.SALIDA, piezas, duracionUs = 600_000, activa = false),
            AnimacionAplicada(3, "c", MomentoAnimacion.SIEMPRE, continuas = conts[0].second, activa = false)
        )
        igualesConLinea(video(), video().copy(animaciones = apagadas), "apagadas")
        // Ni las que no tienen nada que hacer
        val vacias = listOf(
            AnimacionAplicada(1, "a", MomentoAnimacion.ENTRADA, duracionUs = 600_000),
            AnimacionAplicada(2, "b", MomentoAnimacion.SALIDA, piezas, duracionUs = 0),
            AnimacionAplicada(3, "c", MomentoAnimacion.SIEMPRE, piezas = piezas)
        )
        igualesConLinea(video(), video().copy(animaciones = vacias), "vacias")
    }

    @Test
    fun cadaAplicadaTieneSuDuracion() {
        val zoom = AnimacionAplicada(1, "zoom", MomentoAnimacion.ENTRADA, listOf(Movimiento(Animacion.ZOOM_ENTRA)), duracionUs = 300_000)
        val izquierda = AnimacionAplicada(2, "izq", MomentoAnimacion.ENTRADA, listOf(Movimiento(Animacion.IZQUIERDA)), duracionUs = 900_000)
        val juntas = Cuadros.escena(Cuadros.proyecto(foto(3_000_000).copy(animaciones = listOf(zoom, izquierda))))
        val sola = Cuadros.escena(Cuadros.proyecto(foto(3_000_000).copy(animaciones = listOf(izquierda))))
        // A los 0.5 s el zoom ya acabo y solo queda el desplazamiento, que sigue
        val v = Cuadros.volcado(juntas.cuadro(15))
        assertTrue(v.numeros.contentEquals(Cuadros.volcado(sola.cuadro(15)).numeros))
        assertFalse(Cuadros.volcado(juntas.cuadro(3)).numeros.contentEquals(Cuadros.volcado(sola.cuadro(3)).numeros))
        val quieta = Cuadros.escena(Cuadros.proyecto(foto(3_000_000)))
        assertFalse(v.numeros.contentEquals(Cuadros.volcado(quieta.cuadro(15)).numeros))
        assertTrue(Cuadros.volcado(juntas.cuadro(30)).numeros.contentEquals(Cuadros.volcado(quieta.cuadro(30)).numeros))
        // Dos salidas: la corta solo actua al final
        val flash = AnimacionAplicada(3, "flash", MomentoAnimacion.SALIDA, listOf(Movimiento(Animacion.FLASH)), duracionUs = 200_000)
        val baja = AnimacionAplicada(4, "baja", MomentoAnimacion.SALIDA, listOf(Movimiento(Animacion.ABAJO)), duracionUs = 1_000_000)
        val e = Cuadros.escena(Cuadros.proyecto(foto(3_000_000).copy(animaciones = listOf(flash, baja))))
        fun delante(n: Long) = e.cuadro(n).pasos.filterIsInstance<PasoClip>().single()
        assertEquals(0f, delante(70).luz, 0f)
        assertTrue(delante(89).luz > 0.5f)
        assertFalse(delante(70).matrices[0].contentEquals(Cuadros.escena(Cuadros.proyecto(foto(3_000_000))).cuadro(70).clips.single().matrices[0]))
    }

    // ------------------------------------------------------------------
    // Operaciones
    // ------------------------------------------------------------------

    private fun aplicada(nombre: String, momento: MomentoAnimacion, dur: Long, vararg t: Animacion) =
        AnimacionAplicada(0, nombre, momento, t.map { Movimiento(it) }, duracionUs = dur)

    @Test
    fun seAcumulanSeQuitanYSeCambianPorSeparado() {
        val base = video().copy(entrada = listOf(Movimiento(Animacion.APARECER)), duracionEntradaUs = 300_000)
        var c = base.conAnimacion(aplicada("Flash", MomentoAnimacion.ENTRADA, 300_000, Animacion.FLASH))
        c = c.conAnimacion(aplicada("Camara", MomentoAnimacion.ENTRADA, 400_000, Animacion.CAMARA))
        c = c.conAnimacion(AnimacionAplicada(0, "Latido", MomentoAnimacion.SIEMPRE, continuas = listOf(Continuo(AnimacionContinua.LATIDO))))
        c = c.conAnimacion(aplicada("Abajo", MomentoAnimacion.SALIDA, 500_000, Animacion.ABAJO))
        // La heredada se unifica primero y conserva su sitio; las nuevas van al final
        assertEquals(listOf("Aparecer", "Flash", "Camara", "Latido", "Abajo"), c.animaciones.map { it.nombre })
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), c.animaciones.map { it.id })
        assertTrue(c.entrada.isEmpty())
        // Un id libre se respeta; uno ocupado se cambia por el siguiente
        assertEquals(40L, c.conAnimacion(aplicada("x", MomentoAnimacion.ENTRADA, 1, Animacion.GIRO).copy(id = 40)).animaciones.last().id)
        assertEquals(6L, c.conAnimacion(aplicada("x", MomentoAnimacion.ENTRADA, 1, Animacion.GIRO).copy(id = 2)).animaciones.last().id)

        // Quitar una deja las demas exactamente igual
        val sin = c.sinAnimacion(3)
        assertEquals(c.animaciones.filter { it.id != 3L }, sin.animaciones)
        assertTrue(c.sinAnimacion(99) === c)
        // Cambiar la duracion de una no toca las otras
        val cambiada = c.cambiarAnimacion(2) { it.copy(duracionUs = 700_000) }
        assertEquals(700_000L, cambiada.animaciones.first { it.id == 2L }.duracionUs)
        assertEquals(c.animaciones.filter { it.id != 2L }, cambiada.animaciones.filter { it.id != 2L })
        assertEquals(c.animaciones.map { it.id }, cambiada.animaciones.map { it.id })
        // El id no se puede cambiar por accidente, ni dejar una duracion negativa
        val rara = c.cambiarAnimacion(2) { it.copy(id = 77, duracionUs = -5) }
        assertEquals(2L, rara.animaciones[1].id)
        assertEquals(0L, rara.animaciones[1].duracionUs)
        // Un cambio que no cambia nada no gasta un paso de deshacer
        assertTrue(c.cambiarAnimacion(2) { it } === c)
        assertTrue(c.cambiarAnimacion(99) { it.copy(duracionUs = 1) } === c)

        // Sobre un bloque de antes las operaciones usan los ids de la lista unificada
        val viejo = video().copy(entrada = combs[0].piezas, salida = combs[1].piezas, duracionSalidaUs = 800_000)
        val ids = viejo.conAnimacionesUnificadas().animaciones.map { it.id }
        assertEquals(listOf(1L, 2L), ids)
        val q = viejo.cambiarAnimacion(2) { it.copy(duracionUs = 100_000) }
        assertEquals(listOf(500_000L, 100_000L), q.animaciones.map { it.duracionUs })
        assertEquals(listOf(MomentoAnimacion.SALIDA), viejo.sinAnimacion(1).animaciones.map { it.momento })
    }

    @Test
    fun duracionSugeridaSegunLoQueHace() {
        val diez = 10_000_000L
        fun d(vararg t: Animacion, dur: Long = diez) = duracionSugeridaUs(t.map { Movimiento(it) }, MomentoAnimacion.ENTRADA, dur)
        for (t in listOf(Animacion.FLASH, Animacion.FLASH_NEGRO, Animacion.ESTROBO, Animacion.NEGATIVO, Animacion.RGB, Animacion.PARPADEO)) {
            assertEquals("$t", 300_000L, d(t))
        }
        for (t in listOf(Animacion.SACUDIR, Animacion.SACUDIR_VERTICAL, Animacion.CAMARA, Animacion.TWITCH, Animacion.GLITCH)) {
            assertEquals("$t", 400_000L, d(t))
        }
        for (t in listOf(Animacion.APARECER, Animacion.ZOOM_ENTRA, Animacion.ZOOM_SALE, Animacion.IZQUIERDA, Animacion.DERECHA,
            Animacion.ARRIBA, Animacion.ABAJO, Animacion.GIRO, Animacion.REVELAR, Animacion.DESENFOCAR)) {
            assertEquals("$t", 500_000L, d(t))
        }
        for (t in listOf(Animacion.REBOTE, Animacion.PENDULO, Animacion.ZOOM_VIBRA)) assertEquals("$t", 700_000L, d(t))
        // Con varias piezas, la mas larga
        assertEquals(500_000L, d(Animacion.FLASH, Animacion.ZOOM_ENTRA))
        assertEquals(700_000L, d(Animacion.CAMARA, Animacion.PENDULO))
        // Nunca mas de la mitad del bloque ni menos de un fotograma
        assertEquals(200_000L, d(Animacion.ZOOM_ENTRA, dur = 400_000))
        assertEquals(33_333L, d(Animacion.FLASH, dur = 50_000))
        // ...ni mas que el bloque entero, aunque sea mas corto que un fotograma
        assertEquals(20_000L, d(Animacion.FLASH, dur = 20_000))
        // Siempre dura el bloque; sin piezas, medio segundo
        assertEquals(diez, duracionSugeridaUs(emptyList(), MomentoAnimacion.SIEMPRE, diez))
        assertEquals(500_000L, duracionSugeridaUs(emptyList(), MomentoAnimacion.SALIDA, diez))
        // Todo el catalogo da algo razonable
        for (c in combs) {
            val us = duracionSugeridaUs(c.piezas, MomentoAnimacion.SALIDA, 3_000_000)
            assertTrue(c.nombre, us in 300_000L..700_000L)
        }
    }

    @Test
    fun tramosParaLaLineaDeTiempo() {
        val c = video(1_000_000).copy(
            entrada = listOf(Movimiento(Animacion.APARECER)), duracionEntradaUs = 200_000,
            animaciones = listOf(
                aplicada("a", MomentoAnimacion.ENTRADA, 400_000, Animacion.GIRO),
                aplicada("b", MomentoAnimacion.ENTRADA, 900_000, Animacion.GIRO).copy(activa = false),
                aplicada("c", MomentoAnimacion.SALIDA, 1_500_000, Animacion.FLASH),
                AnimacionAplicada(9, "d", MomentoAnimacion.SIEMPRE, continuas = listOf(Continuo(AnimacionContinua.PULSO)))
            )
        )
        assertEquals(400_000L to 1_000_000L, c.tramosAnimacion())
        assertEquals(0L to 0L, video().tramosAnimacion())
        assertTrue(c.mueveSiempre())
        assertFalse(video().mueveSiempre())
        assertTrue(video().copy(continua = listOf(Continuo(AnimacionContinua.GIRAR))).mueveSiempre())
    }

    // ------------------------------------------------------------------
    // Mantenimiento
    // ------------------------------------------------------------------

    private val claves = listOf(
        Clave(0, Encuadre(escala = 1f)), Clave(400_000, Encuadre(x = 0.2f, escala = 1.3f)),
        Clave(1_200_000, Encuadre(y = -0.1f, rotacion = 10f)), Clave(2_000_000, Encuadre(escala = 2f))
    )
    private val originales = listOf(
        Clave(0, Encuadre(escala = 1f)), Clave(300_000, Encuadre(x = 0.1f)), Clave(500_000, Encuadre(x = 0.25f, escala = 1.3f)),
        Clave(900_000, Encuadre(x = 0.1f, rotacion = 4f)), Clave(1_200_000, Encuadre(y = -0.1f, rotacion = 10f)),
        Clave(1_600_000, Encuadre(escala = 1.5f), Curva.LINEAL), Clave(2_000_000, Encuadre(escala = 2f))
    )

    @Test
    fun cambiarLaVelocidadAcotaLasAplicadasYEscalaLasClavesOriginales() {
        val c = Clip(
            id = 1, origen = Cuadros.video, hastaUs = 3_000_000, claves = claves, clavesOriginales = originales,
            animaciones = listOf(aplicada("a", MomentoAnimacion.ENTRADA, 2_000_000, Animacion.GIRO), aplicada("b", MomentoAnimacion.SALIDA, 300_000, Animacion.FLASH))
        )
        val r = c.conVelocidad(2f)
        assertEquals(1_500_000L, r.duracionUs)
        assertEquals(listOf(1_500_000L, 300_000L), r.animaciones.map { it.duracionUs })
        assertEquals(r.claves.map { it.tiempoUs }, listOf(0L, 200_000L, 600_000L, 1_000_000L))
        assertEquals(originales.map { it.tiempoUs / 2 }, r.clavesOriginales!!.map { it.tiempoUs })
        // Sin originales siguen sin originales
        assertNull(c.copy(clavesOriginales = null).conVelocidad(2f).clavesOriginales)
        // A la misma velocidad o en una foto, nada cambia
        assertEquals(c, c.conVelocidad(1f))
    }

    @Test
    fun dividirRepartelasAplicadasYLasClavesOriginales() {
        val ent = AnimacionAplicada(1, "e", MomentoAnimacion.ENTRADA, listOf(Movimiento(Animacion.ZOOM_ENTRA)), duracionUs = 400_000)
        val sal = AnimacionAplicada(2, "s", MomentoAnimacion.SALIDA, listOf(Movimiento(Animacion.FLASH)), duracionUs = 300_000)
        val siempre = AnimacionAplicada(3, "c", MomentoAnimacion.SIEMPRE, continuas = listOf(Continuo(AnimacionContinua.PULSO)))
        val (p, ids) = Edicion.agregarPrincipal(Proyecto(), listOf(Cuadros.video))
        val q = Edicion.cambiar(p, ids[0]) { it.copy(hastaUs = 2_000_000, claves = claves, clavesOriginales = originales, animaciones = listOf(ent, sal, siempre)) }
        val corte = 700_000L
        val (r, _) = Edicion.dividir(q, ids[0], corte)!!
        val (a, b) = r.principal
        assertEquals(listOf(ent, siempre), a.animaciones)
        assertEquals(listOf(sal, siempre), b.animaciones)
        // Las originales se parten exactamente como se partirian si fueran las claves
        assertEquals(listOf(0L, 300_000L, 500_000L, corte), a.clavesOriginales!!.map { it.tiempoUs })
        assertEquals(listOf(0L, 200_000L, 500_000L, 900_000L, 1_300_000L), b.clavesOriginales!!.map { it.tiempoUs })
        val comoClaves = Edicion.dividir(Edicion.cambiar(q, ids[0]) { it.copy(claves = originales, clavesOriginales = null) }, ids[0], corte)!!.first
        assertEquals(comoClaves.principal[0].claves, a.clavesOriginales)
        assertEquals(comoClaves.principal[1].claves, b.clavesOriginales)
        // Y en el corte las dos mitades siguen donde estaba la original, sin salto
        val entero = q.principal[0].copy(claves = originales)
        assertEquals(entero.encuadreEn(corte), a.copy(claves = a.clavesOriginales!!).encuadreEn(corte))
        assertEquals(entero.encuadreEn(corte), b.copy(claves = b.clavesOriginales!!).encuadreEn(0))
        // Y las claves de siempre se parten como antes
        assertEquals(listOf(0L, 400_000L, corte), a.claves.map { it.tiempoUs })
        assertEquals(listOf(0L, 500_000L, 1_300_000L), b.claves.map { it.tiempoUs })
        // Sin originales, sin originales en las dos mitades
        val sinOriginales = Edicion.dividir(Edicion.cambiar(q, ids[0]) { it.copy(clavesOriginales = null) }, ids[0], corte)!!.first
        assertTrue(sinOriginales.principal.all { it.clavesOriginales == null })
    }

    @Test
    fun recortarMueveLasClavesOriginalesComoLasClaves() {
        val (p, ids) = Edicion.agregarPrincipal(Proyecto(), listOf(Cuadros.video))
        val q = Edicion.cambiar(p, ids[0]) { it.copy(hastaUs = 2_000_000, claves = claves, clavesOriginales = originales) }
        val r = Edicion.recortar(q, ids[0], 600_000, 2_000_000).principal[0]
        assertEquals(listOf(0L, 600_000L, 1_400_000L), r.claves.map { it.tiempoUs })
        assertEquals(listOf(0L, 300_000L, 600_000L, 1_000_000L, 1_400_000L), r.clavesOriginales!!.map { it.tiempoUs })
        val comoClaves = Edicion.recortar(Edicion.cambiar(q, ids[0]) { it.copy(claves = originales, clavesOriginales = null) }, ids[0], 600_000, 2_000_000)
        assertEquals(comoClaves.principal[0].claves, r.clavesOriginales)
        assertEquals(q.principal[0].copy(claves = originales).encuadreEn(600_000), r.copy(claves = r.clavesOriginales!!).encuadreEn(0))
        // Alargar por delante tambien las mueve, hacia atras
        val s = Edicion.recortar(Edicion.recortar(q, ids[0], 600_000, 2_000_000), ids[0], 100_000, 2_000_000).principal[0]
        assertEquals(r.clavesOriginales!!.map { it.tiempoUs + 500_000 }, s.clavesOriginales!!.map { it.tiempoUs })
    }

    @Test
    fun alPasarAPrincipalSeVanLasClavesOriginales() {
        val p = Edicion.agregarPrincipal(Proyecto(), listOf(Cuadros.video)).first
        val (q, id) = Edicion.agregarSuperpuesto(p, Cuadros.video, 1_000_000)
        val aplicadas = listOf(aplicada("a", MomentoAnimacion.ENTRADA, 300_000, Animacion.GIRO).copy(id = 1))
        val r = Edicion.aPrincipal(Edicion.cambiar(q, id) { it.copy(claves = claves, clavesOriginales = originales, animaciones = aplicadas) }, id)
        val c = r.principal.first { it.id == id }
        assertTrue(c.claves.isEmpty())
        assertNull(c.clavesOriginales)
        // Las animaciones se quedan, como la entrada y la salida
        assertEquals(aplicadas, c.animaciones)
    }

    // ------------------------------------------------------------------
    // Bitrate
    // ------------------------------------------------------------------

    @Test
    fun elBitrateCuentaLasAplicadas() {
        fun tramos(c: Clip) = Linea.tramos(Proyecto(principal = listOf(c)), normalizar = false)
        val entrando = tramos(foto().copy(animaciones = listOf(aplicada("a", MomentoAnimacion.ENTRADA, 400_000, Animacion.APARECER).copy(id = 1))))
        assertTrue(entrando.first { it.desdeUs == 0L }.factor > entrando.first { it.desdeUs == 400_000L }.factor)
        // Una salida con flash pide mas al final que una que solo se mueve
        val flash = tramos(foto().copy(animaciones = listOf(aplicada("f", MomentoAnimacion.SALIDA, 400_000, Animacion.FLASH).copy(id = 1))))
        val baja = tramos(foto().copy(animaciones = listOf(aplicada("b", MomentoAnimacion.SALIDA, 400_000, Animacion.ABAJO).copy(id = 1))))
        assertEquals(1_600_000L, flash.last().desdeUs)
        assertEquals(1.25f, flash.last().factor / baja.last().factor, 1e-4f)
        assertEquals(flash.first().factor, baja.first().factor, 0f)
        // Apagada, como si no estuviera
        assertEquals(tramos(foto()), tramos(foto().copy(animaciones = listOf(aplicada("f", MomentoAnimacion.SALIDA, 400_000, Animacion.FLASH).copy(id = 1, activa = false)))))
        // Siempre: se mueve todo el bloque
        val siempre = tramos(foto().copy(animaciones = listOf(AnimacionAplicada(1, "s", MomentoAnimacion.SIEMPRE, continuas = listOf(Continuo(AnimacionContinua.SHAKE_FLASH))))))
        assertEquals(tramos(foto().copy(continua = listOf(Continuo(AnimacionContinua.SHAKE_FLASH)))), siempre)
    }

    @Test
    fun elLatigazoEsUnGolpeFuerte() {
        fun tramos(t: TipoEfectoVideo) = Linea.tramos(
            Proyecto(principal = listOf(foto().copy(efectos = listOf(EfectoVideo(t, 500_000, 800_000))))), normalizar = false
        )
        val latigazo = tramos(TipoEfectoVideo.LATIGAZO).first { it.desdeUs == 500_000L }
        val mover = tramos(TipoEfectoVideo.MOVER).first { it.desdeUs == 500_000L }
        assertEquals(1.25f, latigazo.factor / mover.factor, 1e-4f)
        assertNotEquals(latigazo.factor, mover.factor)
    }
}
