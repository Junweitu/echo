package tech.echo.app.core.audio

import tech.echo.app.core.model.RecordingStatus

enum class RecordingNotificationAction(val label: String) {
    PAUSE("暂停"),
    RESUME("继续"),
}

enum class RecordingLiveIndicator {
    MICROPHONE,
    BREATHING_DOT,
}

data class RecordingNotificationSpec(
    val title: String,
    val text: String,
    val shortCriticalText: String,
    val action: RecordingNotificationAction,
    val requestPromotedOngoing: Boolean = true,
    val colorized: Boolean = false,
    val color: Int,
    val livePulseColor: Int? = null,
    val liveIndicator: RecordingLiveIndicator,
    val showChronometer: Boolean,
    val progressIndeterminate: Boolean,
) {
    companion object {
        const val MAX_SHORT_CRITICAL_TEXT_LENGTH = 7
        const val COLOR_RECORDING = 0xFFE5484D.toInt()
        const val COLOR_LISTENING = 0xFF2563EB.toInt()
        const val COLOR_PAUSED = 0xFF5F6368.toInt()

        fun from(status: RecordingStatus): RecordingNotificationSpec = when (status) {
            RecordingStatus.PAUSED -> RecordingNotificationSpec(
                title = "Echo 已暂停",
                text = "麦克风已关闭",
                shortCriticalText = "暂停",
                action = RecordingNotificationAction.RESUME,
                color = COLOR_PAUSED,
                liveIndicator = RecordingLiveIndicator.MICROPHONE,
                showChronometer = false,
                progressIndeterminate = false,
            )
            RecordingStatus.LISTENING -> RecordingNotificationSpec(
                title = "Echo 聆听中",
                text = "等待人声，后台运行中",
                shortCriticalText = "聆听",
                action = RecordingNotificationAction.PAUSE,
                color = COLOR_LISTENING,
                livePulseColor = COLOR_LISTENING,
                liveIndicator = RecordingLiveIndicator.BREATHING_DOT,
                showChronometer = true,
                progressIndeterminate = true,
            )
            RecordingStatus.RECORDING -> RecordingNotificationSpec(
                title = "Echo 录制中",
                text = "检测到人声，正在保存片段",
                shortCriticalText = "录制",
                action = RecordingNotificationAction.PAUSE,
                color = COLOR_RECORDING,
                livePulseColor = COLOR_RECORDING,
                liveIndicator = RecordingLiveIndicator.BREATHING_DOT,
                showChronometer = true,
                progressIndeterminate = true,
            )
        }
    }
}
