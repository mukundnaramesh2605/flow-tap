package com.flowtap.data.actions

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.flowtap.domain.model.FlowAction
import com.flowtap.domain.run.RunContext
import com.flowtap.domain.run.StepResult
import com.flowtap.system.ActivityLauncher
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class LaunchActions @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val launcher: ActivityLauncher,
) {
    suspend fun openUrl(action: FlowAction.OpenUrl, run: RunContext): StepResult {
        val uri = runCatching { action.url.toUri() }.getOrNull()
        if (uri?.scheme !in setOf("http", "https")) return StepResult.Failed("“${action.url}” isn't a web address")
        val intent = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
        return launcher.launch(intent, what = uri?.host ?: action.url, run = run)
    }

    suspend fun openApp(action: FlowAction.OpenApp, run: RunContext): StepResult {
        val intent = context.packageManager.getLaunchIntentForPackage(action.packageName)
            ?: return StepResult.Failed("${action.appLabel} isn't installed on this phone")
        return launcher.launch(intent, what = action.appLabel, run = run)
    }
}
