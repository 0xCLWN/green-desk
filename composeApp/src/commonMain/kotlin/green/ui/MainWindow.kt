package green.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import green.model.AppState
import green.model.VlessKey
import green.model.activeKey
import green.model.isBaked

@Composable
fun MainWindow(
    state: AppState,
    onToggleProxy: () -> Unit,
    onToggleSysProxy: () -> Unit,
    onAddKey: (uri: String, name: String) -> Unit,
    onRemoveKey: (id: String) -> Unit,
    onActivateKey: (id: String) -> Unit,
    onRenameKey: (id: String, name: String) -> Unit,
    onEditKey: (id: String, name: String, uri: String) -> Unit,
    onUpdatePorts: (socksPort: Int, httpPort: Int) -> Unit,
    onInstallUpdate: () -> Unit,
    onDismissUpdate: () -> Unit,
    onCheckUpdate: () -> Unit,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var editingKey by remember { mutableStateOf<VlessKey?>(null) }

    MaterialTheme(colorScheme = GreenThemeColors) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF1A1A1A)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 26.dp),
            ) {
                Spacer(Modifier.height(26.dp))
                StatusSection(state = state, onToggle = onToggleProxy)
                Spacer(Modifier.height(24.dp))
                HorizontalDivider(color = BorderCard, thickness = 1.dp)
                SystemProxyRow(enabled = state.sysProxyEnabled, onToggle = onToggleSysProxy)
                HorizontalDivider(color = BorderCard, thickness = 1.dp)
                state.availableUpdate?.let { update ->
                    Spacer(Modifier.height(12.dp))
                    UpdateBanner(
                        info = update,
                        progress = state.updateProgress,
                        onInstall = onInstallUpdate,
                        onDismiss = onDismissUpdate,
                    )
                }
                Spacer(Modifier.height(24.dp))
                KeysHeader(
                    onAdd = { showAddDialog = true },
                    onSettings = { showSettingsDialog = true },
                )
                Spacer(Modifier.height(14.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (state.keys.isEmpty()) {
                        EmptyState(onAdd = { showAddDialog = true })
                    } else {
                        KeyList(
                            keys = state.keys,
                            activeKeyId = state.activeKeyId,
                            onActivate = onActivateKey,
                            onRename = onRenameKey,
                            onEdit = { key -> editingKey = key },
                            onRemove = onRemoveKey,
                        )
                    }
                }
                state.error?.let { err ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = err,
                        color = DestructiveRed,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    )
                }
                Spacer(Modifier.height(22.dp))
            }
        }
    }

    if (showSettingsDialog) {
        MaterialTheme(colorScheme = GreenThemeColors) {
            SettingsDialog(
                socksPort = state.socksPort,
                httpPort = state.httpPort,
                checkingUpdate = state.checkingUpdate,
                onDismiss = { showSettingsDialog = false },
                onConfirm = { s, h ->
                    onUpdatePorts(s, h)
                    showSettingsDialog = false
                },
                onCheckUpdate = onCheckUpdate,
            )
        }
    }

    if (showAddDialog) {
        MaterialTheme(colorScheme = GreenThemeColors) {
            AddKeyDialog(
                onDismiss = { showAddDialog = false },
                onConfirm = { uri, name ->
                    onAddKey(uri, name)
                    showAddDialog = false
                },
            )
        }
    }

    editingKey?.let { key ->
        MaterialTheme(colorScheme = GreenThemeColors) {
            EditKeyDialog(
                key = key,
                onDismiss = { editingKey = null },
                onConfirm = { name, uri ->
                    onEditKey(key.id, name, uri)
                    editingKey = null
                },
            )
        }
    }
}

@Composable
private fun StatusSection(state: AppState, onToggle: () -> Unit) {
    val pulseAlpha = remember { Animatable(1f) }
    LaunchedEffect(state.connecting) {
        if (state.connecting) {
            while (true) {
                pulseAlpha.animateTo(0.3f, tween(900, easing = FastOutSlowInEasing))
                pulseAlpha.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
            }
        } else {
            pulseAlpha.snapTo(1f)
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            verticalAlignment = Alignment.Top,
            modifier = Modifier.weight(1f).padding(end = 12.dp),
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 7.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            state.running -> AccentGreen
                            state.connecting -> AccentGreen.copy(alpha = pulseAlpha.value)
                            else -> DotDisconnected
                        }
                    )
            )
            Column {
                Text(
                    text = when {
                        state.running -> "Connected"
                        state.connecting -> "Connecting…"
                        else -> "Disconnected"
                    },
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    letterSpacing = (-0.2).sp,
                    lineHeight = 26.sp,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = state.activeKey?.name ?: "No key selected",
                    fontSize = 13.5.sp,
                    color = TextSecondary,
                )
            }
        }
        when {
            state.running -> OutlinedButton(
                onClick = onToggle,
                shape = RoundedCornerShape(100.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentGreen),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, StopBorderGreen),
                modifier = Modifier.defaultMinSize(minWidth = 96.dp, minHeight = 36.dp),
            ) {
                Text("Stop", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
            }
            state.connecting -> Button(
                onClick = {},
                enabled = false,
                shape = RoundedCornerShape(100.dp),
                colors = ButtonDefaults.buttonColors(
                    disabledContainerColor = Color(0xFF1C3A22),
                    disabledContentColor = Color(0xFF5A8A64),
                ),
                modifier = Modifier.defaultMinSize(minWidth = 96.dp, minHeight = 36.dp),
            ) {
                Text("Connecting…", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
            }
            else -> Button(
                onClick = onToggle,
                enabled = state.activeKeyId != null,
                shape = RoundedCornerShape(100.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentGreen,
                    contentColor = OnAccent,
                    disabledContainerColor = ToggleTrackOff,
                    disabledContentColor = TextMuted,
                ),
                modifier = Modifier.defaultMinSize(minWidth = 96.dp, minHeight = 36.dp),
            ) {
                Text("Start", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
            }
        }
    }
}

@Composable
private fun SystemProxyRow(enabled: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onToggle)
            .padding(vertical = 13.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "System Proxy",
            color = TextMid,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
        ProxyToggle(checked = enabled, onClick = onToggle)
    }
}

@Composable
private fun ProxyToggle(checked: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 42.dp, height = 24.dp)
            .clip(RoundedCornerShape(100.dp))
            .background(if (checked) AccentGreen else ToggleTrackOff)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 3.dp)
                .size(18.dp)
                .clip(CircleShape)
                .background(ToggleThumb)
        )
    }
}

@Composable
private fun KeysHeader(onAdd: () -> Unit, onSettings: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "KEYS",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextMuted,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onSettings, modifier = Modifier.size(30.dp)) {
            Icon(
                Icons.Default.Settings,
                contentDescription = "Settings",
                tint = TextSecondary,
                modifier = Modifier.size(15.dp),
            )
        }
        IconButton(onClick = onAdd, modifier = Modifier.size(30.dp)) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Add key",
                tint = TextSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.5.dp, ToggleTrackOff, RoundedCornerShape(12.dp))
            .padding(vertical = 34.dp, horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .border(2.dp, RadioBorderUnselected, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(RadioBorderUnselected)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "No keys yet",
                    color = TextMid,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "Add a VLESS key to get started",
                    color = TextMuted,
                    fontSize = 13.sp,
                )
            }
            Button(
                onClick = onAdd,
                shape = RoundedCornerShape(100.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentGreen,
                    contentColor = OnAccent,
                ),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 9.dp),
            ) {
                Text("Add key", fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
            }
        }
    }
}

@Composable
private fun KeyList(
    keys: List<VlessKey>,
    activeKeyId: String?,
    onActivate: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onEdit: (VlessKey) -> Unit,
    onRemove: (String) -> Unit,
) {
    var renamingId by remember { mutableStateOf<String?>(null) }
    var renameValue by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize().padding(end = 8.dp),
        ) {
            items(keys, key = { it.id }) { key ->
                KeyRow(
                    key = key,
                    isActive = key.id == activeKeyId,
                    isRenaming = key.id == renamingId,
                    renameValue = renameValue,
                    onRenameValueChange = { renameValue = it },
                    onRenameCommit = {
                        if (renameValue.isNotBlank()) onRename(key.id, renameValue)
                        renamingId = null
                    },
                    onRenameCancel = { renamingId = null },
                    onActivate = { onActivate(key.id) },
                    onStartRename = {
                        renamingId = key.id
                        renameValue = key.name
                    },
                    onEdit = { onEdit(key) },
                    onRemove = { onRemove(key.id) },
                )
            }
        }
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(listState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            style = androidx.compose.foundation.ScrollbarStyle(
                minimalHeight = 48.dp,
                thickness = 4.dp,
                shape = RoundedCornerShape(2.dp),
                hoverDurationMillis = 300,
                unhoverColor = Color(0x28FFFFFF),
                hoverColor = Color(0x55FFFFFF),
            ),
        )
    }
}

@Composable
private fun KeyRow(
    key: VlessKey,
    isActive: Boolean,
    isRenaming: Boolean,
    renameValue: String,
    onRenameValueChange: (String) -> Unit,
    onRenameCommit: () -> Unit,
    onRenameCancel: () -> Unit,
    onActivate: () -> Unit,
    onStartRename: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    var menuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(isRenaming) {
        if (isRenaming) focusRequester.requestFocus()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isActive) BgCardSelected else BgCard)
            .border(1.5.dp, if (isActive) BorderSelected else BorderCard, RoundedCornerShape(10.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onActivate)
                .padding(start = 12.dp, top = 9.dp, bottom = 9.dp, end = 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(17.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, if (isActive) AccentGreen else RadioBorderUnselected, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (isActive) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(AccentGreen)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                if (isRenaming) {
                    BasicTextField(
                        value = renameValue,
                        onValueChange = onRenameValueChange,
                        singleLine = true,
                        textStyle = TextStyle(
                            color = TextPrimary,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        cursorBrush = SolidColor(AccentGreen),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .onKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown) {
                                    when (event.key) {
                                        Key.Enter -> { onRenameCommit(); true }
                                        Key.Escape -> { onRenameCancel(); true }
                                        else -> false
                                    }
                                } else false
                            },
                    )
                } else {
                    Text(
                        text = key.name,
                        color = TextPrimary,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = key.uri,
                    color = TextMuted,
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Box(modifier = Modifier.padding(end = 4.dp)) {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.size(26.dp),
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "Menu",
                    tint = TextSecondary,
                    modifier = Modifier.size(14.dp),
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("Rename", fontSize = 13.5.sp) },
                    onClick = { menuExpanded = false; onStartRename() },
                )
                DropdownMenuItem(
                    text = { Text("Edit", fontSize = 13.5.sp) },
                    onClick = { menuExpanded = false; onEdit() },
                )
                if (!key.isBaked) {
                    HorizontalDivider(color = BorderCard)
                    DropdownMenuItem(
                        text = { Text("Delete", color = DestructiveRed, fontSize = 13.5.sp) },
                        onClick = { menuExpanded = false; onRemove() },
                    )
                }
            }
        }
    }
}
