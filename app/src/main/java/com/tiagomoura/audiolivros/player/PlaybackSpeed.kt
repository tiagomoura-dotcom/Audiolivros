package com.tiagomoura.audiolivros.player

import kotlin.math.abs
import kotlin.math.roundToInt

object PlaybackSpeed {
    /** Ciclo do botão Velocidade (seção 2.2). */
    val CYCLE = listOf(1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 0.75f)
    const val MIN = 0.5f
    const val MAX = 3.0f
    const val STEP = 0.05f

    /** Número de passos intermediários de um controle deslizante de [MIN] a [MAX]. */
    val SLIDER_STEPS: Int = ((MAX - MIN) / STEP).roundToInt() - 1

    /**
     * Próxima velocidade do ciclo. Se a atual não estiver no ciclo (foi ajustada
     * fina), vai para o primeiro valor do ciclo maior que ela.
     */
    fun next(current: Float): Float {
        val index = CYCLE.indexOfFirst { abs(it - current) < 0.001f }
        if (index >= 0) return CYCLE[(index + 1) % CYCLE.size]
        return CYCLE.filter { it > current }.minOrNull() ?: CYCLE.min()
    }

    /** Arredonda para o passo de 0,05 e limita ao intervalo permitido. */
    fun normalize(speed: Float): Float {
        val steps = (speed / STEP).roundToInt()
        return (steps * STEP).coerceIn(MIN, MAX).let { (it * 100).roundToInt() / 100f }
    }

    /** "1,0", "1,25", "1,5" — com vírgula decimal, como no Brasil. */
    fun label(speed: Float): String {
        val hundredths = (speed * 100).roundToInt()
        val whole = hundredths / 100
        val fraction = hundredths % 100
        val fractionText = when {
            fraction == 0 -> "0"
            fraction % 10 == 0 -> (fraction / 10).toString()
            else -> "%02d".format(fraction)
        }
        return "$whole,$fractionText"
    }
}
