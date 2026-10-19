package com.royermethod.app.editor.exportar

import java.nio.ByteBuffer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class SincronizacionVideoTest {
    private val avc = "video/avc"
    private val hevc = "video/hevc"

    private fun avcc(vararg nals: ByteArray): ByteBuffer {
        val b = ByteBuffer.allocate(nals.sumOf { it.size + 4 })
        nals.forEach { b.putInt(it.size); b.put(it) }
        return b.apply { flip() }
    }

    private fun comprobar(mime: String, b: ByteBuffer) = SincronizacionVideo.esClave(mime, b, 0, b.limit())

    @Test
    fun soloLosIdrAvcSonPuntosDeAcceso() {
        assertTrue(comprobar(avc, avcc(byteArrayOf(0x65, 0x01))))
        assertFalse(comprobar(avc, avcc(byteArrayOf(0x41, 0x01))))
        assertFalse(comprobar(avc, avcc(byteArrayOf(0x01, 0x01))))
        assertTrue(comprobar(avc, avcc(byteArrayOf(0x67, 0x01), byteArrayOf(0x68, 0x01), byteArrayOf(0x65, 0x01))))
        assertFalse(comprobar(avc, avcc(byteArrayOf(0x67, 0x01), byteArrayOf(0x68, 0x01))))
    }

    @Test
    fun hevcAceptaSoloIrapDe16A21() {
        for (tipo in 0..31) {
            val esClave = comprobar(hevc, avcc(byteArrayOf((tipo shl 1).toByte(), 1)))
            assertEquals("tipo HEVC $tipo", tipo in 16..21, esClave)
        }
    }

    @Test
    fun admiteAnnexBConStartCodesDeTresYCuatroBytes() {
        val datos = ByteBuffer.wrap(byteArrayOf(0, 0, 0, 1, 0x67, 2, 0, 0, 1, 0x65, 3))
        assertTrue(comprobar(avc, datos))
        assertFalse(comprobar(avc, ByteBuffer.wrap(byteArrayOf(0, 0, 1, 0x41, 3))))
        assertTrue(comprobar(hevc, ByteBuffer.wrap(byteArrayOf(0, 0, 1, 0x26, 1, 3))))
    }

    @Test
    fun largosQueParecenStartCodeNoSeConfundenConAnnexB() {
        val nal = ByteArray(256) { 2 }.also { it[0] = 0x65 }
        assertTrue(comprobar(avc, avcc(nal)))
        nal[0] = 0x41
        assertFalse(comprobar(avc, avcc(nal)))
    }

    @Test
    fun respetaOffsetTamanoYEstadoDelBuffer() {
        val datos = ByteBuffer.allocateDirect(20).apply {
            put(byteArrayOf(9, 9, 9))
            putInt(2); put(byteArrayOf(0x65, 3))
            putInt(2); put(byteArrayOf(0x41, 3))
            limit(15); position(11)
        }
        assertTrue(SincronizacionVideo.esClave(avc, datos, 3, 6))
        assertFalse(SincronizacionVideo.esClave(avc, datos, 9, 6))
        assertEquals(11, datos.position())
        assertEquals(15, datos.limit())
        assertTrue(SincronizacionVideo.esClave(avc, datos.asReadOnlyBuffer(), 3, 6))
    }

    @Test
    fun rechazaBuffersIncompletosFueraDeLimitesYCabecerasInvalidas() {
        val datos = avcc(byteArrayOf(0x65, 1))
        assertFalse(SincronizacionVideo.esClave(avc, datos, -1, 3))
        assertFalse(SincronizacionVideo.esClave(avc, datos, 0, 7))
        assertFalse(SincronizacionVideo.esClave(avc, datos, Int.MAX_VALUE, 2))
        assertFalse(SincronizacionVideo.esClave(avc, datos, 0, 0))
        assertFalse(comprobar(avc, ByteBuffer.wrap(byteArrayOf(0, 0, 0, 8, 0x65))))
        assertFalse(comprobar(avc, avcc(byteArrayOf(0xe5.toByte(), 1))))
        assertFalse(comprobar(hevc, avcc(byteArrayOf(0x26))))
        assertFalse(comprobar(hevc, avcc(byteArrayOf(0x26, 0))))
        assertFalse(comprobar("audio/mp4a-latm", datos))
    }

    @Test
    fun tiposDevuelveCadaNalEnOrden() {
        val b = avcc(byteArrayOf(0x40, 1), byteArrayOf(0x42, 1), byteArrayOf(0x44, 1), byteArrayOf(0x26, 1))
        assertEquals(listOf(32, 33, 34, 19), SincronizacionVideo.tipos(hevc, b, 0, b.limit()))
        assertEquals(19, SincronizacionVideo.tipoImagen(hevc, listOf(32, 33, 34, 19)))
        assertEquals(5, SincronizacionVideo.tipoImagen(avc, listOf(7, 8, 5)))
        assertEquals(null, SincronizacionVideo.tipoImagen(avc, listOf(7, 8)))
        assertEquals(null, SincronizacionVideo.tipos(avc, avcc(byteArrayOf(0xe5.toByte(), 1)), 0, 6))
    }

    /** Un paquete HEVC de una sola imagen del [tipo] dado. */
    private fun imagenHevc(tipo: Int) = avcc(byteArrayOf((tipo shl 1).toByte(), 1, 0x55))

    private fun colgados(mime: String, paquetes: List<Pair<Long, ByteBuffer>>): Int {
        val v = VigiaColgados(mime)
        paquetes.forEach { (t, b) -> v.ver(b, 0, b.limit(), t) }
        return v.cuantos
    }

    /**
     * Lo que salio en la exportacion de Royer del 2026-10-18 entre 6,6 y 7,9 s
     * (H.265, un B entre cada dos P), en el orden del archivo: pts en
     * microsegundos y tipo de NAL. En los cortes de 6,867 y 7,333 se pidio un
     * fotograma completo y el codificador mando detras del IDR el B pendiente
     * (TRAIL_N, apoyado en fotogramas de antes del IDR). En el de 7,833 cayo
     * en el sitio de un B y salio bien.
     */
    private val exportacionDeRoyer = listOf(
        6_600_000L to 1, 6_566_667L to 0, 6_666_667L to 1, 6_633_333L to 0, 6_733_333L to 1, 6_700_000L to 0,
        6_800_000L to 1, 6_766_667L to 0,
        6_866_667L to 19, 6_833_333L to 0,
        6_933_333L to 1, 6_900_000L to 0, 7_000_000L to 1, 6_966_667L to 0, 7_066_667L to 1, 7_033_333L to 0,
        7_133_333L to 1, 7_100_000L to 0, 7_200_000L to 1, 7_166_667L to 0, 7_266_667L to 1, 7_233_333L to 0,
        7_333_333L to 19, 7_300_000L to 0,
        7_400_000L to 1, 7_366_667L to 0, 7_466_667L to 1, 7_433_333L to 0, 7_533_333L to 1, 7_500_000L to 0,
        7_600_000L to 1, 7_566_667L to 0, 7_666_667L to 1, 7_633_333L to 0, 7_733_333L to 1, 7_700_000L to 0,
        7_800_000L to 1, 7_766_667L to 0,
        7_833_333L to 19,
        7_900_000L to 1, 7_866_667L to 0
    )

    @Test
    fun cuentaLosBColgadosDeLaExportacionDeRoyer() {
        assertEquals(2, colgados(hevc, exportacionDeRoyer.map { (t, tipo) -> t to imagenHevc(tipo) }))
    }

    @Test
    fun unGrupoNormalConBNoTieneColgados() {
        // IDR, P, B, P, B... cada B se ensena antes que su P pero despues del IDR
        val normal = listOf(0L to 19, 66_667L to 1, 33_333L to 0, 133_333L to 1, 100_000L to 0)
        assertEquals(0, colgados(hevc, normal.map { (t, tipo) -> t to imagenHevc(tipo) }))
    }

    @Test
    fun losAnterioresQueLaNormaPermiteNoCuentan() {
        // RADL tras IDR_W_RADL y RASL tras CRA: legales
        assertEquals(0, colgados(hevc, listOf(100_000L to imagenHevc(19), 66_667L to imagenHevc(7), 33_333L to imagenHevc(6))))
        assertEquals(0, colgados(hevc, listOf(100_000L to imagenHevc(21), 66_667L to imagenHevc(9), 33_333L to imagenHevc(6))))
        // IDR_N_LP no admite ninguno, e IDR_W_RADL no admite RASL
        assertEquals(1, colgados(hevc, listOf(100_000L to imagenHevc(20), 66_667L to imagenHevc(7))))
        assertEquals(1, colgados(hevc, listOf(100_000L to imagenHevc(19), 66_667L to imagenHevc(8))))
    }

    @Test
    fun enH264CualquierAnteriorAlIdrCuenta() {
        val idr = avcc(byteArrayOf(0x67, 1), byteArrayOf(0x68, 1), byteArrayOf(0x65, 1))
        val b = avcc(byteArrayOf(0x01, 1))
        val p = avcc(byteArrayOf(0x41, 1))
        assertEquals(1, colgados(avc, listOf(0L to idr, 66_667L to p, 33_333L to b, 133_333L to idr, 100_000L to b)))
        assertEquals(0, colgados(avc, listOf(0L to idr, 66_667L to p, 33_333L to b, 100_000L to idr, 166_667L to p, 133_333L to b)))
    }

    @Test
    fun loQueNoSeEntiendeSeIgnora() {
        val roto = ByteBuffer.wrap(byteArrayOf(0, 0, 0, 9, 1))
        assertEquals(0, colgados(hevc, listOf(100_000L to imagenHevc(19), 50_000L to roto)))
        assertEquals(0, colgados("audio/mp4a-latm", listOf(100_000L to imagenHevc(19), 50_000L to imagenHevc(0))))
    }

    /** Un corte H.264 con su cabecera y el principio de la del corte (first_mb_in_slice 0 y slice_type). */
    private fun corteAvc(cabecera: Int, inicio: Int) = avcc(byteArrayOf(cabecera.toByte(), inicio.toByte(), 0x80.toByte()))

    private fun empieza(mime: String, b: ByteBuffer) = SincronizacionVideo.puedeEmpezar(mime, b, 0, b.limit())

    @Test
    fun seEmpiezaEnIdrIrapOImagenI() {
        assertEquals(true, empieza(hevc, imagenHevc(19)))
        assertEquals(true, empieza(hevc, imagenHevc(21)))
        assertEquals(true, empieza(hevc, imagenHevc(16)))
        assertEquals(false, empieza(hevc, imagenHevc(0)))
        assertEquals(false, empieza(hevc, imagenHevc(1)))
        assertEquals(false, empieza(hevc, imagenHevc(9)))
        // Con VPS, SPS y PPS delante cuenta la imagen
        assertEquals(true, empieza(hevc, avcc(byteArrayOf(0x40, 1), byteArrayOf(0x42, 1), byteArrayOf(0x44, 1), byteArrayOf(0x26, 1))))
        assertEquals(true, empieza(avc, avcc(byteArrayOf(0x67, 1), byteArrayOf(0x68, 1), byteArrayOf(0x65, 1))))
        // H.264 sin IDR: I (slice_type 7 y 2) si, P (5) y B (6) no
        assertEquals(true, empieza(avc, corteAvc(0x41, 0x88)))
        assertEquals(true, empieza(avc, corteAvc(0x41, 0xB0)))
        assertEquals(false, empieza(avc, corteAvc(0x41, 0x98)))
        assertEquals(false, empieza(avc, corteAvc(0x01, 0x9C)))
        // Lo que no se entiende, null: sin imagen, roto o de otro tipo
        assertEquals(null, empieza(hevc, avcc(byteArrayOf(0x40, 1))))
        assertEquals(null, empieza(avc, ByteBuffer.wrap(byteArrayOf(0, 0, 0, 9, 1))))
        assertEquals(null, empieza(avc, avcc(byteArrayOf(0x41))))
        assertEquals(null, empieza("video/x-vnd.on2.vp9", imagenHevc(19)))
    }

    @Test
    fun seEmpiezaTambienConStartCodes() {
        // Como lo da MediaExtractor: Annex B con start codes de cuatro bytes
        val idr = ByteBuffer.wrap(byteArrayOf(0, 0, 0, 1, 0x40, 1, 9, 0, 0, 0, 1, 0x26, 1, 0x55))
        val b = ByteBuffer.wrap(byteArrayOf(0, 0, 0, 1, 0x00, 1, 0x55, 0x22))
        assertEquals(true, empieza(hevc, idr))
        assertEquals(false, empieza(hevc, b))
        assertEquals(false, SincronizacionVideo.esReferencia(hevc, b, 0, b.limit()))
    }

    @Test
    fun referenciaPorTipoEnH265YPorNalRefIdcEnH264() {
        fun ref(mime: String, b: ByteBuffer) = SincronizacionVideo.esReferencia(mime, b, 0, b.limit())
        assertEquals(false, ref(hevc, imagenHevc(0)))
        assertEquals(true, ref(hevc, imagenHevc(1)))
        assertEquals(false, ref(hevc, imagenHevc(8)))
        assertEquals(true, ref(hevc, imagenHevc(19)))
        assertEquals(false, ref(avc, avcc(byteArrayOf(0x01, 1))))
        assertEquals(true, ref(avc, avcc(byteArrayOf(0x41, 1))))
        assertEquals(true, ref(avc, avcc(byteArrayOf(0x67, 1), byteArrayOf(0x65, 1))))
        assertEquals(null, ref(avc, avcc(byteArrayOf(0x67, 1))))
    }

    private fun tirados(mime: String, paquetes: List<Triple<Long, ByteBuffer, Boolean>>): List<Long> {
        val f = FiltroColgados(mime)
        return paquetes.filter { (t, b, sync) -> f.tirar(b, 0, b.limit(), t, sync) }.map { it.first }
    }

    @Test
    fun alDecodificarSeTiranLosDosBColgadosDeRoyerYNadaMas() {
        val paquetes = exportacionDeRoyer.map { (t, tipo) -> Triple(t, imagenHevc(tipo), tipo == 19) }
        assertEquals(listOf(6_833_333L, 7_300_000L), tirados(hevc, paquetes))
        // Igual si el archivo los marcaba tambien como sync (las exportaciones de antes)
        val antiguas = exportacionDeRoyer.map { (t, tipo) -> Triple(t, imagenHevc(tipo), tipo == 19 || t == 6_833_333L || t == 7_300_000L) }
        assertEquals(listOf(6_833_333L, 7_300_000L), tirados(hevc, antiguas))
    }

    @Test
    fun noSeTiraLoNormalNiUnColgadoDelQueSeApoyanOtros() {
        val normal = listOf(0L to 19, 66_667L to 1, 33_333L to 0, 133_333L to 1, 100_000L to 0)
        assertEquals(emptyList<Long>(), tirados(hevc, normal.map { (t, tipo) -> Triple(t, imagenHevc(tipo), tipo == 19) }))
        // Colgado pero TRAIL_R: sin el se romperian los que se apoyan en el
        val conReferencia = listOf(100_000L to 19, 66_667L to 1, 166_667L to 1)
        assertEquals(emptyList<Long>(), tirados(hevc, conReferencia.map { (t, tipo) -> Triple(t, imagenHevc(tipo), tipo == 19) }))
        // H.264: un B colgado (nal_ref_idc 0) si, un P colgado no
        val idr = avcc(byteArrayOf(0x67, 1), byteArrayOf(0x68, 1), byteArrayOf(0x65, 1))
        val h264 = listOf(Triple(100_000L, idr, true), Triple(66_667L, avcc(byteArrayOf(0x01, 1)), false), Triple(33_333L, avcc(byteArrayOf(0x41, 1)), false))
        assertEquals(listOf(66_667L), tirados(avc, h264))
    }
}
