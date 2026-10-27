package com.tiagomoura.audiolivros.chapters

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tiagomoura.audiolivros.core.database.Book
import com.tiagomoura.audiolivros.core.database.BookDao
import com.tiagomoura.audiolivros.core.database.ChapterSource
import com.tiagomoura.audiolivros.player.PlayerConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class EditorChapter(
    val id: Long,
    val number: Int,
    val title: String,
    val startMs: Long,
    val durationMs: Long,
    val source: ChapterSource,
)

data class ChapterEditorState(
    val loading: Boolean = true,
    val book: Book? = null,
    val chapters: List<EditorChapter> = emptyList(),
    /** Posição atual, se o livro estiver carregado no player. */
    val currentPositionMs: Long? = null,
    val busy: Boolean = false,
)

sealed interface EditorMessage {
    data class Detected(val count: Int) : EditorMessage
    data object NothingFound : EditorMessage
    data class Failed(val reason: String?) : EditorMessage
}

@HiltViewModel
class ChapterEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    bookDao: BookDao,
    private val chapters: ChapterRepository,
    private val player: PlayerConnection,
) : ViewModel() {

    val bookId: String = checkNotNull(savedStateHandle.get<String>(ARG_BOOK_ID))
    val openReanalysis: Boolean = savedStateHandle.get<Boolean>(ARG_REANALYZE) ?: false

    private val busy = MutableStateFlow(false)
    private val messages = Channel<EditorMessage>(Channel.BUFFERED)
    val messageFlow = messages.receiveAsFlow()
    private var previewJob: Job? = null

    val state: StateFlow<ChapterEditorState> = combine(
        bookDao.observe(bookId),
        chapters.observe(bookId),
        player.state,
        busy,
    ) { book, list, playerState, isBusy ->
        val duration = book?.durationMs?.takeIf { it > 0 }
            ?: playerState.durationMs.takeIf { playerState.bookId == bookId } ?: 0L
        ChapterEditorState(
            loading = false,
            book = book,
            chapters = list.mapIndexed { index, chapter ->
                val end = list.getOrNull(index + 1)?.startMs ?: duration
                EditorChapter(chapter.id, index + 1, chapter.title, chapter.startMs, (end - chapter.startMs).coerceAtLeast(0), chapter.source)
            },
            currentPositionMs = playerState.positionMs.takeIf { playerState.connected && playerState.bookId == bookId },
            busy = isBusy,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChapterEditorState())

    init {
        player.load(bookId, play = false)
    }

    fun rename(chapterId: Long, title: String) {
        if (title.isBlank()) return
        viewModelScope.launch { chapters.rename(chapterId, title) }
    }

    fun nudge(chapter: EditorChapter, deltaMs: Long) {
        viewModelScope.launch { chapters.setStart(chapter.id, chapter.startMs + deltaMs) }
    }

    /** Ouve 5 s antes do ponto de início (para conferir se o corte está certo). */
    fun previewBefore(chapter: EditorChapter) = preview(chapter.startMs - PREVIEW_MS)

    /** Ouve os primeiros 5 s do capítulo. */
    fun previewAfter(chapter: EditorChapter) = preview(chapter.startMs)

    private fun preview(fromMs: Long) {
        previewJob?.cancel()
        player.playFrom(bookId, fromMs.coerceAtLeast(0))
        previewJob = viewModelScope.launch {
            delay(PREVIEW_MS + 300)
            player.pause()
        }
    }

    fun addAtCurrentPosition(title: String) {
        val position = state.value.currentPositionMs ?: return
        viewModelScope.launch { chapters.add(bookId, position, title) }
    }

    fun delete(chapterId: Long) {
        viewModelScope.launch { chapters.delete(chapterId) }
    }

    fun reanalyzeEmbedded() = reanalyze { chapters.applyEmbedded(bookId) }

    fun reanalyzeDescription(text: String) = reanalyze { chapters.applyDescription(bookId, text) }

    fun reanalyzeSubtitle(uri: Uri) = reanalyze { chapters.applySubtitle(bookId, uri) }

    private fun reanalyze(block: suspend () -> Int) {
        viewModelScope.launch {
            busy.value = true
            val message = try {
                val count = block()
                if (count > 0) EditorMessage.Detected(count) else EditorMessage.NothingFound
            } catch (e: Exception) {
                EditorMessage.Failed(e.localizedMessage)
            } finally {
                busy.value = false
            }
            messages.send(message)
        }
    }

    companion object {
        const val ARG_BOOK_ID = "bookId"
        const val ARG_REANALYZE = "reanalisar"
        private const val PREVIEW_MS = 5_000L
    }
}
