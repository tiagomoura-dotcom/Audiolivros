package com.tiagomoura.audiolivros.chapters

/**
 * Detecta capítulos numa legenda (seção 5, item 4): palavra-chave seguida de número
 * arábico, romano ou por extenso. Palavras como "prólogo" e "epílogo" valem sozinhas
 * quando abrem a fala. Ocorrências a menos de 60 s de um capítulo já marcado são
 * ignoradas, para evitar menções no meio da narração.
 */
class SubtitleChapterDetector(keywords: List<String>) {

    private class Keyword(val display: String, val normalized: String, val pattern: Regex)

    private val keywords: List<Keyword> = keywords
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinctBy { normalizeText(it) }
        .map { keyword ->
            val normalized = normalizeText(keyword)
            val boundary = if (normalized.last().isLetter()) "(?![a-z])" else ""
            Keyword(
                display = keyword.replaceFirstChar { it.uppercase() },
                normalized = normalized,
                pattern = Regex("""(?<![a-z])${Regex.escape(normalized)}$boundary[\s:.\-]*"""),
            )
        }

    /**
     * @param silences pausas conhecidas (início e fim em ms). Se uma pausa de pelo
     * menos 1 s terminar até 5 s antes da fala, o capítulo começa no fim dela.
     */
    fun detect(cues: List<SubtitleCue>, silences: List<LongRange> = emptyList()): List<ParsedChapter> {
        val found = mutableListOf<ParsedChapter>()
        var lastStart = Long.MIN_VALUE
        for (cue in cues.sortedBy { it.startMs }) {
            val title = matchTitle(normalizeText(cue.text)) ?: continue
            if (lastStart != Long.MIN_VALUE && cue.startMs - lastStart < MIN_DISTANCE_MS) continue
            val start = adjustToSilence(cue.startMs, silences)
            found += ParsedChapter(title, start)
            lastStart = cue.startMs
        }
        return found.normalized()
    }

    private fun matchTitle(text: String): String? {
        for (keyword in keywords) {
            for (match in keyword.pattern.findAll(text)) {
                val words = text.substring(match.range.last + 1)
                    .split(' ')
                    .map { it.trim(',', '.', ';', ':', '!', '?', '"', '\'', ')', '(') }
                    .filter { it.isNotEmpty() }
                    .take(MAX_NUMBER_WORDS)
                val number = PortugueseNumbers.parse(words)
                if (number != null) return "${keyword.display} ${number.value}"
                if (keyword.normalized in STANDALONE && match.range.first == 0) return keyword.display
            }
        }
        return null
    }

    private fun adjustToSilence(startMs: Long, silences: List<LongRange>): Long {
        val silence = silences
            .filter { it.last - it.first >= MIN_SILENCE_MS && it.last in (startMs - SILENCE_WINDOW_MS)..startMs }
            .maxByOrNull { it.last }
        return silence?.last ?: startMs
    }

    companion object {
        const val MIN_DISTANCE_MS = 60_000L
        private const val MIN_SILENCE_MS = 1_000L
        private const val SILENCE_WINDOW_MS = 5_000L
        private const val MAX_NUMBER_WORDS = 6
        private val STANDALONE = setOf("prologo", "epilogo", "prefacio", "introducao", "apresentacao", "posfacio")

        val DEFAULT_KEYWORDS = listOf("capítulo", "cap.", "parte", "prólogo", "epílogo", "prefácio")
    }
}
