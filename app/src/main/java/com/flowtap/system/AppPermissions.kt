package com.flowtap.system

import android.Manifest
import android.os.Build
import android.provider.Settings
import com.flowtap.automation.AlarmScheduler
import com.flowtap.domain.model.FlowAction
import com.flowtap.domain.model.MessageStyle
import com.flowtap.domain.model.TapFlow
import com.flowtap.domain.model.Trigger
import com.flowtap.domain.run.Fix
import com.flowtap.system.shizuku.ShizukuManager
import javax.inject.Inject
import javax.inject.Singleton

/** Everything FlowTap may ask for, with the reason shown to the user. */
enum class AppPermission(val title: String, val rationale: String) {
    Notifications(
        "Notifications",
        "For “Notify” actions, and so automations can ask you to finish a step they couldn't do alone.",
    ),
    Sms("Send SMS", "For “Send SMS” actions. FlowTap only sends the messages you set up, to the numbers you chose."),
    Phone("Phone calls", "For “Phone call” actions, so the call starts without an extra tap."),
    NearbyDevices(
        "Nearby devices",
        "To turn Bluetooth on or off without Shizuku, and to notice when a paired device (like your car) connects.",
    ),
    WriteSettings("Modify system settings", "For “Brightness” actions."),
    DoNotDisturb("Do Not Disturb access", "Android requires this to switch the ringer to or from silent."),
    ExactAlarms("Alarms & reminders", "So schedules run at the exact minute instead of “sometime soon”."),
    Battery(
        "Unrestricted battery",
        "Stops Android from delaying or skipping automations to save power, especially overnight.",
    ),
    Overlay(
        "Display over other apps",
        "Optional. Lets automations open apps or start calls while FlowTap is closed, if Shizuku isn't running.",
    ),
}

data class PermissionState(
    val permission: AppPermission,
    val granted: Boolean,
    /** Not applicable on this Android version (e.g. granted automatically). Hidden from lists. */
    val applicable: Boolean,
    /** Flow names that use it; empty means nothing needs it right now. */
    val neededBy: List<String>,
    val fix: Fix?,
    /** Shizuku can grant it without leaving the app. */
    val shizukuCanGrant: Boolean,
)

@Singleton
class AppPermissions @Inject constructor(
    private val access: SystemAccess,
    private val notifier: Notifier,
    private val scheduler: AlarmScheduler,
    private val shizuku: ShizukuManager,
) {
    fun states(flows: List<TapFlow>): List<PermissionState> {
        val shizukuReady = shizuku.isReady
        return AppPermission.entries.map { p ->
            val users = flows.filter { flow -> needs(flow, p, shizukuReady) }.map { it.name }
            PermissionState(
                permission = p,
                granted = isGranted(p),
                applicable = isApplicable(p),
                neededBy = users,
                fix = fixFor(p),
                shizukuCanGrant = shizukuReady && p.specialAccess != null,
            )
        }.filter { it.applicable }
    }

    /**
     * Runtime permissions [flow] will need if run now, that aren't granted yet. Asked for up front
     * when the user taps the flow, so the run itself doesn't stop on a missing permission.
     */
    fun missingRuntimePermissions(flow: TapFlow): List<AppPermission> {
        val shizukuReady = shizuku.isReady
        return AppPermission.entries.filter { it.runtimePermission != null && !isGranted(it) && needs(flow, it, shizukuReady) }
    }

    /** The Android permission string behind [p], for runtime ones. */
    fun runtimePermissionOf(p: AppPermission): String? = p.runtimePermission

    /** Grants special access via Shizuku. True if it's granted afterwards. */
    suspend fun grantWithShizuku(p: AppPermission): Boolean {
        val special = p.specialAccess ?: return false
        return access.ensure(special)
    }

    private fun isGranted(p: AppPermission): Boolean {
        p.runtimePermission?.let { return access.hasPermission(it) && (p != AppPermission.Notifications || notifier.canPost) }
        p.specialAccess?.let { return access.has(it) }
        return when (p) {
            AppPermission.Notifications -> notifier.canPost
            AppPermission.ExactAlarms -> scheduler.canScheduleExact
            else -> true
        }
    }

    private fun isApplicable(p: AppPermission): Boolean = when (p) {
        AppPermission.NearbyDevices -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        AppPermission.ExactAlarms -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        else -> true
    }

    private fun fixFor(p: AppPermission): Fix? {
        p.runtimePermission?.let { return Fix.GrantPermissions(listOf(it)) }
        p.specialAccess?.let { return Fix.OpenSettings(access.settingsAction(it), forThisApp = access.settingsTakesPackage(it)) }
        return when (p) {
            AppPermission.Notifications -> Fix.OpenSettings(Settings.ACTION_APP_NOTIFICATION_SETTINGS, forThisApp = true)
            AppPermission.ExactAlarms ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Fix.OpenSettings(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, forThisApp = true)
                } else {
                    null
                }
            else -> null
        }
    }

    private fun needs(flow: TapFlow, p: AppPermission, shizukuReady: Boolean): Boolean {
        val automated = flow.triggers.any { it.enabled }
        return when (p) {
            AppPermission.Notifications ->
                automated || flow.actions.any { it is FlowAction.ShowMessage && it.style == MessageStyle.Notification }
            AppPermission.Sms -> flow.actions.any { it is FlowAction.SendSms }
            AppPermission.Phone -> flow.actions.any { it is FlowAction.PhoneCall }
            AppPermission.NearbyDevices ->
                flow.triggers.any { it is Trigger.BluetoothDevice && it.enabled } ||
                    (!shizukuReady && flow.actions.any { it is FlowAction.SetBluetooth })
            AppPermission.WriteSettings -> flow.actions.any { it is FlowAction.SetBrightness }
            AppPermission.DoNotDisturb -> flow.actions.any { it is FlowAction.SetRinger }
            AppPermission.ExactAlarms -> flow.triggers.any { it is Trigger.TimeOfDay && it.enabled }
            AppPermission.Battery -> automated
            // Only matters for automations that open things, when Shizuku can't do it for them.
            AppPermission.Overlay -> automated && !shizukuReady && flow.actions.any {
                it is FlowAction.OpenApp || it is FlowAction.OpenUrl || it is FlowAction.PhoneCall
            }
        }
    }
}

private val AppPermission.runtimePermission: String?
    get() = when (this) {
        AppPermission.Notifications -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else null
        AppPermission.Sms -> Manifest.permission.SEND_SMS
        AppPermission.Phone -> Manifest.permission.CALL_PHONE
        AppPermission.NearbyDevices -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Manifest.permission.BLUETOOTH_CONNECT else null
        else -> null
    }

private val AppPermission.specialAccess: SpecialAccess?
    get() = when (this) {
        AppPermission.WriteSettings -> SpecialAccess.WriteSettings
        AppPermission.DoNotDisturb -> SpecialAccess.DoNotDisturb
        AppPermission.Battery -> SpecialAccess.BatteryUnrestricted
        AppPermission.Overlay -> SpecialAccess.Overlay
        else -> null
    }
