package tech.echo.app.core.audio

import android.content.Context
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig

/**
 * Silero VAD，改由 sherpa-onnx 的原生 Android 實作負責狀態與推理。
 *
 * 先前自行用 ONNX Runtime 維護 Silero RNN state，在 Note10+ 真機上會出現
 * 明顯人聲概率異常，之後又必須靠能量兜底，造成環境底噪被誤認成人聲、
 * 一小句話被錄成接近 60 秒。這裡改用 sherpa-onnx 官方 VAD 實作。
 */
class SileroVadDetector(context: Context) : VadDetector {

    private val vad = Vad(
        assetManager = context.assets,
        config = VadModelConfig(
            sileroVadModelConfig = SileroVadModelConfig(
                model = MODEL_ASSET,
                threshold = AudioConfig.VAD_THRESHOLD,
                minSilenceDuration = 0.25f,
                minSpeechDuration = 0.20f,
                windowSize = AudioConfig.FRAME_SAMPLES,
                maxSpeechDuration = 60.0f,
            ),
            sampleRate = AudioConfig.SAMPLE_RATE,
            numThreads = 1,
            provider = "cpu",
            debug = false,
        ),
    )

    override fun probability(frame: ShortArray): Float {
        require(frame.size == AudioConfig.FRAME_SAMPLES) {
            "幀長必須為 ${AudioConfig.FRAME_SAMPLES}，實際 ${frame.size}"
        }
        val samples = FloatArray(frame.size) { frame[it] / 32768.0f }
        return vad.compute(samples)
    }

    override fun reset() {
        vad.reset()
    }

    override fun close() {
        vad.release()
    }

    companion object {
        const val MODEL_ASSET = "silero_vad.onnx"
    }
}
