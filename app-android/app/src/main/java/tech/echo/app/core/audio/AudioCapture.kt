package tech.echo.app.core.audio

import android.Manifest
import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 麦克风采集（见 codex-core-audio.md §2.2）。
 *
 * 用 AudioRecord 拿 PCM 帧（16kHz/mono/16bit），以 [AudioConfig.FRAME_SAMPLES]
 * 采样点为一帧，通过 [Flow] 往下游（VAD）推。
 *
 * 错误处理：
 * - AudioRecord 初始化失败 → 抛 [AudioCaptureException]，由 Service 捕获后停服并提示。
 * - 读取返回错误码 → 结束流，不崩溃。
 */
class AudioCapture {

    private val running = AtomicBoolean(false)

    /**
     * 开始采集，返回 PCM 帧流。collect 取消时自动停止并释放 AudioRecord。
     * 需 RECORD_AUDIO 权限（由 Service 在启动前确保已授予）。
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun frames(): Flow<ShortArray> = callbackFlow {
        val minBuf = AudioRecord.getMinBufferSize(
            AudioConfig.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuf == AudioRecord.ERROR || minBuf == AudioRecord.ERROR_BAD_VALUE) {
            Log.w(TAG, "invalid min buffer size=$minBuf")
            close(AudioCaptureException("无法获取最小缓冲区大小：$minBuf"))
            return@callbackFlow
        }
        // 缓冲取「最小值」与「若干帧」的较大者，降低 overrun 丢帧风险
        val bufferSize = maxOf(minBuf, AudioConfig.FRAME_SAMPLES * 2 * 8)

        @SuppressLint("MissingPermission")
        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            AudioConfig.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize,
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            Log.w(TAG, "AudioRecord init failed")
            close(AudioCaptureException("AudioRecord 初始化失败"))
            return@callbackFlow
        }

        running.set(true)
        record.startRecording()
        Log.i(TAG, "AudioRecord started minBuf=$minBuf bufferSize=$bufferSize state=${record.recordingState}")

        // 在 IO 线程循环读满整帧再推
        val readThread = Thread {
            val frame = ShortArray(AudioConfig.FRAME_SAMPLES)
            var frames = 0
            try {
                while (running.get() && isActive) {
                    var offset = 0
                    // 读满一整帧（AudioRecord 可能一次读不满）
                    while (offset < frame.size && running.get()) {
                        val read = record.read(frame, offset, frame.size - offset)
                        if (read < 0) {
                            Log.w(TAG, "AudioRecord read error=$read frames=$frames")
                            close(AudioCaptureException("AudioRecord.read 错误码：$read"))
                            return@Thread
                        }
                        offset += read
                    }
                    if (offset == frame.size) {
                        frames++
                        trySend(frame.copyOf())
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "AudioRecord read loop failed frames=$frames", t)
                close(t)
            } finally {
                Log.i(TAG, "AudioRecord read loop ended frames=$frames")
            }
        }.apply { name = "echo-audio-read"; start() }

        awaitClose {
            running.set(false)
            runCatching { readThread.join(500) }
            runCatching {
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) record.stop()
            }
            record.release()
            Log.i(TAG, "AudioRecord released")
        }
    }.flowOn(Dispatchers.IO)

    companion object {
        private const val TAG = "EchoAudioCapture"
    }
}

/** 采集异常：麦克风初始化/读取失败。 */
class AudioCaptureException(message: String) : Exception(message)
