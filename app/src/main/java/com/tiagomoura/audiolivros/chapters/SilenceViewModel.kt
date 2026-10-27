package com.tiagomoura.audiolivros.chapters

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tiagomoura.audiolivros.core.database.BookDao
import com.tiagomoura.audiolivros.core.database.ChapterSource
import com.tiagomoura.audiolivros.settings.DetectionSettings
import com.tiagomoura.audiolivros.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Sensitivity(val thresholdDb: Float, val minPauseSeconds: Float, val minChapterMinutes: Float)

data class SilenceState(
    val title: String = "",
    val hasEnvelope: Boolean = false,
    val analysis: AnalysisProgress? = null,
    val sensitivity: Sensitivity = Sensitivity(
        DetectionSettings.DEFAULT_THRESHOLD_DB,
        DetectionSettings.DEFAULT_MIN_PAUSE_S,
        DetectionSettings.DEFAULT_MIN_CHAPTER_MIN,
    ),
    val preview: List<ParsedChapter> = emptyList(),
)

/**
 * Tela de reanálise por pausas: um controle de sensibilidade recalcula a lista
 * em tempo real a partir do envelope salvo, sem decodificar o áudio de novo.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SilenceViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val bookDao: BookDao,
    private val envelopes: EnvelopeStore,
    private val scheduler: ChapterAnalysisScheduler,
    private val chapters: ChapterRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    val bookId: String = checkNotNull(savedStateHandle.get<String>(ARG_BOOK_ID))

    private val envelope = MutableStateFlow<Envelope?>(null)
    private val sensitivity = MutableStateFlow<Sensitivity?>(null)
    private val title = MutableStateFlow("")
    private val applied = Channel<Int>(Channel.BUFFERED)
    val appliedFlow = applied.receiveAsFlow()
    private var durationMs = 0L

    private val preview = combine(envelope, sensitivity) { env, s -> env to s }
        .mapLatest { (env, s) -> if (env == null || s == null) emptyList() else detect(env, s) }
        .flowOn(Dispatchers.Default)

    val state: StateFlow<SilenceState> = combine(
        title,
        envelope,
        sensitivity,
        scheduler.observe(bookId),
        preview,
    ) { bookTitle, env, s, progress, list ->
        SilenceState(
            title = bookTitle,
            hasEnvelope = env != null,
            analysis = progress,
            sensitivity = s ?: SilenceState().sensitivity,
            preview = if (env != null) list else emptyList(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SilenceState())

    init {
        viewModelScope.launch {
            val book = bookDao.get(bookId)
            title.value = book?.title.orEmpty()
            durationMs = book?.durationMs ?: 0L
            val detection = settings.currentDetection()
            sensitivity.value = Sensitivity(detection.silenceThresholdDb, detection.minPauseSeconds, detection.minChapterMinutes)
            loadEnvelope()
        }
        // Quando a análise termina, carrega o envelope recém-salvo.
        viewModelScope.launch {
            scheduler.observe(bookId).collect { progress ->
                if (progress != null && !progress.running && envelope.value == null) loadEnvelope()
            }
        }
    }

    private suspend fun loadEnvelope() {
        envelope.value = withContext(Dispatchers.IO) { envelopes.load(bookId) }
    }

    fun analyze() = scheduler.analyze(bookId, applyChapters = false)

    fun cancel() = scheduler.cancel(bookId)

    fun setSensitivity(value: Sensitivity) {
        sensitivity.value = value
    }

    /** Salva os capítulos da prévia e guarda a sensibilidade como padrão. */
    fun apply() {
        val s = sensitivity.value ?: return
        viewModelScope.launch {
            val env = envelope.value ?: return@launch
            val found = withContext(Dispatchers.Default) { detect(env, s) }
            val current = settings.currentDetection()
            settings.setDetection(
                current.copy(
                    silenceThresholdDb = s.thresholdDb,
                    minPauseSeconds = s.minPauseSeconds,
                    minChapterMinutes = s.minChapterMinutes,
                ),
            )
            applied.send(chapters.applyParsed(bookId, found, ChapterSource.SILENCE))
        }
    }

    private fun detect(env: Envelope, s: Sensitivity): List<ParsedChapter> = SilenceDetector.chapters(
        env,
        thresholdDb = s.thresholdDb,
        minPauseMs = (s.minPauseSeconds * 1000).toLong(),
        minChapterMs = (s.minChapterMinutes * 60_000).toLong(),
        durationMs = durationMs.takeIf { it > 0 } ?: env.durationMs,
    )

    companion object {
        const val ARG_BOOK_ID = "bookId"
    }
}
