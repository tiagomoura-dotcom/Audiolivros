package com.tiagomoura.audiolivros.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tiagomoura.audiolivros.core.database.Book
import com.tiagomoura.audiolivros.core.database.BookDao
import com.tiagomoura.audiolivros.core.database.ChapterDao
import com.tiagomoura.audiolivros.core.database.ChapterSource
import com.tiagomoura.audiolivros.settings.PlaybackSettings
import com.tiagomoura.audiolivros.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

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
    val chapterSource: ChapterSource? = null,
    val skipSeconds: Int = PlaybackSettings.DEFAULT_SKIP_SECONDS,
) {
    val bookProgress: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val chapterElapsedMs: Long get() = (positionMs - chapter.startMs).coerceAtLeast(0)
    val chapterProgress: Float
        get() = if (chapter.durationMs > 0) (chapterElapsedMs.toFloat() / chapter.durationMs).coerceIn(0f, 1f) else 0f
}

@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    bookDao: BookDao,
    chapterDao: ChapterDao,
    settings: SettingsRepository,
    private val player: PlayerConnection,
) : ViewModel() {

    val bookId: String = checkNotNull(savedStateHandle.get<String>(ARG_BOOK_ID)) { "bookId ausente" }

    private val timeline = combine(bookDao.observe(bookId), chapterDao.observeForBook(bookId)) { book, chapters ->
        book to chapters.map { TimelineChapter(it.title, it.startMs) }
    }

    val state: StateFlow<PlayerScreenState> = combine(
        timeline,
        player.state,
        settings.playback.map { it.skipIntervalSeconds },
    ) { (book, chapters), playerState, skipSeconds ->
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
            chapterSource = book.chapterSource,
            skipSeconds = skipSeconds,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlayerScreenState())

    private val chapters: StateFlow<List<TimelineChapter>> = timeline
        .map { it.second }
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

    /** Busca dentro do capítulo atual; [fraction] vai de 0 a 1. */
    fun seekInChapter(fraction: Float) {
        val chapter = state.value.chapter
        player.seekTo(chapter.startMs + (chapter.durationMs * fraction.coerceIn(0f, 1f)).toLong())
    }

    fun cycleSpeed() = player.setSpeed(PlaybackSpeed.next(state.value.speed))

    fun setSpeed(speed: Float) = player.setSpeed(PlaybackSpeed.normalize(speed))

    companion object {
        const val ARG_BOOK_ID = "bookId"
    }
}
