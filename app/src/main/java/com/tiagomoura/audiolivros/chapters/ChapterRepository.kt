package com.tiagomoura.audiolivros.chapters

import android.content.Context
import android.net.Uri
import android.util.Log
import com.tiagomoura.audiolivros.core.database.Book
import com.tiagomoura.audiolivros.core.database.BookDao
import com.tiagomoura.audiolivros.core.database.BookStatus
import com.tiagomoura.audiolivros.core.database.Chapter
import com.tiagomoura.audiolivros.core.database.ChapterDao
import com.tiagomoura.audiolivros.core.database.ChapterSource
import com.tiagomoura.audiolivros.core.di.ApplicationScope
import com.tiagomoura.audiolivros.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Capítulos de cada livro: detecção pelas fontes da seção 5 e edição manual.
 * Cada capítulo guarda a sua fonte; o livro guarda a fonte da última análise.
 */
@Singleton
class ChapterRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookDao: BookDao,
    private val chapterDao: ChapterDao,
    private val settings: SettingsRepository,
    private val analysis: ChapterAnalysisScheduler,
    @ApplicationScope private val scope: CoroutineScope,
) {
    fun observe(bookId: String): Flow<List<Chapter>> = chapterDao.observeForBook(bookId)

    /** Detecção automática depois de importar: a primeira fonte que der resultado. */
    fun autoDetectInBackground(bookId: String) {
        scope.launch(Dispatchers.IO) {
            try {
                autoDetect(bookId)
            } catch (e: Exception) {
                Log.w(TAG, "Falha na detecção de capítulos de $bookId", e)
                bookDao.updateStatus(bookId, BookStatus.READY, 0)
            }
        }
    }

    /**
     * Fontes em ordem de prioridade (seção 5): metadados embutidos, capítulos do
     * info.json, legenda baixada e, se nada der resultado, pausas.
     */
    suspend fun autoDetect(bookId: String, infoJsonChapters: List<ParsedChapter> = emptyList()) {
        val book = bookDao.get(bookId) ?: return
        bookDao.updateStatus(bookId, BookStatus.PROCESSING, 0)
        val embedded = readEmbedded(book)
        if (embedded.size > 1) {
            save(book, embedded, ChapterSource.EMBEDDED)
            bookDao.updateStatus(bookId, BookStatus.READY, 100)
            return
        }
        if (infoJsonChapters.size > 1) {
            save(book, infoJsonChapters, ChapterSource.INFO_JSON)
            bookDao.updateStatus(bookId, BookStatus.READY, 100)
            return
        }
        val fromSubtitle = book.subtitlePath?.let { path -> detectInSubtitle(File(path).readLines()) }.orEmpty()
        if (fromSubtitle.size > 1) {
            save(book, fromSubtitle, ChapterSource.SUBTITLE)
            bookDao.updateStatus(bookId, BookStatus.READY, 100)
            return
        }
        // Nenhuma fonte pronta: análise por pausas em segundo plano (o worker atualiza o status).
        analysis.analyze(bookId, applyChapters = true)
    }

    /** Reanalisa pelos metadados embutidos. Retorna quantos capítulos encontrou. */
    suspend fun applyEmbedded(bookId: String): Int = withContext(Dispatchers.IO) {
        val book = bookDao.get(bookId) ?: return@withContext 0
        readEmbedded(book).also { if (it.isNotEmpty()) save(book, it, ChapterSource.EMBEDDED) }.size
    }

    /** Reanalisa pela lista colada da descrição do vídeo. */
    suspend fun applyDescription(bookId: String, text: String): Int = withContext(Dispatchers.IO) {
        val book = bookDao.get(bookId) ?: return@withContext 0
        DescriptionParser.parse(text).also { if (it.isNotEmpty()) save(book, it, ChapterSource.DESCRIPTION) }.size
    }

    /** Reanalisa por uma legenda .srt ou .vtt escolhida pelo usuário. */
    suspend fun applySubtitle(bookId: String, uri: Uri): Int = withContext(Dispatchers.IO) {
        val book = bookDao.get(bookId) ?: return@withContext 0
        val lines = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readLines() }.orEmpty()
        detectInSubtitle(lines).also { if (it.isNotEmpty()) save(book, it, ChapterSource.SUBTITLE) }.size
    }

    /** Salva uma lista vinda de outra fonte (info.json, pausas). */
    suspend fun applyParsed(bookId: String, chapters: List<ParsedChapter>, source: ChapterSource): Int =
        withContext(Dispatchers.IO) {
            val book = bookDao.get(bookId) ?: return@withContext 0
            if (chapters.isNotEmpty()) save(book, chapters, source)
            chapters.size
        }

    suspend fun detectInSubtitle(lines: List<String>): List<ParsedChapter> {
        val keywords = settings.currentDetection().keywords
        return SubtitleChapterDetector(keywords).detect(SubtitleParser.parse(lines.asSequence()))
    }

    private fun readEmbedded(book: Book): List<ParsedChapter> = try {
        FileByteSource.open(context, Uri.parse(book.audioUri))?.use { EmbeddedChapterReader.read(it) }.orEmpty()
    } catch (e: Exception) {
        Log.w(TAG, "Não foi possível ler capítulos embutidos", e)
        emptyList()
    }

    private suspend fun save(book: Book, chapters: List<ParsedChapter>, source: ChapterSource) {
        val withinBook = if (book.durationMs > 0) chapters.filter { it.startMs < book.durationMs } else chapters
        chapterDao.replaceForBook(
            book.id,
            withinBook.mapIndexed { index, chapter ->
                Chapter(bookId = book.id, index = index, title = chapter.title, startMs = chapter.startMs, source = source)
            },
        )
        bookDao.updateChapterSource(book.id, source)
    }

    // ---------- Edição manual (seção 2.6) ----------

    suspend fun rename(chapterId: Long, title: String) {
        val chapter = chapterDao.get(chapterId) ?: return
        chapterDao.updateAll(listOf(chapter.copy(title = title.trim(), source = ChapterSource.MANUAL)))
    }

    suspend fun setStart(chapterId: Long, startMs: Long) {
        val chapter = chapterDao.get(chapterId) ?: return
        val book = bookDao.get(chapter.bookId)
        val limit = book?.durationMs?.takeIf { it > 0 } ?: Long.MAX_VALUE
        chapterDao.updateAll(listOf(chapter.copy(startMs = startMs.coerceIn(0, limit - 1), source = ChapterSource.MANUAL)))
        renumber(chapter.bookId)
    }

    suspend fun add(bookId: String, startMs: Long, title: String) {
        val existing = chapterDao.getForBook(bookId)
        if (existing.isEmpty()) {
            // Sem capítulos, o livro inteiro era um só: o início vira o capítulo 1.
            chapterDao.insertAll(listOf(Chapter(bookId = bookId, index = 0, title = defaultChapterTitle(1), startMs = 0, source = ChapterSource.MANUAL)))
        }
        if (existing.none { it.startMs == startMs } && (startMs > 0 || existing.isNotEmpty())) {
            chapterDao.insertAll(listOf(Chapter(bookId = bookId, index = 0, title = title, startMs = startMs, source = ChapterSource.MANUAL)))
        }
        renumber(bookId)
        if (existing.isEmpty()) bookDao.updateChapterSource(bookId, ChapterSource.MANUAL)
    }

    suspend fun delete(chapterId: Long) {
        val chapter = chapterDao.get(chapterId) ?: return
        chapterDao.delete(chapterId)
        renumber(chapter.bookId)
    }

    private suspend fun renumber(bookId: String) {
        val chapters = chapterDao.getForBook(bookId)
        val changed = chapters.mapIndexedNotNull { index, chapter -> if (chapter.index != index) chapter.copy(index = index) else null }
        if (changed.isNotEmpty()) chapterDao.updateAll(changed)
    }

    private companion object {
        const val TAG = "ChapterRepository"
    }
}
