package com.tiagomoura.audiolivros.bookmarks

import com.tiagomoura.audiolivros.core.database.Bookmark
import com.tiagomoura.audiolivros.core.database.BookmarkDao
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** Marcações de ponto (só início) e de trecho (início e fim), em posições absolutas no livro. */
@Singleton
class BookmarkRepository @Inject constructor(
    private val dao: BookmarkDao,
) {
    fun observe(bookId: String): Flow<List<Bookmark>> = dao.observeForBook(bookId)

    suspend fun addPoint(bookId: String, positionMs: Long, note: String? = null): Long =
        dao.insert(Bookmark(bookId = bookId, startMs = positionMs, endMs = null, note = note, createdAt = System.currentTimeMillis()))

    /** Cria um trecho; aceita início e fim em qualquer ordem. */
    suspend fun addRange(bookId: String, fromMs: Long, toMs: Long, note: String? = null): Long =
        dao.insert(
            Bookmark(
                bookId = bookId,
                startMs = minOf(fromMs, toMs),
                endMs = maxOf(fromMs, toMs),
                note = note,
                createdAt = System.currentTimeMillis(),
            ),
        )

    suspend fun updateNote(id: Long, note: String) = dao.updateNote(id, note.trim().ifEmpty { null })

    suspend fun delete(id: Long) = dao.delete(id)
}
