package tech.echo.app.core.audio

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * Silero VAD（ONNX）实现（见 design.md §4.1）。
 *
 * 模型：assets/silero_vad.onnx（v5），单帧 512 采样点@16kHz。
 * 输入：
 *   - input: float[1][512] PCM（归一化到 [-1,1]）
 *   - state: float[2][1][128] RNN 隐状态（跨帧传递）
 *   - sr:    int64 标量 = 16000
 * 输出：
 *   - output: float[1][1] 有声概率
 *   - stateN: float[2][1][128] 新隐状态
 *
 * 有状态：必须把上一帧输出的 state 喂给下一帧；reset 时清零。
 * 非线程安全：由单一音频线程串行调用。
 */
class SileroVadDetector(context: Context) : VadDetector {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession

    /** RNN 隐状态 [2][1][128]，跨帧传递。 */
    private val stateShape = longArrayOf(2, 1, 128)
    private var state = FloatArray(2 * 1 * 128)

    init {
        val modelBytes = context.assets.open(MODEL_ASSET).use { it.readBytes() }
        session = env.createSession(modelBytes, OrtSession.SessionOptions())
    }

    override fun probability(frame: ShortArray): Float {
        require(frame.size == AudioConfig.FRAME_SAMPLES) {
            "帧长必须为 ${AudioConfig.FRAME_SAMPLES}，实际 ${frame.size}"
        }
        // PCM short → float 归一化
        val input = FloatArray(frame.size) { frame[it] / 32768f }

        val inputTensor = OnnxTensor.createTensor(
            env, FloatBuffer.wrap(input), longArrayOf(1, frame.size.toLong())
        )
        val stateTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(state), stateShape)
        val srTensor = OnnxTensor.createTensor(
            env, LongBuffer.wrap(longArrayOf(AudioConfig.SAMPLE_RATE.toLong())), sampleRateTensorShape()
        )

        return inputTensor.use { inp ->
            stateTensor.use { st ->
                srTensor.use { sr ->
                    val inputs = mapOf("input" to inp, "state" to st, "sr" to sr)
                    session.run(inputs).use { result ->
                        // 取新 state 回写
                        @Suppress("UNCHECKED_CAST")
                        val newState = (result.get(1).value as Array<Array<FloatArray>>)
                        writeBackState(newState)
                        // 取概率
                        val prob = (result.get(0).value as Array<FloatArray>)[0][0]
                        prob
                    }
                }
            }
        }
    }

    private fun writeBackState(newState: Array<Array<FloatArray>>) {
        var i = 0
        for (a in newState) for (b in a) for (v in b) state[i++] = v
    }

    override fun reset() {
        state = FloatArray(2 * 1 * 128)
    }

    override fun close() {
        session.close()
    }

    companion object {
        const val MODEL_ASSET = "silero_vad.onnx"

        fun sampleRateTensorShape(): LongArray = longArrayOf()
    }
}
