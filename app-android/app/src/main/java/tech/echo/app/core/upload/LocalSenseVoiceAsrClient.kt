package tech.echo.app.core.upload

import android.content.Context
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import tech.echo.app.core.audio.AudioConfig
import tech.echo.app.core.audio.WavWriter
import java.io.File
import java.io.FileInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 完全離線的 SenseVoice 中文語音辨識。
 *
 * 模型與 sherpa-onnx Android AAR 由 GitHub Actions 在編譯時放入 APK；
 * 執行時不需要任何 ASR API Key，也不會把錄音上傳到語音服務。
 */
@Singleton
class LocalSenseVoiceAsrClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val diagnosticsStore: AsrDiagnosticsStore,
) : AsrClient {

    private val recognizerMutex = Mutex()
    @Volatile private var recognizer: OfflineRecognizer? = null

    override suspend fun transcribe(audioFile: File): List<AsrUtterance> = withContext(Dispatchers.Default) {
        require(audioFile.exists()) { "錄音檔案不存在：${audioFile.absolutePath}" }
        val startedAt = System.currentTimeMillis()

        try {
            val samples = readCanonicalEchoWav(audioFile)
            val text = recognizerMutex.withLock {
                val r = ensureRecognizer()
                val stream = r.createStream()
                try {
                    stream.acceptWaveform(samples, AudioConfig.SAMPLE_RATE)
                    r.decode(stream)
                    r.getResult(stream).text.trim()
                } finally {
                    stream.release()
                }
            }

            val elapsed = System.currentTimeMillis() - startedAt
            diagnosticsStore.record(
                audioFile.absolutePath,
                AsrDiagnostic(
                    engine = "SenseVoice（本機）",
                    elapsedMs = elapsed,
                ),
            )

            if (text.isBlank()) emptyList()
            else listOf(
                AsrUtterance(
                    speakerLabel = null,
                    text = text,
                    startMs = 0L,
                    endMs = wavDurationMs(audioFile),
                )
            )
        } catch (t: Throwable) {
            diagnosticsStore.record(
                audioFile.absolutePath,
                AsrDiagnostic(
                    engine = "SenseVoice（本機）",
                    elapsedMs = System.currentTimeMillis() - startedAt,
                    fallbackReason = t.message ?: t.javaClass.simpleName,
                ),
            )
            throw t
        }
    }

    private fun ensureRecognizer(): OfflineRecognizer {
        recognizer?.let { return it }

        val senseVoice = OfflineSenseVoiceModelConfig(
            model = "$MODEL_ASSET_DIR/model.int8.onnx",
            language = "zh",
            useInverseTextNormalization = true,
        )
        val modelConfig = OfflineModelConfig(
            senseVoice = senseVoice,
            tokens = "$MODEL_ASSET_DIR/tokens.txt",
            numThreads = 2,
            debug = false,
            provider = "cpu",
        )
        val config = OfflineRecognizerConfig(
            modelConfig = modelConfig,
            decodingMethod = "greedy_search",
        )

        return OfflineRecognizer(
            assetManager = context.assets,
            config = config,
        ).also { recognizer = it }
    }

    /**
     * Echo 自己產生的 WAV 固定為 44-byte header / PCM16 / mono / 16 kHz，
     * 因此可以避免額外解碼器，直接轉成 sherpa-onnx 所需 FloatArray。
     */
    private fun readCanonicalEchoWav(file: File): FloatArray {
        val pcmBytes = (file.length() - WavWriter.WAV_HEADER_SIZE).coerceAtLeast(0L)
        require(pcmBytes >= 2) { "WAV 檔案沒有可辨識的 PCM 音訊" }
        require(pcmBytes % 2L == 0L) { "WAV PCM 長度不是 16-bit 對齊" }
        require(pcmBytes <= Int.MAX_VALUE) { "WAV 檔案過大" }

        val bytes = ByteArray(pcmBytes.toInt())
        FileInputStream(file).use { input ->
            var remain = WavWriter.WAV_HEADER_SIZE
            while (remain > 0) {
                val skipped = input.skip(remain.toLong())
                if (skipped > 0) {
                    remain -= skipped.toInt()
                } else {
                    if (input.read() < 0) error("WAV header 不完整")
                    remain--
                }
            }

            var offset = 0
            while (offset < bytes.size) {
                val n = input.read(bytes, offset, bytes.size - offset)
                if (n < 0) break
                offset += n
            }
            require(offset == bytes.size) { "WAV PCM 資料讀取不完整" }
        }

        return FloatArray(bytes.size / 2) { i ->
            val lo = bytes[i * 2].toInt() and 0xff
            val hi = bytes[i * 2 + 1].toInt()
            val sample = ((hi shl 8) or lo).toShort()
            sample / 32768.0f
        }
    }

    private fun wavDurationMs(file: File): Long {
        val pcmBytes = (file.length() - WavWriter.WAV_HEADER_SIZE).coerceAtLeast(0L)
        val bytesPerSecond = AudioConfig.SAMPLE_RATE * 2L
        return pcmBytes * 1000L / bytesPerSecond
    }

    companion object {
        const val MODEL_ASSET_DIR = "sensevoice"
    }
}
