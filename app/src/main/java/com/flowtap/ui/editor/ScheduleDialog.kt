package com.flowtap.ui.editor

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import com.flowtap.domain.model.Trigger
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** Time + days editor for one schedule. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScheduleDialog(
    edit: ScheduleEdit,
    onSave: (hour: Int, minute: Int, days: Set<Int>) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val timeState = rememberTimePickerState(
        initialHour = edit.hour,
        initialMinute = edit.minute,
        is24Hour = DateFormat.is24HourFormat(context),
    )
    var days by rememberSaveable(edit) { mutableStateOf(edit.days.toList()) }
    // The clock dial needs ~400dp; fall back to text entry on short screens (e.g. landscape).
    val roomy = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() } >= 600.dp

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (edit.triggerId == null) "New schedule" else "Edit schedule") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (roomy) TimePicker(timeState) else TimeInput(timeState)
                Text("Repeat on", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DayOfWeek.entries.forEach { day ->
                        val selected = day.value in days
                        FilterChip(
                            selected = selected,
                            onClick = { days = if (selected) days - day.value else days + day.value },
                            label = { Text(day.getDisplayName(TextStyle.NARROW, Locale.getDefault())) },
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip(onClick = { days = Trigger.TimeOfDay.ALL_DAYS.toList() }, label = { Text("Every day") })
                    AssistChip(onClick = { days = Trigger.TimeOfDay.WEEKDAYS.toList() }, label = { Text("Weekdays") })
                    AssistChip(onClick = { days = Trigger.TimeOfDay.WEEKEND.toList() }, label = { Text("Weekends") })
                }
                if (days.isEmpty()) {
                    Text(
                        "Pick at least one day.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(timeState.hour, timeState.minute, days.toSet()) },
                enabled = days.isNotEmpty(),
            ) { Text("Save") }
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
