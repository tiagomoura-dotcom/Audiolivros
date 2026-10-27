package com.tiagomoura.audiolivros.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tiagomoura.audiolivros.core.database.BookStatus
import com.tiagomoura.audiolivros.core.database.ChapterSource
import com.tiagomoura.audiolivros.core.database.BookWithProgress
import com.tiagomoura.audiolivros.player.PlayerConnection
import com.tiagomoura.audiolivros.player.PlayerUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Livro como mostrado na biblioteca, com a posição ao vivo se estiver tocando. */
data class LibraryItem(
    val id: String,
    val title: String,
    val author: String?,
    val coverPath: String?,
    val placeholderColor: Int,
    val chapterCount: Int,
    val chapterSource: ChapterSource?,
    val status: BookStatus,
    val processingProgress: Int,
    val finished: Boolean,
    val started: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val lastPlayedAt: Long?,
) {
    val progress: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val remainingMs: Long get() = (durationMs - positionMs).coerceAtLeast(0)
}

data class LibraryUiState(
    val loading: Boolean = true,
    val books: List<LibraryItem> = emptyList(),
    val continueListening: LibraryItem? = null,
    val continueIsPlaying: Boolean = false,
    val importing: Boolean = false,
) {
    val processingCount: Int get() = books.count { it.status == BookStatus.PROCESSING }
}

sealed interface LibraryMessage {
    data class Imported(val title: String) : LibraryMessage
    data class AlreadyImported(val title: String) : LibraryMessage
    data class ImportFailed(val reason: String?) : LibraryMessage
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: BookRepository,
    private val player: PlayerConnection,
) : ViewModel() {

    private val importing = MutableStateFlow(false)
    private val messages = Channel<LibraryMessage>(Channel.BUFFERED)
    val messageFlow = messages.receiveAsFlow()

    val state: StateFlow<LibraryUiState> = combine(
        repository.observeLibrary(),
        player.state,
        importing,
    ) { books, playerState, isImporting ->
        val items = books.map { it.toItem(playerState) }
        val continueItem = items.filter { it.lastPlayedAt != null }.maxByOrNull { it.lastPlayedAt ?: 0 }
        LibraryUiState(
            loading = false,
            books = items,
            continueListening = continueItem,
            continueIsPlaying = continueItem != null && playerState.bookId == continueItem.id && playerState.playWhenReady,
            importing = isImporting,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun import(uri: Uri) {
        viewModelScope.launch {
            importing.value = true
            val message = try {
                val book = repository.import(uri)
                LibraryMessage.Imported(book.title)
            } catch (e: AlreadyImportedException) {
                LibraryMessage.AlreadyImported(e.book.title)
            } catch (e: Exception) {
                LibraryMessage.ImportFailed(e.localizedMessage)
            } finally {
                importing.value = false
            }
            messages.send(message)
        }
    }

    fun togglePlayPause(bookId: String) = player.togglePlayPause(bookId)

    fun rename(bookId: String, title: String) {
        if (title.isBlank()) return
        viewModelScope.launch { repository.rename(bookId, title) }
    }

    fun updateAuthor(bookId: String, author: String) {
        viewModelScope.launch { repository.updateAuthor(bookId, author) }
    }

    fun delete(bookId: String) {
        player.unloadIfCurrent(bookId)
        viewModelScope.launch { repository.delete(bookId) }
    }

    private fun BookWithProgress.toItem(playerState: PlayerUiState): LibraryItem {
        val isCurrent = playerState.bookId == book.id && playerState.connected
        val position = if (isCurrent) playerState.positionMs else positionMs ?: 0L
        val duration = book.durationMs.takeIf { it > 0 } ?: if (isCurrent) playerState.durationMs else 0L
        return LibraryItem(
            id = book.id,
            title = book.title,
            author = book.author,
            coverPath = book.coverPath,
            placeholderColor = book.placeholderColor,
            chapterCount = chapterCount,
            chapterSource = book.chapterSource,
            status = book.status,
            processingProgress = book.processingProgress,
            finished = book.finished,
            started = positionMs != null || isCurrent,
            positionMs = position,
            durationMs = duration,
            lastPlayedAt = book.lastPlayedAt,
        )
    }
}
