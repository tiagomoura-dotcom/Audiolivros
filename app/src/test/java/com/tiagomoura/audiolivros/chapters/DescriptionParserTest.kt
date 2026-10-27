package com.tiagomoura.audiolivros.chapters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DescriptionParserTest {

    @Test
    fun `exemplos da especificação`() {
        assertEquals(ParsedChapter("Capítulo 1 - Do título", 0), DescriptionParser.parseLine("0:00 Capítulo 1 - Do título"))
        assertEquals(ParsedChapter("Cap. 12: A ópera", 3_753_000), DescriptionParser.parseLine("1:02:33 — Cap. 12: A ópera"))
        assertEquals(ParsedChapter("Prólogo", 0), DescriptionParser.parseLine("Prólogo 00:00"))
        assertEquals(ParsedChapter("Parte II", 750_000), DescriptionParser.parseLine("12:30 | Parte II"))
    }

    @Test
    fun `horário no fim com separador`() {
        assertEquals(ParsedChapter("Epílogo", 5_405_000), DescriptionParser.parseLine("Epílogo - 1:30:05"))
        assertEquals(ParsedChapter("A partida", 65_000), DescriptionParser.parseLine("A partida (01:05)"))
    }

    @Test
    fun `marcadores e colchetes no início`() {
        assertEquals(ParsedChapter("Introdução", 0), DescriptionParser.parseLine("• [00:00] Introdução"))
        assertEquals(ParsedChapter("Fim", 3_600_000), DescriptionParser.parseLine("- 1:00:00 – Fim"))
    }

    @Test
    fun `linhas sem horário são ignoradas`() {
        assertNull(DescriptionParser.parseLine("Obrigado por ouvir!"))
        assertNull(DescriptionParser.parseLine(""))
        assertNull(DescriptionParser.parseLine("Gravado em 2024 no estúdio 3"))
    }

    @Test
    fun `horários inválidos são ignorados`() {
        assertNull(DescriptionParser.parseLine("0:75 Errado"))
        assertNull(DescriptionParser.parseLine("1:75:00 Errado"))
    }

    @Test
    fun `descrição completa vira lista ordenada`() {
        val text = """
            Audiolivro completo. Domínio público.

            Capítulos:
            0:00 Do título
            4:10 Do livro
            10:45 A denúncia
            Prólogo 00:00
            Siga o canal!
        """.trimIndent()
        val chapters = DescriptionParser.parse(text)
        assertEquals(listOf(0L, 250_000L, 645_000L), chapters.map { it.startMs })
        assertEquals("Do título", chapters[0].title)
        assertEquals("A denúncia", chapters[2].title)
    }

    @Test
    fun `título vazio recebe nome padrão`() {
        val chapters = DescriptionParser.parse("0:00\n5:00 —")
        assertEquals(listOf("Capítulo 1", "Capítulo 2"), chapters.map { it.title })
    }
}
