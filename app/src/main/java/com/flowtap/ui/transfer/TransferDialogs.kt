package com.flowtap.ui.transfer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.flowtap.data.transfer.ExportPayload
import com.flowtap.data.transfer.ImportCandidate
import com.flowtap.data.transfer.ImportPreview
import com.flowtap.domain.model.TapFlow
import com.flowtap.ui.common.brush
import com.flowtap.ui.common.vector

@Composable
fun ExportChoiceDialog(
    payload: ExportPayload,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.IosShare, contentDescription = null) },
        title = { Text(if (payload.flowCount == 1) "Export flow" else "Export ${payload.flowCount} flows") },
        text = {
            Text(
                "Send the file to the other phone (Quick Share, Messages, email…) and open it there " +
                    "with FlowTap. Schedules are included.",
            )
        },
        confirmButton = {
            Button(onClick = onShare) {
                Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Share")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onSave) {
                Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Save to file")
            }
        },
    )
}

@Composable
fun ImportPreviewDialog(
    preview: ImportPreview,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val count = preview.candidates.size
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Download, contentDescription = null) },
        title = { Text(if (count == 1) "Import 1 flow?" else "Import $count flows?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "They'll be added alongside your existing flows. Imported schedules keep their on/off state.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(preview.candidates) { CandidateRow(it) }
                }
                if (preview.skippedFlows > 0) {
                    Warning("${preview.skippedFlows} unreadable flow(s) in the file will be skipped.")
                }
            }
        },
        confirmButton = { Button(onClick = onConfirm) { Text("Import") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CandidateRow(candidate: ImportCandidate) {
    val notes = buildList {
        if (candidate.missingApps.isNotEmpty()) {
            add("Not installed here: ${candidate.missingApps.joinToString()}")
        }
        if (candidate.skippedActions > 0) add("${candidate.skippedActions} unsupported action(s) skipped")
        if (candidate.skippedTriggers > 0) add("${candidate.skippedTriggers} unsupported schedule(s) skipped")
    }
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { FlowBadge(candidate.flow) },
        headlineContent = { Text(candidate.flow.name) },
        supportingContent = {
            Column {
                val flow = candidate.flow
                Text("${flow.actions.size} actions · ${flow.triggers.size} schedules")
                notes.forEach { Warning(it) }
            }
        },
    )
}

@Composable
private fun FlowBadge(flow: TapFlow) {
    Box(
        Modifier.size(40.dp).clip(MaterialTheme.shapes.small).background(flow.color.brush),
        contentAlignment = Alignment.Center,
    ) {
        Icon(flow.icon.vector, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun Warning(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            Icons.Rounded.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(14.dp).padding(top = 2.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
    }
}
