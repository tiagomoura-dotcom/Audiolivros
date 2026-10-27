package com.tiagomoura.audiolivros.player

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** O que as telas sabem do player. Vem sempre do MediaController. */
data class PlayerUiState(
    val connected: Boolean = false,
    val bookId: String? = null,
    val isPlaying: Boolean = false,
    val playWhenReady: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0,
    /** Duração informada pelo player, ou 0 se ainda desconhecida. */
    val durationMs: Long = 0,
    val speed: Float = 1f,
    /** Trecho de marcação tocando agora, se houver. */
    val rangeActive: Boolean = false,
    val rangeStartMs: Long = 0,
    val rangeEndMs: Long = 0,
    val rangeRepeat: Boolean = false,
    /** Timer de sono: tempo restante, ou nulo se desligado. */
    val timerRemainingMs: Long? = null,
    val timerEndOfChapter: Boolean = false,
)

/**
 * Conexão das telas com o [PlaybackService] por um MediaController. A posição é
 * lida do controller a cada 500 ms enquanto alguma tela observa o estado.
 */
@Singleton
class PlayerConnection @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val scope = MainScope()
    private val controllerReady = CompletableDeferred<MediaController>()
    private var controller: MediaController? = null

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh()
    }

    init {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token)
            .setListener(object : MediaController.Listener {
                override fun onExtrasChanged(controller: MediaController, extras: Bundle) = refresh()
            })
            .buildAsync()
        future.addListener(
            {
                val connected = runCatching { future.get() }.getOrNull() ?: return@addListener
                controller = connected
                connected.addListener(listener)
                refresh()
                controllerReady.complete(connected)
            },
            ContextCompat.getMainExecutor(context),
        )

        scope.launch {
            _state.subscriptionCount
                .map { it > 0 }
                .distinctUntilChanged()
                .collectLatest { observed ->
                    while (observed) {
                        refresh()
                        delay(POSITION_POLL_MS)
                    }
                }
        }
    }

    private fun refresh() {
        val c = controller ?: return
        val duration = c.duration
        val extras = c.sessionExtras
        _state.value = PlayerUiState(
            connected = true,
            bookId = c.currentMediaItem?.mediaId,
            isPlaying = c.isPlaying,
            playWhenReady = c.playWhenReady,
            isBuffering = c.playbackState == Player.STATE_BUFFERING,
            positionMs = c.currentPosition.coerceAtLeast(0),
            durationMs = if (duration == C.TIME_UNSET || duration < 0) 0 else duration,
            speed = c.playbackParameters.speed,
            rangeActive = extras.getBoolean(PlaybackService.EXTRA_RANGE_ACTIVE),
            rangeStartMs = extras.getLong(PlaybackService.EXTRA_RANGE_START),
            rangeEndMs = extras.getLong(PlaybackService.EXTRA_RANGE_END),
            rangeRepeat = extras.getBoolean(PlaybackService.EXTRA_RANGE_REPEAT),
            timerRemainingMs = extras.getLong(PlaybackService.EXTRA_TIMER_REMAINING_MS)
                .takeIf { extras.getBoolean(PlaybackService.EXTRA_TIMER_ACTIVE) },
            timerEndOfChapter = extras.getBoolean(PlaybackService.EXTRA_TIMER_END_OF_CHAPTER),
        )
    }

    private fun withController(block: (MediaController) -> Unit) {
        scope.launch { block(controllerReady.await()) }
    }

    /** Carrega o livro no serviço (se ainda não estiver carregado) e, opcionalmente, toca. */
    fun load(bookId: String, play: Boolean) = withController { c ->
        if (c.currentMediaItem?.mediaId != bookId) {
            c.setMediaItem(MediaItem.Builder().setMediaId(bookId).build())
            c.prepare()
        } else if (c.playbackState == Player.STATE_IDLE) {
            c.prepare()
        }
        if (play) {
            if (c.playbackState == Player.STATE_ENDED) c.seekTo(0)
            c.play()
        }
    }

    fun togglePlayPause(bookId: String) = withController { c ->
        if (c.currentMediaItem?.mediaId == bookId && c.playWhenReady) {
            c.pause()
        } else {
            load(bookId, play = true)
        }
    }

    /** Carrega o livro, vai até [positionMs] e toca. */
    fun playFrom(bookId: String, positionMs: Long) {
        load(bookId, play = false)
        withController { c ->
            c.seekTo(positionMs.coerceAtLeast(0))
            c.play()
        }
    }

    fun pause() = withController { it.pause() }

    /** Toca um trecho: do início ao fim, pausando no fim, ou em loop se [repeat]. */
    fun playRange(startMs: Long, endMs: Long, repeat: Boolean) = withController {
        it.sendCustomCommand(
            PlaybackService.COMMAND_PLAY_RANGE,
            bundleOf(
                PlaybackService.ARG_START_MS to startMs,
                PlaybackService.ARG_END_MS to endMs,
                PlaybackService.ARG_REPEAT to repeat,
            ),
        )
    }

    fun setRangeRepeat(repeat: Boolean) = withController {
        it.sendCustomCommand(PlaybackService.COMMAND_SET_RANGE_REPEAT, bundleOf(PlaybackService.ARG_REPEAT to repeat))
    }

    fun stopRange() = withController { it.sendCustomCommand(PlaybackService.COMMAND_STOP_RANGE, Bundle.EMPTY) }

    /** Voltar e avançar usam o intervalo das configurações, aplicado pelo serviço. */
    fun seekBack() = withController { it.sendCustomCommand(PlaybackService.COMMAND_SEEK_BACK, Bundle.EMPTY) }

    fun seekForward() = withController { it.sendCustomCommand(PlaybackService.COMMAND_SEEK_FORWARD, Bundle.EMPTY) }

    fun setTimer(minutes: Int, endOfChapter: Boolean) = withController {
        it.sendCustomCommand(
            PlaybackService.COMMAND_SET_TIMER,
            bundleOf(PlaybackService.ARG_MINUTES to minutes, PlaybackService.ARG_END_OF_CHAPTER to endOfChapter),
        )
    }

    fun cancelTimer() = withController { it.sendCustomCommand(PlaybackService.COMMAND_CANCEL_TIMER, Bundle.EMPTY) }

    fun seekTo(positionMs: Long) = withController { it.seekTo(positionMs.coerceAtLeast(0)) }

    fun setSpeed(speed: Float) = withController { it.setPlaybackSpeed(speed) }

    /** Para e descarrega o livro, se for o atual (antes de excluí-lo). */
    fun unloadIfCurrent(bookId: String) = withController { c ->
        if (c.currentMediaItem?.mediaId == bookId) {
            c.stop()
            c.clearMediaItems()
        }
    }

    private companion object {
        const val POSITION_POLL_MS = 500L
    }
}
