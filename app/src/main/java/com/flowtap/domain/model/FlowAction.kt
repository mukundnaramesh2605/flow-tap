package com.flowtap.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ToggleState { On, Off, Toggle }

@Serializable
enum class RingerMode { Silent, Vibrate, Normal }

@Serializable
enum class MessageStyle { Toast, Notification }

/**
 * One step in a flow. Serial names are part of the export format, so keep them stable.
 */
@Serializable
sealed interface FlowAction {

    @Serializable
    @SerialName("open_url")
    data class OpenUrl(val url: String) : FlowAction

    @Serializable
    @SerialName("open_app")
    data class OpenApp(val packageName: String, val appLabel: String) : FlowAction

    @Serializable
    @SerialName("wifi")
    data class SetWifi(val state: ToggleState) : FlowAction

    @Serializable
    @SerialName("bluetooth")
    data class SetBluetooth(val state: ToggleState) : FlowAction

    @Serializable
    @SerialName("torch")
    data class SetTorch(val state: ToggleState) : FlowAction

    @Serializable
    @SerialName("ringer")
    data class SetRinger(val mode: RingerMode) : FlowAction

    /** [percent] is 0..100 of the system brightness range. */
    @Serializable
    @SerialName("brightness")
    data class SetBrightness(val percent: Int) : FlowAction

    @Serializable
    @SerialName("sms")
    data class SendSms(val phoneNumber: String, val message: String) : FlowAction

    @Serializable
    @SerialName("call")
    data class PhoneCall(val phoneNumber: String) : FlowAction

    @Serializable
    @SerialName("message")
    data class ShowMessage(val text: String, val style: MessageStyle = MessageStyle.Toast) : FlowAction

    @Serializable
    @SerialName("delay")
    data class Delay(val seconds: Int) : FlowAction
}
