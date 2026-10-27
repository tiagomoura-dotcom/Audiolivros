package com.tiagomoura.audiolivros.backup

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupFormatTest {

    private val example: String =
        checkNotNull(javaClass.classLoader?.getResource("exemplo-backup.json")).readText()

    @Test
    fun `lê o exemplo da documentação`() {
        val backup = BackupFormat.read(example)
        val book = backup.books.single()
        assertEquals("Dom Casmurro", book.title)
        assertEquals(Fingerprint("Dom Casmurro.mp3", 412_345_678, 24_612_000), book.fingerprint)
        assertEquals(9_341_000L, book.positionMs)
        assertEquals(1.25f, book.speed)
        assertFalse(book.finished)
        assertEquals("DESCRIPTION", book.chapterSource)
        assertEquals(listOf(0L, 250_000L, 645_000L), book.chapters.map { it.startMs })
        assertEquals(805_000L, book.bookmarks[0].endMs)
        assertNull(book.bookmarks[1].endMs)
        assertEquals("Marcado pela notificação", book.bookmarks[1].note)
        assertEquals(BackupFormat.parseIso("2026-10-25T23:41:12-03:00"), book.playbackUpdatedAt)
    }

    @Test
    fun `grava e lê de volta sem perder nada`() {
        val zone = ZoneId.of("America/Sao_Paulo")
        val original = BackupFormat.read(example)
        val again = BackupFormat.read(BackupFormat.write(original, zone))
        assertEquals(original, again)
        assertTrue(BackupFormat.write(original, zone).contains("\"exportedAt\": \"2026-10-26T07:40:00-03:00\""))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `recusa arquivo que não é backup`() {
        BackupFormat.read("""{"format": "outro", "version": 1}""")
    }

    @Test
    fun `impressão digital com tolerância de 1 s na duração`() {
        val base = Fingerprint("a.mp3", 100, 60_000)
        assertTrue(base.matches(Fingerprint("a.mp3", 100, 60_900)))
        assertTrue(base.matches(Fingerprint("a.mp3", 100, 59_000)))
        assertFalse(base.matches(Fingerprint("a.mp3", 100, 61_500)))
        assertFalse(base.matches(Fingerprint("a.mp3", 101, 60_000)))
        assertFalse(base.matches(Fingerprint("b.mp3", 100, 60_000)))
    }

    @Test
    fun `nome do arquivo de posição`() {
        assertEquals("Dom Casmurro.posicao.json", positionFileName("Dom Casmurro"))
        assertEquals("A_B_ C.posicao.json", positionFileName("A/B: C"))
        assertEquals("livro.posicao.json", positionFileName("  "))
    }
}
