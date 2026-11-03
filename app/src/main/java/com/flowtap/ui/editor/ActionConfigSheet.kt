package com.flowtap.ui.editor

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.flowtap.data.apps.InstalledApp
import com.flowtap.domain.model.FlowAction
import com.flowtap.domain.model.RingerMode
import com.flowtap.domain.model.ToggleState
import com.flowtap.ui.common.MAX_DELAY_SECONDS
import com.flowtap.ui.common.icon
import com.flowtap.ui.common.kind
import com.flowtap.ui.common.validationError

/**
 * Edits one action's parameters on a local copy; nothing reaches the flow until Done.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionConfigSheet(
    edit: ActionEdit,
    apps: List<InstalledApp>?,
    loadApps: () -> Unit,
    loadIcon: suspend (packageName: String, sizePx: Int) -> ImageBitmap?,
    onDone: (FlowAction) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var draft by remember(edit) { mutableStateOf(edit.action) }
    val error = draft.validationError

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(draft.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(draft.kind.title, style = MaterialTheme.typography.titleLarge)
                    Text(
                        draft.kind.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            when (val action = draft) {
                is FlowAction.OpenUrl -> OutlinedTextField(
                    value = action.url,
                    onValueChange = { draft = action.copy(url = it.trim()) },
                    label = { Text("Web address") },
                    placeholder = { Text("https://example.com") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                is FlowAction.OpenApp -> AppPicker(
                    selected = action.packageName,
                    apps = apps,
                    loadApps = loadApps,
                    loadIcon = loadIcon,
                    onSelect = { draft = FlowAction.OpenApp(it.packageName, it.label) },
                )
                is FlowAction.SetWifi -> ToggleChooser(action.state) { draft = action.copy(state = it) }
                is FlowAction.SetBluetooth -> ToggleChooser(action.state) { draft = action.copy(state = it) }
                is FlowAction.SetTorch -> ToggleChooser(action.state) { draft = action.copy(state = it) }
                is FlowAction.SetRinger -> Segmented(
                    options = RingerMode.entries,
                    selected = action.mode,
                    label = { it.name },
                    onSelect = { draft = action.copy(mode = it) },
                )
                is FlowAction.SetBrightness -> Column {
                    Text("${action.percent}%", style = MaterialTheme.typography.headlineMedium)
                    Slider(
                        value = action.percent.toFloat(),
                        onValueChange = { draft = action.copy(percent = it.toInt()) },
                        valueRange = 0f..100f,
                    )
                    Text(
                        "Also turns off adaptive brightness so the level sticks.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                is FlowAction.SendSms -> {
                    PhoneField(action.phoneNumber) { draft = action.copy(phoneNumber = it) }
                    OutlinedTextField(
                        value = action.message,
                        onValueChange = { draft = action.copy(message = it) },
                        label = { Text("Message") },
                        minLines = 3,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                is FlowAction.PhoneCall -> PhoneField(action.phoneNumber) { draft = action.copy(phoneNumber = it) }
                is FlowAction.ShowMessage -> OutlinedTextField(
                    value = action.text,
                    onValueChange = { draft = action.copy(text = it) },
                    label = { Text("Message") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                is FlowAction.Delay -> DelayChooser(action.seconds) { draft = action.copy(seconds = it) }
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
                Spacer(Modifier.weight(1f))
                if (error != null && draft != edit.action) {
                    Text(
                        error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(end = 12.dp).weight(1f, fill = false),
                    )
                }
                Button(onClick = { onDone(draft.normalized()) }, enabled = error == null) {
                    Text(if (edit.key == null) "Add" else "Done")
                }
            }
        }
    }
}

/** Fills in what people usually leave out, like the https:// on a URL. */
private fun FlowAction.normalized(): FlowAction = when (this) {
    is FlowAction.OpenUrl -> if (url.contains("://")) this else copy(url = "https://$url")
    is FlowAction.SendSms -> copy(phoneNumber = phoneNumber.trim(), message = message.trim())
    is FlowAction.PhoneCall -> copy(phoneNumber = phoneNumber.trim())
    is FlowAction.ShowMessage -> copy(text = text.trim())
    else -> this
}

@Composable
private fun ToggleChooser(selected: ToggleState, onSelect: (ToggleState) -> Unit) =
    Segmented(ToggleState.entries, selected, label = { it.name }, onSelect = onSelect)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) { Text(label(option)) }
        }
    }
}

@Composable
private fun PhoneField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text("Phone number") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun DelayChooser(seconds: Int, onChange: (Int) -> Unit) {
    var text by remember { mutableStateOf(seconds.toString()) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { input ->
                text = input.filter(Char::isDigit).take(4)
                onChange(text.toIntOrNull() ?: 0)
            },
            label = { Text("Seconds") },
            supportingText = { Text("Up to $MAX_DELAY_SECONDS seconds (1 hour)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(5, 10, 30, 60).forEach { preset ->
                FilterChip(
                    selected = seconds == preset,
                    onClick = { text = preset.toString(); onChange(preset) },
                    label = { Text("${preset}s") },
                )
            }
        }
    }
}

@Composable
private fun AppPicker(
    selected: String,
    apps: List<InstalledApp>?,
    loadApps: () -> Unit,
    loadIcon: suspend (String, Int) -> ImageBitmap?,
    onSelect: (InstalledApp) -> Unit,
) {
    LaunchedEffect(Unit) { loadApps() }
    var query by remember { mutableStateOf("") }

    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        placeholder = { Text("Search apps") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    if (apps == null) {
        Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val filtered = remember(apps, query) {
        if (query.isBlank()) apps else apps.filter { it.label.contains(query.trim(), ignoreCase = true) }
    }
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
        items(filtered, key = { it.packageName }) { app ->
            ListItem(
                modifier = Modifier.clip(MaterialTheme.shapes.medium).clickable { onSelect(app) },
                colors = ListItemDefaults.colors(
                    containerColor = if (app.packageName == selected) MaterialTheme.colorScheme.secondaryContainer
                    else Color.Transparent,
                ),
                leadingContent = { AppIcon(app.packageName, loadIcon) },
                headlineContent = { Text(app.label) },
                trailingContent = {
                    if (app.packageName == selected) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                    }
                },
            )
        }
    }
}

@Composable
private fun AppIcon(packageName: String, loadIcon: suspend (String, Int) -> ImageBitmap?) {
    val sizePx = with(LocalDensity.current) { 36.dp.roundToPx() }
    val bitmap by produceState<ImageBitmap?>(null, packageName) { value = loadIcon(packageName, sizePx) }
    val current = bitmap
    if (current != null) {
        Image(current, contentDescription = null, modifier = Modifier.size(36.dp))
    } else {
        Icon(Icons.Rounded.Android, contentDescription = null, modifier = Modifier.size(36.dp))
    }
}
