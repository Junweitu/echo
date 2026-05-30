package tech.echo.app.core.upload

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import tech.echo.app.core.settings.AppConfigProvider
import tech.echo.app.core.settings.VolcAsrResourceIds
import java.io.File
import java.io.IOException
import java.util.Base64
import java.util.UUID
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class VolcAsrClient @Inject constructor(
    private val configProvider: AppConfigProvider,
    private val httpClient: OkHttpClient,
    private val json: Json,
    @Named("VolcAsrEndpoint") private val endpoint: String,
) : AsrClient {

    override suspend fun transcribe(audioFile: File): List<AsrUtterance> = withContext(Dispatchers.IO) {
        val config = configProvider.current()
        if (!config.isAsrConfigured) {
            throw AsrConfigurationException("火山 ASR 配置不完整")
        }
        VolcAsrResourceIds.fileFlashCompatibilityError(config.volcResourceId)?.let { message ->
            throw AsrConfigurationException(message)
        }
        if (!audioFile.exists()) {
            throw IOException("音频文件不存在：${audioFile.absolutePath}")
        }

        val body = VolcRecognizeRequest(
            user = VolcUser(uid = config.volcAppId),
            audio = VolcAudio(
                data = Base64.getEncoder().encodeToString(audioFile.readBytes()),
            ),
            request = VolcRequest(modelName = "bigmodel", enableSpeakerInfo = true),
        )
        val requestBuilder = Request.Builder()
            .url(endpoint)
            .addHeader("Content-Type", "application/json")
            .addHeader("X-Api-Resource-Id", config.volcResourceId)
            .addHeader("X-Api-Request-Id", UUID.randomUUID().toString())
            .addHeader("X-Api-Sequence", "-1")
            .post(json.encodeToString(body).toRequestBody(JSON_MEDIA_TYPE))

        if (config.volcAccessKey.isBlank()) {
            requestBuilder.addHeader("X-Api-Key", config.volcAppId)
        } else {
            requestBuilder
                .addHeader("X-Api-App-Key", config.volcAppId)
                .addHeader("X-Api-Access-Key", config.volcAccessKey)
        }

        httpClient.newCall(requestBuilder.build()).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            val statusCode = response.header("X-Api-Status-Code")
            if (!response.isSuccessful || statusCode != SUCCESS_CODE) {
                throw AsrStatusException(
                    statusCode = statusCode ?: "HTTP_${response.code}",
                    statusMessage = response.header("X-Api-Message"),
                    responseBody = responseBody,
                )
            }
            val parsed = json.decodeFromString<VolcRecognizeResponse>(responseBody)
            val utterances = parsed.result?.utterances.orEmpty().mapNotNull { it.toAsrUtterance() }
            if (utterances.isNotEmpty()) utterances else listOf(
                AsrUtterance(
                    speakerLabel = null,
                    text = parsed.result?.text.orEmpty(),
                    startMs = 0,
                    endMs = parsed.audioInfo?.duration ?: 0,
                ),
            ).filter { it.text.isNotBlank() }
        }
    }

    private fun VolcUtterance.toAsrUtterance(): AsrUtterance? {
        val normalizedText = text.trim()
        if (normalizedText.isEmpty()) return null
        return AsrUtterance(
            speakerLabel = speaker ?: speakerLabel,
            text = normalizedText,
            startMs = startTime ?: 0,
            endMs = endTime ?: 0,
        )
    }

    private companion object {
        const val SUCCESS_CODE = "20000000"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

@Serializable
private data class VolcRecognizeRequest(
    val user: VolcUser,
    val audio: VolcAudio,
    val request: VolcRequest,
)

@Serializable
private data class VolcUser(
    val uid: String,
)

@Serializable
private data class VolcAudio(
    val data: String,
)

@Serializable
private data class VolcRequest(
    @SerialName("model_name") val modelName: String,
    @SerialName("enable_speaker_info") val enableSpeakerInfo: Boolean,
)

@Serializable
private data class VolcRecognizeResponse(
    @SerialName("audio_info") val audioInfo: VolcAudioInfo? = null,
    val result: VolcResult? = null,
)

@Serializable
private data class VolcAudioInfo(
    val duration: Long = 0,
)

@Serializable
private data class VolcResult(
    val text: String = "",
    val utterances: List<VolcUtterance> = emptyList(),
)

@Serializable
private data class VolcUtterance(
    val text: String = "",
    val speaker: String? = null,
    @SerialName("speaker_label") val speakerLabel: String? = null,
    @SerialName("start_time") val startTime: Long? = null,
    @SerialName("end_time") val endTime: Long? = null,
)
