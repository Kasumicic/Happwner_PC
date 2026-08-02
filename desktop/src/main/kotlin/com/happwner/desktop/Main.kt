package com.happwner.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.JPopupContextMenuRepresentation
import androidx.compose.foundation.LocalContextMenuRepresentation
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.happwner.BindMode
import com.happwner.DEFAULT_USER_AGENT
import com.happwner.Subscription
import com.happwner.ThemeMode
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import java.awt.EventQueue
import java.awt.Window as AwtWindow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

private const val OMEGAPLEXX_PROFILE_URL = "https://github.com/Omegaplexx"
private const val ORIGINAL_PROJECT_URL = "https://github.com/Omegaplexx/Happwner"
private const val KASUMICIC_PROFILE_URL = "https://github.com/Kasumicic"
private const val DESKTOP_PROJECT_URL = "https://github.com/Kasumicic/Happwner_PC"

@OptIn(ExperimentalFoundationApi::class)
fun main(args: Array<String>) = application {
    val viewModel = remember { AppViewModel() }
    var visible by remember { mutableStateOf("--minimized" !in args) }
    val text = strings(viewModel.state.settings.language)
    val icon = remember { BitmapPainter(AppIcon.image(256).toComposeImageBitmap()) }
    val exiting = remember { AtomicBoolean(false) }
    val nativeWindow = remember { AtomicReference<AwtWindow?>() }
    val windowState = rememberWindowState(size = DpSize(1100.dp, 800.dp))
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
        val textShortcuts = DesktopTextShortcutDispatcher.install()
        onDispose {
            textShortcuts.close()
            viewModel.close()
        }
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
        val nativeContextMenu = remember(window) { JPopupContextMenuRepresentation(window) }
        CompositionLocalProvider(LocalContextMenuRepresentation provides nativeContextMenu) {
            val darkTheme = when (viewModel.state.settings.themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            MaterialTheme(colorScheme = happwnerColorScheme(darkTheme)) {
                AppScreen(viewModel, requestExit)
            }
        }
    }
}

private fun happwnerColorScheme(darkTheme: Boolean) = if (darkTheme) {
    darkColorScheme(
        primary = Color(0xFF39D6F2),
        onPrimary = Color(0xFF00363E),
        primaryContainer = Color(0xFF004E5A),
        onPrimaryContainer = Color(0xFFA4EEFC),
        secondary = Color(0xFFB1CBD0),
        background = Color(0xFF101416),
        surface = Color(0xFF101416),
        surfaceVariant = Color(0xFF20282B),
    )
} else {
    lightColorScheme(
        primary = Color(0xFF006878),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFA4EEFC),
        onPrimaryContainer = Color(0xFF001F25),
        secondary = Color(0xFF4A6267),
        background = Color(0xFFF7FAFB),
        surface = Color(0xFFF7FAFB),
        surfaceVariant = Color(0xFFDBE4E6),
    )
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
    var qrSubscription by remember { mutableStateOf<Subscription?>(null) }
    var adding by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }

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
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(text.subscriptionsTab) },
                    icon = { Icon(Icons.Default.Subscriptions, null) },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(text.settingsTab) },
                    icon = { Icon(Icons.Default.Settings, null) },
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text(text.diagnosticsTab) },
                    icon = { Icon(Icons.Default.Info, null) },
                )
            }
            when (selectedTab) {
                0 -> SubscriptionsScreen(
                    viewModel = viewModel,
                    text = text,
                    onAdd = { adding = true },
                    onCopy = { url -> writeSystemClipboard(url) },
                    onShowQr = { qrSubscription = it },
                    onEdit = { edited = it },
                    onDelete = { pendingDelete = it },
                )
                1 -> SettingsScreen(viewModel = viewModel, text = text, onExit = onExit)
                else -> DiagnosticsScreen(viewModel = viewModel, text = text)
            }
        }
    }

    if (adding || edited != null) {
        SubscriptionDialog(
            initial = edited,
            text = text,
            lastHwid = state.settings.lastHwid,
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

    qrSubscription?.let { subscription ->
        val subscriptionUrl = "${viewModel.activeBaseUrl()}/sub/${subscription.id}"
        val qrCode = remember(subscriptionUrl) { QrCodeGenerator.create(subscriptionUrl) }
        AlertDialog(
            onDismissRequest = { qrSubscription = null },
            title = { Text("${text.qrCode}: ${subscription.name}") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        bitmap = qrCode,
                        contentDescription = text.qrCode,
                        modifier = Modifier.size(300.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(subscriptionUrl, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(
                    onClick = { writeSystemClipboard(subscriptionUrl) },
                ) {
                    Icon(Icons.Default.ContentCopy, null)
                    Spacer(Modifier.width(6.dp))
                    Text(text.copy)
                }
            },
            dismissButton = {
                TextButton(onClick = { qrSubscription = null }) {
                    Text(text.close)
                }
            },
        )
    }
}

@Composable
private fun SubscriptionsScreen(
    viewModel: AppViewModel,
    text: UiStrings,
    onAdd: () -> Unit,
    onCopy: (String) -> Unit,
    onShowQr: (Subscription) -> Unit,
    onEdit: (Subscription) -> Unit,
    onDelete: (Subscription) -> Unit,
) {
    val state = viewModel.state
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(text.server, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (viewModel.serverRunning) "${text.running}: ${viewModel.activeBaseUrl()}" else text.stopped,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    viewModel.serverError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Switch(
                    checked = state.settings.serverEnabled,
                    onCheckedChange = { viewModel.updateSettings(state.settings.copy(serverEnabled = it)) },
                )
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${text.subscriptionCount}: ${state.subscriptions.size}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = onAdd) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text(text.add)
            }
        }
        HorizontalDivider()
        if (state.subscriptions.isEmpty()) {
            Text(
                text.empty,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.subscriptions, key = { it.id }) { subscription ->
                    val subscriptionUrl = "${viewModel.activeBaseUrl()}/sub/${subscription.id}"
                    SubscriptionCard(
                        subscription = subscription,
                        baseUrl = viewModel.activeBaseUrl(),
                        text = text,
                        onCopy = { onCopy(subscriptionUrl) },
                        showQr = state.settings.bindMode == BindMode.LAN,
                        onShowQr = { onShowQr(subscription) },
                        checkState = viewModel.subscriptionChecks[subscription.id],
                        lastRequest = viewModel.lastSubscriptionRequests[subscription.id],
                        profileCopyState = viewModel.profileCopyStates[subscription.id],
                        onCheck = { viewModel.checkSubscription(subscription) },
                        onCopyProfiles = { viewModel.copyProfiles(subscription) },
                        onEdit = { onEdit(subscription) },
                        onDelete = { onDelete(subscription) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(viewModel: AppViewModel, text: UiStrings, onExit: () -> Unit) {
    val state = viewModel.state
    val uriHandler = LocalUriHandler.current
    var linkOpenFailed by remember { mutableStateOf(false) }
    val openLink: (String) -> Unit = { url ->
        linkOpenFailed = runCatching { uriHandler.openUri(url) }.isFailure
    }
    var lanInterfaceMenuExpanded by remember { mutableStateOf(false) }
    var portText by remember(state.settings.port) {
        mutableStateOf(TextFieldValue(state.settings.port.toString()))
    }
    val validatedPort = InputValidator.validPort(portText.text)

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SettingsCard(title = text.appearance) {
                Text(text.theme, style = MaterialTheme.typography.bodyLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ThemeMode.entries.forEach { mode ->
                        val label = when (mode) {
                            ThemeMode.DARK -> text.darkTheme
                            ThemeMode.LIGHT -> text.lightTheme
                            ThemeMode.SYSTEM -> text.systemTheme
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = state.settings.themeMode == mode,
                                onClick = { viewModel.updateSettings(state.settings.copy(themeMode = mode)) },
                            )
                            Text(label)
                            Spacer(Modifier.width(14.dp))
                        }
                    }
                }
            }
        }
        item {
            SettingsCard(title = text.serverSettings) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(text.server, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (viewModel.serverRunning) "${text.running}: ${viewModel.activeBaseUrl()}" else text.stopped,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            viewModel.updateSettings(
                                state.settings.copy(bindMode = if (it) BindMode.LAN else BindMode.LOCAL),
                            )
                        },
                    )
                    Text(text.lanMode)
                    Spacer(Modifier.width(20.dp))
                    OutlinedTextField(
                        value = portText,
                        onValueChange = {
                            val filtered = it.text.filter(Char::isDigit).take(5)
                            portText = it.copy(
                                text = filtered,
                                selection = TextRange(it.selection.end.coerceAtMost(filtered.length)),
                            )
                        },
                        label = { Text(text.port) },
                        isError = validatedPort == null,
                        supportingText = { if (validatedPort == null) Text(text.invalidPort) },
                        singleLine = true,
                        modifier = Modifier
                            .width(130.dp)
                            .desktopTextShortcuts(portText) {
                                val filtered = it.text.filter(Char::isDigit).take(5)
                                portText = it.copy(
                                    text = filtered,
                                    selection = TextRange(it.selection.end.coerceAtMost(filtered.length)),
                                )
                            },
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = validatedPort != null && validatedPort != state.settings.port,
                        onClick = {
                            validatedPort?.let { viewModel.updateSettings(state.settings.copy(port = it)) }
                        },
                    ) {
                        Text(text.apply)
                    }
                }
                if (state.settings.bindMode == BindMode.LAN) {
                    Text(text.lanWarning, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    val interfaces = NetworkAddresses.privateIpv4Interfaces()
                    val selectedInterface = interfaces.firstOrNull { it.address == state.settings.lanAddress }
                    val selectedLabel = when {
                        state.settings.lanAddress.isBlank() -> text.automaticInterface
                        selectedInterface != null -> interfaceLabel(selectedInterface)
                        else -> "${state.settings.lanAddress} (${text.unavailable})"
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${text.lanInterface}:")
                        Spacer(Modifier.width(12.dp))
                        Box(Modifier.weight(1f)) {
                            OutlinedButton(
                                onClick = { lanInterfaceMenuExpanded = true },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(selectedLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            DropdownMenu(
                                expanded = lanInterfaceMenuExpanded,
                                onDismissRequest = { lanInterfaceMenuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(text.automaticInterface) },
                                    onClick = {
                                        lanInterfaceMenuExpanded = false
                                        viewModel.updateSettings(state.settings.copy(lanAddress = ""))
                                    },
                                )
                                interfaces.forEach { network ->
                                    DropdownMenuItem(
                                        text = { Text(interfaceLabel(network)) },
                                        onClick = {
                                            lanInterfaceMenuExpanded = false
                                            viewModel.updateSettings(state.settings.copy(lanAddress = network.address))
                                        },
                                    )
                                }
                            }
                        }
                    }
                    if (interfaces.isEmpty()) {
                        Text(text.noLanInterfaces, color = MaterialTheme.colorScheme.error)
                    } else {
                        val shownAddresses = selectedInterface?.let { listOf(it.address) }
                            ?: interfaces.map(LanInterface::address)
                        Text(
                            shownAddresses.joinToString("  •  ") { "http://$it:${state.settings.port}" },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                viewModel.serverError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        }
        item {
            SettingsCard(title = text.applicationSettings) {
                CheckRow(
                    checked = state.settings.launchAtLogin,
                    onChecked = {
                        viewModel.updateSettings(state.settings.copy(launchAtLogin = it), updateAutostart = true)
                    },
                    label = text.autostart,
                )
                OutlinedButton(onClick = onExit) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, null)
                    Spacer(Modifier.width(8.dp))
                    Text(text.fullExit)
                }
            }
        }
        item {
            SettingsCard(title = text.contacts) {
                Text(
                    text.supportWithStars,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ContributorCard(
                        name = "Omegaplexx",
                        role = text.originalAuthor,
                        avatarResource = "omegaplexx.jpg",
                        profileUrl = OMEGAPLEXX_PROFILE_URL,
                        projectUrl = ORIGINAL_PROJECT_URL,
                        text = text,
                        onOpenLink = openLink,
                        modifier = Modifier.weight(1f),
                    )
                    ContributorCard(
                        name = "Kasumicic",
                        role = text.desktopAuthor,
                        avatarResource = "kasumicic.jpg",
                        profileUrl = KASUMICIC_PROFILE_URL,
                        projectUrl = DESKTOP_PROJECT_URL,
                        text = text,
                        onOpenLink = openLink,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (linkOpenFailed) {
                    Text(
                        text.linkOpenFailed,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun ContributorCard(
    name: String,
    role: String,
    avatarResource: String,
    profileUrl: String,
    projectUrl: String,
    text: UiStrings,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Image(
                painter = painterResource(avatarResource),
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(76.dp).clip(CircleShape),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text(
                    role,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { onOpenLink(profileUrl) }) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(text.githubProfile)
                    }
                    TextButton(onClick = { onOpenLink(projectUrl) }) {
                        Icon(Icons.Default.Star, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(text.githubProject)
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticsScreen(viewModel: AppViewModel, text: UiStrings) {
    val activities = viewModel.subscriptionActivity
    var reportCopied by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(text.diagnosticActivity, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${text.events}: ${activities.size}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            OutlinedButton(
                onClick = {
                    reportCopied = viewModel.copyDiagnosticReport()
                },
            ) {
                Icon(Icons.Default.ContentCopy, null)
                Spacer(Modifier.width(8.dp))
                Text(if (reportCopied) text.reportCopied else text.copyReport)
            }
            Spacer(Modifier.width(8.dp))
            TextButton(
                enabled = activities.isNotEmpty(),
                onClick = {
                    viewModel.clearDiagnostics()
                    reportCopied = false
                },
            ) {
                Text(text.clearLog)
            }
        }
        HorizontalDivider()
        if (activities.isEmpty()) {
            Text(
                text.noActivity,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(activities) { activity ->
                    DiagnosticActivityCard(activity, text)
                }
            }
        }
    }
}

@Composable
private fun DiagnosticActivityCard(activity: SubscriptionActivity, text: UiStrings) {
    val request = activity.request
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    activity.subscriptionName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    formatRequestTime(request.completedAtMillis),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                diagnosticResultText(request, text),
                color = if (request.error != null || request.profileCount == 0) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            val origin = when (request.origin) {
                SubscriptionRequestOrigin.CLIENT -> text.clientRequest
                SubscriptionRequestOrigin.MANUAL_CHECK -> text.manualCheck
                SubscriptionRequestOrigin.PROFILE_COPY -> text.profileCopy
            }
            val details = buildList {
                add("${text.requestType}: $origin")
                request.clientAddress?.let { add("${text.client}: $it") }
                request.durationMillis?.let { add("${text.duration}: $it ms") }
                if (request.transformations.isNotEmpty()) {
                    add("${text.transformations}: ${request.transformations.joinToString()}")
                }
            }
            Text(
                details.joinToString(" • "),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            request.userInfo?.let { userInfo ->
                Text(
                    formatUserInfo(userInfo, text),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

private fun interfaceLabel(network: LanInterface): String =
    "${network.displayName} (${network.systemName}) — ${network.address}"

@Composable
private fun SubscriptionCard(
    subscription: Subscription,
    baseUrl: String,
    text: UiStrings,
    onCopy: () -> Unit,
    showQr: Boolean,
    onShowQr: () -> Unit,
    checkState: SubscriptionCheckState?,
    lastRequest: SubscriptionRequestRecord?,
    profileCopyState: ProfileCopyState?,
    onCheck: () -> Unit,
    onCopyProfiles: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(subscription.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "$baseUrl/sub/${subscription.id}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, text.edit) }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, text.delete) }
            }
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
                conversionWarningText(it.xraySkipped, it.uriPreserved, text)?.let { warning ->
                    Text(
                        warning,
                        color = MaterialTheme.colorScheme.tertiary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                it.userInfo?.let { userInfo ->
                    Text(
                        formatUserInfo(userInfo, text),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
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
                checkConversionWarningText(it, text)?.let { warning ->
                    Text(
                        warning,
                        color = MaterialTheme.colorScheme.tertiary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
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
            profileCopyState?.let { copyState ->
                Text(
                    profileCopyResultText(copyState, text),
                    color = if (
                        copyState is ProfileCopyState.Error ||
                        copyState is ProfileCopyState.ClipboardError ||
                        copyState is ProfileCopyState.NoProfiles
                    ) {
                        MaterialTheme.colorScheme.error
                    } else if (
                        copyState is ProfileCopyState.Success &&
                        (copyState.xraySkipped > 0 || copyState.uriPreserved > 0)
                    ) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    enabled = checkState !is SubscriptionCheckState.Running,
                    onClick = onCheck,
                ) { Text(if (checkState is SubscriptionCheckState.Running) text.checking else text.check) }
                TextButton(onClick = onCopy) {
                    Icon(Icons.Default.ContentCopy, null)
                    Spacer(Modifier.width(6.dp))
                    Text(text.copy)
                }
                TextButton(
                    enabled = profileCopyState !is ProfileCopyState.Running,
                    onClick = onCopyProfiles,
                ) {
                    Icon(Icons.Default.ContentCopy, null)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (profileCopyState is ProfileCopyState.Running) text.copyingProfiles else text.copyProfiles,
                    )
                }
                if (showQr) {
                    TextButton(onClick = onShowQr) {
                        Icon(Icons.Default.QrCode2, null)
                        Spacer(Modifier.width(6.dp))
                        Text(text.qrCode)
                    }
                }
            }
        }
    }
}

private fun profileCopyResultText(state: ProfileCopyState, text: UiStrings): String = when (state) {
    ProfileCopyState.Running -> text.copyingProfiles
    is ProfileCopyState.Success -> listOfNotNull(
        "${text.profilesCopied}: ${state.profileCount} • ${formatBytes(state.sizeBytes)}",
        conversionWarningText(state.xraySkipped, state.uriPreserved, text),
    ).joinToString(" • ")
    is ProfileCopyState.NoProfiles -> listOfNotNull(
        text.noProfiles,
        conversionWarningText(state.xraySkipped, state.uriPreserved, text),
    ).joinToString(" • ")
    ProfileCopyState.ClipboardError -> text.clipboardFailed
    is ProfileCopyState.Error -> "${text.checkFailed}: ${state.message}"
}

private fun diagnosticResultText(record: SubscriptionRequestRecord, text: UiStrings): String {
    record.error?.let { return "${text.checkFailed}: $it" }
    val servedStatus = record.servedStatusCode?.let { "HTTP $it" }
    val providerStatus = record.statusCode
        ?.takeIf { it != record.servedStatusCode }
        ?.let { "${text.provider} HTTP $it" }
    val size = record.sizeBytes?.let(::formatBytes)
    val profiles = record.profileCount?.let { "${text.profiles}: $it" }
    val protocols = record.protocols.takeIf { it.isNotEmpty() }
        ?.entries?.joinToString { "${it.key}: ${it.value}" }
    val warning = conversionWarningText(record.xraySkipped, record.uriPreserved, text)
    return listOfNotNull(servedStatus, providerStatus, size, profiles, protocols, warning).joinToString(" • ")
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

private fun checkConversionWarningText(state: SubscriptionCheckState, text: UiStrings): String? = when (state) {
    is SubscriptionCheckState.Success -> conversionWarningText(state.xraySkipped, state.uriPreserved, text)
    is SubscriptionCheckState.NoProfiles -> conversionWarningText(state.xraySkipped, state.uriPreserved, text)
    else -> null
}

private fun conversionWarningText(xraySkipped: Int, uriPreserved: Int, text: UiStrings): String? =
    buildList {
        if (xraySkipped > 0) add("⚠ ${text.xrayProfilesSkipped}: $xraySkipped")
        if (uriPreserved > 0) add("⚠ ${text.jsonProfilesPreserved}: $uriPreserved")
    }.joinToString(" • ").takeIf(String::isNotEmpty)

private fun formatBytes(bytes: Int): String = formatBytes(bytes.toLong())

private fun formatBytes(bytes: Long): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB", "EB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return if (unit == 0) "$bytes ${units[unit]}" else "%.1f %s".format(value, units[unit])
}

private val requestTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(ZoneId.systemDefault())

private fun formatRequestTime(timestampMillis: Long): String =
    requestTimeFormatter.format(Instant.ofEpochMilli(timestampMillis))

private fun formatUserInfo(info: SubscriptionUserInfo, text: UiStrings): String {
    val usedBytes = info.usedBytes
    val totalBytes = info.totalBytes
    val traffic = when {
        totalBytes == 0L -> "${text.traffic}: ${text.unlimited}"
        totalBytes != null && usedBytes != null ->
            "${text.traffic}: ${text.used} ${formatBytes(usedBytes)} / ${formatBytes(totalBytes)}"
        totalBytes != null -> "${text.traffic}: ${formatBytes(totalBytes)}"
        usedBytes != null -> "${text.traffic}: ${text.used} ${formatBytes(usedBytes)}"
        else -> null
    }
    val remaining = info.remainingBytes?.let { "${text.remaining}: ${formatBytes(it)}" }
    val expiration = info.expireEpochSeconds?.let { epochSeconds ->
        if (epochSeconds == 0L) {
            "${text.expires}: ${text.noExpiration}"
        } else {
            runCatching {
                "${text.expires}: ${requestTimeFormatter.format(Instant.ofEpochSecond(epochSeconds))}"
            }.getOrNull()
        }
    }
    return listOfNotNull(traffic, remaining, expiration).joinToString(" • ")
}

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
    lastHwid: String,
    onDismiss: () -> Unit,
    onSave: (Subscription) -> Unit,
) {
    var name by remember { mutableStateOf(TextFieldValue(initial?.name.orEmpty())) }
    var source by remember { mutableStateOf(TextFieldValue(initial?.source.orEmpty())) }
    var hwid by remember { mutableStateOf(TextFieldValue(initial?.hwid.orEmpty())) }
    var userAgent by remember { mutableStateOf(TextFieldValue(initial?.userAgent ?: DEFAULT_USER_AGENT)) }
    var enabled by remember { mutableStateOf(initial?.enabled ?: true) }
    var decodeBase64 by remember { mutableStateOf(initial?.decodeBase64 ?: true) }
    var jsonToUri by remember { mutableStateOf(initial?.jsonToUri ?: false) }
    var xrayToSingBox by remember { mutableStateOf(initial?.xrayToSingBox ?: false) }
    val sourceIssue = InputValidator.sourceIssue(source.text)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) text.add else text.edit) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 580.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(text.name) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().desktopTextShortcuts(name) { name = it },
                )
                OutlinedTextField(
                    value = source,
                    onValueChange = { source = it },
                    label = { Text(text.source) },
                    isError = source.text.isNotBlank() && sourceIssue != null,
                    supportingText = {
                        if (source.text.isNotBlank() && sourceIssue != null) Text(text.invalidSource)
                    },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth().desktopTextShortcuts(source) { source = it },
                )
                OutlinedTextField(
                    value = hwid,
                    onValueChange = { hwid = it },
                    label = { Text("HWID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().desktopTextShortcuts(hwid) { hwid = it },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val generated = java.util.UUID.randomUUID().toString()
                            hwid = TextFieldValue(generated, TextRange(generated.length))
                        },
                    ) {
                        Text(text.generate)
                    }
                    OutlinedButton(
                        enabled = lastHwid.isNotBlank(),
                        onClick = {
                            hwid = TextFieldValue(lastHwid, TextRange(lastHwid.length))
                        },
                    ) {
                        Text(text.useLastHwid)
                    }
                    if (lastHwid.isBlank()) {
                        Text(
                            text.noSavedHwid,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterVertically),
                        )
                    }
                }
                OutlinedTextField(
                    value = userAgent,
                    onValueChange = { userAgent = it },
                    label = { Text(text.ua) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().desktopTextShortcuts(userAgent) { userAgent = it },
                )
                CheckRow(enabled, { enabled = it }, text.enabled)
                CheckRow(decodeBase64, { decodeBase64 = it }, text.decodeBase64)
                CheckRow(jsonToUri, { jsonToUri = it }, text.jsonToUri)
                CheckRow(xrayToSingBox, { xrayToSingBox = it }, text.xrayToSingBox)
            }
        },
        confirmButton = {
            Button(
                enabled = name.text.isNotBlank() && sourceIssue == null,
                onClick = {
                    onSave(
                        Subscription(
                            id = initial?.id ?: java.util.UUID.randomUUID().toString(),
                            name = name.text.trim(),
                            source = source.text.trim(),
                            hwid = hwid.text.trim(),
                            userAgent = userAgent.text.trim(),
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
