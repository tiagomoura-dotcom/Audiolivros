package com.tiagomoura.audiolivros.chapters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PortugueseNumbersTest {

    private fun value(text: String) = PortugueseNumbers.parse(text.split(' '))?.value

    @Test
    fun arabicos() {
        assertEquals(3, value("3"))
        assertEquals(148, value("148 e"))
    }

    @Test
    fun romanos() {
        assertEquals(3, value("iii"))
        assertEquals(14, value("xiv"))
        assertEquals(49, value("xlix"))
        assertNull(PortugueseNumbers.romanValue("iiii"))
        assertNull(PortugueseNumbers.romanValue("vix"))
    }

    @Test
    fun `cardinais por extenso`() {
        assertEquals(1, value("um"))
        assertEquals(3, value("tres"))
        assertEquals(21, value("vinte e um de"))
        assertEquals(99, value("noventa e nove"))
        assertEquals(120, value("cento e vinte"))
        assertEquals(14, value("catorze"))
    }

    @Test
    fun `ordinais por extenso`() {
        assertEquals(1, value("primeiro"))
        assertEquals(3, value("terceiro"))
        assertEquals(2, value("segunda parte"))
        assertEquals(11, value("decimo primeiro"))
        assertEquals(23, value("vigesimo terceiro"))
    }

    @Test
    fun `e sem número depois não é consumido`() {
        val result = PortugueseNumbers.parse("dez e a historia".split(' '))
        assertEquals(10, result?.value)
        assertEquals(1, result?.wordsUsed)
    }

    @Test
    fun `palavra comum não é número`() {
        assertNull(value("da historia"))
        assertNull(value(""))
    }
}
