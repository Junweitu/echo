package tech.echo.app.core.summary

/** 每日整理用 LLM 抽象，屏蔽具体供应商。 */
interface LlmClient {
    suspend fun summarize(prompt: String): String
}

class LlmConfigurationException(message: String) : IllegalStateException(message)
