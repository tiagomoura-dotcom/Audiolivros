package com.tiagomoura.audiolivros.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.tiagomoura.audiolivros.core.database.Book
import com.tiagomoura.audiolivros.core.database.BookDao
import com.tiagomoura.audiolivros.core.database.BookStatus
import com.tiagomoura.audiolivros.covers.PlaceholderCover
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Livros baixados por link (seção 4.2). O livro entra na biblioteca na hora, com
 * o progresso do download; o worker preenche título, autor, capa e capítulos.
 */
@Singleton
class DownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookDao: BookDao,
) {
    fun bookDir(bookId: String) = File(context.filesDir, "books/$bookId")

    fun optionsFile(bookId: String) = File(bookDir(bookId), OPTIONS_FILE)

    suspend fun start(url: String, options: DownloadOptions): String = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        options.save(optionsFile(id))
        bookDao.insert(
            Book(
                id = id,
                title = url,
                author = null,
                audioUri = "",
                isCopied = true,
                fileName = "",
                fileSizeBytes = 0,
                durationMs = 0,
                coverPath = null,
                placeholderColor = PlaceholderCover.colorFor(url),
                chapterSource = null,
                status = BookStatus.PROCESSING,
                processingProgress = 0,
                addedAt = System.currentTimeMillis(),
                lastPlayedAt = null,
                finished = false,
                sourceUrl = url,
            ),
        )
        enqueue(id)
        id
    }

    suspend fun retry(bookId: String) {
        bookDao.updateStatus(bookId, BookStatus.PROCESSING, 0, null)
        enqueue(bookId)
    }

    fun cancel(bookId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(uniqueName(bookId))
    }

    private fun enqueue(bookId: String) {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(DownloadWorker.KEY_BOOK_ID to bookId))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(uniqueName(bookId), ExistingWorkPolicy.REPLACE, request)
    }

    companion object {
        const val OPTIONS_FILE = "opcoes-download.properties"

        fun uniqueName(bookId: String) = "download-$bookId"
    }
}
