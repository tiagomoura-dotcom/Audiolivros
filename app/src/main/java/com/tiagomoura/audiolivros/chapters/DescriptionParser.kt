package com.tiagomoura.audiolivros.chapters

/**
 * Lista de capítulos colada da descrição de um vídeo (seção 5, item 3). Cada
 * linha com um horário no início ou no fim vira um capítulo; as demais são ignoradas.
 */
object DescriptionParser {

    private const val TIME = """(?:(\d{1,2}):)?(\d{1,2}):(\d{2})(?!\d)"""
    private const val SEPARATORS = """[\s\-–—:|.•·*]*"""

    private val timeAtStart = Regex("""^\s*[•*\-–—]?\s*[\[(]?\s*$TIME\s*[\])]?$SEPARATORS(.*)$""")
    private val timeAtEnd = Regex("""^(.*?)$SEPARATORS[\[(]?\s*$TIME\s*[\])]?\s*$""")
    private val edgeSeparators = Regex("""^[\s\-–—:|.•·*]+|[\s\-–—:|•·*]+$""")

    fun parse(text: String): List<ParsedChapter> =
        text.lineSequence().mapNotNull(::parseLine).toList().normalized()

    internal fun parseLine(line: String): ParsedChapter? {
        timeAtStart.matchEntire(line)?.let { match ->
            val (hours, minutes, seconds, title) = match.destructured
            val start = toMillis(hours, minutes, seconds) ?: return null
            return ParsedChapter(clean(title), start)
        }
        timeAtEnd.matchEntire(line)?.let { match ->
            val (title, hours, minutes, seconds) = match.destructured
            val start = toMillis(hours, minutes, seconds) ?: return null
            return ParsedChapter(clean(title), start)
        }
        return null
    }

    private fun clean(title: String) = title.replace(edgeSeparators, "").trim()

    private fun toMillis(hours: String, minutes: String, seconds: String): Long? {
        val h = hours.toLongOrNull() ?: 0
        val m = minutes.toLong()
        val s = seconds.toLong()
        if (s >= 60) return null
        if (hours.isNotEmpty() && m >= 60) return null
        return ((h * 60 + m) * 60 + s) * 1000
    }
}
