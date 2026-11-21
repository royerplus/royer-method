package com.royermethod.app.editor.ui

import com.royermethod.app.editor.modelo.*
import org.junit.Assert.*
import org.junit.Test

class OrdenCapasTest {
    private fun texto(id: Long) = Clip(id, texto = Texto("T$id"), hastaUs = 1_000_000)

    /** Filas 0, 1 (dos bloques), 2 (solo un tramo de ajustes) y 4. */
    private val p = Proyecto(
        capas = listOf(
            Capa(0, 0, texto(1)),
            Capa(2_000_000, 1, texto(3)), Capa(0, 1, texto(2)),
            Capa(0, 2, Clip(4, esAjuste = true, hastaUs = 1_000_000)),
            Capa(0, 4, texto(5))
        ),
        siguienteId = 6
    )

    private fun orden(q: Proyecto) = filasOrden(q).map { f -> f.capas.map { it.id } }
    private fun sitios(q: Proyecto) = q.capas.map { it.id to it.inicioUs }.toSet()

    @Test fun lasFilasVanDeDelanteADetrasYLosTramosDeAjustesNoCuentan() {
        assertEquals(listOf(listOf(5L), listOf(2L, 3L), listOf(1L)), orden(p))
        assertEquals(listOf(4, 1, 0), filasOrden(p).map { it.fila })
        // La fila se reconoce por su bloque mas antiguo aunque cambie de sitio
        assertEquals(listOf(5L, 2L, 1L), filasOrden(p).map { it.clave })
    }

    @Test fun llevarLaDeDetrasAlFrenteConservaElOrdenDeLasDemas() {
        val q = moverFila(p, clave = 1, destino = 0)
        assertEquals(listOf(listOf(1L), listOf(5L), listOf(2L, 3L)), orden(q))
        // Ni los tiempos ni los bloques cambian, y el tramo de ajustes sigue en su fila
        assertEquals(sitios(p), sitios(q))
        assertEquals(2, q.capas.single { it.id == 4L }.fila)
    }

    @Test fun losBloquesDeUnaFilaSeMuevenJuntos() {
        val q = moverFila(p, clave = 2, destino = 2)
        assertEquals(listOf(listOf(5L), listOf(1L), listOf(2L, 3L)), orden(q))
        assertEquals(q.capas.single { it.id == 2L }.fila, q.capas.single { it.id == 3L }.fila)
    }

    @Test fun unDestinoFueraDeLaListaEsLaOrilla() {
        assertEquals(listOf(listOf(2L, 3L), listOf(1L), listOf(5L)), orden(moverFila(p, clave = 5, destino = 99)))
        assertEquals(listOf(listOf(1L), listOf(5L), listOf(2L, 3L)), orden(moverFila(p, clave = 1, destino = -3)))
    }

    @Test fun sinCambioDevuelveElMismoProyecto() {
        assertSame(p, moverFila(p, clave = 5, destino = 0))
        assertSame(p, moverFila(p, clave = 77, destino = 0))
        assertSame(p, moverFila(p, clave = 4, destino = 0))
    }

    @Test fun laCasillaEnsenaElBloqueBajoElCabezalOElMasCercano() {
        val fila = filasOrden(p).single { it.clave == 2L }
        assertEquals(2L, fila.enCabezal(500_000).id)
        assertEquals(3L, fila.enCabezal(2_500_000).id)
        // En el hueco entre los dos, el que quede mas cerca
        assertEquals(2L, fila.enCabezal(1_400_000).id)
        assertEquals(3L, fila.enCabezal(1_900_000).id)
        assertEquals(3L, fila.enCabezal(9_000_000).id)
    }

    @Test fun losRotulosPonenLasTildesSoloEnPalabrasEnteras() {
        assertEquals("Transición", Rotulos.de("Transicion"))
        assertEquals("Línea de luz", Rotulos.de("Linea de luz"))
        assertEquals("Más rápido", Rotulos.de("Mas rapido"))
        // "Lineal" no es "Linea" con una letra mas, y lo que ya esta bien se queda
        assertEquals("Lineal", Rotulos.de("Lineal"))
        assertEquals("Zoom 3D", Rotulos.de("Zoom 3D"))
        assertEquals("Máscara", Rotulos.de("Máscara"))
    }
}
