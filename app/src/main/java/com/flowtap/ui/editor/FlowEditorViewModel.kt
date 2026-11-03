package com.flowtap.ui.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.navigation.toRoute
import com.flowtap.data.FlowRepository
import com.flowtap.data.apps.AppCatalog
import com.flowtap.data.apps.InstalledApp
import com.flowtap.data.run.FlowRunManager
import com.flowtap.domain.model.FlowAction
import com.flowtap.domain.model.FlowColor
import com.flowtap.domain.model.FlowIcon
import com.flowtap.domain.model.TapFlow
import com.flowtap.domain.model.Trigger
import com.flowtap.domain.model.withEnabled
import com.flowtap.domain.run.RunState
import com.flowtap.ui.navigation.EditorRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/** An action in the editor, with a key that stays put while the list is reordered. */
data class DraftAction(val key: Long, val action: FlowAction)

/** The action open in the config sheet. [key] is null while adding a new one. */
data class ActionEdit(val key: Long?, val action: FlowAction)

/** The schedule open in the schedule dialog. [triggerId] is null while adding a new one. */
data class ScheduleEdit(val triggerId: String?, val hour: Int, val minute: Int, val days: Set<Int>)

/** The Bluetooth trigger open in its dialog. [triggerId] is null while adding a new one. */
data class BluetoothEdit(
    val triggerId: String?,
    val address: String?,
    val event: Trigger.ConnectionEvent,
)

data class EditorUiState(
    val isNew: Boolean,
    val isLoading: Boolean = !isNew,
    val name: String = "",
    val icon: FlowIcon = FlowIcon.entries.random(),
    val color: FlowColor = FlowColor.entries.random(),
    val actions: List<DraftAction> = emptyList(),
    val triggers: List<Trigger> = emptyList(),
    /** Packages used by Open app actions that aren't installed on this device. */
    val missingPackages: Set<String> = emptySet(),
    val hasChanges: Boolean = false,
    val showActionPicker: Boolean = false,
    val actionEdit: ActionEdit? = null,
    val scheduleEdit: ScheduleEdit? = null,
    val bluetoothEdit: BluetoothEdit? = null,
) {
    val canSave: Boolean get() = !isLoading && name.isNotBlank()
}

sealed interface EditorEvent {
    data object Close : EditorEvent
    data class Saved(val flowId: Long) : EditorEvent
}

@HiltViewModel
class FlowEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: FlowRepository,
    private val appCatalog: AppCatalog,
    private val runManager: FlowRunManager,
) : ViewModel() {

    val flowId = savedStateHandle.toRoute<EditorRoute>().flowId
    private val isNew = flowId == EditorRoute.NEW_FLOW

    private var nextKey = 0L
    private var original: TapFlow? = null

    private val _uiState = MutableStateFlow(EditorUiState(isNew = isNew))
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val _events = Channel<EditorEvent>(Channel.BUFFERED)
    val events: Flow<EditorEvent> = _events.receiveAsFlow()

    private val _apps = MutableStateFlow<List<InstalledApp>?>(null)

    /** Launchable apps for the picker; null until first requested and loaded. */
    val apps: StateFlow<List<InstalledApp>?> = _apps.asStateFlow()

    private val _testReport = MutableStateFlow<RunState.Finished?>(null)

    /** Result of the last "test as automation" run, shown until dismissed. */
    val testReport: StateFlow<RunState.Finished?> = _testReport.asStateFlow()

    private val _testing = MutableStateFlow(false)
    val testing: StateFlow<Boolean> = _testing.asStateFlow()

    init {
        if (isNew) {
            original = buildFlow(_uiState.value)
        } else {
            viewModelScope.launch {
                val existing = repository.getFlow(flowId)
                if (existing == null) {
                    _events.send(EditorEvent.Close)
                    return@launch
                }
                original = existing
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        name = existing.name,
                        icon = existing.icon,
                        color = existing.color,
                        actions = existing.actions.map { action -> DraftAction(nextKey++, action) },
                        triggers = existing.triggers,
                    ).withDerived()
                }
            }
        }
    }

    fun setName(name: String) = edit { copy(name = name) }
    fun setIcon(icon: FlowIcon) = edit { copy(icon = icon) }
    fun setColor(color: FlowColor) = edit { copy(color = color) }

    // --- Actions ---

    fun showActionPicker(show: Boolean) = _uiState.update { it.copy(showActionPicker = show) }

    /** Picking a kind opens its config sheet; the action is only added once confirmed there. */
    fun startNewAction(action: FlowAction) =
        _uiState.update { it.copy(showActionPicker = false, actionEdit = ActionEdit(key = null, action = action)) }

    fun startEditAction(key: Long) {
        val draft = _uiState.value.actions.find { it.key == key } ?: return
        _uiState.update { it.copy(actionEdit = ActionEdit(key, draft.action)) }
    }

    fun dismissActionEdit() = _uiState.update { it.copy(actionEdit = null) }

    fun commitAction(action: FlowAction) {
        val editing = _uiState.value.actionEdit ?: return
        edit {
            val updated = if (editing.key == null) {
                actions + DraftAction(nextKey++, action)
            } else {
                actions.map { if (it.key == editing.key) it.copy(action = action) else it }
            }
            copy(actions = updated, actionEdit = null)
        }
    }

    fun deleteAction(key: Long) = edit {
        copy(actions = actions.filterNot { it.key == key }, actionEdit = actionEdit?.takeIf { it.key != key })
    }

    /** Moves the action with key [fromKey] into the slot held by [toKey]. Ignores non-action keys. */
    fun moveAction(fromKey: Any, toKey: Any) = edit {
        val from = actions.indexOfFirst { it.key == fromKey }
        val to = actions.indexOfFirst { it.key == toKey }
        if (from < 0 || to < 0) return@edit this
        copy(actions = actions.toMutableList().apply { add(to, removeAt(from)) })
    }

    private val iconCache = mutableMapOf<String, ImageBitmap?>()

    suspend fun appIcon(packageName: String, sizePx: Int): ImageBitmap? =
        iconCache.getOrPut(packageName) { appCatalog.icon(packageName, sizePx)?.asImageBitmap() }

    fun loadApps() {
        if (_apps.value != null) return
        viewModelScope.launch { _apps.value = appCatalog.launchableApps() }
    }

    // --- Schedules ---

    fun startNewSchedule() = _uiState.update {
        it.copy(scheduleEdit = ScheduleEdit(triggerId = null, hour = 22, minute = 0, days = Trigger.TimeOfDay.ALL_DAYS))
    }

    fun startEditTrigger(triggerId: String) {
        when (val trigger = _uiState.value.triggers.find { it.id == triggerId }) {
            is Trigger.TimeOfDay -> _uiState.update {
                it.copy(scheduleEdit = ScheduleEdit(trigger.id, trigger.hour, trigger.minute, trigger.days))
            }
            is Trigger.BluetoothDevice -> _uiState.update {
                it.copy(bluetoothEdit = BluetoothEdit(trigger.id, trigger.address, trigger.event))
            }
            null -> Unit
        }
    }

    fun dismissScheduleEdit() = _uiState.update { it.copy(scheduleEdit = null) }

    fun commitSchedule(hour: Int, minute: Int, days: Set<Int>) {
        val editing = _uiState.value.scheduleEdit ?: return
        if (days.isEmpty()) return
        edit {
            val updated = if (editing.triggerId == null) {
                triggers + Trigger.TimeOfDay(UUID.randomUUID().toString(), hour, minute, days, enabled = true)
            } else {
                triggers.map { t ->
                    if (t is Trigger.TimeOfDay && t.id == editing.triggerId) t.copy(hour = hour, minute = minute, days = days)
                    else t
                }
            }
            copy(triggers = updated, scheduleEdit = null)
        }
    }

    fun startNewBluetoothTrigger() = _uiState.update {
        it.copy(bluetoothEdit = BluetoothEdit(triggerId = null, address = null, event = Trigger.ConnectionEvent.Connected))
    }

    fun dismissBluetoothEdit() = _uiState.update { it.copy(bluetoothEdit = null) }

    fun commitBluetoothTrigger(address: String, deviceName: String, event: Trigger.ConnectionEvent) {
        val editing = _uiState.value.bluetoothEdit ?: return
        edit {
            val updated = if (editing.triggerId == null) {
                triggers + Trigger.BluetoothDevice(UUID.randomUUID().toString(), address, deviceName, event)
            } else {
                triggers.map { t ->
                    if (t is Trigger.BluetoothDevice && t.id == editing.triggerId) {
                        t.copy(address = address, deviceName = deviceName, event = event)
                    } else {
                        t
                    }
                }
            }
            copy(triggers = updated, bluetoothEdit = null)
        }
    }

    fun setTriggerEnabled(triggerId: String, enabled: Boolean) = edit {
        copy(
            triggers = triggers.map { t -> if (t.id == triggerId) t.withEnabled(enabled) else t },
        )
    }

    fun deleteTrigger(triggerId: String) = edit {
        copy(triggers = triggers.filterNot { it.id == triggerId }, scheduleEdit = null, bluetoothEdit = null)
    }

    // --- Test run ---

    /**
     * Runs the saved flow the way an automation would (no UI assumed), so the Shizuku and
     * notification fallbacks can be checked without waiting for a schedule.
     */
    fun testAsAutomation() {
        val state = _uiState.value
        if (state.isNew || state.hasChanges || _testing.value) return
        viewModelScope.launch {
            _testing.value = true
            try {
                _testReport.value = runManager.start(buildFlow(state), interactive = false).await()
            } finally {
                _testing.value = false
            }
        }
    }

    fun dismissTestReport() {
        _testReport.value = null
    }

    // --- Persist ---

    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        viewModelScope.launch {
            val id = repository.save(buildFlow(state).copy(name = state.name.trim()))
            _events.send(EditorEvent.Saved(id))
        }
    }

    fun delete() {
        if (isNew) return
        viewModelScope.launch {
            repository.delete(flowId)
            _events.send(EditorEvent.Close)
        }
    }

    private fun buildFlow(state: EditorUiState) = TapFlow(
        id = if (isNew) 0 else flowId,
        name = state.name,
        icon = state.icon,
        color = state.color,
        actions = state.actions.map { it.action },
        triggers = state.triggers,
    )

    private inline fun edit(crossinline change: EditorUiState.() -> EditorUiState) {
        _uiState.update { it.change().withDerived() }
    }

    private fun EditorUiState.withDerived(): EditorUiState {
        val base = original
        return copy(
            hasChanges = base != null && buildFlow(this) != base,
            missingPackages = actions.mapNotNull { (it.action as? FlowAction.OpenApp)?.packageName }
                .filter { it.isNotBlank() && !appCatalog.isInstalled(it) }
                .toSet(),
        )
    }
}
