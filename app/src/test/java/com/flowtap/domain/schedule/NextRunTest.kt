package com.flowtap.domain.schedule

import com.flowtap.domain.model.Trigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class NextRunTest {
    private val zone = ZoneId.of("Europe/London")

    // 2026-11-03 is a Tuesday.
    private fun at(day: Int, hour: Int, minute: Int = 0) = ZonedDateTime.of(2026, 11, day, hour, minute, 0, 0, zone)

    @Test
    fun `later today when the time hasn't passed`() {
        val trigger = Trigger.TimeOfDay("t", hour = 23, minute = 0)
        assertEquals(at(3, 23), trigger.nextRunAfter(at(3, 10)))
    }

    @Test
    fun `tomorrow when today's time has passed or is now`() {
        val trigger = Trigger.TimeOfDay("t", hour = 23, minute = 0)
        assertEquals(at(4, 23), trigger.nextRunAfter(at(3, 23)))
    }

    @Test
    fun `skips to the next selected day`() {
        val weekdays = Trigger.TimeOfDay("t", hour = 7, minute = 0, days = Trigger.TimeOfDay.WEEKDAYS)
        // Friday 6 Nov after 7am -> Monday 9 Nov.
        assertEquals(at(9, 7), weekdays.nextRunAfter(at(6, 8)))
    }

    @Test
    fun `same weekday next week when it's the only day and already passed`() {
        val tuesdays = Trigger.TimeOfDay("t", hour = 9, minute = 0, days = setOf(2))
        assertEquals(at(10, 9), tuesdays.nextRunAfter(at(3, 9, 30)))
    }

    @Test
    fun `no days means never`() {
        assertNull(Trigger.TimeOfDay("t", 9, 0, days = emptySet()).nextRunAfter(at(3, 9)))
    }
}
