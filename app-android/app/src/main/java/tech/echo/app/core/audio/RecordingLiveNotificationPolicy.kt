package tech.echo.app.core.audio

import android.app.NotificationManager
import androidx.core.app.NotificationCompat

object RecordingLiveNotificationPolicy {
    const val CHANNEL_ID = "echo_live_recording"
    const val LEGACY_CHANNEL_ID = "echo_recording"
    const val CHANNEL_IMPORTANCE = NotificationManager.IMPORTANCE_DEFAULT
    const val PRIORITY = NotificationCompat.PRIORITY_DEFAULT
    const val ENABLE_SOUND = false
    const val ENABLE_VIBRATION = false
}
