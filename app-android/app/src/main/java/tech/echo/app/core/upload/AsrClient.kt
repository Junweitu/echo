package tech.echo.app.core.upload

import java.io.File

/** 云端 ASR 抽象，屏蔽火山/未来供应商差异。 */
interface AsrClient {
    suspend fun transcribe(audioFile: File): List<AsrUtterance>
}

data class AsrUtterance(
    val speakerLabel: String?,
    val text: String,
    val startMs: Long,
    val endMs: Long,
)

class AsrConfigurationException(message: String) : IllegalStateException(message)

class AsrStatusException(
    val statusCode: String,
    val statusMessage: String?,
    val responseBody: String,
) : IllegalStateException(
    buildString {
        append("火山 ASR 请求失败：status=")
        append(statusCode)
        if (!statusMessage.isNullOrBlank()) {
            append(", message=")
            append(statusMessage)
        }
    }
)
