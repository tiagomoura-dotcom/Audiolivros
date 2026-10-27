package com.tiagomoura.audiolivros.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tiagomoura.audiolivros.R
import com.tiagomoura.audiolivros.backup.BackupRepository
import com.tiagomoura.audiolivros.chapters.shortLabel
import com.tiagomoura.audiolivros.core.util.formatBackupDate
import com.tiagomoura.audiolivros.core.database.BookStatus
import com.tiagomoura.audiolivros.core.theme.Borda
import com.tiagomoura.audiolivros.core.theme.Destaque
import com.tiagomoura.audiolivros.core.theme.Raios
import com.tiagomoura.audiolivros.core.theme.RotuloSecao
import com.tiagomoura.audiolivros.core.theme.Superficie
import com.tiagomoura.audiolivros.core.theme.Superficie2
import com.tiagomoura.audiolivros.core.theme.Texto
import com.tiagomoura.audiolivros.core.theme.TextoSecundario
import com.tiagomoura.audiolivros.core.theme.TextoSobreDestaque
import com.tiagomoura.audiolivros.core.util.formatDuration
import com.tiagomoura.audiolivros.core.util.formatRemaining
import com.tiagomoura.audiolivros.covers.BookCover
import com.tiagomoura.audiolivros.download.DownloadOptions
import com.tiagomoura.audiolivros.download.DownloadWorker
import com.tiagomoura.audiolivros.player.rememberNotificationPermissionRequest
import kotlinx.coroutines.launch

/** Tipos de audio aceitos pelo importador (seção 4.1). */
private val AUDIO_MIME_TYPES = arrayOf("audio/*")

@Composable
fun LibraryScreen(
    onOpenBook: (String) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val updatingYtDlp by viewModel.updatingYtDlp.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val requestNotifications = rememberNotificationPermissionRequest()
    val scope = rememberCoroutineScope()
    var linkPanelOpen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.messageFlow.collect { message ->
            val text = when (message) {
                is LibraryMessage.Imported -> context.getString(R.string.importado, message.title)
                is LibraryMessage.AlreadyImported -> context.getString(R.string.ja_importado, message.title)
                is LibraryMessage.ImportFailed -> context.getString(
                    R.string.erro_importar,
                    message.reason ?: context.getString(R.string.erro_desconhecido),
                )
                LibraryMessage.DownloadStarted -> context.getString(R.string.download_iniciado)
                LibraryMessage.Exported -> context.getString(R.string.backup_exportado)
                is LibraryMessage.Failed -> context.getString(
                    R.string.erro_generico,
                    message.reason ?: context.getString(R.string.erro_desconhecido),
                )
                LibraryMessage.InvalidLink -> context.getString(R.string.link_invalido)
                is LibraryMessage.YtDlpUpdated -> context.getString(
                    if (message.alreadyUpToDate) R.string.ytdlp_ja_atualizado else R.string.ytdlp_atualizado,
                    message.version ?: "?",
                )
                is LibraryMessage.YtDlpUpdateFailed -> context.getString(
                    R.string.ytdlp_erro_atualizar,
                    message.reason ?: context.getString(R.string.erro_desconhecido),
                )
            }
            snackbar.showSnackbar(text)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.import(uri)
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.exportBackup(uri)
    }
    var relinkBookId by rememberSaveable { mutableStateOf<String?>(null) }
    val relinkLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val bookId = relinkBookId
        if (uri != null && bookId != null) viewModel.relink(bookId, uri)
        relinkBookId = null
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = padding.calculateTopPadding() + 24.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "cabecalho") {
                Header(state, onSettings = onOpenSettings)
            }

            state.continueListening?.let { book ->
                item(key = "continuar") {
                    ContinueListeningCard(
                        book = book,
                        isPlaying = state.continueIsPlaying,
                        onOpen = { onOpenBook(book.id) },
                        onPlayPause = {
                            requestNotifications()
                            viewModel.togglePlayPause(book.id)
                        },
                    )
                }
            }

            item(key = "acoes") {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { importLauncher.launch(AUDIO_MIME_TYPES) },
                            enabled = !state.importing,
                            shape = Raios.Botao,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 52.dp),
                        ) {
                            if (state.importing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = TextoSobreDestaque,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.importando), maxLines = 1)
                            } else {
                                Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.importar_arquivo), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        OutlinedButton(
                            onClick = { linkPanelOpen = !linkPanelOpen },
                            shape = Raios.Botao,
                            border = BorderStroke(1.dp, if (linkPanelOpen) Destaque else Borda),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = Superficie, contentColor = Texto),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 52.dp),
                        ) {
                            Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.baixar_por_link), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    AnimatedVisibility(visible = linkPanelOpen) {
                        Box(modifier = Modifier.padding(top = 12.dp)) {
                            LinkPanel(
                                onDownload = { link, options ->
                                    viewModel.download(link, options)
                                    linkPanelOpen = false
                                },
                            )
                        }
                    }
                }
            }

            item(key = "rotulo") {
                Text(
                    text = stringResource(R.string.todos_os_livros).uppercase(),
                    style = RotuloSecao,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
            }

            if (!state.loading && state.books.isEmpty()) {
                item(key = "vazia") {
                    Text(
                        text = stringResource(R.string.biblioteca_vazia),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextoSecundario,
                    )
                }
            }

            items(state.books, key = { it.id }) { book ->
                BookRow(
                    book = book,
                    onOpen = {
                        if (book.isDownloading && book.status != BookStatus.ERROR) {
                            scope.launch { snackbar.showSnackbar(context.getString(R.string.ainda_baixando)) }
                        } else if (book.missingFile) {
                            relinkBookId = book.id
                            relinkLauncher.launch(AUDIO_MIME_TYPES)
                        } else if (!book.isDownloading) {
                            onOpenBook(book.id)
                        }
                    },
                    updatingYtDlp = updatingYtDlp,
                    onRetry = { viewModel.retryDownload(book.id) },
                    onUpdateYtDlp = viewModel::updateYtDlp,
                    onRename = { viewModel.rename(book.id, it) },
                    onEditAuthor = { viewModel.updateAuthor(book.id, it) },
                    onDelete = { viewModel.delete(book.id) },
                )
            }

            item(key = "backup") {
                BackupCard(
                    lastBackupAt = state.lastBackupAt,
                    onExport = { exportLauncher.launch(BackupRepository.backupFileName()) },
                )
            }
        }
    }
}

@Composable
private fun Header(state: LibraryUiState, onSettings: () -> Unit) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(bottom = 8.dp)) {
        HeaderTexts(state, modifier = Modifier.weight(1f))
        IconButton(onClick = onSettings, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Outlined.Settings, contentDescription = stringResource(R.string.configuracoes), tint = TextoSecundario)
        }
    }
}

@Composable
private fun HeaderTexts(state: LibraryUiState, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.biblioteca_titulo),
            style = MaterialTheme.typography.headlineLarge,
        )
        if (!state.loading) {
            // Em português, o plural "one" do Android inclui o zero ("0 livro"); por isso o caso à parte.
            val books = if (state.books.isEmpty()) {
                stringResource(R.string.biblioteca_resumo_zero)
            } else {
                pluralStringResource(R.plurals.biblioteca_resumo_livros, state.books.size, state.books.size)
            }
            val summary = if (state.processingCount > 0) {
                stringResource(
                    R.string.biblioteca_resumo_juncao,
                    books,
                    pluralStringResource(R.plurals.biblioteca_resumo_processando, state.processingCount, state.processingCount),
                )
            } else {
                books
            }
            Text(text = summary, style = MaterialTheme.typography.bodyMedium, color = TextoSecundario)
        }
    }
}

@Composable
private fun ContinueListeningCard(
    book: LibraryItem,
    isPlaying: Boolean,
    onOpen: () -> Unit,
    onPlayPause: () -> Unit,
) {
    Surface(
        color = Superficie,
        contentColor = Texto,
        shape = Raios.Cartao,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = stringResource(R.string.continuar_ouvindo).uppercase(), style = RotuloSecao)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                BookCover(
                    title = book.title,
                    coverPath = book.coverPath,
                    placeholderColor = book.placeholderColor,
                    modifier = Modifier.width(60.dp),
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = book.author ?: stringResource(R.string.autor_desconhecido),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextoSecundario,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = formatDuration(book.positionMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = Destaque,
                    )
                }
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = onPlayPause,
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Destaque,
                        contentColor = TextoSobreDestaque,
                    ),
                    modifier = Modifier.size(52.dp),
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        contentDescription = stringResource(if (isPlaying) R.string.pausar else R.string.tocar),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            LinearProgressIndicator(
                progress = { book.progress },
                modifier = Modifier.fillMaxWidth(),
                color = Destaque,
                trackColor = Borda,
                drawStopIndicator = {},
            )
            Spacer(Modifier.height(8.dp))
            Row {
                Text(
                    text = stringResource(R.string.status_porcentagem, (book.progress * 100).toInt()),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextoSecundario,
                    modifier = Modifier.weight(1f),
                )
                if (book.durationMs > 0) {
                    Text(
                        text = stringResource(R.string.livro_restante, formatRemaining(book.remainingMs)),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextoSecundario,
                    )
                }
            }
        }
    }
}

private enum class BookDialog { RENAME, AUTHOR, DELETE, DOWNLOAD_ERROR }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookRow(
    book: LibraryItem,
    onOpen: () -> Unit,
    updatingYtDlp: Boolean,
    onRetry: () -> Unit,
    onUpdateYtDlp: () -> Unit,
    onRename: (String) -> Unit,
    onEditAuthor: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var dialog by rememberSaveable { mutableStateOf<BookDialog?>(null) }

    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .combinedClickable(
                    onClick = {
                        if (book.status == BookStatus.ERROR && book.isDownloading) dialog = BookDialog.DOWNLOAD_ERROR else onOpen()
                    },
                    onLongClick = { menuOpen = true },
                )
                .padding(vertical = 6.dp),
        ) {
            BookCover(
                title = book.title,
                coverPath = book.coverPath,
                placeholderColor = book.placeholderColor,
                modifier = Modifier.width(48.dp),
                initialsSize = MaterialTheme.typography.bodyMedium.fontSize,
                initialsPadding = 4.dp,
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = book.author ?: stringResource(R.string.autor_desconhecido),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextoSecundario,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val details = if (book.chapterCount > 0) {
                    val count = pluralStringResource(R.plurals.livro_capitulos, book.chapterCount, book.chapterCount)
                    book.chapterSource?.let { stringResource(R.string.livro_capitulos_origem, count, stringResource(it.shortLabel())) }
                        ?: count
                } else if (book.durationMs > 0) {
                    stringResource(R.string.livro_duracao, formatDuration(book.durationMs))
                } else {
                    null
                }
                if (details != null) {
                    Text(text = details, style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = { if (book.status == BookStatus.PROCESSING) book.processingProgress / 100f else book.progress },
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp),
                        color = Destaque,
                        trackColor = Superficie2,
                        drawStopIndicator = {},
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = statusText(book),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (book.finished) Destaque else TextoSecundario,
                    )
                }
            }
        }

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.renomear)) },
                leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                onClick = { menuOpen = false; dialog = BookDialog.RENAME },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.editar_autor)) },
                leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                onClick = { menuOpen = false; dialog = BookDialog.AUTHOR },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.excluir)) },
                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                onClick = { menuOpen = false; dialog = BookDialog.DELETE },
            )
        }
    }

    when (dialog) {
        BookDialog.RENAME -> TextInputDialog(
            title = stringResource(R.string.renomear),
            label = stringResource(R.string.titulo),
            initial = book.title,
            onConfirm = { onRename(it); dialog = null },
            onDismiss = { dialog = null },
        )
        BookDialog.AUTHOR -> TextInputDialog(
            title = stringResource(R.string.editar_autor),
            label = stringResource(R.string.autor),
            initial = book.author.orEmpty(),
            onConfirm = { onEditAuthor(it); dialog = null },
            onDismiss = { dialog = null },
        )
        BookDialog.DELETE -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.excluir_livro_titulo)) },
            text = { Text(stringResource(R.string.excluir_livro_texto, book.title)) },
            confirmButton = {
                TextButton(onClick = { onDelete(); dialog = null }) { Text(stringResource(R.string.excluir)) }
            },
            dismissButton = {
                TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.cancelar)) }
            },
        )
        BookDialog.DOWNLOAD_ERROR -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(stringResource(R.string.falha_download_titulo)) },
            text = {
                Column {
                    Text(book.errorMessage ?: stringResource(R.string.erro_desconhecido))
                    if (book.suggestsYtDlpUpdate) {
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.falha_download_sugestao), color = Destaque)
                    }
                    if (updatingYtDlp) {
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.atualizando_ytdlp), color = TextoSecundario)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onRetry(); dialog = null }) { Text(stringResource(R.string.tentar_de_novo)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = onUpdateYtDlp, enabled = !updatingYtDlp) { Text(stringResource(R.string.atualizar_ytdlp)) }
                    TextButton(onClick = { dialog = null }) { Text(stringResource(R.string.fechar)) }
                }
            },
        )
        null -> Unit
    }
}

@Composable
private fun statusText(book: LibraryItem): String = when {
    book.status == BookStatus.PROCESSING && book.isDownloading -> downloadStatus(book.processingProgress)
    book.status == BookStatus.PROCESSING -> stringResource(R.string.status_processando, book.processingProgress)
    book.status == BookStatus.ERROR && book.isDownloading -> stringResource(R.string.status_falha_download)
    book.missingFile -> stringResource(R.string.status_arquivo_ausente)
    book.status == BookStatus.ERROR -> stringResource(R.string.status_erro)
    book.finished -> stringResource(R.string.status_concluido)
    !book.started -> stringResource(R.string.status_novo)
    else -> stringResource(R.string.status_porcentagem, (book.progress * 100).toInt())
}

@Composable
private fun TextInputDialog(
    title: String,
    label: String,
    initial: String,
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
                singleLine = true,
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text(stringResource(R.string.salvar)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancelar)) } },
    )
}

@Composable
private fun LinkPanel(onDownload: (String, DownloadOptions) -> Unit) {
    var link by rememberSaveable { mutableStateOf("") }
    var chaptersAndSubtitles by rememberSaveable { mutableStateOf(true) }
    var convertToMp3 by rememberSaveable { mutableStateOf(true) }
    var thumbnailAsCover by rememberSaveable { mutableStateOf(true) }

    Surface(color = Superficie, contentColor = Texto, shape = Raios.Cartao, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = link,
                onValueChange = { link = it },
                label = { Text(stringResource(R.string.link_do_video)) },
                singleLine = true,
                shape = Raios.Botao,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OptionRow(stringResource(R.string.opcao_capitulos_legenda), chaptersAndSubtitles) { chaptersAndSubtitles = it }
            OptionRow(stringResource(R.string.opcao_converter_mp3), convertToMp3) { convertToMp3 = it }
            OptionRow(stringResource(R.string.opcao_miniatura_capa), thumbnailAsCover) { thumbnailAsCover = it }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    onDownload(link, DownloadOptions(chaptersAndSubtitles, convertToMp3, thumbnailAsCover))
                    link = ""
                },
                enabled = link.isNotBlank(),
                shape = Raios.Botao,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
            ) {
                Text(stringResource(R.string.baixar_audio))
            }
        }
    }
}

@Composable
private fun OptionRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange),
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(checkedColor = Destaque, checkmarkColor = TextoSobreDestaque, uncheckedColor = Borda),
        )
        Spacer(Modifier.width(12.dp))
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun BackupCard(lastBackupAt: Long?, onExport: () -> Unit) {
    Surface(
        color = Superficie,
        contentColor = Texto,
        shape = Raios.Cartao,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = stringResource(R.string.posicoes_e_marcacoes), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = lastBackupAt?.let {
                        stringResource(
                            R.string.salvas_no_aparelho_backup,
                            formatBackupDate(it, stringResource(R.string.hoje), stringResource(R.string.ontem)),
                        )
                    } ?: stringResource(R.string.salvas_no_aparelho),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextoSecundario,
                )
            }
            Spacer(Modifier.width(12.dp))
            OutlinedButton(
                onClick = onExport,
                shape = Raios.Botao,
                border = BorderStroke(1.dp, Borda),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Texto),
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Icon(Icons.Outlined.SaveAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.exportar))
            }
        }
    }
}

/** O progresso do download é dividido em etapas: 0–79 baixando, 80–94 convertendo, 95+ capítulos. */
@Composable
private fun downloadStatus(progress: Int): String = when {
    progress < DownloadWorker.CONVERT_START ->
        stringResource(R.string.status_baixando, progress * 100 / DownloadWorker.CONVERT_START)
    progress < DownloadWorker.CHAPTERS_START -> stringResource(
        R.string.status_convertendo,
        (progress - DownloadWorker.CONVERT_START) * 100 / (DownloadWorker.CHAPTERS_START - DownloadWorker.CONVERT_START),
    )
    else -> stringResource(R.string.status_processando, progress)
}
