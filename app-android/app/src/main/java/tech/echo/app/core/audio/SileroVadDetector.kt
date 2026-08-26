package tech.echo.app.core.audio

import android.content.Context
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig

/**
 * Silero VAD backed by sherpa-onnx's official streaming Android API.
 *
 * Important: do not call Vad.compute() frame-by-frame here. sherpa-onnx's own
 * Android microphone example feeds 512-sample chunks through acceptWaveform()
 * and reads isSpeechDetected(). That path owns Silero's internal overlap,
 * recurrent state, minimum speech duration, and silence handling correctly.
 *
 * Echo's existing VadStateMachine still supplies preroll, minimum segment
 * length, and final end-of-segment debounce. We expose sherpa's speech state as
 * 1.0/0.0 so the rest of the recording pipeline can stay unchanged.
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

        // This is the usage recommended by sherpa-onnx's Android VAD example.
        // acceptWaveform() handles the model's internal window/overlap state;
        // clear() only clears completed queued speech segments, not model state.
        vad.acceptWaveform(samples)
        val speechDetected = vad.isSpeechDetected()
        vad.clear()

        return if (speechDetected) 1.0f else 0.0f
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
