package com.tiagomoura.audiolivros.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatTest {

    @Test
    fun `minutos e segundos`() {
        assertEquals("00:00", formatDuration(0))
        assertEquals("04:21", formatDuration(261_000))
        assertEquals("59:59", formatDuration(3_599_999))
    }

    @Test
    fun `com horas`() {
        assertEquals("1:02:33", formatDuration(3_753_000))
        assertEquals("10:00:00", formatDuration(36_000_000))
    }

    @Test
    fun `negativo vira zero`() {
        assertEquals("00:00", formatDuration(-5_000))
    }

    @Test
    fun `tempo restante resumido`() {
        assertEquals("3 h 12 min", formatRemaining(11_520_000))
        assertEquals("2 h", formatRemaining(7_200_000))
        assertEquals("45 min", formatRemaining(2_700_000))
        assertEquals("30 s", formatRemaining(30_000))
    }
}
