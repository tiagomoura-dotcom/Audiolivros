package com.tiagomoura.audiolivros.chapters

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tiagomoura.audiolivros.R
import com.tiagomoura.audiolivros.core.theme.Borda
import com.tiagomoura.audiolivros.core.theme.Destaque
import com.tiagomoura.audiolivros.core.theme.Divisoria
import com.tiagomoura.audiolivros.core.theme.Raios
import com.tiagomoura.audiolivros.core.theme.RotuloSecao
import com.tiagomoura.audiolivros.core.theme.Superficie
import com.tiagomoura.audiolivros.core.theme.Texto
import com.tiagomoura.audiolivros.core.theme.TextoSecundario
import com.tiagomoura.audiolivros.core.util.formatDuration
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SilenceScreen(
    onBack: () -> Unit,
    viewModel: SilenceViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.appliedFlow.collect { count ->
            snackbar.showSnackbar(context.resources.getQuantityString(R.plurals.capitulos_detectados, count, count))
            onBack()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.fonte_pausas), style = MaterialTheme.typography.titleMedium) },
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
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "titulo") {
                Text(text = state.title, style = MaterialTheme.typography.titleLarge)
            }
            val analysis = state.analysis
            if (analysis?.running == true) {
                item(key = "progresso") {
                    Surface(color = Superficie, contentColor = Texto, shape = Raios.Cartao, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.analisando_audio, analysis.percent), style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(10.dp))
                            LinearProgressIndicator(
                                progress = { analysis.percent / 100f },
                                color = Destaque,
                                trackColor = Borda,
                                modifier = Modifier.fillMaxWidth(),
                                drawStopIndicator = {},
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.analise_segundo_plano),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextoSecundario,
                            )
                            OutlinedButton(
                                onClick = viewModel::cancel,
                                shape = Raios.Botao,
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .heightIn(min = 48.dp),
                            ) { Text(stringResource(R.string.cancelar)) }
                        }
                    }
                }
            } else if (!state.hasEnvelope) {
                item(key = "analisar") {
                    Column {
                        if (analysis?.failed == true) {
                            Text(
                                text = stringResource(R.string.erro_reanalisar, analysis.error ?: stringResource(R.string.erro_desconhecido)),
                                color = Destaque,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        Text(
                            text = stringResource(R.string.pausas_explicacao),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextoSecundario,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = viewModel::analyze,
                            shape = Raios.Botao,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp),
                        ) { Text(stringResource(R.string.analisar_audio)) }
                    }
                }
            }

            if (state.hasEnvelope) {
                item(key = "sensibilidade") {
                    SensitivityControls(state.sensitivity, viewModel::setSensitivity)
                }
                item(key = "resultado") {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = pluralStringResource(R.plurals.livro_capitulos, state.preview.size, state.preview.size).uppercase(),
                            style = RotuloSecao,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                item(key = "aplicar") {
                    Button(
                        onClick = viewModel::apply,
                        enabled = state.preview.size > 1,
                        shape = Raios.Botao,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp),
                    ) { Text(stringResource(R.string.aplicar_capitulos)) }
                }
                items(state.preview, key = { it.startMs }) { chapter ->
                    Column {
                        Row(modifier = Modifier.padding(vertical = 10.dp)) {
                            Text(chapter.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Spacer(Modifier.width(8.dp))
                            Text(formatDuration(chapter.startMs), style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
                        }
                        HorizontalDivider(color = Divisoria)
                    }
                }
            }
        }
    }
}

@Composable
private fun SensitivityControls(value: Sensitivity, onChange: (Sensitivity) -> Unit) {
    Surface(color = Superficie, contentColor = Texto, shape = Raios.Cartao, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            LabeledSlider(
                label = stringResource(R.string.limiar_silencio, value.thresholdDb.roundToInt()),
                help = stringResource(R.string.limiar_silencio_ajuda),
                value = value.thresholdDb,
                range = -60f..-20f,
                steps = 39,
                onChange = { onChange(value.copy(thresholdDb = it.roundToInt().toFloat())) },
            )
            LabeledSlider(
                label = stringResource(R.string.pausa_minima, formatDecimal(value.minPauseSeconds)),
                help = null,
                value = value.minPauseSeconds,
                range = 0.5f..6f,
                steps = 10,
                onChange = { onChange(value.copy(minPauseSeconds = (it * 2).roundToInt() / 2f)) },
            )
            LabeledSlider(
                label = stringResource(R.string.capitulo_minimo, value.minChapterMinutes.roundToInt()),
                help = null,
                value = value.minChapterMinutes,
                range = 1f..30f,
                steps = 28,
                onChange = { onChange(value.copy(minChapterMinutes = it.roundToInt().toFloat())) },
            )
        }
    }
}

@Composable
fun LabeledSlider(
    label: String,
    help: String?,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        if (help != null) Text(help, style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(thumbColor = Destaque, activeTrackColor = Destaque, inactiveTrackColor = Borda),
        )
    }
}

/** "2,0", "1,5": uma casa decimal com vírgula. */
fun formatDecimal(value: Float): String {
    val tenths = (value * 10).roundToInt()
    return "${tenths / 10},${tenths % 10}"
}
