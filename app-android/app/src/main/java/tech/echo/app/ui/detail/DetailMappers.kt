package tech.echo.app.ui.detail

import tech.echo.app.core.data.db.DailySummaryEntity
import tech.echo.app.core.data.db.SegmentEntity
import tech.echo.app.core.data.db.SegmentStatus
import tech.echo.app.core.data.db.SummaryStatusDb
import tech.echo.app.core.model.DailySummary
import tech.echo.app.core.model.SummaryStatus
import tech.echo.app.core.model.TimelineEntry
import tech.echo.app.core.model.TranscriptSegment
import tech.echo.app.core.text.TraditionalChinese
import tech.echo.app.core.time.EchoDateFormatter
import java.time.Clock
import java.time.ZoneId
import java.util.Locale

object DetailMappers {
    fun toDailySummary(
        entity: DailySummaryEntity?,
        date: String,
        clock: Clock = Clock.systemDefaultZone(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): DailySummary =
        DailySummary(
            date = date,
            displayDate = TraditionalChinese.convert(EchoDateFormatter.displayDate(date, clock)),
            summaryStatus = entity?.status.toUiStatus(),
            diary = entity?.diary?.takeIf { it.isNotBlank() }
                ?.let(TraditionalChinese::convert)
                ?: "還沒有整理結果",
            todos = TraditionalChinese.convert(entity?.todos.orEmpty()),
            inspirations = TraditionalChinese.convert(entity?.inspirations.orEmpty()),
            timeline = entity?.timeline.orEmpty().map {
                TimelineEntry(
                    time = it.time,
                    person = TraditionalChinese.convert(it.person),
                    topic = TraditionalChinese.convert(it.topic),
                )
            },
        )

    fun toTranscriptSegments(
        segments: List<SegmentEntity>,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): List<TranscriptSegment> =
        segments.sortedByDescending { it.startTime }.map { segment ->
            val transcript = segment.transcriptText
                ?.takeIf { it.isNotBlank() }
                ?.let(TraditionalChinese::convert)
                ?: segment.transcriptFallback()
            TranscriptSegment(
                id = segment.id,
                speakerKey = segment.speakerLabel?.takeIf { it.isNotBlank() },
                time = EchoDateFormatter.timeLabel(segment.startTime, zoneId),
                speakerLabel = segment.displaySpeakerLabel(),
                claimed = !segment.speakerPersonId.isNullOrBlank(),
                audioPath = segment.audioPath,
                durationMs = segment.durationMs,
                status = segment.status,
                text = transcript + segment.asrDiagnosticDisplay(),
                startTimeMs = segment.startTime,
            )
        }

    private fun SegmentEntity.displaySpeakerLabel(): String =
        when {
            !speakerPersonId.isNullOrBlank() -> TraditionalChinese.convert(speakerPersonId)
            !speakerLabel.isNullOrBlank() -> "說話者 $speakerLabel"
            else -> "說話者 A"
        }

    private fun SegmentEntity.transcriptFallback(): String =
        when (status) {
            SegmentStatus.RECORDED.name -> "等待語音轉寫"
            SegmentStatus.UPLOADING.name,
            SegmentStatus.TRANSCRIBING.name -> "正在轉寫"
            SegmentStatus.FAILED.name -> "轉寫失敗，背景會重試"
            else -> "暫無轉寫文字"
        }

    private fun SegmentEntity.asrDiagnosticDisplay(): String {
        val engine = asrEngine?.takeIf { it.isNotBlank() } ?: return ""
        val seconds = asrElapsedMs?.let { String.format(Locale.US, "%.2f", it / 1000.0) }
        return buildString {
            append("\n\n［ASR：")
            append(TraditionalChinese.convert(engine))
            if (seconds != null) append(" · ${seconds} 秒")
            append('］')
            asrFallbackReason?.takeIf { it.isNotBlank() }?.let {
                append("\n［Samsung 備援原因：")
                append(TraditionalChinese.convert(it))
                append('］')
            }
        }
    }

    private fun String?.toUiStatus(): SummaryStatus =
        when (this) {
            SummaryStatusDb.DONE.name -> SummaryStatus.DONE
            SummaryStatusDb.GENERATING.name -> SummaryStatus.GENERATING
            SummaryStatusDb.FAILED.name -> SummaryStatus.FAILED
            else -> SummaryStatus.PENDING
        }
}
