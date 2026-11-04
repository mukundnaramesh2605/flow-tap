package com.flowtap.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowtap.data.FlowRepository
import com.flowtap.data.run.FlowRunManager
import com.flowtap.domain.model.TapFlow
import com.flowtap.domain.run.RunState
import com.flowtap.system.AppPermission
import com.flowtap.system.AppPermissions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    val flows: List<TapFlow> = emptyList(),
)

/** Asked before a run so it doesn't stop on a missing permission halfway through. */
data class Preflight(val flow: TapFlow, val permissions: List<AppPermission>)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: FlowRepository,
    private val runManager: FlowRunManager,
    private val permissions: AppPermissions,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = repository.observeFlows()
        .map { HomeUiState(isLoading = false, flows = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** Live run progress keyed by flow id; absent means idle. */
    val runStates: StateFlow<Map<Long, RunState>> = runManager.states

    private val _finishedWithIssues = Channel<RunState.Finished>(Channel.BUFFERED)

    /** Runs that ended with something for the user to look at. */
    val finishedWithIssues: Flow<RunState.Finished> = _finishedWithIssues.receiveAsFlow()

    private val _openReport = MutableStateFlow<RunState.Finished?>(null)
    val openReport: StateFlow<RunState.Finished?> = _openReport.asStateFlow()

    private val _preflight = MutableStateFlow<Preflight?>(null)
    val preflight: StateFlow<Preflight?> = _preflight.asStateFlow()

    /** Permissions the user has already been asked about this session; never nag twice. */
    private val asked = mutableSetOf<AppPermission>()

    private val _setupCount = MutableStateFlow(0)

    /** How many permissions your flows use that aren't granted yet (the optional overlay excluded). */
    val setupCount: StateFlow<Int> = _setupCount.asStateFlow()

    fun refreshSetup() {
        viewModelScope.launch {
            _setupCount.value = permissions.states(uiState.value.flows.ifEmpty { repository.getFlows() })
                .count { !it.granted && it.neededBy.isNotEmpty() && it.permission != AppPermission.Overlay }
        }
    }

    /** A tile tap: explain and request missing permissions first, otherwise run straight away. */
    fun onTileTap(flow: TapFlow) {
        if (runManager.isRunning(flow.id)) return
        val missing = permissions.missingRuntimePermissions(flow) - asked
        if (missing.isEmpty()) run(flow) else _preflight.value = Preflight(flow, missing)
    }

    /** Android permission strings to request for the open preflight. */
    fun preflightPermissions(): Array<String> =
        _preflight.value?.permissions.orEmpty().mapNotNull(permissions::runtimePermissionOf).toTypedArray()

    /** Called after the system prompt (or "Run anyway"); runs regardless of the answer. */
    fun finishPreflight() {
        val pending = _preflight.value ?: return
        asked += pending.permissions
        _preflight.value = null
        run(pending.flow)
        refreshSetup()
    }

    fun cancelPreflight() {
        _preflight.value = null
    }

    fun run(flow: TapFlow) {
        if (runManager.isRunning(flow.id)) return
        viewModelScope.launch {
            val report = runManager.start(flow, interactive = true).await()
            if (report.issues.isNotEmpty()) _finishedWithIssues.send(report)
        }
    }

    fun showReport(report: RunState.Finished?) {
        _openReport.value = report
    }
}
