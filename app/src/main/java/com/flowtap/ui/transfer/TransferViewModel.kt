package com.flowtap.ui.transfer

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flowtap.data.transfer.ExportPayload
import com.flowtap.data.transfer.FlowTransferManager
import com.flowtap.data.transfer.ImportFormatException
import com.flowtap.data.transfer.ImportPreview
import com.flowtap.data.transfer.IncomingImports
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TransferUiState(
    /** An export waiting for the user to pick Share or Save. */
    val pendingExport: ExportPayload? = null,
    /** A parsed file waiting for the user to confirm the import. */
    val importPreview: ImportPreview? = null,
    val busy: Boolean = false,
)

sealed interface TransferEvent {
    data class Message(val text: String) : TransferEvent
    data class Share(val uri: Uri, val fileName: String) : TransferEvent
    data class PickSaveLocation(val fileName: String) : TransferEvent
}

/** Activity-scoped: owns export/import dialogs so any screen can start one. */
@HiltViewModel
class TransferViewModel @Inject constructor(
    private val manager: FlowTransferManager,
    private val incoming: IncomingImports,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TransferUiState())
    val uiState: StateFlow<TransferUiState> = _uiState.asStateFlow()

    private val _events = Channel<TransferEvent>(Channel.BUFFERED)
    val events: Flow<TransferEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            incoming.pending.filterNotNull().collect { incoming.consume()?.let(::openImport) }
        }
    }

    /** Starts an export of [flowIds], or of every flow when null. */
    fun export(flowIds: Set<Long>? = null) = work {
        val payload = manager.prepareExport(flowIds)
        if (payload.flowCount == 0) {
            _events.send(TransferEvent.Message("There are no flows to export yet."))
            return@work
        }
        _uiState.update { it.copy(pendingExport = payload) }
    }

    fun dismissExport() = _uiState.update { it.copy(pendingExport = null) }

    fun shareExport() = work {
        val payload = _uiState.value.pendingExport ?: return@work
        _uiState.update { it.copy(pendingExport = null) }
        _events.send(TransferEvent.Share(manager.shareableUri(payload), payload.fileName))
    }

    fun saveExport() = work {
        val payload = _uiState.value.pendingExport ?: return@work
        _events.send(TransferEvent.PickSaveLocation(payload.fileName))
    }

    /** Result of the system "save as" picker; null when the user backed out. */
    fun onSaveLocation(uri: Uri?) = work {
        val payload = _uiState.value.pendingExport ?: return@work
        _uiState.update { it.copy(pendingExport = null) }
        if (uri == null) return@work
        manager.writeTo(uri, payload)
        _events.send(TransferEvent.Message("Saved ${payload.fileName}"))
    }

    fun openImport(uri: Uri?) {
        if (uri == null) return
        work {
            val preview = manager.readPreview(uri)
            if (preview.candidates.isEmpty()) {
                _events.send(TransferEvent.Message("No flows found in that file."))
            } else {
                _uiState.update { it.copy(importPreview = preview) }
            }
        }
    }

    fun dismissImport() = _uiState.update { it.copy(importPreview = null) }

    fun confirmImport() = work {
        val preview = _uiState.value.importPreview ?: return@work
        _uiState.update { it.copy(importPreview = null) }
        val count = manager.import(preview)
        _events.send(TransferEvent.Message(if (count == 1) "Imported 1 flow" else "Imported $count flows"))
    }

    private fun work(block: suspend () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true) }
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: ImportFormatException) {
                _events.send(TransferEvent.Message(e.message ?: "Couldn't read that file."))
            } catch (e: Exception) {
                _events.send(TransferEvent.Message("Something went wrong: ${e.message ?: e::class.simpleName}"))
            } finally {
                _uiState.update { it.copy(busy = false) }
            }
        }
    }
}
