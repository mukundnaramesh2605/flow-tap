package com.flowtap.automation

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.flowtap.R
import com.flowtap.data.FlowRepository
import com.flowtap.data.run.FlowRunManager
import com.flowtap.domain.run.RunState
import com.flowtap.domain.run.StepResult
import com.flowtap.system.Notifier
import com.flowtap.ui.common.summary
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Runs automation-triggered flows with the screen off and the app closed. Being a foreground
 * service keeps the process alive; a partial wake lock keeps the CPU awake through Wait steps.
 */
@AndroidEntryPoint
class FlowRunService : Service() {

    @Inject lateinit var repository: FlowRepository
    @Inject lateinit var runManager: FlowRunManager
    @Inject lateinit var notifier: Notifier

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val active = mutableSetOf<Long>()
    private val names = mutableMapOf<Long, String>()
    private lateinit var wakeLock: PowerManager.WakeLock

    override fun onCreate() {
        super.onCreate()
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FlowTap:automation")
            .apply { setReferenceCounted(false) }
        scope.launch {
            runManager.states.collectLatest { updateProgressNotification(it) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must go foreground promptly after startForegroundService, before any I/O.
        goForeground(progressNotification("Starting automation…"))

        val flowId = intent?.getLongExtra(EXTRA_FLOW_ID, -1L) ?: -1L
        if (flowId < 0 || flowId in active) {
            stopIfIdle()
            return START_NOT_STICKY
        }
        active += flowId
        wakeLock.acquire(WAKE_LOCK_MAX_MS)
        scope.launch {
            try {
                val flow = repository.getFlow(flowId) ?: return@launch
                names[flowId] = flow.name
                val report = runManager.start(flow, interactive = false).await()
                postSummaryIfNeeded(report)
            } finally {
                active -= flowId
                names -= flowId
                stopIfIdle()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        if (wakeLock.isHeld) wakeLock.release()
        super.onDestroy()
    }

    private fun stopIfIdle() {
        if (active.isNotEmpty()) return
        if (wakeLock.isHeld) wakeLock.release()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun goForeground(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun updateProgressNotification(states: Map<Long, RunState>) {
        if (active.isEmpty()) return
        val lines = active.mapNotNull { id ->
            val name = names[id] ?: return@mapNotNull null
            when (val state = states[id]) {
                is RunState.Running -> "$name · step ${state.stepIndex + 1} of ${state.totalSteps}"
                is RunState.Finished -> "$name · finishing"
                null -> name
            }
        }
        if (lines.isEmpty()) return
        // Foreground notifications can always be updated, even without POST_NOTIFICATIONS on 13+.
        @Suppress("MissingPermission")
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, progressNotification(lines.joinToString("\n")))
    }

    private fun progressNotification(text: String): Notification =
        NotificationCompat.Builder(this, Notifier.CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_stat_flowtap)
            .setContentTitle("Running automation")
            .setContentText(text.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    /**
     * Hand-offs already posted their own tap-to-finish notification; this covers steps that were
     * blocked or failed, so an 11 PM run never fails without a trace.
     */
    private fun postSummaryIfNeeded(report: RunState.Finished) {
        val problems = report.issues.filter { it.result is StepResult.Blocked || it.result is StepResult.Failed }
        if (problems.isEmpty()) return
        val text = problems.joinToString("\n") { outcome ->
            val message = when (val r = outcome.result) {
                is StepResult.Blocked -> r.message
                is StepResult.Failed -> r.message
                else -> ""
            }
            "• ${outcome.action.summary}: $message"
        }
        notifier.postMessage(
            title = if (problems.size == 1) "${report.flowName}: 1 step didn't run" else "${report.flowName}: ${problems.size} steps didn't run",
            text = text + "\nOpen FlowTap and run it by hand to see Fix options.",
        )
    }

    companion object {
        const val EXTRA_FLOW_ID = "flow_id"
        private const val NOTIFICATION_ID = 1
        // Longest possible flow is a handful of hour-long waits; cap so a bug can't drain the battery.
        private const val WAKE_LOCK_MAX_MS = 2 * 60 * 60 * 1000L
    }
}
