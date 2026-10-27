package com.tiagomoura.audiolivros.chapters

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tiagomoura.audiolivros.R
import com.tiagomoura.audiolivros.core.theme.Borda
import com.tiagomoura.audiolivros.core.theme.Destaque
import com.tiagomoura.audiolivros.core.theme.Divisoria
import com.tiagomoura.audiolivros.core.theme.Raios
import com.tiagomoura.audiolivros.core.theme.Superficie
import com.tiagomoura.audiolivros.core.theme.Texto
import com.tiagomoura.audiolivros.core.theme.TextoSecundario
import com.tiagomoura.audiolivros.core.util.formatDuration

private val SUBTITLE_MIME_TYPES = arrayOf("application/x-subrip", "text/vtt", "text/plain", "application/octet-stream", "*/*")

private sealed interface EditorDialog {
    data class Rename(val chapter: EditorChapter) : EditorDialog
    data class Delete(val chapter: EditorChapter) : EditorDialog
    data object Add : EditorDialog
    data object Description : EditorDialog
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterEditorScreen(
    onBack: () -> Unit,
    onSilenceAnalysis: (String) -> Unit,
    viewModel: ChapterEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var sheetOpen by rememberSaveable { mutableStateOf(viewModel.openReanalysis) }
    var dialog by remember { mutableStateOf<EditorDialog?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.messageFlow.collect { message ->
            val text = when (message) {
                is EditorMessage.Detected -> context.resources.getQuantityString(
                    R.plurals.capitulos_detectados, message.count, message.count,
                )
                EditorMessage.NothingFound -> context.getString(R.string.nenhum_capitulo_encontrado)
                is EditorMessage.Failed -> context.getString(
                    R.string.erro_reanalisar, message.reason ?: context.getString(R.string.erro_desconhecido),
                )
            }
            snackbar.showSnackbar(text)
        }
    }

    val subtitleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.reanalyzeSubtitle(uri)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.editar_capitulos), style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.voltar))
                    }
                },
                actions = {
                    if (state.busy) {
                        CircularProgressIndicator(color = Destaque, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                    } else {
                        IconButton(onClick = { sheetOpen = true }) {
                            Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.reanalisar))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "acoes") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                    Button(
                        onClick = { dialog = EditorDialog.Add },
                        enabled = state.currentPositionMs != null,
                        shape = Raios.Botao,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = state.currentPositionMs?.let { stringResource(R.string.adicionar_em, formatDuration(it)) }
                                ?: stringResource(R.string.adicionar_capitulo),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    OutlinedButton(
                        onClick = { sheetOpen = true },
                        enabled = !state.busy,
                        shape = Raios.Botao,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.reanalisar), maxLines = 1)
                    }
                }
            }

            if (!state.loading && state.chapters.isEmpty()) {
                item(key = "vazio") {
                    Text(
                        text = stringResource(R.string.editor_vazio),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextoSecundario,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            }

            items(state.chapters, key = { it.id }) { chapter ->
                EditableChapterRow(
                    chapter = chapter,
                    onRename = { dialog = EditorDialog.Rename(chapter) },
                    onNudge = { delta -> viewModel.nudge(chapter, delta) },
                    onPreviewBefore = { viewModel.previewBefore(chapter) },
                    onPreviewAfter = { viewModel.previewAfter(chapter) },
                    onDelete = { dialog = EditorDialog.Delete(chapter) },
                )
            }
        }
    }

    if (sheetOpen) {
        ModalBottomSheet(onDismissRequest = { sheetOpen = false }, containerColor = Superficie) {
            Text(
                text = stringResource(R.string.reanalisar_titulo),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            Text(
                text = stringResource(R.string.reanalisar_aviso),
                style = MaterialTheme.typography.bodySmall,
                color = TextoSecundario,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.size(8.dp))
            SourceOption(Icons.Outlined.LibraryMusic, R.string.fonte_metadados, R.string.fonte_metadados_ajuda) {
                sheetOpen = false
                viewModel.reanalyzeEmbedded()
            }
            SourceOption(Icons.Outlined.Description, R.string.fonte_descricao, R.string.fonte_descricao_ajuda) {
                sheetOpen = false
                dialog = EditorDialog.Description
            }
            SourceOption(Icons.Outlined.Subtitles, R.string.fonte_legenda, R.string.fonte_legenda_ajuda) {
                sheetOpen = false
                subtitleLauncher.launch(SUBTITLE_MIME_TYPES)
            }
            SourceOption(Icons.Outlined.GraphicEq, R.string.fonte_pausas, R.string.fonte_pausas_ajuda) {
                sheetOpen = false
                onSilenceAnalysis(viewModel.bookId)
            }
            Spacer(Modifier.size(24.dp))
        }
    }

    when (val current = dialog) {
        is EditorDialog.Rename -> TextDialog(
            title = stringResource(R.string.renomear),
            label = stringResource(R.string.titulo),
            initial = current.chapter.title,
            singleLine = true,
            confirm = stringResource(R.string.salvar),
            onConfirm = { viewModel.rename(current.chapter.id, it); dialog = null },
            onDismiss = { dialog = null },
        )
        is EditorDialog.Delete -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.excluir_capitulo_titulo)) },
            text = { Text(current.chapter.title) },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(current.chapter.id); dialog = null }) {
                    Text(stringResource(R.string.excluir))
                }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.cancelar)) } },
        )
        EditorDialog.Add -> TextDialog(
            title = stringResource(R.string.adicionar_capitulo),
            label = stringResource(R.string.titulo),
            initial = stringResource(R.string.capitulo_numero, state.chapters.size + 1),
            singleLine = true,
            confirm = stringResource(R.string.salvar),
            onConfirm = { viewModel.addAtCurrentPosition(it); dialog = null },
            onDismiss = { dialog = null },
        )
        EditorDialog.Description -> TextDialog(
            title = stringResource(R.string.fonte_descricao),
            label = stringResource(R.string.cole_descricao),
            initial = "",
            singleLine = false,
            confirm = stringResource(R.string.detectar),
            onConfirm = { viewModel.reanalyzeDescription(it); dialog = null },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

@Composable
private fun SourceOption(icon: androidx.compose.ui.graphics.vector.ImageVector, title: Int, help: Int, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(help), color = TextoSecundario) },
        leadingContent = { Icon(icon, contentDescription = null, tint = Destaque) },
        colors = ListItemDefaults.colors(containerColor = Superficie),
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
    )
}

@Composable
private fun EditableChapterRow(
    chapter: EditorChapter,
    onRename: () -> Unit,
    onNudge: (Long) -> Unit,
    onPreviewBefore: () -> Unit,
    onPreviewAfter: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = chapter.number.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = TextoSecundario,
                modifier = Modifier.width(32.dp),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clickable(onClick = onRename),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(text = chapter.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    text = stringResource(R.string.inicio_duracao, formatDuration(chapter.startMs), formatDuration(chapter.durationMs)),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextoSecundario,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.excluir), tint = TextoSecundario)
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 32.dp, top = 4.dp),
        ) {
            SmallButton(stringResource(R.string.menos_um_segundo), Modifier.weight(1f)) { onNudge(-1_000) }
            SmallButton(stringResource(R.string.mais_um_segundo), Modifier.weight(1f)) { onNudge(1_000) }
            SmallButton(stringResource(R.string.ouvir_antes), Modifier.weight(1.3f), onPreviewBefore)
            SmallButton(stringResource(R.string.ouvir_depois), Modifier.weight(1.3f), onPreviewAfter)
        }
    }
    HorizontalDivider(color = Divisoria)
}

@Composable
private fun SmallButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        color = Superficie,
        contentColor = Texto,
        shape = Raios.Botao,
        border = androidx.compose.foundation.BorderStroke(1.dp, Borda),
        modifier = modifier
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick),
    ) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
            Text(text = label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

@Composable
private fun TextDialog(
    title: String,
    label: String,
    initial: String,
    singleLine: Boolean,
    confirm: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                singleLine = singleLine,
                minLines = if (singleLine) 1 else 6,
                maxLines = if (singleLine) 1 else 12,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancelar)) } },
    )
}
