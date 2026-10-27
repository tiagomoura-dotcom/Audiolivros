package com.tiagomoura.audiolivros.download

import android.content.Context
import android.util.Log
import com.tiagomoura.audiolivros.core.di.ApplicationScope
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * youtubedl-android (yt-dlp + Python + ffmpeg empacotados). A inicialização extrai
 * os binários na primeira vez e é feita em segundo plano quando o app abre.
 */
@Singleton
class YtDlp @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val ready = CompletableDeferred<Result<Unit>>()
    private val started = AtomicBoolean(false)
    private val _version = MutableStateFlow<String?>(null)
    val version: StateFlow<String?> = _version.asStateFlow()

    fun initInBackground() {
        if (!started.compareAndSet(false, true)) return
        scope.launch(Dispatchers.IO) {
            val result = runCatching {
                YoutubeDL.getInstance().init(context)
                FFmpeg.getInstance().init(context)
            }
            result.exceptionOrNull()?.let { Log.e(TAG, "Falha ao iniciar o yt-dlp", it) }
            _version.value = runCatching { YoutubeDL.getInstance().version(context) }.getOrNull()
            ready.complete(result)
        }
    }

    /** Espera a inicialização; lança a exceção dela se tiver falhado. */
    suspend fun awaitReady() {
        initInBackground()
        ready.await().getOrThrow()
    }

    /** "Atualizar yt-dlp": usa a atualização da própria biblioteca. Retorna a versão nova. */
    suspend fun update(): UpdateResult = withContext(Dispatchers.IO) {
        awaitReady()
        val status = YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
        val newVersion = YoutubeDL.getInstance().version(context)
        _version.value = newVersion
        UpdateResult(alreadyUpToDate = status == YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE, version = newVersion)
    }

    data class UpdateResult(val alreadyUpToDate: Boolean, val version: String?)

    private companion object {
        const val TAG = "YtDlp"
    }
}
