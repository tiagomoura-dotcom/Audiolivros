package com.tiagomoura.audiolivros.chapters

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Nível de volume (RMS em dBFS) a cada janela de [windowMs], calculado em blocos
 * a partir de amostras PCM mono normalizadas entre −1 e 1. O áudio nunca fica
 * inteiro na memória: só a soma da janela atual.
 */
class EnvelopeBuilder(private val sampleRate: Int, private val windowMs: Int = DEFAULT_WINDOW_MS) {

    private val samplesPerWindow = (sampleRate.toLong() * windowMs / 1000).toInt().coerceAtLeast(1)
    private var sumSquares = 0.0
    private var count = 0
    private val levels = FloatList()

    fun add(samples: FloatArray, size: Int = samples.size) {
        for (i in 0 until size) {
            val sample = samples[i]
            sumSquares += sample * sample
            count++
            if (count == samplesPerWindow) closeWindow()
        }
    }

    private fun closeWindow() {
        levels.add(toDbfs(sqrt(sumSquares / count)))
        sumSquares = 0.0
        count = 0
    }

    /** Fecha a janela incompleta do fim e devolve o envelope. */
    fun finish(): Envelope {
        if (count > 0) closeWindow()
        return Envelope(windowMs, levels.toArray())
    }

    companion object {
        const val DEFAULT_WINDOW_MS = 100
        const val FLOOR_DB = -100f

        fun toDbfs(rms: Double): Float =
            if (rms <= 0.0) FLOOR_DB else (20 * log10(rms)).toFloat().coerceAtLeast(FLOOR_DB)
    }
}

/** Envelope salvo: uma lista de floats (cerca de 1,4 MB para 10 h com janelas de 100 ms). */
class Envelope(val windowMs: Int, val levelsDb: FloatArray) {

    val durationMs: Long get() = levelsDb.size.toLong() * windowMs

    fun write(output: OutputStream) {
        val data = DataOutputStream(output.buffered())
        data.writeInt(MAGIC)
        data.writeInt(windowMs)
        data.writeInt(levelsDb.size)
        for (level in levelsDb) data.writeFloat(level)
        data.flush()
    }

    companion object {
        private const val MAGIC = 0x454E5631 // "ENV1"

        fun read(input: InputStream): Envelope? {
            val data = DataInputStream(input.buffered())
            if (data.readInt() != MAGIC) return null
            val windowMs = data.readInt()
            val size = data.readInt()
            if (windowMs <= 0 || size < 0) return null
            return Envelope(windowMs, FloatArray(size) { data.readFloat() })
        }
    }
}

data class Silence(val startMs: Long, val endMs: Long) {
    val durationMs: Long get() = endMs - startMs
}

/**
 * Detecção de capítulos por pausas (seção 5.1). Silêncio = janelas seguidas abaixo
 * do limiar com a duração mínima. Cada fim de silêncio é candidato a início de
 * capítulo; candidatos que criariam capítulos menores que o mínimo são descartados,
 * mantendo os silêncios mais longos.
 */
object SilenceDetector {

    fun silences(envelope: Envelope, thresholdDb: Float, minPauseMs: Long): List<Silence> {
        val result = mutableListOf<Silence>()
        val window = envelope.windowMs.toLong()
        var runStart = -1
        val levels = envelope.levelsDb
        for (i in 0..levels.size) {
            val quiet = i < levels.size && levels[i] < thresholdDb
            if (quiet && runStart < 0) runStart = i
            if (!quiet && runStart >= 0) {
                val silence = Silence(runStart * window, i * window)
                if (silence.durationMs >= minPauseMs) result += silence
                runStart = -1
            }
        }
        return result
    }

    fun chapters(
        envelope: Envelope,
        thresholdDb: Float,
        minPauseMs: Long,
        minChapterMs: Long,
        durationMs: Long = envelope.durationMs,
    ): List<ParsedChapter> {
        val candidates = silences(envelope, thresholdDb, minPauseMs)
            .filter { it.endMs in 1 until durationMs }
            .sortedByDescending { it.durationMs }
        val accepted = mutableListOf<Long>()
        for (silence in candidates) {
            val start = silence.endMs
            val farFromEdges = start >= minChapterMs && durationMs - start >= minChapterMs
            if (farFromEdges && accepted.all { kotlin.math.abs(it - start) >= minChapterMs }) {
                accepted += start
            }
        }
        val starts = listOf(0L) + accepted.sorted()
        return starts.mapIndexed { index, start -> ParsedChapter(defaultChapterTitle(index + 1), start) }
    }
}

/** Lista de floats que cresce sem caixas (Float) para não pesar em livros longos. */
internal class FloatList(initialCapacity: Int = 4096) {
    private var data = FloatArray(initialCapacity)
    var size = 0
        private set

    fun add(value: Float) {
        if (size == data.size) data = data.copyOf(data.size * 2)
        data[size++] = value
    }

    fun toArray(): FloatArray = data.copyOf(size)
}
