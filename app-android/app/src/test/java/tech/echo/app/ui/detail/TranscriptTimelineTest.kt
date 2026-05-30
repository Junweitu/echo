package tech.echo.app.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Test
import tech.echo.app.core.model.TranscriptSegment

class TranscriptTimelineTest {

    @Test
    fun `sections are grouped by hour with newest segments first`() {
        val sections = TranscriptTimeline.sections(
            listOf(
                segment(id = "10-a", time = "10:05", startTimeMs = 100L),
                segment(id = "10-b", time = "10:45", startTimeMs = 200L),
                segment(id = "11-a", time = "11:01", startTimeMs = 300L),
            )
        )

        assertEquals(listOf("11:00", "10:00"), sections.map { it.headerLabel })
        assertEquals(listOf("10-b", "10-a"), sections[1].segments.map { it.id })
    }

    @Test
    fun `hour index points to the header item in the flattened list`() {
        val sections = TranscriptTimeline.sections(
            listOf(
                segment(id = "12-a", time = "12:01", startTimeMs = 300L),
                segment(id = "11-a", time = "11:20", startTimeMs = 200L),
                segment(id = "11-b", time = "11:05", startTimeMs = 100L),
            )
        )

        assertEquals(
            mapOf("12" to 0, "11" to 2),
            TranscriptTimeline.itemIndexByHour(sections),
        )
    }

    private fun segment(
        id: String,
        time: String,
        startTimeMs: Long,
    ): TranscriptSegment = TranscriptSegment(
        id = id,
        speakerKey = "A",
        time = time,
        speakerLabel = "Speaker A",
        claimed = false,
        audioPath = "",
        durationMs = 1000,
        status = "DONE",
        text = "测试",
        startTimeMs = startTimeMs,
    )
}
