package com.tiagomoura.audiolivros.chapters

/**
 * Leitura aleatória de um arquivo, sem carregá-lo inteiro. No app é um FileChannel;
 * nos testes, um ByteArray.
 */
interface ByteSource {
    val length: Long

    /** Lê até [size] bytes a partir de [position]. Retorna quantos bytes leu (0 no fim). */
    fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int
}

/** Lê exatamente [size] bytes, ou nulo se o arquivo acabar antes. */
fun ByteSource.readFully(position: Long, size: Int): ByteArray? {
    if (size < 0 || position < 0 || position + size > length) return null
    val buffer = ByteArray(size)
    var done = 0
    while (done < size) {
        val read = readAt(position + done, buffer, done, size - done)
        if (read <= 0) return null
        done += read
    }
    return buffer
}

class ByteArraySource(private val bytes: ByteArray) : ByteSource {
    override val length: Long get() = bytes.size.toLong()

    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (position >= bytes.size) return 0
        val count = minOf(size.toLong(), bytes.size - position).toInt()
        System.arraycopy(bytes, position.toInt(), buffer, offset, count)
        return count
    }
}

internal fun ByteArray.u8(at: Int): Int = this[at].toInt() and 0xFF
internal fun ByteArray.u16(at: Int): Int = (u8(at) shl 8) or u8(at + 1)
internal fun ByteArray.u32(at: Int): Long =
    (u8(at).toLong() shl 24) or (u8(at + 1).toLong() shl 16) or (u8(at + 2).toLong() shl 8) or u8(at + 3).toLong()
internal fun ByteArray.u64(at: Int): Long = (u32(at) shl 32) or u32(at + 4)
internal fun ByteArray.ascii(at: Int, size: Int): String = String(this, at, size, Charsets.ISO_8859_1)
