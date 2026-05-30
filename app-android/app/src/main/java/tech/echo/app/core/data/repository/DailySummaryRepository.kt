package tech.echo.app.core.data.repository

import kotlinx.coroutines.flow.Flow
import tech.echo.app.core.data.db.DailySummaryDao
import tech.echo.app.core.data.db.DailySummaryEntity
import tech.echo.app.core.data.db.SummaryStatusRow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 每日整理仓库：整理 Worker 的写入口，详情/历史页的读出口。
 */
@Singleton
class DailySummaryRepository @Inject constructor(
    private val dao: DailySummaryDao,
) {
    suspend fun upsert(summary: DailySummaryEntity) = dao.upsert(summary)

    fun observeByDate(date: String): Flow<DailySummaryEntity?> = dao.observeByDate(date)

    suspend fun getByDate(date: String): DailySummaryEntity? = dao.getByDate(date)

    fun observeAllStatus(): Flow<List<SummaryStatusRow>> = dao.observeAllStatus()

    suspend fun updateStatus(date: String, status: String) = dao.updateStatus(date, status)
}
