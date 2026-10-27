package com.tiagomoura.audiolivros.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tiagomoura.audiolivros.backup.BackupRepository
import com.tiagomoura.audiolivros.download.YtDlp
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsState(
    val playback: PlaybackSettings = PlaybackSettings(),
    val detection: DetectionSettings = DetectionSettings(),
    val ytDlpVersion: String? = null,
    val updatingYtDlp: Boolean = false,
    val lastBackupAt: Long? = null,
    val positionFolder: String? = null,
)

sealed interface SettingsMessage {
    data class YtDlpUpdated(val version: String?, val alreadyUpToDate: Boolean) : SettingsMessage
    data class Failed(val reason: String?) : SettingsMessage
    data object Exported : SettingsMessage
    data class Imported(val matched: Int, val missing: Int) : SettingsMessage
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val ytDlp: YtDlp,
    private val backup: BackupRepository,
) : ViewModel() {

    private val updating = MutableStateFlow(false)
    private val messages = Channel<SettingsMessage>(Channel.BUFFERED)
    val messageFlow = messages.receiveAsFlow()

    val state: StateFlow<SettingsState> = combine(
        combine(settings.playback, settings.detection) { p, d -> p to d },
        ytDlp.version,
        updating,
        settings.lastBackupAt,
        settings.positionFolder,
    ) { (playback, detection), version, isUpdating, lastBackup, folder ->
        SettingsState(playback, detection, version, isUpdating, lastBackup, folder)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())

    init {
        ytDlp.initInBackground()
    }

    fun setSkip(seconds: Int) = launch { settings.setSkipInterval(seconds) }

    fun setRewind(seconds: Int) = launch { settings.setRewindOnResume(seconds) }

    fun setDetection(value: DetectionSettings) = launch { settings.setDetection(value) }

    fun updateYtDlp() {
        if (updating.value) return
        viewModelScope.launch {
            updating.value = true
            val message = try {
                val result = ytDlp.update()
                SettingsMessage.YtDlpUpdated(result.version, result.alreadyUpToDate)
            } catch (e: Exception) {
                SettingsMessage.Failed(e.localizedMessage)
            } finally {
                updating.value = false
            }
            messages.send(message)
        }
    }

    fun export(uri: Uri) = launch {
        messages.send(runCatching { backup.exportTo(uri) }.fold({ SettingsMessage.Exported }, { SettingsMessage.Failed(it.localizedMessage) }))
    }

    fun import(uri: Uri) = launch {
        messages.send(
            runCatching { backup.importFrom(uri) }.fold(
                { SettingsMessage.Imported(it.matched, it.missing) },
                { SettingsMessage.Failed(it.localizedMessage) },
            ),
        )
    }

    /** Pasta do arquivo de posição automático, com permissão persistente. */
    fun setPositionFolder(uri: Uri?) = launch {
        val previous = settings.currentPositionFolder()
        if (previous != null) {
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    Uri.parse(previous),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        settings.setPositionFolder(uri?.toString())
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
