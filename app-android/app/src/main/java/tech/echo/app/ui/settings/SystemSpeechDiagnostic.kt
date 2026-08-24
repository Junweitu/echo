package tech.echo.app.ui.settings

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import javax.inject.Inject
import kotlin.coroutines.resume

/**
 * Diagnostics for Android/Samsung system speech recognition.
 *
 * This does NOT replace Echo's current Vosk ASR. It only checks what recognition
 * services the device exposes and whether Android 12's EXTRA_AUDIO_INJECT_SOURCE
 * can feed Echo's WAV segments into the system/on-device recognizer.
 */
class SystemSpeechDiagnostic @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioProvider: AssetAsrTestAudioProvider,
) {

    suspend fun run(): ConnectionTestResult = withContext(Dispatchers.IO) {
        val generalAvailable = SpeechRecognizer.isRecognitionAvailable(context)
        val onDeviceAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        val services = queryRecognitionServices()

        val lines = mutableListOf<String>()
        lines += "系统语音识别：${if (generalAvailable) "可用" else "不可用"}"
        lines += "装置端语音识别：${if (onDeviceAvailable) "可用" else "不可用"}"
        lines += if (services.isEmpty()) {
            "RecognitionService：未枚举到服务"
        } else {
            "RecognitionService：${services.joinToString("；")}"
        }

        if (!generalAvailable) {
            return@withContext ConnectionTestResult(false, lines.joinToString("\n"))
        }

        val sampleDir = File(context.cacheDir, "connection-test")
        val sample = audioProvider.createSampleFile(sampleDir)
        try {
            val systemResult = runCatching {
                recognizeInjected(sample, onDevice = false)
            }.fold(
                onSuccess = { "系统 WAV 注入：成功 → ${it.take(80)}" },
                onFailure = { "系统 WAV 注入：失败 → ${it.readable()}" },
            )
            lines += systemResult

            if (onDeviceAvailable) {
                val localResult = runCatching {
                    recognizeInjected(sample, onDevice = true)
                }.fold(
                    onSuccess = { "装置端 WAV 注入：成功 → ${it.take(80)}" },
                    onFailure = { "装置端 WAV 注入：失败 → ${it.readable()}" },
                )
                lines += localResult
            }
        } finally {
            sample.delete()
        }

        val injectionWorked = lines.any { it.contains("WAV 注入：成功") }
        ConnectionTestResult(injectionWorked, lines.joinToString("\n"))
    }

    private suspend fun recognizeInjected(audioFile: File, onDevice: Boolean): String =
        withTimeout(TEST_TIMEOUT_MS) {
            withContext(Dispatchers.Main.immediate) {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.speechdiag.files",
                    audioFile,
                )
                val servicePackages = queryRecognitionServicePackages()
                servicePackages.forEach { packageName ->
                    runCatching {
                        context.grantUriPermission(
                            packageName,
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    }
                }

                try {
                    recognizeOnMainThread(uri, onDevice)
                } finally {
                    servicePackages.forEach { packageName ->
                        runCatching {
                            context.revokeUriPermission(
                                packageName,
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION,
                            )
                        }
                    }
                }
            }
        }

    private suspend fun recognizeOnMainThread(uri: Uri, onDevice: Boolean): String =
        suspendCancellableCoroutine { continuation ->
            val recognizer = try {
                if (onDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                } else {
                    SpeechRecognizer.createSpeechRecognizer(context)
                }
            } catch (t: Throwable) {
                continuation.resumeWith(Result.failure(t))
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
                    finish(Result.failure(IllegalStateException("SpeechRecognizer error=$error (${errorName(error)})")))
                }

                override fun onResults(results: Bundle?) {
                    val texts = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        .orEmpty()
                        .filter { it.isNotBlank() }
                    if (texts.isEmpty()) {
                        finish(Result.failure(IllegalStateException("没有返回识别文字")))
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
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, onDevice)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    putExtra(RecognizerIntent.EXTRA_AUDIO_INJECT_SOURCE, uri)
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newRawUri("echo-speech-diagnostic", uri)
            }

            runCatching { recognizer.startListening(intent) }
                .onFailure { finish(Result.failure(it)) }
        }

    private fun queryRecognitionServices(): List<String> =
        queryRecognitionServiceInfos().map { info ->
            val service = info.serviceInfo
            "${service.packageName}/${service.name.substringAfterLast('.')}"
        }.distinct()

    private fun queryRecognitionServicePackages(): List<String> =
        queryRecognitionServiceInfos().map { it.serviceInfo.packageName }.distinct()

    @Suppress("DEPRECATION")
    private fun queryRecognitionServiceInfos() =
        if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.queryIntentServices(
                Intent(RecognitionService.SERVICE_INTERFACE),
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()),
            )
        } else {
            context.packageManager.queryIntentServices(
                Intent(RecognitionService.SERVICE_INTERFACE),
                PackageManager.MATCH_ALL,
            )
        }

    private fun Throwable.readable(): String = message ?: javaClass.simpleName

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

    private companion object {
        const val TEST_TIMEOUT_MS = 20_000L
    }
}
