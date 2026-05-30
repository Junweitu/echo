package tech.echo.app.ui.history

import org.junit.Assert.assertEquals
import org.junit.Test
import tech.echo.app.core.data.db.DayCount
import tech.echo.app.core.data.db.SummaryStatusDb
import tech.echo.app.core.data.db.SummaryStatusRow
import tech.echo.app.core.model.SummaryStatus
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class HistoryDayMapperTest {

    private val clock = Clock.fixed(
        Instant.parse("2026-05-30T02:00:00Z"),
        ZoneId.of("Asia/Shanghai"),
    )

    @Test
    fun combinesSegmentCountsWithSummaryStatusRows() {
        val days = HistoryDayMapper.map(
            counts = listOf(
                DayCount("20260530", 2, 100L),
                DayCount("20260529", 1, 50L),
                DayCount("20260528", 3, 10L),
            ),
            statuses = listOf(
                SummaryStatusRow("20260530", SummaryStatusDb.DONE.name),
                SummaryStatusRow("20260529", SummaryStatusDb.GENERATING.name),
                SummaryStatusRow("20260528", SummaryStatusDb.FAILED.name),
            ),
            clock = clock,
        )

        assertEquals("今天 5月30日", days[0].displayDate)
        assertEquals(SummaryStatus.DONE, days[0].summaryStatus)
        assertEquals("昨天 5月29日", days[1].displayDate)
        assertEquals(SummaryStatus.GENERATING, days[1].summaryStatus)
        assertEquals(SummaryStatus.FAILED, days[2].summaryStatus)
    }
}
