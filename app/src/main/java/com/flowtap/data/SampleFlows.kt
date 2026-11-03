package com.flowtap.data

import com.flowtap.domain.model.FlowAction
import com.flowtap.domain.model.FlowColor
import com.flowtap.domain.model.FlowIcon
import com.flowtap.domain.model.MessageStyle
import com.flowtap.domain.model.RingerMode
import com.flowtap.domain.model.TapFlow
import com.flowtap.domain.model.ToggleState
import com.flowtap.domain.model.Trigger

object SampleFlows {

    val bedtime = TapFlow(
        name = "Bedtime",
        icon = FlowIcon.Moon,
        color = FlowColor.Indigo,
        actions = listOf(
            FlowAction.SetRinger(RingerMode.Silent),
            FlowAction.SetWifi(ToggleState.Off),
            FlowAction.SetBrightness(percent = 10),
        ),
        triggers = listOf(
            Trigger.TimeOfDay(id = "bedtime-2300", hour = 23, minute = 0, enabled = false),
        ),
    )

    val driving = TapFlow(
        name = "Driving",
        icon = FlowIcon.Car,
        color = FlowColor.Teal,
        actions = listOf(
            FlowAction.SetBluetooth(ToggleState.On),
            FlowAction.SetRinger(RingerMode.Normal),
            FlowAction.OpenApp(packageName = "com.google.android.apps.maps", appLabel = "Google Maps"),
        ),
    )

    // Extra dummies so the phase 1 grid has something to show. Dropped when Room seeding lands.
    private val morning = TapFlow(
        name = "Good Morning",
        icon = FlowIcon.Sun,
        color = FlowColor.Amber,
        actions = listOf(
            FlowAction.SetRinger(RingerMode.Normal),
            FlowAction.SetWifi(ToggleState.On),
            FlowAction.SetBrightness(percent = 70),
            FlowAction.ShowMessage("Rise and shine ☀️"),
        ),
        triggers = listOf(
            Trigger.TimeOfDay(id = "morning-0700", hour = 7, minute = 0, days = Trigger.TimeOfDay.WEEKDAYS),
            Trigger.TimeOfDay(id = "morning-0900", hour = 9, minute = 0, days = Trigger.TimeOfDay.WEEKEND),
        ),
    )

    private val focus = TapFlow(
        name = "Focus",
        icon = FlowIcon.Headphones,
        color = FlowColor.Coral,
        actions = listOf(
            FlowAction.SetRinger(RingerMode.Vibrate),
            FlowAction.Delay(seconds = 2),
            FlowAction.ShowMessage("Focus mode on", MessageStyle.Notification),
        ),
    )

    private val flashlight = TapFlow(
        name = "Torch",
        icon = FlowIcon.Flashlight,
        color = FlowColor.Slate,
        actions = listOf(FlowAction.SetTorch(ToggleState.Toggle)),
    )

    val all: List<TapFlow> = listOf(bedtime, driving, morning, focus, flashlight)
}
