package tech.echo.app.ui.detail

import tech.echo.app.core.model.TranscriptSegment

data class TranscriptHourSection(
    val hourKey: String,
    val headerLabel: String,
    val segments: List<TranscriptSegment>,
)

object TranscriptTimeline {
    fun sections(segments: List<TranscriptSegment>): List<TranscriptHourSection> {
        val sorted = segments.sortedWith(
            compareByDescending<TranscriptSegment> { it.startTimeMs }
                .thenByDescending { it.id }
        )
        return sorted
            .groupBy { it.hourKey() }
            .map { (hour, rows) ->
                TranscriptHourSection(
                    hourKey = hour,
                    headerLabel = "$hour:00",
                    segments = rows,
                )
            }
    }

    fun itemIndexByHour(sections: List<TranscriptHourSection>): Map<String, Int> {
        var itemIndex = 0
        return buildMap {
            sections.forEach { section ->
                put(section.hourKey, itemIndex)
                itemIndex += 1 + section.segments.size
            }
        }
    }

    private fun TranscriptSegment.hourKey(): String =
        time.substringBefore(':', missingDelimiterValue = time)
            .filter { it.isDigit() }
            .padStart(2, '0')
            .takeLast(2)
}
