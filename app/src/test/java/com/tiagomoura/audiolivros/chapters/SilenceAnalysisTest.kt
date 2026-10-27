package com.tiagomoura.audiolivros.chapters

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class SilenceAnalysisTest {

    /** Envelope com uma janela de 100 ms por caractere: '#' = fala (-10 dB), '.' = silêncio (-60 dB). */
    private fun envelope(pattern: String) =
        Envelope(100, FloatArray(pattern.length) { if (pattern[it] == '#') -10f else -60f })

    @Test
    fun `envelope em dBFS por janela, com blocos de qualquer tamanho`() {
        val sampleRate = 8_000
        val builder = EnvelopeBuilder(sampleRate)
        // 1 s de seno com amplitude 0,5 (RMS ≈ 0,354 → ≈ −9 dBFS) seguido de 0,5 s de silêncio,
        // entregue em blocos irregulares.
        val tone = FloatArray(sampleRate) { (0.5 * sin(2 * PI * 440 * it / sampleRate)).toFloat() }
        val silence = FloatArray(sampleRate / 2)
        tone.toList().chunked(777).forEach { builder.add(it.toFloatArray()) }
        builder.add(silence)
        val result = builder.finish()
        assertEquals(15, result.levelsDb.size)
        assertEquals(-9.03f, result.levelsDb[3], 0.1f)
        assertEquals(EnvelopeBuilder.FLOOR_DB, result.levelsDb[12], 0.001f)
    }

    @Test
    fun `envelope salvo e lido de volta`() {
        val original = Envelope(100, floatArrayOf(-10f, -55.5f, -100f))
        val bytes = ByteArrayOutputStream().also { original.write(it) }.toByteArray()
        val read = Envelope.read(ByteArrayInputStream(bytes))!!
        assertEquals(100, read.windowMs)
        assertArrayEquals(original.levelsDb, read.levelsDb, 0f)
    }

    @Test
    fun `silêncios com duração mínima`() {
        val silences = SilenceDetector.silences(envelope("##...###......##."), thresholdDb = -40f, minPauseMs = 300)
        assertEquals(listOf(Silence(200, 500), Silence(800, 1_400)), silences)
    }

    @Test
    fun `capítulos no fim de cada pausa`() {
        // 10 s de fala, 3 s de pausa, 10 s de fala, 3 s de pausa, 10 s de fala.
        val pattern = "#".repeat(100) + ".".repeat(30) + "#".repeat(100) + ".".repeat(30) + "#".repeat(100)
        val chapters = SilenceDetector.chapters(envelope(pattern), -40f, minPauseMs = 2_000, minChapterMs = 5_000)
        assertEquals(listOf(0L, 13_000L, 26_000L), chapters.map { it.startMs })
        assertEquals(listOf("Capítulo 1", "Capítulo 2", "Capítulo 3"), chapters.map { it.title })
    }

    @Test
    fun `mantém a pausa mais longa quando duas criariam capítulo curto`() {
        // Pausa curta (2 s) e logo depois uma longa (4 s): só a longa vira capítulo.
        val pattern = "#".repeat(100) + ".".repeat(20) + "#".repeat(20) + ".".repeat(40) + "#".repeat(100)
        val chapters = SilenceDetector.chapters(envelope(pattern), -40f, minPauseMs = 2_000, minChapterMs = 8_000)
        assertEquals(listOf(0L, 18_000L), chapters.map { it.startMs })
    }

    @Test
    fun `limiar e pausa mínima mudam o resultado sem decodificar de novo`() {
        val levels = FloatArray(300) { if (it in 100..129) -45f else -10f }
        val env = Envelope(100, levels)
        assertEquals(2, SilenceDetector.chapters(env, -40f, 2_000, 5_000).size)
        assertEquals(1, SilenceDetector.chapters(env, -50f, 2_000, 5_000).size)
        assertEquals(1, SilenceDetector.chapters(env, -40f, 4_000, 5_000).size)
    }

    @Test
    fun `pausas perto do início ou do fim não criam capítulo`() {
        val pattern = "#".repeat(10) + ".".repeat(30) + "#".repeat(200) + ".".repeat(30) + "#".repeat(10)
        val chapters = SilenceDetector.chapters(envelope(pattern), -40f, 2_000, 5_000)
        assertEquals(listOf(0L), chapters.map { it.startMs })
    }
}
