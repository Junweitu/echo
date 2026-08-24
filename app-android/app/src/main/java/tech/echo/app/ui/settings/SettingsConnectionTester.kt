package tech.echo.app.ui.settings

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tech.echo.app.core.summary.LlmClient
import tech.echo.app.core.upload.AsrClient
import java.io.File
import javax.inject.Inject

data class ConnectionTestResult(
    val success: Boolean,
    val message: String,
)

class SettingsConnectionTester internal constructor(
    private val sampleDir: File,
    private val asrClient: AsrClient,
    private val llmClient: LlmClient,
    private val audioProvider: AsrTestAudioProvider,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        asrClient: AsrClient,
        llmClient: LlmClient,
        audioProvider: AssetAsrTestAudioProvider,
    ) : this(File(context.cacheDir, "connection-test"), asrClient, llmClient, audioProvider)

    suspend fun testAsr(): ConnectionTestResult = withContext(Dispatchers.IO) {
        runCatching {
            val file = audioProvider.createSampleFile(sampleDir)
            try {
                asrClient.transcribe(file)
            } finally {
                file.delete()
            }
        }.fold(
            onSuccess = { utterances ->
                if (utterances.isNotEmpty()) {
                    ConnectionTestResult(true, "本机中文语音识别正常：${utterances.first().text.take(24)}")
                } else {
                    ConnectionTestResult(true, "本机中文语音模型已加载（测试音频未识别到文字）")
                }
            },
            onFailure = { ConnectionTestResult(false, it.readableMessage("本机语音识别失败")) },
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
