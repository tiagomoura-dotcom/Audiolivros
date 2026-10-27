package com.tiagomoura.audiolivros.chapters

import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleTest {

    private val srt = """
        1
        00:00:01,000 --> 00:00:04,500
        Prólogo. Antes de tudo,

        2
        00:00:30,000 --> 00:00:33,000
        <i>Capítulo primeiro.</i>

        3
        00:00:50,000 --> 00:00:53,000
        como disse no capítulo 1, ele

        4
        00:10:00,000 --> 00:10:03,000
        Capítulo III — A Ópera

        5
        01:02:03,250 --> 01:02:05,000
        capitulo vinte e um
        uma nova vida
    """.trimIndent()

    @Test
    fun `lê srt com várias linhas e tags`() {
        val cues = SubtitleParser.parse(srt)
        assertEquals(5, cues.size)
        assertEquals(30_000, cues[1].startMs)
        assertEquals("Capítulo primeiro.", cues[1].text)
        assertEquals(3_723_250, cues[4].startMs)
        assertEquals("capitulo vinte e um uma nova vida", cues[4].text)
    }

    @Test
    fun `lê vtt com cabeçalho e configurações da fala`() {
        val vtt = """
            WEBVTT
            Kind: captions

            00:01.000 --> 00:03.000 align:start position:0%
            Capítulo 2

            01:00:00.500 --> 01:00:02.000
            Parte <c.colorE5E5E5>dois</c>
        """.trimIndent()
        val cues = SubtitleParser.parse(vtt)
        assertEquals(listOf(1_000L, 3_600_500L), cues.map { it.startMs })
        assertEquals("Parte dois", cues[1].text)
    }

    @Test
    fun `detecta capítulos por palavra-chave e número`() {
        val detector = SubtitleChapterDetector(SubtitleChapterDetector.DEFAULT_KEYWORDS)
        val chapters = detector.detect(SubtitleParser.parse(srt))
        // "Capítulo primeiro" a 29 s do prólogo e a menção aos 50 s são ignorados (menos de 60 s).
        assertEquals(listOf(1_000L, 600_000L, 3_723_250L), chapters.map { it.startMs })
        assertEquals(listOf("Prólogo", "Capítulo 3", "Capítulo 21"), chapters.map { it.title })
    }

    @Test
    fun `números por extenso e romanos da especificação`() {
        val detector = SubtitleChapterDetector(listOf("capítulo"))
        val cues = listOf(
            SubtitleCue(0, 1, "capitulo 3"),
            SubtitleCue(100_000, 1, "Capítulo III"),
            SubtitleCue(200_000, 1, "capítulo terceiro"),
            SubtitleCue(300_000, 1, "Capítulo vinte e um"),
        )
        assertEquals(
            listOf("Capítulo 3", "Capítulo 3", "Capítulo 3", "Capítulo 21"),
            detector.detect(cues).map { it.title },
        )
    }

    @Test
    fun `palavra-chave sem número e no meio da fala não conta`() {
        val detector = SubtitleChapterDetector(SubtitleChapterDetector.DEFAULT_KEYWORDS)
        val cues = listOf(
            SubtitleCue(0, 1, "fazia parte da familia"),
            SubtitleCue(100_000, 1, "o capitulo anterior"),
            SubtitleCue(200_000, 1, "nos capitulos 3 e 4"),
            SubtitleCue(300_000, 1, "como no prologo"),
        )
        assertEquals(emptyList<ParsedChapter>(), detector.detect(cues))
    }

    @Test
    fun `abreviação cap com ponto`() {
        val detector = SubtitleChapterDetector(listOf("cap."))
        val chapters = detector.detect(listOf(SubtitleCue(5_000, 6_000, "Cap. 7, D. Glória")))
        assertEquals(listOf(ParsedChapter("Cap. 7", 5_000)), chapters)
    }

    @Test
    fun `ajusta o início para o fim de uma pausa próxima`() {
        val detector = SubtitleChapterDetector(listOf("capítulo"))
        val cues = listOf(SubtitleCue(100_000, 102_000, "Capítulo 2"))
        val silences = listOf(90_000L..92_000L, 96_000L..98_500L, 99_500L..99_800L)
        // A pausa de 0,3 s é curta demais; a de 2,5 s termina 1,5 s antes da fala.
        assertEquals(98_500L, detector.detect(cues, silences).single().startMs)
        // Pausa mais de 5 s antes: não ajusta.
        assertEquals(100_000L, detector.detect(cues, listOf(90_000L..94_000L)).single().startMs)
    }
}
