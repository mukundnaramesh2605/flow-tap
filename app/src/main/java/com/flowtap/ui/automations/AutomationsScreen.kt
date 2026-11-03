package com.flowtap.ui.automations

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.TimerOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flowtap.automation.HealthIssue
import com.flowtap.domain.model.Trigger
import com.flowtap.ui.common.EmptyState
import com.flowtap.ui.common.brush
import com.flowtap.ui.common.daysLabel
import com.flowtap.ui.common.eventLabel
import com.flowtap.ui.common.timeLabel
import com.flowtap.ui.common.vector
import com.flowtap.ui.run.rememberFixLauncher
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationsScreen(
    contentPadding: PaddingValues,
    onOpenFlow: (Long) -> Unit,
    viewModel: AutomationsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val issues by viewModel.issues.collectAsStateWithLifecycle()
    val showBatteryPrompt by viewModel.showBatteryPrompt.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val launchFix = rememberFixLauncher()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshHealth() }
    // Toggles change which checks matter (e.g. the first enabled schedule brings up battery advice).
    LaunchedEffect(uiState.items.map { it.trigger.enabled }) { viewModel.refreshHealth() }
    LaunchedEffect(viewModel) { viewModel.fixes.collect(launchFix) }

    Scaffold(
        modifier = Modifier
            .padding(bottom = contentPadding.calculateBottomPadding())
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = { LargeTopAppBar(title = { Text("Automations") }, scrollBehavior = scrollBehavior) },
    ) { innerPadding ->
        when {
            uiState.isLoading -> Box(Modifier.fillMaxSize().padding(innerPadding), Alignment.Center) {
                CircularProgressIndicator()
            }
            uiState.items.isEmpty() -> EmptyState(
                icon = Icons.Rounded.Schedule,
                title = "Nothing automated yet",
                message = "Open a flow and add a schedule or a Bluetooth trigger, and it'll run on its own — " +
                    "like Bedtime at 11 PM on weeknights, or Driving when the car connects.",
                modifier = Modifier.padding(innerPadding),
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = innerPadding.calculateTopPadding() + 4.dp,
                    bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(issues, key = { "issue-${it.kind}" }) { issue ->
                    HealthCard(issue, onFix = { viewModel.fix(issue) }, modifier = Modifier.animateItem())
                }
                items(uiState.items, key = { "${it.flow.id}/${it.trigger.id}" }) { item ->
                    AutomationRow(
                        item = item,
                        now = uiState.now,
                        onClick = { onOpenFlow(item.flow.id) },
                        onToggle = { viewModel.setEnabled(item.flow.id, item.trigger.id, it) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }

    if (showBatteryPrompt) {
        AlertDialog(
            onDismissRequest = { viewModel.answerBatteryPrompt(allow = false) },
            icon = { Icon(Icons.Rounded.BatteryAlert, contentDescription = null) },
            title = { Text("Keep automations on time?") },
            text = {
                Text(
                    "Android puts apps to sleep to save battery, and that can delay or skip your schedules — " +
                        "Bedtime at 11 PM might not happen until you next pick up the phone.\n\n" +
                        "Letting FlowTap run unrestricted fixes this. It doesn't run in the background otherwise; " +
                        "it only wakes when one of your triggers fires.",
                )
            },
            confirmButton = { Button(onClick = { viewModel.answerBatteryPrompt(allow = true) }) { Text("Allow") } },
            dismissButton = { TextButton(onClick = { viewModel.answerBatteryPrompt(allow = false) }) { Text("Not now") } },
        )
    }
}

@Composable
private fun HealthCard(issue: HealthIssue, onFix: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(issue.kind.icon, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(issue.title, style = MaterialTheme.typography.titleSmall)
                Text(issue.detail, style = MaterialTheme.typography.bodySmall)
                if (issue.fix != null || issue.silentFix) {
                    FilledTonalButton(onClick = onFix, modifier = Modifier.padding(top = 8.dp)) {
                        Text(if (issue.silentFix) "Fix with Shizuku" else "Fix")
                    }
                }
            }
        }
    }
}

private val HealthIssue.Kind.icon: ImageVector
    get() = when (this) {
        HealthIssue.Kind.BatteryOptimized -> Icons.Rounded.BatteryAlert
        HealthIssue.Kind.InexactAlarms -> Icons.Rounded.TimerOff
        HealthIssue.Kind.NotificationsOff -> Icons.Rounded.NotificationsOff
        HealthIssue.Kind.BluetoothPermission -> Icons.Rounded.BluetoothDisabled
        HealthIssue.Kind.ShizukuDown -> Icons.Rounded.Terminal
    }

@Composable
private fun AutomationRow(
    item: AutomationItem,
    now: ZonedDateTime,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val trigger = item.trigger
    Card(
        modifier = modifier.fillMaxWidth().clip(CardDefaults.shape).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(MaterialTheme.shapes.small).background(item.flow.color.brush),
                contentAlignment = Alignment.Center,
            ) {
                Icon(item.flow.icon.vector, contentDescription = null, tint = Color.White)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                when (trigger) {
                    is Trigger.TimeOfDay -> {
                        Text(trigger.timeLabel(), style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "${item.flow.name} · ${trigger.daysLabel()}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    is Trigger.BluetoothDevice -> {
                        Text(trigger.deviceName, style = MaterialTheme.typography.titleLarge)
                        Text(
                            "${item.flow.name} · ${trigger.eventLabel()}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                val status = when {
                    !trigger.enabled -> "Off"
                    item.nextRun != null -> "Next: ${formatNextRun(item.nextRun, now)}"
                    trigger is Trigger.BluetoothDevice -> "Listening"
                    else -> "No days selected"
                }
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (trigger.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                )
            }
            Switch(checked = trigger.enabled, onCheckedChange = onToggle)
        }
    }
}

private fun formatNextRun(at: ZonedDateTime, now: ZonedDateTime): String {
    val until = Duration.between(now, at)
    val relative = when {
        until.toMinutes() < 60 -> "in ${until.toMinutes().coerceAtLeast(1)} min"
        until.toHours() < 24 -> "in ${until.toHours()}h ${until.toMinutes() % 60}m"
        else -> at.format(DateTimeFormatter.ofPattern("EEE"))
    }
    val time = at.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    return "$relative · $time"
}
