package com.flowtap.domain.schedule

import com.flowtap.domain.model.Trigger
import java.time.ZonedDateTime

/**
 * The next moment strictly after [now] at which this schedule fires, or null if no days are
 * selected. Built from local date + time so DST shifts land on the intended wall-clock time.
 */
fun Trigger.TimeOfDay.nextRunAfter(now: ZonedDateTime): ZonedDateTime? {
    if (days.isEmpty()) return null
    for (offset in 0L..7L) {
        val date = now.toLocalDate().plusDays(offset)
        if (date.dayOfWeek.value !in days) continue
        val candidate = date.atTime(hour, minute).atZone(now.zone)
        if (candidate.isAfter(now)) return candidate
    }
    return null
}
