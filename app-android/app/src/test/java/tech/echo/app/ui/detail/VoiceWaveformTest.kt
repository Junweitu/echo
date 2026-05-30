package tech.echo.app.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceWaveformTest {

    @Test
    fun `heights are deterministic and normalized`() {
        val first = VoiceWaveform.heights("segment-a.wav", count = 8)
        val second = VoiceWaveform.heights("segment-a.wav", count = 8)

        assertEquals(first, second)
        assertEquals(8, first.size)
        assertTrue(first.all { it in 0.25f..1f })
    }

    @Test
    fun `played bar count follows playback progress`() {
        assertEquals(0, VoiceWaveform.playedBars(progress = 0f, count = 20))
        assertEquals(10, VoiceWaveform.playedBars(progress = 0.5f, count = 20))
        assertEquals(20, VoiceWaveform.playedBars(progress = 1f, count = 20))
    }
}
