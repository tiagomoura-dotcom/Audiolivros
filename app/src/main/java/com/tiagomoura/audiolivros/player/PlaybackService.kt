package com.tiagomoura.audiolivros.player

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.tiagomoura.audiolivros.MainActivity
import com.tiagomoura.audiolivros.R
import com.tiagomoura.audiolivros.backup.PositionFileWriter
import com.tiagomoura.audiolivros.core.database.Book
import com.tiagomoura.audiolivros.core.database.BookDao
import com.tiagomoura.audiolivros.core.database.Bookmark
import com.tiagomoura.audiolivros.core.database.BookmarkDao
import com.tiagomoura.audiolivros.core.database.ChapterDao
import com.tiagomoura.audiolivros.core.database.PlaybackState
import com.tiagomoura.audiolivros.core.database.PlaybackStateDao
import com.tiagomoura.audiolivros.core.di.ApplicationScope
import com.tiagomoura.audiolivros.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Serviço de reprodução: a única fonte da verdade sobre a posição atual.
 * As telas enviam só o id do livro; o serviço resolve o arquivo, a posição
 * salva e a velocidade, e grava a posição conforme a seção 6.4.
 */
@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject lateinit var bookDao: BookDao
    @Inject lateinit var playbackStateDao: PlaybackStateDao
    @Inject lateinit var chapterDao: ChapterDao
    @Inject lateinit var bookmarkDao: BookmarkDao
    @Inject lateinit var positionFiles: PositionFileWriter
    @Inject lateinit var settings: SettingsRepository
    @Inject @field:ApplicationScope lateinit var appScope: CoroutineScope

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var player: ExoPlayer
    private var session: MediaSession? = null
    private var periodicSave: Job? = null
    @Volatile private var rewindOnResumeMs = 0L
    @Volatile private var skipMs = 30_000L

    /** Timer de sono (seção 6.3): tempo restante de reprodução, ou até o fim do capítulo. */
    private var timerRemainingMs: Long? = null
    private var timerEndOfChapter = false
    private var timerTargetEndMs = 0L
    private var timerJob: Job? = null
    private var timerLabel: String? = null

    /** Livro atual e seus capítulos, observados do banco. */
    private val currentBookId = MutableStateFlow<String?>(null)
    @Volatile private var chapters: List<TimelineChapter> = emptyList()
    private var lastChapterIndex = -1
    private var speedAppliedFor: String? = null

    /** Trecho tocando agora (seção 7): para no fim ou repete até o usuário parar. */
    private data class PlaybackRange(val startMs: Long, val endMs: Long, val repeat: Boolean)
    private var range: PlaybackRange? = null
    private var rangeJob: Job? = null
    private var internalSeek = false

    override fun onCreate() {
        super.onCreate()
        val playbackSettings = runBlocking { settings.currentPlayback() }
        skipMs = playbackSettings.skipIntervalSeconds * 1000L
        rewindOnResumeMs = playbackSettings.rewindOnResumeSeconds * 1000L

        val extractors = DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true)
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this, extractors))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setSeekBackIncrementMs(skipMs)
            .setSeekForwardIncrementMs(skipMs)
            .build()
        player.addListener(PlayerListener())

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        observeChapters()
        observeSettings()

        session = MediaSession.Builder(this, ChapterAwarePlayer(player, ::timeline))
            .setCallback(SessionCallback())
            .setSessionActivity(openApp)
            .setMediaButtonPreferences(mediaButtons(playbackSettings.skipIntervalSeconds))
            .build()
    }

    private fun timeline(): ChapterTimeline =
        ChapterTimeline(chapters, player.duration.takeIf { it > 0 } ?: 0L)

    @kotlin.OptIn(ExperimentalCoroutinesApi::class)
    private fun observeChapters() {
        serviceScope.launch {
            currentBookId
                .flatMapLatest { id -> if (id == null) flowOf(emptyList()) else chapterDao.observeForBook(id) }
                .collect { list ->
                    chapters = list.map { TimelineChapter(it.title, it.startMs) }
                    lastChapterIndex = -1
                    updateSessionMetadata()
                }
        }
        // Capa trocada durante a reprodução: atualiza a notificação e a tela bloqueada.
        serviceScope.launch {
            currentBookId
                .flatMapLatest { id -> if (id == null) flowOf(null) else bookDao.observe(id) }
                .map { it?.coverPath to it?.coverPath?.let { path -> File(path).lastModified() } }
                .distinctUntilChanged()
                .collect { (coverPath, _) -> updateArtwork(coverPath) }
        }
    }

    /**
     * Notificação e tela bloqueada: título do livro e, abaixo, o capítulo atual.
     * Troca só os metadados do item, sem interromper o áudio.
     */
    private fun updateSessionMetadata() {
        val item = player.currentMediaItem ?: return
        val author = item.mediaMetadata.extras?.getString(EXTRA_AUTHOR)
        val chapterTitle = chapters.takeIf { it.isNotEmpty() }?.let { timeline().positionAt(player.currentPosition).title }
        val subtitle = listOfNotNull(chapterTitle, extraSubtitle()).joinToString(" · ").ifEmpty { author }
        if (item.mediaMetadata.artist?.toString() == subtitle) return
        val metadata = item.mediaMetadata.buildUpon()
            .setArtist(subtitle)
            .setSubtitle(subtitle)
            .build()
        player.replaceMediaItem(player.currentMediaItemIndex, item.buildUpon().setMediaMetadata(metadata).build())
    }

    private fun updateArtwork(coverPath: String?) {
        val item = player.currentMediaItem ?: return
        val artwork = coverPath?.let { File(it) }?.takeIf { it.exists() }
            ?.let { Uri.fromFile(it).buildUpon().appendQueryParameter("v", it.lastModified().toString()).build() }
        if (item.mediaMetadata.artworkUri == artwork) return
        val metadata = item.mediaMetadata.buildUpon().setArtworkUri(artwork).build()
        player.replaceMediaItem(player.currentMediaItemIndex, item.buildUpon().setMediaMetadata(metadata).build())
    }

    /** Texto extra no subtítulo da notificação: o tempo restante do timer de sono. */
    private fun extraSubtitle(): String? = timerLabel

    /** Intervalo de pulo e retrocesso ao retomar mudam sem reiniciar o serviço. */
    private fun observeSettings() {
        serviceScope.launch {
            var lastSkip = -1
            settings.playback.collect { value ->
                skipMs = value.skipIntervalSeconds * 1000L
                rewindOnResumeMs = value.rewindOnResumeSeconds * 1000L
                if (value.skipIntervalSeconds != lastSkip) {
                    lastSkip = value.skipIntervalSeconds
                    session?.setMediaButtonPreferences(mediaButtons(value.skipIntervalSeconds))
                }
            }
        }
    }

    private fun seekBy(deltaMs: Long) {
        val duration = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        player.seekTo((player.currentPosition + deltaMs).coerceIn(0, duration))
    }

    private fun startTimer(minutes: Int, endOfChapter: Boolean) {
        timerJob?.cancel()
        timerEndOfChapter = endOfChapter
        timerTargetEndMs = if (endOfChapter) timeline().positionAt(player.currentPosition).endMs else 0L
        timerRemainingMs = if (endOfChapter) remainingInChapter() else minutes * 60_000L
        timerJob = serviceScope.launch {
            var last = SystemClock.elapsedRealtime()
            while (isActive) {
                delay(TIMER_TICK_MS)
                val now = SystemClock.elapsedRealtime()
                val remaining = if (timerEndOfChapter) {
                    remainingInChapter()
                } else {
                    val current = timerRemainingMs ?: break
                    if (player.isPlaying) current - (now - last) else current
                }
                last = now
                timerRemainingMs = remaining
                // Nos últimos 10 s, o volume abaixa aos poucos até pausar.
                player.volume = (remaining.toFloat() / FADE_MS).coerceIn(0f, 1f)
                if (remaining <= 0) {
                    player.pause()
                    cancelTimer()
                    break
                }
                refreshTimerLabel()
            }
        }
        refreshTimerLabel()
    }

    private fun remainingInChapter(): Long {
        val speed = player.playbackParameters.speed.takeIf { it > 0 } ?: 1f
        return ((timerTargetEndMs - player.currentPosition) / speed).toLong().coerceAtLeast(0)
    }

    private fun cancelTimer() {
        timerJob?.cancel()
        timerJob = null
        timerRemainingMs = null
        timerEndOfChapter = false
        player.volume = 1f
        refreshTimerLabel()
    }

    private fun refreshTimerLabel() {
        val remaining = timerRemainingMs
        val label = when {
            remaining == null -> null
            remaining >= 60_000 -> getString(R.string.timer_notificacao_min, ((remaining + 59_999) / 60_000).toInt())
            else -> getString(R.string.timer_notificacao_s, ((remaining + 999) / 1000).toInt())
        }
        publishState()
        if (label != timerLabel) {
            timerLabel = label
            updateSessionMetadata()
        }
    }

    /** A cada segundo de reprodução: detecta troca de capítulo e salva a posição a cada 5 s. */
    private fun onPlaybackTick(tick: Long) {
        val index = timeline().indexAt(player.currentPosition)
        if (index != lastChapterIndex) {
            if (lastChapterIndex >= 0) savePosition()
            lastChapterIndex = index
            updateSessionMetadata()
        }
        if (tick % (SAVE_INTERVAL_MS / TICK_MS) == 0L) savePosition()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        savePosition()
        // Continua tocando se o usuário fechou o app pela multitarefa durante a reprodução.
        if (!player.playWhenReady || player.mediaItemCount == 0 || player.playbackState == Player.STATE_ENDED) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        snapshot()?.let { snapshot ->
            runBlocking(Dispatchers.IO) { persist(snapshot) }
        }
        periodicSave?.cancel()
        session?.release()
        session = null
        player.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun mediaButtons(skipSeconds: Int): List<CommandButton> {
        val backIcon = when (skipSeconds) {
            10 -> CommandButton.ICON_SKIP_BACK_10
            15 -> CommandButton.ICON_SKIP_BACK_15
            30 -> CommandButton.ICON_SKIP_BACK_30
            else -> CommandButton.ICON_SKIP_BACK
        }
        val forwardIcon = when (skipSeconds) {
            10 -> CommandButton.ICON_SKIP_FORWARD_10
            15 -> CommandButton.ICON_SKIP_FORWARD_15
            30 -> CommandButton.ICON_SKIP_FORWARD_30
            else -> CommandButton.ICON_SKIP_FORWARD
        }
        return listOf(
            CommandButton.Builder(backIcon)
                .setDisplayName(getString(R.string.voltar_segundos, skipSeconds))
                .setSessionCommand(COMMAND_SEEK_BACK)
                .setSlots(CommandButton.SLOT_BACK)
                .build(),
            CommandButton.Builder(forwardIcon)
                .setDisplayName(getString(R.string.avancar_segundos, skipSeconds))
                .setSessionCommand(COMMAND_SEEK_FORWARD)
                .setSlots(CommandButton.SLOT_FORWARD)
                .build(),
            CommandButton.Builder(CommandButton.ICON_UNDEFINED)
                .setCustomIconResId(R.drawable.ic_bookmark_add)
                .setDisplayName(getString(R.string.marcar))
                .setSessionCommand(COMMAND_ADD_BOOKMARK)
                .setSlots(CommandButton.SLOT_OVERFLOW)
                .build(),
        )
    }

    /** Botão Marcar da notificação: cria um ponto sem desbloquear o celular. */
    private fun addBookmarkFromNotification() {
        val bookId = player.currentMediaItem?.mediaId ?: return
        val position = player.currentPosition.coerceAtLeast(0)
        val note = getString(R.string.marcado_pela_notificacao)
        appScope.launch(Dispatchers.IO) {
            runCatching {
                bookmarkDao.insert(
                    Bookmark(bookId = bookId, startMs = position, endMs = null, note = note, createdAt = System.currentTimeMillis()),
                )
            }
        }
    }

    private fun playRange(startMs: Long, endMs: Long, repeat: Boolean) {
        if (endMs <= startMs) return
        range = PlaybackRange(startMs, endMs, repeat)
        seekInternally(startMs)
        player.play()
        rangeJob?.cancel()
        rangeJob = serviceScope.launch {
            while (isActive) {
                delay(RANGE_CHECK_MS)
                val current = range ?: break
                if (player.currentPosition >= current.endMs) {
                    if (current.repeat) {
                        seekInternally(current.startMs)
                    } else {
                        player.pause()
                        stopRange()
                    }
                }
            }
        }
        publishState()
    }

    private fun stopRange() {
        range = null
        rangeJob?.cancel()
        rangeJob = null
        publishState()
    }

    private fun seekInternally(positionMs: Long) {
        internalSeek = true
        player.seekTo(positionMs)
    }

    /** Estado extra para as telas (trecho e, no marco 6, timer), nos extras da sessão. */
    private fun publishState() {
        val current = range
        session?.setSessionExtras(
            bundleOf(
                EXTRA_RANGE_ACTIVE to (current != null),
                EXTRA_RANGE_START to (current?.startMs ?: 0L),
                EXTRA_RANGE_END to (current?.endMs ?: 0L),
                EXTRA_RANGE_REPEAT to (current?.repeat ?: false),
                EXTRA_TIMER_ACTIVE to (timerRemainingMs != null),
                EXTRA_TIMER_REMAINING_MS to (timerRemainingMs ?: 0L),
                EXTRA_TIMER_END_OF_CHAPTER to timerEndOfChapter,
            ),
        )
    }

    /** Estado atual do player, capturado na thread principal. */
    private data class Snapshot(
        val bookId: String,
        val positionMs: Long,
        val durationMs: Long,
        val speed: Float,
        val playing: Boolean,
    )

    private fun snapshot(): Snapshot? {
        val item = player.currentMediaItem ?: return null
        if (item.mediaId.isEmpty()) return null
        return Snapshot(
            bookId = item.mediaId,
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = player.duration,
            speed = player.playbackParameters.speed,
            playing = player.isPlaying,
        )
    }

    /** Grava a posição sem bloquear; a gravação continua mesmo se o serviço for encerrado. */
    private fun savePosition() {
        val snapshot = snapshot() ?: return
        appScope.launch(Dispatchers.IO) { persist(snapshot) }
    }

    private suspend fun persist(snapshot: Snapshot) {
        try {
            playbackStateDao.upsert(
                PlaybackState(
                    bookId = snapshot.bookId,
                    positionMs = snapshot.positionMs,
                    speed = snapshot.speed,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            if (snapshot.durationMs > 0) {
                bookDao.updateDurationIfUnknown(snapshot.bookId, snapshot.durationMs)
                if (snapshot.positionMs >= snapshot.durationMs - FINISHED_THRESHOLD_MS) {
                    bookDao.updateFinished(snapshot.bookId, true)
                }
            }
            // Arquivo de posição automático: ao pausar e no máximo uma vez por minuto tocando.
            positionFiles.write(snapshot.bookId, force = !snapshot.playing)
        } catch (e: Exception) {
            // Livro excluído durante a reprodução, por exemplo. Nunca derrubar o serviço por isso.
            Log.w(TAG, "Não foi possível salvar a posição de ${snapshot.bookId}", e)
        }
    }

    private fun buildMediaItem(book: Book, speed: Float): MediaItem {
        // O "?v=" muda quando a capa é trocada, para a notificação não mostrar a antiga do cache.
        val artwork: Uri? = book.coverPath?.let { File(it) }?.takeIf { it.exists() }
            ?.let { Uri.fromFile(it).buildUpon().appendQueryParameter("v", it.lastModified().toString()).build() }
        val metadata = MediaMetadata.Builder()
            .setTitle(book.title)
            .setArtist(book.author)
            .setAlbumTitle(book.title)
            .setArtworkUri(artwork)
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_AUDIO_BOOK)
            .setExtras(bundleOf(EXTRA_SPEED to speed, EXTRA_AUTHOR to book.author))
            .build()
        return MediaItem.Builder()
            .setMediaId(book.id)
            .setUri(book.audioUri.toUri())
            .setMediaMetadata(metadata)
            .build()
    }

    private inner class SessionCallback : MediaSession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(COMMAND_SEEK_BACK)
                .add(COMMAND_SEEK_FORWARD)
                .add(COMMAND_ADD_BOOKMARK)
                .add(COMMAND_PLAY_RANGE)
                .add(COMMAND_STOP_RANGE)
                .add(COMMAND_SET_RANGE_REPEAT)
                .add(COMMAND_SET_TIMER)
                .add(COMMAND_CANCEL_TIMER)
                .build()
            val builder = MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
            if (session.isMediaNotificationController(controller)) {
                // Na notificação e na tela bloqueada, os botões laterais são −30 s e +30 s.
                val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                    .remove(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .remove(Player.COMMAND_SEEK_TO_NEXT)
                    .remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .build()
                builder.setAvailablePlayerCommands(playerCommands)
            }
            return builder.build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                ACTION_SEEK_BACK -> seekBy(-skipMs)
                ACTION_SEEK_FORWARD -> seekBy(skipMs)
                ACTION_SET_TIMER -> startTimer(args.getInt(ARG_MINUTES), args.getBoolean(ARG_END_OF_CHAPTER))
                ACTION_CANCEL_TIMER -> cancelTimer()
                ACTION_ADD_BOOKMARK -> addBookmarkFromNotification()
                ACTION_PLAY_RANGE -> playRange(
                    args.getLong(ARG_START_MS),
                    args.getLong(ARG_END_MS),
                    args.getBoolean(ARG_REPEAT),
                )
                ACTION_STOP_RANGE -> stopRange()
                ACTION_SET_RANGE_REPEAT -> range?.let {
                    range = it.copy(repeat = args.getBoolean(ARG_REPEAT))
                    publishState()
                }
                else -> return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = serviceScope.future {
            // Antes de trocar de livro, grava a posição do atual.
            savePosition()
            val requestedId = mediaItems.getOrNull(startIndex.coerceAtLeast(0))?.mediaId
                ?: mediaItems.firstOrNull()?.mediaId
            val book = requestedId?.let { bookDao.get(it) }
                ?: throw IllegalArgumentException("Livro não encontrado: $requestedId")
            check(book.audioUri.isNotEmpty()) { "O áudio de ${book.id} ainda não foi baixado" }
            val saved = playbackStateDao.get(book.id)
            val start = when {
                startPositionMs != C.TIME_UNSET -> startPositionMs
                saved == null -> 0L
                book.finished && book.durationMs > 0 &&
                    saved.positionMs >= book.durationMs - FINISHED_THRESHOLD_MS -> 0L
                else -> (saved.positionMs - rewindOnResumeMs).coerceAtLeast(0)
            }
            MediaSession.MediaItemsWithStartPosition(
                listOf(buildMediaItem(book, saved?.speed ?: 1f)),
                0,
                start,
            )
        }
    }

    private inner class PlayerListener : Player.Listener {

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val bookId = mediaItem?.mediaId
            currentBookId.value = bookId
            // A velocidade salva só é aplicada ao carregar o livro, não quando só os metadados mudam.
            if (bookId != null && bookId != speedAppliedFor) {
                speedAppliedFor = bookId
                lastChapterIndex = -1
                val speed = mediaItem?.mediaMetadata?.extras?.getFloat(EXTRA_SPEED, 1f) ?: 1f
                if (player.playbackParameters.speed != speed) player.setPlaybackSpeed(speed)
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            periodicSave?.cancel()
            if (isPlaying) {
                player.currentMediaItem?.mediaId?.let { bookId ->
                    appScope.launch(Dispatchers.IO) {
                        runCatching { bookDao.updateLastPlayed(bookId, System.currentTimeMillis()) }
                    }
                }
                periodicSave = serviceScope.launch {
                    var tick = 0L
                    while (isActive) {
                        delay(TICK_MS)
                        onPlaybackTick(++tick)
                    }
                }
            } else {
                savePosition()
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                // Uma busca do usuário encerra o trecho; as do próprio trecho, não.
                if (internalSeek) internalSeek = false else if (range != null) stopRange()
                savePosition()
                updateSessionMetadata()
            }
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            savePosition()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) savePosition()
        }
    }

    companion object {
        private const val TAG = "PlaybackService"
        private const val SAVE_INTERVAL_MS = 5_000L
        private const val FINISHED_THRESHOLD_MS = 30_000L
        private const val EXTRA_SPEED = "audiolivros.velocidade"
        private const val EXTRA_AUTHOR = "audiolivros.autor"
        private const val TICK_MS = 1_000L

        const val ACTION_SEEK_BACK = "audiolivros.VOLTAR"
        const val ACTION_SEEK_FORWARD = "audiolivros.AVANCAR"
        val COMMAND_SEEK_BACK = SessionCommand(ACTION_SEEK_BACK, Bundle.EMPTY)
        val COMMAND_SEEK_FORWARD = SessionCommand(ACTION_SEEK_FORWARD, Bundle.EMPTY)

        const val ACTION_ADD_BOOKMARK = "audiolivros.MARCAR"
        const val ACTION_PLAY_RANGE = "audiolivros.TOCAR_TRECHO"
        const val ACTION_STOP_RANGE = "audiolivros.PARAR_TRECHO"
        const val ACTION_SET_RANGE_REPEAT = "audiolivros.REPETIR_TRECHO"
        val COMMAND_ADD_BOOKMARK = SessionCommand(ACTION_ADD_BOOKMARK, Bundle.EMPTY)
        val COMMAND_PLAY_RANGE = SessionCommand(ACTION_PLAY_RANGE, Bundle.EMPTY)
        val COMMAND_STOP_RANGE = SessionCommand(ACTION_STOP_RANGE, Bundle.EMPTY)
        val COMMAND_SET_RANGE_REPEAT = SessionCommand(ACTION_SET_RANGE_REPEAT, Bundle.EMPTY)

        const val ARG_START_MS = "inicio"
        const val ARG_END_MS = "fim"
        const val ARG_REPEAT = "repetir"

        const val EXTRA_RANGE_ACTIVE = "trecho.ativo"
        const val EXTRA_RANGE_START = "trecho.inicio"
        const val EXTRA_RANGE_END = "trecho.fim"
        const val EXTRA_RANGE_REPEAT = "trecho.repetir"
        private const val RANGE_CHECK_MS = 100L

        const val ACTION_SET_TIMER = "audiolivros.TIMER"
        const val ACTION_CANCEL_TIMER = "audiolivros.CANCELAR_TIMER"
        val COMMAND_SET_TIMER = SessionCommand(ACTION_SET_TIMER, Bundle.EMPTY)
        val COMMAND_CANCEL_TIMER = SessionCommand(ACTION_CANCEL_TIMER, Bundle.EMPTY)
        const val ARG_MINUTES = "minutos"
        const val ARG_END_OF_CHAPTER = "fim_do_capitulo"
        const val EXTRA_TIMER_ACTIVE = "timer.ativo"
        const val EXTRA_TIMER_REMAINING_MS = "timer.restante"
        const val EXTRA_TIMER_END_OF_CHAPTER = "timer.fim_do_capitulo"
        private const val TIMER_TICK_MS = 250L
        private const val FADE_MS = 10_000f
    }
}
