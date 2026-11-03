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
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flowtap.domain.model.Trigger
import com.flowtap.ui.common.EmptyState
import com.flowtap.ui.common.brush
import com.flowtap.ui.common.daysLabel
import com.flowtap.ui.common.timeLabel
import com.flowtap.ui.common.vector
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
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

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
                title = "Nothing scheduled",
                message = "Give a flow a schedule and it'll run on its own — like Bedtime at 11 PM on weeknights.",
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
                item { PhaseNotice() }
                items(uiState.items, key = { "${it.flow.id}/${it.trigger.id}" }) { item ->
                    AutomationRow(
                        item = item,
                        onClick = { onOpenFlow(item.flow.id) },
                        onToggle = { viewModel.setEnabled(item.flow.id, item.trigger.id, it) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

@Composable
private fun PhaseNotice() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        Spacer(Modifier.width(10.dp))
        Text(
            "Preview: schedules don't fire yet. The alarm engine lands in phase 4.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun AutomationRow(
    item: AutomationItem,
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
                }
                Text(
                    text = item.nextRun?.let { "Next: ${formatNextRun(it)}" } ?: "Off",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (item.nextRun != null) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline,
                )
            }
            Switch(checked = trigger.enabled, onCheckedChange = onToggle)
        }
    }
}

private fun formatNextRun(at: ZonedDateTime, now: ZonedDateTime = ZonedDateTime.now()): String {
    val until = Duration.between(now, at)
    val relative = when {
        until.toMinutes() < 60 -> "in ${until.toMinutes().coerceAtLeast(1)} min"
        until.toHours() < 24 -> "in ${until.toHours()}h ${until.toMinutesPart()}m"
        else -> at.format(DateTimeFormatter.ofPattern("EEE"))
    }
    val time = at.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    return "$relative · $time"
}
