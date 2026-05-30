package tech.echo.app.core.time

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class EchoDateFormatterTest {

    private val shanghai: ZoneId = ZoneId.of("Asia/Shanghai")
    private val clock: Clock = Clock.fixed(Instant.parse("2026-05-30T02:00:00Z"), shanghai)

    @Test
    fun todayKeyUsesLocalDate() {
        assertEquals("20260530", EchoDateFormatter.todayKey(clock))
    }

    @Test
    fun displayDateNamesTodayAndYesterday() {
        assertEquals("今天 5月30日", EchoDateFormatter.displayDate("20260530", clock))
        assertEquals("昨天 5月29日", EchoDateFormatter.displayDate("20260529", clock))
        assertEquals("5月28日", EchoDateFormatter.displayDate("20260528", clock))
    }

    @Test
    fun timeLabelUsesLocalTime() {
        assertEquals(
            "10:03",
            EchoDateFormatter.timeLabel(
                Instant.parse("2026-05-30T02:03:04Z").toEpochMilli(),
                shanghai,
            ),
        )
    }
}
