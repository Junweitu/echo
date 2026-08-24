package tech.echo.app.core.summary

import tech.echo.app.core.data.db.SegmentEntity
import tech.echo.app.core.text.TraditionalChinese
import tech.echo.app.core.time.EchoDateFormatter
import java.time.ZoneId

/** 產生 DeepSeek 每日整理 prompt。 */
object SummaryPromptBuilder {

    fun build(
        date: String,
        segments: List<SegmentEntity>,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String {
        val transcript = segments.joinToString("\n") { segment ->
            val time = EchoDateFormatter.timeLabel(segment.startTime, zoneId)
            val speaker = segment.speakerLabel?.takeIf { it.isNotBlank() }
                ?.let { "說話者 $it" }
                ?: "未知說話者"
            val text = TraditionalChinese.convert(segment.transcriptText.orEmpty())
                .replace('\n', ' ')
                .trim()
            "[$time] $speaker：$text"
        }

        return """
            你是 Echo 的每日語音整理助手。請只根據使用者當天的轉寫內容整理，不要編造沒有出現的事實。
            日期：$date

            所有中文內容一律使用繁體中文（台灣用字），不要輸出簡體中文。
            請輸出嚴格 JSON，不要輸出 Markdown。JSON schema 範例：
            {
              "diary": "一段自然語言日記",
              "todos": ["明確行動項目"],
              "inspirations": ["想法或靈感"],
              "timeline": [{"time": "09:20", "person": "說話者 A", "topic": "一句話主題"}]
            }

            規則：
            - diary 使用自然的繁體中文段落，保留當天發生的事情與重要判斷。
            - todos 只提取明確承諾、要做的動作或待追蹤事項。
            - inspirations 只提取有價值的想法，不要把普通聊天硬湊成靈感。
            - timeline 依時間順序輸出，topic 簡短。

            當天轉寫：
            $transcript
        """.trimIndent()
    }
}
