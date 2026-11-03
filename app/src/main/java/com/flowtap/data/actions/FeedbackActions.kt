package com.flowtap.data.actions

import android.Manifest
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import com.flowtap.domain.model.FlowAction
import com.flowtap.domain.model.MessageStyle
import com.flowtap.domain.run.Fix
import com.flowtap.domain.run.RunContext
import com.flowtap.domain.run.StepResult
import com.flowtap.system.Notifier
import com.flowtap.system.SystemAccess
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class FeedbackActions @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val notifier: Notifier,
    private val access: SystemAccess,
) {
    suspend fun show(action: FlowAction.ShowMessage, run: RunContext): StepResult = when (action.style) {
        // Plain text toasts are allowed from the background, so these work in automations too.
        MessageStyle.Toast -> withContext(Dispatchers.Main) {
            Toast.makeText(context, action.text, Toast.LENGTH_LONG).show()
            StepResult.Done
        }
        MessageStyle.Notification -> when {
            notifier.postMessage(run.flowName, action.text) -> StepResult.Done
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !access.hasPermission(Manifest.permission.POST_NOTIFICATIONS) -> StepResult.Blocked(
                "FlowTap isn't allowed to post notifications",
                Fix.GrantPermissions(listOf(Manifest.permission.POST_NOTIFICATIONS)),
            )
            else -> StepResult.Blocked(
                "Notifications for FlowTap are turned off",
                Fix.OpenSettings(Settings.ACTION_APP_NOTIFICATION_SETTINGS, forThisApp = true),
            )
        }
    }
}
