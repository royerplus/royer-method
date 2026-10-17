package com.royermethod.app.editor.modelo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Convertir en efecto": el bloque se ve igual antes y despues en todos sus
 * fotogramas y en todas las muestras del obturador, con tolerancia 1e-4
 * (relativa en lo que va en pixeles). Solo cambia el orden de algunas sumas.
 */
class ConvertirAnimacionTest {

    private val combs = Combinaciones.entrada
    private val conts = Combinaciones.continuas
    private val curvas = Curva.entries
    private fun video(dur: Long = 2_000_000) = Clip(id = 1, origen = Cuadros.video, hastaUs = dur)
    private fun foto(dur: Long = 2_000_000) = Clip(id = 1, origen = Cuadros.foto, hastaUs = dur)

    /** Convierte la animacion [id] y comprueba que todo se ve igual. Devuelve la conversion. */
    private fun convertirIgual(clip: Clip, id: Long, que: String, fondo: Fondo = Fondo.NEGRO): Conversion {
        val conv = clip.convertirEnEfecto(id)
        assertNotNull(que, conv)
        conv!!
        // Nunca quedan las dos copias: la animacion se va en el mismo paso
        assertTrue(que, conv.clip.animaciones.none { it.id == id })
        assertTrue(que, conv.clip.entrada.isEmpty() && conv.clip.salida.isEmpty() && conv.clip.continua.isEmpty())
        assertEquals(que, clip.efectos.size + 1, conv.clip.efectos.size)
        assertEquals(que, conv.efecto, conv.clip.efectos.last())
        assertEquals(TipoEfectoVideo.ANIMACION, conv.efecto.tipo)
        val pa = Cuadros.proyecto(clip, fondo)
        val pb = Cuadros.proyecto(conv.clip, fondo)
        val ea = Cuadros.escena(pa)
        val eb = Cuadros.escena(pb)
        for (n in 0 until Linea.fotogramas(pa)) Cuadros.parecidos(ea, n, eb, n, que)
        // Fuera de la rejilla tambien, como la vista previa al arrastrar
        for (t in longArrayOf(1, 7_777, 250_001, clip.duracionUs / 2 + 3, clip.duracionUs - 1)) {
            val va = Cuadros.volcado(ea.cuadroEn(t))
            val vb = Cuadros.volcado(eb.cuadroEn(t))
            assertEquals("$que t$t", va.forma, vb.forma)
        }
        // Y el reparto de bits del video exportado es el mismo
        assertEquals(que, Linea.tramos(pa, normalizar = false), Linea.tramos(pb, normalizar = false))
        return conv
    }

    private fun conAplicada(c: Clip, a: AnimacionAplicada) = c.copy(animaciones = listOf(a.copy(id = 1)))

    @Test
    fun todasLasAnimacionesDeEntradaYSalida() {
        for ((k, t) in Animacion.entries.withIndex()) {
            // Por defecto, con curva y tramo parcial, y con intensidad fuera de lo normal
            val variantes = listOf(
                Movimiento(t),
                Movimiento(t, 1.3f, curvas[k % curvas.size], 0.15f, 0.85f),
                Movimiento(t, 0f, curvas[(k + 2) % curvas.size], 0.4f, 0.6f),
                Movimiento(t, 3.6f, curvas[(k + 4) % curvas.size], 0f, 0.5f)
            )
            for ((j, m) in variantes.withIndex()) {
                val ent = AnimacionAplicada(0, "e", MomentoAnimacion.ENTRADA, listOf(m), duracionUs = 433_333 + 100_000L * j)
                convertirIgual(conAplicada(video(), ent), 1, "entrada $t $j", if (j % 2 == 0) Fondo.NEGRO else Fondo.DESENFOCADO)
                val sal = AnimacionAplicada(0, "s", MomentoAnimacion.SALIDA, listOf(m), duracionUs = 500_000 + 77_777L * j)
                convertirIgual(conAplicada(foto(), sal), 1, "salida $t $j")
            }
        }
    }

    @Test
    fun todasLasContinuas() {
        for ((k, t) in AnimacionContinua.entries.withIndex()) {
            for ((j, v) in listOf(1f, 0.5f, 2.3f).withIndex()) {
                val a = AnimacionAplicada(0, "c", MomentoAnimacion.SIEMPRE, continuas = listOf(Continuo(t, 0.6f + 0.5f * j, v)))
                val clip = if (j == 1) video(2_500_000).copy(claves = listOf(Clave(0, Encuadre(escala = 1.2f)), Clave(1_500_000, Encuadre(x = 0.1f, rotacion = 5f))))
                    else foto(1_700_000 + 100_000L * k)
                convertirIgual(conAplicada(clip, a), 1, "siempre $t $v", if (j == 2) Fondo.DESENFOCADO else Fondo.NEGRO)
            }
        }
    }

    @Test
    fun todoElCatalogo() {
        for ((k, c) in combs.withIndex()) {
            val dur = duracionSugeridaUs(c.piezas, MomentoAnimacion.ENTRADA, 2_000_000)
            convertirIgual(conAplicada(video(), AnimacionAplicada(0, c.nombre, MomentoAnimacion.ENTRADA, c.piezas, duracionUs = dur)), 1, "entrada ${c.nombre}")
            convertirIgual(conAplicada(foto(), AnimacionAplicada(0, c.nombre, MomentoAnimacion.SALIDA, c.piezas, duracionUs = dur + 33_333L * (k % 4))), 1,
                "salida ${c.nombre}", Fondo.DESENFOCADO)
        }
        for (c in conts) {
            convertirIgual(conAplicada(foto(), AnimacionAplicada(0, c.first, MomentoAnimacion.SIEMPRE, continuas = c.second)), 1, "siempre ${c.first}")
        }
    }

    @Test
    fun conVariasAlaVezYLasDeAntes() {
        // Entrada, salida y continuas de antes mas aplicadas: se convierten una a una
        for ((k, c) in combs.withIndex()) {
            if (k % 3 != 0) continue
            var clip = video().copy(
                entrada = c.piezas, duracionEntradaUs = 500_000,
                salida = combs[(k + 5) % combs.size].piezas, duracionSalidaUs = 650_000,
                continua = conts[k % conts.size].second,
                animaciones = listOf(
                    AnimacionAplicada(10, "otra", MomentoAnimacion.ENTRADA, combs[(k + 11) % combs.size].piezas, duracionUs = 300_000),
                    AnimacionAplicada(11, "mas", MomentoAnimacion.SIEMPRE, continuas = conts[(k + 3) % conts.size].second)
                )
            )
            val original = clip
            // Las de antes reciben los ids siguientes: 12, 13 y 14
            for (id in listOf(13L, 10L, 12L, 14L, 11L)) {
                clip = convertirIgual(clip, id, "${c.nombre} $id").clip
            }
            assertTrue(clip.animaciones.isEmpty())
            assertEquals(5, clip.efectos.size)
            assertEquals(listOf(1L, 2L, 3L, 4L, 5L), clip.efectos.map { it.id })
            // Todas convertidas, sigue igual que al principio
            val ea = Cuadros.escena(Cuadros.proyecto(original))
            val eb = Cuadros.escena(Cuadros.proyecto(clip))
            for (n in 0 until 60L) Cuadros.parecidos(ea, n, eb, n, "${c.nombre} todas")
        }
    }

    @Test
    fun masLargaQueElBloque() {
        val giro = listOf(Movimiento(Animacion.GIRO), Movimiento(Animacion.GLITCH, 0.8f, Curva.LINEAL), Movimiento(Animacion.ESTROBO))
        val ent = conAplicada(foto(1_000_000), AnimacionAplicada(0, "e", MomentoAnimacion.ENTRADA, giro, duracionUs = 1_500_000))
        val e = convertirIgual(ent, 1, "entrada larga").efecto
        assertEquals(0L to 1_000_000L, e.inicioUs to e.finUs)
        assertEquals(1_500_000L, e.duracionBaseUs)
        val sal = conAplicada(foto(1_000_000), AnimacionAplicada(0, "s", MomentoAnimacion.SALIDA, giro, duracionUs = 1_500_000))
        val s = convertirIgual(sal, 1, "salida larga").efecto
        assertEquals(0L to 1_000_000L, s.inicioUs to s.finUs)
        assertEquals(500_000L, s.desfaseUs)
        // En un bloque con entrada y salida pisandose, y en capas con partes
        val corto = foto(500_000).copy(animaciones = listOf(
            AnimacionAplicada(1, "e", MomentoAnimacion.ENTRADA, combs[20].piezas, duracionUs = 400_000),
            AnimacionAplicada(2, "s", MomentoAnimacion.SALIDA, combs[40].piezas, duracionUs = 450_000)
        ))
        convertirIgual(convertirIgual(corto, 2, "corto salida").clip, 1, "corto entrada")
        val partes = video().copy(
            piezas = listOf(PiezaVideo("a", RegionUv(0f, 0f, 1f, 0.5f)), PiezaVideo("b", RegionUv(0f, 0.5f, 1f, 1f),
                claves = listOf(Clave(0, Encuadre()), Clave(1_000_000, Encuadre(x = 0.2f))))),
            animaciones = listOf(AnimacionAplicada(1, "e", MomentoAnimacion.ENTRADA, combs[44].piezas, duracionUs = 600_000))
        )
        convertirIgual(partes, 1, "con partes", Fondo.DESENFOCADO)
    }

    @Test
    fun loQueSeConvierteYDondeQueda() {
        val c = video().copy(animaciones = listOf(
            AnimacionAplicada(1, "Zoom", MomentoAnimacion.ENTRADA, listOf(Movimiento(Animacion.ZOOM_ENTRA)), duracionUs = 400_000),
            AnimacionAplicada(2, "Flash", MomentoAnimacion.SALIDA, listOf(Movimiento(Animacion.FLASH)), duracionUs = 300_000, activa = false),
            AnimacionAplicada(3, "Latido", MomentoAnimacion.SIEMPRE, continuas = listOf(Continuo(AnimacionContinua.LATIDO, 1.2f, 2f))),
            AnimacionAplicada(4, "Vacia", MomentoAnimacion.ENTRADA),
            AnimacionAplicada(5, "Cero", MomentoAnimacion.SALIDA, listOf(Movimiento(Animacion.FLASH)), duracionUs = 0),
            AnimacionAplicada(6, "Sin continuas", MomentoAnimacion.SIEMPRE, listOf(Movimiento(Animacion.FLASH)))
        ), efectos = listOf(EfectoVideo(TipoEfectoVideo.SACUDIDA, 0, 500_000, id = 7)))
        val e = c.convertirEnEfecto(1)!!.efecto
        assertEquals(listOf(0L, 400_000L, 400_000L, 0L), listOf(e.inicioUs, e.finUs, e.duracionBaseUs, e.desfaseUs))
        assertEquals(AnimacionEnEfecto("Zoom", MomentoAnimacion.ENTRADA, listOf(Movimiento(Animacion.ZOOM_ENTRA))), e.animacion)
        assertEquals(listOf(1f, 1f, 0L, 0L), listOf(e.intensidad, e.rapidez, e.fundidoEntradaUs, e.fundidoSalidaUs))
        assertEquals(8L, e.id)
        // Apagada sigue apagada
        val s = c.convertirEnEfecto(2)!!.efecto
        assertEquals(listOf(1_700_000L, 2_000_000L, 300_000L), listOf(s.inicioUs, s.finUs, s.duracionBaseUs))
        assertFalse(s.activo)
        val l = c.convertirEnEfecto(3)!!.efecto
        assertEquals(listOf(0L, 2_000_000L, 2_000_000L), listOf(l.inicioUs, l.finUs, l.duracionBaseUs))
        // Lo que no hace nada no se convierte
        for (id in listOf(4L, 5L, 6L, 99L)) assertNull("$id", c.convertirEnEfecto(id))
        assertFalse(c.animaciones[3].convertible())
        assertTrue(c.animaciones[0].convertible() && c.animaciones[2].convertible())
        // Un bloque de antes se convierte por los ids de la lista unificada
        val viejo = foto().copy(entrada = combs[5].piezas, continua = conts[2].second)
        val conv = viejo.convertirEnEfecto(2)!!
        assertEquals(MomentoAnimacion.SIEMPRE, conv.efecto.animacion!!.momento)
        assertEquals(conts[2].first, conv.efecto.animacion!!.nombre)
        assertEquals(listOf(MomentoAnimacion.ENTRADA), conv.clip.animaciones.map { it.momento })
    }

    @Test
    fun elEfectoSePuedeMoverYRecortar() {
        val quieta = Cuadros.escena(Cuadros.proyecto(foto(3_000_000)))
        val casos = listOf(
            AnimacionAplicada(1, "e", MomentoAnimacion.ENTRADA, combs.first { it.nombre == "Impacto con flash" }.piezas, duracionUs = 600_000),
            AnimacionAplicada(1, "s", MomentoAnimacion.SALIDA, combs.first { it.nombre == "Glitch RGB" }.piezas, duracionUs = 600_000),
            AnimacionAplicada(1, "c", MomentoAnimacion.SIEMPRE, continuas = conts.first { it.first == "Zoom con temblor" }.second)
        )
        val un = 1_000_000L
        for (a in casos) {
            val clip = foto(3_000_000).copy(animaciones = listOf(a))
            val conv = clip.convertirEnEfecto(1)!!
            // Un tramo de un segundo para poder moverlo dentro del bloque
            val tramo = when (a.momento) {
                MomentoAnimacion.SALIDA -> conv.efecto.conRango(conv.efecto.finUs - un, conv.efecto.finUs)
                else -> conv.efecto.conRango(conv.efecto.inicioUs, conv.efecto.inicioUs + un)
            }
            val base = Cuadros.escena(Cuadros.proyecto(conv.clip.copy(efectos = listOf(tramo))))
            val f0 = Linea.fotogramaDe(tramo.inicioUs)
            // Mover un segundo (30 fotogramas justos): lo de dentro se ve igual, un segundo despues o antes
            val delta = if (a.momento == MomentoAnimacion.SALIDA) -un else un
            val movido = tramo.desplazado(delta, 3_000_000)
            assertEquals(tramo.duracionUs, movido.duracionUs)
            assertEquals(tramo.inicioUs + delta, movido.inicioUs)
            val em = Cuadros.escena(Cuadros.proyecto(conv.clip.copy(efectos = listOf(movido))))
            val df = delta / un * 30
            for (n in f0 + 1 until f0 + 29) Cuadros.parecidos(base, n, em, n + df, "${a.momento} movido $n")
            // Fuera de su tramo el bloque se ve como sin animacion
            // (donde estaba antes de moverlo)
            for (n in f0 + 2..f0 + 28) {
                assertTrue("${a.momento} fuera $n", Cuadros.volcado(em.cuadro(n)).numeros.contentEquals(Cuadros.volcado(quieta.cuadro(n)).numeros))
            }
            // Recortar conserva el reloj: lo que queda se ve igual que antes de recortar
            val recortado = tramo.conRango(tramo.inicioUs + 300_000, tramo.finUs - 200_000)
            val er = Cuadros.escena(Cuadros.proyecto(conv.clip.copy(efectos = listOf(recortado))))
            val r0 = Linea.fotogramaDe(recortado.inicioUs) + 1
            val r1 = Linea.fotogramaDe(recortado.finUs) - 1
            for (n in r0 until r1) Cuadros.parecidos(base, n, er, n, "${a.momento} recortado $n")
            for (n in listOf(f0 + 2, r1 + 2)) {
                assertTrue("${a.momento} recortado fuera $n", Cuadros.volcado(er.cuadro(n)).numeros.contentEquals(Cuadros.volcado(quieta.cuadro(n)).numeros))
            }
        }
    }

    @Test
    fun conFundidosYRapidezSigueSiendoValido() {
        // Despues de convertir se puede ajustar como cualquier efecto: nada se rompe
        for (c in combs + combs.take(3)) {
            for (momento in listOf(MomentoAnimacion.ENTRADA, MomentoAnimacion.SALIDA)) {
                val clip = foto().copy(animaciones = listOf(AnimacionAplicada(1, c.nombre, momento, c.piezas, duracionUs = 700_000)))
                val e = clip.convertirEnEfecto(1)!!
                val ajustado = e.efecto.copy(intensidad = 2.5f, rapidez = 1.7f, fundidoEntradaUs = 200_000, fundidoSalidaUs = 100_000,
                    progresos = if (c.nombre.length % 2 == 0) listOf(ProgresoEfecto(0, 0f), ProgresoEfecto(300_000, 0.8f), ProgresoEfecto(700_000, 1f)) else emptyList())
                valido(e.clip.copy(efectos = listOf(ajustado)), "${c.nombre} $momento")
            }
        }
        for (c in conts) {
            val clip = foto().copy(animaciones = listOf(AnimacionAplicada(1, c.first, MomentoAnimacion.SIEMPRE, continuas = c.second)))
            val e = clip.convertirEnEfecto(1)!!
            valido(e.clip.copy(efectos = listOf(e.efecto.copy(intensidad = 0.4f, rapidez = 3f, desfaseUs = -200_000))), c.first)
        }
    }

    private fun valido(c: Clip, que: String) {
        val e = Cuadros.escena(Cuadros.proyecto(c, Fondo.DESENFOCADO))
        for (n in 0L until 60L) {
            for (paso in e.cuadro(n).clips) {
                assertTrue("$que $n", paso.matrices.all { m -> m.all { it.isFinite() } })
                assertTrue("$que $n", paso.opacidades.all { it in 0f..1f })
                assertTrue("$que $n", paso.luz in -1f..1f && paso.rgb >= 0f && paso.negativo in 0f..1f && paso.desenfoque.isFinite())
            }
        }
    }

    @Test
    fun limitacionesEnFrasesCortas() {
        val todas = ArrayList<String>()
        for (m in MomentoAnimacion.entries) for (c in combs) {
            val a = AnimacionAplicada(1, c.nombre, m, c.piezas, if (m == MomentoAnimacion.SIEMPRE) conts[combs.indexOf(c) % conts.size].second else emptyList())
            val l = limitacionesConversion(a)
            assertTrue(l.isNotEmpty())
            assertTrue(l[0], l[0].contains("bordes del bloque"))
            todas.addAll(l)
        }
        // Lo que depende de lo que lleva
        val acercamiento = AnimacionAplicada(1, "a", MomentoAnimacion.SIEMPRE, continuas = listOf(Continuo(AnimacionContinua.ACERCAMIENTO)))
        assertTrue(limitacionesConversion(acercamiento).any { it.contains("Acercamiento") })
        assertTrue(limitacionesConversion(acercamiento).any { it.contains("marco") })
        val estrobo = AnimacionAplicada(1, "e", MomentoAnimacion.SIEMPRE, continuas = listOf(Continuo(AnimacionContinua.ESTROBO)))
        assertTrue(limitacionesConversion(estrobo).none { it.contains("marco") || it.contains("Acercamiento") })
        val revelar = AnimacionAplicada(1, "r", MomentoAnimacion.ENTRADA, listOf(Movimiento(Animacion.REVELAR)))
        assertTrue(limitacionesConversion(revelar).any { it.contains("mascara") })
        val mover = AnimacionAplicada(1, "m", MomentoAnimacion.ENTRADA, listOf(Movimiento(Animacion.IZQUIERDA)))
        assertTrue(limitacionesConversion(mover).none { it.contains("mascara") })
        // Para ensenarlas en la interfaz: espanol sin tildes ni enies, y cortas
        for (t in todas.distinct() + limitacionesConversion(acercamiento) + limitacionesConversion(revelar)) {
            assertTrue(t, t.all { it.code < 128 })
            assertTrue(t, t.length <= 160)
            assertTrue(t, t.endsWith("."))
        }
    }
}
