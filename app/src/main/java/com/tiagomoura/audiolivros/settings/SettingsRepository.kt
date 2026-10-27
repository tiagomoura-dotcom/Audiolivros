package com.tiagomoura.audiolivros.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.tiagomoura.audiolivros.chapters.SubtitleChapterDetector
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "configuracoes")

data class PlaybackSettings(
    val skipIntervalSeconds: Int = DEFAULT_SKIP_SECONDS,
    val rewindOnResumeSeconds: Int = DEFAULT_REWIND_SECONDS,
) {
    companion object {
        const val DEFAULT_SKIP_SECONDS = 30
        const val DEFAULT_REWIND_SECONDS = 3
        val SKIP_OPTIONS = listOf(10, 15, 30, 60)
        val REWIND_OPTIONS = listOf(0, 3, 5, 10)
    }
}

/** Parâmetros da detecção de capítulos (seção 2.5). */
data class DetectionSettings(
    val silenceThresholdDb: Float = DEFAULT_THRESHOLD_DB,
    val minPauseSeconds: Float = DEFAULT_MIN_PAUSE_S,
    val minChapterMinutes: Float = DEFAULT_MIN_CHAPTER_MIN,
    val keywords: List<String> = SubtitleChapterDetector.DEFAULT_KEYWORDS,
) {
    companion object {
        const val DEFAULT_THRESHOLD_DB = -40f
        const val DEFAULT_MIN_PAUSE_S = 2.0f
        const val DEFAULT_MIN_CHAPTER_MIN = 3f
    }
}

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private companion object {
        const val KEYWORD_SEPARATOR = "\n"
    }

    private val skipKey = intPreferencesKey("intervalo_pulo_s")
    private val rewindKey = intPreferencesKey("retroceder_ao_retomar_s")

    val playback: Flow<PlaybackSettings> = context.dataStore.data.map { prefs ->
        PlaybackSettings(
            skipIntervalSeconds = prefs[skipKey] ?: PlaybackSettings.DEFAULT_SKIP_SECONDS,
            rewindOnResumeSeconds = prefs[rewindKey] ?: PlaybackSettings.DEFAULT_REWIND_SECONDS,
        )
    }

    suspend fun currentPlayback(): PlaybackSettings = playback.first()

    private val thresholdKey = floatPreferencesKey("limiar_silencio_db")
    private val minPauseKey = floatPreferencesKey("pausa_minima_s")
    private val minChapterKey = floatPreferencesKey("capitulo_minimo_min")
    private val keywordsKey = stringPreferencesKey("palavras_chave")

    val detection: Flow<DetectionSettings> = context.dataStore.data.map { prefs ->
        DetectionSettings(
            silenceThresholdDb = prefs[thresholdKey] ?: DetectionSettings.DEFAULT_THRESHOLD_DB,
            minPauseSeconds = prefs[minPauseKey] ?: DetectionSettings.DEFAULT_MIN_PAUSE_S,
            minChapterMinutes = prefs[minChapterKey] ?: DetectionSettings.DEFAULT_MIN_CHAPTER_MIN,
            keywords = prefs[keywordsKey]?.split(KEYWORD_SEPARATOR)?.map { it.trim() }?.filter { it.isNotEmpty() }
                ?: SubtitleChapterDetector.DEFAULT_KEYWORDS,
        )
    }

    suspend fun currentDetection(): DetectionSettings = detection.first()

    private val lastBackupKey = longPreferencesKey("ultimo_backup")
    private val positionFolderKey = stringPreferencesKey("pasta_arquivo_posicao")
    private val batteryCardDismissedKey = booleanPreferencesKey("cartao_bateria_dispensado")

    val lastBackupAt: Flow<Long?> = context.dataStore.data.map { it[lastBackupKey] }

    suspend fun setLastBackupAt(timestamp: Long) {
        context.dataStore.edit { it[lastBackupKey] = timestamp }
    }

    /** Pasta escolhida para o arquivo de posição automático (nula = desligado). */
    val positionFolder: Flow<String?> = context.dataStore.data.map { it[positionFolderKey] }

    suspend fun currentPositionFolder(): String? = positionFolder.first()

    suspend fun setPositionFolder(uri: String?) {
        context.dataStore.edit { if (uri == null) it.remove(positionFolderKey) else it[positionFolderKey] = uri }
    }

    val batteryCardDismissed: Flow<Boolean> = context.dataStore.data.map { it[batteryCardDismissedKey] ?: false }

    suspend fun setBatteryCardDismissed(dismissed: Boolean) {
        context.dataStore.edit { it[batteryCardDismissedKey] = dismissed }
    }

    suspend fun setDetection(settings: DetectionSettings) {
        context.dataStore.edit {
            it[thresholdKey] = settings.silenceThresholdDb
            it[minPauseKey] = settings.minPauseSeconds
            it[minChapterKey] = settings.minChapterMinutes
            it[keywordsKey] = settings.keywords.joinToString(KEYWORD_SEPARATOR)
        }
    }

    suspend fun setSkipInterval(seconds: Int) {
        context.dataStore.edit { it[skipKey] = seconds }
    }

    suspend fun setRewindOnResume(seconds: Int) {
        context.dataStore.edit { it[rewindKey] = seconds }
    }
}
