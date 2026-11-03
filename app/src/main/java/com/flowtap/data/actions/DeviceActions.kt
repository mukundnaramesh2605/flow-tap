package com.flowtap.data.actions

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.flowtap.domain.model.FlowAction
import com.flowtap.domain.model.RingerMode
import com.flowtap.domain.model.ToggleState
import com.flowtap.domain.run.Fix
import com.flowtap.domain.run.StepResult
import com.flowtap.system.SpecialAccess
import com.flowtap.system.SystemAccess
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.math.pow
import kotlin.math.roundToInt

class DeviceActions @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val access: SystemAccess,
) {
    private val cameraManager get() = context.getSystemService(CameraManager::class.java)
    private val audioManager get() = context.getSystemService(AudioManager::class.java)

    // --- Flashlight. setTorchMode needs no permission. The torch goes off if FlowTap's process dies. ---

    suspend fun torch(action: FlowAction.SetTorch): StepResult {
        val cameraId = torchCameraId() ?: return StepResult.Failed("This phone has no flashlight")
        val turnOn = when (action.state) {
            ToggleState.On -> true
            ToggleState.Off -> false
            ToggleState.Toggle -> !(torchIsOn(cameraId) ?: false)
        }
        return try {
            cameraManager.setTorchMode(cameraId, turnOn)
            StepResult.Done
        } catch (_: CameraAccessException) {
            StepResult.Failed("The camera is in use by another app, so the flashlight is unavailable")
        } catch (e: IllegalArgumentException) {
            StepResult.Failed("Flashlight unavailable: ${e.message}")
        }
    }

    /** Prefers the rear camera's flash. */
    private fun torchCameraId(): String? = try {
        val withFlash = cameraManager.cameraIdList.filter {
            cameraManager.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
        withFlash.firstOrNull {
            cameraManager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) ==
                CameraCharacteristics.LENS_FACING_BACK
        } ?: withFlash.firstOrNull()
    } catch (_: CameraAccessException) {
        null
    }

    /** Android only reports torch state through a callback, which fires immediately on registration. */
    private suspend fun torchIsOn(cameraId: String): Boolean? = withTimeoutOrNull(1_000) {
        callbackFlow {
            val callback = object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(id: String, enabled: Boolean) {
                    if (id == cameraId) trySend(enabled)
                }

                override fun onTorchModeUnavailable(id: String) {
                    if (id == cameraId) trySend(false)
                }
            }
            cameraManager.registerTorchCallback(callback, Handler(Looper.getMainLooper()))
            awaitClose { cameraManager.unregisterTorchCallback(callback) }
        }.first()
    }

    // --- Ringer. Going to or from silent needs Do Not Disturb access on Android 7+. ---

    suspend fun ringer(action: FlowAction.SetRinger): StepResult {
        val target = when (action.mode) {
            RingerMode.Silent -> AudioManager.RINGER_MODE_SILENT
            RingerMode.Vibrate -> AudioManager.RINGER_MODE_VIBRATE
            RingerMode.Normal -> AudioManager.RINGER_MODE_NORMAL
        }
        if (audioManager.ringerMode == target) return StepResult.Done
        if (trySetRinger(target)) return StepResult.Done
        // Most likely missing DND access: grant it via Shizuku if we can, then retry once.
        if (access.ensure(SpecialAccess.DoNotDisturb) && trySetRinger(target)) return StepResult.Done
        return StepResult.Blocked(
            "Changing to or from silent needs Do Not Disturb access",
            Fix.OpenSettings(access.settingsAction(SpecialAccess.DoNotDisturb)),
        )
    }

    private fun trySetRinger(mode: Int): Boolean = try {
        audioManager.ringerMode = mode
        true
    } catch (_: SecurityException) {
        false
    }

    // --- Brightness. Needs "modify system settings". ---

    suspend fun brightness(action: FlowAction.SetBrightness): StepResult {
        if (!access.ensure(SpecialAccess.WriteSettings)) {
            return StepResult.Blocked(
                "Setting brightness needs permission to modify system settings",
                Fix.OpenSettings(access.settingsAction(SpecialAccess.WriteSettings), forThisApp = true),
            )
        }
        val resolver = context.contentResolver
        return try {
            // Adaptive brightness would immediately override a fixed level.
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, brightnessLevel(action.percent))
            StepResult.Done
        } catch (e: SecurityException) {
            StepResult.Failed("Android refused the brightness change: ${e.message}")
        }
    }
}

/**
 * Maps a slider percentage to the 1..255 system level. People perceive brightness roughly on a
 * power curve, so 50% means "half as bright looking", not half the backlight. Never 0, which some
 * devices treat as screen off.
 */
fun brightnessLevel(percent: Int): Int {
    val fraction = percent.coerceIn(0, 100) / 100.0
    return (1 + 254 * fraction.pow(2.2)).roundToInt().coerceIn(1, 255)
}
