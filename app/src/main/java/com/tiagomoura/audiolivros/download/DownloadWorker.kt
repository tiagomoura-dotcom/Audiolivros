package com.tiagomoura.audiolivros.download

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.tiagomoura.audiolivros.R
import com.tiagomoura.audiolivros.chapters.ChapterRepository
import com.tiagomoura.audiolivros.chapters.ParsedChapter
import com.tiagomoura.audiolivros.core.database.BookDao
import com.tiagomoura.audiolivros.core.database.BookStatus
import com.tiagomoura.audiolivros.core.notifications.Notifications
import com.tiagomoura.audiolivros.covers.CoverStorage
import com.tiagomoura.audiolivros.covers.PlaceholderCover
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible

/**
 * Baixa o áudio de um link com o yt-dlp em primeiro plano, com notificação de
 * progresso (etapas "Baixando", "Convertendo", "Detectando capítulos").
 */
@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val bookDao: BookDao,
    private val ytDlp: YtDlp,
    private val covers: CoverStorage,
    private val chapters: ChapterRepository,
    private val downloads: DownloadRepository,
) : CoroutineWorker(context, params) {

    private enum class Step(val label: Int) {
        DOWNLOADING(R.string.etapa_baixando),
        CONVERTING(R.string.etapa_convertendo),
        CHAPTERS(R.string.etapa_detectando),
    }

    private data class Progress(val step: Step, val percent: Int)

    private val bookId: String get() = inputData.getString(KEY_BOOK_ID).orEmpty()
    private val processId: String get() = "download-$bookId"
    private var notificationTitle: String = ""

    override suspend fun getForegroundInfo(): ForegroundInfo = foreground(Progress(Step.DOWNLOADING, 0))

    private fun foreground(progress: Progress): ForegroundInfo = Notifications.foregroundInfo(
        NOTIFICATION_ID_BASE + (bookId.hashCode() and 0xFFFF),
        Notifications.progress(
            applicationContext,
            notificationTitle.ifEmpty { applicationContext.getString(R.string.baixar_por_link) },
            applicationContext.getString(progress.step.label),
            progress.percent,
        ),
    )

    override suspend fun doWork(): Result {
        val book = bookDao.get(bookId) ?: return Result.failure()
        val url = book.sourceUrl ?: return Result.failure()
        notificationTitle = url
        val dir = downloads.bookDir(bookId)
        val options = DownloadOptions.load(downloads.optionsFile(bookId))
        bookDao.updateStatus(bookId, BookStatus.PROCESSING, 0, null)

        return try {
            coroutineScope {
                val progress = MutableStateFlow(Progress(Step.DOWNLOADING, 0))
                val reporter = launch {
                    progress.collectLatest { current ->
                        bookDao.updateProgress(bookId, current.percent)
                        runCatching { setForeground(foreground(current)) }
                    }
                }

                ytDlp.awaitReady()
                dir.mkdirs()
                dir.listFiles()?.filter { it.name != DownloadRepository.OPTIONS_FILE }?.forEach { it.deleteRecursively() }

                try {
                    runInterruptible(Dispatchers.IO) { runYtDlp(url, dir, options, withSubtitles = true, progress) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val subtitleFailure = options.chaptersAndSubtitles &&
                        e.message.orEmpty().contains("subtitle", ignoreCase = true)
                    when {
                        // O áudio chegou; o erro foi num extra (legenda, miniatura). Segue com o que há.
                        findAudio(dir) != null -> Log.w(TAG, "yt-dlp terminou com erro, mas o áudio foi baixado", e)
                        // A legenda é opcional: se só ela falhou (ex.: HTTP 429), baixa de novo sem legenda.
                        subtitleFailure -> {
                            Log.w(TAG, "Legendas falharam; repetindo sem legendas", e)
                            dir.listFiles()?.filter { it.name != DownloadRepository.OPTIONS_FILE }?.forEach { it.deleteRecursively() }
                            runInterruptible(Dispatchers.IO) { runYtDlp(url, dir, options, withSubtitles = false, progress) }
                        }
                        else -> throw e
                    }
                }

                val result = collectResult(dir, options)
                notificationTitle = result.title
                progress.value = Progress(Step.CHAPTERS, 95)
                bookDao.update(
                    book.copy(
                        title = result.title,
                        author = result.author,
                        audioUri = Uri.fromFile(result.audio).toString(),
                        isCopied = true,
                        fileName = result.audio.name,
                        fileSizeBytes = result.audio.length(),
                        durationMs = result.durationMs,
                        coverPath = result.coverPath,
                        placeholderColor = PlaceholderCover.colorFor(result.title),
                        thumbnailPath = result.thumbnail?.absolutePath,
                        subtitlePath = result.subtitle?.absolutePath,
                        status = BookStatus.PROCESSING,
                        processingProgress = 95,
                        errorMessage = null,
                    ),
                )
                chapters.autoDetect(bookId, result.infoChapters)
                reporter.cancel()
            }
            Result.success()
        } catch (e: CancellationException) {
            YoutubeDL.getInstance().destroyProcessById(processId)
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Falha no download de $url", e)
            val message = DownloadErrors.summarize(e.message) ?: e.javaClass.simpleName
            bookDao.updateStatus(bookId, BookStatus.ERROR, 0, message)
            Result.failure()
        }
    }

    private fun runYtDlp(
        url: String,
        dir: File,
        options: DownloadOptions,
        withSubtitles: Boolean,
        progress: MutableStateFlow<Progress>,
    ) {
        val request = YoutubeDLRequest(url)
        options.ytDlpOptions(dir, withSubtitles).forEach { (option, argument) ->
            if (argument == null) request.addOption(option) else request.addOption(option, argument)
        }
        YoutubeDL.getInstance().execute(request, processId) { percent, _, line ->
            val step = if (line.contains("[ExtractAudio]") || line.contains("[EmbedThumbnail]") ||
                line.contains("[FFmpeg") || line.contains("[Metadata]")
            ) {
                Step.CONVERTING
            } else {
                progress.value.step
            }
            val overall = when (step) {
                Step.DOWNLOADING -> (percent.coerceIn(0f, 100f) * 0.85f).toInt()
                Step.CONVERTING -> 90
                Step.CHAPTERS -> 95
            }
            progress.value = Progress(step, maxOf(overall, progress.value.percent))
        }
    }

    private fun findAudio(dir: File): File? = dir.listFiles()
        ?.filter { it.extension.lowercase() in AUDIO_EXTENSIONS && !it.name.endsWith(".part") }
        ?.maxByOrNull { it.length() }

    private class DownloadResult(
        val audio: File,
        val title: String,
        val author: String?,
        val durationMs: Long,
        val coverPath: String?,
        val thumbnail: File?,
        val subtitle: File?,
        val infoChapters: List<ParsedChapter>,
    )

    private fun collectResult(dir: File, options: DownloadOptions): DownloadResult {
        val files = dir.listFiles()?.toList().orEmpty()
        val audio = findAudio(dir)
            ?: throw IllegalStateException(applicationContext.getString(R.string.erro_audio_nao_encontrado))
        val info = files.firstOrNull { it.name.endsWith(".info.json") }
            ?.let { runCatching { InfoJson.parse(it.readText()) }.getOrNull() }
        val thumbnail = files.firstOrNull { it.extension.lowercase() in IMAGE_EXTENSIONS }
        val subtitle = files.filter { it.extension.lowercase() == "srt" }
            .sortedByDescending { it.name.contains(".pt") }
            .firstOrNull()
        val title = info?.title ?: audio.nameWithoutExtension
        val coverPath = if (options.thumbnailAsCover && thumbnail != null) {
            runCatching { covers.save(bookId, thumbnail.readBytes()) }.getOrNull()
        } else {
            null
        }
        return DownloadResult(
            audio = audio,
            title = title,
            author = info?.author,
            durationMs = readDuration(audio) ?: info?.durationMs ?: 0L,
            coverPath = coverPath,
            thumbnail = thumbnail,
            subtitle = subtitle,
            infoChapters = info?.chapters.orEmpty(),
        )
    }

    private fun readDuration(file: File): Long? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } catch (e: RuntimeException) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    companion object {
        private const val TAG = "DownloadWorker"
        private const val NOTIFICATION_ID_BASE = 30_000
        const val KEY_BOOK_ID = "livro"
        private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "m4b", "aac", "opus", "ogg", "oga", "webm", "flac", "wav", "mka")
        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
    }
}
