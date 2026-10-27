package com.tiagomoura.audiolivros.chapters

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedChapterParsersTest {

    // ---------- ID3 ----------

    private fun ByteArrayOutputStream.int32(value: Long) {
        write((value shr 24).toInt() and 0xFF); write((value shr 16).toInt() and 0xFF)
        write((value shr 8).toInt() and 0xFF); write(value.toInt() and 0xFF)
    }

    private fun syncSafe(value: Int) = byteArrayOf(
        (value shr 21 and 0x7F).toByte(), (value shr 14 and 0x7F).toByte(),
        (value shr 7 and 0x7F).toByte(), (value and 0x7F).toByte(),
    )

    private fun id3Frame(id: String, payload: ByteArray, major: Int): ByteArray = ByteArrayOutputStream().apply {
        write(id.toByteArray(Charsets.ISO_8859_1))
        if (major == 4) write(syncSafe(payload.size)) else int32(payload.size.toLong())
        write(0); write(0)
        write(payload)
    }.toByteArray()

    private fun chap(elementId: String, startMs: Long, title: ByteArray, major: Int): ByteArray {
        val body = ByteArrayOutputStream().apply {
            write(elementId.toByteArray()); write(0)
            int32(startMs); int32(startMs + 1000); int32(0xFFFFFFFFL); int32(0xFFFFFFFFL)
            write(id3Frame("TIT2", title, major))
        }.toByteArray()
        return id3Frame("CHAP", body, major)
    }

    private fun id3Tag(major: Int, frames: List<ByteArray>, padding: Int = 0): ByteArray {
        val content = ByteArrayOutputStream().apply { frames.forEach(::write); write(ByteArray(padding)) }.toByteArray()
        return ByteArrayOutputStream().apply {
            write("ID3".toByteArray()); write(major); write(0); write(0)
            write(syncSafe(content.size))
            write(content)
            write(ByteArray(64)) // "áudio"
        }.toByteArray()
    }

    private fun utf8Title(text: String) = byteArrayOf(3) + text.toByteArray(Charsets.UTF_8)
    private fun utf16Title(text: String) = byteArrayOf(1) + text.toByteArray(Charsets.UTF_16) + byteArrayOf(0, 0)

    @Test
    fun `id3 v2_3 com capa grande antes dos capítulos`() {
        val cover = id3Frame("APIC", ByteArray(5_000) { 7 }, 3)
        val tag = id3Tag(
            3,
            listOf(
                cover,
                chap("ch1", 250_000, utf16Title("Do livro"), 3),
                chap("ch0", 0, utf16Title("Do título"), 3),
            ),
            padding = 100,
        )
        val chapters = EmbeddedChapterReader.read(ByteArraySource(tag))
        assertEquals(listOf(ParsedChapter("Do título", 0), ParsedChapter("Do livro", 250_000)), chapters)
    }

    @Test
    fun `id3 v2_4 com tamanhos syncsafe e utf-8`() {
        val tag = id3Tag(4, listOf(chap("a", 0, utf8Title("Capítulo 1"), 4), chap("b", 645_000, utf8Title("A denúncia"), 4)))
        assertEquals(
            listOf(ParsedChapter("Capítulo 1", 0), ParsedChapter("A denúncia", 645_000)),
            Id3ChapterParser.parse(ByteArraySource(tag)),
        )
    }

    @Test
    fun `id3 sem capítulos e arquivo sem etiqueta`() {
        assertTrue(Id3ChapterParser.parse(ByteArraySource(id3Tag(3, listOf(id3Frame("TIT2", utf8Title("x"), 3))))).isEmpty())
        assertTrue(EmbeddedChapterReader.read(ByteArraySource(ByteArray(100))).isEmpty())
    }

    // ---------- MP4 ----------

    private fun atom(type: String, vararg children: ByteArray): ByteArray {
        val payload = children.fold(ByteArray(0)) { acc, child -> acc + child }
        return ByteArrayOutputStream().apply {
            int32(8L + payload.size)
            write(type.toByteArray(Charsets.ISO_8859_1))
            write(payload)
        }.toByteArray()
    }

    private fun bytes(vararg values: Long, width: Int = 4): ByteArray = ByteArrayOutputStream().apply {
        values.forEach { value -> for (shift in (width - 1) downTo 0) write((value shr (shift * 8)).toInt() and 0xFF) }
    }.toByteArray()

    private val ftyp = atom("ftyp", "M4B ".toByteArray(), bytes(0), "isomM4B ".toByteArray())

    @Test
    fun `m4b com atom nero chpl`() {
        val titles = listOf("Abertura" to 0L, "Parte dois" to 125_500L)
        val chplBody = ByteArrayOutputStream().apply {
            write(1); write(byteArrayOf(0, 0, 0)); write(bytes(0))
            write(titles.size)
            titles.forEach { (title, ms) ->
                write(bytes(ms * 10_000, width = 8))
                val encoded = title.toByteArray(Charsets.UTF_8)
                write(encoded.size); write(encoded)
            }
        }.toByteArray()
        val file = ftyp + atom("mdat", ByteArray(300)) + atom("moov", atom("mvhd", ByteArray(100)), atom("udta", atom("chpl", chplBody)))
        assertEquals(
            listOf(ParsedChapter("Abertura", 0), ParsedChapter("Parte dois", 125_500)),
            EmbeddedChapterReader.read(ByteArraySource(file)),
        )
    }

    @Test
    fun `m4b com faixa de texto de capítulos do quicktime`() {
        val samples = listOf("Prólogo", "Capítulo 1", "Capítulo 2").map { title ->
            val encoded = title.toByteArray(Charsets.UTF_8)
            bytes(encoded.size.toLong(), width = 2) + encoded + bytes(12, 0x656E6364, 0x100) // atom encd
        }
        val header = ftyp
        val mdatHeaderSize = 8
        val firstSampleOffset = header.size + mdatHeaderSize
        val mdat = atom("mdat", *samples.toTypedArray())
        // Dois chunks: o primeiro com 2 amostras, o segundo com 1.
        val chunk1 = firstSampleOffset.toLong()
        val chunk2 = chunk1 + samples[0].size + samples[1].size

        fun fullBox(type: String, body: ByteArray) = atom(type, bytes(0) + body)
        val tkhdAudio = fullBox("tkhd", bytes(0, 0, 1, 0, 0) + ByteArray(60))
        val audioTrack = atom("trak", tkhdAudio, atom("tref", atom("chap", bytes(2))))
        val textTrack = atom(
            "trak",
            fullBox("tkhd", bytes(0, 0, 2, 0, 0) + ByteArray(60)),
            atom(
                "mdia",
                fullBox("mdhd", bytes(0, 0, 1000, 600_000) + bytes(0)),
                atom(
                    "minf",
                    atom(
                        "stbl",
                        fullBox("stts", bytes(2, 1, 60_000, 2, 240_000)),
                        fullBox("stsc", bytes(2, 1, 2, 1, 2, 1, 1)),
                        fullBox("stsz", bytes(0, 3, samples[0].size.toLong(), samples[1].size.toLong(), samples[2].size.toLong())),
                        fullBox("stco", bytes(2, chunk1, chunk2)),
                    ),
                ),
            ),
        )
        val file = header + mdat + atom("moov", audioTrack, textTrack)
        assertEquals(
            listOf(ParsedChapter("Prólogo", 0), ParsedChapter("Capítulo 1", 60_000), ParsedChapter("Capítulo 2", 300_000)),
            EmbeddedChapterReader.read(ByteArraySource(file)),
        )
    }

    @Test
    fun `m4a sem capítulos`() {
        val file = ftyp + atom("moov", atom("mvhd", ByteArray(100)))
        assertTrue(EmbeddedChapterReader.read(ByteArraySource(file)).isEmpty())
    }
}
