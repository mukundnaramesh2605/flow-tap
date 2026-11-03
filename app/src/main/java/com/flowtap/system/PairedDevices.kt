package com.flowtap.system

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class PairedDevice(val address: String, val name: String)

@Singleton
class PairedDevices @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val access: SystemAccess,
) {
    /** Needed on Android 12+ to list paired devices and to receive their connect broadcasts. */
    val permission: String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Manifest.permission.BLUETOOTH_CONNECT else null

    val hasPermission: Boolean get() = permission == null || access.hasPermission(permission)

    /** Paired devices sorted by name, or null if Bluetooth is missing or the permission isn't granted. */
    @SuppressLint("MissingPermission") // checked via hasPermission
    fun list(): List<PairedDevice>? {
        if (!hasPermission) return null
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return null
        return try {
            adapter.bondedDevices.orEmpty()
                .map { PairedDevice(it.address, it.name?.takeIf(String::isNotBlank) ?: it.address) }
                .sortedBy { it.name.lowercase() }
        } catch (_: SecurityException) {
            null
        }
    }
}
