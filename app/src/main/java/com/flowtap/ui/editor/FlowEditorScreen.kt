package com.flowtap.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flowtap.domain.model.FlowAction
import com.flowtap.domain.model.FlowColor
import com.flowtap.domain.model.FlowIcon
import com.flowtap.domain.model.Trigger
import com.flowtap.ui.common.brush
import com.flowtap.ui.common.daysLabel
import com.flowtap.ui.common.icon
import com.flowtap.ui.common.primary
import com.flowtap.ui.common.summary
import com.flowtap.ui.common.timeLabel
import com.flowtap.ui.common.title
import com.flowtap.ui.common.vector
import com.flowtap.ui.run.RunReportDialog
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlowEditorScreen(
    onBack: () -> Unit,
    onExport: (flowId: Long) -> Unit,
    viewModel: FlowEditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val testReport by viewModel.testReport.collectAsStateWithLifecycle()
    val testing by viewModel.testing.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { onBack() }
    }
    val requestBack = { if (uiState.hasChanges) confirmDiscard = true else onBack() }
    BackHandler(enabled = uiState.hasChanges) { confirmDiscard = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isNew) "New flow" else "Edit flow") },
                navigationIcon = {
                    IconButton(onClick = requestBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::save, enabled = uiState.canSave) {
                        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Save")
                    }
                    if (!uiState.isNew) {
                        EditorOverflow(
                            saved = !uiState.hasChanges,
                            testing = testing,
                            onTest = viewModel::testAsAutomation,
                            onExport = { onExport(viewModel.flowId) },
                            onDelete = { confirmDelete = true },
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize().padding(innerPadding), Alignment.Center) { CircularProgressIndicator() }
        } else {
            EditorList(uiState, innerPadding, viewModel)
        }
    }

    if (uiState.showActionPicker) {
        ActionPickerSheet(onPick = viewModel::startNewAction, onDismiss = { viewModel.showActionPicker(false) })
    }
    uiState.actionEdit?.let { edit ->
        ActionConfigSheet(
            edit = edit,
            apps = apps,
            loadApps = viewModel::loadApps,
            loadIcon = viewModel::appIcon,
            onDone = viewModel::commitAction,
            onDelete = edit.key?.let { key -> { viewModel.deleteAction(key) } },
            onDismiss = viewModel::dismissActionEdit,
        )
    }
    uiState.scheduleEdit?.let { edit ->
        ScheduleDialog(
            edit = edit,
            onSave = viewModel::commitSchedule,
            onDelete = edit.triggerId?.let { id -> { viewModel.deleteSchedule(id) } },
            onDismiss = viewModel::dismissScheduleEdit,
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
            title = { Text("Delete “${uiState.name}”?") },
            text = { Text("This removes the flow and its schedules. This can't be undone.") },
            confirmButton = {
                Button(
                    onClick = { confirmDelete = false; viewModel.delete() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
    testReport?.let { RunReportDialog(it, onDismiss = viewModel::dismissTestReport) }
    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard changes?") },
            text = { Text("You have unsaved changes to this flow.") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onBack() }) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } },
        )
    }
}

@Composable
private fun EditorOverflow(
    saved: Boolean,
    testing: Boolean,
    onTest: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            if (testing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Icon(Icons.Rounded.MoreVert, contentDescription = "More")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(if (saved) "Test as automation" else "Save to test as automation") },
                leadingIcon = { Icon(Icons.Rounded.Science, contentDescription = null) },
                enabled = saved && !testing,
                onClick = { open = false; onTest() },
            )
            DropdownMenuItem(
                text = { Text(if (saved) "Export flow" else "Save to export") },
                leadingIcon = { Icon(Icons.Rounded.IosShare, contentDescription = null) },
                enabled = saved,
                onClick = { open = false; onExport() },
            )
            DropdownMenuItem(
                text = { Text("Delete flow") },
                leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                onClick = { open = false; onDelete() },
            )
        }
    }
}

@Composable
private fun EditorList(uiState: EditorUiState, innerPadding: PaddingValues, viewModel: FlowEditorViewModel) {
    val haptics = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        viewModel.moveAction(from.key, to.key)
        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(
            top = innerPadding.calculateTopPadding() + 8.dp,
            bottom = innerPadding.calculateBottomPadding() + 32.dp,
        ),
    ) {
        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                FlowPreview(uiState.icon, uiState.color)
                OutlinedTextField(
                    value = uiState.name,
                    onValueChange = viewModel::setName,
                    label = { Text("Flow name") },
                    placeholder = { Text("e.g. Bedtime") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
                Column {
                    SectionHeader("Color")
                    ColorPicker(selected = uiState.color, onSelect = viewModel::setColor)
                }
                Column {
                    SectionHeader("Icon")
                    IconPicker(selected = uiState.icon, accent = uiState.color, onSelect = viewModel::setIcon)
                }
                SectionHeader(
                    if (uiState.actions.isEmpty()) "Actions" else "Actions · ${uiState.actions.size}",
                    hint = if (uiState.actions.size > 1) "Drag ≡ to reorder" else null,
                )
            }
        }

        if (uiState.actions.isEmpty()) {
            item(key = "actions-empty") {
                PlaceholderCard("No actions yet. Add a few — they run top to bottom when the flow runs.")
            }
        }
        items(uiState.actions, key = { it.key }) { draft ->
            ReorderableItem(reorderState, key = draft.key) { isDragging ->
                val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "dragElevation")
                ActionRow(
                    index = uiState.actions.indexOf(draft),
                    action = draft.action,
                    appMissing = (draft.action as? FlowAction.OpenApp)?.packageName in uiState.missingPackages,
                    elevation = elevation,
                    onClick = { viewModel.startEditAction(draft.key) },
                    onDelete = { viewModel.deleteAction(draft.key) },
                    dragHandle = {
                        Icon(
                            Icons.Rounded.DragHandle,
                            contentDescription = "Reorder",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .draggableHandle(
                                    onDragStarted = { haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate) },
                                    onDragStopped = { haptics.performHapticFeedback(HapticFeedbackType.GestureEnd) },
                                )
                                .padding(8.dp),
                        )
                    },
                )
            }
        }
        item(key = "add-action") {
            FilledTonalButton(
                onClick = { viewModel.showActionPicker(true) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add action")
            }
        }

        item(key = "schedules") {
            Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("Schedules", hint = "Fires automatically from phase 4")
                if (uiState.triggers.isEmpty()) {
                    PlaceholderCard("Runs only when you tap it. Add a schedule to run it automatically.")
                }
                uiState.triggers.forEach { trigger ->
                    TriggerRow(
                        trigger = trigger,
                        onClick = { viewModel.startEditSchedule(trigger.id) },
                        onToggle = { viewModel.setScheduleEnabled(trigger.id, it) },
                    )
                }
                OutlinedButton(
                    onClick = viewModel::startNewSchedule,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) {
                    Icon(Icons.Rounded.Schedule, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add schedule")
                }
            }
        }
    }
}

@Composable
private fun FlowPreview(icon: FlowIcon, color: FlowColor) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(96.dp).clip(MaterialTheme.shapes.large).background(color.brush),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon.vector, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
        }
    }
}

@Composable
private fun SectionHeader(text: String, hint: String? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.weight(1f))
        if (hint != null) {
            Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ActionRow(
    index: Int,
    action: FlowAction,
    appMissing: Boolean,
    elevation: Dp,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    dragHandle: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        onClick = onClick,
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            dragHandle()
            Box(
                Modifier.size(36.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(action.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
                Text("${index + 1}. ${action.title}", style = MaterialTheme.typography.titleSmall)
                Text(action.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                if (appMissing) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                        Icon(Icons.Rounded.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Not installed on this phone", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.Close, contentDescription = "Remove ${action.title}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TriggerRow(trigger: Trigger, onClick: () -> Unit, onToggle: (Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                when (trigger) {
                    is Trigger.TimeOfDay -> {
                        Text(trigger.timeLabel(), style = MaterialTheme.typography.titleMedium)
                        Text(trigger.daysLabel(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Switch(checked = trigger.enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun ColorPicker(selected: FlowColor, onSelect: (FlowColor) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(FlowColor.entries) { color ->
            val isSelected = color == selected
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color.brush)
                    .then(
                        if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        else Modifier,
                    )
                    .clickable(onClickLabel = color.name) { onSelect(color) },
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IconPicker(selected: FlowIcon, accent: FlowColor, onSelect: (FlowIcon) -> Unit) {
    FlowRow(
        modifier = Modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FlowIcon.entries.forEach { icon ->
            val isSelected = icon == selected
            val bg by animateColorAsState(
                if (isSelected) accent.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                label = "iconBg",
            )
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(bg)
                    .clickable(onClickLabel = icon.name) { onSelect(icon) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon.vector,
                    contentDescription = icon.name,
                    tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PlaceholderCard(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(MaterialTheme.shapes.medium)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
            .padding(16.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
