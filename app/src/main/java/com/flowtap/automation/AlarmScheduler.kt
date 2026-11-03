package com.flowtap.automation

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.edit
import androidx.core.net.toUri
import com.flowtap.domain.model.TapFlow
import com.flowtap.domain.model.Trigger
import com.flowtap.domain.schedule.nextRunAfter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps exactly one AlarmManager alarm per enabled time trigger, each set for its next occurrence.
 * Always recomputed from the full flow list, so it's safe to call any time (boot, edit, alarm fired).
 */
@Singleton
class AlarmScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val prefs = context.getSharedPreferences("alarm_scheduler", Context.MODE_PRIVATE)
    private val mutex = Mutex()

    /** False when Android only allows inexact alarms (the user revoked "Alarms & reminders"). */
    val canScheduleExact: Boolean
        get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    /**
     * Schedules every enabled time trigger in [flows] and cancels alarms for ones that are gone or off.
     * [justFired] (flow id, trigger id) skips that trigger's current minute, in case its alarm was
     * delivered a touch early; other triggers due in the same minute are left alone.
     */
    suspend fun reschedule(flows: List<TapFlow>, justFired: Pair<Long, String>? = null) = mutex.withLock {
        val now = ZonedDateTime.now()
        val wanted = mutableSetOf<String>()
        for (flow in flows) {
            for (trigger in flow.triggers) {
                if (trigger !is Trigger.TimeOfDay || !trigger.enabled) continue
                val from = if (justFired == flow.id to trigger.id) now.plusMinutes(1).withSecond(0) else now
                val next = trigger.nextRunAfter(from) ?: continue
                val key = alarmKey(flow.id, trigger.id)
                set(key, next.toInstant().toEpochMilli())
                wanted += key
            }
        }
        val previous = prefs.getStringSet(KEY_SCHEDULED, emptySet()).orEmpty()
        (previous - wanted).forEach(::cancel)
        prefs.edit { putStringSet(KEY_SCHEDULED, wanted) }
    }

    private fun set(key: String, atMillis: Long) {
        val pending = pendingIntent(key, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        try {
            if (canScheduleExact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
            } else {
                // Better late than never; the Automations screen warns about this state.
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm refused; falling back to inexact", e)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
        }
    }

    private fun cancel(key: String) {
        val pending = pendingIntent(key, PendingIntent.FLAG_NO_CREATE) ?: return
        alarmManager.cancel(pending)
        pending.cancel()
    }

    /** The data URI makes each trigger's PendingIntent distinct, so they never overwrite each other. */
    private fun pendingIntent(key: String, flag: Int): PendingIntent? {
        val (flowId, triggerId) = parseKey(key) ?: return null
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(AlarmReceiver.ACTION_FIRE)
            .setData("flowtap://trigger/$key".toUri())
            .putExtra(AlarmReceiver.EXTRA_FLOW_ID, flowId)
            .putExtra(AlarmReceiver.EXTRA_TRIGGER_ID, triggerId)
        return PendingIntent.getBroadcast(context, 0, intent, flag or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun alarmKey(flowId: Long, triggerId: String) = "$flowId/$triggerId"

    private fun parseKey(key: String): Pair<Long, String>? {
        val flowId = key.substringBefore('/').toLongOrNull() ?: return null
        return flowId to key.substringAfter('/')
    }

    private companion object {
        const val TAG = "AlarmScheduler"
        const val KEY_SCHEDULED = "scheduled"
    }
}
