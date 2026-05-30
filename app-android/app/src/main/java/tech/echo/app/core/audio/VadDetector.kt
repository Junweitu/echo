package tech.echo.app.core.audio

/**
 * VAD 检测器抽象：输入一帧 PCM，输出有声概率 [0,1]。
 *
 * 抽象出接口的目的：
 * - 状态机 [VadStateMachine] 只依赖"概率"，不绑死 Silero，可用假概率序列单测。
 * - 未来可换 WebRTC VAD 等实现（design.md §4.1 备选）。
 */
interface VadDetector {
    /**
     * 对一帧 PCM（[AudioConfig.FRAME_SAMPLES] 个采样点）给出有声概率。
     * @return 0f..1f，越大越可能是人声。
     */
    fun probability(frame: ShortArray): Float

    /** 重置内部状态（Silero 是有状态 RNN，新会话或长静音后需重置）。 */
    fun reset()

    /** 释放底层资源（ONNX session 等）。 */
    fun close()
}
