package tech.echo.app.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.echo.app.core.data.db.DailySummaryEntity
import tech.echo.app.core.data.db.SegmentEntity
import tech.echo.app.core.data.db.SegmentStatus
import tech.echo.app.core.data.db.SummaryStatusDb
import tech.echo.app.core.data.db.TimelineEntryData
import tech.echo.app.core.model.SummaryStatus
import java.time.Instant
import java.time.Clock
import java.time.ZoneId

class DetailMappersTest {

    private val zoneId = ZoneId.of("Asia/Shanghai")
    private val clock = Clock.fixed(Instant.parse("2026-05-30T02:00:00Z"), zoneId)

    @Test
    fun mapsDailySummaryEntityToUiSummary() {
        val summary = DetailMappers.toDailySummary(
            entity = DailySummaryEntity(
                date = "20260530",
                diary = "今天聊了报价。",
                todos = listOf("回邮件"),
                inspirations = listOf("做语音整理"),
                timeline = listOf(TimelineEntryData("10:00", "我", "报价")),
                status = SummaryStatusDb.DONE.name,
            ),
            date = "20260530",
            clock = clock,
            zoneId = zoneId,
        )

        assertEquals("今天 5月30日", summary.displayDate)
        assertEquals(SummaryStatus.DONE, summary.summaryStatus)
        assertEquals("今天聊了报价。", summary.diary)
        assertEquals("回邮件", summary.todos.single())
        assertEquals("报价", summary.timeline.single().topic)
    }

    @Test
    fun mapsSegmentsToTranscriptRows() {
        val start = Instant.parse("2026-05-30T02:03:00Z").toEpochMilli()
        val segments = DetailMappers.toTranscriptSegments(
            segments = listOf(
                SegmentEntity(
                    id = "a",
                    date = "20260530",
                    startTime = start,
                    durationMs = 1000,
                    audioPath = "/tmp/a.wav",
                    speakerLabel = "A",
                    transcriptText = "你好",
                    status = SegmentStatus.DONE.name,
                ),
                SegmentEntity(
                    id = "b",
                    date = "20260530",
                    startTime = start + 1,
                    durationMs = 1000,
                    audioPath = "/tmp/b.wav",
                    transcriptText = null,
                    status = SegmentStatus.RECORDED.name,
                ),
            ),
            zoneId = zoneId,
        )

        assertEquals("b", segments[0].id)
        assertEquals(null, segments[0].speakerKey)
        assertEquals("Speaker A", segments[0].speakerLabel)
        assertEquals("等待上传转写", segments[0].text)
        assertEquals("/tmp/b.wav", segments[0].audioPath)
        assertEquals(start + 1, segments[0].startTimeMs)
        assertEquals("10:03", segments[1].time)
        assertEquals("a", segments[1].id)
        assertEquals("A", segments[1].speakerKey)
        assertEquals("Speaker A", segments[1].speakerLabel)
        assertFalse(segments[0].claimed)
        assertEquals("你好", segments[1].text)
        assertEquals("/tmp/a.wav", segments[1].audioPath)
        assertEquals(1000, segments[1].durationMs)
        assertEquals(SegmentStatus.DONE.name, segments[1].status)
        assertEquals(start, segments[1].startTimeMs)
    }

    @Test
    fun personIdMarksSegmentClaimedAndDisplaysClaimedName() {
        val start = Instant.parse("2026-05-30T02:03:00Z").toEpochMilli()
        val segment = DetailMappers.toTranscriptSegments(
            segments = listOf(
                SegmentEntity(
                    id = "a",
                    date = "20260530",
                    startTime = start,
                    durationMs = 1000,
                    audioPath = "/tmp/a.wav",
                    speakerLabel = "A",
                    speakerPersonId = "我",
                    transcriptText = "你好",
                    status = SegmentStatus.DONE.name,
                ),
            ),
            zoneId = zoneId,
        ).single()

        assertTrue(segment.claimed)
        assertEquals("我", segment.speakerLabel)
    }
}
