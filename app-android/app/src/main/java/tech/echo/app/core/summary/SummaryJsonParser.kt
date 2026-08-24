package tech.echo.app.core.summary

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import tech.echo.app.core.data.db.DailySummaryEntity
import tech.echo.app.core.data.db.SummaryStatusDb
import tech.echo.app.core.data.db.TimelineEntryData
import tech.echo.app.core.text.TraditionalChinese

@Serializable
data class StructuredSummary(
    val diary: String = "",
    val todos: List<String> = emptyList(),
    val inspirations: List<String> = emptyList(),
    val timeline: List<TimelineEntryData> = emptyList(),
)

/** 解析 LLM 回傳的每日整理 JSON，並統一成繁體中文。 */
object SummaryJsonParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(raw: String): StructuredSummary =
        json.decodeFromString<StructuredSummary>(stripMarkdownFence(raw))

    fun toEntity(date: String, summary: StructuredSummary): DailySummaryEntity =
        DailySummaryEntity(
            date = date,
            diary = TraditionalChinese.convert(summary.diary.trim()),
            todos = summary.todos
                .map { TraditionalChinese.convert(it.trim()) }
                .filter { it.isNotEmpty() },
            inspirations = summary.inspirations
                .map { TraditionalChinese.convert(it.trim()) }
                .filter { it.isNotEmpty() },
            timeline = summary.timeline
                .filter { it.time.isNotBlank() || it.person.isNotBlank() || it.topic.isNotBlank() }
                .map {
                    it.copy(
                        person = TraditionalChinese.convert(it.person),
                        topic = TraditionalChinese.convert(it.topic),
                    )
                },
            status = SummaryStatusDb.DONE.name,
            generatedAt = System.currentTimeMillis(),
        )

    private fun stripMarkdownFence(raw: String): String {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("```")) return trimmed
        return trimmed
            .lines()
            .drop(1)
            .dropLast(1)
            .joinToString("\n")
            .trim()
    }
}
