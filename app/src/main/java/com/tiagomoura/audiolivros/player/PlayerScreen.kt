package com.tiagomoura.audiolivros.player

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.FastForward
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.ContentCut
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tiagomoura.audiolivros.R
import com.tiagomoura.audiolivros.chapters.longLabel
import com.tiagomoura.audiolivros.core.theme.Divisoria
import com.tiagomoura.audiolivros.core.theme.Superficie2
import com.tiagomoura.audiolivros.core.theme.Texto
import com.tiagomoura.audiolivros.core.theme.Borda
import com.tiagomoura.audiolivros.core.theme.Destaque
import com.tiagomoura.audiolivros.core.theme.Raios
import com.tiagomoura.audiolivros.core.theme.Superficie
import com.tiagomoura.audiolivros.core.theme.TextoSecundario
import com.tiagomoura.audiolivros.core.theme.TextoSobreDestaque
import com.tiagomoura.audiolivros.core.util.formatDuration
import com.tiagomoura.audiolivros.core.util.formatRemaining
import com.tiagomoura.audiolivros.covers.BookCover
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    onEditChapters: (reanalyze: Boolean) -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val requestNotifications = rememberNotificationPermissionRequest()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val notAvailable: (Int) -> Unit = { featureRes ->
        scope.launch {
            snackbar.showSnackbar(context.getString(R.string.ainda_nao_disponivel, context.getString(featureRes)))
        }
    }
    var menuOpen by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.tocando_agora), style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.voltar))
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.menu))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            listOf(
                                R.string.editar_capitulos,
                                R.string.reanalisar_capitulos,
                                R.string.exportar_este_livro,
                                R.string.detalhes_do_arquivo,
                            ).forEach { item ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(item)) },
                                    onClick = {
                                        menuOpen = false
                                        when (item) {
                                            R.string.editar_capitulos -> onEditChapters(false)
                                            R.string.reanalisar_capitulos -> onEditChapters(true)
                                            else -> notAvailable(item)
                                        }
                                    },
                                )
                            }
                        }
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
            Box(modifier = Modifier.fillMaxWidth(0.62f)) {
                BookCover(
                    title = book.title,
                    coverPath = book.coverPath,
                    placeholderColor = book.placeholderColor,
                    shape = Raios.CapaGrande,
                    initialsSize = 40.sp,
                    initialsPadding = 16.dp,
                    modifier = Modifier.fillMaxWidth(),
                )
                FilledIconButton(
                    onClick = { notAvailable(R.string.foto_de_capa) },
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Superficie2, contentColor = Texto),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                        .size(48.dp),
                ) {
                    Icon(Icons.Outlined.PhotoCamera, contentDescription = stringResource(R.string.foto_de_capa))
                }
            }
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
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                SpeedButton(
                    speed = state.speed,
                    onCycle = viewModel::cycleSpeed,
                    onSetSpeed = viewModel::setSpeed,
                    modifier = Modifier.weight(1f),
                )
                ActionButton(Icons.Outlined.Timer, stringResource(R.string.timer), Modifier.weight(1f)) {
                    notAvailable(R.string.timer)
                }
                ActionButton(Icons.Outlined.BookmarkAdd, stringResource(R.string.marcar), Modifier.weight(1f)) {
                    notAvailable(R.string.marcar)
                }
                ActionButton(Icons.Outlined.ContentCut, stringResource(R.string.trecho), Modifier.weight(1f)) {
                    notAvailable(R.string.trecho)
                }
            }

            Spacer(Modifier.height(16.dp))
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

            Spacer(Modifier.height(20.dp))
            ChaptersAndBookmarks(state = state, onPlayChapter = viewModel::playChapter)
        }
    }
}

@Composable
private fun ChaptersAndBookmarks(state: PlayerScreenState, onPlayChapter: (Long) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(0) }
    Column(modifier = Modifier.fillMaxWidth()) {
        SecondaryTabRow(
            selectedTabIndex = tab,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = Texto,
            indicator = {
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tab),
                    color = Destaque,
                )
            },
            divider = { HorizontalDivider(color = Divisoria) },
        ) {
            Tab(
                selected = tab == 0,
                onClick = { tab = 0 },
                text = { Text(stringResource(R.string.aba_capitulos)) },
                selectedContentColor = Texto,
                unselectedContentColor = TextoSecundario,
            )
            Tab(
                selected = tab == 1,
                onClick = { tab = 1 },
                text = { Text(stringResource(R.string.aba_marcacoes, state.bookmarkCount)) },
                selectedContentColor = Texto,
                unselectedContentColor = TextoSecundario,
            )
        }
        if (tab == 0) {
            val header = state.chapterSource?.takeIf { state.chapters.size > 1 || state.chapters.firstOrNull()?.title != null }
                ?.let { source ->
                    stringResource(
                        R.string.capitulos_cabecalho,
                        pluralStringResource(R.plurals.livro_capitulos, state.chapters.size, state.chapters.size),
                        stringResource(source.longLabel()),
                    )
                }
                ?: stringResource(R.string.sem_capitulos)
            Text(
                text = header,
                style = MaterialTheme.typography.bodySmall,
                color = TextoSecundario,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            state.chapters.forEach { chapter ->
                ChapterRow(
                    chapter = chapter,
                    isCurrent = chapter.index == state.chapter.index,
                    listened = state.book?.finished == true || state.positionMs >= chapter.endMs && chapter.endMs > 0,
                    onClick = { onPlayChapter(chapter.startMs) },
                )
            }
        } else {
            Text(
                text = stringResource(R.string.sem_marcacoes),
                style = MaterialTheme.typography.bodyMedium,
                color = TextoSecundario,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }
    }
}

@Composable
private fun ChapterRow(chapter: ChapterPosition, isCurrent: Boolean, listened: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Text(
            text = (chapter.index + 1).toString(),
            style = MaterialTheme.typography.labelLarge,
            color = if (isCurrent) Destaque else TextoSecundario,
            modifier = Modifier.width(36.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = chapter.title ?: stringResource(R.string.livro_inteiro),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isCurrent) Destaque else Texto,
                maxLines = 2,
            )
            Text(
                text = if (isCurrent) {
                    stringResource(R.string.capitulos_cabecalho, formatDuration(chapter.durationMs), stringResource(R.string.tocando))
                } else {
                    formatDuration(chapter.durationMs)
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextoSecundario,
            )
        }
        if (listened && !isCurrent) {
            Icon(Icons.Outlined.Check, contentDescription = null, tint = TextoSecundario, modifier = Modifier.size(18.dp))
        }
    }
    HorizontalDivider(color = Divisoria)
}

@Composable
private fun ActionButton(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        color = Superficie,
        contentColor = Texto,
        shape = Raios.Botao,
        modifier = modifier
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick),
    ) {
        ActionButtonContent(icon, label)
    }
}

@Composable
private fun ActionButtonContent(icon: ImageVector, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = TextoSecundario)
        Spacer(Modifier.height(4.dp))
        Text(text = label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
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
private fun SpeedButton(speed: Float, onCycle: () -> Unit, onSetSpeed: (Float) -> Unit, modifier: Modifier = Modifier) {
    var fineTuning by remember { mutableStateOf(false) }
    val label = stringResource(R.string.velocidade_valor, PlaybackSpeed.label(speed))

    Surface(
        color = Superficie,
        contentColor = Texto,
        shape = Raios.Botao,
        modifier = modifier
            .heightIn(min = 64.dp)
            .combinedClickable(
                onClickLabel = stringResource(R.string.velocidade),
                onClick = onCycle,
                onLongClickLabel = stringResource(R.string.velocidade_ajuste),
                onLongClick = { fineTuning = true },
            ),
    ) {
        ActionButtonContent(Icons.Outlined.Speed, label)
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
