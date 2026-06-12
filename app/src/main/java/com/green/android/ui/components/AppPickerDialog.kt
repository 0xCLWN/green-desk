package com.green.android.ui.components

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.createBitmap
import com.green.android.R
import com.green.android.ui.theme.Accent
import com.green.android.ui.theme.Border2
import com.green.android.ui.theme.Dim
import com.green.android.ui.theme.OnAccent
import com.green.android.ui.theme.Surface
import com.green.android.ui.theme.TextPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppInfo(val packageName: String, val label: String)

@Composable
fun AppPickerDialog(
    allowedApps: Set<String>,
    suggestedApps: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    val context = LocalContext.current
    val pm = context.packageManager
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selected by remember(allowedApps) { mutableStateOf(allowedApps) }
    var query by remember { mutableStateOf("") }
    val filtered = remember(apps, query) {
        if (query.isBlank()) apps
        else apps.filter { it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true) }
    }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 || it.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0 }
                .map { AppInfo(it.packageName, pm.getApplicationLabel(it).toString()) }
            val installedPkgs = installed.map { it.packageName }.toSet()
            val uninstalled = suggestedApps
                .filter { it !in installedPkgs }
                .map { AppInfo(it, it) }
            (installed + uninstalled).sortedBy { it.label.lowercase() }
        }
        loading = false
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Surface)
        ) {
            Text(stringResource(R.string.dialog_split_title), fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary,
                modifier = Modifier.padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 4.dp))
            Text(stringResource(R.string.dialog_split_desc),
                fontSize = 13.sp, color = Dim,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp))
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.search_hint), color = Dim) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = inputColors(),
            )
            if (loading) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(120.dp), contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Accent)
                }
            } else {
                LazyColumn(
                    Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                ) {
                    items(filtered, key = { it.packageName }) { app ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected =
                                        if (app.packageName in selected) selected - app.packageName else selected + app.packageName
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val icon by produceState<BitmapPainter?>(null, app.packageName) {
                                value = withContext(Dispatchers.IO) {
                                    runCatching {
                                        val d = pm.getApplicationIcon(app.packageName)
                                        val bmp = createBitmap(d.intrinsicWidth.coerceAtLeast(1), d.intrinsicHeight.coerceAtLeast(1))
                                        android.graphics.Canvas(bmp).also { c -> d.setBounds(0, 0, c.width, c.height); d.draw(c) }
                                        BitmapPainter(bmp.asImageBitmap())
                                    }.getOrNull()
                                }
                            }
                            if (icon != null) {
                                Image(painter = icon!!, contentDescription = null, modifier = Modifier.size(36.dp))
                            } else {
                                Spacer(Modifier.size(36.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(app.label, fontSize = 14.sp, color = TextPrimary)
                                Text(app.packageName, fontSize = 11.sp, color = Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Checkbox(
                                checked = app.packageName in selected,
                                onCheckedChange = { checked ->
                                    selected = if (checked) selected + app.packageName else selected - app.packageName
                                },
                                colors = androidx.compose.material3.CheckboxDefaults.colors(
                                    checkedColor = Accent, checkmarkColor = OnAccent, uncheckedColor = Border2,
                                ),
                            )
                        }
                    }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (suggestedApps.isNotEmpty()) {
                    TextButton(onClick = { selected = selected + suggestedApps.toSet() }) {
                        Text(stringResource(R.string.btn_tunnel_suggested), color = Accent)
                    }
                }
                if (selected.isNotEmpty()) {
                    TextButton(onClick = { selected = emptySet() }) { Text(stringResource(R.string.btn_clear), color = Dim) }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel), color = Dim) }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { onConfirm(selected) },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent),
                ) { Text(stringResource(R.string.btn_save), fontWeight = FontWeight.Bold) }
            }
        }
    }
}
