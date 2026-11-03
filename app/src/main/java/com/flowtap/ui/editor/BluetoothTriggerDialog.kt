package com.flowtap.ui.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.flowtap.domain.model.Trigger

/** Pick a paired device and whether to run when it connects or disconnects. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothTriggerDialog(
    edit: BluetoothEdit,
    onSave: (address: String, deviceName: String, event: Trigger.ConnectionEvent) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
    devicesViewModel: PairedDevicesViewModel = hiltViewModel(),
) {
    // Re-read after the permission prompt returns.
    var refresh by remember { mutableIntStateOf(0) }
    val devices = remember(refresh) { devicesViewModel.devices() }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    var selected by rememberSaveable(edit) { mutableStateOf(edit.address) }
    var event by rememberSaveable(edit) { mutableStateOf(edit.event) }
    val neededPermission = devicesViewModel.permission

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (edit.triggerId == null) "Bluetooth trigger" else "Edit Bluetooth trigger") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    Trigger.ConnectionEvent.entries.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = event == option,
                            onClick = { event = option },
                            shape = SegmentedButtonDefaults.itemShape(index, Trigger.ConnectionEvent.entries.size),
                        ) { Text(if (option == Trigger.ConnectionEvent.Connected) "Connects" else "Disconnects") }
                    }
                }
                when {
                    devices == null && neededPermission != null -> {
                        Text(
                            "FlowTap needs the Nearby devices permission to see your paired devices and to " +
                                "notice when they connect.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        FilledTonalButton(onClick = { permission.launch(neededPermission) }) {
                            Text("Allow")
                        }
                    }
                    devices.isNullOrEmpty() -> Text(
                        "No paired devices. Pair your car or headphones in Bluetooth settings first.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    else -> LazyColumn(Modifier.heightIn(max = 300.dp)) {
                        items(devices, key = { it.address }) { device ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = selected == device.address,
                                        onClick = { selected = device.address },
                                        role = Role.RadioButton,
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = selected == device.address, onClick = null)
                                Column {
                                    Text(device.name, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        device.address,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val device = devices?.find { it.address == selected }
            Button(onClick = { device?.let { onSave(it.address, it.name, event) } }, enabled = device != null) {
                Text("Save")
            }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
