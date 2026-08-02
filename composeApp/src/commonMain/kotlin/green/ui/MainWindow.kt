package green.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import green.model.AppState
import green.model.VlessKey
import green.model.activeKey
import green.model.isBaked

@Composable
fun MainWindow(
    state: AppState,
    onToggleProxy: () -> Unit,
    onAddKey: (uri: String, name: String) -> Unit,
    onRemoveKey: (id: String) -> Unit,
    onActivateKey: (id: String) -> Unit,
) {
    var showAddDialog by remember { mutableStateOf(false) }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(16.dp)) {
                StatusBar(state = state, onToggle = onToggleProxy)
                Spacer(Modifier.height(16.dp))
                KeyListHeader(onAdd = { showAddDialog = true })
                Spacer(Modifier.height(8.dp))
                KeyList(
                    keys = state.keys,
                    activeKeyId = state.activeKeyId,
                    onActivate = onActivateKey,
                    onRemove = onRemoveKey,
                )
                state.error?.let { err ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = err,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddKeyDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { uri, name ->
                onAddKey(uri, name)
                showAddDialog = false
            },
        )
    }
}

@Composable
private fun StatusBar(state: AppState, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (state.running) Color(0xFF22C55E) else Color(0xFF6B7280))
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (state.running) "Connected" else "Disconnected",
                style = MaterialTheme.typography.titleMedium,
            )
            if (state.running && (state.upBytes > 0 || state.downBytes > 0)) {
                Text(
                    text = "↑ ${state.upBytes.toHumanBytes()}  ↓ ${state.downBytes.toHumanBytes()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.activeKey?.let { key ->
                Text(
                    text = key.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Button(
            onClick = onToggle,
            enabled = state.activeKeyId != null,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (state.running) Color(0xFF6B7280) else Color(0xFF22C55E),
            ),
        ) {
            Text(if (state.running) "Stop" else "Start")
        }
    }
}

@Composable
private fun KeyListHeader(onAdd: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Keys",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onAdd, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Add, contentDescription = "Add key")
        }
    }
}

@Composable
private fun KeyList(
    keys: List<VlessKey>,
    activeKeyId: String?,
    onActivate: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    if (keys.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "No keys yet. Add a vless:// link.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(keys, key = { it.id }) { key ->
                KeyRow(
                    key = key,
                    isActive = key.id == activeKeyId,
                    onActivate = { onActivate(key.id) },
                    onRemove = { onRemove(key.id) },
                )
            }
        }
    }
}

@Composable
private fun KeyRow(
    key: VlessKey,
    isActive: Boolean,
    onActivate: () -> Unit,
    onRemove: () -> Unit,
) {
    val borderColor = if (isActive) Color(0xFF22C55E) else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(onClick = onActivate)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = key.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = key.uri.take(60) + if (key.uri.length > 60) "…" else "",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!key.isBaked) {
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Remove",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun Long.toHumanBytes(): String = when {
    this < 1024 -> "${this}B"
    this < 1024 * 1024 -> "${"%.1f".format(this / 1024.0)}KB"
    this < 1024 * 1024 * 1024 -> "${"%.1f".format(this / 1024.0 / 1024.0)}MB"
    else -> "${"%.1f".format(this / 1024.0 / 1024.0 / 1024.0)}GB"
}
