package tech.echo.app.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 片段数据访问对象。
 *
 * 计数/时长用 Flow 暴露，UI 层 collect 后随落盘实时刷新"已记录 N 段·约 X 分"。
 */
@Dao
interface SegmentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(segment: SegmentEntity)

    /** 某天的全部片段，按开始时间正序（详情页"原始记录"用）。 */
    @Query("SELECT * FROM segment WHERE date = :date ORDER BY startTime ASC")
    fun observeByDate(date: String): Flow<List<SegmentEntity>>

    /** 某天片段数（Flow，实时驱动今天主页计数）。 */
    @Query("SELECT COUNT(*) FROM segment WHERE date = :date")
    fun observeCountByDate(date: String): Flow<Int>

    /** 某天总时长毫秒（Flow，可能为 null 当天无片段时按 0 处理）。 */
    @Query("SELECT COALESCE(SUM(durationMs), 0) FROM segment WHERE date = :date")
    fun observeTotalDurationByDate(date: String): Flow<Long>

    /** 所有有片段的日期 + 每天计数，按日期倒序（历史列表用）。 */
    @Query(
        "SELECT date AS date, COUNT(*) AS segmentCount, MAX(startTime) AS lastStartTime " +
            "FROM segment GROUP BY date ORDER BY date DESC"
    )
    fun observeDailyCounts(): Flow<List<DayCount>>

    /** 清理某天之前的片段（配合 7 天保留策略，阶段 1 暂不调度）。 */
    @Query("DELETE FROM segment WHERE date < :beforeDate")
    suspend fun deleteOlderThan(beforeDate: String)

    // —— 阶段 2：上传转写 ——

    /** 待上传/重试候选。仓库层负责排序：新录音优先，旧失败稍后重试。 */
    @Query("SELECT * FROM segment WHERE status IN ('RECORDED', 'UPLOADING', 'TRANSCRIBING', 'FAILED')")
    suspend fun findUploadCandidates(): List<SegmentEntity>

    /** 更新片段状态（上传中/转写中/失败的状态流转）。 */
    @Query("UPDATE segment SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    /** 转写成功：写回文本 + 说话人标签，置 done。 */
    @Query(
        "UPDATE segment SET transcriptText = :text, speakerLabel = :speakerLabel, " +
            "status = 'DONE' WHERE id = :id"
    )
    suspend fun markTranscribed(id: String, text: String, speakerLabel: String?)

    /** 手动认领单段。阶段 3 person 表落地前，speakerPersonId 暂存显示名。 */
    @Query("UPDATE segment SET speakerPersonId = :personName WHERE id = :id")
    suspend fun claimSpeakerForSegment(id: String, personName: String)

    /** 手动认领同一天同一 ASR speakerLabel 的片段。 */
    @Query(
        "UPDATE segment SET speakerPersonId = :personName " +
            "WHERE date = :date AND speakerLabel = :speakerLabel"
    )
    suspend fun claimSpeakerByDateAndLabel(date: String, speakerLabel: String, personName: String)

    /** 某天 status=done 的片段（每日整理取数据用），按时间正序。 */
    @Query("SELECT * FROM segment WHERE date = :date AND status = 'DONE' ORDER BY startTime ASC")
    suspend fun getDoneByDate(date: String): List<SegmentEntity>

    /** 某天全部片段（详情页"原始记录"展示，含未转写的）。 */
    @Query("SELECT * FROM segment WHERE date = :date ORDER BY startTime ASC")
    suspend fun getByDate(date: String): List<SegmentEntity>
}

/** 历史列表聚合行：一天 + 片段数。 */
data class DayCount(
    val date: String,
    val segmentCount: Int,
    val lastStartTime: Long,
)
