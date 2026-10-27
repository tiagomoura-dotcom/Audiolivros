package com.tiagomoura.audiolivros.player

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.FastRewind
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.Forward30
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material.icons.outlined.Replay30
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tiagomoura.audiolivros.R
import com.tiagomoura.audiolivros.core.theme.Borda
import com.tiagomoura.audiolivros.core.theme.Destaque
import com.tiagomoura.audiolivros.core.theme.Raios
import com.tiagomoura.audiolivros.core.theme.Superficie
import com.tiagomoura.audiolivros.core.theme.TextoSecundario
import com.tiagomoura.audiolivros.core.theme.TextoSobreDestaque
import com.tiagomoura.audiolivros.core.util.formatDuration
import com.tiagomoura.audiolivros.core.util.formatRemaining
import com.tiagomoura.audiolivros.covers.BookCover

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val requestNotifications = rememberNotificationPermissionRequest()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.tocando_agora), style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.voltar))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        val book = state.book
        if (book == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                if (state.loading) {
                    CircularProgressIndicator(color = Destaque)
                } else {
                    Text(stringResource(R.string.livro_nao_encontrado), color = TextoSecundario)
                }
            }
            return@Scaffold
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            BookCover(
                title = book.title,
                coverPath = book.coverPath,
                placeholderColor = book.placeholderColor,
                shape = Raios.CapaGrande,
                initialsSize = 40.sp,
                initialsPadding = 16.dp,
                modifier = Modifier.fillMaxWidth(0.62f),
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = book.title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                text = book.author ?: stringResource(R.string.autor_desconhecido),
                style = MaterialTheme.typography.bodyMedium,
                color = TextoSecundario,
                textAlign = TextAlign.Center,
            )
            Text(
                text = state.chapter.title ?: stringResource(R.string.livro_inteiro),
                style = MaterialTheme.typography.titleSmall,
                color = Destaque,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )

            Spacer(Modifier.height(20.dp))
            ChapterSeekBar(state = state, onSeek = viewModel::seekInChapter)

            Spacer(Modifier.height(16.dp))
            Controls(
                state = state,
                onPreviousChapter = viewModel::previousChapter,
                onSeekBack = viewModel::seekBack,
                onPlayPause = {
                    requestNotifications()
                    viewModel.playPause()
                },
                onSeekForward = viewModel::seekForward,
                onNextChapter = viewModel::nextChapter,
            )

            Spacer(Modifier.height(24.dp))
            SpeedButton(
                speed = state.speed,
                onCycle = viewModel::cycleSpeed,
                onSetSpeed = viewModel::setSpeed,
            )

            Spacer(Modifier.height(20.dp))
            if (state.durationMs > 0) {
                Text(
                    text = stringResource(
                        R.string.progresso_livro,
                        (state.bookProgress * 100).toInt(),
                        formatRemaining(state.durationMs - state.positionMs),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextoSecundario,
                )
            }
        }
    }
}

@Composable
private fun ChapterSeekBar(state: PlayerScreenState, onSeek: (Float) -> Unit) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val fraction = dragFraction ?: state.chapterProgress
    val shownElapsed = (state.chapter.durationMs * fraction).toLong()

    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = fraction,
            onValueChange = { dragFraction = it },
            onValueChangeFinished = {
                dragFraction?.let(onSeek)
                dragFraction = null
            },
            enabled = state.isCurrent && state.chapter.durationMs > 0,
            colors = SliderDefaults.colors(
                thumbColor = Destaque,
                activeTrackColor = Destaque,
                inactiveTrackColor = Borda,
            ),
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = formatDuration(shownElapsed),
                style = MaterialTheme.typography.bodySmall,
                color = TextoSecundario,
            )
            Text(
                text = stringResource(R.string.capitulo_de, state.chapter.index + 1, state.chapter.count),
                style = MaterialTheme.typography.bodySmall,
                color = TextoSecundario,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.tempo_restante, formatDuration(state.chapter.durationMs - shownElapsed)),
                style = MaterialTheme.typography.bodySmall,
                color = TextoSecundario,
            )
        }
    }
}

@Composable
private fun Controls(
    state: PlayerScreenState,
    onPreviousChapter: () -> Unit,
    onSeekBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekForward: () -> Unit,
    onNextChapter: () -> Unit,
) {
    val (backIcon, forwardIcon) = skipIcons(state.skipSeconds)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier.fillMaxWidth(),
    ) {
        ControlButton(Icons.Outlined.SkipPrevious, stringResource(R.string.capitulo_anterior), onPreviousChapter, state.isCurrent)
        ControlButton(backIcon, stringResource(R.string.voltar_segundos, state.skipSeconds), onSeekBack, state.isCurrent)
        FilledIconButton(
            onClick = onPlayPause,
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = Destaque,
                contentColor = TextoSobreDestaque,
            ),
            modifier = Modifier.size(76.dp),
        ) {
            if (state.isBuffering && state.isPlaying) {
                CircularProgressIndicator(
                    color = TextoSobreDestaque,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(32.dp),
                )
            } else {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                    contentDescription = stringResource(if (state.isPlaying) R.string.pausar else R.string.tocar),
                    modifier = Modifier.size(40.dp),
                )
            }
        }
        ControlButton(forwardIcon, stringResource(R.string.avancar_segundos, state.skipSeconds), onSeekForward, state.isCurrent)
        ControlButton(Icons.Outlined.SkipNext, stringResource(R.string.proximo_capitulo), onNextChapter, state.isCurrent)
    }
}

private fun skipIcons(seconds: Int): Pair<ImageVector, ImageVector> = when (seconds) {
    10 -> Icons.Outlined.Replay10 to Icons.Outlined.Forward10
    30 -> Icons.Outlined.Replay30 to Icons.Outlined.Forward30
    else -> Icons.Outlined.FastRewind to Icons.Outlined.FastForward
}

@Composable
private fun ControlButton(icon: ImageVector, description: String, onClick: () -> Unit, enabled: Boolean) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(52.dp)) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(30.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SpeedButton(speed: Float, onCycle: () -> Unit, onSetSpeed: (Float) -> Unit) {
    var fineTuning by remember { mutableStateOf(false) }
    val label = stringResource(R.string.velocidade_valor, PlaybackSpeed.label(speed))

    Surface(
        color = Superficie,
        shape = Raios.Botao,
        modifier = Modifier
            .heightIn(min = 56.dp)
            .width(120.dp)
            .combinedClickable(
                onClickLabel = stringResource(R.string.velocidade),
                onClick = onCycle,
                onLongClickLabel = stringResource(R.string.velocidade_ajuste),
                onLongClick = { fineTuning = true },
            ),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(vertical = 8.dp),
        ) {
            Icon(Icons.Outlined.Speed, contentDescription = null, tint = TextoSecundario)
            Text(text = label, style = MaterialTheme.typography.labelLarge)
        }
    }

    if (fineTuning) {
        var value by remember { mutableFloatStateOf(speed) }
        AlertDialog(
            onDismissRequest = { fineTuning = false },
            title = { Text(stringResource(R.string.velocidade_ajuste)) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.velocidade_valor, PlaybackSpeed.label(value)),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Slider(
                        value = value,
                        onValueChange = { value = PlaybackSpeed.normalize(it) },
                        valueRange = PlaybackSpeed.MIN..PlaybackSpeed.MAX,
                        steps = PlaybackSpeed.SLIDER_STEPS,
                        colors = SliderDefaults.colors(
                            thumbColor = Destaque,
                            activeTrackColor = Destaque,
                            inactiveTrackColor = Borda,
                        ),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onSetSpeed(value)
                    fineTuning = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { fineTuning = false }) { Text(stringResource(R.string.cancelar)) }
            },
        )
    }
}
