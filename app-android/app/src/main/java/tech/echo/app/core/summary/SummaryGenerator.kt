package tech.echo.app.core.summary

import tech.echo.app.core.data.db.DailySummaryEntity
import tech.echo.app.core.data.db.SummaryStatusDb
import tech.echo.app.core.data.repository.DailySummaryRepository
import tech.echo.app.core.data.repository.SegmentRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SummaryGenerator @Inject constructor(
    private val segmentRepository: SegmentRepository,
    private val dailySummaryRepository: DailySummaryRepository,
    private val llmClient: LlmClient,
) {

    suspend fun generate(date: String): Boolean {
        val segments = segmentRepository.getDoneByDate(date)
            .filter { !it.transcriptText.isNullOrBlank() }
        if (segments.isEmpty()) {
            dailySummaryRepository.upsert(DailySummaryEntity(date = date, status = SummaryStatusDb.PENDING.name))
            return false
        }

        val existing = dailySummaryRepository.getByDate(date) ?: DailySummaryEntity(date = date)
        dailySummaryRepository.upsert(existing.copy(status = SummaryStatusDb.GENERATING.name))

        return runCatching {
            val prompt = SummaryPromptBuilder.build(date, segments)
            val structured = SummaryJsonParser.parse(llmClient.summarize(prompt))
            dailySummaryRepository.upsert(SummaryJsonParser.toEntity(date, structured))
        }.onFailure {
            dailySummaryRepository.updateStatus(date, SummaryStatusDb.FAILED.name)
        }.isSuccess
    }
}
