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
}
