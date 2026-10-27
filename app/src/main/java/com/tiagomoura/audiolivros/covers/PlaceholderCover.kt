package com.tiagomoura.audiolivros.covers

/** Capa provisória: cor escolhida pelo hash do título e iniciais (seção 3). */
object PlaceholderCover {
    val COLORS: List<Int> = listOf(
        0xFF3B4A6B.toInt(),
        0xFF5B3F4E.toInt(),
        0xFF4A5A3E.toInt(),
        0xFF2F5B57.toInt(),
    )

    private val ignoredWords = setOf("a", "as", "o", "os", "de", "da", "das", "do", "dos", "e", "the", "of")

    fun colorFor(title: String): Int = COLORS[Math.floorMod(title.trim().lowercase().hashCode(), COLORS.size)]

    /** Até duas iniciais das palavras significativas do título: "Dom Casmurro" → "DC". */
    fun initials(title: String): String {
        val words = title.split(Regex("[\\s\\p{Punct}]+")).filter { it.isNotBlank() }
        val significant = words.filter { it.lowercase() !in ignoredWords }.ifEmpty { words }
        return significant.take(2).mapNotNull { word -> word.firstOrNull { it.isLetterOrDigit() } }
            .joinToString("") { it.uppercase() }
            .ifEmpty { "?" }
    }
}
