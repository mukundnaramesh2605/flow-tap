package com.flowtap.data.actions

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import com.flowtap.domain.model.FlowAction
import com.flowtap.domain.model.ToggleState
import com.flowtap.domain.run.RunContext
import com.flowtap.domain.run.StepResult
import com.flowtap.system.ActivityLauncher
import com.flowtap.system.SystemAccess
import com.flowtap.system.shizuku.ShizukuManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Wi-Fi and Bluetooth. Each tries, in order:
 * 1. The direct API, on Android versions that still allow it (Wi-Fi < 10, Bluetooth < 13).
 * 2. Shizuku, which toggles silently as the shell user.
 * 3. The system panel/settings screen, so the user can flip it (or a notification, for automations).
 */
class RadioActions @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val shizuku: ShizukuManager,
    private val access: SystemAccess,
    private val launcher: ActivityLauncher,
) {
    private val wifiManager get() = context.applicationContext.getSystemService(WifiManager::class.java)
    private val bluetoothAdapter: BluetoothAdapter? get() = context.getSystemService(BluetoothManager::class.java)?.adapter

    // --- Wi-Fi ---

    suspend fun wifi(action: FlowAction.SetWifi, run: RunContext): StepResult {
        val wifi = wifiManager ?: return StepResult.Failed("This device has no Wi-Fi")
        val target = action.state.resolve(current = wifi.isWifiEnabled)
        if (wifi.isWifiEnabled == target) return StepResult.Done
        val word = if (target) "on" else "off"

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            return if (wifi.setWifiEnabled(target)) StepResult.Done else StepResult.Failed("Android refused to turn Wi-Fi $word")
        }

        val state = if (target) "enabled" else "disabled"
        val svc = if (target) "enable" else "disable"
        if (shizukuToggle(listOf("cmd wifi set-wifi-enabled $state", "svc wifi $svc")) { wifi.isWifiEnabled == target }) {
            return StepResult.Done
        }

        // Android 10+ blocks apps from toggling Wi-Fi; the panel lets the user do it in one tap.
        return launcher.launch(
            Intent(Settings.Panel.ACTION_WIFI),
            what = "Wi-Fi settings",
            run = run,
            doneResult = StepResult.HandedOff("Opened the Wi-Fi panel — turn Wi-Fi $word there. Start Shizuku to do this silently."),
            notifyTitle = "${run.flowName}: tap to turn Wi-Fi $word",
        )
    }

    // --- Bluetooth ---

    suspend fun bluetooth(action: FlowAction.SetBluetooth, run: RunContext): StepResult {
        val adapter = bluetoothAdapter ?: return StepResult.Failed("This device has no Bluetooth")
        val target = action.state.resolve(current = adapter.isEnabled)
        if (adapter.isEnabled == target) return StepResult.Done
        val word = if (target) "on" else "off"
        // Android 12+ gates Bluetooth control behind the "Nearby devices" runtime permission.
        val hasConnect = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            access.hasPermission(Manifest.permission.BLUETOOTH_CONNECT)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && hasConnect) {
            @Suppress("DEPRECATION", "MissingPermission")
            val accepted = if (target) adapter.enable() else adapter.disable()
            if (accepted && awaitState { adapter.isEnabled == target }) return StepResult.Done
        }

        val verb = if (target) "enable" else "disable"
        if (shizukuToggle(listOf("cmd bluetooth_manager $verb", "svc bluetooth $verb")) { adapter.isEnabled == target }) {
            return StepResult.Done
        }

        // The enable prompt is a one-tap dialog; there's no equivalent for disabling, so open settings.
        val intent = if (target && hasConnect) {
            Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        } else {
            Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        }
        return launcher.launch(
            intent,
            what = "Bluetooth settings",
            run = run,
            doneResult = StepResult.HandedOff("Asked Android to turn Bluetooth $word — confirm on screen. Start Shizuku to do this silently."),
            notifyTitle = "${run.flowName}: tap to turn Bluetooth $word",
        )
    }

    // --- Shared ---

    /** Tries each command via Shizuku until [reached] holds. False if Shizuku is down or nothing worked. */
    private suspend fun shizukuToggle(commands: List<String>, reached: () -> Boolean): Boolean {
        if (!shizuku.isReady) return false
        for (command in commands) {
            val result = shizuku.exec(command) ?: return false
            if (result.ok && awaitState(reached)) return true
        }
        return false
    }

    /** Radios take a moment to change state; poll briefly. */
    private suspend fun awaitState(reached: () -> Boolean): Boolean =
        withTimeoutOrNull(STATE_TIMEOUT_MS) {
            while (!reached()) delay(POLL_MS)
            true
        } ?: false

    private fun ToggleState.resolve(current: Boolean): Boolean = when (this) {
        ToggleState.On -> true
        ToggleState.Off -> false
        ToggleState.Toggle -> !current
    }

    private companion object {
        const val STATE_TIMEOUT_MS = 6_000L
        const val POLL_MS = 250L
    }
}
