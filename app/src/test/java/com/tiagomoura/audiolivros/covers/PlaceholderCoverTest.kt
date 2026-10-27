package com.tiagomoura.audiolivros.covers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceholderCoverTest {

    @Test
    fun `iniciais das palavras significativas`() {
        assertEquals("DC", PlaceholderCover.initials("Dom Casmurro"))
        assertEquals("MP", PlaceholderCover.initials("Memórias Póstumas de Brás Cubas"))
        assertEquals("CA", PlaceholderCover.initials("O cortiço: Aluísio"))
        assertEquals("É", PlaceholderCover.initials("É"))
        assertEquals("?", PlaceholderCover.initials("  "))
    }

    @Test
    fun `cor estável e entre as quatro da paleta`() {
        val color = PlaceholderCover.colorFor("Dom Casmurro")
        assertEquals(color, PlaceholderCover.colorFor("Dom Casmurro"))
        assertTrue(color in PlaceholderCover.COLORS)
    }
}
