package com.royermethod.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class RelojTest {

    /** El reloj de antes, tal cual, para comprobar que sin milesimas no cambia nada. */
    private fun relojAntes(us: Long): String {
        val decimas = us / 100_000
        val s = decimas / 10
        return "%02d:%02d.%d".format(s / 60, s % 60, decimas % 10)
    }

    @Test fun sinMilesimasEsElDeSiempre() {
        var us = 0L
        while (us < 4_000_000_000L) {
            assertEquals(relojAntes(us), reloj(us))
            us += 1_234_567L
        }
        for (us in longArrayOf(0, 1, 99_999, 100_000, 7_433_333, 59_999_999, 60_000_000, 3_599_999_999)) {
            assertEquals(relojAntes(us), reloj(us))
            assertEquals(relojAntes(us), reloj(us, milesimas = false))
        }
    }

    @Test fun enPausaConMilesimas() {
        assertEquals("00:00.005", reloj(5_000, milesimas = true))
        assertEquals("00:00.001", reloj(1_000, milesimas = true))
        assertEquals("00:00.000", reloj(999, milesimas = true))
        assertEquals("00:01.234", reloj(1_234_567, milesimas = true))
        assertEquals("00:07.433", reloj(7_433_333, milesimas = true))
        assertEquals("01:05.000", reloj(65_000_000, milesimas = true))
        assertEquals("00:00.000", reloj(-5, milesimas = true))
    }
}
