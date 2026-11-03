package com.flowtap.system

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.flowtap.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Notifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val manager = NotificationManagerCompat.from(context)
    private val nextId = AtomicInteger(1_000)

    /** Creates channels. Idempotent; called at app start. */
    fun createChannels() {
        val system = context.getSystemService(NotificationManager::class.java)
        system.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_MESSAGES, "Flow messages", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Notifications posted by your flows' \"Notify\" actions"
                },
                NotificationChannel(CHANNEL_ACTION_NEEDED, "Needs your tap", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "When an automation can't finish a step on its own, e.g. Wi-Fi without Shizuku"
                },
            ),
        )
    }

    val canPost: Boolean
        get() = manager.areNotificationsEnabled() &&
            (
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
                )

    /** Returns false when notifications are blocked. */
    fun postMessage(title: String, text: String): Boolean = post(
        NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_stat_flowtap)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openAppIntent())
            .setAutoCancel(true),
    )

    /**
     * Asks the user to finish a step by tapping. [tapIntent] is started from the notification,
     * which Android allows even when FlowTap is in the background.
     */
    fun postActionNeeded(title: String, text: String, tapIntent: Intent): Boolean {
        val id = nextId.getAndIncrement()
        val pending = PendingIntent.getActivity(
            context,
            id,
            tapIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return post(
            NotificationCompat.Builder(context, CHANNEL_ACTION_NEEDED)
                .setSmallIcon(R.drawable.ic_stat_flowtap)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(pending)
                .setAutoCancel(true),
            id,
        )
    }

    private fun openAppIntent(): PendingIntent? =
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }

    @SuppressLint("MissingPermission") // checked by canPost
    private fun post(builder: NotificationCompat.Builder, id: Int = nextId.getAndIncrement()): Boolean {
        if (!canPost) return false
        manager.notify(id, builder.build())
        return true
    }

    companion object {
        const val CHANNEL_MESSAGES = "flow_messages"
        const val CHANNEL_ACTION_NEEDED = "action_needed"
    }
}
