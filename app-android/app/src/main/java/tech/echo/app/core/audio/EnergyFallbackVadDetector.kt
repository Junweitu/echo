package tech.echo.app.core.audio

import kotlin.math.sqrt

/**
 * Silero 优先的轻量兜底 VAD。
 *
 * 真机实测当前 ONNX 输出会在明显人声下保持极低概率，导致状态机永远不启动。
 * 这里仅在模型低分但帧能量明显达到人声级别时兜底，避免录音 MVP 完全漏段。
 */
class EnergyFallbackVadDetector(
    private val delegate: VadDetector,
    private val rmsThreshold: Float = AudioConfig.ENERGY_VAD_RMS_THRESHOLD,
    private val fallbackSpeechProbability: Float = AudioConfig.VAD_THRESHOLD,
) : VadDetector {

    override fun probability(frame: ShortArray): Float {
        val modelProbability = delegate.probability(frame)
        if (modelProbability >= AudioConfig.VAD_THRESHOLD) return modelProbability

        return if (normalizedRms(frame) >= rmsThreshold) {
            fallbackSpeechProbability
        } else {
            modelProbability
        }
    }

    override fun reset() = delegate.reset()

    override fun close() = delegate.close()

    companion object {
        fun normalizedRms(frame: ShortArray): Float {
            if (frame.isEmpty()) return 0f
            var sumSquares = 0.0
            frame.forEach { sample ->
                val normalized = sample / 32768.0
                sumSquares += normalized * normalized
            }
            return sqrt(sumSquares / frame.size).toFloat()
        }
    }
}
