package com.wassupluke.widgets.data

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

object AlarmDay {
    /**
     * Day prefix for an alarm at [triggerEpochMillis] relative to [nowEpochMillis], or null
     * when it is today (or already past today). [tomorrow] is the localized "tomorrow" word.
     */
    fun dayPrefix(
        triggerEpochMillis: Long,
        nowEpochMillis: Long,
        zone: ZoneId,
        locale: Locale,
        tomorrow: String
    ): String? {
        val today = Instant.ofEpochMilli(nowEpochMillis).atZone(zone).toLocalDate()
        val triggerDate = Instant.ofEpochMilli(triggerEpochMillis).atZone(zone).toLocalDate()
        return when (ChronoUnit.DAYS.between(today, triggerDate)) {
            in Long.MIN_VALUE..0L -> null
            1L -> tomorrow
            in 2L..6L -> triggerDate.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
            else -> triggerDate.format(DateTimeFormatter.ofPattern("MMM d", locale))
        }
    }
}
