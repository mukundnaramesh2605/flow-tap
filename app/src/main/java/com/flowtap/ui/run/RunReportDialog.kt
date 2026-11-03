package com.flowtap.ui.run

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.flowtap.domain.run.Fix
import com.flowtap.domain.run.RunState
import com.flowtap.domain.run.StepOutcome
import com.flowtap.domain.run.StepResult
import com.flowtap.ui.common.title

/** Step-by-step results of a run, with a Fix button on anything FlowTap can help resolve. */
@Composable
fun RunReportDialog(report: RunState.Finished, onDismiss: () -> Unit) {
    val onFix = rememberFixLauncher()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(report.flowName) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (report.outcomes.isEmpty()) {
                    item { Text("This flow has no actions yet.") }
                }
                items(report.outcomes) { outcome ->
                    OutcomeRow(outcome, onFix = onFix)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun OutcomeRow(outcome: StepOutcome, onFix: (Fix) -> Unit) {
    val (icon, tint, message) = outcome.result.presentation()
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp).padding(top = 2.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("${outcome.index + 1}. ${outcome.action.title}", style = MaterialTheme.typography.titleSmall)
            if (message != null) {
                Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val fix = (outcome.result as? StepResult.Blocked)?.fix
            if (fix != null) {
                FilledTonalButton(onClick = { onFix(fix) }, modifier = Modifier.padding(top = 6.dp)) { Text("Fix") }
            }
        }
    }
}

@Composable
private fun StepResult.presentation(): Triple<ImageVector, Color, String?> = when (this) {
    StepResult.Done -> Triple(Icons.Rounded.CheckCircle, MaterialTheme.colorScheme.primary, null)
    is StepResult.HandedOff -> Triple(Icons.Rounded.TouchApp, MaterialTheme.colorScheme.tertiary, message)
    is StepResult.Blocked -> Triple(Icons.Rounded.Warning, MaterialTheme.colorScheme.error, message)
    is StepResult.Failed -> Triple(Icons.Rounded.Error, MaterialTheme.colorScheme.error, message)
}

/** One line for a snackbar, e.g. "Bedtime: Wi-Fi needs you" or "Bedtime: 2 steps need attention". */
fun RunState.Finished.snackbarText(): String {
    val issues = issues
    return when (issues.size) {
        0 -> "$flowName: done"
        1 -> "$flowName · ${issues.single().action.title}: ${issues.single().result.shortText()}"
        else -> "$flowName: ${issues.size} steps need attention"
    }
}

private fun StepResult.shortText(): String = when (this) {
    StepResult.Done -> "done"
    is StepResult.HandedOff -> message.substringBefore(" — ").substringBefore(". ")
    is StepResult.Blocked -> message
    is StepResult.Failed -> message
}
