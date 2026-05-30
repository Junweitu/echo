package tech.echo.app.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnergyFallbackVadDetectorTest {

    private class FakeVad(private val probability: Float) : VadDetector {
        var resetCalled = false
        var closeCalled = false

        override fun probability(frame: ShortArray): Float = probability
        override fun reset() {
            resetCalled = true
        }
        override fun close() {
            closeCalled = true
        }
    }

    @Test
    fun `loud frame becomes speech when model probability is too low`() {
        val detector = EnergyFallbackVadDetector(
            delegate = FakeVad(0.01f),
            rmsThreshold = 0.008f,
        )
        val loudFrame = ShortArray(AudioConfig.FRAME_SAMPLES) { 5_000 }

        assertTrue(detector.probability(loudFrame) >= AudioConfig.VAD_THRESHOLD)
    }

    @Test
    fun `quiet frame keeps model probability`() {
        val detector = EnergyFallbackVadDetector(
            delegate = FakeVad(0.01f),
            rmsThreshold = 0.008f,
        )
        val quietFrame = ShortArray(AudioConfig.FRAME_SAMPLES) { 40 }

        assertEquals(0.01f, detector.probability(quietFrame), 0.0001f)
    }

    @Test
    fun `delegate high probability is preserved`() {
        val detector = EnergyFallbackVadDetector(
            delegate = FakeVad(0.7f),
            rmsThreshold = 0.008f,
        )
        val quietFrame = ShortArray(AudioConfig.FRAME_SAMPLES) { 40 }

        assertEquals(0.7f, detector.probability(quietFrame), 0.0001f)
    }

    @Test
    fun `reset and close are forwarded`() {
        val delegate = FakeVad(0.01f)
        val detector = EnergyFallbackVadDetector(delegate = delegate)

        detector.reset()
        detector.close()

        assertTrue(delegate.resetCalled)
        assertTrue(delegate.closeCalled)
    }
}
