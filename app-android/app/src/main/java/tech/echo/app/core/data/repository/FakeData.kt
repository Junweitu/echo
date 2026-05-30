package tech.echo.app.core.data.repository

import tech.echo.app.core.model.DailySummary
import tech.echo.app.core.model.DaySummaryItem
import tech.echo.app.core.model.SummaryStatus
import tech.echo.app.core.model.TimelineEntry
import tech.echo.app.core.model.TranscriptSegment

/**
 * 假数据源：UI 阶段填充三屏，内容对齐 ui-mockups。
 * 阶段 2 接入 Room + 云端后由真实仓库替换。
 */
object FakeData {

    /** 历史列表（见 ui-design.md §4.2 / 02 号图）。 */
    val historyDays: List<DaySummaryItem> = listOf(
        DaySummaryItem("20260529", "今天 5月29日", 8, SummaryStatus.DONE),
        DaySummaryItem("20260528", "昨天 5月28日", 15, SummaryStatus.DONE),
        DaySummaryItem("20260527", "5月27日", 6, SummaryStatus.GENERATING),
        DaySummaryItem("20260526", "5月26日", 3, SummaryStatus.DONE),
    )

    /** 当天整理结果（见 ui-design.md §4.3 整理页）。 */
    val dailySummary: DailySummary = DailySummary(
        date = "20260529",
        displayDate = "5月29日",
        summaryStatus = SummaryStatus.DONE,
        diary = "今天上午和老婆讨论了周末安排，下午和张三确认了项目报价。",
        todos = listOf(
            "给张三回邮件确认报价",
            "周五前订机票",
        ),
        inspirations = listOf(
            "可以做一个语音整理的 App",
        ),
        timeline = listOf(
            TimelineEntry("09:20", "老婆", "周末安排"),
            TimelineEntry("14:05", "张三", "项目报价"),
        ),
    )

    /** 当天原始转写片段（见 ui-design.md §4.3 原始记录页）。 */
    val transcriptSegments: List<TranscriptSegment> = listOf(
        TranscriptSegment("fake-1", "A", "09:20", "我", true, "", 0, "DONE", "这周末想带孩子去郊外玩玩。"),
        TranscriptSegment("fake-2", "B", "09:21", "老婆", true, "", 0, "DONE", "好啊，那订周六的吧，周日要回老家。"),
        TranscriptSegment("fake-3", "C", "14:05", "Speaker C", false, "", 0, "DONE", "关于报价我们这边需要再核算一下成本。"),
    )

    fun summaryByDate(date: String): DailySummary = dailySummary

    fun segmentsByDate(date: String): List<TranscriptSegment> = transcriptSegments
}
