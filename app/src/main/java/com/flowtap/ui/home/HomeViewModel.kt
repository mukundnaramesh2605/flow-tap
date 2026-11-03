package com.flowtap.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowtap.data.FlowRepository
import com.flowtap.data.run.FlowRunManager
import com.flowtap.domain.model.TapFlow
import com.flowtap.domain.run.RunState
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

@HiltViewModel
class HomeViewModel @Inject constructor(
    repository: FlowRepository,
    private val runManager: FlowRunManager,
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
