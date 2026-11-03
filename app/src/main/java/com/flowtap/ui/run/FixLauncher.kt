package com.flowtap.ui.run

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.flowtap.domain.run.Fix

/** Returns a handler that carries out a [Fix]: a permission prompt or the right settings screen. */
@Composable
fun rememberFixLauncher(): (Fix) -> Unit {
    val context = LocalContext.current
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
    return remember(context, permissions) {
        { fix ->
            when (fix) {
                is Fix.GrantPermissions -> permissions.launch(fix.permissions.toTypedArray())
                is Fix.OpenSettings -> context.openSettings(fix)
            }
        }
    }
}

private fun Context.openSettings(fix: Fix.OpenSettings) {
    val intent = Intent(fix.action)
    if (fix.forThisApp) {
        if (fix.action == Settings.ACTION_APP_NOTIFICATION_SETTINGS) {
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        } else {
            intent.data = Uri.fromParts("package", packageName, null)
        }
    }
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // Some OEM builds drop specific screens; the app info page has every toggle.
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
    }
}
