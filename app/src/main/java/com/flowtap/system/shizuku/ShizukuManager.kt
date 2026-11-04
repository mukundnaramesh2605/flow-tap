package com.flowtap.system.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import com.flowtap.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import javax.inject.Inject
import javax.inject.Singleton

enum class ShizukuStatus {
    /** Shizuku isn't installed or its service isn't started (e.g. after a reboot). */
    NotRunning,

    /** Running, but FlowTap hasn't been allowed yet. */
    PermissionNeeded,

    /** Running, but older than v11 and lacking the APIs FlowTap uses. */
    Unsupported,

    Ready,
}

data class ShellResult(val exitCode: Int, val output: String) {
    val ok: Boolean get() = exitCode == 0
}

/**
 * Tracks Shizuku on this device and runs shell commands through it. Every call is defensive:
 * Shizuku can die at any moment (reboot, user stops it), and that must never crash FlowTap.
 */
@Singleton
class ShizukuManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val _status = MutableStateFlow(ShizukuStatus.NotRunning)
    val status: StateFlow<ShizukuStatus> = _status.asStateFlow()

    val isReady: Boolean get() = refresh() == ShizukuStatus.Ready

    private val bindMutex = Mutex()
    @Volatile private var service: IShellService? = null
    private var connection: ServiceConnection? = null

    private val userServiceArgs = Shizuku.UserServiceArgs(ComponentName(context.packageName, ShellService::class.java.name))
        .daemon(false)
        .processNameSuffix("shell")
        .debuggable(BuildConfig.DEBUG)
        .version(BuildConfig.VERSION_CODE)

    init {
        Shizuku.addBinderReceivedListenerSticky { refresh() }
        Shizuku.addBinderDeadListener {
            service = null
            connection = null
            refresh()
        }
        Shizuku.addRequestPermissionResultListener { _, _ -> refresh() }
    }

    /** Re-reads the current state. Cheap; safe to call from anywhere. */
    fun refresh(): ShizukuStatus {
        val status = try {
            when {
                !Shizuku.pingBinder() -> ShizukuStatus.NotRunning
                Shizuku.isPreV11() -> ShizukuStatus.Unsupported
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> ShizukuStatus.Ready
                else -> ShizukuStatus.PermissionNeeded
            }
        } catch (e: Exception) {
            Log.w(TAG, "Shizuku status check failed", e)
            ShizukuStatus.NotRunning
        }
        _status.value = status
        return status
    }

    /**
     * Shows Shizuku's permission dialog when it's running and FlowTap isn't allowed yet.
     * Skips it if the user picked "deny" with "don't ask again". Returns whether a dialog was shown.
     */
    fun requestPermissionIfNeeded(): Boolean = try {
        if (refresh() == ShizukuStatus.PermissionNeeded && !Shizuku.shouldShowRequestPermissionRationale()) {
            Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
            true
        } else {
            false
        }
    } catch (e: Exception) {
        Log.w(TAG, "Shizuku permission request failed", e)
        false
    }

    /**
     * Explicit "Allow" from Settings. Returns false when Shizuku won't show its dialog because the
     * user chose "deny and don't ask again"; FlowTap must then be allowed from inside the Shizuku app.
     */
    fun requestPermission(): Boolean = try {
        if (refresh() != ShizukuStatus.PermissionNeeded || Shizuku.shouldShowRequestPermissionRationale()) {
            false
        } else {
            Shizuku.requestPermission(PERMISSION_REQUEST_CODE)
            true
        }
    } catch (e: Exception) {
        Log.w(TAG, "Shizuku permission request failed", e)
        false
    }

    /** Runs [command] as the shell user. Returns null when Shizuku isn't usable right now. */
    suspend fun exec(command: String): ShellResult? {
        if (!isReady) return null
        val shell = bindService() ?: return null
        return withContext(Dispatchers.IO) {
            try {
                val raw = shell.exec(command)
                val code = raw.substringBefore('\n').toIntOrNull() ?: -1
                ShellResult(code, raw.substringAfter('\n', ""))
            } catch (e: Exception) {
                Log.w(TAG, "Shizuku exec failed: $command", e)
                service = null
                null
            }
        }
    }

    private suspend fun bindService(): IShellService? = bindMutex.withLock {
        service?.takeIf { it.asBinder().pingBinder() }?.let { return it }
        val ready = CompletableDeferred<IShellService?>()
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val shell = binder?.takeIf { it.pingBinder() }?.let(IShellService.Stub::asInterface)
                service = shell
                ready.complete(shell)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                service = null
            }
        }
        try {
            withContext(Dispatchers.Main) { Shizuku.bindUserService(userServiceArgs, conn) }
            connection = conn
        } catch (e: Exception) {
            Log.w(TAG, "Shizuku bind failed", e)
            return null
        }
        // First bind spawns a process; give it a few seconds.
        withTimeoutOrNull(BIND_TIMEOUT_MS) { ready.await() }
    }

    private companion object {
        const val TAG = "ShizukuManager"
        const val PERMISSION_REQUEST_CODE = 4242
        const val BIND_TIMEOUT_MS = 8_000L
    }
}
