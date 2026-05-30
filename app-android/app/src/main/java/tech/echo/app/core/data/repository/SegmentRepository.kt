package tech.echo.app.core.data.repository

import kotlinx.coroutines.flow.Flow
import tech.echo.app.core.data.db.DayCount
import tech.echo.app.core.data.db.SegmentDao
import tech.echo.app.core.data.db.SegmentEntity
import tech.echo.app.core.data.db.SegmentStatus
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 片段仓库：录音引擎落盘的写入口，UI 读取的统一出口。
 *
 * 录音侧：每切出一段调 [insertSegment]。
 * UI 侧：今天主页 collect [observeCount]/[observeTotalDuration]，历史列表 collect [observeDailyCounts]。
 */
@Singleton
class SegmentRepository @Inject constructor(
    private val dao: SegmentDao,
) {
    suspend fun insertSegment(segment: SegmentEntity) = dao.insert(segment)

    fun observeByDate(date: String): Flow<List<SegmentEntity>> = dao.observeByDate(date)

    fun observeCount(date: String): Flow<Int> = dao.observeCountByDate(date)

    fun observeTotalDuration(date: String): Flow<Long> = dao.observeTotalDurationByDate(date)

    fun observeDailyCounts(): Flow<List<DayCount>> = dao.observeDailyCounts()

    suspend fun deleteOlderThan(beforeDate: String) = dao.deleteOlderThan(beforeDate)

    // —— 阶段 2：上传转写 ——

    suspend fun findPendingUpload(limit: Int): List<SegmentEntity> =
        dao.findUploadCandidates()
            .sortedWith(compareBy<SegmentEntity> { it.uploadPriority() }.thenBy { it.startTime })
            .take(limit)

    suspend fun updateStatus(id: String, status: String) = dao.updateStatus(id, status)

    suspend fun markTranscribed(id: String, text: String, speakerLabel: String?) =
        dao.markTranscribed(id, text, speakerLabel)

    suspend fun claimSpeaker(
        date: String,
        segmentId: String,
        speakerLabel: String?,
        personName: String,
    ) {
        val name = personName.trim()
        if (name.isBlank()) return

        if (speakerLabel.isNullOrBlank()) {
            dao.claimSpeakerForSegment(segmentId, name)
        } else {
            dao.claimSpeakerByDateAndLabel(date, speakerLabel, name)
        }
    }

    suspend fun getDoneByDate(date: String): List<SegmentEntity> = dao.getDoneByDate(date)

    suspend fun getByDate(date: String): List<SegmentEntity> = dao.getByDate(date)

    private fun SegmentEntity.uploadPriority(): Int =
        when (status) {
            SegmentStatus.RECORDED.name -> 0
            SegmentStatus.UPLOADING.name,
            SegmentStatus.TRANSCRIBING.name -> 1
            else -> 2
        }
}
