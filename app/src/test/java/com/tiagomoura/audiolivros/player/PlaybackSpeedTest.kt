package com.tiagomoura.audiolivros.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackSpeedTest {

    @Test
    fun `ciclo do botão velocidade`() {
        var speed = 1.0f
        val visited = mutableListOf<Float>()
        repeat(6) {
            speed = PlaybackSpeed.next(speed)
            visited += speed
        }
        assertEquals(listOf(1.25f, 1.5f, 1.75f, 2.0f, 0.75f, 1.0f), visited)
    }

    @Test
    fun `velocidade fora do ciclo vai para o próximo valor maior`() {
        assertEquals(1.25f, PlaybackSpeed.next(1.1f), 0.001f)
        assertEquals(0.75f, PlaybackSpeed.next(2.5f), 0.001f)
    }

    @Test
    fun `ajuste fino arredonda para passos de 0,05 e respeita os limites`() {
        assertEquals(1.35f, PlaybackSpeed.normalize(1.3612f), 0.0001f)
        assertEquals(0.5f, PlaybackSpeed.normalize(0.1f), 0.0001f)
        assertEquals(3.0f, PlaybackSpeed.normalize(3.7f), 0.0001f)
    }

    @Test
    fun `controle deslizante tem um ponto a cada 0,05`() {
        // 0,5 a 3,0 em passos de 0,05 = 51 valores = 49 passos intermediários.
        assertEquals(49, PlaybackSpeed.SLIDER_STEPS)
    }

    @Test
    fun `rótulo com vírgula decimal`() {
        assertEquals("1,0", PlaybackSpeed.label(1.0f))
        assertEquals("1,25", PlaybackSpeed.label(1.25f))
        assertEquals("1,5", PlaybackSpeed.label(1.5f))
        assertEquals("0,75", PlaybackSpeed.label(0.75f))
        assertEquals("2,05", PlaybackSpeed.label(2.05f))
        assertEquals("3,0", PlaybackSpeed.label(3.0f))
    }
}
