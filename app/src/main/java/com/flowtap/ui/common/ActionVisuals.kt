package com.flowtap.ui.common

import android.util.Patterns
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.ui.graphics.vector.ImageVector
import com.flowtap.domain.model.FlowAction
import com.flowtap.domain.model.MessageStyle
import com.flowtap.domain.model.RingerMode
import com.flowtap.domain.model.ToggleState

enum class ActionGroup(val label: String) {
    Apps("Apps & web"),
    Device("Device"),
    Communication("Calls & messages"),
    Feedback("Feedback"),
    Control("Timing"),
}

/** Every action a user can add, with how it looks in the picker and its starting parameters. */
enum class ActionKind(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val group: ActionGroup,
    val create: () -> FlowAction,
) {
    OpenApp("Open app", "Launch an installed app", Icons.Rounded.Apps, ActionGroup.Apps,
        { FlowAction.OpenApp(packageName = "", appLabel = "") }),
    OpenUrl("Open URL", "Open a web page in your browser", Icons.Rounded.Language, ActionGroup.Apps,
        { FlowAction.OpenUrl(url = "") }),
    Wifi("Wi-Fi", "Turn Wi-Fi on or off", Icons.Rounded.Wifi, ActionGroup.Device,
        { FlowAction.SetWifi(ToggleState.On) }),
    Bluetooth("Bluetooth", "Turn Bluetooth on or off", Icons.Rounded.Bluetooth, ActionGroup.Device,
        { FlowAction.SetBluetooth(ToggleState.On) }),
    Torch("Flashlight", "Turn the torch on or off", Icons.Rounded.FlashlightOn, ActionGroup.Device,
        { FlowAction.SetTorch(ToggleState.Toggle) }),
    Ringer("Ringer mode", "Silent, vibrate or normal", Icons.Rounded.NotificationsActive, ActionGroup.Device,
        { FlowAction.SetRinger(RingerMode.Silent) }),
    Brightness("Brightness", "Set screen brightness", Icons.Rounded.BrightnessMedium, ActionGroup.Device,
        { FlowAction.SetBrightness(percent = 50) }),
    Sms("Send SMS", "Text a preset message", Icons.Rounded.Sms, ActionGroup.Communication,
        { FlowAction.SendSms(phoneNumber = "", message = "") }),
    Call("Phone call", "Call a number", Icons.Rounded.Call, ActionGroup.Communication,
        { FlowAction.PhoneCall(phoneNumber = "") }),
    Toast("Show toast", "Pop up a short message", Icons.AutoMirrored.Rounded.Chat, ActionGroup.Feedback,
        { FlowAction.ShowMessage(text = "", style = MessageStyle.Toast) }),
    Notification("Notification", "Post a notification", Icons.Rounded.Notifications, ActionGroup.Feedback,
        { FlowAction.ShowMessage(text = "", style = MessageStyle.Notification) }),
    Delay("Wait", "Pause before the next action", Icons.Rounded.HourglassTop, ActionGroup.Control,
        { FlowAction.Delay(seconds = 5) }),
}

val FlowAction.kind: ActionKind
    get() = when (this) {
        is FlowAction.OpenUrl -> ActionKind.OpenUrl
        is FlowAction.OpenApp -> ActionKind.OpenApp
        is FlowAction.SetWifi -> ActionKind.Wifi
        is FlowAction.SetBluetooth -> ActionKind.Bluetooth
        is FlowAction.SetTorch -> ActionKind.Torch
        is FlowAction.SetRinger -> ActionKind.Ringer
        is FlowAction.SetBrightness -> ActionKind.Brightness
        is FlowAction.SendSms -> ActionKind.Sms
        is FlowAction.PhoneCall -> ActionKind.Call
        is FlowAction.ShowMessage -> if (style == MessageStyle.Toast) ActionKind.Toast else ActionKind.Notification
        is FlowAction.Delay -> ActionKind.Delay
    }

val FlowAction.title: String get() = kind.title

val FlowAction.icon: ImageVector get() = kind.icon

/** One-line description of the configured action, e.g. "Wi-Fi off". */
val FlowAction.summary: String
    get() = when (this) {
        is FlowAction.OpenUrl -> url.ifBlank { "No URL set" }
        is FlowAction.OpenApp -> appLabel.ifBlank { "No app chosen" }
        is FlowAction.SetWifi -> "Wi-Fi ${state.label}"
        is FlowAction.SetBluetooth -> "Bluetooth ${state.label}"
        is FlowAction.SetTorch -> "Flashlight ${state.label}"
        is FlowAction.SetRinger -> "Ringer ${mode.label}"
        is FlowAction.SetBrightness -> "Brightness $percent%"
        is FlowAction.SendSms -> if (phoneNumber.isBlank()) "No number set" else "Text $phoneNumber"
        is FlowAction.PhoneCall -> if (phoneNumber.isBlank()) "No number set" else "Call $phoneNumber"
        is FlowAction.ShowMessage -> text.ifBlank { "No message set" }
        is FlowAction.Delay -> "Wait ${seconds}s"
    }

/** Why this action can't be saved as configured, or null when it's ready to run. */
val FlowAction.validationError: String?
    get() = when (this) {
        is FlowAction.OpenUrl -> when {
            url.isBlank() -> "Enter a URL"
            !Patterns.WEB_URL.matcher(url).matches() -> "That doesn't look like a web address"
            else -> null
        }
        is FlowAction.OpenApp -> if (packageName.isBlank()) "Choose an app" else null
        is FlowAction.SendSms -> when {
            !phoneNumber.isPhoneLike() -> "Enter a phone number"
            message.isBlank() -> "Enter a message"
            else -> null
        }
        is FlowAction.PhoneCall -> if (!phoneNumber.isPhoneLike()) "Enter a phone number" else null
        is FlowAction.ShowMessage -> if (text.isBlank()) "Enter a message" else null
        is FlowAction.Delay -> if (seconds !in 1..MAX_DELAY_SECONDS) "Pick 1 to $MAX_DELAY_SECONDS seconds" else null
        is FlowAction.SetBrightness -> if (percent !in 0..100) "Pick 0 to 100%" else null
        is FlowAction.SetWifi, is FlowAction.SetBluetooth, is FlowAction.SetTorch, is FlowAction.SetRinger -> null
    }

const val MAX_DELAY_SECONDS = 3600

private fun String.isPhoneLike(): Boolean = count { it.isDigit() } >= 3 && all { it.isDigit() || it in "+-() " }

val ToggleState.label: String
    get() = when (this) {
        ToggleState.On -> "on"
        ToggleState.Off -> "off"
        ToggleState.Toggle -> "toggle"
    }

val RingerMode.label: String
    get() = when (this) {
        RingerMode.Silent -> "silent"
        RingerMode.Vibrate -> "vibrate"
        RingerMode.Normal -> "normal"
    }
