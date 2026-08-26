package tech.echo.app.ui.settings

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tech.echo.app.core.summary.LlmClient
import tech.echo.app.core.text.TraditionalChinese
import tech.echo.app.core.upload.LocalZipformerAsrClient
import java.io.File
import javax.inject.Inject

data class ConnectionTestResult(
    val success: Boolean,
    val message: String,
)

class SettingsConnectionTester internal constructor(
    private val sampleDir: File,
    private val localZipformerAsrClient: LocalZipformerAsrClient,
    private val llmClient: LlmClient,
    private val audioProvider: AsrTestAudioProvider,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        localZipformerAsrClient: LocalZipformerAsrClient,
        llmClient: LlmClient,
        audioProvider: AssetAsrTestAudioProvider,
    ) : this(File(context.cacheDir, "connection-test"), localZipformerAsrClient, llmClient, audioProvider)

    suspend fun testAsr(): ConnectionTestResult = withContext(Dispatchers.IO) {
        runCatching {
            val file = audioProvider.createSampleFile(sampleDir)
            try {
                localZipformerAsrClient.transcribe(file)
            } finally {
                file.delete()
            }
        }.fold(
            onSuccess = { utterances ->
                if (utterances.isNotEmpty()) {
                    ConnectionTestResult(
                        true,
                        "Zipformer CTC 本機中文辨識正常：${TraditionalChinese.convert(utterances.first().text.take(40))}",
                    )
                } else {
                    ConnectionTestResult(true, "Zipformer CTC 模型已載入（測試音訊未辨識到文字）")
                }
            },
            onFailure = { ConnectionTestResult(false, it.readableMessage("Zipformer CTC 本機語音辨識失敗")) },
        )
    }

    suspend fun testLlm(): ConnectionTestResult =
        runCatching {
            llmClient.summarize("""請只回傳 JSON：{"ok":true}""")
        }.fold(
            onSuccess = { ConnectionTestResult(true, "DeepSeek 連線正常") },
            onFailure = { ConnectionTestResult(false, it.readableMessage("DeepSeek 連線失敗")) },
        )

    private fun Throwable.readableMessage(prefix: String): String =
        TraditionalChinese.convert("$prefix：${message ?: javaClass.simpleName}")
}
