package tech.echo.app.ui.settings

import android.content.ClipData
import android.content.ComponentName
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

/**
 * Diagnostics for Android/Samsung system speech recognition.
 *
 * Echo keeps Vosk as the production ASR for now. This diagnostic checks both the
 * device default recognizer and every exposed RecognitionService explicitly, so
 * Samsung/Bixby can be distinguished from Google instead of being hidden behind
 * SpeechRecognizer.createSpeechRecognizer(context).
 */
class SystemSpeechDiagnostic @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audioProvider: AssetAsrTestAudioProvider,
) {

    suspend fun run(): ConnectionTestResult = withContext(Dispatchers.IO) {
        val generalAvailable = SpeechRecognizer.isRecognitionAvailable(context)
        val onDeviceAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        val services = queryRecognitionServiceTargets()

        val lines = mutableListOf<String>()
        lines += "系统语音识别：${if (generalAvailable) "可用" else "不可用"}"
        lines += "装置端语音识别：${if (onDeviceAvailable) "可用" else "不可用"}"
        lines += if (services.isEmpty()) {
            "RecognitionService：未枚举到服务"
        } else {
            "RecognitionService：${services.joinToString("；") { it.displayName }}"
        }

        if (!generalAvailable) {
            return@withContext ConnectionTestResult(false, lines.joinToString("\n"))
        }

        val sampleDir = File(context.cacheDir, "connection-test")
        val sample = audioProvider.createSampleFile(sampleDir)
        try {
            lines += testOne(
                label = "默认系统",
                audioFile = sample,
                component = null,
                onDevice = false,
            )

            services
                .filterNot { it.component.packageName == "com.google.android.tts" }
                .forEach { target ->
                    lines += testOne(
                        label = target.shortLabel,
                        audioFile = sample,
                        component = target.component,
                        onDevice = false,
                    )
                }

            if (onDeviceAvailable) {
                lines += testOne(
                    label = "Android 装置端",
                    audioFile = sample,
                    component = null,
                    onDevice = true,
                )
            }
        } finally {
            sample.delete()
        }

        val injectionWorked = lines.any { it.contains("WAV 注入成功") }
        ConnectionTestResult(injectionWorked, lines.joinToString("\n"))
    }

    private suspend fun testOne(
        label: String,
        audioFile: File,
        component: ComponentName?,
        onDevice: Boolean,
    ): String = runCatching {
        recognizeInjected(audioFile, component, onDevice)
    }.fold(
        onSuccess = { "$label WAV 注入成功 → ${it.take(100)}" },
        onFailure = { "$label WAV 注入失败 → ${it.readable()}" },
    )

    private suspend fun recognizeInjected(
        audioFile: File,
        component: ComponentName?,
        onDevice: Boolean,
    ): String = withTimeout(TEST_TIMEOUT_MS) {
        withContext(Dispatchers.Main.immediate) {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.speechdiag.files",
                audioFile,
            )
            val packages = buildSet {
                component?.packageName?.let(::add)
                queryRecognitionServiceTargets().forEach { add(it.component.packageName) }
            }
            packages.forEach { packageName ->
                runCatching {
                    context.grantUriPermission(
                        packageName,
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
            }

            try {
                recognizeOnMainThread(uri, component, onDevice)
            } finally {
                packages.forEach { packageName ->
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

    private suspend fun recognizeOnMainThread(
        uri: Uri,
        component: ComponentName?,
        onDevice: Boolean,
    ): String = suspendCancellableCoroutine { continuation ->
        val recognizer = try {
            when {
                onDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                component != null ->
                    SpeechRecognizer.createSpeechRecognizer(context, component)
                else ->
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

    private fun queryRecognitionServiceTargets(): List<ServiceTarget> =
        queryRecognitionServiceInfos().map { info ->
            val service = info.serviceInfo
            val component = ComponentName(service.packageName, service.name)
            ServiceTarget(
                component = component,
                displayName = "${service.packageName}/${service.name.substringAfterLast('.')}",
                shortLabel = when {
                    service.packageName.contains("samsung", ignoreCase = true) -> "Samsung/Bixby"
                    service.packageName.contains("googlequicksearchbox", ignoreCase = true) -> "Google"
                    else -> service.packageName
                },
            )
        }.distinctBy { it.component.flattenToString() }

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

    private data class ServiceTarget(
        val component: ComponentName,
        val displayName: String,
        val shortLabel: String,
    )

    private companion object {
        const val TEST_TIMEOUT_MS = 10_000L
    }
}
