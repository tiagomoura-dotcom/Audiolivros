package com.tiagomoura.audiolivros.chapters

/** Uma fala da legenda, com início e fim em milissegundos. */
data class SubtitleCue(val startMs: Long, val endMs: Long, val text: String)

/** Lê legendas .srt e .vtt. Trabalha linha a linha, sem carregar o arquivo de uma vez. */
object SubtitleParser {

    private const val TIMESTAMP = """(?:(\d+):)?(\d{1,2}):(\d{2})[,.](\d{1,3})"""
    private val timing = Regex("""^\s*$TIMESTAMP\s*-->\s*$TIMESTAMP""")
    private val tags = Regex("""<[^>]*>|\{[^}]*}""")

    fun parse(lines: Sequence<String>): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        var start = -1L
        var end = -1L
        val text = StringBuilder()

        fun flush() {
            if (start >= 0 && text.isNotBlank()) cues += SubtitleCue(start, end, text.toString().trim())
            start = -1
            text.clear()
        }

        for (rawLine in lines) {
            val line = rawLine.trimEnd('﻿', '\r')
            val match = timing.find(line)
            when {
                match != null -> {
                    flush()
                    val g = match.groupValues
                    start = toMillis(g[1], g[2], g[3], g[4])
                    end = toMillis(g[5], g[6], g[7], g[8])
                }
                line.isBlank() -> flush()
                start >= 0 -> {
                    if (text.isNotEmpty()) text.append(' ')
                    text.append(line.replace(tags, "").trim())
                }
            }
        }
        flush()
        return cues
    }

    fun parse(text: String): List<SubtitleCue> = parse(text.lineSequence())

    private fun toMillis(hours: String, minutes: String, seconds: String, fraction: String): Long {
        val millis = fraction.padEnd(3, '0').toLong()
        return (((hours.toLongOrNull() ?: 0) * 60 + minutes.toLong()) * 60 + seconds.toLong()) * 1000 + millis
    }
}
