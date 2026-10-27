package com.tiagomoura.audiolivros.library

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.tiagomoura.audiolivros.chapters.ChapterRepository
import com.tiagomoura.audiolivros.core.database.Book
import com.tiagomoura.audiolivros.core.database.BookDao
import com.tiagomoura.audiolivros.core.database.BookStatus
import com.tiagomoura.audiolivros.core.database.BookWithProgress
import com.tiagomoura.audiolivros.covers.CoverStorage
import com.tiagomoura.audiolivros.covers.PlaceholderCover
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class AlreadyImportedException(val book: Book) : Exception("Livro já importado: ${book.title}")

@Singleton
class BookRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookDao: BookDao,
    private val coverStorage: CoverStorage,
    private val chapterRepository: ChapterRepository,
) {
    fun observeLibrary(): Flow<List<BookWithProgress>> = bookDao.observeAllWithProgress()

    fun observeBook(id: String): Flow<Book?> = bookDao.observe(id)

    /**
     * Importa um arquivo escolhido pelo OpenDocument (seção 4.1). Guarda o URI com
     * permissão persistente; se não for possível, copia para `filesDir/books/<id>/`.
     */
    suspend fun import(uri: Uri): Book = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val (displayName, sizeBytes) = queryNameAndSize(uri)
        bookDao.findByFile(displayName, sizeBytes)?.let { throw AlreadyImportedException(it) }

        val id = UUID.randomUUID().toString()
        var audioUri = uri
        var copied = false
        try {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: SecurityException) {
            Log.i(TAG, "Sem permissão persistente; copiando o arquivo", e)
            audioUri = copyToAppStorage(id, uri, displayName)
            copied = true
        }

        val metadata = readMetadata(audioUri)
        val title = metadata.title?.takeIf { it.isNotBlank() }?.trim()
            ?: displayName.substringBeforeLast('.', displayName)
        val coverPath = metadata.picture?.let { coverStorage.save(id, it) }

        val book = Book(
            id = id,
            title = title,
            author = metadata.author?.takeIf { it.isNotBlank() }?.trim(),
            audioUri = audioUri.toString(),
            isCopied = copied,
            fileName = displayName,
            fileSizeBytes = sizeBytes,
            durationMs = metadata.durationMs,
            coverPath = coverPath,
            placeholderColor = PlaceholderCover.colorFor(title),
            chapterSource = null,
            status = BookStatus.READY,
            processingProgress = 0,
            addedAt = System.currentTimeMillis(),
            lastPlayedAt = null,
            finished = false,
        )
        bookDao.insert(book)
        chapterRepository.autoDetectInBackground(book.id)
        book
    }

    suspend fun rename(id: String, title: String) = bookDao.updateTitle(id, title.trim())

    suspend fun updateAuthor(id: String, author: String) = bookDao.updateAuthor(id, author.trim().ifEmpty { null })

    /** Remove o livro, a capa e a cópia local. O arquivo original do usuário não é apagado. */
    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val book = bookDao.get(id) ?: return@withContext
        bookDao.delete(id)
        coverStorage.delete(id)
        if (book.isCopied) {
            File(context.filesDir, "books/$id").deleteRecursively()
        } else {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    Uri.parse(book.audioUri),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
    }

    private fun queryNameAndSize(uri: Uri): Pair<String, Long> {
        var name: String? = null
        var size = -1L
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                }
            }
        return (name ?: uri.lastPathSegment ?: "audiolivro") to size
    }

    /** Copia em blocos: o arquivo nunca é carregado inteiro na memória. */
    private fun copyToAppStorage(id: String, uri: Uri, displayName: String): Uri {
        val directory = File(context.filesDir, "books/$id").apply { mkdirs() }
        val safeName = displayName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val target = File(directory, safeName)
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Não foi possível abrir o arquivo")
        input.use { source ->
            target.outputStream().use { sink -> source.copyTo(sink, COPY_BUFFER_BYTES) }
        }
        return Uri.fromFile(target)
    }

    private class AudioMetadata(val title: String?, val author: String?, val durationMs: Long, val picture: ByteArray?)

    private fun readMetadata(uri: Uri): AudioMetadata {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            fun meta(key: Int): String? = retriever.extractMetadata(key)?.takeIf { it.isNotBlank() }
            return AudioMetadata(
                title = meta(MediaMetadataRetriever.METADATA_KEY_TITLE),
                author = meta(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    ?: meta(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                    ?: meta(MediaMetadataRetriever.METADATA_KEY_AUTHOR)
                    ?: meta(MediaMetadataRetriever.METADATA_KEY_WRITER),
                durationMs = meta(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                picture = retriever.embeddedPicture,
            )
        } catch (e: RuntimeException) {
            Log.w(TAG, "Não foi possível ler os metadados", e)
            return AudioMetadata(null, null, 0L, null)
        } finally {
            runCatching { retriever.release() }
        }
    }

    private companion object {
        const val TAG = "BookRepository"
        const val COPY_BUFFER_BYTES = 256 * 1024
    }
}
