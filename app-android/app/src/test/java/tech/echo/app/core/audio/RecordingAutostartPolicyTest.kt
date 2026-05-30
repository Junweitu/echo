package tech.echo.app.core.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingAutostartPolicyTest {

    @Test
    fun `starts only after onboarding when mic permission is granted`() {
        assertTrue(RecordingAutostartPolicy.shouldStart(onboarded = true, hasMicPermission = true))
        assertFalse(RecordingAutostartPolicy.shouldStart(onboarded = false, hasMicPermission = true))
        assertFalse(RecordingAutostartPolicy.shouldStart(onboarded = true, hasMicPermission = false))
    }
}
