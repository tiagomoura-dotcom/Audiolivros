package com.tiagomoura.audiolivros.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tiagomoura.audiolivros.R
import com.tiagomoura.audiolivros.backup.BackupRepository
import com.tiagomoura.audiolivros.chapters.LabeledSlider
import com.tiagomoura.audiolivros.chapters.formatDecimal
import com.tiagomoura.audiolivros.core.theme.Destaque
import com.tiagomoura.audiolivros.core.theme.Raios
import com.tiagomoura.audiolivros.core.theme.RotuloSecao
import com.tiagomoura.audiolivros.core.theme.Superficie
import com.tiagomoura.audiolivros.core.theme.Superficie2
import com.tiagomoura.audiolivros.core.theme.Texto
import com.tiagomoura.audiolivros.core.theme.TextoSecundario
import com.tiagomoura.audiolivros.core.theme.TextoSobreDestaque
import com.tiagomoura.audiolivros.core.util.formatBackupDate
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val ignoringBattery = rememberIgnoringBatteryOptimizations()

    LaunchedEffect(viewModel) {
        viewModel.messageFlow.collect { message ->
            val text = when (message) {
                is SettingsMessage.YtDlpUpdated -> context.getString(
                    if (message.alreadyUpToDate) R.string.ytdlp_ja_atualizado else R.string.ytdlp_atualizado,
                    message.version ?: "?",
                )
                is SettingsMessage.Failed -> context.getString(R.string.erro_generico, message.reason ?: context.getString(R.string.erro_desconhecido))
                SettingsMessage.Exported -> context.getString(R.string.backup_exportado)
                is SettingsMessage.Imported -> context.getString(R.string.backup_importado, message.matched, message.missing)
            }
            snackbar.showSnackbar(text)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) viewModel.export(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.import(uri)
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.setPositionFolder(uri)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.configuracoes), style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.voltar))
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
                bottom = padding.calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "reproducao") {
                Section(stringResource(R.string.secao_reproducao)) {
                    Text(stringResource(R.string.intervalo_pulo), style = MaterialTheme.typography.bodyMedium)
                    Choices(PlaybackSettings.SKIP_OPTIONS, state.playback.skipIntervalSeconds, viewModel::setSkip) {
                        stringResource(R.string.segundos, it)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.retroceder_ao_retomar), style = MaterialTheme.typography.bodyMedium)
                    Choices(PlaybackSettings.REWIND_OPTIONS, state.playback.rewindOnResumeSeconds, viewModel::setRewind) {
                        stringResource(R.string.segundos, it)
                    }
                }
            }

            item(key = "deteccao") {
                val detection = state.detection
                Section(stringResource(R.string.secao_deteccao)) {
                    LabeledSlider(
                        label = stringResource(R.string.limiar_silencio, detection.silenceThresholdDb.roundToInt()),
                        help = stringResource(R.string.limiar_silencio_ajuda),
                        value = detection.silenceThresholdDb,
                        range = -60f..-20f,
                        steps = 39,
                        onChange = { viewModel.setDetection(detection.copy(silenceThresholdDb = it.roundToInt().toFloat())) },
                    )
                    LabeledSlider(
                        label = stringResource(R.string.pausa_minima, formatDecimal(detection.minPauseSeconds)),
                        help = null,
                        value = detection.minPauseSeconds,
                        range = 0.5f..6f,
                        steps = 10,
                        onChange = { viewModel.setDetection(detection.copy(minPauseSeconds = (it * 2).roundToInt() / 2f)) },
                    )
                    LabeledSlider(
                        label = stringResource(R.string.capitulo_minimo, detection.minChapterMinutes.roundToInt()),
                        help = null,
                        value = detection.minChapterMinutes,
                        range = 1f..30f,
                        steps = 28,
                        onChange = { viewModel.setDetection(detection.copy(minChapterMinutes = it.roundToInt().toFloat())) },
                    )
                    KeywordsField(detection.keywords) { viewModel.setDetection(detection.copy(keywords = it)) }
                }
            }

            item(key = "downloads") {
                Section(stringResource(R.string.secao_downloads)) {
                    Text(
                        text = stringResource(R.string.versao_ytdlp, state.ytDlpVersion ?: stringResource(R.string.carregando)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = viewModel::updateYtDlp,
                        enabled = !state.updatingYtDlp,
                        shape = Raios.Botao,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(if (state.updatingYtDlp) R.string.atualizando_ytdlp else R.string.atualizar_ytdlp))
                    }
                }
            }

            item(key = "bateria") {
                Column {
                    Text(stringResource(R.string.secao_bateria).uppercase(), style = RotuloSecao, modifier = Modifier.padding(bottom = 8.dp))
                    BatteryCard(ignoring = ignoringBattery)
                }
            }

            item(key = "backup") {
                Section(stringResource(R.string.secao_backup)) {
                    Text(
                        text = state.lastBackupAt?.let { stringResource(R.string.ultimo_backup, formatBackupDate(it, stringResource(R.string.hoje), stringResource(R.string.ontem))) }
                            ?: stringResource(R.string.nenhum_backup),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextoSecundario,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { exportLauncher.launch(BackupRepository.backupFileName()) },
                            shape = Raios.Botao,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp),
                        ) { Text(stringResource(R.string.exportar)) }
                        OutlinedButton(
                            onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                            shape = Raios.Botao,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp),
                        ) { Text(stringResource(R.string.importar)) }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 48.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.arquivo_posicao_auto), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = state.positionFolder?.let { folderLabel(it) } ?: stringResource(R.string.arquivo_posicao_ajuda),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextoSecundario,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = state.positionFolder != null,
                            onCheckedChange = { on -> if (on) folderLauncher.launch(null) else viewModel.setPositionFolder(null) },
                            colors = SwitchDefaults.colors(checkedThumbColor = TextoSobreDestaque, checkedTrackColor = Destaque),
                        )
                    }
                    if (state.positionFolder != null) {
                        OutlinedButton(
                            onClick = { folderLauncher.launch(null) },
                            shape = Raios.Botao,
                            modifier = Modifier.heightIn(min = 48.dp),
                        ) { Text(stringResource(R.string.trocar_pasta)) }
                    }
                }
            }
        }
    }
}

private fun folderLabel(uri: String): String =
    Uri.decode(Uri.parse(uri).lastPathSegment.orEmpty()).substringAfter(':').ifEmpty { uri }

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title.uppercase(), style = RotuloSecao, modifier = Modifier.padding(bottom = 8.dp))
        Surface(color = Superficie, contentColor = Texto, shape = Raios.Cartao, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) { content() }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun Choices(options: List<Int>, selected: Int, onSelect: (Int) -> Unit, label: @Composable (Int) -> String) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Destaque,
                    selectedLabelColor = TextoSobreDestaque,
                    containerColor = Superficie2,
                ),
            )
        }
    }
}

@Composable
private fun KeywordsField(keywords: List<String>, onSave: (List<String>) -> Unit) {
    var text by rememberSaveable(keywords) { mutableStateOf(keywords.joinToString(", ")) }
    val parsed = text.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(stringResource(R.string.palavras_chave)) },
        supportingText = { Text(stringResource(R.string.palavras_chave_ajuda)) },
        modifier = Modifier.fillMaxWidth(),
    )
    if (parsed != keywords && parsed.isNotEmpty()) {
        OutlinedButton(onClick = { onSave(parsed) }, shape = Raios.Botao, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.salvar))
        }
    }
}
