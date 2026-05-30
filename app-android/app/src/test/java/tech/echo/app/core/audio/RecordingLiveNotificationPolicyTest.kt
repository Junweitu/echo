package tech.echo.app.core.audio

import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RecordingLiveNotificationPolicyTest {

    @Test
    fun `live notification channel is promotable without sound or vibration`() {
        assertNotEquals(
            RecordingLiveNotificationPolicy.LEGACY_CHANNEL_ID,
            RecordingLiveNotificationPolicy.CHANNEL_ID,
        )
        assertEquals(
            NotificationManager.IMPORTANCE_DEFAULT,
            RecordingLiveNotificationPolicy.CHANNEL_IMPORTANCE,
        )
        assertEquals(
            NotificationCompat.PRIORITY_DEFAULT,
            RecordingLiveNotificationPolicy.PRIORITY,
        )
        assertFalse(RecordingLiveNotificationPolicy.ENABLE_SOUND)
        assertFalse(RecordingLiveNotificationPolicy.ENABLE_VIBRATION)
    }
}
