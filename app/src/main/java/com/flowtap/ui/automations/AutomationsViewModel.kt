package com.flowtap.ui.automations

import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowtap.automation.AutomationHealth
import com.flowtap.automation.HealthIssue
import com.flowtap.data.FlowRepository
import com.flowtap.domain.model.TapFlow
import com.flowtap.domain.model.Trigger
import com.flowtap.domain.model.withEnabled
import com.flowtap.domain.run.Fix
import com.flowtap.domain.schedule.nextRunAfter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import javax.inject.Inject

data class AutomationItem(
    val flow: TapFlow,
    val trigger: Trigger,
    /** Null when disabled, when the schedule has no days, or for event triggers. */
    val nextRun: ZonedDateTime?,
)

data class AutomationsUiState(
    val isLoading: Boolean = true,
    val items: List<AutomationItem> = emptyList(),
    val now: ZonedDateTime = ZonedDateTime.now(),
)

@HiltViewModel
class AutomationsViewModel @Inject constructor(
    @param:ApplicationContext context: Context,
    private val repository: FlowRepository,
    private val health: AutomationHealth,
) : ViewModel() {

    private val prefs = context.getSharedPreferences("automations_ui", Context.MODE_PRIVATE)

    /** Ticks each minute so "in 3h 20m" stays current while the screen is open. */
    private val clock: Flow<ZonedDateTime> = flow {
        while (true) {
            val now = ZonedDateTime.now()
            emit(now)
            delay((60 - now.second) * 1_000L)
        }
    }

    val uiState: StateFlow<AutomationsUiState> = combine(repository.observeFlows(), clock) { flows, now ->
        val items = flows.flatMap { flow ->
            flow.triggers.map { trigger ->
                val next = if (trigger.enabled && trigger is Trigger.TimeOfDay) trigger.nextRunAfter(now) else null
                AutomationItem(flow, trigger, next)
            }
        }
        // Soonest schedule first, then event triggers, then anything switched off.
        val ordered = items.sortedWith(
            compareBy<AutomationItem> { !it.trigger.enabled }
                .thenBy(nullsLast()) { it.nextRun },
        )
        AutomationsUiState(isLoading = false, items = ordered, now = now)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AutomationsUiState())

    private val _issues = MutableStateFlow<List<HealthIssue>>(emptyList())
    val issues: StateFlow<List<HealthIssue>> = _issues.asStateFlow()

    /** The one-time battery explanation, shown the first time it's relevant. */
    private val _showBatteryPrompt = MutableStateFlow(false)
    val showBatteryPrompt: StateFlow<Boolean> = _showBatteryPrompt.asStateFlow()

    private val _fixes = Channel<Fix>(Channel.BUFFERED)

    /** Fixes the UI has to carry out (permission prompts, settings screens). */
    val fixes: Flow<Fix> = _fixes.receiveAsFlow()

    /** Call on resume: permissions and Shizuku may have changed while the user was away. */
    fun refreshHealth() {
        viewModelScope.launch {
            val current = health.check(repository.getFlows())
            _issues.value = current
            val batteryIssue = current.any { it.kind == HealthIssue.Kind.BatteryOptimized }
            if (batteryIssue && !prefs.getBoolean(KEY_BATTERY_PROMPTED, false)) _showBatteryPrompt.value = true
        }
    }

    fun fix(issue: HealthIssue) {
        viewModelScope.launch {
            if (issue.silentFix && health.fixSilently(issue)) {
                refreshHealth()
                return@launch
            }
            issue.fix?.let { _fixes.send(it) }
        }
    }

    fun answerBatteryPrompt(allow: Boolean) {
        prefs.edit { putBoolean(KEY_BATTERY_PROMPTED, true) }
        _showBatteryPrompt.value = false
        if (allow) _issues.value.find { it.kind == HealthIssue.Kind.BatteryOptimized }?.let(::fix)
    }

    fun setEnabled(flowId: Long, triggerId: String, enabled: Boolean) {
        viewModelScope.launch {
            val flow = repository.getFlow(flowId) ?: return@launch
            repository.save(flow.copy(triggers = flow.triggers.map { if (it.id == triggerId) it.withEnabled(enabled) else it }))
            refreshHealth()
        }
    }

    private companion object {
        const val KEY_BATTERY_PROMPTED = "battery_prompted"
    }
}
