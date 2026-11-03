package com.flowtap.ui.common

import com.flowtap.domain.model.Trigger
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

fun Trigger.describe(locale: Locale = Locale.getDefault()): String = when (this) {
    is Trigger.TimeOfDay -> "${timeLabel(locale)} · ${daysLabel(locale)}"
    is Trigger.BluetoothDevice -> eventLabel()
}

/** "When Car Audio connects". */
fun Trigger.BluetoothDevice.eventLabel(): String = when (event) {
    Trigger.ConnectionEvent.Connected -> "When $deviceName connects"
    Trigger.ConnectionEvent.Disconnected -> "When $deviceName disconnects"
}

fun Trigger.TimeOfDay.timeLabel(locale: Locale = Locale.getDefault()): String =
    LocalTime.of(hour, minute).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))

fun Trigger.TimeOfDay.daysLabel(locale: Locale = Locale.getDefault()): String = when (days) {
    Trigger.TimeOfDay.ALL_DAYS -> "Every day"
    Trigger.TimeOfDay.WEEKDAYS -> "Weekdays"
    Trigger.TimeOfDay.WEEKEND -> "Weekends"
    else -> daysOfWeek.joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) }
}
