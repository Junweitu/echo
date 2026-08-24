package tech.echo.app.core.upload

import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import tech.echo.app.core.audio.AudioConfig
import tech.echo.app.core.audio.WavWriter
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resumeWithException

/**
 * 正式 ASR：Samsung/Bixby 優先，必要時以 Vosk 本機辨識備援。
 *
 * Note10+ 實測顯示：短 WAV 可由 Samsung/Bixby RecognitionService 正常辨識，
 * 但約 60 秒 WAV 可能長時間不回傳。因此保留原始錄音檔不變，只在 ASR 階段
 * 暫時切成約 16 秒的小 WAV，依序送 Samsung；某一小段失敗後，該段與後續
 * 小段直接改用 Vosk，避免每一段都等待 Samsung timeout。
 */
@Singleton
class SamsungPreferredAsrClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val localFallback: LocalVoskAsrClient,
    private val diagnosticsStore: AsrDiagnosticsStore,
) : AsrClient {

    private val mutex = Mutex()

    override suspend fun transcribe(audioFile: File): List<AsrUtterance> {
        require(audioFile.exists()) { "錄音檔不存在：${audioFile.absolutePath}" }
        val started = SystemClock.elapsedRealtime()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return transcribeWholeFileWithVosk(
                audioFile = audioFile,
                started = started,
                fallbackReason = "Android 12 以下不支援 WAV 注入",
            )
        }

        return mutex.withLock {
            runCatching { transcribeChunked(audioFile) }
                .fold(
                    onSuccess = { outcome ->
                        diagnosticsStore.record(
                            audioFile.absolutePath,
                            AsrDiagnostic(
                                engine = outcome.engine,
                                elapsedMs = SystemClock.elapsedRealtime() - started,
                                fallbackReason = outcome.fallbackReason,
                            )
                        )
                        outcome.utterances
                    },
                    onFailure = { error ->
                        Log.w(TAG, "Chunked Samsung ASR failed; using Vosk for whole file: ${error.message}", error)
                        transcribeWholeFileWithVosk(
                            audioFile = audioFile,
                            started = started,
                            fallbackReason = error.message ?: error.javaClass.simpleName,
                        )
                    },
                )
        }
    }

    private suspend fun transcribeChunked(audioFile: File): ChunkedOutcome {
        val chunkDir = File(context.cacheDir, CHUNK_DIR)
        val chunks = withContext(Dispatchers.IO) {
            WavChunker.split(
                source = audioFile,
                targetDir = chunkDir,
                chunkMs = SAMSUNG_CHUNK_MS,
                overlapMs = CHUNK_OVERLAP_MS,
            )
        }

        val texts = mutableListOf<String>()
        val fallbackReasons = mutableListOf<String>()
        var samsungAllowed = true
        var samsungCount = 0
        var voskCount = 0

        try {
            chunks.forEachIndexed { index, chunk ->
                val text = if (samsungAllowed) {
                    runCatching { transcribeOneWithSamsung(chunk.file) }
                        .fold(
                            onSuccess = {
                                samsungCount += 1
                                it
                            },
                            onFailure = { samsungError ->
                                // 一旦同一個原始片段中的 Samsung 呼叫失敗，後續 chunk
                                // 不再重複等待 timeout；直接用本機 Vosk 完成。
                                samsungAllowed = false
                                val reason = "第 ${index + 1}/${chunks.size} 段：${samsungError.message ?: samsungError.javaClass.simpleName}"
                                fallbackReasons += reason
                                Log.w(TAG, "Samsung chunk failed; switching remaining chunks to Vosk: $reason", samsungError)
                                voskCount += 1
                                transcribeOneWithVosk(chunk.file)
                            },
                        )
                } else {
                    voskCount += 1
                    transcribeOneWithVosk(chunk.file)
                }

                if (text.isNotBlank()) texts += text.trim()
            }
        } finally {
            withContext(Dispatchers.IO) {
                chunks.filter { it.temporary }.forEach { runCatching { it.file.delete() } }
                runCatching { chunkDir.delete() }
            }
        }

        check(texts.isNotEmpty()) { "Samsung/Vosk 都沒有回傳辨識文字" }
        val merged = mergeChunkTexts(texts)
        check(merged.isNotBlank()) { "合併後的辨識文字為空" }

        val engine = when {
            voskCount == 0 -> ENGINE_SAMSUNG
            samsungCount == 0 -> ENGINE_VOSK
            else -> ENGINE_MIXED
        }

        return ChunkedOutcome(
            utterances = listOf(
                AsrUtterance(
                    speakerLabel = null,
                    text = merged,
                    startMs = 0L,
                    endMs = wavDurationMs(audioFile),
                )
            ),
            engine = engine,
            fallbackReason = fallbackReasons.takeIf { it.isNotEmpty() }?.joinToString("；"),
        )
    }

    private suspend fun transcribeOneWithSamsung(audioFile: File): String {
        val text = withTimeout(timeoutForChunk(audioFile)) {
            withContext(Dispatchers.Main.immediate) {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.speechdiag.files",
                    audioFile,
                )
                context.grantUriPermission(
                    SAMSUNG_PACKAGE,
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
                try {
                    recognizeOnMainThread(uri)
                } finally {
                    runCatching {
                        context.revokeUriPermission(
                            SAMSUNG_PACKAGE,
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    }
                }
            }
        }.trim()

        check(text.isNotBlank()) { "Samsung ASR 未回傳文字" }
        return text
    }

    private suspend fun transcribeOneWithVosk(audioFile: File): String {
        val utterances = localFallback.transcribe(audioFile)
        val text = TranscriptionFormatter.combineText(utterances).trim()
        check(text.isNotBlank()) { "Vosk 未回傳文字" }
        return text
    }

    private suspend fun transcribeWholeFileWithVosk(
        audioFile: File,
        started: Long,
        fallbackReason: String,
    ): List<AsrUtterance> {
        val result = localFallback.transcribe(audioFile)
        diagnosticsStore.record(
            audioFile.absolutePath,
            AsrDiagnostic(
                engine = ENGINE_VOSK,
                elapsedMs = SystemClock.elapsedRealtime() - started,
                fallbackReason = fallbackReason,
            )
        )
        return result
    }

    /**
     * chunk 之間保留 400ms 重疊，避免剛好切在中文字/詞中間。
     * 若兩段 ASR 真的重複回傳相同尾首文字，移除最長的相同前後綴。
     */
    private fun mergeChunkTexts(parts: List<String>): String {
        var merged = ""
        parts.filter { it.isNotBlank() }.forEach { raw ->
            val next = raw.trim()
            if (merged.isBlank()) {
                merged = next
                return@forEach
            }

            val maxOverlap = minOf(MAX_TEXT_OVERLAP_CHARS, merged.length, next.length)
            var overlap = 0
            for (length in maxOverlap downTo MIN_TEXT_OVERLAP_CHARS) {
                if (merged.regionMatches(
                        thisOffset = merged.length - length,
                        other = next,
                        otherOffset = 0,
                        length = length,
                        ignoreCase = false,
                    )
                ) {
                    overlap = length
                    break
                }
            }

            val remainder = next.drop(overlap)
            if (remainder.isEmpty()) return@forEach

            val needsAsciiSpace = merged.lastOrNull()?.isLetterOrDigit() == true &&
                remainder.firstOrNull()?.isLetterOrDigit() == true &&
                merged.last().code < 128 && remainder.first().code < 128
            merged += if (needsAsciiSpace) " $remainder" else remainder
        }
        return merged.trim()
    }

    private suspend fun recognizeOnMainThread(uri: Uri): String =
        suspendCancellableCoroutine { continuation ->
            val recognizer = try {
                SpeechRecognizer.createSpeechRecognizer(context, SAMSUNG_COMPONENT)
            } catch (t: Throwable) {
                continuation.resumeWithException(t)
                return@suspendCancellableCoroutine
            }

            var completed = false
            fun finish(result: Result<String>) {
                if (completed) return
                completed = true
                runCatching { recognizer.destroy() }
                if (continuation.isActive) continuation.resumeWith(result)
            }

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit

                override fun onError(error: Int) {
                    finish(
                        Result.failure(
                            IllegalStateException(
                                "Samsung SpeechRecognizer error=$error (${errorName(error)})"
                            )
                        )
                    )
                }

                override fun onResults(results: Bundle?) {
                    val texts = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        .orEmpty()
                        .filter { it.isNotBlank() }
                    if (texts.isEmpty()) {
                        finish(Result.failure(IllegalStateException("Samsung ASR 沒有回傳辨識文字")))
                    } else {
                        finish(Result.success(texts.first()))
                    }
                }
            })

            continuation.invokeOnCancellation {
                if (!completed) {
                    completed = true
                    runCatching { recognizer.cancel() }
                    runCatching { recognizer.destroy() }
                }
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-TW")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
                putExtra(RecognizerIntent.EXTRA_AUDIO_INJECT_SOURCE, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newRawUri("echo-segment", uri)
            }

            runCatching { recognizer.startListening(intent) }
                .onFailure { finish(Result.failure(it)) }
        }

    private fun timeoutForChunk(file: File): Long =
        (wavDurationMs(file) + CHUNK_EXTRA_TIMEOUT_MS)
            .coerceIn(CHUNK_MIN_TIMEOUT_MS, CHUNK_MAX_TIMEOUT_MS)

    private fun wavDurationMs(file: File): Long {
        val pcmBytes = (file.length() - WavWriter.WAV_HEADER_SIZE).coerceAtLeast(0L)
        val bytesPerSecond = AudioConfig.SAMPLE_RATE * 2L
        return pcmBytes * 1000L / bytesPerSecond
    }

    private fun errorName(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "ERROR_AUDIO"
        SpeechRecognizer.ERROR_CLIENT -> "ERROR_CLIENT"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "ERROR_INSUFFICIENT_PERMISSIONS"
        SpeechRecognizer.ERROR_NETWORK -> "ERROR_NETWORK"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "ERROR_NETWORK_TIMEOUT"
        SpeechRecognizer.ERROR_NO_MATCH -> "ERROR_NO_MATCH"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "ERROR_RECOGNIZER_BUSY"
        SpeechRecognizer.ERROR_SERVER -> "ERROR_SERVER"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "ERROR_SPEECH_TIMEOUT"
        SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "ERROR_TOO_MANY_REQUESTS"
        else -> "UNKNOWN"
    }

    private data class ChunkedOutcome(
        val utterances: List<AsrUtterance>,
        val engine: String,
        val fallbackReason: String?,
    )

    companion object {
        private const val TAG = "EchoSamsungAsr"
        const val ENGINE_SAMSUNG = "Samsung/Bixby"
        const val ENGINE_VOSK = "Vosk（備援）"
        const val ENGINE_MIXED = "Samsung/Bixby + Vosk（局部備援）"

        private const val SAMSUNG_PACKAGE = "com.samsung.android.bixby.agent"
        private val SAMSUNG_COMPONENT = ComponentName(
            SAMSUNG_PACKAGE,
            "com.samsung.android.bixby.agent.RecognitionServiceTrampoline",
        )

        private const val CHUNK_DIR = "samsung-asr-chunks"
        private const val SAMSUNG_CHUNK_MS = 16_000L
        private const val CHUNK_OVERLAP_MS = 400L
        private const val CHUNK_EXTRA_TIMEOUT_MS = 14_000L
        private const val CHUNK_MIN_TIMEOUT_MS = 20_000L
        private const val CHUNK_MAX_TIMEOUT_MS = 32_000L
        private const val MAX_TEXT_OVERLAP_CHARS = 16
        private const val MIN_TEXT_OVERLAP_CHARS = 2
    }
}
