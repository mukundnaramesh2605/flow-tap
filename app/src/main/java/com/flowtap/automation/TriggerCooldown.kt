package com.flowtap.automation

import android.content.Context
import android.os.SystemClock
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Ignores repeats of the same event within a short window. Persisted, since receivers may get a fresh process. */
@Singleton
class TriggerCooldown @Inject constructor(
    @param:ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("trigger_cooldown", Context.MODE_PRIVATE)

    /** True if [key] hasn't fired in the last [windowMs]; records this firing. */
    @Synchronized
    fun tryAcquire(key: String, windowMs: Long = WINDOW_MS): Boolean {
        // elapsedRealtime survives clock changes; a reboot resets it, which also clears the window.
        val now = SystemClock.elapsedRealtime()
        val last = prefs.getLong(key, Long.MIN_VALUE)
        if (last != Long.MIN_VALUE && now >= last && now - last < windowMs) return false
        prefs.edit { putLong(key, now) }
        return true
    }

    private companion object {
        const val WINDOW_MS = 60_000L
    }
}
