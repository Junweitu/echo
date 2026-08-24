package tech.echo.app.core.upload

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import tech.echo.app.core.audio.AudioConfig
import tech.echo.app.core.audio.WavWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fully local/offline Chinese ASR powered by Vosk.
 *
 * The small Chinese model is bundled into the APK by GitHub Actions under assets/vosk-model-small-cn-0.22.
 * On first use it is copied to app-private storage because Vosk requires a filesystem model path.
 */
@Singleton
class LocalVoskAsrClient @Inject constructor(
    @ApplicationContext private val context: Context,
) : AsrClient {

    private val modelMutex = Mutex()
    @Volatile private var cachedModel: Model? = null

    override suspend fun transcribe(audioFile: File): List<AsrUtterance> = withContext(Dispatchers.IO) {
        require(audioFile.exists()) { "录音文件不存在：${audioFile.absolutePath}" }
        val model = ensureModel()
        val pieces = mutableListOf<String>()

        Recognizer(model, AudioConfig.SAMPLE_RATE.toFloat()).use { recognizer ->
            FileInputStream(audioFile).use { input ->
                // Echo writes canonical PCM WAV: 44-byte header, 16-bit mono, 16 kHz.
                var remainingHeader = WavWriter.WAV_HEADER_SIZE
                while (remainingHeader > 0) {
                    val skipped = input.skip(remainingHeader.toLong())
                    if (skipped <= 0) {
                        if (input.read() == -1) error("WAV 文件过短")
                        remainingHeader--
                    } else {
                        remainingHeader -= skipped.toInt()
                    }
                }

                val buffer = ByteArray(8 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    if (recognizer.acceptWaveForm(buffer, read)) {
                        extractText(recognizer.result)?.let(pieces::add)
                    }
                }
                extractText(recognizer.finalResult)?.let(pieces::add)
            }
        }

        val text = normalizeChineseSpacing(pieces.joinToString(" ").trim())
        if (text.isBlank()) emptyList()
        else listOf(
            AsrUtterance(
                speakerLabel = null,
                text = text,
                startMs = 0L,
                endMs = wavDurationMs(audioFile),
            )
        )
    }

    private suspend fun ensureModel(): Model {
        cachedModel?.let { return it }
        return modelMutex.withLock {
            cachedModel?.let { return@withLock it }
            withContext(Dispatchers.IO) {
                val target = File(context.filesDir, MODEL_DIR_NAME)
                val required = File(target, "am/final.mdl")
                if (!required.exists()) {
                    target.deleteRecursively()
                    copyAssetTree(MODEL_ASSET_PATH, target)
                }
                check(required.exists()) {
                    "本机语音模型未正确安装，请重新安装包含中文模型的 Echo APK"
                }
                Model(target.absolutePath).also { cachedModel = it }
            }
        }
    }

    private fun copyAssetTree(assetPath: String, target: File) {
        val children = context.assets.list(assetPath).orEmpty()
        if (children.isNotEmpty()) {
            target.mkdirs()
            children.forEach { child ->
                copyAssetTree("$assetPath/$child", File(target, child))
            }
            return
        }

        target.parentFile?.mkdirs()
        context.assets.open(assetPath).use { input ->
            FileOutputStream(target).use { output -> input.copyTo(output) }
        }
    }

    private fun extractText(json: String): String? =
        runCatching { JSONObject(json).optString("text").trim() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }

    /** Vosk Chinese often inserts spaces between Han characters; remove only Han-to-Han spaces. */
    private fun normalizeChineseSpacing(raw: String): String {
        if (raw.isBlank()) return raw
        val out = StringBuilder(raw.length)
        for (i in raw.indices) {
            val c = raw[i]
            if (c == ' ' && i > 0 && i + 1 < raw.length && isHan(raw[i - 1]) && isHan(raw[i + 1])) {
                continue
            }
            out.append(c)
        }
        return out.toString().replace(Regex("\\s+"), " ").trim()
    }

    private fun isHan(c: Char): Boolean =
        Character.UnicodeScript.of(c.code) == Character.UnicodeScript.HAN

    private fun wavDurationMs(file: File): Long {
        val pcmBytes = (file.length() - WavWriter.WAV_HEADER_SIZE).coerceAtLeast(0L)
        val bytesPerSecond = AudioConfig.SAMPLE_RATE * 2L // mono 16-bit
        return pcmBytes * 1000L / bytesPerSecond
    }

    companion object {
        const val MODEL_DIR_NAME = "vosk-model-small-cn-0.22"
        const val MODEL_ASSET_PATH = MODEL_DIR_NAME
    }
}
