package com.flowtap.ui.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.flowtap.data.FlowRepository
import com.flowtap.domain.model.FlowColor
import com.flowtap.domain.model.FlowIcon
import com.flowtap.domain.model.TapFlow
import com.flowtap.ui.navigation.EditorRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditorUiState(
    val isNew: Boolean,
    val isLoading: Boolean,
    val draft: TapFlow,
) {
    val canSave: Boolean get() = !isLoading && draft.name.isNotBlank()
}

@HiltViewModel
class FlowEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: FlowRepository,
) : ViewModel() {

    private val flowId = savedStateHandle.toRoute<EditorRoute>().flowId
    private val isNew = flowId == EditorRoute.NEW_FLOW

    private val _uiState = MutableStateFlow(
        EditorUiState(
            isNew = isNew,
            isLoading = !isNew,
            draft = TapFlow(name = "", icon = FlowIcon.entries.random(), color = FlowColor.entries.random()),
        ),
    )
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val _closeEvents = Channel<Unit>(Channel.BUFFERED)

    /** Emits when the editor should close (after save or delete). */
    val closeEvents: Flow<Unit> = _closeEvents.receiveAsFlow()

    init {
        if (!isNew) {
            viewModelScope.launch {
                val existing = repository.observeFlow(flowId).first()
                if (existing == null) {
                    _closeEvents.send(Unit)
                } else {
                    _uiState.update { it.copy(isLoading = false, draft = existing) }
                }
            }
        }
    }

    fun setName(name: String) = updateDraft { copy(name = name) }
    fun setIcon(icon: FlowIcon) = updateDraft { copy(icon = icon) }
    fun setColor(color: FlowColor) = updateDraft { copy(color = color) }

    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        viewModelScope.launch {
            repository.save(state.draft.copy(name = state.draft.name.trim()))
            _closeEvents.send(Unit)
        }
    }

    fun delete() {
        if (isNew) return
        viewModelScope.launch {
            repository.delete(flowId)
            _closeEvents.send(Unit)
        }
    }

    private inline fun updateDraft(crossinline change: TapFlow.() -> TapFlow) {
        _uiState.update { it.copy(draft = it.draft.change()) }
    }
}
