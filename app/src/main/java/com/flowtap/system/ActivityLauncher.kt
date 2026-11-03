package com.flowtap.system

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.flowtap.domain.run.Fix
import com.flowtap.domain.run.RunContext
import com.flowtap.domain.run.StepResult
import com.flowtap.system.shizuku.ShizukuManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Starts activities from a flow, picking a path Android allows right now:
 * 1. FlowTap is on screen, or has "display over other apps" -> start directly.
 * 2. Shizuku is ready -> `am start` as the shell user, which may start activities from the background.
 * 3. Otherwise -> post a notification; tapping it opens the target.
 */
@Singleton
class ActivityLauncher @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val visibility: AppVisibility,
    private val access: SystemAccess,
    private val shizuku: ShizukuManager,
    private val notifier: Notifier,
) {
    /**
     * @param what short label for messages, e.g. "Google Maps".
     * @param doneResult what to report when the activity did open (panels report a hand-off).
     * @param notifyTitle title for the fallback notification; defaults to "tap to open [what]".
     */
    suspend fun launch(
        intent: Intent,
        what: String,
        run: RunContext,
        doneResult: StepResult = StepResult.Done,
        notifyTitle: String = "${run.flowName}: tap to open $what",
    ): StepResult {
        val target = Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        if (visibility.isForeground || access.has(SpecialAccess.Overlay)) {
            return try {
                context.startActivity(target)
                doneResult
            } catch (_: ActivityNotFoundException) {
                StepResult.Failed("Nothing on this phone can open $what")
            } catch (e: SecurityException) {
                StepResult.Failed("Android blocked opening $what: ${e.message}")
            }
        }

        if (target.resolveActivity(context.packageManager) == null) {
            return StepResult.Failed("Nothing on this phone can open $what")
        }

        val uri = target.toUri(Intent.URI_INTENT_SCHEME)
        val result = shizuku.exec("am start --user current ${uri.shellQuoted()}")
        if (result != null && result.ok && !result.output.contains("Error", ignoreCase = true)) {
            return doneResult
        }

        val posted = notifier.postActionNeeded(
            title = notifyTitle,
            text = "FlowTap can't do this from the background without Shizuku. Tap to finish.",
            tapIntent = target,
        )
        return if (posted) {
            StepResult.HandedOff("Posted a notification — tap it to finish")
        } else {
            StepResult.Blocked(
                "Couldn't open $what in the background, and notifications are off",
                Fix.OpenSettings(Settings.ACTION_APP_NOTIFICATION_SETTINGS, forThisApp = true),
            )
        }
    }
}

/** Single-quotes for `sh -c`, escaping embedded single quotes. */
internal fun String.shellQuoted(): String = "'" + replace("'", "'\\''") + "'"
