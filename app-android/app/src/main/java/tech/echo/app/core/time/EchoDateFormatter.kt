package tech.echo.app.core.time

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** echo 内部统一日期/时间展示工具。 */
object EchoDateFormatter {
    private val dateKeyFormatter: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE
    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)

    fun todayKey(clock: Clock = Clock.systemDefaultZone()): String =
        LocalDate.now(clock).format(dateKeyFormatter)

    fun yesterdayKey(clock: Clock = Clock.systemDefaultZone()): String =
        LocalDate.now(clock).minusDays(1).format(dateKeyFormatter)

    fun displayDate(date: String, clock: Clock = Clock.systemDefaultZone()): String {
        val day = parseDate(date) ?: return date
        val today = LocalDate.now(clock)
        val short = "${day.monthValue}月${day.dayOfMonth}日"
        return when (day) {
            today -> "今天 $short"
            today.minusDays(1) -> "昨天 $short"
            else -> short
        }
    }

    fun timeLabel(epochMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(epochMs)
            .atZone(zoneId)
            .toLocalTime()
            .format(timeFormatter)

    private fun parseDate(date: String): LocalDate? =
        runCatching { LocalDate.parse(date, dateKeyFormatter) }.getOrNull()
}
