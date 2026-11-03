package com.flowtap

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.flowtap.data.transfer.IncomingImports
import com.flowtap.system.shizuku.ShizukuManager
import com.flowtap.system.shizuku.ShizukuStatus
import com.flowtap.ui.FlowTapRoot
import com.flowtap.ui.theme.FlowTapTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var incomingImports: IncomingImports
    @Inject lateinit var shizuku: ShizukuManager

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            handleImportIntent(intent)
            askForShizukuOnce()
        }
        setContent {
            FlowTapTheme {
                FlowTapRoot()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleImportIntent(intent)
    }

    /**
     * Shizuku's binder arrives asynchronously after launch, so wait briefly for it, then show
     * Shizuku's own permission dialog if FlowTap isn't allowed yet. Silent if Shizuku isn't running.
     */
    private fun askForShizukuOnce() {
        lifecycleScope.launch {
            withTimeoutOrNull(SHIZUKU_WAIT_MS) {
                shizuku.status.first { it != ShizukuStatus.NotRunning }
            }
            shizuku.requestPermissionIfNeeded()
        }
    }

    /** A flow file opened with, or shared to, FlowTap. */
    private fun handleImportIntent(intent: Intent?) {
        val uri = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            else -> null
        } ?: return
        incomingImports.offer(uri)
    }

    private companion object {
        const val SHIZUKU_WAIT_MS = 3_000L
    }
}
