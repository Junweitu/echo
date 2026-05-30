package tech.echo.app.ui.settings

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tech.echo.app.core.summary.LlmClient
import tech.echo.app.core.upload.AsrClient
import tech.echo.app.core.upload.AsrStatusException
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
            onSuccess = { ConnectionTestResult(true, "豆包语音连接正常") },
            onFailure = {
                if (it is AsrStatusException && it.statusCode == SILENT_AUDIO_STATUS) {
                    ConnectionTestResult(true, "豆包语音连接正常（测试音频无语音）")
                } else {
                    ConnectionTestResult(false, it.readableMessage("豆包语音连接失败"))
                }
            },
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

private const val SILENT_AUDIO_STATUS = "20000003"
