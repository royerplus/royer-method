package com.royermethod.app.editor.modelo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EdicionTest {

    private val video = Origen("v", "v", TipoOrigen.VIDEO, 1920, 1080, duracionUs = 4_000_000, fps = 30f)
    private val foto = Origen("f", "f", TipoOrigen.IMAGEN, 1000, 1000)

    @Test
    fun dividirNoPierdeNiRepiteNada() {
        val (p, ids) = Edicion.agregarPrincipal(Proyecto(), listOf(video))
        val antes = Linea.duracion(p)
        val (q, nuevo) = Edicion.dividir(p, ids[0], 1_500_000)!!
        assertEquals(2, q.principal.size)
        assertEquals(antes, Linea.duracion(q))
        val (a, b) = q.principal
        assertEquals(1_500_000L, a.hastaUs)
        assertEquals(1_500_000L, b.desdeUs)
        assertEquals(nuevo, b.id)
        // El fotograma del archivo en cada instante es el mismo que antes de dividir
        val e1 = Linea.principalEn(p.principal, Linea.inicios(p.principal), 2_000_000)!!
        val e2 = Linea.principalEn(q.principal, Linea.inicios(q.principal), 2_000_000)!!
        assertEquals(e1.primero.tiempoArchivo(e1.localPrimero), e2.primero.tiempoArchivo(e2.localPrimero))
    }

    @Test
    fun dividirConVelocidadCortaEnElSitioDelArchivo() {
        val (p, ids) = Edicion.agregarPrincipal(Proyecto(), listOf(video))
        val r = Edicion.cambiar(p, ids[0]) { it.copy(velocidad = 2f) }
        val (q, _) = Edicion.dividir(r, ids[0], 1_000_000)!!
        assertEquals(2_000_000L, q.principal[0].hastaUs)
        assertEquals(1_000_000L, q.principal[1].duracionUs)
    }

    @Test
    fun dividirMuyCercaDelBordeNoHaceNada() {
        val (p, ids) = Edicion.agregarPrincipal(Proyecto(), listOf(video))
        assertNull(Edicion.dividir(p, ids[0], 10_000))
    }

    @Test
    fun laTransicionSeQuedaConLaSegundaMitad() {
        val (p, ids) = Edicion.agregarPrincipal(Proyecto(), listOf(video, video))
        val r = Edicion.cambiar(p, ids[0]) { it.copy(transicion = Transicion.LINEA, entrada = listOf(Movimiento(Animacion.APARECER))) }
        val (q, _) = Edicion.dividir(r, ids[0], 1_000_000)!!
        assertEquals(Transicion.NINGUNA, q.principal[0].transicion)
        assertEquals(listOf(Movimiento(Animacion.APARECER)), q.principal[0].entrada)
        assertEquals(Transicion.LINEA, q.principal[1].transicion)
        assertTrue(q.principal[1].entrada.isEmpty())
    }

    @Test
    fun insertarJuntoAlCorteMasCercano() {
        val (p, _) = Edicion.agregarPrincipal(Proyecto(), listOf(video, video))
        // En el primer cuarto del segundo clip: delante de el
        val (q, ids) = Edicion.agregarPrincipal(p, listOf(foto), 4_500_000)
        assertEquals(ids[0], q.principal[1].id)
    }

    @Test
    fun lasCapasNoSePisan() {
        var p = Proyecto()
        p = Edicion.agregarSuperpuesto(p, foto, 0).first
        p = Edicion.agregarTexto(p, "hola", 1_000_000).first
        assertEquals(listOf(0, 1), p.capas.map { it.fila })
        // Moverla a una fila ocupada la manda a la siguiente libre
        val id = p.capas[1].id
        val q = Edicion.moverCapa(p, id, 500_000, 0)
        assertTrue(q.capas.first { it.id == id }.fila != 0)
    }

    @Test
    fun recortarMueveLosFotogramasClave() {
        val (p, ids) = Edicion.agregarPrincipal(Proyecto(), listOf(video))
        var q = Edicion.ponerClave(p, ids[0], 2_000_000, Encuadre(escala = 2f))
        // El primer clave fija tambien el inicio
        assertEquals(listOf(0L, 2_000_000L), q.principal[0].claves.map { it.tiempoUs })
        val antes = q.principal[0].encuadreEn(1_000_000)
        q = Edicion.recortar(q, ids[0], 1_000_000, 4_000_000)
        // La clave del 0 se sustituye por una en el nuevo principio, con el encuadre de ahi
        assertEquals(listOf(0L, 1_000_000L), q.principal[0].claves.map { it.tiempoUs })
        assertEquals(antes.escala, q.principal[0].encuadreEn(0).escala, 0.001f)
    }

    @Test
    fun dividirNoHaceSaltarElEncuadre() {
        val (p, ids) = Edicion.agregarPrincipal(Proyecto(), listOf(video))
        val q = Edicion.ponerClave(p, ids[0], 2_000_000, Encuadre(escala = 2f))
        val enCorte = q.principal[0].encuadreEn(1_000_000).escala
        val r = Edicion.dividir(q, ids[0], 1_000_000)!!.first
        assertEquals(enCorte, r.principal[0].encuadreEn(r.principal[0].duracionUs).escala, 0.01f)
        assertEquals(enCorte, r.principal[1].encuadreEn(0).escala, 0.01f)
        assertEquals(2f, r.principal[1].encuadreEn(1_000_000).escala, 0.01f)
    }

    @Test
    fun pasarDeCapaAPrincipalYVolver() {
        var p = Edicion.agregarPrincipal(Proyecto(), listOf(video)).first
        val (q, id) = Edicion.agregarSuperpuesto(p, video, 1_000_000)
        p = Edicion.aPrincipal(q, id)
        assertEquals(2, p.principal.size)
        assertTrue(p.capas.isEmpty())
        p = Edicion.aCapa(p, id)
        assertEquals(1, p.principal.size)
        assertNotNull(p.capas.firstOrNull { it.id == id })
    }
}
