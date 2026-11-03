package com.flowtap

import android.app.Application
import com.flowtap.automation.AutomationEngine
import com.flowtap.system.Notifier
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class FlowTapApp : Application() {

    @Inject lateinit var notifier: Notifier
    @Inject lateinit var automationEngine: AutomationEngine

    override fun onCreate() {
        super.onCreate()
        notifier.createChannels()
        // Keeps alarms in step with the database for as long as the process lives.
        automationEngine.start()
    }
}
