package com.tiagomoura.audiolivros.download

import com.tiagomoura.audiolivros.chapters.ParsedChapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InfoJsonTest {

    @Test
    fun `capítulos, título e autor do info json`() {
        val info = InfoJson.parse(
            """
            {"id": "abc", "title": " Dom Casmurro — audiolivro ", "uploader": "Canal Domínio Público",
             "channel": "Outro", "duration": 24612.5, "formats": [{"x": 1}],
             "chapters": [
               {"start_time": 250.0, "end_time": 645.0, "title": "Do livro"},
               {"start_time": 0.0, "end_time": 250.0, "title": "Do título"},
               {"start_time": 645.4, "end_time": 900, "title": ""}
             ]}
            """.trimIndent(),
        )
        assertEquals("Dom Casmurro — audiolivro", info.title)
        assertEquals("Canal Domínio Público", info.author)
        assertEquals(24_612_500L, info.durationMs)
        assertEquals(
            listOf(ParsedChapter("Do título", 0), ParsedChapter("Do livro", 250_000), ParsedChapter("Capítulo 3", 645_400)),
            info.chapters,
        )
    }

    @Test
    fun `sem capítulos e sem uploader usa o canal`() {
        val info = InfoJson.parse("""{"title": "X", "channel": "Canal", "chapters": null}""")
        assertEquals("Canal", info.author)
        assertTrue(info.chapters.isEmpty())
        assertNull(info.durationMs)
    }

    @Test
    fun `erros que sugerem atualizar o yt-dlp`() {
        assertTrue(DownloadErrors.suggestsUpdate("ERROR: [youtube] abc: nsig extraction failed: You may need to update"))
        assertTrue(DownloadErrors.suggestsUpdate("ERROR: unable to download video data: HTTP Error 403: Forbidden"))
        assertFalse(DownloadErrors.suggestsUpdate("ERROR: Video unavailable. This video is private"))
        assertFalse(DownloadErrors.suggestsUpdate(null))
    }

    @Test
    fun `resumo do erro é a última linha ERROR`() {
        val log = "[youtube] Extracting URL\nWARNING: algo\nERROR: [youtube] abc: Video unavailable\n"
        assertEquals("[youtube] abc: Video unavailable", DownloadErrors.summarize(log))
    }
}
