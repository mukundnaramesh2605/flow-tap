package com.flowtap.ui.transfer

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** What screens call to start an export or import. Dialogs and system pickers are handled here. */
class TransferController internal constructor(
    val exportAll: () -> Unit,
    val exportFlow: (Long) -> Unit,
    val import: () -> Unit,
)

@Composable
fun rememberTransferController(viewModel: TransferViewModel, snackbarHostState: SnackbarHostState): TransferController {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
        viewModel::onSaveLocation,
    )
    // Shared files often arrive with a generic type, so don't filter too strictly.
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), viewModel::openImport)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is TransferEvent.Message -> snackbarHostState.showSnackbar(event.text)
                is TransferEvent.PickSaveLocation -> saveLauncher.launch(event.fileName)
                is TransferEvent.Share -> {
                    val send = Intent(Intent.ACTION_SEND)
                        .setType("application/json")
                        .putExtra(Intent.EXTRA_STREAM, event.uri)
                        .putExtra(Intent.EXTRA_SUBJECT, event.fileName)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    send.clipData = ClipData.newRawUri(event.fileName, event.uri)
                    try {
                        context.startActivity(Intent.createChooser(send, "Send flows"))
                    } catch (_: ActivityNotFoundException) {
                        snackbarHostState.showSnackbar("No app available to share with.")
                    }
                }
            }
        }
    }

    uiState.pendingExport?.let { payload ->
        ExportChoiceDialog(
            payload = payload,
            onShare = viewModel::shareExport,
            onSave = viewModel::saveExport,
            onDismiss = viewModel::dismissExport,
        )
    }
    uiState.importPreview?.let { preview ->
        ImportPreviewDialog(preview = preview, onConfirm = viewModel::confirmImport, onDismiss = viewModel::dismissImport)
    }

    return remember(viewModel) {
        TransferController(
            exportAll = { viewModel.export() },
            exportFlow = { id -> viewModel.export(setOf(id)) },
            import = { openLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
        )
    }
}
