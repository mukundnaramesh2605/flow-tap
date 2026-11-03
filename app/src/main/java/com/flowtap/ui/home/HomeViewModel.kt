package com.flowtap.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowtap.data.FlowRepository
import com.flowtap.domain.model.TapFlow
import com.flowtap.domain.run.FlowRunner
import com.flowtap.domain.run.RunState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    val flows: List<TapFlow> = emptyList(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    repository: FlowRepository,
    private val runner: FlowRunner,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = repository.observeFlows()
        .map { HomeUiState(isLoading = false, flows = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private val _runStates = MutableStateFlow<Map<Long, RunState>>(emptyMap())

    /** Live run progress keyed by flow id; absent means idle. */
    val runStates: StateFlow<Map<Long, RunState>> = _runStates.asStateFlow()

    private val runJobs = mutableMapOf<Long, Job>()

    fun run(flow: TapFlow) {
        if (runJobs[flow.id]?.isActive == true) return
        runJobs[flow.id] = viewModelScope.launch {
            runner.run(flow).collect { state -> _runStates.update { it + (flow.id to state) } }
            // Leave the success/failure badge up briefly before returning the tile to idle.
            delay(RESULT_LINGER_MS)
            _runStates.update { it - flow.id }
        }
    }

    private companion object {
        const val RESULT_LINGER_MS = 1_400L
    }
}
