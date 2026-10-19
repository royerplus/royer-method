package com.royermethod.app.editor.modelo

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Las herramientas de movimiento: pocos parametros que se ajustan despues de ponerlos. */
class HerramientasEfectoTest {
    private val video = Origen("v", "v", TipoOrigen.VIDEO, 1080, 1920, 4_000_000, 30f)
    private val foto = Origen("f", "f", TipoOrigen.IMAGEN, 600, 800)
    private val medidor = Medidor { c, _ -> if (c.origen?.uri == "f") 600f to 800f else 1080f to 1920f }
    private val W = 576
    private val H = 1024

    private fun proyecto(c: Clip, blur: Boolean = false, fondo: Fondo = Fondo.NEGRO) =
        Proyecto(principal = listOf(c), fondo = fondo, salida = AjustesSalida(desenfoqueMovimiento = blur))
    private fun escena(c: Clip, blur: Boolean = false, fondo: Fondo = Fondo.NEGRO) =
        Escena(proyecto(c, blur, fondo), W, H, medidor)
    private fun efecto(c: Clip, local: Long) = escena(c).efectosVideo(c, local)
    private fun clip(vararg e: EfectoVideo) = Clip(1, origen = video, efectos = e.toList())
    private fun lineal(a: AjustesEfecto) = a.copy(curva = Curva.LINEAL)

    /** Todo lo que dibuja un fotograma, con los numeros bit a bit: para comparar dos. */
    private fun firma(c: Cuadro): String {
        val sb = StringBuilder()
        fun b(f: Float) = Integer.toHexString(java.lang.Float.floatToRawIntBits(f))
        fun fs(a: FloatArray) = a.joinToString(",") { b(it) }
        fun paso(p: Paso) {
            when (p) {
                is PasoSolido -> sb.append("S ${p.argb} ${fs(p.opacidades)}\n")
                is PasoLinea -> sb.append("L ${fs(p.bordes)}\n")
                is PasoClip -> sb.append("C ${p.clip.id} ${p.esFondo} ${p.matrices.joinToString("|") { fs(it) }} ${fs(p.opacidades)} " +
                    "${fs(p.bordes)} ${p.mascara} ${b(p.desenfoque)} ${b(p.desenfoqueX)} ${b(p.desenfoqueY)} ${b(p.espejoBordes)} " +
                    "${b(p.luz)} ${b(p.rgb)} ${b(p.negativo)} ${b(p.anchoEnLienzo)} ${b(p.altoEnLienzo)} ${p.recorteFuente} ${p.mezcla}\n")
                is PasoPartes -> { sb.append("P ${p.mezcla}\n"); p.partes.forEach { paso(it) } }
                is PasoGrupo -> { sb.append("G ${p.matrices.joinToString("|") { fs(it) }} ${fs(p.bordes)} ${p.mascara}\n"); p.pasos.forEach { paso(it) } }
            }
        }
        c.pasos.forEach { paso(it) }
        return sb.toString()
    }

    // ---- Movimientos ----

    @Test
    fun moverZoomYGiroSiguenSuValorConLaCurvaYElAngulo() {
        val mover = EfectoVideo(TipoEfectoVideo.MOVER, 0, 1_000_000, ajustes = lineal(CatalogoEfectos.ajustesDe(TipoEfectoVideo.MOVER)))
        val c = clip(mover)
        // Entra desde la izquierda: un lienzo fuera, exacto, sin desviarse en vertical
        assertEquals(-1f, efecto(c, 0).dx, 0f)
        assertEquals(0f, efecto(c, 0).dy, 0f)
        assertEquals(-0.75f, efecto(c, 250_000).dx, 1e-6f)
        assertEquals(0f, efecto(c, 999_999).dx, 1e-5f)
        assertEquals(-0.375f, efecto(clip(mover.copy(intensidad = 0.5f)), 250_000).dx, 1e-6f)
        val diagonal = clip(mover.copy(ajustes = mover.ajustes.copy(angulo = 45f)))
        assertEquals(0.75f * cos(Math.PI / 4).toFloat(), efecto(diagonal, 250_000).dx, 1e-6f)
        assertEquals(0.75f * sin(Math.PI / 4).toFloat(), efecto(diagonal, 250_000).dy, 1e-6f)
        val abajo = clip(mover.copy(ajustes = mover.ajustes.copy(angulo = 90f)))
        assertEquals(0f, efecto(abajo, 0).dx, 0f)
        assertEquals(1f, efecto(abajo, 0).dy, 0f)
        // Llega al motor igual que un encuadre movido un lienzo
        val movido = escena(c).cuadroEn(0).clips.single().matrices.single()
        val aMano = Clip(1, origen = video, encuadre = Encuadre(x = -1f))
        assertArrayEquals(escena(aMano).cuadroEn(0).clips.single().matrices.single(), movido, 0f)

        val zoom = clip(EfectoVideo(TipoEfectoVideo.ZOOM, 0, 1_000_000, ajustes = lineal(CatalogoEfectos.ajustesDe(TipoEfectoVideo.ZOOM))))
        assertEquals(1.3f, efecto(zoom, 0).escala, 1e-6f)
        assertEquals(1.15f, efecto(zoom, 500_000).escala, 1e-6f)
        val medio = clip(zoom.efectos.single().copy(intensidad = 0.5f))
        assertEquals(1.15f, efecto(medio, 0).escala, 1e-6f)

        val giro = clip(EfectoVideo(TipoEfectoVideo.GIRAR, 0, 1_000_000, ajustes = lineal(CatalogoEfectos.ajustesDe(TipoEfectoVideo.GIRAR))))
        assertEquals(-90f, efecto(giro, 0).rotacion, 0f)
        assertEquals(-45f, efecto(giro, 500_000).rotacion, 1e-4f)
        // Fuera de su tramo no hace nada
        assertEquals(Efecto(), efecto(giro, 1_000_000))
    }

    @Test
    fun laCurvaDeLosAjustesManda() {
        val a = AjustesEfecto(desde = 0f, hasta = 1f, curva = Curva.SALIDA)
        val e = EfectoVideo(TipoEfectoVideo.MOVER, 0, 1_000_000, ajustes = a)
        assertEquals(1f - 0.5f * 0.5f * 0.5f, e.valorEn(500_000), 1e-6f)
        val b = e.copy(ajustes = a.copy(bezier = Bezier(0f, 0f, 1f, 1f)))
        assertEquals(b.ajustes.bezier!!.en(0.25f), b.valorEn(250_000), 0f)
    }

    @Test
    fun elLatigazoVaHaciaElAnguloYVuelvePorElLadoContrario() {
        val e = EfectoVideo(TipoEfectoVideo.LATIGAZO, 0, 1_000_000,
            ajustes = AjustesEfecto(angulo = 180f, desde = 1f, curva = Curva.LINEAL))
        val c = clip(e)
        assertEquals(0f, efecto(c, 0).dx, 0f)
        assertEquals(-0.5f, efecto(c, 250_000).dx, 1e-6f)
        // Justo antes de la mitad esta un lienzo a la izquierda; en la mitad, uno a
        // la derecha: dos lienzos de diferencia, que con bordes espejo son la misma imagen
        assertEquals(-1f, efecto(c, 499_999).dx, 1e-5f)
        assertEquals(1f, efecto(c, 500_000).dx, 0f)
        assertEquals(0.5f, efecto(c, 750_000).dx, 1e-6f)
        assertEquals(0f, efecto(c, 999_999).dx, 1e-5f)
        assertEquals(0f, efecto(c, 250_000).dy, 0f)
        // Con la curva que acelera, la vuelta frena: la curva al reves
        val acelera = clip(e.copy(ajustes = e.ajustes.copy(curva = Curva.ENTRADA)))
        assertEquals(-0.125f, efecto(acelera, 250_000).dx, 1e-6f)
        assertEquals(0.125f, efecto(acelera, 750_000).dx, 1e-6f)
        assertEquals(-0.25f, efecto(clip(e.copy(intensidad = 0.5f)), 250_000).dx, 1e-6f)
    }

    // ---- Desenfoque direccional ----

    @Test
    fun elDesenfoqueDireccionalVaEnCualquierAnguloYNoSeAnula() {
        fun d(angulo: Float, i: Float = 1f) = efecto(clip(EfectoVideo(TipoEfectoVideo.DESENFOQUE_DIRECCIONAL, 0, 1_000_000,
            intensidad = i, ajustes = AjustesEfecto(angulo = angulo))), 500_000)
        val horizontal = efecto(clip(EfectoVideo(TipoEfectoVideo.DESENFOQUE_HORIZONTAL, 0, 1_000_000)), 500_000)
        val vertical = efecto(clip(EfectoVideo(TipoEfectoVideo.DESENFOQUE_VERTICAL, 0, 1_000_000)), 500_000)
        // A 0 y a 90 grados es lo mismo que los de siempre, bit a bit
        assertEquals(horizontal, d(0f))
        assertEquals(vertical, d(90f))
        // Un rastro es una linea: 180 es el mismo que 0, y 270 el mismo que 90
        assertEquals(d(0f), d(180f))
        assertEquals(d(90f), d(270f))
        assertEquals(d(135f).desenfoqueX, d(315f).desenfoqueX, 1e-4f)
        assertEquals(d(135f).desenfoqueY, d(315f).desenfoqueY, 1e-4f)
        val r = H * 0.04f
        assertEquals(r * cos(Math.PI / 4).toFloat(), d(135f).desenfoqueX, 1e-3f)
        assertEquals(-r * sin(Math.PI / 4).toFloat(), d(135f).desenfoqueY, 1e-3f)
        // Dos iguales en sentidos contrarios suman en vez de anularse
        val dos = clip(EfectoVideo(TipoEfectoVideo.DESENFOQUE_DIRECCIONAL, 0, 1_000_000, ajustes = AjustesEfecto(angulo = 30f)),
            EfectoVideo(TipoEfectoVideo.DESENFOQUE_DIRECCIONAL, 0, 1_000_000, ajustes = AjustesEfecto(angulo = 210f)))
        assertEquals(2f * d(30f).desenfoqueX, efecto(dos, 500_000).desenfoqueX, 1e-3f)
        assertEquals(2f * d(30f).desenfoqueY, efecto(dos, 500_000).desenfoqueY, 1e-3f)
        assertEquals(d(30f, 0.5f).desenfoqueX * 2f, d(30f).desenfoqueX, 1e-3f)
        // Y llega al paso de dibujo con su signo
        val paso = escena(clip(EfectoVideo(TipoEfectoVideo.DESENFOQUE_DIRECCIONAL, 0, 1_000_000,
            ajustes = AjustesEfecto(angulo = 135f)))).cuadroEn(500_000).clips.single()
        assertTrue(paso.desenfoqueX > 0f && paso.desenfoqueY < 0f)
    }

    // ---- Mascaras ----

    @Test
    fun elCirculoTieneCentroRadioGrosorYSuavidad() {
        val base = EfectoVideo(TipoEfectoVideo.CIRCULO, 0, 1_000_000, ajustes = AjustesEfecto(curva = Curva.LINEAL))
        val todo = sqrt(W.toFloat() * W + H.toFloat() * H) / 2f + 4f
        val mitad = efecto(clip(base), 500_000)
        assertEquals(Mascara(0f, 0f, H * 0.003f + 0.5f, radial = true), mitad.mascara)
        assertEquals(0.5f * todo, mitad.borde, 1e-3f)
        // Radio cero: nada, ni el punto del borde suave
        assertEquals(-(H * 0.003f + 0.5f) / 2f, efecto(clip(base), 0).borde, 0f)
        assertEquals(todo, efecto(clip(base.copy(ajustes = base.ajustes.copy(desde = 1f))), 0).borde, 1e-3f)
        // Con el centro movido, el radio que lo cubre todo llega a la esquina mas lejana
        val a = base.ajustes.copy(centroX = 0.25f, centroY = -0.1f, grosor = 0.15f, suavidad = 0.01f, invertir = true)
        val anillo = efecto(clip(base.copy(ajustes = a)), 500_000)
        val cx = 0.25f * W
        val cy = -0.1f * H
        val lejos = sqrt((W / 2f + cx) * (W / 2f + cx) + (H / 2f - cy) * (H / 2f - cy)) + 4f
        val m = anillo.mascara!!
        assertEquals(cx, m.centroX, 0f)
        assertEquals(cy, m.centroY, 0f)
        assertEquals(0.15f * lejos, m.grosor, 1e-3f)
        assertEquals(0.01f * H, m.suave, 1e-4f)
        assertTrue(m.invertida && m.radial)
        assertEquals(0.5f * lejos, anillo.borde, 1e-3f)
        // No se funde: el fundido no destapa la imagen al empezar ni encoge el radio
        val fundido = base.copy(fundidoEntradaUs = 1_000_000, fundidoSalidaUs = 1_000_000, intensidad = 0.3f)
        assertEquals(mitad, efecto(clip(fundido), 500_000))
        assertTrue(efecto(clip(fundido), 0).mascara != null)
        // Apagado o con intensidad cero, no recorta
        assertNull(efecto(clip(base.copy(intensidad = 0f)), 500_000).mascara)
        assertNull(efecto(clip(base.copy(activo = false)), 500_000).mascara)
    }

    @Test
    fun elBarridoAvanzaHaciaElAnguloDeFueraAFuera() {
        val base = EfectoVideo(TipoEfectoVideo.BARRIDO, 0, 1_000_000, ajustes = AjustesEfecto(curva = Curva.LINEAL))
        val largo = W / 2f + 2f
        assertEquals(Mascara(1f, 0f, 2f), efecto(clip(base), 0).mascara)
        assertEquals(-largo - 1f, efecto(clip(base), 0).borde, 0f)
        assertEquals(0f, efecto(clip(base), 500_000).borde, 1e-3f)
        assertEquals(largo + 1f, efecto(clip(base.copy(ajustes = base.ajustes.copy(desde = 1f))), 500_000).borde, 0f)
        val abajo = efecto(clip(base.copy(ajustes = base.ajustes.copy(angulo = 90f))), 250_000)
        assertEquals(Mascara(0f, 1f, 2f), abajo.mascara)
        assertEquals(-(H / 2f + 2f) / 2f, abajo.borde, 1e-3f)
        val diagonal = efecto(clip(base.copy(ajustes = base.ajustes.copy(angulo = 45f, suavidad = 0.02f, invertir = true,
            centroX = -0.1f))), 250_000)
        val c = cos(Math.PI / 4).toFloat()
        val suave = 0.02f * H
        // Con el centro movido el recorrido se alarga lo que se aparta en la direccion
        val l = (c * W + c * H) / 2f + abs(c * (-0.1f * W)) + suave
        val m = diagonal.mascara!!
        assertEquals(c, m.dirX, 1e-6f)
        assertEquals(c, m.dirY, 1e-6f)
        assertEquals(suave, m.suave, 1e-4f)
        assertTrue(m.invertida)
        assertEquals(-0.1f * W, m.centroX, 0f)
        assertEquals(-l / 2f, diagonal.borde, 1e-2f)
    }

    @Test
    fun losCirculosYElRevelarDeAntesAceptanCentro() {
        val cerrar = EfectoVideo(TipoEfectoVideo.CIRCULO_CERRAR, 0, 1_000_000)
        val centrado = efecto(clip(cerrar), 500_000)
        assertEquals(0f, centrado.mascara!!.centroX, 0f)
        assertEquals(0f, centrado.mascara!!.grosor, 0f)
        val movido = efecto(clip(cerrar.copy(ajustes = AjustesEfecto(centroX = 0.5f))), 500_000)
        val lejos = sqrt(W.toFloat() * W + (H / 2f) * (H / 2f)) + 4f
        assertEquals(0.5f * W, movido.mascara!!.centroX, 0f)
        assertEquals(lejos * 0.5f, movido.borde, 1e-3f)
        val revelar = EfectoVideo(TipoEfectoVideo.REVELAR_HORIZONTAL, 0, 1_000_000)
        val r = efecto(clip(revelar.copy(ajustes = AjustesEfecto(centroX = 0.1f, centroY = 0.2f))), 250_000)
        // El borde recorre tambien lo que se aparta el centro (0,1 de ancho a
        // cada lado): a un cuarto del tramo, un cuarto de ese recorrido
        assertEquals((W + 2f * 0.1f * W) * (0.25f - 0.5f), r.borde, 1e-3f)
        assertEquals(Mascara(1f, 0f, 2f, centroX = 0.1f * W, centroY = 0.2f * H), r.mascara)
        // Centrado, el de siempre
        assertEquals(W * (0.25f - 0.5f), efecto(clip(revelar), 250_000).borde, 0f)
    }

    /** Los centros de pixel de una rejilla que llega a los cuatro bordes del lienzo (para [seVe]). */
    private fun rejilla(): List<Pair<Float, Float>> = (0..24).flatMap { i ->
        (0..24).map { j -> (-W / 2f + 0.5f + (W - 1f) * i / 24f) to (-H / 2f + 0.5f + (H - 1f) * j / 24f) }
    }

    @Test
    fun conElCentroMovidoElBarridoYElRevelarTapanYDescubrenTodo() {
        val centros = listOf(0f to 0f, -0.25f to 0f, 0.25f to -0.3f, 0.45f to 0.45f, -0.5f to 0.2f)
        for ((cx, cy) in centros) {
            for (angulo in listOf(0f, 45f, 90f, 180f, 300f)) {
                val a = AjustesEfecto(curva = Curva.LINEAL, angulo = angulo, centroX = cx, centroY = cy)
                val b = EfectoVideo(TipoEfectoVideo.BARRIDO, 0, 1_000_000, ajustes = a)
                val que = "barrido $angulo centro $cx,$cy"
                // Descubierto 0: nada; 1: todo, tambien en las esquinas del lado contrario al centro
                val nada = efecto(clip(b.copy(ajustes = a.copy(desde = 0f, hasta = 0f))), 500_000)
                val todo = efecto(clip(b.copy(ajustes = a.copy(desde = 1f, hasta = 1f))), 500_000)
                for ((x, y) in rejilla()) {
                    assertEquals("$que nada ($x, $y)", 0f, seVe(nada, x, y), 0f)
                    assertEquals("$que todo ($x, $y)", 1f, seVe(todo, x, y), 0f)
                }
                // Al final del tramo ya esta todo descubierto: cuando se quita la mascara no salta nada
                val fin = efecto(clip(b), 999_999)
                assertTrue(que, rejilla().all { (x, y) -> seVe(fin, x, y) == 1f })
                assertTrue(que, rejilla().all { (x, y) -> seVe(efecto(clip(b), 0), x, y) == 0f })
                // A medio camino el borde pasa por el centro
                val medio = efecto(clip(b), 500_000)
                assertEquals(que, 0.5f, seVe(medio, cx * W, cy * H), 1e-3f)
            }
            val r = EfectoVideo(TipoEfectoVideo.REVELAR_HORIZONTAL, 0, 1_000_000, ajustes = AjustesEfecto(centroX = cx, centroY = cy))
            val que = "revelar centro $cx,$cy"
            assertTrue(que, rejilla().all { (x, y) -> seVe(efecto(clip(r), 0), x, y) == 0f })
            assertTrue(que, rejilla().all { (x, y) -> seVe(efecto(clip(r), 999_999), x, y) > 0.5f })
            assertNull(efecto(clip(r), 1_000_000).mascara)
        }
    }

    @Test
    fun elZoomYElGiroSeQuedanQuietosEnSuCentro() {
        // Donde cae en la textura el pixel (x, y) del lienzo (desde su esquina)
        fun uv(c: Clip, t: Long, x: Float, y: Float): Pair<Float, Float> {
            val m = escena(c).cuadroEn(t).clips.single().matrices.single()
            return (m[0] * x + m[3] * y + m[6]) to (m[1] * x + m[4] * y + m[7])
        }
        val quieto = clip()
        for ((tipo, a) in listOf(
            TipoEfectoVideo.ZOOM to CatalogoEfectos.ajustesDe(TipoEfectoVideo.ZOOM).copy(desde = 2f, centroX = 0.25f, centroY = -0.3f),
            TipoEfectoVideo.GIRAR to CatalogoEfectos.ajustesDe(TipoEfectoVideo.GIRAR).copy(desde = -60f, centroX = -0.4f, centroY = 0.2f)
        )) {
            val c = clip(EfectoVideo(tipo, 0, 1_000_000, ajustes = a))
            val px = W / 2f + a.centroX * W
            val py = H / 2f + a.centroY * H
            // El centro elegido ve lo mismo que sin efecto; otro punto, no
            for (t in listOf(0L, 300_000L, 700_000L)) {
                val (u0, v0) = uv(quieto, t, px, py)
                val (u1, v1) = uv(c, t, px, py)
                assertEquals("$tipo $t", u0, u1, 1e-5f)
                assertEquals("$tipo $t", v0, v1, 1e-5f)
            }
            assertNotEquals(uv(quieto, 0, W / 2f, H / 2f), uv(c, 0, W / 2f, H / 2f))
            // Sin centro, el de siempre: ni un desplazamiento
            val centrado = efecto(clip(EfectoVideo(tipo, 0, 1_000_000, ajustes = a.copy(centroX = 0f, centroY = 0f))), 300_000)
            assertEquals(0f, centrado.dx, 0f)
            assertEquals(0f, centrado.dy, 0f)
            assertTrue(Parametro.CENTRO in CatalogoEfectos.parametros(tipo))
        }
    }

    @Test
    fun losEfectosDeAntesNoMiranLosAjustesNuevos() {
        val raros = AjustesEfecto(angulo = 33f, desde = -0.4f, hasta = 2.5f, curva = Curva.ELASTICO,
            bezier = Bezier(0.1f, 0.9f, 0.3f, -0.2f), grosor = 0.3f, suavidad = 0.05f, invertir = true, vertical = true)
        val conCentro = setOf(TipoEfectoVideo.CIRCULO_CERRAR, TipoEfectoVideo.CIRCULO_ABRIR, TipoEfectoVideo.REVELAR_HORIZONTAL)
        val viejos = TipoEfectoVideo.values().filter { it.ordinal <= TipoEfectoVideo.REVELAR_HORIZONTAL.ordinal }
        assertEquals(19, viejos.size)
        for (tipo in viejos) {
            val e = EfectoVideo(tipo, 100_000, 1_900_000, intensidad = 1.4f, rapidez = 1.3f, fundidoEntradaUs = 200_000)
            val a = clip(e)
            val b = clip(e.copy(ajustes = if (tipo in conCentro) raros else raros.copy(centroX = 0.3f, centroY = -0.2f)))
            for (n in 0L..60L) assertEquals("$tipo #$n", firma(escena(a, blur = true).cuadro(n)), firma(escena(b, blur = true).cuadro(n)))
        }
    }

    @Test
    fun otroCentroUOtroGrosorEsOtraClaseDeMascara() {
        // Un circulo centrado acaba en 1 s y empieza otro con el centro movido. En
        // el fotograma de 1 s, las muestras del primero no pueden leer su radio
        // como si fuera del segundo: copian el borde de la muestra mas cercana.
        val uno = EfectoVideo(TipoEfectoVideo.CIRCULO, 0, 1_000_000, ajustes = AjustesEfecto(desde = 1f, hasta = 0.2f, curva = Curva.LINEAL))
        val dos = EfectoVideo(TipoEfectoVideo.CIRCULO, 1_000_000, 2_000_000,
            ajustes = AjustesEfecto(desde = 0.2f, hasta = 1f, centroX = 0.3f, curva = Curva.LINEAL))
        val paso = escena(clip(uno, dos), blur = true).cuadroEn(1_002_000).clips.single()
        assertEquals(0.3f * W, paso.mascara!!.centroX, 0f)
        val b = paso.bordes
        assertEquals(8, b.size)
        for (i in 0..2) assertEquals(b[3], b[i], 0f)
        // Un anillo y un circulo con el mismo centro tampoco son la misma clase
        val anillo = dos.copy(ajustes = dos.ajustes.copy(centroX = 0f, grosor = 0.1f))
        val p2 = escena(clip(uno, anillo), blur = true).cuadroEn(1_002_000).clips.single()
        assertTrue(p2.mascara!!.grosor > 0f)
        for (i in 0..2) assertEquals(p2.bordes[3], p2.bordes[i], 0f)
    }

    @Test
    fun lasMuestrasSinAnilloLlevanElBordeQueNoRecorta() {
        // El sombreador no corta por dentro con un borde de SIN_BORDE: esas
        // muestras se ven enteras, sin anillo.
        val anillo = EfectoVideo(TipoEfectoVideo.CIRCULO, 1_000_000, 2_000_000, ajustes = AjustesEfecto(grosor = 0.1f))
        val paso = escena(clip(anillo), blur = true).cuadroEn(1_000_000).clips.single()
        assertTrue(paso.mascara!!.grosor > 0f)
        assertTrue(paso.bordes.take(4).all { it == Escena.SIN_BORDE })
        assertTrue(paso.bordes.drop(4).all { abs(it) < Escena.SIN_BORDE / 2f })
        val invertido = anillo.copy(ajustes = anillo.ajustes.copy(invertir = true))
        val p2 = escena(clip(invertido), blur = true).cuadroEn(1_000_000).clips.single()
        assertTrue(p2.bordes.take(4).all { it == -Escena.SIN_BORDE })
    }

    // ---- Mitades ----

    private fun mitades(a: AjustesEfecto = CatalogoEfectos.ajustesDe(TipoEfectoVideo.MITADES), inicio: Long = 0, fin: Long = 1_000_000) =
        EfectoVideo(TipoEfectoVideo.MITADES, inicio, fin, ajustes = a)

    @Test
    fun enReposoLasMitadesSonElBloqueSinEfecto() {
        val quietas = mitades(AjustesEfecto(desde = 0f, hasta = 0f))
        for (fondo in listOf(Fondo.NEGRO, Fondo.DESENFOCADO)) for (blur in listOf(false, true)) {
            val con = Clip(1, origen = foto, hastaUs = 3_000_000, efectos = listOf(quietas))
            val sin = con.copy(efectos = emptyList())
            for (n in 0L..40L) assertEquals("$fondo $blur #$n", firma(Escena(proyecto(sin, blur, fondo), 300, 400, medidor).cuadro(n)),
                firma(Escena(proyecto(con, blur, fondo), 300, 400, medidor).cuadro(n)))
        }
        // Las de por defecto acaban juntas: despues del tramo es el bloque entero
        val c = Clip(1, origen = foto, hastaUs = 3_000_000, efectos = listOf(mitades(fin = 500_000)))
        val e = Escena(proyecto(c, blur = true), 300, 400, medidor)
        val entero = Escena(proyecto(c.copy(efectos = emptyList()), blur = true), 300, 400, medidor)
        for (n in 16L..40L) assertEquals("#$n", firma(entero.cuadro(n)), firma(e.cuadro(n)))
        assertTrue(e.cuadro(5).pasos.any { it is PasoPartes })
    }

    @Test
    fun aMitadDeRecorridoLasDosPartesSeAlejanEnSentidosOpuestos() {
        val lineal = AjustesEfecto(angulo = 90f, desde = 0.5f, hasta = 0f, curva = Curva.LINEAL)
        fun partes(a: AjustesEfecto, encuadre: Encuadre = Encuadre()): List<PasoClip> {
            val c = Clip(1, origen = foto, hastaUs = 3_000_000, encuadre = encuadre, efectos = listOf(mitades(a)))
            val p = Escena(proyecto(c), 300, 400, medidor).cuadroEn(500_000).pasos.filterIsInstance<PasoPartes>().single()
            return p.partes
        }
        fun aMano(r1: RegionUv, e1: Encuadre, r2: RegionUv, e2: Encuadre, encuadre: Encuadre = Encuadre()): List<PasoClip> {
            val c = Clip(1, origen = foto, hastaUs = 3_000_000, encuadre = encuadre,
                piezas = listOf(PiezaVideo("a", r1, e1), PiezaVideo("b", r2, e2)))
            return Escena(proyecto(c), 300, 400, medidor).cuadroEn(500_000).pasos.filterIsInstance<PasoPartes>().single().partes
        }
        fun iguales(a: List<PasoClip>, b: List<PasoClip>) {
            assertEquals(b.map { it.recorteFuente }, a.map { it.recorteFuente })
            for (i in a.indices) assertArrayEquals(b[i].matrices.single(), a[i].matrices.single(), 0f)
        }
        // Corte horizontal, hacia abajo: la de arriba viene de arriba y la de abajo de abajo
        val h = partes(lineal)
        iguales(h, aMano(RegionUv(0f, 0f, 1f, 0.5f), Encuadre(y = -0.25f), RegionUv(0f, 0.5f, 1f, 1f), Encuadre(y = 0.25f)))
        // Corte vertical hacia la derecha
        val v = partes(lineal.copy(angulo = 0f, vertical = true))
        iguales(v, aMano(RegionUv(0f, 0f, 0.5f, 1f), Encuadre(x = -0.25f), RegionUv(0.5f, 0f, 1f, 1f), Encuadre(x = 0.25f)))
        // En espejo, la mitad que va a la izquierda del lienzo es la derecha de la fuente
        val espejo = Encuadre(espejo = true)
        iguales(partes(lineal.copy(angulo = 0f, vertical = true), espejo),
            aMano(RegionUv(0.5f, 0f, 1f, 1f), Encuadre(x = -0.25f), RegionUv(0f, 0f, 0.5f, 1f), Encuadre(x = 0.25f), espejo))
        // En diagonal se mueven en los dos ejes, en sentidos opuestos
        val d = partes(lineal.copy(angulo = 45f))
        val (ux, uy) = Escena.direccion(45f)
        iguales(d, aMano(RegionUv(0f, 0f, 1f, 0.5f), Encuadre(x = -0.25f * ux, y = -0.25f * uy),
            RegionUv(0f, 0.5f, 1f, 1f), Encuadre(x = 0.25f * ux, y = 0.25f * uy)))
        // El corte cae en medio de lo que se ve del bloque recortado
        val recortado = partes(lineal, Encuadre(recorte = Recorte(arriba = 0.2f, abajo = 0.1f)))
        assertEquals(0.2f + 0.7f / 2f, recortado[0].recorteFuente!!.abajo, 1e-6f)
        assertEquals(recortado[0].recorteFuente!!.abajo, recortado[1].recorteFuente!!.arriba, 0f)
        // La intensidad multiplica el recorrido
        val flojo = Clip(1, origen = foto, hastaUs = 3_000_000, efectos = listOf(mitades(lineal).copy(intensidad = 0.5f)))
        val pf = Escena(proyecto(flojo), 300, 400, medidor).cuadroEn(500_000).pasos.filterIsInstance<PasoPartes>().single().partes
        iguales(pf, aMano(RegionUv(0f, 0f, 1f, 0.5f), Encuadre(y = -0.125f), RegionUv(0f, 0.5f, 1f, 1f), Encuadre(y = 0.125f)))
    }

    @Test
    fun lasMitadesConFondoDesenfocadoYEnUnaCapa() {
        val c = Clip(1, origen = foto, hastaUs = 3_000_000, efectos = listOf(mitades()))
        val pasos = Escena(proyecto(c, fondo = Fondo.DESENFOCADO), 300, 400, medidor).cuadroEn(200_000).pasos
        // Entre las mitades se ve el fondo: va detras, forzado
        val fondo = pasos.filterIsInstance<PasoClip>().single()
        assertTrue(fondo.esFondo)
        assertTrue(pasos.indexOf(fondo) < pasos.indexOfFirst { it is PasoPartes })
        val capa = Proyecto(principal = listOf(Clip(2, origen = video)), capas = listOf(Capa(1_000_000, 1, c, Mezcla.PANTALLA)),
            fondo = Fondo.NEGRO, salida = AjustesSalida(desenfoqueMovimiento = true))
        val partes = Escena(capa, 300, 400, medidor).cuadroEn(1_200_000).pasos.filterIsInstance<PasoPartes>().single()
        assertEquals(Mezcla.PANTALLA, partes.mezcla)
        assertEquals(2, partes.partes.size)
        assertTrue(partes.partes.all { it.clip.id == 1L && it.muestras == 8 })
        // Antes de su tramo la capa se ve entera
        assertTrue(Escena(capa.copy(capas = listOf(Capa(1_000_000, 1, c.copy(efectos = listOf(mitades(inicio = 500_000, fin = 900_000))), Mezcla.PANTALLA))),
            300, 400, medidor).cuadroEn(1_200_000).pasos.none { it is PasoPartes })
    }

    @Test
    fun enLosBordesDelTramoNingunaMuestraSaleEntera() {
        val c = Clip(1, origen = foto, hastaUs = 3_000_000,
            efectos = listOf(mitades(AjustesEfecto(angulo = 90f, desde = 0.5f, hasta = 0f, curva = Curva.LINEAL), 300_000, 800_000)))
        val e = Escena(proyecto(c, blur = true), 300, 400, medidor)
        val entera = Escena(proyecto(c.copy(efectos = emptyList()), blur = true), 300, 400, medidor).cuadroEn(300_000).clips.single().matrices.single()
        // El fotograma del comienzo tiene 4 muestras antes del tramo: todas ven las
        // mitades separadas como al empezar, ninguna el bloque entero.
        val inicio = e.cuadroEn(300_000).pasos.filterIsInstance<PasoPartes>().single().partes
        for (p in inicio) {
            for (i in 0..3) assertArrayEquals(p.matrices[0], p.matrices[i], 0f)
            for (i in 0..7) assertFalse(p.matrices[i].contentEquals(entera))
        }
        // Las de arriba suben y las de abajo bajan, cada vez menos, sin saltos
        val arriba = inicio[0].matrices.map { it[7] }
        val abajo = inicio[1].matrices.map { it[7] }
        for (i in 1..7) {
            assertTrue(arriba[i] <= arriba[i - 1])
            assertTrue(abajo[i] >= abajo[i - 1])
        }
        // Al acabar: todas casi juntas, y el fotograma siguiente es el bloque entero
        val fin = e.cuadroEn(800_000).pasos.filterIsInstance<PasoPartes>().singleOrNull()?.partes
        if (fin != null) for (p in fin) for (i in 4..7) assertArrayEquals(p.matrices.last(), p.matrices[i], 0f)
        assertTrue(e.cuadroEn(800_000 + 33_333).pasos.none { it is PasoPartes })
    }

    @Test
    fun conPartesHechasAManoLasMitadesNoHacenNada() {
        val piezas = listOf(PiezaVideo("a", RegionUv(derecha = 0.3f), Encuadre(x = 0.1f)), PiezaVideo("b", RegionUv(izquierda = 0.3f)))
        val c = Clip(1, origen = foto, hastaUs = 3_000_000, piezas = piezas)
        val con = c.copy(efectos = listOf(mitades()))
        for (n in 0L..20L) assertEquals(firma(Escena(proyecto(c, true), 300, 400, medidor).cuadro(n)),
            firma(Escena(proyecto(con, true), 300, 400, medidor).cuadro(n)))
    }

    @Test
    fun conVariasMitadesMandaLaUltimaActiva() {
        val vertical = mitades(AjustesEfecto(angulo = 0f, desde = 0.5f, hasta = 0f, vertical = true))
        val c = Clip(1, origen = foto, hastaUs = 3_000_000, efectos = listOf(mitades(), vertical))
        val partes = Escena(proyecto(c), 300, 400, medidor).cuadroEn(200_000).pasos.filterIsInstance<PasoPartes>().single().partes
        assertEquals(RegionUv(0f, 0f, 0.5f, 1f), partes[0].recorteFuente)
        val apagada = c.copy(efectos = listOf(mitades(), vertical.copy(activo = false)))
        val p2 = Escena(proyecto(apagada), 300, 400, medidor).cuadroEn(200_000).pasos.filterIsInstance<PasoPartes>().single().partes
        assertEquals(RegionUv(0f, 0f, 1f, 0.5f), p2[0].recorteFuente)
    }

    // ---- Catalogo y combinaciones ----

    @Test
    fun cadaTipoEstaEnUnGrupoMenosLaAnimacion() {
        val todos = CatalogoEfectos.grupos.flatMap { it.second }
        assertEquals(todos.size, todos.toSet().size)
        assertEquals(TipoEfectoVideo.values().toSet() - TipoEfectoVideo.ANIMACION, todos.toSet())
        for (tipo in TipoEfectoVideo.values()) {
            val p = CatalogoEfectos.parametros(tipo)
            assertEquals(tipo.name, Parametro.DESDE in p, CatalogoEfectos.rango(tipo) != null)
            assertEquals(tipo.name, tipo !in setOf(TipoEfectoVideo.CIRCULO, TipoEfectoVideo.BARRIDO), Parametro.INTENSIDAD in p)
            assertTrue(CatalogoEfectos.descripcion(tipo).isNotBlank())
            assertTrue(CatalogoEfectos.duracionSugeridaUs(tipo) > 0)
        }
    }

    @Test
    fun unEfectoNuevoLlevaLosAjustesDeSuTipoYCabeEnElBloque() {
        val m = CatalogoEfectos.nuevo(TipoEfectoVideo.MOVER, 1_000_000, 3_000_000, 7)
        assertEquals(1_000_000L, m.inicioUs)
        assertEquals(1_500_000L, m.finUs)
        assertEquals(7L, m.id)
        assertEquals(AjustesEfecto(angulo = 180f, desde = 1f, hasta = 0f, curva = Curva.SALIDA), m.ajustes)
        // Al final del bloque se adelanta para conservar su duracion
        val tarde = CatalogoEfectos.nuevo(TipoEfectoVideo.LATIGAZO, 2_900_000, 3_000_000, 1)
        assertEquals(2_700_000L to 3_000_000L, tarde.inicioUs to tarde.finUs)
        // Si el bloque es mas corto, ocupa todo el bloque
        val corto = CatalogoEfectos.nuevo(TipoEfectoVideo.ZOOM, 50_000, 100_000, 1)
        assertEquals(0L to 100_000L, corto.inicioUs to corto.finUs)
    }

    @Test
    fun cadaCombinacionAgregaEfectosIndependientesDeUnMismoGrupo() {
        val viejo = EfectoVideo(TipoEfectoVideo.RGB, 0, 400_000)
        val c = Clip(1, origen = video, efectos = listOf(viejo))
        val dur = c.duracionUs
        val esperados = mapOf("Mitades que se juntan" to 2, "Circulo que se abre" to 1, "Anillo que se abre" to 1,
            "Latigazo con desenfoque" to 3, "Sacudida con desenfoque" to 2)
        assertEquals(esperados.keys, CatalogoEfectos.combinaciones.map { it.nombre }.toSet())
        for (comb in CatalogoEfectos.combinaciones) {
            val una = CatalogoEfectos.aplicar(comb, c, 1_000_000)
            val dos = CatalogoEfectos.aplicar(comb, una, 1_000_000)
            val nuevos = una.efectos.drop(1)
            assertEquals(comb.nombre, esperados.getValue(comb.nombre), nuevos.size)
            // El que ya estaba sigue igual, con un id para poder elegirlo
            assertEquals(viejo, una.efectos.first().copy(id = 0))
            assertEquals(dos.efectos.size, dos.efectos.map { it.id }.toSet().size)
            assertTrue(dos.efectos.all { it.id > 0 })
            assertTrue(nuevos.all { it.grupo == "${comb.nombre} 1" })
            assertTrue(dos.efectos.drop(una.efectos.size).all { it.grupo == "${comb.nombre} 2" })
            assertTrue(nuevos.all { it.inicioUs == 1_000_000L && it.finUs <= dur && it.finUs > it.inicioUs })
            // Se mueve, se recorta y se quita uno sin tocar los demas
            val primero = nuevos.first()
            val movido = una.cambiarEfecto(primero.id) { it.desplazado(200_000, dur) }
            assertEquals(1_200_000L, movido.efectos.single { it.id == primero.id }.inicioUs)
            assertEquals(nuevos.drop(1), movido.efectos.drop(2))
            val recortado = una.cambiarEfecto(primero.id) { it.conRango(it.inicioUs + 33_333, it.finUs) }
            assertEquals(nuevos.drop(1), recortado.efectos.drop(2))
            assertEquals(una.efectos.size - 1, una.sinEfecto(primero.id).efectos.size)
            assertTrue(una.sinEfecto(primero.id).efectos.none { it.id == primero.id })
            // Todo el grupo a la vez, sin salirse del bloque
            val grupo = una.moverGrupo("${comb.nombre} 1", 10_000_000)
            val fin = nuevos.maxOf { it.finUs }
            assertTrue(grupo.efectos.drop(1).all { it.finUs <= dur })
            assertEquals(dur, grupo.efectos.drop(1).maxOf { it.finUs })
            assertTrue(grupo.efectos.drop(1).all { it.inicioUs == 1_000_000L + dur - fin })
            assertEquals(una.efectos.first(), grupo.efectos.first())
        }
    }

    /** Cuanto se ve de la imagen en un punto (px desde el centro del lienzo), con la cuenta del sombreador CAPA. */
    private fun seVe(e: Efecto, x: Float, y: Float): Float {
        val m = e.mascara ?: return 1f
        val qx = x - m.centroX
        val qy = y - m.centroY
        val d = if (m.radial) sqrt(qx * qx + qy * qy) else qx * m.dirX + qy * m.dirY
        var v = ((e.borde - d) / m.suave + 0.5f).coerceIn(0f, 1f)
        if (m.grosor > 0f && abs(e.borde) < 15_000f) v *= ((d - (e.borde - m.grosor)) / m.suave + 0.5f).coerceIn(0f, 1f)
        return if (m.invertida) 1f - v else v
    }

    @Test
    fun elAnilloQueSeAbreDejaVerLoDeDebajoYAcabaConLaImagenEntera() {
        val c = Clip(1, origen = video)
        val ef = CatalogoEfectos.aplicar(CatalogoEfectos.combinaciones.single { it.nombre == "Anillo que se abre" }, c, 0).efectos.single()
        val conAnillo = c.copy(efectos = listOf(ef))
        val esquinas = listOf(-W / 2f to -H / 2f, W / 2f to -H / 2f, -W / 2f to H / 2f, W / 2f to H / 2f)
        val puntos = esquinas + (0f to 0f)
        // Al empezar no tapa nada
        val inicio = efecto(conAnillo, 0)
        for ((x, y) in puntos) assertEquals("($x, $y) al empezar", 1f, seVe(inicio, x, y), 0f)
        // A mitad: el centro ya se ve y una banda hacia las esquinas deja ver lo de debajo
        val mitad = efecto(conAnillo, 300_000)
        assertEquals(1f, seVe(mitad, 0f, 0f), 0f)
        val diagonal = (0..200).map { n -> seVe(mitad, W / 2f * n / 200f, H / 2f * n / 200f) }
        assertTrue(diagonal.any { it == 0f })
        assertEquals(1f, diagonal.first(), 0f)
        // Al acabar el anillo ha salido por las esquinas: la imagen entera,
        // igual que justo despues del tramo (sin aparecer de golpe)
        for (t in longArrayOf(570_000, 590_000, 599_999)) {
            val e = efecto(conAnillo, t)
            for ((x, y) in puntos) assertEquals("($x, $y) en $t", 1f, seVe(e, x, y), 0f)
            for (n in 0..200) assertEquals(1f, seVe(e, W / 2f * n / 200f, H / 2f * n / 200f), 0f)
        }
        assertNull(efecto(conAnillo, 600_000).mascara)
    }

    @Test
    fun lasCombinacionesLlevanLoQueDicen() {
        val c = Clip(1, origen = video)
        fun de(nombre: String) = CatalogoEfectos.aplicar(CatalogoEfectos.combinaciones.single { it.nombre == nombre }, c, 0).efectos
        val m = de("Mitades que se juntan")
        assertEquals(listOf(TipoEfectoVideo.MITADES, TipoEfectoVideo.DESENFOQUE_DIRECCIONAL), m.map { it.tipo })
        assertEquals(AjustesEfecto(angulo = 90f, desde = 0.5f, hasta = 0f, curva = Curva.SALIDA), m[0].ajustes)
        assertEquals(90f, m[1].ajustes.angulo, 0f)
        assertEquals(500_000L, m[1].fundidoSalidaUs)
        val anillo = de("Anillo que se abre").single()
        assertEquals(600_000L, anillo.duracionUs)
        assertEquals(AjustesEfecto(desde = 0f, hasta = 1.15f, grosor = 0.15f, curva = Curva.SALIDA, invertir = true), anillo.ajustes)
        val l = de("Latigazo con desenfoque")
        assertEquals(listOf(TipoEfectoVideo.LATIGAZO, TipoEfectoVideo.DESENFOQUE_DIRECCIONAL, TipoEfectoVideo.BORDES_ESPEJO), l.map { it.tipo })
        assertTrue(l.all { it.inicioUs == 0L && it.finUs == 300_000L })
        assertEquals(1.5f, l[1].intensidad, 0f)
        assertEquals(150_000L to 150_000L, l[1].fundidoEntradaUs to l[1].fundidoSalidaUs)
        // La sacudida de la combinacion es la de siempre, sin tocar
        val s = de("Sacudida con desenfoque")
        val sola = Clip(1, origen = video, efectos = listOf(EfectoVideo(TipoEfectoVideo.SACUDIDA, 0, 500_000)))
        val combinada = Clip(1, origen = video, efectos = listOf(s[0]))
        for (t in 0L until 500_000L step 7_919L) assertEquals(efecto(sola, t), efecto(combinada, t))
        assertEquals(0.6f, s[1].intensidad, 0f)
        assertEquals(166_666L, s[1].fundidoEntradaUs)
        // En un bloque mas corto que la combinacion, todo cabe dentro
        val corto = Clip(1, origen = foto, hastaUs = 200_000)
        val dentro = CatalogoEfectos.aplicar(CatalogoEfectos.combinaciones.first(), corto, 150_000).efectos
        assertTrue(dentro.all { it.inicioUs == 0L && it.finUs == 200_000L })
    }

    @Test
    fun losIdsSonUnicosYLosViejosRecibenUno() {
        val c = Clip(1, origen = video)
        assertEquals(1L, c.siguienteIdEfecto())
        val e = EfectoVideo(TipoEfectoVideo.FLASH, 0, 100_000)
        val conIds = c.copy(efectos = listOf(e, e.copy(id = 5), e.copy(id = 5), e)).conIdsEfectos()
        assertEquals(listOf(6L, 5L, 7L, 8L), conIds.efectos.map { it.id })
        assertEquals(listOf(e, e, e, e), conIds.efectos.map { it.copy(id = 0) })
        assertSame(conIds, conIds.conIdsEfectos())
        assertEquals(9L, conIds.siguienteIdEfecto())
    }

    // ---- Mover y recortar conservando el reloj ----

    @Test
    fun moverUnEfectoMueveSuResultadoExactamenteEnElTiempo() {
        val puntos = listOf(ProgresoEfecto(0, 0f), ProgresoEfecto(300_000, 0.9f), ProgresoEfecto(700_000, 0.2f), ProgresoEfecto(1_100_000, 1f))
        val tipos = listOf(TipoEfectoVideo.MOVER, TipoEfectoVideo.CIRCULO_CERRAR, TipoEfectoVideo.SACUDIDA, TipoEfectoVideo.FLASH,
            TipoEfectoVideo.LATIGAZO, TipoEfectoVideo.BARRIDO, TipoEfectoVideo.CIRCULO)
        for (tipo in tipos) {
            val e = EfectoVideo(tipo, 500_000, 1_500_000, intensidad = 1.2f, rapidez = 1.7f, desfaseUs = -33_333,
                fundidoEntradaUs = 100_000, fundidoSalidaUs = 200_000, duracionBaseUs = 1_100_000, progresos = puntos,
                ajustes = CatalogoEfectos.ajustesDe(tipo).copy(angulo = 30f))
            val c = clip(e)
            for (d in listOf(1_234_567L, -400_000L, 17L)) {
                val m = c.cambiarEfecto(0) { it.desplazado(d, c.duracionUs) }
                val movido = m.efectos.single()
                assertEquals(e.duracionUs, movido.duracionUs)
                assertEquals(e.desfaseUs, movido.desfaseUs)
                assertEquals(e.progresos, movido.progresos)
                var t = e.inicioUs - 50_000
                while (t < e.finUs + 50_000) {
                    assertEquals("$tipo $d $t", efecto(c, t), efecto(m, t + d))
                    t += 3_331
                }
            }
            // Mas alla del bloque se queda en el borde, con su duracion
            val alFinal = e.desplazado(10_000_000, c.duracionUs)
            assertEquals(c.duracionUs, alFinal.finUs)
            assertEquals(e.duracionUs, alFinal.duracionUs)
            // Recortar no mueve el reloj: lo que queda se ve igual que antes
            val recortado = clip(e.conRango(700_000, 1_200_000))
            var t = 700_000L
            while (t < 1_200_000) {
                assertEquals("$tipo $t", efecto(c, t), efecto(recortado, t))
                t += 4_999
            }
            // Y ampliar despues de mover sigue el mismo reloj
            val movidoYAmpliado = clip(e.desplazado(250_000, c.duracionUs).conRango(650_000, 1_750_000))
            t = 750_000L
            while (t < 1_750_000) {
                assertEquals("$tipo $t", efecto(c, t - 250_000), efecto(movidoYAmpliado, t))
                t += 4_999
            }
        }
    }
}
