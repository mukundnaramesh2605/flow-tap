package com.flowtap.data

import com.flowtap.domain.model.FlowAction
import com.flowtap.domain.model.FlowColor
import com.flowtap.domain.model.FlowIcon
import com.flowtap.domain.model.RingerMode
import com.flowtap.domain.model.TapFlow
import com.flowtap.domain.model.ToggleState
import com.flowtap.domain.model.Trigger

/** Inserted once, when the database is first created. */
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
            Trigger.TimeOfDay(id = "seed-bedtime-2300", hour = 23, minute = 0, enabled = false),
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

    val all: List<TapFlow> = listOf(bedtime, driving)
}
