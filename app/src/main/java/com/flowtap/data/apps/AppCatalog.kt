package com.flowtap.data.apps

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class InstalledApp(val packageName: String, val label: String)

/**
 * Launchable apps on this device. Visibility on Android 11+ comes from the launcher-intent
 * <queries> entry in the manifest, so only apps with a launcher icon are seen.
 */
@Singleton
class AppCatalog @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val pm get() = context.packageManager

    suspend fun launchableApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(intent, 0)
            .map { InstalledApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun isInstalled(packageName: String): Boolean = pm.getLaunchIntentForPackage(packageName) != null

    suspend fun icon(packageName: String, sizePx: Int): Bitmap? = withContext(Dispatchers.IO) {
        runCatching { pm.getApplicationIcon(packageName).toBitmap(sizePx, sizePx) }.getOrNull()
    }
}
