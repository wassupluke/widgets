package com.wassupluke.widgets

import com.wassupluke.widgets.data.AlarmDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class AlarmDayTest {
    private val zone = ZoneId.of("UTC")
    private val locale = Locale.US
    // 2026-06-08T08:00Z is a Monday.
    private val now = Instant.parse("2026-06-08T08:00:00Z").toEpochMilli()

    private fun prefix(triggerIso: String): String? =
        AlarmDay.dayPrefix(
            Instant.parse(triggerIso).toEpochMilli(), now, zone, locale, "Tomorrow"
        )

    @Test fun todayHasNoPrefix() = assertNull(prefix("2026-06-08T20:00:00Z"))
    @Test fun pastSameDayHasNoPrefix() = assertNull(prefix("2026-06-08T06:00:00Z"))
    @Test fun tomorrow() = assertEquals("Tomorrow", prefix("2026-06-09T07:00:00Z"))
    @Test fun weekdayWithinWeek() = assertEquals("Thu", prefix("2026-06-11T07:00:00Z"))
    @Test fun exactlyOneWeekIsDate() = assertEquals("Jun 15", prefix("2026-06-15T07:00:00Z"))
    @Test fun beyondWeekIsDate() = assertEquals("Jun 18", prefix("2026-06-18T07:00:00Z"))
}
