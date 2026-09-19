package com.green.android.ui.components

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import com.green.android.ui.theme.AccentSoft
import com.green.android.ui.theme.Border2
import com.green.android.ui.theme.Dim
import com.green.android.ui.theme.OnAccent
import com.green.android.ui.theme.Surface
import com.green.android.ui.theme.TextPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppInfo(val packageName: String, val label: String, val isSuggested: Boolean = false)

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
    val installedSuggested = remember(apps) { apps.filter { it.isSuggested } }
    val filtered = remember(apps, query) {
        if (query.isBlank()) apps
        else apps.filter { it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true) }
    }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 || it.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0 }
                .map { AppInfo(it.packageName, pm.getApplicationLabel(it).toString(), isSuggested = it.packageName in suggestedApps) }
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
                if (installedSuggested.isNotEmpty() && !installedSuggested.all { it.packageName in selected }) {
                    SuggestionBanner(
                        apps = installedSuggested,
                        pm = pm,
                        onAddAll = { selected = selected + installedSuggested.map { it.packageName }.toSet() },
                    )
                }
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
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (installedSuggested.isNotEmpty()) {
                    TextButton(onClick = { selected = selected + installedSuggested.map { it.packageName }.toSet() }) {
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

private val BANNER_CATEGORY_PRIORITY = listOf(
    // Messenger: Telegram first
    listOf("org.telegram.messenger", "org.telegram.messenger.web", "org.thunderdog.challegram",
        "com.whatsapp", "org.thoughtcrime.securesms", "com.discord", "com.facebook.orca"),
    // Browser: Chrome first
    listOf("com.android.chrome", "com.brave.browser"),
    // AI chat: Claude first
    listOf("com.anthropic.claude", "com.openai.chatgpt", "com.google.android.apps.bard",
        "ai.perplexity.app.android", "com.microsoft.copilot", "com.microsoft.bing"),
    // Entertainment / social
    listOf("com.google.android.youtube", "com.netflix.mediaclient", "com.spotify.music",
        "tv.twitch.android.app", "com.zhiliaoapp.musically", "com.instagram.android",
        "com.twitter.android", "com.reddit.frontpage"),
)

@Composable
private fun SuggestionBanner(apps: List<AppInfo>, pm: PackageManager, onAddAll: () -> Unit) {
    val installedPkgs = remember(apps) { apps.map { it.packageName }.toSet() }
    val preview = remember(installedPkgs) {
        BANNER_CATEGORY_PRIORITY
            .mapNotNull { priority -> priority.firstOrNull { it in installedPkgs } }
            .mapNotNull { pkg -> apps.firstOrNull { it.packageName == pkg } }
            .ifEmpty { apps.take(4) }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, Accent.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .background(AccentSoft)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            preview.forEach { app ->
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
                if (icon != null)
                    Image(icon!!, null, Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)))
                else
                    Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(Accent.copy(0.12f)))
            }
            val overflow = apps.size - preview.size
            if (overflow > 0)
                Text("+$overflow", fontSize = 11.sp, color = Accent.copy(0.7f), fontWeight = FontWeight.SemiBold)
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.section_suggested), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Accent)
            Text(stringResource(R.string.banner_suggested_count, apps.size), fontSize = 11.sp, color = Accent.copy(0.7f))
        }
        TextButton(
            onClick = onAddAll,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(stringResource(R.string.btn_tunnel_suggested_add), color = Accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}
