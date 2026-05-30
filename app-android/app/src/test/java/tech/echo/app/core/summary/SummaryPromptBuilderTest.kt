package tech.echo.app.core.summary

import org.junit.Assert.assertTrue
import org.junit.Test
import tech.echo.app.core.data.db.SegmentEntity
import tech.echo.app.core.data.db.SegmentStatus
import java.time.Instant
import java.time.ZoneId

class SummaryPromptBuilderTest {

    @Test
    fun promptContainsJsonSchemaAndTranscriptLines() {
        val prompt = SummaryPromptBuilder.build(
            date = "20260530",
            segments = listOf(
                SegmentEntity(
                    id = "s1",
                    date = "20260530",
                    startTime = Instant.parse("2026-05-30T02:03:00Z").toEpochMilli(),
                    durationMs = 1000,
                    audioPath = "/tmp/s1.wav",
                    speakerLabel = "A",
                    transcriptText = "周末订机票。",
                    status = SegmentStatus.DONE.name,
                ),
            ),
            zoneId = ZoneId.of("Asia/Shanghai"),
        )

        assertTrue(prompt.contains("json", ignoreCase = true))
        assertTrue(prompt.contains("\"diary\""))
        assertTrue(prompt.contains("[10:03] 说话人 A：周末订机票。"))
    }
}
