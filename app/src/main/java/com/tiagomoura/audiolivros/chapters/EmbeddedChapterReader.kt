package com.tiagomoura.audiolivros.chapters

/** Escolhe o parser pela assinatura do arquivo: ID3 (MP3) ou ftyp (MP4/M4B/M4A). */
object EmbeddedChapterReader {
    fun read(source: ByteSource): List<ParsedChapter> {
        val header = source.readFully(0, 12) ?: return emptyList()
        return when {
            header.ascii(0, 3) == "ID3" -> Id3ChapterParser.parse(source)
            header.ascii(4, 4) == "ftyp" -> Mp4ChapterParser.parse(source)
            else -> emptyList()
        }
    }
}
