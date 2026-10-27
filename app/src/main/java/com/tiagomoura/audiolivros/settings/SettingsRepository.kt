package com.tiagomoura.audiolivros.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val skipKey = intPreferencesKey("intervalo_pulo_s")
    private val rewindKey = intPreferencesKey("retroceder_ao_retomar_s")

    val playback: Flow<PlaybackSettings> = context.dataStore.data.map { prefs ->
        PlaybackSettings(
            skipIntervalSeconds = prefs[skipKey] ?: PlaybackSettings.DEFAULT_SKIP_SECONDS,
            rewindOnResumeSeconds = prefs[rewindKey] ?: PlaybackSettings.DEFAULT_REWIND_SECONDS,
        )
    }

    suspend fun currentPlayback(): PlaybackSettings = playback.first()

    suspend fun setSkipInterval(seconds: Int) {
        context.dataStore.edit { it[skipKey] = seconds }
    }

    suspend fun setRewindOnResume(seconds: Int) {
        context.dataStore.edit { it[rewindKey] = seconds }
    }
}
