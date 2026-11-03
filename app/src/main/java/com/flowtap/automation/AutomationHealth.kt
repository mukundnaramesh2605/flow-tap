package com.flowtap.automation

import android.Manifest
import android.os.Build
import android.provider.Settings
import com.flowtap.domain.model.FlowAction
import com.flowtap.domain.model.TapFlow
import com.flowtap.domain.model.Trigger
import com.flowtap.domain.run.Fix
import com.flowtap.system.Notifier
import com.flowtap.system.SpecialAccess
import com.flowtap.system.SystemAccess
import com.flowtap.system.shizuku.ShizukuManager
import com.flowtap.system.shizuku.ShizukuStatus
import javax.inject.Inject
import javax.inject.Singleton

/** Something that will make automations late, flaky, or need a tap. Listed on the Automations screen. */
data class HealthIssue(
    val kind: Kind,
    val title: String,
    val detail: String,
    /** What the main button does. Null when the user has to act outside FlowTap (e.g. start Shizuku). */
    val fix: Fix?,
    /** True when Shizuku can fix it without leaving the app. */
    val silentFix: Boolean = false,
) {
    enum class Kind { BatteryOptimized, InexactAlarms, NotificationsOff, BluetoothPermission, ShizukuDown }
}

@Singleton
class AutomationHealth @Inject constructor(
    private val access: SystemAccess,
    private val scheduler: AlarmScheduler,
    private val notifier: Notifier,
    private val shizuku: ShizukuManager,
) {
    /** Checks only what matters for the given flows' enabled triggers. Cheap; call on resume. */
    fun check(flows: List<TapFlow>): List<HealthIssue> {
        val triggers = flows.flatMap { it.triggers }.filter { it.enabled }
        if (triggers.isEmpty()) return emptyList()
        val shizukuReady = shizuku.refresh() == ShizukuStatus.Ready

        return buildList {
            if (!access.has(SpecialAccess.BatteryUnrestricted)) {
                add(
                    HealthIssue(
                        HealthIssue.Kind.BatteryOptimized,
                        title = "Battery optimization is on",
                        detail = "Android may delay or skip automations to save battery, especially overnight. " +
                            "Allowing FlowTap to run unrestricted keeps them on time. It only wakes for your triggers.",
                        fix = Fix.OpenSettings(access.settingsAction(SpecialAccess.BatteryUnrestricted), forThisApp = true),
                        silentFix = shizukuReady,
                    ),
                )
            }
            if (triggers.any { it is Trigger.TimeOfDay } && !scheduler.canScheduleExact &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            ) {
                add(
                    HealthIssue(
                        HealthIssue.Kind.InexactAlarms,
                        title = "Exact alarms are off",
                        detail = "Without “Alarms & reminders” access, schedules can run several minutes late.",
                        fix = Fix.OpenSettings(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, forThisApp = true),
                    ),
                )
            }
            if (!notifier.canPost) {
                add(
                    HealthIssue(
                        HealthIssue.Kind.NotificationsOff,
                        title = "Notifications are off",
                        detail = "When a step can't finish on its own, FlowTap asks via a notification. " +
                            "Without them, those steps fail silently.",
                        fix = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            !access.hasPermission(Manifest.permission.POST_NOTIFICATIONS)
                        ) {
                            Fix.GrantPermissions(listOf(Manifest.permission.POST_NOTIFICATIONS))
                        } else {
                            Fix.OpenSettings(Settings.ACTION_APP_NOTIFICATION_SETTINGS, forThisApp = true)
                        },
                    ),
                )
            }
            if (triggers.any { it is Trigger.BluetoothDevice } && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                !access.hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
            ) {
                add(
                    HealthIssue(
                        HealthIssue.Kind.BluetoothPermission,
                        title = "Bluetooth triggers can't fire",
                        detail = "Android only tells FlowTap about device connections with the Nearby devices permission.",
                        fix = Fix.GrantPermissions(listOf(Manifest.permission.BLUETOOTH_CONNECT)),
                    ),
                )
            }
            val automatedFlows = flows.filter { flow -> flow.triggers.any { it.enabled } }
            if (!shizukuReady && automatedFlows.any { it.actions.any(FlowAction::wantsShizukuInBackground) }) {
                add(
                    HealthIssue(
                        HealthIssue.Kind.ShizukuDown,
                        title = "Shizuku isn't connected",
                        detail = "Wi-Fi, Bluetooth and open-app steps will post a notification to tap instead of " +
                            "running silently. Shizuku stops on every reboot — open it and start it again.",
                        fix = null,
                    ),
                )
            }
        }
    }

    /** Fixes [issue] silently via Shizuku when possible. Returns true if it's resolved. */
    suspend fun fixSilently(issue: HealthIssue): Boolean = when (issue.kind) {
        HealthIssue.Kind.BatteryOptimized -> access.ensure(SpecialAccess.BatteryUnrestricted)
        else -> false
    }
}

/** Steps that run silently in the background only with Shizuku. */
private fun FlowAction.wantsShizukuInBackground(): Boolean = when (this) {
    is FlowAction.SetWifi, is FlowAction.SetBluetooth, is FlowAction.OpenApp,
    is FlowAction.OpenUrl, is FlowAction.PhoneCall -> true
    else -> false
}
