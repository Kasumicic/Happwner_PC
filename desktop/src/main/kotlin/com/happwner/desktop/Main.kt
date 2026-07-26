package com.happwner.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.happwner.BindMode
import com.happwner.Subscription
import java.awt.EventQueue
import java.awt.Window as AwtWindow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

fun main(args: Array<String>) = application {
    val viewModel = remember { AppViewModel() }
    var visible by remember { mutableStateOf("--minimized" !in args) }
    val text = strings(viewModel.state.settings.language)
    val icon = rememberVectorPainter(Icons.Default.Dns)
    val exiting = remember { AtomicBoolean(false) }
    val nativeWindow = remember { AtomicReference<AwtWindow?>() }
    val windowState = rememberWindowState(size = DpSize(1100.dp, 720.dp))
    val hideWindow: () -> Unit = {
        hideNativeWindow(nativeWindow.get())
        visible = false
    }
    val requestExit: () -> Unit = {
        if (exiting.compareAndSet(false, true)) {
            val finishExit = {
                hideNativeWindow(nativeWindow.getAndSet(null))
                viewModel.close()
                exitApplication()
            }
            if (EventQueue.isDispatchThread()) finishExit() else EventQueue.invokeLater(finishExit)
        }
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.close() }
    }

    DisposableEffect(text.open, text.exit) {
        val tray = DesktopTray(
            tooltip = text.title,
            openLabel = text.open,
            exitLabel = text.exit,
            onOpen = {
                prepareNativeWindowForShow(nativeWindow.get())
                visible = true
            },
            onExit = requestExit,
        )
        runCatching { tray.install() }
        onDispose { tray.close() }
    }

    Window(
        title = text.title,
        icon = icon,
        state = windowState,
        visible = visible,
        resizable = false,
        onCloseRequest = hideWindow,
    ) {
        DisposableEffect(window) {
            nativeWindow.set(window)
            onDispose { nativeWindow.compareAndSet(window, null) }
        }
        LaunchedEffect(visible, window) {
            if (visible) restoreNativeWindowAfterShow(window)
        }
        MaterialTheme {
            AppScreen(viewModel, requestExit)
        }
    }
}

private val isLinuxDesktop: Boolean =
    System.getProperty("os.name").startsWith("Linux", ignoreCase = true)

private fun hideNativeWindow(window: AwtWindow?) {
    if (window == null) return
    if (isLinuxDesktop) runCatching { window.opacity = 0f }
    window.isVisible = false
}

private fun prepareNativeWindowForShow(window: AwtWindow?) {
    if (window != null && isLinuxDesktop) runCatching { window.opacity = 0f }
}

private fun restoreNativeWindowAfterShow(window: AwtWindow) {
    if (isLinuxDesktop) runCatching { window.opacity = 1f }
    window.toFront()
    window.requestFocus()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppScreen(viewModel: AppViewModel, onExit: () -> Unit) {
    val state = viewModel.state
    val text = strings(state.settings.language)
    var edited by remember { mutableStateOf<Subscription?>(null) }
    var pendingDelete by remember { mutableStateOf<Subscription?>(null) }
    var adding by remember { mutableStateOf(false) }
    var portText by remember(state.settings.port) { mutableStateOf(state.settings.port.toString()) }
    val clipboard = LocalClipboardManager.current
    val validatedPort = InputValidator.validPort(portText)

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(text.title) },
            actions = {
                TextButton(onClick = onExit) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, null)
                    Spacer(Modifier.width(6.dp))
                    Text(text.fullExit)
                }
                TextButton(onClick = {
                    val language = if (state.settings.language == "ru") "en" else "ru"
                    viewModel.updateSettings(state.settings.copy(language = language))
                }) { Text(if (state.settings.language == "ru") "EN" else "RU") }
            },
        )
    }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(text.server, style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (viewModel.serverRunning) "${text.running}: ${viewModel.activeBaseUrl()}" else text.stopped,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Switch(
                            checked = state.settings.serverEnabled,
                            onCheckedChange = { viewModel.updateSettings(state.settings.copy(serverEnabled = it)) },
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = state.settings.bindMode == BindMode.LAN,
                            onCheckedChange = {
                                viewModel.updateSettings(state.settings.copy(bindMode = if (it) BindMode.LAN else BindMode.LOCAL))
                            },
                        )
                        Text(text.lanMode)
                        Spacer(Modifier.width(20.dp))
                        OutlinedTextField(
                            value = portText,
                            onValueChange = { portText = it.filter(Char::isDigit).take(5) },
                            label = { Text(text.port) },
                            isError = validatedPort == null,
                            supportingText = {
                                if (validatedPort == null) Text(text.invalidPort)
                            },
                            singleLine = true,
                            modifier = Modifier.width(130.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(
                            enabled = validatedPort != null,
                            onClick = {
                                validatedPort?.let {
                                    viewModel.updateSettings(state.settings.copy(port = it))
                                }
                            },
                        ) { Text(text.apply) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = state.settings.launchAtLogin,
                            onCheckedChange = {
                                viewModel.updateSettings(state.settings.copy(launchAtLogin = it), updateAutostart = true)
                            },
                        )
                        Text(text.autostart)
                    }
                    if (state.settings.bindMode == BindMode.LAN) {
                        Text(text.lanWarning, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        val addresses = NetworkAddresses.privateIpv4()
                        if (addresses.isNotEmpty()) Text(addresses.joinToString("  •  ") { "http://$it:${state.settings.port}" })
                    }
                    viewModel.serverError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${text.server}: ${state.subscriptions.size}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Button(onClick = { adding = true }) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text(text.add)
                }
            }
            HorizontalDivider()
            if (state.subscriptions.isEmpty()) {
                Text(text.empty, modifier = Modifier.padding(16.dp))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.subscriptions, key = { it.id }) { subscription ->
                        SubscriptionCard(
                            subscription = subscription,
                            baseUrl = viewModel.activeBaseUrl(),
                            text = text,
                            onCopy = { clipboard.setText(AnnotatedString("${viewModel.activeBaseUrl()}/sub/${subscription.id}")) },
                            checkState = viewModel.subscriptionChecks[subscription.id],
                            lastRequest = viewModel.lastSubscriptionRequests[subscription.id],
                            onCheck = { viewModel.checkSubscription(subscription) },
                            onEdit = { edited = subscription },
                            onDelete = { pendingDelete = subscription },
                        )
                    }
                }
            }
        }
    }

    if (adding || edited != null) {
        SubscriptionDialog(
            initial = edited,
            text = text,
            onDismiss = { adding = false; edited = null },
            onSave = {
                viewModel.saveSubscription(it)
                adding = false
                edited = null
            },
        )
    }

    pendingDelete?.let { subscription ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(text.deleteSubscriptionTitle) },
            text = { Text(text.deleteSubscriptionMessage.format(subscription.name)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSubscription(subscription.id)
                        pendingDelete = null
                    },
                ) {
                    Text(text.delete)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(text.cancel)
                }
            },
        )
    }
}

@Composable
private fun SubscriptionCard(
    subscription: Subscription,
    baseUrl: String,
    text: UiStrings,
    onCopy: () -> Unit,
    checkState: SubscriptionCheckState?,
    lastRequest: SubscriptionRequestRecord?,
    onCheck: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(subscription.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "$baseUrl/sub/${subscription.id}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
                lastRequest?.let {
                    Text(
                        "${text.lastRequest}: ${formatRequestTime(it.completedAtMillis)} • ${requestResultText(it, text)}",
                        color = if (it.error != null || it.profileCount == 0) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                checkState?.let {
                    Text(
                        checkResultText(it, text),
                        color = if (it is SubscriptionCheckState.Error || it is SubscriptionCheckState.NoProfiles) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    checkResponsePreview(it)?.takeIf(String::isNotBlank)?.let { preview ->
                        Text(
                            "${text.response}: $preview",
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            TextButton(
                enabled = checkState !is SubscriptionCheckState.Running,
                onClick = onCheck,
            ) { Text(if (checkState is SubscriptionCheckState.Running) text.checking else text.check) }
            IconButton(onClick = onCopy) { Icon(Icons.Default.ContentCopy, text.copy) }
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, text.edit) }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, text.delete) }
        }
    }
}

private fun checkResultText(state: SubscriptionCheckState, text: UiStrings): String = when (state) {
    SubscriptionCheckState.Running -> text.checking
    is SubscriptionCheckState.Success -> {
        val source = state.statusCode?.let { "HTTP $it" } ?: text.localData
        val protocols = state.inspection.protocols.entries.joinToString { "${it.key}: ${it.value}" }
        "${text.checkSuccess}: $source • ${formatBytes(state.sizeBytes)} • ${text.profiles}: ${state.inspection.profileCount} ($protocols)"
    }
    is SubscriptionCheckState.NoProfiles -> {
        val source = state.statusCode?.let { "HTTP $it" } ?: text.localData
        "$source • ${formatBytes(state.sizeBytes)} • ${text.noProfiles}"
    }
    is SubscriptionCheckState.Error -> "${text.checkFailed}: ${state.message}"
}

private fun checkResponsePreview(state: SubscriptionCheckState): String? = when (state) {
    is SubscriptionCheckState.Success -> state.inspection.preview
    is SubscriptionCheckState.NoProfiles -> state.preview
    else -> null
}

private fun formatBytes(bytes: Int): String = when {
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

private val requestTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(ZoneId.systemDefault())

private fun formatRequestTime(timestampMillis: Long): String =
    requestTimeFormatter.format(Instant.ofEpochMilli(timestampMillis))

private fun requestResultText(record: SubscriptionRequestRecord, text: UiStrings): String {
    record.error?.let { return "${text.checkFailed}: $it" }
    val source = record.statusCode?.let { "HTTP $it" } ?: text.localData
    val size = record.sizeBytes?.let(::formatBytes)
    val profiles = record.profileCount?.let { "${text.profiles}: $it" }
    return listOfNotNull(source, size, profiles).joinToString(" • ")
}

@Composable
private fun SubscriptionDialog(
    initial: Subscription?,
    text: UiStrings,
    onDismiss: () -> Unit,
    onSave: (Subscription) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var source by remember { mutableStateOf(initial?.source.orEmpty()) }
    var hwid by remember { mutableStateOf(initial?.hwid.orEmpty()) }
    var userAgent by remember { mutableStateOf(initial?.userAgent ?: "Happ/1.0") }
    var enabled by remember { mutableStateOf(initial?.enabled ?: true) }
    var decodeBase64 by remember { mutableStateOf(initial?.decodeBase64 ?: false) }
    var jsonToUri by remember { mutableStateOf(initial?.jsonToUri ?: false) }
    var xrayToSingBox by remember { mutableStateOf(initial?.xrayToSingBox ?: false) }
    val sourceIssue = InputValidator.sourceIssue(source)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) text.add else text.edit) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(text.name) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = source,
                    onValueChange = { source = it },
                    label = { Text(text.source) },
                    isError = source.isNotBlank() && sourceIssue != null,
                    supportingText = {
                        if (source.isNotBlank() && sourceIssue != null) Text(text.invalidSource)
                    },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(hwid, { hwid = it }, label = { Text("HWID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(userAgent, { userAgent = it }, label = { Text(text.ua) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                CheckRow(enabled, { enabled = it }, text.enabled)
                CheckRow(decodeBase64, { decodeBase64 = it }, text.decodeBase64)
                CheckRow(jsonToUri, { jsonToUri = it }, text.jsonToUri)
                CheckRow(xrayToSingBox, { xrayToSingBox = it }, text.xrayToSingBox)
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank() && sourceIssue == null,
                onClick = {
                    onSave(
                        Subscription(
                            id = initial?.id ?: java.util.UUID.randomUUID().toString(),
                            name = name.trim(),
                            source = source.trim(),
                            hwid = hwid.trim(),
                            userAgent = userAgent.trim(),
                            enabled = enabled,
                            decodeBase64 = decodeBase64,
                            jsonToUri = jsonToUri,
                            xrayToSingBox = xrayToSingBox,
                        ),
                    )
                },
            ) { Text(text.save) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(text.cancel) } },
    )
}

@Composable
private fun CheckRow(checked: Boolean, onChecked: (Boolean) -> Unit, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onChecked)
        Text(label)
    }
}
