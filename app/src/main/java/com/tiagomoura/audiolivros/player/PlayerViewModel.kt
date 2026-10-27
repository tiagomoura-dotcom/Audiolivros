package com.tiagomoura.audiolivros.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tiagomoura.audiolivros.core.database.Book
import com.tiagomoura.audiolivros.core.database.BookDao
import android.net.Uri
import com.tiagomoura.audiolivros.backup.BackupRepository
import com.tiagomoura.audiolivros.bookmarks.BookmarkRepository
import com.tiagomoura.audiolivros.covers.CoverRepository
import com.tiagomoura.audiolivros.core.database.Bookmark
import com.tiagomoura.audiolivros.core.database.ChapterDao
import com.tiagomoura.audiolivros.core.database.ChapterSource
import com.tiagomoura.audiolivros.settings.PlaybackSettings
import com.tiagomoura.audiolivros.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface PlayerMessage {
    data object CoverFailed : PlayerMessage
    data object Exported : PlayerMessage
    data class ExportFailed(val reason: String?) : PlayerMessage
}

/** Marcação como mostrada no player, com o capítulo correspondente. */
data class BookmarkItem(
    val id: Long,
    val startMs: Long,
    val endMs: Long?,
    val note: String?,
    val chapterNumber: Int,
    val chapterTitle: String?,
) {
    val isRange: Boolean get() = endMs != null
}

data class PlayerScreenState(
    val loading: Boolean = true,
    val book: Book? = null,
    /** Verdadeiro quando o serviço já carregou este livro. */
    val isCurrent: Boolean = false,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val speed: Float = 1f,
    val chapter: ChapterPosition = ChapterPosition(0, 1, null, 0, 0),
    val chapters: List<ChapterPosition> = emptyList(),
    val bookmarks: List<BookmarkItem> = emptyList(),
    /** Início do trecho sendo marcado (entre o primeiro toque em Trecho e o Concluir). */
    val pendingRangeStartMs: Long? = null,
    val rangeActive: Boolean = false,
    val rangeStartMs: Long = 0,
    val rangeEndMs: Long = 0,
    val rangeRepeat: Boolean = false,
    val timerRemainingMs: Long? = null,
    val timerEndOfChapter: Boolean = false,
    val chapterSource: ChapterSource? = null,
    val skipSeconds: Int = PlaybackSettings.DEFAULT_SKIP_SECONDS,
) {
    val bookProgress: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val chapterElapsedMs: Long get() = (positionMs - chapter.startMs).coerceAtLeast(0)
    val bookmarkCount: Int get() = bookmarks.size

    /** Marcações dentro do capítulo atual, para desenhar na barra. */
    val chapterBookmarks: List<BookmarkItem>
        get() = bookmarks.filter { b ->
            val end = b.endMs ?: b.startMs
            end >= chapter.startMs && b.startMs <= chapter.endMs
        }

    val chapterProgress: Float
        get() = if (chapter.durationMs > 0) (chapterElapsedMs.toFloat() / chapter.durationMs).coerceIn(0f, 1f) else 0f
}

private data class BookTimeline(val book: Book?, val chapters: List<TimelineChapter>, val bookmarks: List<Bookmark>)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    bookDao: BookDao,
    chapterDao: ChapterDao,
    private val bookmarkRepository: BookmarkRepository,
    private val settings: SettingsRepository,
    private val player: PlayerConnection,
    private val covers: CoverRepository,
    private val backup: BackupRepository,
) : ViewModel() {

    private val messages = Channel<PlayerMessage>(Channel.BUFFERED)
    val messageFlow = messages.receiveAsFlow()

    val batteryCardDismissed: StateFlow<Boolean> = settings.batteryCardDismissed
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val bookId: String = checkNotNull(savedStateHandle.get<String>(ARG_BOOK_ID)) { "bookId ausente" }

    private val timeline = combine(
        bookDao.observe(bookId),
        chapterDao.observeForBook(bookId),
        bookmarkRepository.observe(bookId),
    ) { book, chapters, bookmarks ->
        BookTimeline(book, chapters.map { TimelineChapter(it.title, it.startMs) }, bookmarks)
    }

    private val pendingRangeStart = MutableStateFlow<Long?>(null)
    private val noteRequests = Channel<Long>(Channel.BUFFERED)

    /** Id da marcação recém-criada, para abrir o campo opcional de nota. */
    val noteRequestFlow = noteRequests.receiveAsFlow()

    val state: StateFlow<PlayerScreenState> = combine(
        timeline,
        player.state,
        settings.playback.map { it.skipIntervalSeconds },
        pendingRangeStart,
    ) { (book, chapters, bookmarks), playerState, skipSeconds, pendingStart ->
        if (book == null) return@combine PlayerScreenState(loading = false)
        val isCurrent = playerState.connected && playerState.bookId == book.id
        val duration = if (isCurrent && playerState.durationMs > 0) playerState.durationMs else book.durationMs
        val position = if (isCurrent) playerState.positionMs else 0L
        val chapterTimeline = ChapterTimeline(chapters, duration)
        PlayerScreenState(
            loading = false,
            book = book,
            isCurrent = isCurrent,
            isPlaying = isCurrent && playerState.playWhenReady,
            isBuffering = isCurrent && playerState.isBuffering,
            positionMs = position,
            durationMs = duration,
            speed = if (isCurrent) playerState.speed else 1f,
            chapter = chapterTimeline.positionAt(position),
            chapters = chapterTimeline.all(),
            bookmarks = bookmarks.map { bookmark ->
                val at = chapterTimeline.positionAt(bookmark.startMs)
                BookmarkItem(bookmark.id, bookmark.startMs, bookmark.endMs, bookmark.note, at.index + 1, at.title)
            },
            pendingRangeStartMs = pendingStart,
            rangeActive = isCurrent && playerState.rangeActive,
            rangeStartMs = playerState.rangeStartMs,
            rangeEndMs = playerState.rangeEndMs,
            rangeRepeat = playerState.rangeRepeat,
            timerRemainingMs = playerState.timerRemainingMs.takeIf { isCurrent },
            timerEndOfChapter = playerState.timerEndOfChapter,
            chapterSource = book.chapterSource,
            skipSeconds = skipSeconds,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlayerScreenState())

    private val chapters: StateFlow<List<TimelineChapter>> = timeline
        .map { it.chapters }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        // Abrir o player carrega o livro no serviço sem mudar o estado de tocar/pausar.
        player.load(bookId, play = false)
    }

    private fun currentTimeline(): ChapterTimeline = ChapterTimeline(chapters.value, state.value.durationMs)

    fun playPause() = player.togglePlayPause(bookId)

    fun seekBack() = player.seekBack()

    fun seekForward() = player.seekForward()

    fun previousChapter() = player.seekTo(currentTimeline().previousTarget(state.value.positionMs))

    fun nextChapter() {
        currentTimeline().nextTarget(state.value.positionMs)?.let(player::seekTo)
    }

    fun playChapter(startMs: Long) {
        player.seekTo(startMs)
        player.load(bookId, play = true)
    }

    /** Busca dentro do capítulo atual; [fraction] vai de 0 a 1. */
    fun seekInChapter(fraction: Float) {
        val chapter = state.value.chapter
        player.seekTo(chapter.startMs + (chapter.durationMs * fraction.coerceIn(0f, 1f)).toLong())
    }

    fun cycleSpeed() = player.setSpeed(PlaybackSpeed.next(state.value.speed))

    fun setSpeed(speed: Float) = player.setSpeed(PlaybackSpeed.normalize(speed))

    // ---------- Capa (seção 8) ----------

    fun cameraUri(): Uri = covers.cameraUri()

    fun setCoverFromUri(uri: Uri) = coverAction { covers.setFromUri(bookId, uri) }

    fun setCoverFromCamera() = coverAction { covers.setFromCamera(bookId) }

    fun setCoverFromThumbnail() = coverAction { covers.setFromThumbnail(bookId) }

    fun removeCover() {
        viewModelScope.launch { covers.remove(bookId) }
    }

    private fun coverAction(block: suspend () -> Boolean) {
        viewModelScope.launch {
            val ok = runCatching { block() }.getOrDefault(false)
            if (!ok) messages.send(PlayerMessage.CoverFailed)
        }
    }

    // ---------- Exportar e bateria ----------

    fun exportBook(uri: Uri) {
        viewModelScope.launch {
            val result = runCatching { backup.exportTo(uri, bookId) }
            messages.send(if (result.isSuccess) PlayerMessage.Exported else PlayerMessage.ExportFailed(result.exceptionOrNull()?.localizedMessage))
        }
    }

    fun dismissBatteryCard() {
        viewModelScope.launch { settings.setBatteryCardDismissed(true) }
    }

    // ---------- Timer de sono (seção 6.3) ----------

    fun setTimer(minutes: Int) = player.setTimer(minutes, endOfChapter = false)

    fun setTimerEndOfChapter() = player.setTimer(0, endOfChapter = true)

    fun cancelTimer() = player.cancelTimer()

    // ---------- Marcações (seção 7) ----------

    /** Botão Marcar: ponto na posição atual; depois abre o campo opcional de nota. */
    fun markPoint() {
        val current = state.value
        if (!current.isCurrent) return
        viewModelScope.launch {
            noteRequests.send(bookmarkRepository.addPoint(bookId, current.positionMs))
        }
    }

    /** Botão Trecho: o primeiro toque marca o início; o segundo (Concluir) cria o trecho. */
    fun toggleRange() {
        val current = state.value
        if (!current.isCurrent) return
        val start = pendingRangeStart.value
        if (start == null) {
            pendingRangeStart.value = current.positionMs
            return
        }
        pendingRangeStart.value = null
        if (start == current.positionMs) return
        viewModelScope.launch {
            noteRequests.send(bookmarkRepository.addRange(bookId, start, current.positionMs))
        }
    }

    fun cancelPendingRange() {
        pendingRangeStart.value = null
    }

    /** Ponto: vai para a posição e toca. Trecho: toca do início e pausa no fim (ou repete). */
    fun playBookmark(item: BookmarkItem, repeat: Boolean = false) {
        val end = item.endMs
        if (end == null) {
            player.playFrom(bookId, item.startMs)
        } else {
            player.playRange(item.startMs, end, repeat)
        }
    }

    fun setRangeRepeat(repeat: Boolean) = player.setRangeRepeat(repeat)

    fun stopRange() = player.stopRange()

    fun updateNote(id: Long, note: String) {
        viewModelScope.launch { bookmarkRepository.updateNote(id, note) }
    }

    fun deleteBookmark(id: Long) {
        viewModelScope.launch { bookmarkRepository.delete(id) }
    }

    companion object {
        const val ARG_BOOK_ID = "bookId"
    }
}
