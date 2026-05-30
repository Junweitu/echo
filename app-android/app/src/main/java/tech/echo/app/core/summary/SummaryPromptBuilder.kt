package tech.echo.app.core.summary

import tech.echo.app.core.data.db.SegmentEntity
import tech.echo.app.core.time.EchoDateFormatter
import java.time.ZoneId

/** 生成 DeepSeek 每日整理 prompt。 */
object SummaryPromptBuilder {

    fun build(
        date: String,
        segments: List<SegmentEntity>,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String {
        val transcript = segments.joinToString("\n") { segment ->
            val time = EchoDateFormatter.timeLabel(segment.startTime, zoneId)
            val speaker = segment.speakerLabel?.takeIf { it.isNotBlank() }
                ?.let { "说话人 $it" }
                ?: "未知说话人"
            val text = segment.transcriptText.orEmpty().replace('\n', ' ').trim()
            "[$time] $speaker：$text"
        }

        return """
            你是 echo 的每日语音整理助手。请只基于用户当天转写内容整理，不要编造没有出现的事实。
            日期：$date

            请输出严格 JSON，不要输出 Markdown。JSON schema 示例：
            {
              "diary": "一段自然语言日记",
              "todos": ["明确行动项"],
              "inspirations": ["想法或灵感"],
              "timeline": [{"time": "09:20", "person": "说话人 A", "topic": "一句话主题"}]
            }

            规则：
            - diary 用中文自然段，保留当天发生了什么和重要判断。
            - todos 只提取明确承诺、要做的动作或待跟进事项。
            - inspirations 只提取有价值的想法，不要把普通聊天硬凑成灵感。
            - timeline 按时间顺序输出，topic 简短。

            当天转写：
            $transcript
        """.trimIndent()
    }
}
