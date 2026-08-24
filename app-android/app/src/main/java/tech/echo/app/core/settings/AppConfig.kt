package tech.echo.app.core.settings

/**
 * App 内用户配置。
 *
 * 当前正式 ASR 使用本机 Vosk，因此语音转写不再需要火山引擎密钥。
 * 旧的火山字段暂时保留，只为兼容既有设置数据与未来可选回退；设置页不再要求填写。
 * DeepSeek 三件套继续用于每日整理。
 */
data class AppConfig(
    // —— 旧火山引擎 ASR 配置（兼容保留，当前本机 ASR 不使用）——
    val volcAppId: String = "",
    val volcAccessKey: String = "",
    val volcResourceId: String = DEFAULT_VOLC_RESOURCE_ID,
    // —— DeepSeek LLM ——
    val deepSeekBaseUrl: String = DEFAULT_DEEPSEEK_BASE_URL,
    val deepSeekApiKey: String = "",
    val deepSeekModel: String = DEFAULT_DEEPSEEK_MODEL,
) {
    /** 当前本机 ASR 随 APK 提供，不需要用户配置。 */
    val isAsrConfigured: Boolean
        get() = true

    /** LLM 配置是否齐全（缺则每日整理不启动）。 */
    val isLlmConfigured: Boolean
        get() = deepSeekBaseUrl.isNotBlank() && deepSeekApiKey.isNotBlank() && deepSeekModel.isNotBlank()

    companion object {
        const val DEFAULT_VOLC_RESOURCE_ID = VolcAsrResourceIds.FILE_FLASH_TURBO
        const val DEFAULT_DEEPSEEK_BASE_URL = "https://api.deepseek.com"
        const val DEFAULT_DEEPSEEK_MODEL = "deepseek-v4-flash"
    }
}
