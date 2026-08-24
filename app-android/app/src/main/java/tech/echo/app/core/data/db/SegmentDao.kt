package tech.echo.app.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SegmentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(segment: SegmentEntity)

    @Query("SELECT * FROM segment WHERE date = :date ORDER BY startTime ASC")
    fun observeByDate(date: String): Flow<List<SegmentEntity>>

    @Query("SELECT COUNT(*) FROM segment WHERE date = :date")
    fun observeCountByDate(date: String): Flow<Int>

    @Query("SELECT COALESCE(SUM(durationMs), 0) FROM segment WHERE date = :date")
    fun observeTotalDurationByDate(date: String): Flow<Long>

    @Query(
        "SELECT date AS date, COUNT(*) AS segmentCount, MAX(startTime) AS lastStartTime " +
            "FROM segment GROUP BY date ORDER BY date DESC"
    )
    fun observeDailyCounts(): Flow<List<DayCount>>

    @Query("DELETE FROM segment WHERE date < :beforeDate")
    suspend fun deleteOlderThan(beforeDate: String)

    @Query("SELECT * FROM segment WHERE status IN ('RECORDED', 'UPLOADING', 'TRANSCRIBING', 'FAILED')")
    suspend fun findUploadCandidates(): List<SegmentEntity>

    @Query("UPDATE segment SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query(
        "UPDATE segment SET transcriptText = :text, speakerLabel = :speakerLabel, " +
            "asrEngine = :asrEngine, asrElapsedMs = :asrElapsedMs, asrFallbackReason = :asrFallbackReason, " +
            "status = 'DONE' WHERE id = :id"
    )
    suspend fun markTranscribed(
        id: String,
        text: String,
        speakerLabel: String?,
        asrEngine: String?,
        asrElapsedMs: Long?,
        asrFallbackReason: String?,
    )

    @Query("UPDATE segment SET speakerPersonId = :personName WHERE id = :id")
    suspend fun claimSpeakerForSegment(id: String, personName: String)

    @Query(
        "UPDATE segment SET speakerPersonId = :personName " +
            "WHERE date = :date AND speakerLabel = :speakerLabel"
    )
    suspend fun claimSpeakerByDateAndLabel(date: String, speakerLabel: String, personName: String)

    @Query("SELECT * FROM segment WHERE date = :date AND status = 'DONE' ORDER BY startTime ASC")
    suspend fun getDoneByDate(date: String): List<SegmentEntity>

    @Query("SELECT * FROM segment WHERE date = :date ORDER BY startTime ASC")
    suspend fun getByDate(date: String): List<SegmentEntity>
}

data class DayCount(
    val date: String,
    val segmentCount: Int,
    val lastStartTime: Long,
)
