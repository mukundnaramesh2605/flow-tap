package com.flowtap

import android.app.Application
import com.flowtap.system.Notifier
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class FlowTapApp : Application() {

    @Inject lateinit var notifier: Notifier

    override fun onCreate() {
        super.onCreate()
        notifier.createChannels()
    }
}
