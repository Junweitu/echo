package tech.echo.app.core.audio

/**
 * 录音引擎集中配置（见 codex-core-audio.md §2.3/§2.4/§2.5）。
 *
 * 所有"魔法数字"集中在此，便于跑两天后调参，也便于单测注入不同值。
 */
object AudioConfig {
    /** 采样率：16kHz（ASR 够用又省空间）。 */
    const val SAMPLE_RATE = 16_000

    /** 单帧采样点数：Silero VAD 要求 512（32ms@16kHz）。 */
    const val FRAME_SAMPLES = 512

    /** 单帧毫秒数（512 / 16000 = 32ms）。 */
    const val FRAME_MS = FRAME_SAMPLES * 1000 / SAMPLE_RATE

    // —— VAD 状态机 ——
    /** 有声概率阈值：高于视为有声帧。 */
    const val VAD_THRESHOLD = 0.5f

    /**
     * Silero 漏判时的能量兜底阈值。
     *
     * 0.008 约等于 -42dBFS，实测真机说话峰值常在 -20~-40dBFS，静音底噪通常低于 -55dBFS。
     */
    const val ENERGY_VAD_RMS_THRESHOLD = 0.008f

    /** 连续多少有声帧确认进入"录入中"（去抖，避免瞬时噪声触发）。 */
    const val SPEECH_START_FRAMES = 3

    /** 连续多少静音帧判定一段结束（约 0.8 秒 ÷ 32ms ≈ 25 帧）。 */
    const val SILENCE_END_FRAMES = 25

    // —— 环形预录缓冲 ——
    /** 预录时长毫秒：触发录入时回补这么久的历史帧，防丢句子开头。 */
    const val PREROLL_MS = 1_500

    /** 预录缓冲容纳的帧数（1500 / 32 ≈ 47 帧）。 */
    const val PREROLL_FRAMES = PREROLL_MS / FRAME_MS

    // —— 片段边界 ——
    /** 最短段毫秒：短于此丢弃（滤掉咳嗽/关门等孤立噪声）。 */
    const val MIN_SEGMENT_MS = 1_000

    /** 最长段毫秒：超过强制切段（便于后续并发转写）。 */
    const val MAX_SEGMENT_MS = 60_000
}
