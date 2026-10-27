package com.tiagomoura.audiolivros.chapters

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.tiagomoura.audiolivros.R
import com.tiagomoura.audiolivros.core.database.BookDao
import com.tiagomoura.audiolivros.core.database.BookStatus
import com.tiagomoura.audiolivros.core.database.ChapterSource
import com.tiagomoura.audiolivros.core.notifications.Notifications
import com.tiagomoura.audiolivros.settings.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Análise por pausas em primeiro plano, com notificação de progresso. Gera e salva
 * o envelope; se [KEY_APPLY], já salva os capítulos com as configurações atuais.
 */
@HiltWorker
class ChapterAnalysisWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val bookDao: BookDao,
    private val chapters: ChapterRepository,
    private val envelopes: EnvelopeStore,
    private val settings: SettingsRepository,
) : CoroutineWorker(context, params) {

    private val bookId: String get() = inputData.getString(KEY_BOOK_ID).orEmpty()

    override suspend fun getForegroundInfo(): ForegroundInfo = foreground(title = "", percent = null)

    private fun foreground(title: String, percent: Int?): ForegroundInfo =
        Notifications.foregroundInfo(
            NOTIFICATION_ID_BASE + (bookId.hashCode() and 0xFFFF),
            Notifications.progress(
                applicationContext,
                title.ifEmpty { applicationContext.getString(R.string.app_name) },
                applicationContext.getString(R.string.etapa_detectando),
                percent,
            ),
        )

    override suspend fun doWork(): Result {
        val book = bookDao.get(bookId) ?: return Result.failure()
        runCatching { setForeground(foreground(book.title, 0)) }
        bookDao.updateStatus(book.id, BookStatus.PROCESSING, 0)
        return try {
            val envelope = envelopes.load(book.id) ?: AudioEnvelopeDecoder(applicationContext)
                .decode(Uri.parse(book.audioUri)) { percent ->
                    setProgress(workDataOf(KEY_PROGRESS to percent))
                    bookDao.updateProgress(book.id, percent)
                    runCatching { setForeground(foreground(book.title, percent)) }
                }
                .also { envelopes.save(book.id, it) }

            if (inputData.getBoolean(KEY_APPLY, false)) {
                val detection = settings.currentDetection()
                val found = SilenceDetector.chapters(
                    envelope,
                    thresholdDb = detection.silenceThresholdDb,
                    minPauseMs = (detection.minPauseSeconds * 1000).toLong(),
                    minChapterMs = (detection.minChapterMinutes * 60_000).toLong(),
                    durationMs = book.durationMs.takeIf { it > 0 } ?: envelope.durationMs,
                )
                // Um único "capítulo" (o livro inteiro) não é resultado útil.
                if (found.size > 1) chapters.applyParsed(book.id, found, ChapterSource.SILENCE)
            }
            bookDao.updateStatus(book.id, BookStatus.READY, 100)
            Result.success()
        } catch (e: CancellationException) {
            bookDao.updateStatus(book.id, BookStatus.READY, 0)
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Falha na análise por pausas", e)
            bookDao.updateStatus(book.id, BookStatus.READY, 0)
            Result.failure(workDataOf(KEY_ERROR to (e.localizedMessage ?: e.javaClass.simpleName)))
        }
    }

    companion object {
        private const val TAG = "ChapterAnalysisWorker"
        private const val NOTIFICATION_ID_BASE = 20_000
        const val KEY_BOOK_ID = "livro"
        const val KEY_APPLY = "aplicar"
        const val KEY_PROGRESS = "progresso"
        const val KEY_ERROR = "erro"

        fun uniqueName(bookId: String) = "analise-pausas-$bookId"
    }
}

/** Estado da análise de um livro, para a tela de reanálise. */
data class AnalysisProgress(val running: Boolean, val percent: Int, val failed: Boolean, val error: String?)

@Singleton
class ChapterAnalysisScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val workManager get() = WorkManager.getInstance(context)

    fun analyze(bookId: String, applyChapters: Boolean) {
        val request = OneTimeWorkRequestBuilder<ChapterAnalysisWorker>()
            .setInputData(
                workDataOf(
                    ChapterAnalysisWorker.KEY_BOOK_ID to bookId,
                    ChapterAnalysisWorker.KEY_APPLY to applyChapters,
                ),
            )
            .build()
        workManager.enqueueUniqueWork(ChapterAnalysisWorker.uniqueName(bookId), ExistingWorkPolicy.KEEP, request)
    }

    fun cancel(bookId: String) {
        workManager.cancelUniqueWork(ChapterAnalysisWorker.uniqueName(bookId))
    }

    fun observe(bookId: String): Flow<AnalysisProgress?> =
        workManager.getWorkInfosForUniqueWorkFlow(ChapterAnalysisWorker.uniqueName(bookId)).map { infos ->
            val info = infos.lastOrNull() ?: return@map null
            AnalysisProgress(
                running = info.state == WorkInfo.State.RUNNING || info.state == WorkInfo.State.ENQUEUED,
                percent = info.progress.getInt(ChapterAnalysisWorker.KEY_PROGRESS, 0),
                failed = info.state == WorkInfo.State.FAILED,
                error = info.outputData.getString(ChapterAnalysisWorker.KEY_ERROR),
            )
        }
}
