package tech.echo.app.ui.settings

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tech.echo.app.core.summary.LlmClient
import tech.echo.app.core.upload.LocalVoskAsrClient
import java.io.File
import javax.inject.Inject

data class ConnectionTestResult(
    val success: Boolean,
    val message: String,
)

class SettingsConnectionTester internal constructor(
    private val sampleDir: File,
    private val localVoskAsrClient: LocalVoskAsrClient,
    private val llmClient: LlmClient,
    private val audioProvider: AsrTestAudioProvider,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        localVoskAsrClient: LocalVoskAsrClient,
        llmClient: LlmClient,
        audioProvider: AssetAsrTestAudioProvider,
    ) : this(File(context.cacheDir, "connection-test"), localVoskAsrClient, llmClient, audioProvider)

    suspend fun testAsr(): ConnectionTestResult = withContext(Dispatchers.IO) {
        runCatching {
            val file = audioProvider.createSampleFile(sampleDir)
            try {
                localVoskAsrClient.transcribe(file)
            } finally {
                file.delete()
            }
        }.fold(
            onSuccess = { utterances ->
                if (utterances.isNotEmpty()) {
                    ConnectionTestResult(true, "Vosk 本机中文识别正常：${utterances.first().text.take(24)}")
                } else {
                    ConnectionTestResult(true, "Vosk 中文模型已加载（测试音频未识别到文字）")
                }
            },
            onFailure = { ConnectionTestResult(false, it.readableMessage("Vosk 本机语音识别失败")) },
        )
    }

    suspend fun testLlm(): ConnectionTestResult =
        runCatching {
            llmClient.summarize("""请只返回 JSON：{"ok":true}""")
        }.fold(
            onSuccess = { ConnectionTestResult(true, "DeepSeek 连接正常") },
            onFailure = { ConnectionTestResult(false, it.readableMessage("DeepSeek 连接失败")) },
        )

    private fun Throwable.readableMessage(prefix: String): String =
        "$prefix：${message ?: javaClass.simpleName}"
}
