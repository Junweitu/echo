package tech.echo.app.core.settings

/**
 * App 内用户配置（见 design.md §6.2 / journal 2026-05-29 决策）。
 *
 * echo 不内置任何 key，全部由用户在设置页填写，加密存本地。
 * 火山配置：旧控制台用 App ID + Access Key；新控制台可把 API Key 填在 App ID/API Key
 * 字段并留空 Access Key。DeepSeek 三件套：每日整理 LLM 用。
 * baseUrl/模型名给默认值可改，key 留空表示未配置。
 */
data class AppConfig(
    // —— 火山引擎 ASR ——
    val volcAppId: String = "",
    val volcAccessKey: String = "",
    val volcResourceId: String = DEFAULT_VOLC_RESOURCE_ID,
    // —— DeepSeek LLM ——
    val deepSeekBaseUrl: String = DEFAULT_DEEPSEEK_BASE_URL,
    val deepSeekApiKey: String = "",
    val deepSeekModel: String = DEFAULT_DEEPSEEK_MODEL,
) {
    /** ASR 配置是否齐全（Resource ID + 旧/新控制台任一 key 形式）。 */
    val isAsrConfigured: Boolean
        get() = volcAppId.isNotBlank() && volcResourceId.isNotBlank()

    /** LLM 配置是否齐全（缺则每日整理不启动）。 */
    val isLlmConfigured: Boolean
        get() = deepSeekBaseUrl.isNotBlank() && deepSeekApiKey.isNotBlank() && deepSeekModel.isNotBlank()

    companion object {
        const val DEFAULT_VOLC_RESOURCE_ID = VolcAsrResourceIds.FILE_FLASH_TURBO
        const val DEFAULT_DEEPSEEK_BASE_URL = "https://api.deepseek.com"
        const val DEFAULT_DEEPSEEK_MODEL = "deepseek-v4-flash"
    }
}
