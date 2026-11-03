package com.flowtap.automation

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.IntentCompat
import com.flowtap.di.ApplicationScope
import com.flowtap.domain.model.Trigger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Runs [block] off the main thread while keeping the broadcast alive. Receivers get ~10 seconds;
 * anything longer (the flow itself) is handed to [FlowRunService].
 */
private fun BroadcastReceiver.runAsync(scope: CoroutineScope, tag: String, block: suspend () -> Unit) {
    val pending = goAsync()
    scope.launch {
        try {
            withTimeoutOrNull(9_000) { block() } ?: Log.w(tag, "Timed out handling broadcast")
        } catch (e: Exception) {
            Log.e(tag, "Broadcast handling failed", e)
        } finally {
            pending.finish()
        }
    }
}

/** Fires when a scheduled time trigger is due. */
@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {
    @Inject lateinit var engine: AutomationEngine
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val flowId = intent.getLongExtra(EXTRA_FLOW_ID, -1)
        val triggerId = intent.getStringExtra(EXTRA_TRIGGER_ID) ?: return
        if (flowId < 0) return
        runAsync(scope, "AlarmReceiver") { engine.onAlarm(flowId, triggerId) }
    }

    companion object {
        const val ACTION_FIRE = "com.flowtap.action.TRIGGER_FIRED"
        const val EXTRA_FLOW_ID = "flow_id"
        const val EXTRA_TRIGGER_ID = "trigger_id"
    }
}

/**
 * Alarms don't survive a reboot, and clock/timezone changes shift every "next run". Re-arm them all.
 * Also re-arms when the user grants exact-alarm access and after app updates.
 */
@AndroidEntryPoint
class RescheduleReceiver : BroadcastReceiver() {
    @Inject lateinit var engine: AutomationEngine
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED) return
        runAsync(scope, "RescheduleReceiver") { engine.rescheduleAll() }
    }

    private companion object {
        // The exact-alarm action is only ever sent on Android 12+; matching its string on older versions is harmless.
        @SuppressLint("InlinedApi")
        val HANDLED = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
    }
}

/**
 * Paired-device connects/disconnects. These broadcasts are exempt from Android's implicit-broadcast
 * limits and, because they need BLUETOOTH_CONNECT, also allow starting the run service from the background.
 */
@AndroidEntryPoint
class BluetoothTriggerReceiver : BroadcastReceiver() {
    @Inject lateinit var engine: AutomationEngine
    @Inject lateinit var cooldown: TriggerCooldown
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val event = when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> Trigger.ConnectionEvent.Connected
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> Trigger.ConnectionEvent.Disconnected
            else -> return
        }
        val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            ?: return
        // Reading the address needs no permission; the broadcast itself requires BLUETOOTH_CONNECT.
        val address = device.address ?: return
        // Some car kits drop and re-establish the link a few times in a row.
        if (!cooldown.tryAcquire("bt/$address/$event")) return
        runAsync(scope, "BluetoothTriggerReceiver") { engine.onBluetoothEvent(address, event) }
    }
}
