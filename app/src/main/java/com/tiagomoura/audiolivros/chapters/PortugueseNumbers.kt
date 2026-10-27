package com.tiagomoura.audiolivros.chapters

/**
 * Lê números escritos depois de uma palavra-chave: arábicos ("3"), romanos ("iii")
 * ou por extenso, cardinais ou ordinais ("tres", "terceiro", "vinte e um",
 * "vigesimo primeiro"). O texto já vem normalizado (minúsculas, sem acentos).
 */
object PortugueseNumbers {

    data class Result(val value: Int, val wordsUsed: Int)

    private val cardinals: Map<String, Int> = buildMap {
        put("zero", 0)
        listOf("um", "uma").forEach { put(it, 1) }
        listOf("dois", "duas").forEach { put(it, 2) }
        put("tres", 3); put("quatro", 4); put("cinco", 5); put("seis", 6)
        put("sete", 7); put("oito", 8); put("nove", 9); put("dez", 10)
        put("onze", 11); put("doze", 12); put("treze", 13)
        put("catorze", 14); put("quatorze", 14); put("quinze", 15)
        put("dezesseis", 16); put("dezasseis", 16); put("dezessete", 17); put("dezassete", 17)
        put("dezoito", 18); put("dezenove", 19); put("dezanove", 19)
        put("vinte", 20); put("trinta", 30); put("quarenta", 40); put("cinquenta", 50)
        put("sessenta", 60); put("setenta", 70); put("oitenta", 80); put("noventa", 90)
        put("cem", 100); put("cento", 100)
        listOf("duzentos", "duzentas").forEach { put(it, 200) }
        listOf("trezentos", "trezentas").forEach { put(it, 300) }
        listOf("quatrocentos", "quatrocentas").forEach { put(it, 400) }
        listOf("quinhentos", "quinhentas").forEach { put(it, 500) }
    }

    private val ordinalStems: Map<String, Int> = mapOf(
        "primeir" to 1, "segund" to 2, "terceir" to 3, "quart" to 4, "quint" to 5,
        "sext" to 6, "setim" to 7, "septim" to 7, "oitav" to 8, "non" to 9, "decim" to 10,
        "vigesim" to 20, "trigesim" to 30, "quadragesim" to 40, "quinquagesim" to 50,
        "sexagesim" to 60, "septuagesim" to 70, "setuagesim" to 70, "octogesim" to 80,
        "nonagesim" to 90, "centesim" to 100, "ducentesim" to 200, "trecentesim" to 300,
    )

    private fun ordinal(word: String): Int? {
        val stem = when {
            word.endsWith("os") || word.endsWith("as") -> word.dropLast(2)
            word.endsWith("o") || word.endsWith("a") -> word.dropLast(1)
            else -> return null
        }
        return ordinalStems[stem]
    }

    private fun wordValue(word: String): Int? = cardinals[word] ?: ordinal(word)

    /** Lê o número no começo de [words]. Retorna nulo se a primeira palavra não for número. */
    fun parse(words: List<String>): Result? {
        val first = words.firstOrNull() ?: return null
        first.toIntOrNull()?.let { return Result(it, 1) }
        romanValue(first)?.let { return Result(it, 1) }

        var total = 0
        var used = 0
        var index = 0
        var lastPart = Int.MAX_VALUE
        while (index < words.size) {
            val value = wordValue(words[index]) ?: break
            // Cada parte precisa ser de uma casa menor que a anterior: "vinte e um" (20 + 1),
            // "decimo primeiro" (10 + 1). "um uma" não vira 2.
            if (used > 0 && value >= placeOf(lastPart)) break
            total += value
            lastPart = value
            index++
            used = index
            // "vinte e um": o "e" só conta se vier outro número depois.
            if (index + 1 < words.size && words[index] == "e" && wordValue(words[index + 1]) != null) {
                index++
            }
        }
        return if (used > 0) Result(total, used) else null
    }

    /** Casa decimal de um número: 20 → 10, 100 → 100, 7 → 1. */
    private fun placeOf(value: Int): Int {
        var place = 1
        while (place * 10 <= value) place *= 10
        return place
    }

    /** Numeral romano válido de 1 a 3999 ("iv", "xii"); nulo para qualquer outra palavra. */
    fun romanValue(word: String): Int? {
        if (word.isEmpty() || word.any { it !in "ivxlcdm" }) return null
        val values = mapOf('i' to 1, 'v' to 5, 'x' to 10, 'l' to 50, 'c' to 100, 'd' to 500, 'm' to 1000)
        var total = 0
        for (i in word.indices) {
            val current = values.getValue(word[i])
            val next = word.getOrNull(i + 1)?.let(values::getValue) ?: 0
            total += if (current < next) -current else current
        }
        return total.takeIf { it in 1..3999 && toRoman(it) == word }
    }

    private fun toRoman(number: Int): String {
        val table = listOf(
            1000 to "m", 900 to "cm", 500 to "d", 400 to "cd", 100 to "c", 90 to "xc",
            50 to "l", 40 to "xl", 10 to "x", 9 to "ix", 5 to "v", 4 to "iv", 1 to "i",
        )
        var rest = number
        return buildString {
            for ((value, symbol) in table) {
                while (rest >= value) {
                    append(symbol)
                    rest -= value
                }
            }
        }
    }
}
