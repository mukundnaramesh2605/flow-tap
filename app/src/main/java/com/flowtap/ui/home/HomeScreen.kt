package com.flowtap.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flowtap.domain.model.TapFlow
import com.flowtap.domain.run.RunState
import com.flowtap.ui.common.EmptyState

@Composable
fun HomeScreen(
    contentPadding: PaddingValues,
    onCreateFlow: () -> Unit,
    onEditFlow: (Long) -> Unit,
    onImport: () -> Unit,
    onExportAll: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val runStates by viewModel.runStates.collectAsStateWithLifecycle()
    HomeContent(
        uiState = uiState,
        runStates = runStates,
        contentPadding = contentPadding,
        onRun = viewModel::run,
        onCreateFlow = onCreateFlow,
        onEditFlow = onEditFlow,
        onImport = onImport,
        onExportAll = onExportAll,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeContent(
    uiState: HomeUiState,
    runStates: Map<Long, RunState>,
    contentPadding: PaddingValues,
    onRun: (TapFlow) -> Unit,
    onCreateFlow: () -> Unit,
    onEditFlow: (Long) -> Unit,
    onImport: () -> Unit,
    onExportAll: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier
            .padding(bottom = contentPadding.calculateBottomPadding())
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            LargeTopAppBar(
                title = { Text("My Flows") },
                actions = { HomeOverflow(hasFlows = uiState.flows.isNotEmpty(), onImport, onExportAll) },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            if (uiState.flows.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onCreateFlow,
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text("New flow") },
                )
            }
        },
    ) { innerPadding ->
        when {
            uiState.isLoading -> Box(Modifier.fillMaxSize().padding(innerPadding), Alignment.Center) {
                CircularProgressIndicator()
            }
            uiState.flows.isEmpty() -> EmptyState(
                icon = Icons.Rounded.AutoAwesome,
                title = "No flows yet",
                message = "A flow is a little recipe of actions — like silencing your phone and dimming " +
                    "the screen at bedtime. Build one and run it with a single tap.",
                actionLabel = "Create your first flow",
                onAction = onCreateFlow,
                secondaryLabel = "Import from the other phone",
                onSecondary = onImport,
                modifier = Modifier.padding(innerPadding),
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 156.dp),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = innerPadding.calculateTopPadding() + 4.dp,
                    bottom = 96.dp, // clear the FAB
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(uiState.flows, key = { it.id }) { flow ->
                    FlowTile(
                        flow = flow,
                        runState = runStates[flow.id],
                        onRun = { onRun(flow) },
                        onEdit = { onEditFlow(flow.id) },
                        modifier = Modifier.animateItem(),
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = "Tap a tile to run it · long-press to edit",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeOverflow(hasFlows: Boolean, onImport: () -> Unit, onExportAll: () -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "More") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Import flows") },
                leadingIcon = { Icon(Icons.Rounded.Download, contentDescription = null) },
                onClick = { open = false; onImport() },
            )
            DropdownMenuItem(
                text = { Text("Export all flows") },
                leadingIcon = { Icon(Icons.Rounded.IosShare, contentDescription = null) },
                enabled = hasFlows,
                onClick = { open = false; onExportAll() },
            )
        }
    }
}
