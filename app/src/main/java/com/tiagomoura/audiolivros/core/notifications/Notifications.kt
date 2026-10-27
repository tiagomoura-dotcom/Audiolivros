package com.tiagomoura.audiolivros.core.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import com.tiagomoura.audiolivros.MainActivity
import com.tiagomoura.audiolivros.R

/** Notificações de tarefas longas (análise de capítulos e downloads). */
object Notifications {
    const val CHANNEL_TASKS = "tarefas"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_TASKS, context.getString(R.string.canal_tarefas), NotificationManager.IMPORTANCE_LOW).apply {
                description = context.getString(R.string.canal_tarefas_descricao)
                setShowBadge(false)
            },
        )
    }

    /** Notificação de progresso: título, etapa ("Baixando", "Detectando capítulos") e porcentagem. */
    fun progress(context: Context, title: String, step: String, percent: Int?): Notification {
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, CHANNEL_TASKS)
            .setSmallIcon(R.drawable.media3_notification_small_icon)
            .setContentTitle(title)
            .setContentText(if (percent != null) "$step · $percent%" else step)
            .setProgress(100, percent ?: 0, percent == null)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openApp)
            .build()
    }

    fun foregroundInfo(id: Int, notification: Notification): ForegroundInfo =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(id, notification)
        }
}
