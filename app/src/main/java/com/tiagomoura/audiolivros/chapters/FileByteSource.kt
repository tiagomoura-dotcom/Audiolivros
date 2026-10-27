package com.tiagomoura.audiolivros.chapters

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.Closeable
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/** [ByteSource] sobre um arquivo do aparelho (content:// ou file://), com leitura posicionada. */
class FileByteSource private constructor(private val descriptor: ParcelFileDescriptor) : ByteSource, Closeable {

    private val channel: FileChannel = FileInputStream(descriptor.fileDescriptor).channel

    override val length: Long = channel.size()

    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        val read = channel.read(ByteBuffer.wrap(buffer, offset, size), position)
        return if (read < 0) 0 else read
    }

    override fun close() {
        channel.close()
        descriptor.close()
    }

    companion object {
        fun open(context: Context, uri: Uri): FileByteSource? =
            context.contentResolver.openFileDescriptor(uri, "r")?.let(::FileByteSource)
    }
}
