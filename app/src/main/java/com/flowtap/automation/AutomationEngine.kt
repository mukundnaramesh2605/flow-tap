package com.flowtap.automation

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.flowtap.data.FlowRepository
import com.flowtap.di.ApplicationScope
import com.flowtap.domain.model.Trigger
import com.flowtap.system.Notifier
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Glue between triggers and runs: keeps alarms in sync with the database, and turns a fired
 * trigger into a background run in [FlowRunService].
 */
@Singleton
class AutomationEngine @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: FlowRepository,
    private val scheduler: AlarmScheduler,
    private val notifier: Notifier,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    /** Call once per process. Any edit, import, delete or toggle reschedules automatically. */
    fun start() {
        scope.launch {
            repository.observeFlows()
                .distinctUntilChangedBy { flows -> flows.map { it.id to it.triggers } }
                .collect { scheduler.reschedule(it) }
        }
    }

    /** For boot, clock changes and app updates, where alarms may have been wiped. */
    suspend fun rescheduleAll() = scheduler.reschedule(repository.getFlows())

    /** An alarm went off: line up the trigger's next occurrence, then run the flow if still enabled. */
    suspend fun onAlarm(flowId: Long, triggerId: String) {
        scheduler.reschedule(repository.getFlows(), justFired = flowId to triggerId)
        val flow = repository.getFlow(flowId) ?: return
        val trigger = flow.triggers.find { it.id == triggerId } as? Trigger.TimeOfDay ?: return
        if (!trigger.enabled) return
        startRun(flowId, flow.name)
    }

    /** A paired device connected or disconnected; run every flow listening for that. */
    suspend fun onBluetoothEvent(address: String, event: Trigger.ConnectionEvent) {
        val flows = repository.getFlows().filter { flow ->
            flow.triggers.any {
                it is Trigger.BluetoothDevice && it.enabled && it.event == event && it.address.equals(address, ignoreCase = true)
            }
        }
        flows.forEach { startRun(it.id, it.name) }
    }

    private fun startRun(flowId: Long, flowName: String) {
        val intent = Intent(context, FlowRunService::class.java).putExtra(FlowRunService.EXTRA_FLOW_ID, flowId)
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (e: IllegalStateException) {
            // Android 12+ refuses background service starts outside its exemptions (exact alarms,
            // Bluetooth broadcasts, battery-exempt apps). Don't fail silently: let the user run it.
            Log.e(TAG, "Couldn't start background run for flow $flowId", e)
            notifier.postActionNeeded(
                title = "Tap to run $flowName",
                text = "Android blocked FlowTap from running this in the background. " +
                    "Turning off battery optimization for FlowTap usually fixes this.",
                tapIntent = RunFlowActivity.intent(context, flowId),
            )
        }
    }

    private companion object {
        const val TAG = "AutomationEngine"
    }
}
