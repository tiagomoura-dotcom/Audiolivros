package com.tiagomoura.audiolivros.core.util

/** Formata milissegundos como "04:21" ou "1:02:33". */
fun formatDuration(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0) / 1000)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

/** Formata uma duração longa de forma resumida: "3 h 12 min", "45 min" ou "30 s". */
fun formatRemaining(ms: Long): String {
    val totalSeconds = ms.coerceAtLeast(0) / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return when {
        hours > 0 && minutes > 0 -> "$hours h $minutes min"
        hours > 0 -> "$hours h"
        minutes > 0 -> "$minutes min"
        else -> "$totalSeconds s"
    }
}

/** "hoje, 07:40", "ontem, 22:10" ou "25/10, 22:10" para o último backup. */
fun formatBackupDate(
    epochMs: Long,
    todayLabel: String,
    yesterdayLabel: String,
    now: Long = System.currentTimeMillis(),
): String {
    val zone = java.time.ZoneId.systemDefault()
    val date = java.time.Instant.ofEpochMilli(epochMs).atZone(zone)
    val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val time = "%02d:%02d".format(date.hour, date.minute)
    return when (date.toLocalDate()) {
        today -> "$todayLabel, $time"
        today.minusDays(1) -> "$yesterdayLabel, $time"
        else -> "%02d/%02d, %s".format(date.dayOfMonth, date.monthValue, time)
    }
}
