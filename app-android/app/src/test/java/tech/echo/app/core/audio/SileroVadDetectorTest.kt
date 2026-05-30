package tech.echo.app.core.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class SileroVadDetectorTest {

    @Test
    fun `sample rate tensor is scalar for Silero v5 model`() {
        assertArrayEquals(longArrayOf(), SileroVadDetector.sampleRateTensorShape())
    }
}
