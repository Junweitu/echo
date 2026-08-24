package tech.echo.app.core.upload

import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
 * Production ASR for the user's Samsung device.
 *
 * Priority:
 * 1) Explicit Samsung/Bixby RecognitionService using Android 12 WAV injection.
 * 2) Fully offline Vosk Chinese fallback if Samsung is unavailable or fails.
 *
 * The Samsung path does not require an API key. It may still use Samsung/network
 * services because this device reports Android on-device recognition unavailable.
 */
@Singleton
class SamsungPreferredAsrClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val localFallback: LocalVoskAsrClient,
) : AsrClient {

    private val mutex = Mutex()

    override suspend fun transcribe(audioFile: File): List<AsrUtterance> {
        require(audioFile.exists()) { "录音文件不存在：${audioFile.absolutePath}" }

        if (!canUseSamsungInjection()) {
            return localFallback.transcribe(audioFile)
        }

        return mutex.withLock {
            runCatching { transcribeWithSamsung(audioFile) }
                .onFailure {
                    Log.w(TAG, "Samsung ASR failed; falling back to Vosk: ${it.message}", it)
                }
                .getOrElse { localFallback.transcribe(audioFile) }
        }
    }

    private fun canUseSamsungInjection(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        return runCatching {
            val intent = Intent("android.speech.RecognitionService").setComponent(SAMSUNG_COMPONENT)
            context.packageManager.resolveService(intent, 0) != null
        }.getOrDefault(false)
    }

    private suspend fun transcribeWithSamsung(audioFile: File): List<AsrUtterance> {
        val text = withTimeout(timeoutFor(audioFile)) {
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

        if (text.isBlank()) throw IllegalStateException("Samsung ASR 未返回文字")
        return listOf(
            AsrUtterance(
                speakerLabel = null,
                text = text,
                startMs = 0L,
                endMs = wavDurationMs(audioFile),
            )
        )
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
                    finish(Result.failure(IllegalStateException("Samsung SpeechRecognizer error=$error (${errorName(error)})")))
                }

                override fun onResults(results: Bundle?) {
                    val texts = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        .orEmpty()
                        .filter { it.isNotBlank() }
                    if (texts.isEmpty()) {
                        finish(Result.failure(IllegalStateException("Samsung ASR 没有返回识别文字")))
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    putExtra(RecognizerIntent.EXTRA_AUDIO_INJECT_SOURCE, uri)
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newRawUri("echo-segment", uri)
            }

            runCatching { recognizer.startListening(intent) }
                .onFailure { finish(Result.failure(it)) }
        }

    private fun timeoutFor(file: File): Long =
        (wavDurationMs(file) + EXTRA_TIMEOUT_MS).coerceIn(MIN_TIMEOUT_MS, MAX_TIMEOUT_MS)

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

    companion object {
        private const val TAG = "EchoSamsungAsr"
        private const val SAMSUNG_PACKAGE = "com.samsung.android.bixby.agent"
        private val SAMSUNG_COMPONENT = ComponentName(
            SAMSUNG_PACKAGE,
            "com.samsung.android.bixby.agent.RecognitionServiceTrampoline",
        )
        private const val EXTRA_TIMEOUT_MS = 20_000L
        private const val MIN_TIMEOUT_MS = 25_000L
        private const val MAX_TIMEOUT_MS = 90_000L
    }
}
