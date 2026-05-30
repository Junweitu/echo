package tech.echo.app.core.summary

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import tech.echo.app.core.data.db.DailySummaryEntity
import tech.echo.app.core.data.db.SummaryStatusDb
import tech.echo.app.core.data.db.TimelineEntryData

@Serializable
data class StructuredSummary(
    val diary: String = "",
    val todos: List<String> = emptyList(),
    val inspirations: List<String> = emptyList(),
    val timeline: List<TimelineEntryData> = emptyList(),
)

/** 解析 LLM 返回的每日整理 JSON。 */
object SummaryJsonParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(raw: String): StructuredSummary =
        json.decodeFromString<StructuredSummary>(stripMarkdownFence(raw))

    fun toEntity(date: String, summary: StructuredSummary): DailySummaryEntity =
        DailySummaryEntity(
            date = date,
            diary = summary.diary.trim(),
            todos = summary.todos.map { it.trim() }.filter { it.isNotEmpty() },
            inspirations = summary.inspirations.map { it.trim() }.filter { it.isNotEmpty() },
            timeline = summary.timeline.filter {
                it.time.isNotBlank() || it.person.isNotBlank() || it.topic.isNotBlank()
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
