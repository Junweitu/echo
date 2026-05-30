package tech.echo.app.core.audio

/**
 * VAD 状态机（见 codex-core-audio.md §2.3 / design.md §4.1）。
 *
 * 输入：逐帧的有声概率 + 帧序（用于算时长）。
 * 输出：每帧产生一个 [VadEvent]，驱动上层"补开头 / 累积写入 / 落盘 / 丢弃"。
 *
 * 状态流转：
 *   SILENCE ──连续 SPEECH_START_FRAMES 帧有声──▶ RECORDING（发 SegmentStart，补预录）
 *   RECORDING ──每有声帧──▶ 发 FrameAppended
 *   RECORDING ──连续 SILENCE_END_FRAMES 帧静音──▶ SILENCE（发 SegmentEnd，判最短段）
 *   RECORDING ──时长达 MAX_SEGMENT_MS──▶ 强制 SegmentEnd 紧接新 SegmentStart（切段）
 *
 * 非线程安全：由单一音频线程串行 feed。
 */
class VadStateMachine(
    private val threshold: Float = AudioConfig.VAD_THRESHOLD,
    private val speechStartFrames: Int = AudioConfig.SPEECH_START_FRAMES,
    private val silenceEndFrames: Int = AudioConfig.SILENCE_END_FRAMES,
    private val frameMs: Int = AudioConfig.FRAME_MS,
    private val minSegmentMs: Int = AudioConfig.MIN_SEGMENT_MS,
    private val maxSegmentMs: Int = AudioConfig.MAX_SEGMENT_MS,
) {
    private enum class State { SILENCE, RECORDING }

    private var state = State.SILENCE

    /** SILENCE 态下累计的连续有声帧数（用于确认起始）。 */
    private var consecutiveSpeech = 0

    /** RECORDING 态下累计的连续静音帧数（用于确认结束）。 */
    private var consecutiveSilence = 0

    /** 当前段已累计的帧数（含补的预录帧由上层加，这里只算 VAD 看到的）。 */
    private var segmentFrames = 0

    /**
     * 喂入一帧的有声概率，返回本帧对应的事件。
     * @param probability 该帧有声概率（来自 VadDetector）。
     */
    fun feed(probability: Float): VadEvent {
        val voiced = probability >= threshold
        return when (state) {
            State.SILENCE -> onSilenceState(voiced)
            State.RECORDING -> onRecordingState(voiced)
        }
    }

    private fun onSilenceState(voiced: Boolean): VadEvent {
        if (!voiced) {
            consecutiveSpeech = 0
            return VadEvent.Idle
        }
        consecutiveSpeech++
        if (consecutiveSpeech < speechStartFrames) {
            // 疑似有声，还没确认——先不触发（去抖）
            return VadEvent.Idle
        }
        // 确认进入录入：补预录、把"确认期"这几帧算进段
        state = State.RECORDING
        consecutiveSilence = 0
        segmentFrames = consecutiveSpeech // 起始确认期的有声帧并入本段
        consecutiveSpeech = 0
        return VadEvent.SegmentStart
    }

    private fun onRecordingState(voiced: Boolean): VadEvent {
        segmentFrames++

        if (voiced) {
            consecutiveSilence = 0
            // 最长段保护只在有声推进时检查：避免段尾静音把时长推过上限误切段。
            if (segmentFrames * frameMs >= maxSegmentMs) {
                val durationMs = (segmentFrames * frameMs).toLong()
                consecutiveSilence = 0
                segmentFrames = 1 // 当前帧作为新段第 1 帧
                return VadEvent.SegmentSplit(durationMs)
            }
            return VadEvent.FrameAppended
        }

        // 静音帧：累计，达到阈值则结束段
        consecutiveSilence++
        if (consecutiveSilence >= silenceEndFrames) {
            // 段时长 = 总帧 - 末尾静音帧（静音尾巴不计入有效内容）
            val effectiveFrames = segmentFrames - consecutiveSilence
            val durationMs = effectiveFrames * frameMs
            resetSegment()
            return if (durationMs >= minSegmentMs) {
                VadEvent.SegmentEnd(durationMs.toLong())
            } else {
                // 太短：当噪声丢弃
                VadEvent.SegmentDiscarded
            }
        }

        return VadEvent.FrameAppended
    }

    private fun resetSegment() {
        state = State.SILENCE
        consecutiveSpeech = 0
        consecutiveSilence = 0
        segmentFrames = 0
    }

    /** 外部强制结束（如暂停/停止）：若正在录入，返回需落盘的段时长，否则 null。 */
    fun forceEnd(): VadEvent {
        if (state != State.RECORDING) return VadEvent.Idle
        val effectiveFrames = segmentFrames - consecutiveSilence
        val durationMs = (effectiveFrames.coerceAtLeast(0) * frameMs).toLong()
        resetSegment()
        return if (durationMs >= minSegmentMs) VadEvent.SegmentEnd(durationMs) else VadEvent.SegmentDiscarded
    }

    /** 完整重置（新会话）。 */
    fun reset() = resetSegment()
}
