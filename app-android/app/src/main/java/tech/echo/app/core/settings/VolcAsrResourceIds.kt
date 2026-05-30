package tech.echo.app.core.settings

/**
 * 火山 ASR Resource ID 与当前 Echo 接口形态的兼容性规则。
 *
 * Echo 现在走“大模型录音文件极速版” recognize/flash，本地 WAV 以 base64 一次请求返回结果。
 * 豆包流式 1.0/2.0 和录音文件标准版 1.0/2.0 都需要不同接口，不能只替换 Resource ID。
 */
object VolcAsrResourceIds {
    const val FILE_FLASH_TURBO = "volc.bigasr.auc_turbo"

    private val streamingIds = setOf(
        "volc.bigasr.sauc.duration",
        "volc.bigasr.sauc.concurrent",
        "volc.seedasr.sauc.duration",
        "volc.seedasr.sauc.concurrent",
    )

    private val standardFileIds = setOf(
        "volc.bigasr.auc",
        "volc.seedasr.auc",
    )

    fun fileFlashCompatibilityError(resourceId: String): String? {
        val normalized = resourceId.trim()
        return when {
            normalized.isBlank() -> null
            normalized == FILE_FLASH_TURBO -> null
            normalized in streamingIds ->
                "当前 Echo 使用录音文件极速版 ASR，不能使用流式语音识别 Resource ID：$normalized。流式 2.0 需要 WebSocket 接入；当前请填 $FILE_FLASH_TURBO。"

            normalized in standardFileIds ->
                "当前 Echo 使用录音文件极速版 ASR，不能使用录音文件标准版 Resource ID：$normalized。豆包录音文件 2.0 标准版需要 submit/query 链路；当前请填 $FILE_FLASH_TURBO。"

            else ->
                "当前 Echo 的 recognize/flash 接口只支持 Resource ID：$FILE_FLASH_TURBO。"
        }
    }

    fun settingsHint(resourceId: String): String =
        fileFlashCompatibilityError(resourceId)
            ?: "当前转写链路使用录音文件极速版，Resource ID 固定为 $FILE_FLASH_TURBO。"
}
