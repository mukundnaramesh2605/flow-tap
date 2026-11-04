package com.flowtap.ui.settings

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowtap.data.FlowRepository
import com.flowtap.data.SampleFlows
import com.flowtap.system.shizuku.ShizukuManager
import com.flowtap.system.shizuku.ShizukuStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val shizuku: ShizukuManager,
    private val repository: FlowRepository,
) : ViewModel() {

    val shizukuStatus: StateFlow<ShizukuStatus> = shizuku.status

    private val _shizukuInstalled = MutableStateFlow(false)

    /** The Shizuku manager app. Shizuku can also run via Sui (root) without it, so this is a hint only. */
    val shizukuInstalled: StateFlow<Boolean> = _shizukuInstalled.asStateFlow()

    private val _testResult = MutableStateFlow<String?>(null)
    val testResult: StateFlow<String?> = _testResult.asStateFlow()

    private val _testing = MutableStateFlow(false)
    val testing: StateFlow<Boolean> = _testing.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    fun refresh() {
        shizuku.refresh()
        _shizukuInstalled.value = shizukuLaunchIntent() != null
    }

    fun allowShizuku() {
        if (!shizuku.requestPermission()) {
            viewModelScope.launch {
                _messages.send("Shizuku won't ask again. Open Shizuku → Authorized apps and turn on FlowTap.")
            }
        }
    }

    fun shizukuLaunchIntent(): Intent? = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)

    /** Runs `id` through Shizuku to prove the shell bridge works end to end. */
    fun testShizuku() {
        if (_testing.value) return
        viewModelScope.launch {
            _testing.value = true
            val result = shizuku.exec("id -un; id -u")
            _testResult.value = when {
                result == null -> "Couldn't reach Shizuku."
                !result.ok -> "Shizuku answered, but the command failed (code ${result.exitCode})."
                else -> {
                    val (user, uid) = result.output.lines().let { it.getOrElse(0) { "?" } to it.getOrElse(1) { "?" } }
                    "Working — commands run as $user (uid $uid)."
                }
            }
            _testing.value = false
        }
    }

    fun restoreSamples() {
        viewModelScope.launch {
            repository.addAll(SampleFlows.all)
            _messages.send("Added Bedtime and Driving")
        }
    }

    private companion object {
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    }
}
