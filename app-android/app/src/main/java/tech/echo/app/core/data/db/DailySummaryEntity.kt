package tech.echo.app.core.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * 每日整理结果实体（见 design.md §5.2 / §8）。
 *
 * 一天一条，date 为主键。四类产出：日记（纯文本）、待办/灵感（字符串列表）、
 * 时间线（结构化条目列表）。列表字段经 [Converters] 序列化为 JSON 存单列。
 */
@Entity(tableName = "daily_summary")
data class DailySummaryEntity(
    @PrimaryKey val date: String,          // yyyyMMdd
    val diary: String = "",
    val todos: List<String> = emptyList(),
    val inspirations: List<String> = emptyList(),
    val timeline: List<TimelineEntryData> = emptyList(),
    val status: String = SummaryStatusDb.PENDING.name,
    val generatedAt: Long = 0L,            // 整理完成的 epoch ms
)

/** 时间线条目（可序列化，存进 daily_summary.timeline JSON 列）。 */
@Serializable
data class TimelineEntryData(
    val time: String,    // "09:20"
    val person: String,  // 说话人真名或编号
    val topic: String,   // 一句话主题
)

/**
 * 整理状态（落库用）。与 UI 层 model.SummaryStatus 对应，分开避免 UI 依赖 db。
 */
enum class SummaryStatusDb {
    PENDING,     // 待整理
    GENERATING,  // 整理中
    DONE,        // 已整理
    FAILED,      // 整理失败
}
