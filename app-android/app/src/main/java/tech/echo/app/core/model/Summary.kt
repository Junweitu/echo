package tech.echo.app.core.model

/** 历史列表的一天条目（见 ui-design.md §4.2）。 */
data class DaySummaryItem(
    val date: String,          // yyyyMMdd
    val displayDate: String,   // "今天 5月29日" / "昨天 5月28日" / "5月27日"
    val segmentCount: Int,
    val summaryStatus: SummaryStatus,
)

enum class SummaryStatus {
    DONE,        // 已整理
    GENERATING,  // 整理中…
    PENDING,     // 待整理
    FAILED,      // 整理失败
}

/** 当天整理结果（见 ui-design.md §4.3 整理页）。 */
data class DailySummary(
    val date: String,
    val displayDate: String,
    val summaryStatus: SummaryStatus,
    val diary: String,
    val todos: List<String>,
    val inspirations: List<String>,
    val timeline: List<TimelineEntry>,
)

data class TimelineEntry(
    val time: String,    // "09:20"
    val person: String,  // "老婆" / "说话人 3"
    val topic: String,   // "周末安排"
)

/** 原始转写片段（见 ui-design.md §4.3 原始记录页）。 */
data class TranscriptSegment(
    val id: String,
    val speakerKey: String?,
    val time: String,          // "09:20"
    val speakerLabel: String,  // 显示名："我"/"老婆"/"说话人 3"
    val claimed: Boolean,      // 是否已认领（决定 chip 样式 + 是否可点改名）
    val audioPath: String,
    val durationMs: Long,
    val status: String,
    val text: String,
    val startTimeMs: Long = 0L,
)
