package tech.echo.app.core.audio

import kotlin.math.sqrt

/**
 * 相容舊注入點的 VAD 包裝器。
 *
 * 之前只要 RMS 超過很低的固定門檻，就會強行把 Silero 低分改成「有聲」，
 * 在真機環境底噪較高時會讓一小句話一路延長到 60 秒上限。
 * 現在 sherpa-onnx 已接管 Silero VAD，因此不再用能量值覆寫模型判斷。
 */
class EnergyFallbackVadDetector(
    private val delegate: VadDetector,
    @Suppress("UNUSED_PARAMETER") private val rmsThreshold: Float = AudioConfig.ENERGY_VAD_RMS_THRESHOLD,
    @Suppress("UNUSED_PARAMETER") private val fallbackSpeechProbability: Float = AudioConfig.VAD_THRESHOLD,
) : VadDetector {

    override fun probability(frame: ShortArray): Float = delegate.probability(frame)

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
