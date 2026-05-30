package tech.echo.app.ui.detail

import tech.echo.app.core.data.db.DailySummaryEntity
import tech.echo.app.core.data.db.SegmentEntity
import tech.echo.app.core.data.db.SegmentStatus
import tech.echo.app.core.data.db.SummaryStatusDb
import tech.echo.app.core.model.DailySummary
import tech.echo.app.core.model.SummaryStatus
import tech.echo.app.core.model.TimelineEntry
import tech.echo.app.core.model.TranscriptSegment
import tech.echo.app.core.time.EchoDateFormatter
import java.time.Clock
import java.time.ZoneId

object DetailMappers {
    fun toDailySummary(
        entity: DailySummaryEntity?,
        date: String,
        clock: Clock = Clock.systemDefaultZone(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): DailySummary =
        DailySummary(
            date = date,
            displayDate = EchoDateFormatter.displayDate(date, clock),
            summaryStatus = entity?.status.toUiStatus(),
            diary = entity?.diary?.takeIf { it.isNotBlank() } ?: "还没有整理结果",
            todos = entity?.todos.orEmpty(),
            inspirations = entity?.inspirations.orEmpty(),
            timeline = entity?.timeline.orEmpty().map {
                TimelineEntry(time = it.time, person = it.person, topic = it.topic)
            },
        )

    fun toTranscriptSegments(
        segments: List<SegmentEntity>,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): List<TranscriptSegment> =
        segments.sortedByDescending { it.startTime }.map { segment ->
            TranscriptSegment(
                id = segment.id,
                speakerKey = segment.speakerLabel?.takeIf { it.isNotBlank() },
                time = EchoDateFormatter.timeLabel(segment.startTime, zoneId),
                speakerLabel = segment.displaySpeakerLabel(),
                claimed = !segment.speakerPersonId.isNullOrBlank(),
                audioPath = segment.audioPath,
                durationMs = segment.durationMs,
                status = segment.status,
                text = segment.transcriptText?.takeIf { it.isNotBlank() } ?: segment.transcriptFallback(),
                startTimeMs = segment.startTime,
            )
        }

    private fun SegmentEntity.displaySpeakerLabel(): String =
        when {
            !speakerPersonId.isNullOrBlank() -> speakerPersonId
            !speakerLabel.isNullOrBlank() -> "Speaker $speakerLabel"
            else -> "Speaker A"
        }

    private fun SegmentEntity.transcriptFallback(): String =
        when (status) {
            SegmentStatus.RECORDED.name -> "等待上传转写"
            SegmentStatus.UPLOADING.name,
            SegmentStatus.TRANSCRIBING.name -> "正在转写"
            SegmentStatus.FAILED.name -> "转写失败，后台会重试"
        else -> "暂无转写文本"
        }

    private fun String?.toUiStatus(): SummaryStatus =
        when (this) {
            SummaryStatusDb.DONE.name -> SummaryStatus.DONE
            SummaryStatusDb.GENERATING.name -> SummaryStatus.GENERATING
            SummaryStatusDb.FAILED.name -> SummaryStatus.FAILED
            else -> SummaryStatus.PENDING
        }
}
