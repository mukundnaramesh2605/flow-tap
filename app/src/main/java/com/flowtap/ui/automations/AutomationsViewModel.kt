package com.flowtap.ui.automations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowtap.data.FlowRepository
import com.flowtap.domain.model.TapFlow
import com.flowtap.domain.model.Trigger
import com.flowtap.domain.schedule.nextRunAfter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import javax.inject.Inject

data class AutomationItem(
    val flow: TapFlow,
    val trigger: Trigger,
    /** Null when disabled or when the schedule has no days. */
    val nextRun: ZonedDateTime?,
)

data class AutomationsUiState(
    val isLoading: Boolean = true,
    val items: List<AutomationItem> = emptyList(),
)

@HiltViewModel
class AutomationsViewModel @Inject constructor(
    private val repository: FlowRepository,
) : ViewModel() {

    val uiState: StateFlow<AutomationsUiState> = repository.observeFlows()
        .map { flows ->
            val now = ZonedDateTime.now()
            val items = flows.flatMap { flow ->
                flow.triggers.map { trigger ->
                    AutomationItem(flow, trigger, nextRun = if (trigger.enabled) trigger.nextRun(now) else null)
                }
            }
            // Soonest first; disabled ones sink to the bottom.
            AutomationsUiState(
                isLoading = false,
                items = items.sortedWith(compareBy(nullsLast()) { it.nextRun }),
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AutomationsUiState())

    fun setEnabled(flowId: Long, triggerId: String, enabled: Boolean) {
        viewModelScope.launch {
            val flow = repository.observeFlow(flowId).first() ?: return@launch
            val triggers = flow.triggers.map { trigger ->
                if (trigger.id != triggerId) trigger
                else when (trigger) {
                    is Trigger.TimeOfDay -> trigger.copy(enabled = enabled)
                }
            }
            repository.save(flow.copy(triggers = triggers))
        }
    }

    private fun Trigger.nextRun(now: ZonedDateTime): ZonedDateTime? = when (this) {
        is Trigger.TimeOfDay -> nextRunAfter(now)
    }
}
