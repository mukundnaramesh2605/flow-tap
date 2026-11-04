package com.flowtap.ui.settings

import android.content.ActivityNotFoundException
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flowtap.BuildConfig
import com.flowtap.system.shizuku.ShizukuStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    onImport: () -> Unit,
    onExportAll: () -> Unit,
    onOpenPermissions: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbarHostState.showSnackbar(it) } }

    Scaffold(
        modifier = Modifier
            .padding(bottom = contentPadding.calculateBottomPadding())
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { LargeTopAppBar(title = { Text("Settings") }, scrollBehavior = scrollBehavior) },
    ) { innerPadding ->
        LazyColumn(contentPadding = PaddingValues(top = innerPadding.calculateTopPadding(), bottom = 24.dp)) {
            item { SectionLabel("Shizuku") }
            item { ShizukuCard(viewModel) }
            item { SectionLabel("Access") }
            item {
                SettingRow(Icons.Rounded.Security, "Permissions", "What FlowTap can do, and why it asks", onOpenPermissions, chevron = true)
            }
            item { SectionLabel("Your flows") }
            item {
                SettingRow(Icons.Rounded.IosShare, "Export all flows", "Share a file to set up the other phone", onExportAll)
            }
            item { SettingRow(Icons.Rounded.Download, "Import flows", "Add flows from an exported file", onImport) }
            item {
                SettingRow(Icons.Rounded.RestartAlt, "Add sample flows", "Bedtime and Driving, as new copies", viewModel::restoreSamples)
            }
            item { SectionLabel("About") }
            item { SettingRow(Icons.Rounded.Info, "FlowTap", "Version ${BuildConfig.VERSION_NAME}") }
        }
    }
}

@Composable
private fun ShizukuCard(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val status by viewModel.shizukuStatus.collectAsStateWithLifecycle()
    val installed by viewModel.shizukuInstalled.collectAsStateWithLifecycle()
    val testResult by viewModel.testResult.collectAsStateWithLifecycle()
    val testing by viewModel.testing.collectAsStateWithLifecycle()
    val (dot, label, detail) = shizukuPresentation(status, installed)

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Terminal, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Box(Modifier.size(12.dp).background(dot, CircleShape))
            }
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (status) {
                    ShizukuStatus.Ready -> FilledTonalButton(onClick = viewModel::testShizuku, enabled = !testing) {
                        if (testing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Text("Test connection")
                    }
                    ShizukuStatus.PermissionNeeded -> FilledTonalButton(onClick = viewModel::allowShizuku) {
                        Text("Allow FlowTap")
                    }
                    ShizukuStatus.NotRunning, ShizukuStatus.Unsupported -> Unit
                }
                val launch = viewModel.shizukuLaunchIntent()
                if (launch != null && status != ShizukuStatus.Ready) {
                    OutlinedButton(onClick = {
                        try {
                            context.startActivity(launch)
                        } catch (_: ActivityNotFoundException) {
                            // Uninstalled since the last refresh; the next resume updates the card.
                        }
                    }) { Text("Open Shizuku") }
                }
            }
            testResult?.takeIf { status == ShizukuStatus.Ready }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.small)
                    .padding(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    Icons.Rounded.RestartAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Shizuku stops every time the phone restarts. After a reboot, open Shizuku and tap Start " +
                        "(via Wireless debugging on Android 11+, or automatically if your phone is rooted). " +
                        "Until then, Wi-Fi and Bluetooth steps open a panel or post a notification instead.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
private fun shizukuPresentation(status: ShizukuStatus, installed: Boolean): Triple<Color, String, String> {
    val green = Color(0xFF2EB872)
    val amber = Color(0xFFF5A623)
    val red = MaterialTheme.colorScheme.error
    return when (status) {
        ShizukuStatus.Ready -> Triple(
            green,
            "Connected",
            "Wi-Fi and Bluetooth toggle silently, and automations can open apps from the background.",
        )
        ShizukuStatus.PermissionNeeded -> Triple(
            amber,
            "Permission needed",
            "Shizuku is running, but FlowTap hasn't been allowed to use it yet.",
        )
        ShizukuStatus.Unsupported -> Triple(
            red,
            "Update Shizuku",
            "This Shizuku version is too old. Update it to v11 or newer.",
        )
        ShizukuStatus.NotRunning -> Triple(
            red,
            if (installed) "Not running" else "Not installed",
            if (installed) {
                "Shizuku is installed but not started."
            } else {
                "Install Shizuku to toggle Wi-Fi and Bluetooth silently. FlowTap works without it, with a tap where needed."
            },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    chevron: Boolean = false,
) {
    ListItem(
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = if (chevron) {
            { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) }
        } else {
            null
        },
    )
}
