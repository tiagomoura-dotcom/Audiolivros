package com.tiagomoura.audiolivros.chapters

/**
 * Capítulos de MP3 em quadros ID3v2 CHAP (v2.3 e v2.4). Lê só a etiqueta no começo
 * do arquivo, quadro a quadro, pulando os grandes (como a capa).
 */
object Id3ChapterParser {

    private const val HEADER_SIZE = 10
    private const val MAX_CHAP_FRAME = 1 shl 20

    fun parse(source: ByteSource): List<ParsedChapter> {
        val header = source.readFully(0, HEADER_SIZE) ?: return emptyList()
        if (header.ascii(0, 3) != "ID3") return emptyList()
        val major = header.u8(3)
        if (major != 3 && major != 4) return emptyList()
        val flags = header.u8(5)
        val tagEnd = HEADER_SIZE + syncSafe(header, 6)

        var position = HEADER_SIZE.toLong()
        if (flags and 0x40 != 0) {
            val ext = source.readFully(position, 4) ?: return emptyList()
            position += if (major == 4) syncSafe(ext, 0).toLong() else 4 + ext.u32(0)
        }

        val chapters = mutableListOf<ParsedChapter>()
        while (position + HEADER_SIZE <= tagEnd) {
            val frameHeader = source.readFully(position, HEADER_SIZE) ?: break
            if (frameHeader.u8(0) == 0) break
            val id = frameHeader.ascii(0, 4)
            val size = if (major == 4) syncSafe(frameHeader, 4).toLong() else frameHeader.u32(4)
            if (size <= 0 || position + HEADER_SIZE + size > tagEnd) break
            if (id == "CHAP" && size <= MAX_CHAP_FRAME) {
                source.readFully(position + HEADER_SIZE, size.toInt())
                    ?.let { parseChap(it, major) }
                    ?.let(chapters::add)
            }
            position += HEADER_SIZE + size
        }
        return chapters.normalized()
    }

    private fun parseChap(data: ByteArray, major: Int): ParsedChapter? {
        val idEnd = data.indexOf(0)
        if (idEnd < 0 || idEnd + 17 > data.size) return null
        val startMs = data.u32(idEnd + 1)
        var position = idEnd + 17
        var title = ""
        while (position + HEADER_SIZE <= data.size) {
            val id = data.ascii(position, 4)
            val size = if (major == 4) syncSafe(data, position + 4) else data.u32(position + 4).toInt()
            if (size <= 0 || position + HEADER_SIZE + size > data.size) break
            if (id == "TIT2") {
                title = decodeText(data, position + HEADER_SIZE, size)
            }
            position += HEADER_SIZE + size
        }
        return ParsedChapter(title, startMs)
    }

    internal fun decodeText(data: ByteArray, offset: Int, size: Int): String {
        if (size < 1) return ""
        val charset = when (data[offset].toInt()) {
            0 -> Charsets.ISO_8859_1
            1 -> Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            else -> Charsets.UTF_8
        }
        return String(data, offset + 1, size - 1, charset).trimEnd('\u0000').trim()
    }

    private fun syncSafe(data: ByteArray, at: Int): Int =
        (data.u8(at) and 0x7F shl 21) or (data.u8(at + 1) and 0x7F shl 14) or
            (data.u8(at + 2) and 0x7F shl 7) or (data.u8(at + 3) and 0x7F)

    private fun ByteArray.indexOf(value: Byte): Int {
        for (i in indices) if (this[i] == value) return i
        return -1
    }
}
