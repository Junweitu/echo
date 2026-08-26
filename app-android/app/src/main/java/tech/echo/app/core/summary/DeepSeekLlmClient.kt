package tech.echo.app.core.summary

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
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeepSeekLlmClient @Inject constructor(
    private val configProvider: AppConfigProvider,
    private val httpClient: OkHttpClient,
    private val json: Json,
) : LlmClient {

    override suspend fun summarize(prompt: String): String = withContext(Dispatchers.IO) {
        val config = configProvider.current()
        if (!config.isLlmConfigured) {
            throw LlmConfigurationException("DeepSeek 配置不完整")
        }

        // 最後一道防線：Authorization header 不容許 CR/LF 或其他控制字元。
        // 即使設定資料來自舊版，也在真正送出 request 前再次清理。
        val apiKey = sanitizeApiKey(config.deepSeekApiKey)
        if (apiKey.isBlank()) throw LlmConfigurationException("DeepSeek API Key 為空")

        val baseUrl = config.deepSeekBaseUrl.replace("\r", "").replace("\n", "").trim().trimEnd('/')
        val model = config.deepSeekModel.replace("\r", "").replace("\n", "").trim()
        val url = "$baseUrl/chat/completions"
        val requestBody = DeepSeekChatRequest(
            model = model,
            messages = listOf(
                DeepSeekMessage(
                    role = "system",
                    content = "你只輸出合法 JSON。使用者 prompt 會包含 json schema。",
                ),
                DeepSeekMessage(role = "user", content = prompt),
            ),
            responseFormat = DeepSeekResponseFormat("json_object"),
        )
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(json.encodeToString(requestBody).toRequestBody(JSON_MEDIA_TYPE))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("DeepSeek 請求失敗：HTTP ${response.code} $body")
            }
            val parsed = json.decodeFromString<DeepSeekChatResponse>(body)
            parsed.choices.firstOrNull()?.message?.content?.takeIf { it.isNotBlank() }
                ?: throw IOException("DeepSeek 返回內容為空")
        }
    }

    private fun sanitizeApiKey(value: String): String = buildString(value.length) {
        value.forEach { ch ->
            if (!ch.isWhitespace() && !Character.isISOControl(ch) && ch != '\u200B' && ch != '\uFEFF') {
                append(ch)
            }
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

@Serializable
private data class DeepSeekChatRequest(
    val model: String,
    val messages: List<DeepSeekMessage>,
    @SerialName("response_format") val responseFormat: DeepSeekResponseFormat,
    val temperature: Double = 0.2,
)

@Serializable
private data class DeepSeekMessage(
    val role: String = "assistant",
    val content: String,
)

@Serializable
private data class DeepSeekResponseFormat(
    val type: String,
)

@Serializable
private data class DeepSeekChatResponse(
    val choices: List<DeepSeekChoice> = emptyList(),
)

@Serializable
private data class DeepSeekChoice(
    val message: DeepSeekMessage,
)
