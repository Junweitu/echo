package tech.echo.app.core.audio

/**
 * VAD 状态机每帧产出的事件，驱动上层录音逻辑。
 */
sealed interface VadEvent {
    /** 静音/疑似有声未确认：无动作。 */
    data object Idle : VadEvent

    /** 确认进入录入：上层应补预录历史帧、开始累积本帧及后续帧。 */
    data object SegmentStart : VadEvent

    /** 录入中的普通帧：上层继续累积写入。 */
    data object FrameAppended : VadEvent

    /** 一段正常结束：上层落盘，[durationMs] 为有效时长。 */
    data class SegmentEnd(val durationMs: Long) : VadEvent

    /** 超长强制切段：上层落盘当前段（[durationMs]），并立即把本帧作为新段开头继续。 */
    data class SegmentSplit(val durationMs: Long) : VadEvent

    /** 段太短被丢弃（噪声）：上层丢掉累积的帧，不落盘。 */
    data object SegmentDiscarded : VadEvent
}
