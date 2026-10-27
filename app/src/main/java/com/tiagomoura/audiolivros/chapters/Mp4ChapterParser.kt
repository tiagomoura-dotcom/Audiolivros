package com.tiagomoura.audiolivros.chapters

/**
 * Capítulos de M4B/M4A: o atom Nero `chpl` (moov/udta/chpl) ou a faixa de texto
 * de capítulos do QuickTime (trak referenciada por tref/chap). Lê só o `moov` e
 * as amostras de texto, nunca o áudio.
 */
object Mp4ChapterParser {

    private const val MAX_MOOV_BYTES = 64L shl 20
    private const val MAX_TEXT_SAMPLE = 2048

    private class Atom(val type: String, val start: Int, val end: Int)

    fun parse(source: ByteSource): List<ParsedChapter> {
        val moov = readMoov(source) ?: return emptyList()
        val nero = children(moov, 0, moov.size).firstOrNull { it.type == "udta" }
            ?.let { udta -> children(moov, udta.start, udta.end).firstOrNull { it.type == "chpl" } }
            ?.let { parseChpl(moov, it) }
            .orEmpty()
        if (nero.isNotEmpty()) return nero.normalized()
        return parseQuickTime(moov, source).normalized()
    }

    private fun readMoov(source: ByteSource): ByteArray? {
        var position = 0L
        while (position + 8 <= source.length) {
            val header = source.readFully(position, 16.coerceAtMost((source.length - position).toInt())) ?: return null
            var size = header.u32(0)
            val type = header.ascii(4, 4)
            var headerSize = 8
            if (size == 1L) {
                if (header.size < 16) return null
                size = header.u64(8)
                headerSize = 16
            } else if (size == 0L) {
                size = source.length - position
            }
            if (size < headerSize) return null
            if (type == "moov") {
                val payload = size - headerSize
                if (payload > MAX_MOOV_BYTES) return null
                return source.readFully(position + headerSize, payload.toInt())
            }
            position += size
        }
        return null
    }

    private fun children(data: ByteArray, start: Int, end: Int): List<Atom> {
        val atoms = mutableListOf<Atom>()
        var position = start
        while (position + 8 <= end) {
            var size = data.u32(position)
            val type = data.ascii(position + 4, 4)
            var headerSize = 8
            if (size == 1L) {
                if (position + 16 > end) break
                size = data.u64(position + 8)
                headerSize = 16
            } else if (size == 0L) {
                size = (end - position).toLong()
            }
            if (size < headerSize || position + size > end) break
            atoms += Atom(type, position + headerSize, (position + size).toInt())
            position += size.toInt()
        }
        return atoms
    }

    private fun child(data: ByteArray, parent: Atom, type: String): Atom? =
        children(data, parent.start, parent.end).firstOrNull { it.type == type }

    private fun path(data: ByteArray, parent: Atom, vararg types: String): Atom? {
        var current = parent
        for (type in types) current = child(data, current, type) ?: return null
        return current
    }

    /** chpl: versão(1) flags(3) [reservado(4) se versão ≥ 1] número(1) e, por capítulo, início em 100 ns(8), tamanho(1), título. */
    private fun parseChpl(data: ByteArray, atom: Atom): List<ParsedChapter> {
        var position = atom.start
        if (position + 5 > atom.end) return emptyList()
        val version = data.u8(position)
        position += 4
        if (version >= 1) position += 4
        if (position >= atom.end) return emptyList()
        val count = data.u8(position)
        position += 1
        val chapters = mutableListOf<ParsedChapter>()
        repeat(count) {
            if (position + 9 > atom.end) return chapters
            val start100ns = data.u64(position)
            val titleSize = data.u8(position + 8)
            position += 9
            if (position + titleSize > atom.end) return chapters
            val title = String(data, position, titleSize, Charsets.UTF_8).trim()
            position += titleSize
            chapters += ParsedChapter(title, start100ns / 10_000)
        }
        return chapters
    }

    private fun parseQuickTime(moov: ByteArray, source: ByteSource): List<ParsedChapter> {
        val root = Atom("moov", 0, moov.size)
        val tracks = children(moov, 0, moov.size).filter { it.type == "trak" }
        val chapterTrackIds = tracks.flatMap { trak ->
            val chap = path(moov, trak, "tref", "chap") ?: return@flatMap emptyList()
            (chap.start until chap.end step 4).filter { it + 4 <= chap.end }.map { moov.u32(it) }
        }.toSet()
        if (chapterTrackIds.isEmpty() || root.end == 0) return emptyList()

        val textTrack = tracks.firstOrNull { trackId(moov, it) in chapterTrackIds } ?: return emptyList()
        val mdhd = path(moov, textTrack, "mdia", "mdhd") ?: return emptyList()
        val timescale = timescale(moov, mdhd).takeIf { it > 0 } ?: return emptyList()
        val stbl = path(moov, textTrack, "mdia", "minf", "stbl") ?: return emptyList()

        val durations = child(moov, stbl, "stts")?.let { sampleDurations(moov, it) } ?: return emptyList()
        val sizes = child(moov, stbl, "stsz")?.let { sampleSizes(moov, it) } ?: return emptyList()
        val chunkOffsets = (child(moov, stbl, "stco")?.let { chunkOffsets(moov, it, wide = false) }
            ?: child(moov, stbl, "co64")?.let { chunkOffsets(moov, it, wide = true) }) ?: return emptyList()
        val stsc = child(moov, stbl, "stsc")?.let { samplesPerChunk(moov, it) } ?: return emptyList()

        val offsets = sampleOffsets(sizes, chunkOffsets, stsc)
        val chapters = mutableListOf<ParsedChapter>()
        var time = 0L
        for (index in offsets.indices) {
            val title = readTextSample(source, offsets[index], sizes[index])
            chapters += ParsedChapter(title, time * 1000 / timescale)
            time += durations.getOrElse(index) { 0L }
        }
        return chapters
    }

    private fun trackId(data: ByteArray, trak: Atom): Long? {
        val tkhd = child(data, trak, "tkhd") ?: return null
        val version = data.u8(tkhd.start)
        val at = tkhd.start + if (version == 1) 20 else 12
        return if (at + 4 <= tkhd.end) data.u32(at) else null
    }

    private fun timescale(data: ByteArray, mdhd: Atom): Long {
        val version = data.u8(mdhd.start)
        val at = mdhd.start + if (version == 1) 20 else 12
        return if (at + 4 <= mdhd.end) data.u32(at) else 0
    }

    private fun sampleDurations(data: ByteArray, stts: Atom): List<Long> {
        val count = data.u32(stts.start + 4).toInt()
        val result = mutableListOf<Long>()
        for (i in 0 until count) {
            val at = stts.start + 8 + i * 8
            if (at + 8 > stts.end) break
            val samples = data.u32(at)
            val delta = data.u32(at + 4)
            repeat(samples.coerceAtMost(100_000).toInt()) { result += delta }
        }
        return result
    }

    private fun sampleSizes(data: ByteArray, stsz: Atom): List<Int> {
        val fixed = data.u32(stsz.start + 4).toInt()
        val count = data.u32(stsz.start + 8).toInt().coerceAtMost(100_000)
        if (fixed != 0) return List(count) { fixed }
        return (0 until count).mapNotNull { i ->
            val at = stsz.start + 12 + i * 4
            if (at + 4 <= stsz.end) data.u32(at).toInt() else null
        }
    }

    private fun chunkOffsets(data: ByteArray, atom: Atom, wide: Boolean): List<Long> {
        val count = data.u32(atom.start + 4).toInt()
        val step = if (wide) 8 else 4
        return (0 until count).mapNotNull { i ->
            val at = atom.start + 8 + i * step
            when {
                at + step > atom.end -> null
                wide -> data.u64(at)
                else -> data.u32(at)
            }
        }
    }

    /** stsc: pares (primeiro chunk, amostras por chunk), com chunks numerados a partir de 1. */
    private fun samplesPerChunk(data: ByteArray, stsc: Atom): List<Pair<Int, Int>> {
        val count = data.u32(stsc.start + 4).toInt()
        return (0 until count).mapNotNull { i ->
            val at = stsc.start + 8 + i * 12
            if (at + 12 <= stsc.end) data.u32(at).toInt() to data.u32(at + 4).toInt() else null
        }
    }

    private fun sampleOffsets(sizes: List<Int>, chunks: List<Long>, stsc: List<Pair<Int, Int>>): List<Long> {
        val offsets = mutableListOf<Long>()
        var sample = 0
        for (chunkIndex in chunks.indices) {
            val chunkNumber = chunkIndex + 1
            val perChunk = stsc.lastOrNull { it.first <= chunkNumber }?.second ?: 1
            var offset = chunks[chunkIndex]
            repeat(perChunk) {
                if (sample >= sizes.size) return offsets
                offsets += offset
                offset += sizes[sample]
                sample++
            }
        }
        return offsets
    }

    private fun readTextSample(source: ByteSource, offset: Long, size: Int): String {
        if (size < 2) return ""
        val data = source.readFully(offset, size.coerceAtMost(MAX_TEXT_SAMPLE)) ?: return ""
        val length = data.u16(0).coerceAtMost(data.size - 2)
        if (length <= 0) return ""
        val isUtf16 = length >= 2 && data.u8(2) == 0xFE && data.u8(3) == 0xFF
        return String(data, 2, length, if (isUtf16) Charsets.UTF_16 else Charsets.UTF_8).trim()
    }
}
