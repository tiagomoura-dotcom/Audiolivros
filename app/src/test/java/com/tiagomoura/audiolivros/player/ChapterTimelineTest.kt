package com.tiagomoura.audiolivros.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChapterTimelineTest {

    private val chapters = listOf(
        TimelineChapter("Do título", 0),
        TimelineChapter("Do livro", 250_000),
        TimelineChapter("A denúncia", 645_000),
    )
    private val timeline = ChapterTimeline(chapters, durationMs = 1_000_000)

    @Test
    fun `posição dentro de um capítulo`() {
        val position = timeline.positionAt(300_000)
        assertEquals(1, position.index)
        assertEquals(3, position.count)
        assertEquals("Do livro", position.title)
        assertEquals(250_000, position.startMs)
        assertEquals(645_000, position.endMs)
        assertEquals(395_000, position.durationMs)
    }

    @Test
    fun `último capítulo vai até o fim do livro`() {
        val position = timeline.positionAt(900_000)
        assertEquals(2, position.index)
        assertEquals(1_000_000, position.endMs)
    }

    @Test
    fun `capítulos fora de ordem são ordenados`() {
        val shuffled = ChapterTimeline(chapters.reversed(), 1_000_000)
        assertEquals(0, shuffled.indexAt(10_000))
        assertEquals(2, shuffled.indexAt(700_000))
    }

    @Test
    fun `primeiro capítulo que não começa no zero cobre o início`() {
        val late = ChapterTimeline(listOf(TimelineChapter("Um", 5_000), TimelineChapter("Dois", 60_000)), 120_000)
        val position = late.positionAt(1_000)
        assertEquals(0, position.index)
        assertEquals(0, position.startMs)
    }

    @Test
    fun `sem capítulos o livro inteiro é um capítulo`() {
        val empty = ChapterTimeline(emptyList(), 500_000)
        val position = empty.positionAt(123_000)
        assertEquals(0, position.index)
        assertEquals(1, position.count)
        assertNull(position.title)
        assertEquals(0, position.startMs)
        assertEquals(500_000, position.endMs)
        assertNull(empty.nextTarget(123_000))
        assertEquals(0, empty.previousTarget(123_000))
    }

    @Test
    fun `anterior volta ao início do capítulo atual depois de 3 s`() {
        assertEquals(250_000, timeline.previousTarget(253_001))
    }

    @Test
    fun `anterior vai ao capítulo anterior nos primeiros 3 s`() {
        assertEquals(0, timeline.previousTarget(252_000))
        assertEquals(250_000, timeline.previousTarget(646_000))
    }

    @Test
    fun `anterior no primeiro capítulo volta ao início`() {
        assertEquals(0, timeline.previousTarget(1_000))
    }

    @Test
    fun `próximo capítulo`() {
        assertEquals(250_000L, timeline.nextTarget(0))
        assertEquals(645_000L, timeline.nextTarget(250_000))
        assertNull(timeline.nextTarget(700_000))
    }

    @Test
    fun `lista de todos os capítulos com durações calculadas`() {
        val all = timeline.all()
        assertEquals(listOf(0L, 250_000L, 645_000L), all.map { it.startMs })
        assertEquals(listOf(250_000L, 395_000L, 355_000L), all.map { it.durationMs })
        assertEquals(1, ChapterTimeline(emptyList(), 10_000).all().size)
    }
}
