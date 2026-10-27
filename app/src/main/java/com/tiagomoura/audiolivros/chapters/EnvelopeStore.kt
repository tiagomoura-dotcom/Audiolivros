package com.tiagomoura.audiolivros.chapters

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Envelope salvo por livro, para reajustar a sensibilidade sem decodificar de novo. */
@Singleton
class EnvelopeStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private fun file(bookId: String) = File(context.filesDir, "envelopes/$bookId.env")

    fun exists(bookId: String): Boolean = file(bookId).exists()

    fun load(bookId: String): Envelope? =
        file(bookId).takeIf { it.exists() }?.let { runCatching { it.inputStream().use(Envelope::read) }.getOrNull() }

    fun save(bookId: String, envelope: Envelope) {
        val target = file(bookId)
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, "${target.name}.tmp")
        temporary.outputStream().use(envelope::write)
        temporary.renameTo(target)
    }

    fun delete(bookId: String) {
        file(bookId).delete()
    }
}
