package com.flowtap.data.actions

import com.flowtap.domain.model.FlowAction
import com.flowtap.domain.run.ActionExecutor
import com.flowtap.domain.run.RunContext
import com.flowtap.domain.run.StepResult
import kotlinx.coroutines.delay
import javax.inject.Inject

/** Routes each action to the class that knows how to run it on this device. */
class ActionDispatcher @Inject constructor(
    private val launch: LaunchActions,
    private val feedback: FeedbackActions,
    private val device: DeviceActions,
    private val communication: CommunicationActions,
    private val radio: RadioActions,
) : ActionExecutor {

    override suspend fun execute(action: FlowAction, context: RunContext): StepResult = when (action) {
        is FlowAction.OpenUrl -> launch.openUrl(action, context)
        is FlowAction.OpenApp -> launch.openApp(action, context)
        is FlowAction.SetWifi -> radio.wifi(action, context)
        is FlowAction.SetBluetooth -> radio.bluetooth(action, context)
        is FlowAction.SetTorch -> device.torch(action)
        is FlowAction.SetRinger -> device.ringer(action)
        is FlowAction.SetBrightness -> device.brightness(action)
        is FlowAction.SendSms -> communication.sendSms(action)
        is FlowAction.PhoneCall -> communication.call(action, context)
        is FlowAction.ShowMessage -> feedback.show(action, context)
        is FlowAction.Delay -> {
            delay(action.seconds * 1_000L)
            StepResult.Done
        }
    }
}
