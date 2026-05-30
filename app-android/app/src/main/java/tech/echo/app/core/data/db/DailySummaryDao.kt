package tech.echo.app.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 每日整理数据访问对象。
 */
@Dao
interface DailySummaryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(summary: DailySummaryEntity)

    /** 某天的整理结果（Flow，详情页 collect 实时刷新）。 */
    @Query("SELECT * FROM daily_summary WHERE date = :date")
    fun observeByDate(date: String): Flow<DailySummaryEntity?>

    /** 某天整理结果（一次性，Worker 写前读旧值用）。 */
    @Query("SELECT * FROM daily_summary WHERE date = :date")
    suspend fun getByDate(date: String): DailySummaryEntity?

    /** 所有整理状态（历史列表 join 用：date → status）。 */
    @Query("SELECT date AS date, status AS status FROM daily_summary")
    fun observeAllStatus(): Flow<List<SummaryStatusRow>>

    /** 更新某天状态（整理中/失败时只改状态不动内容）。 */
    @Query("UPDATE daily_summary SET status = :status WHERE date = :date")
    suspend fun updateStatus(date: String, status: String)
}

/** 历史列表用：一天 + 整理状态。 */
data class SummaryStatusRow(
    val date: String,
    val status: String,
)
