package com.tiagomoura.audiolivros.settings

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.tiagomoura.audiolivros.R
import com.tiagomoura.audiolivros.core.theme.Destaque
import com.tiagomoura.audiolivros.core.theme.Raios
import com.tiagomoura.audiolivros.core.theme.Superficie
import com.tiagomoura.audiolivros.core.theme.Texto
import com.tiagomoura.audiolivros.core.theme.TextoSecundario

/** Otimização de bateria (seção 6.2): alguns fabricantes encerram apps em segundo plano. */
object BatteryOptimization {
    fun isIgnoring(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

    @SuppressLint("BatteryLife")
    fun requestIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))

    fun dontKillMyAppIntent(): Intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://dontkillmyapp.com"))
}

/** Estado da otimização de bateria, reavaliado sempre que a tela volta ao primeiro plano. */
@Composable
fun rememberIgnoringBatteryOptimizations(): Boolean {
    val context = LocalContext.current
    var ignoring by remember { mutableStateOf(BatteryOptimization.isIgnoring(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { ignoring = BatteryOptimization.isIgnoring(context) }
    return ignoring
}

/** Cartão que explica por que liberar o app da otimização de bateria. */
@Composable
fun BatteryCard(ignoring: Boolean, onDismiss: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Surface(color = Superficie, contentColor = Texto, shape = Raios.Cartao, modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (ignoring) Icons.Outlined.BatteryFull else Icons.Outlined.BatteryAlert,
                    contentDescription = null,
                    tint = if (ignoring) TextoSecundario else Destaque,
                )
                Spacer(Modifier.padding(start = 10.dp))
                Text(
                    text = stringResource(if (ignoring) R.string.bateria_liberado else R.string.bateria_titulo),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (!ignoring) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.bateria_explicacao), style = MaterialTheme.typography.bodySmall, color = TextoSecundario)
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { runCatching { context.startActivity(BatteryOptimization.requestIntent(context)) } },
                    shape = Raios.Botao,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.bateria_liberar)) }
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { runCatching { context.startActivity(BatteryOptimization.dontKillMyAppIntent()) } }) {
                        Text(stringResource(R.string.bateria_fabricante))
                    }
                    if (onDismiss != null) {
                        TextButton(onClick = onDismiss) { Text(stringResource(R.string.dispensar)) }
                    }
                }
            }
        }
    }
}
