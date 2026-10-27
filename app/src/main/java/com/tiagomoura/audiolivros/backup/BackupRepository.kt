package com.tiagomoura.audiolivros.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.tiagomoura.audiolivros.core.database.Book
import com.tiagomoura.audiolivros.core.database.BookDao
import com.tiagomoura.audiolivros.core.database.BookStatus
import com.tiagomoura.audiolivros.core.database.Bookmark
import com.tiagomoura.audiolivros.core.database.BookmarkDao
import com.tiagomoura.audiolivros.core.database.Chapter
import com.tiagomoura.audiolivros.core.database.ChapterDao
import com.tiagomoura.audiolivros.core.database.ChapterSource
import com.tiagomoura.audiolivros.core.database.PlaybackState
import com.tiagomoura.audiolivros.core.database.PlaybackStateDao
import com.tiagomoura.audiolivros.covers.PlaceholderCover
import com.tiagomoura.audiolivros.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Resultado da importação: quantos livros foram associados e quantos ficaram com arquivo ausente. */
data class ImportResult(val matched: Int, val missing: Int)

/**
 * Backup e importação (seção 9). A importação associa cada livro do arquivo a um
 * livro existente pela impressão digital; os sem correspondência entram na
 * biblioteca como "arquivo ausente", para o usuário apontar o arquivo.
 */
@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookDao: BookDao,
    private val chapterDao: ChapterDao,
    private val bookmarkDao: BookmarkDao,
    private val playbackStateDao: PlaybackStateDao,
    private val settings: SettingsRepository,
) {
    /** Gera o JSON de todos os livros, ou só de [bookId]. */
    suspend fun exportJson(bookId: String? = null): String = withContext(Dispatchers.IO) {
        val books = if (bookId == null) bookDao.getAll() else listOfNotNull(bookDao.get(bookId))
        BackupFormat.write(Backup(System.currentTimeMillis(), books.filter { it.audioUri.isNotEmpty() }.map { toBackup(it) }))
    }

    suspend fun exportTo(uri: Uri, bookId: String? = null) = withContext(Dispatchers.IO) {
        val text = exportJson(bookId)
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) }
            ?: error("Não foi possível gravar o arquivo")
        if (bookId == null) settings.setLastBackupAt(System.currentTimeMillis())
    }

    suspend fun importFrom(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: error("Não foi possível ler o arquivo")
        restore(BackupFormat.read(text))
    }

    private suspend fun restore(backup: Backup): ImportResult {
        val existing = bookDao.getAll()
        var matched = 0
        var missing = 0
        for (entry in backup.books) {
            val target = existing.firstOrNull { book ->
                book.audioUri.isNotEmpty() && entry.fingerprint.matches(Fingerprint(book.fileName, book.fileSizeBytes, book.durationMs))
            }
            val bookId = if (target != null) {
                matched++
                bookDao.updateFinished(target.id, entry.finished)
                target.id
            } else {
                missing++
                val id = entry.id.takeIf { id -> existing.none { it.id == id } } ?: java.util.UUID.randomUUID().toString()
                bookDao.insert(missingBook(id, entry))
                id
            }
            applyEntry(bookId, entry)
        }
        return ImportResult(matched, missing)
    }

    private suspend fun applyEntry(bookId: String, entry: BackupBook) {
        entry.positionMs?.let { position ->
            playbackStateDao.upsert(PlaybackState(bookId, position, entry.speed, entry.playbackUpdatedAt ?: System.currentTimeMillis()))
        }
        if (entry.chapters.isNotEmpty()) {
            chapterDao.replaceForBook(
                bookId,
                entry.chapters.sortedBy { it.startMs }.mapIndexed { index, chapter ->
                    Chapter(bookId = bookId, index = index, title = chapter.title, startMs = chapter.startMs, source = sourceOf(chapter.source))
                },
            )
            bookDao.updateChapterSource(bookId, entry.chapterSource?.let(::sourceOf))
        }
        if (entry.bookmarks.isNotEmpty()) {
            // Reimportar o mesmo backup não duplica marcações.
            val current = bookmarkDao.getForBook(bookId)
            val fresh = entry.bookmarks.filter { b -> current.none { it.startMs == b.startMs && it.endMs == b.endMs } }
            bookmarkDao.insertAll(
                fresh.map { Bookmark(bookId = bookId, startMs = it.startMs, endMs = it.endMs, note = it.note, createdAt = it.createdAt) },
            )
        }
    }

    private fun missingBook(id: String, entry: BackupBook) = Book(
        id = id,
        title = entry.title,
        author = entry.author,
        audioUri = "",
        isCopied = false,
        fileName = entry.fingerprint.fileName,
        fileSizeBytes = entry.fingerprint.fileSizeBytes,
        durationMs = entry.fingerprint.durationMs,
        coverPath = null,
        placeholderColor = PlaceholderCover.colorFor(entry.title),
        chapterSource = entry.chapterSource?.let(::sourceOf),
        status = BookStatus.ERROR,
        processingProgress = 0,
        addedAt = System.currentTimeMillis(),
        lastPlayedAt = null,
        finished = entry.finished,
        errorMessage = MISSING_FILE,
    )

    /** O usuário apontou o arquivo de um livro "arquivo ausente". */
    suspend fun relink(bookId: String, uri: Uri) = withContext(Dispatchers.IO) {
        val book = bookDao.get(bookId) ?: return@withContext
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        var name = book.fileName
        var size = book.fileSizeBytes
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 && !cursor.isNull(it) }?.let { name = cursor.getString(it) }
                cursor.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !cursor.isNull(it) }?.let { size = cursor.getLong(it) }
            }
        }
        bookDao.update(
            book.copy(audioUri = uri.toString(), fileName = name, fileSizeBytes = size, status = BookStatus.READY, errorMessage = null),
        )
    }

    private suspend fun toBackup(book: Book): BackupBook {
        val playback = playbackStateDao.get(book.id)
        return BackupBook(
            id = book.id,
            title = book.title,
            author = book.author,
            fingerprint = Fingerprint(book.fileName, book.fileSizeBytes, book.durationMs),
            positionMs = playback?.positionMs ?: 0L,
            speed = playback?.speed ?: 1f,
            playbackUpdatedAt = playback?.updatedAt,
            finished = book.finished,
            chapterSource = book.chapterSource?.name,
            chapters = chapterDao.getForBook(book.id).map { BackupChapter(it.index, it.title, it.startMs, it.source.name) },
            bookmarks = bookmarkDao.getForBook(book.id).map { BackupBookmark(it.startMs, it.endMs, it.note, it.createdAt) },
        )
    }

    private fun sourceOf(name: String): ChapterSource =
        runCatching { ChapterSource.valueOf(name) }.getOrDefault(ChapterSource.MANUAL)

    companion object {
        /** Marca, em errorMessage, os livros importados sem arquivo correspondente. */
        const val MISSING_FILE = "arquivo-ausente"

        fun backupFileName(timestamp: Long = System.currentTimeMillis()): String =
            "audiolivros-backup-" + java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).format(java.util.Date(timestamp)) + ".json"
    }
}
