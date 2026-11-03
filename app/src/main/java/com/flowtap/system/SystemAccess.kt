package com.flowtap.system

import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.flowtap.system.shizuku.ShizukuManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Special app-access settings FlowTap uses that aren't normal runtime permissions. */
enum class SpecialAccess {
    /** "Modify system settings": needed for brightness. */
    WriteSettings,

    /** "Do Not Disturb access": needed to switch the ringer to/from silent. */
    DoNotDisturb,

    /** "Display over other apps": lets automations open apps while FlowTap is in the background. */
    Overlay,
}

/**
 * Checks permissions, and with Shizuku can grant FlowTap's own special access silently,
 * so neither phone has to dig through settings screens.
 */
@Singleton
class SystemAccess @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val shizuku: ShizukuManager,
) {
    fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun has(access: SpecialAccess): Boolean = when (access) {
        SpecialAccess.WriteSettings -> Settings.System.canWrite(context)
        SpecialAccess.DoNotDisturb ->
            context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted
        SpecialAccess.Overlay -> Settings.canDrawOverlays(context)
    }

    /** True if FlowTap has [access], granting it via Shizuku first when possible. */
    suspend fun ensure(access: SpecialAccess): Boolean {
        if (has(access)) return true
        val pkg = context.packageName
        val command = when (access) {
            SpecialAccess.WriteSettings -> "appops set $pkg WRITE_SETTINGS allow"
            SpecialAccess.DoNotDisturb -> "cmd notification allow_dnd $pkg"
            SpecialAccess.Overlay -> "appops set $pkg SYSTEM_ALERT_WINDOW allow"
        }
        shizuku.exec(command) ?: return false
        return has(access)
    }

    /** The settings screen where the user can grant [access] by hand. */
    fun settingsAction(access: SpecialAccess): String = when (access) {
        SpecialAccess.WriteSettings -> Settings.ACTION_MANAGE_WRITE_SETTINGS
        SpecialAccess.DoNotDisturb -> Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS
        SpecialAccess.Overlay -> Settings.ACTION_MANAGE_OVERLAY_PERMISSION
    }

    /** Whether [settingsAction] takes a package: URI (DND access doesn't). */
    fun settingsTakesPackage(access: SpecialAccess): Boolean = access != SpecialAccess.DoNotDisturb
}
