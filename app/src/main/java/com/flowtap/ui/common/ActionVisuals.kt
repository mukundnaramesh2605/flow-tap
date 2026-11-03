package com.flowtap.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import com.flowtap.domain.model.FlowAction
import com.flowtap.domain.model.MessageStyle
import com.flowtap.domain.model.RingerMode
import com.flowtap.domain.model.ToggleState

val FlowAction.title: String
    get() = when (this) {
        is FlowAction.OpenUrl -> "Open URL"
        is FlowAction.OpenApp -> "Open app"
        is FlowAction.SetWifi -> "Wi-Fi"
        is FlowAction.SetBluetooth -> "Bluetooth"
        is FlowAction.SetTorch -> "Flashlight"
        is FlowAction.SetRinger -> "Ringer"
        is FlowAction.SetBrightness -> "Brightness"
        is FlowAction.SendSms -> "Send SMS"
        is FlowAction.PhoneCall -> "Call"
        is FlowAction.ShowMessage -> if (style == MessageStyle.Toast) "Show toast" else "Notify"
        is FlowAction.Delay -> "Wait"
    }

/** One-line description of the configured action, e.g. "Wi-Fi off". */
val FlowAction.summary: String
    get() = when (this) {
        is FlowAction.OpenUrl -> url
        is FlowAction.OpenApp -> appLabel
        is FlowAction.SetWifi -> "Wi-Fi ${state.label}"
        is FlowAction.SetBluetooth -> "Bluetooth ${state.label}"
        is FlowAction.SetTorch -> "Flashlight ${state.label}"
        is FlowAction.SetRinger -> when (mode) {
            RingerMode.Silent -> "Ringer silent"
            RingerMode.Vibrate -> "Ringer vibrate"
            RingerMode.Normal -> "Ringer normal"
        }
        is FlowAction.SetBrightness -> "Brightness $percent%"
        is FlowAction.SendSms -> "Text $phoneNumber"
        is FlowAction.PhoneCall -> "Call $phoneNumber"
        is FlowAction.ShowMessage -> text
        is FlowAction.Delay -> "Wait ${seconds}s"
    }

val FlowAction.icon: ImageVector
    get() = when (this) {
        is FlowAction.OpenUrl -> Icons.Rounded.Language
        is FlowAction.OpenApp -> Icons.Rounded.Apps
        is FlowAction.SetWifi -> Icons.Rounded.Wifi
        is FlowAction.SetBluetooth -> Icons.Rounded.Bluetooth
        is FlowAction.SetTorch -> Icons.Rounded.FlashlightOn
        is FlowAction.SetRinger -> Icons.Rounded.NotificationsActive
        is FlowAction.SetBrightness -> Icons.Rounded.BrightnessMedium
        is FlowAction.SendSms -> Icons.Rounded.Sms
        is FlowAction.PhoneCall -> Icons.Rounded.Call
        is FlowAction.ShowMessage -> Icons.Rounded.ChatBubble
        is FlowAction.Delay -> Icons.Rounded.HourglassTop
    }

private val ToggleState.label: String
    get() = when (this) {
        ToggleState.On -> "on"
        ToggleState.Off -> "off"
        ToggleState.Toggle -> "toggle"
    }
