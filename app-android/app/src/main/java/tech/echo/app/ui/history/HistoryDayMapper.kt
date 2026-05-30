package tech.echo.app.ui.history

import tech.echo.app.core.data.db.DayCount
import tech.echo.app.core.data.db.SummaryStatusDb
import tech.echo.app.core.data.db.SummaryStatusRow
import tech.echo.app.core.model.DaySummaryItem
import tech.echo.app.core.model.SummaryStatus
import tech.echo.app.core.time.EchoDateFormatter
import java.time.Clock

object HistoryDayMapper {
    fun map(
        counts: List<DayCount>,
        statuses: List<SummaryStatusRow>,
        clock: Clock = Clock.systemDefaultZone(),
    ): List<DaySummaryItem> {
        val statusByDate = statuses.associateBy { it.date }
        return counts.map { count ->
            DaySummaryItem(
                date = count.date,
                displayDate = EchoDateFormatter.displayDate(count.date, clock),
                segmentCount = count.segmentCount,
                summaryStatus = statusByDate[count.date]?.status.toUiStatus(),
            )
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
