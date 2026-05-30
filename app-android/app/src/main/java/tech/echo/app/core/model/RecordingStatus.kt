package tech.echo.app.core.model

/**
 * 录音状态（见 ui-design.md §4.1 今天主页三态）。
 * 与 Codex 实现的 RecordingController.state 对齐。
 */
enum class RecordingStatus {
    /** 已暂停，未在录音 */
    PAUSED,

    /** 正在聆听（待命，未检测到人声） */
    LISTENING,

    /** 检测到人声、正在记录 */
    RECORDING,
}

/** 今天主页的录音状态快照。 */
data class TodayState(
    val status: RecordingStatus = RecordingStatus.PAUSED,
    val segmentCount: Int = 0,      // 当天已记录段数
    val totalMinutes: Int = 0,      // 当天累计时长（分钟，约数）
    val summaryReady: Boolean = false, // 今天的整理是否已生成
)
