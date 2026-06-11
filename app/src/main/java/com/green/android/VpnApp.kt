package com.green.android

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.createBitmap
import com.green.android.data.Config
import com.green.android.ui.theme.Accent
import com.green.android.ui.theme.AccentSoft
import com.green.android.ui.theme.Border
import com.green.android.ui.theme.Border2
import com.green.android.ui.theme.Danger
import com.green.android.ui.theme.Dim
import com.green.android.ui.theme.Dim2
import com.green.android.ui.theme.Glow
import com.green.android.ui.theme.GradA
import com.green.android.ui.theme.GradB
import com.green.android.ui.theme.OnAccent
import com.green.android.ui.theme.Surface
import com.green.android.ui.theme.Surface3
import com.green.android.ui.theme.TextPrimary
import com.green.android.ui.theme.Warn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// ── Navigation ────────────────────────────────────────────────────────────────

sealed class NavScreen {
    object Home : NavScreen()
    object Settings : NavScreen()
    object GeoSettings : NavScreen()
    object DisguiseSettings : NavScreen()
    object Subscriptions : NavScreen()
    data class Edit(val config: Config) : NavScreen()
    object Import : NavScreen()
}

data class ToastData(val message: String, val isWarn: Boolean = false)

data class AppInfo(val packageName: String, val label: String)

// ── Root ─────────────────────────────────────────────────────────────────────

@Composable
fun VpnApp(viewModel: VpnViewModel) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val status by viewModel.status.collectAsState()
    val configs by viewModel.configs.collectAsState()
    val selectedId by viewModel.selectedId.collectAsState()
    val allowedApps by viewModel.allowedApps.collectAsState()
    val suggestedApps by viewModel.suggestedApps.collectAsState()
    val addError by viewModel.addError.collectAsState()
    val subscriptionImporting by viewModel.subscriptionImporting.collectAsState()
    val autoConnect by viewModel.autoConnect.collectAsState()
    val notify by viewModel.notify.collectAsState()
    val disguise by viewModel.disguise.collectAsState()
    val geo by viewModel.geo.collectAsState()
    val subscriptions by viewModel.subscriptions.collectAsState()
    val updateInfo by viewModel.updateInfo.collectAsState()
    val bannerDismissed by viewModel.bannerDismissed.collectAsState()
    val updateProgress by viewModel.updateProgress.collectAsState()

    var screen by remember { mutableStateOf<NavScreen>(NavScreen.Home) }
    var showAppPicker by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<ToastData?>(null) }
    var shake by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.onPermissionGranted(context)
        else viewModel.onPermissionDenied()
    }

    // Close Import when a new config is successfully added
    var configCount by remember { mutableIntStateOf(-1) }
    LaunchedEffect(configs.size) {
        if (configCount >= 0 && configs.size > configCount && screen == NavScreen.Import) {
            screen = NavScreen.Home
            viewModel.clearAddError()
        }
        configCount = configs.size
    }

    // Success double-tap haptic when connection is established
    LaunchedEffect(status) {
        if (status == VpnStatus.CONNECTED) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            delay(110)
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    // Auto-connect on first configs load if the setting is on
    var autoConnectFired by remember { mutableStateOf(false) }
    LaunchedEffect(configs) {
        if (!autoConnectFired && configs.isNotEmpty() && autoConnect && status == VpnStatus.DISCONNECTED) {
            autoConnectFired = true
            if (selectedId == null) viewModel.select(configs.first())
            viewModel.connect(context, permissionLauncher)
        }
    }

    fun showToast(msg: String, warn: Boolean = false) { toast = ToastData(msg, warn) }

    LaunchedEffect(Unit) {
        viewModel.noUpdateSignal.collect { showToast(context.getString(R.string.toast_up_to_date)) }
    }

    val layerVisible = status == VpnStatus.CONNECTED || status == VpnStatus.CONNECTING

    // Intercept back when pushed screen is open
    BackHandler(enabled = screen != NavScreen.Home) {
        screen = when (screen) {
            NavScreen.GeoSettings, NavScreen.Import, NavScreen.Subscriptions, NavScreen.DisguiseSettings -> NavScreen.Settings
            else -> NavScreen.Home
        }
        viewModel.clearAddError()
    }
    // Intercept back when connected layer is up (lower priority — fires only when no pushed screen)
    BackHandler(enabled = layerVisible) {
        shake = true
        showToast(context.getString(R.string.toast_disconnect_to_go_back), warn = true)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF16201B), Color(0xFF0F1512)),
                    radius = 1200f,
                )
            )
            .systemBarsPadding()
    ) {
        HomeContent(
            configs = configs,
            subscriptions = subscriptions,
            selectedId = selectedId,
            allowedApps = allowedApps,
            geoUpdating = geo.updating,
            updateInfo = updateInfo,
            bannerDismissed = bannerDismissed,
            updateProgress = updateProgress,
            status = status,
            onSelect = { viewModel.select(it) },
            onConnect = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); viewModel.connect(context, permissionLauncher) },
            onOpenSettings = { if (status == VpnStatus.DISCONNECTED) screen = NavScreen.Settings },
            onOpenEdit = { if (status == VpnStatus.DISCONNECTED) screen = NavScreen.Edit(it) },
            onOpenImport = { if (status == VpnStatus.DISCONNECTED) screen = NavScreen.Import },
            onManageSplit = { showAppPicker = true },
            onSkipGeo = { viewModel.skipGeoUpdate() },
            onStartUpdate = { viewModel.startUpdate(context) },
            onDismissUpdate = { viewModel.dismissUpdate() },
        )

        ConnectedLayer(
            visible = layerVisible,
            config = configs.find { it.id == selectedId },
            allowedApps = allowedApps,
            status = status,
            shake = shake,
            onDisconnect = { viewModel.disconnect(context) },
            onLocked = { shake = true; showToast(context.getString(R.string.toast_disconnect_to_change_settings), warn = true) },
            onSplitTap = { shake = true; showToast(context.getString(R.string.toast_disconnect_to_change_split), warn = true) },
            onShakeDone = { shake = false },
        )

        PushedScreen(visible = screen == NavScreen.Settings || screen == NavScreen.GeoSettings || screen == NavScreen.DisguiseSettings || screen == NavScreen.Import || screen == NavScreen.Subscriptions) {
            when (screen) {
                NavScreen.DisguiseSettings -> DisguiseSettingsScreen(
                    disguise = disguise,
                    onDisguise = { viewModel.setDisguise(it) },
                    onBack = { screen = NavScreen.Settings },
                )
                NavScreen.GeoSettings -> GeoSettingsScreen(
                    geoEnabled = geo.enabled, onGeoEnabled = { viewModel.setGeoEnabled(it) },
                    geoipUrl = geo.geoipUrl, onGeoipUrl = { viewModel.setGeoipUrl(it) },
                    geositeUrl = geo.geositeUrl, onGeositeUrl = { viewModel.setGeositeUrl(it) },
                    geoUpdating = geo.updating,
                    geoFilesVersion = geo.filesVersion,
                    onUpdateNow = { viewModel.updateGeoNow() },
                    onImport = { uri, name -> viewModel.importGeoFile(context, uri, name) },
                    onBack = { screen = NavScreen.Settings },
                )
                NavScreen.Import -> ImportScreen(
                    addError = addError,
                    subscriptionImporting = subscriptionImporting,
                    onAdd = { viewModel.addConfig(it) },
                    onSubscription = { viewModel.addSubscription(it) },
                    onBack = { screen = NavScreen.Settings; viewModel.clearAddError() },
                    onClearError = { viewModel.clearAddError() },
                    onToast = ::showToast,
                )
                NavScreen.Subscriptions -> SubscriptionsScreen(
                    subscriptions = subscriptions,
                    addError = addError,
                    subscriptionImporting = subscriptionImporting,
                    onAdd = { viewModel.addSubscription(it) },
                    onDelete = { viewModel.deleteSubscription(it) },
                    onClearError = { viewModel.clearAddError() },
                    onBack = { screen = NavScreen.Settings; viewModel.clearAddError() },
                )
                else -> SettingsScreen(
                    autoConnect = autoConnect, onAutoConnect = { viewModel.setAutoConnect(it) },
                    notify = notify, onNotify = { viewModel.setNotify(it) },
                    disguise = disguise,
                    allowedApps = allowedApps,
                    geoEnabled = geo.enabled,
                    subscriptionCount = subscriptions.size,
                    updateInfo = updateInfo,
                    updateProgress = updateProgress,
                    onStartUpdate = { viewModel.startUpdate(context) },
                    onRecheckUpdates = { viewModel.recheckUpdates() },
                    onSplit = { showAppPicker = true },
                    onImport = { screen = NavScreen.Import },
                    onGeoSettings = { screen = NavScreen.GeoSettings },
                    onDisguiseSettings = { screen = NavScreen.DisguiseSettings },
                    onSubscriptions = { screen = NavScreen.Subscriptions },
                    onBack = { screen = NavScreen.Home },
                )
            }
        }

        PushedScreen(visible = screen is NavScreen.Edit) {
            val cfg = (screen as? NavScreen.Edit)?.config
            if (cfg != null) {
                EditServerScreen(
                    config = cfg,
                    onSave = { viewModel.updateConfig(it); screen = NavScreen.Home; showToast(context.getString(R.string.toast_server_saved)) },
                    onDelete = { viewModel.deleteConfig(cfg); screen = NavScreen.Home; showToast(context.getString(R.string.toast_server_deleted)) },
                    onBack = { screen = NavScreen.Home },
                )
            }
        }

        if (showAppPicker) {
            AppPickerDialog(
                allowedApps = allowedApps,
                suggestedApps = suggestedApps,
                onDismiss = { showAppPicker = false },
                onConfirm = { viewModel.setAllowedApps(it); showAppPicker = false },
            )
        }

        ToastOverlay(toast = toast, onDismiss = { toast = null })
    }
}

// ── Pushed screen wrapper ─────────────────────────────────────────────────────

@Composable
fun PushedScreen(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(tween(350, easing = FastOutSlowInEasing)) { it },
        exit = slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { it },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF0F1512))
        ) { content() }
    }
}

// ── Home screen ───────────────────────────────────────────────────────────────

@Composable
fun HomeContent(
    configs: List<Config>,
    subscriptions: List<com.green.android.data.Subscription>,
    selectedId: Int?,
    allowedApps: Set<String>,
    geoUpdating: Boolean,
    updateInfo: UpdateInfo?,
    bannerDismissed: Boolean,
    updateProgress: Float?,
    status: VpnStatus,
    onSelect: (Config) -> Unit,
    onConnect: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenEdit: (Config) -> Unit,
    onOpenImport: () -> Unit,
    onManageSplit: () -> Unit,
    onSkipGeo: () -> Unit,
    onStartUpdate: () -> Unit,
    onDismissUpdate: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp)
            .padding(top = 14.dp, bottom = 22.dp),
    ) {
        // App bar
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(AccentSoft)
                        .border(1.dp, Accent.copy(alpha = 0.35f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Security, null, tint = Accent, modifier = Modifier.size(15.dp))
                }
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = TextPrimary)) { append("smol green ") }
                        withStyle(SpanStyle(color = Dim)) { append("vpn") }
                    },
                    fontWeight = FontWeight.Bold, fontSize = 19.sp, letterSpacing = (-0.3).sp,
                )
            }
            SmolIconBtn(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, stringResource(R.string.cd_settings), tint = TextPrimary, modifier = Modifier.size(20.dp))
            }
        }

        if (updateInfo != null && updateInfo.isMajorMinor && !bannerDismissed) {
            Spacer(Modifier.height(10.dp))
            UpdateBanner(
                info = updateInfo,
                progress = updateProgress,
                onInstall = onStartUpdate,
                onDismiss = onDismissUpdate
            )
        }

        // Status block
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(20.dp))
            Box(contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(108.dp)) {
                    drawCircle(
                        color = Border2.copy(alpha = 0.5f),
                        radius = size.minDimension / 2f,
                        style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))),
                    )
                }
                Box(
                    Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    Color(0xFF1E2925),
                                    Color(0xFF161E1B)
                                )
                            )
                        )
                        .border(1.dp, Border, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Security, null, tint = Dim, modifier = Modifier.size(40.dp))
                }
            }
            Spacer(Modifier.height(15.dp))
            Text(stringResource(R.string.status_not_connected), fontSize = 25.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp, color = TextPrimary)
            Spacer(Modifier.height(7.dp))
            Text(
                stringResource(R.string.status_subtitle),
                fontSize = 14.sp, color = Dim, textAlign = TextAlign.Center, lineHeight = 20.sp,
                modifier = Modifier.widthIn(max = 240.dp),
            )
            Spacer(Modifier.height(18.dp))
        }

        SplitTunnelLine(allowedApps = allowedApps, readOnly = false, onClick = onManageSplit)
        Spacer(Modifier.height(4.dp))

        if (geoUpdating) {
            Spacer(Modifier.height(8.dp))
            GeoUpdateBanner(onSkip = onSkipGeo)
        }

        Spacer(Modifier.height(20.dp))
        SectionLabel(stringResource(R.string.section_servers))

        val subNameMap = remember(subscriptions) { subscriptions.associateBy { it.id } }
        LazyColumn(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(9.dp),
            contentPadding = PaddingValues(bottom = 4.dp),
        ) {
            items(configs, key = { it.id }) { config ->
                ServerCard(
                    config = config,
                    subscriptionName = config.subscriptionId?.let { subNameMap[it]?.name },
                    selected = config.id == selectedId,
                    onSelect = { onSelect(config) },
                    onEdit = { onOpenEdit(config) },
                )
            }
            item { AddServerCard(onClick = onOpenImport) }
        }

        Spacer(Modifier.height(14.dp))

        Button(
            onClick = onConnect,
            enabled = status != VpnStatus.CONNECTING && selectedId != null,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Accent, contentColor = OnAccent,
                disabledContainerColor = Accent.copy(alpha = 0.35f), disabledContentColor = OnAccent.copy(alpha = 0.5f),
            ),
            contentPadding = PaddingValues(vertical = 17.dp),
            elevation = ButtonDefaults.buttonElevation(0.dp),
        ) {
            Icon(Icons.Default.PowerSettingsNew, null, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.btn_connect), fontSize = 17.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp)
        }
    }
}

// ── Shared components ─────────────────────────────────────────────────────────

@Composable
fun SmolIconBtn(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(13.dp))
            .border(1.dp, Border, RoundedCornerShape(13.dp))
            .background(Surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(), modifier = modifier.padding(bottom = 9.dp),
        fontSize = 11.sp, letterSpacing = 1.6.sp, fontWeight = FontWeight.SemiBold, color = Dim2,
    )
}

@Composable
fun SplitTunnelLine(allowedApps: Set<String>, readOnly: Boolean, onClick: (() -> Unit)? = null) {
    val bg = if (readOnly) Color.White.copy(0.05f) else Surface
    val borderColor = if (readOnly) Color.White.copy(0.12f) else Border
    val iconBg = if (readOnly) Color.White.copy(0.12f) else AccentSoft
    val iconTint = if (readOnly) Color.White.copy(0.7f) else Accent
    val textColor = if (readOnly) Color.White.copy(0.7f) else Dim
    val boldColor = if (readOnly) Color.White else TextPrimary

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .background(bg)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Box(
            Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(iconBg), contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Filled.CallSplit, null, tint = iconTint, modifier = Modifier.size(17.dp))
        }
        val label = buildAnnotatedString {
            if (allowedApps.isEmpty()) {
                withStyle(SpanStyle(color = textColor)) { append(stringResource(R.string.split_whole_phone) + " · ") }
                withStyle(SpanStyle(color = boldColor, fontWeight = FontWeight.SemiBold)) { append(stringResource(R.string.split_all_apps)) }
            } else {
                withStyle(SpanStyle(color = textColor)) { append(stringResource(R.string.split_tunnel_prefix) + " · ") }
                withStyle(SpanStyle(color = boldColor, fontWeight = FontWeight.SemiBold)) { append(pluralStringResource(R.plurals.setting_split_n_apps, allowedApps.size, allowedApps.size)) }
                withStyle(SpanStyle(color = textColor)) { append(" " + stringResource(R.string.split_tunneled_suffix)) }
            }
        }
        Text(label, modifier = Modifier.weight(1f), fontSize = 13.5.sp, lineHeight = 18.sp)
        if (!readOnly) {
            Text(stringResource(R.string.split_manage), fontSize = 12.sp, color = Accent, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun ServerCard(config: Config, subscriptionName: String?, selected: Boolean, onSelect: () -> Unit, onEdit: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, if (selected) Accent else Border, RoundedCornerShape(16.dp))
            .background(Surface)
            .then(if (selected) Modifier.background(AccentSoft) else Modifier)
            .clickable(onClick = onSelect)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box(
            Modifier
                .size(21.dp)
                .clip(CircleShape)
                .border(2.dp, if (selected) Accent else Border2, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(
                Modifier
                    .size(11.dp)
                    .clip(CircleShape)
                    .background(Accent)
            )
        }
        Column(Modifier.weight(1f)) {
            Text(nameWithFlag(config.name), fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val jsonConfigLabel = stringResource(R.string.server_json_config)
            val meta = buildString {
                append(config.vlessLink?.substringAfter("@")?.substringBefore("?") ?: jsonConfigLabel)
                if (subscriptionName != null) append(" · $subscriptionName")
            }
            Text(meta, fontSize = 12.sp, color = Dim, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        }
        Box(
            Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .clickable(onClick = onEdit),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.MoreVert, stringResource(R.string.cd_edit), tint = Dim, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun AddServerCard(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, Border2, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(13.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Add, null, tint = Dim, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.btn_add_server), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Dim)
    }
}

// ── Connected layer ───────────────────────────────────────────────────────────

@Composable
fun ConnectedLayer(
    visible: Boolean,
    config: Config?,
    allowedApps: Set<String>,
    status: VpnStatus,
    shake: Boolean,
    onDisconnect: () -> Unit,
    onLocked: () -> Unit,
    onSplitTap: () -> Unit,
    onShakeDone: () -> Unit,
) {
    val slideY by animateFloatAsState(
        targetValue = if (visible) 0f else 1f,
        animationSpec = spring(dampingRatio = 0.76f, stiffness = Spring.StiffnessMedium),
        label = "layer",
    )
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.93f,
        animationSpec = spring(dampingRatio = 0.76f, stiffness = Spring.StiffnessMedium),
        label = "scale",
    )

    // Bloom flash — bright glow pulse when the layer first appears
    var bloomTarget by remember { mutableFloatStateOf(0f) }
    val bloom by animateFloatAsState(bloomTarget, tween(700, easing = FastOutSlowInEasing), label = "bloom")
    LaunchedEffect(visible) {
        if (visible) { bloomTarget = 1f; delay(60); bloomTarget = 0f }
    }

    var shakeX by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(shake) {
        if (!shake) return@LaunchedEffect
        for (offset in listOf(-6f, 6f, -4f, 4f, -2f, 2f, 0f)) {
            shakeX = offset
            delay(55)
        }
        onShakeDone()
    }

    var testState by remember { mutableStateOf<String?>(null) }
    val testOk = testState?.startsWith("ok:") == true
    val testFailed = testState == "failed"
    val testMs = testState?.removePrefix("ok:")?.toLongOrNull()
    val testMsLabel = if (testMs == 0L) "<1" else testMs?.toString()
    LaunchedEffect(visible) { if (!visible) testState = null }
    LaunchedEffect(testState) {
        if (testState != "testing") return@LaunchedEffect
        testState = withContext(Dispatchers.IO) {
            runCatching {
                val t0 = System.nanoTime()
                java.net.Socket().use { it.connect(java.net.InetSocketAddress("1.1.1.1", 443), 5_000) }
                "ok:${(System.nanoTime() - t0) / 1_000_000L}"
            }.getOrElse { "failed" }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = size.height * slideY
                translationX = shakeX.dp.toPx()
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0.5f, 1f)
            }
    ) {
        // Green gradient background
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(GradA, GradB)))
        )
        // Top radial glow (static base + animated bloom on entry)
        Box(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Accent.copy(alpha = 0.22f + bloom * 0.45f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp)
                .padding(top = 16.dp, bottom = 22.dp),
        ) {
            // Grab handle
            Box(
                Modifier
                    .size(width = 42.dp, height = 5.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color.White.copy(0.35f))
                    .align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(14.dp))

            // Status row
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    PulsingDot()
                    Text(
                        if (status == VpnStatus.CONNECTING) stringResource(R.string.status_connecting) else stringResource(R.string.status_connected),
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
                    )
                }
                Row(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .border(1.dp, Color.White.copy(0.18f), RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(0.22f))
                        .clickable(onClick = onLocked)
                        .padding(horizontal = 11.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Default.Lock, null, tint = Color(0xFFCDEED8), modifier = Modifier.size(13.dp))
                    Text(stringResource(R.string.settings_locked), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFCDEED8))
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(config?.name?.let { nameWithFlag(it) } ?: "—", fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val meta = config?.vlessLink?.substringAfter("@")?.substringBefore("?") ?: ""
            Text(if (meta.isNotEmpty()) "$meta · vless" else "vless", fontSize = 13.sp, color = Color(0xFFAEE6C2), fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(12.dp))

            SplitTunnelLine(allowedApps = allowedApps, readOnly = true, onClick = onSplitTap)
            Spacer(Modifier.height(18.dp))

            Spacer(Modifier.weight(1f))

            // Test connection
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(
                        1.dp, when {
                            testOk -> Glow
                            testFailed -> Danger.copy(alpha = 0.6f)
                            else -> Color.White.copy(0.2f)
                        }, RoundedCornerShape(16.dp)
                    )
                    .background(Color.Black.copy(0.18f))
                    .clickable { if (testState != "testing") testState = "testing" }
                    .padding(13.dp),
                contentAlignment = Alignment.Center,
            ) {
                val testColor = when { testOk -> Glow; testFailed -> Danger; else -> Color.White }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    when {
                        testState == "testing" -> CircularProgressIndicator(Modifier.size(15.dp), color = Color.White, strokeWidth = 2.dp)
                        testFailed -> Icon(Icons.Default.Close, null, tint = Danger, modifier = Modifier.size(15.dp))
                        else -> Icon(Icons.Default.Bolt, null, tint = testColor, modifier = Modifier.size(15.dp))
                    }
                    Text(
                        when {
                            testState == "testing" -> stringResource(R.string.test_testing)
                            testOk -> stringResource(R.string.test_ok, testMsLabel ?: "0")
                            testFailed -> stringResource(R.string.test_failed)
                            else -> stringResource(R.string.test_idle)
                        },
                        fontSize = 16.sp, fontWeight = FontWeight.Bold, color = testColor,
                    )
                }
            }
            Spacer(Modifier.height(11.dp))

            // Disconnect
            Button(
                onClick = onDisconnect,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                contentPadding = PaddingValues(vertical = 17.dp),
                elevation = ButtonDefaults.buttonElevation(0.dp),
            ) {
                Text(stringResource(R.string.btn_disconnect), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.1.sp)
            }
        }
    }
}

@Composable
fun PulsingDot() {
    val inf = rememberInfiniteTransition(label = "pulse")
    val scale by inf.animateFloat(1f, 2.2f, infiniteRepeatable(tween(1900), RepeatMode.Restart), label = "scale")
    val alpha by inf.animateFloat(0.5f, 0f, infiniteRepeatable(tween(1900), RepeatMode.Restart), label = "alpha")
    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(11.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
                .clip(CircleShape)
                .background(Glow))
        Box(
            Modifier
                .size(11.dp)
                .clip(CircleShape)
                .background(Glow)
        )
    }
}

// ── Pushed screen components ──────────────────────────────────────────────────

@Composable
fun PushHeader(title: String, onBack: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SmolIconBtn(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back), tint = TextPrimary, modifier = Modifier.size(20.dp))
            }
            Text(title, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, modifier = Modifier.weight(1f))
            trailing?.invoke()
        }
        HorizontalDivider(color = Border)
    }
}

// ── Settings screen ───────────────────────────────────────────────────────────

@Composable
fun SettingsScreen(
    autoConnect: Boolean, onAutoConnect: (Boolean) -> Unit,
    notify: Boolean, onNotify: (Boolean) -> Unit,
    disguise: String,
    allowedApps: Set<String>,
    geoEnabled: Boolean,
    subscriptionCount: Int,
    updateInfo: UpdateInfo?,
    updateProgress: Float?,
    onStartUpdate: () -> Unit,
    onRecheckUpdates: () -> Unit,
    onSplit: () -> Unit,
    onImport: () -> Unit,
    onGeoSettings: () -> Unit,
    onDisguiseSettings: () -> Unit,
    onSubscriptions: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        PushHeader(stringResource(R.string.screen_settings), onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            SettingsSection(stringResource(R.string.section_connection)) {
                SettingRow(stringResource(R.string.setting_auto_connect), stringResource(R.string.setting_auto_connect_sub)) {
                    SmolToggle(autoConnect, onAutoConnect)
                }
            }
            SettingsSection(stringResource(R.string.section_routing)) {
                SettingRow(stringResource(R.string.setting_split_tunneling), stringResource(R.string.setting_split_tunneling_sub), onClick = onSplit) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (allowedApps.isEmpty()) stringResource(R.string.setting_split_all_apps)
                            else pluralStringResource(R.plurals.setting_split_n_apps, allowedApps.size, allowedApps.size),
                            fontSize = 13.sp, color = Accent, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                        )
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Dim, modifier = Modifier.size(16.dp))
                    }
                }
                HorizontalDivider(color = Border)
                SettingRow(stringResource(R.string.setting_geo_filtering), stringResource(R.string.setting_geo_filtering_sub), onClick = onGeoSettings) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (geoEnabled) stringResource(R.string.setting_geo_on) else stringResource(R.string.setting_geo_off),
                            fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                            color = if (geoEnabled) Accent else Dim,
                        )
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Dim, modifier = Modifier.size(16.dp))
                    }
                }
            }
            SettingsSection(stringResource(R.string.section_data)) {
                SettingRow(stringResource(R.string.setting_subscriptions), stringResource(R.string.setting_subscriptions_sub), onClick = onSubscriptions) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (subscriptionCount > 0) Text(
                            "$subscriptionCount",
                            fontSize = 13.sp, color = Accent, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                        )
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Dim, modifier = Modifier.size(16.dp))
                    }
                }
                HorizontalDivider(color = Border)
                SettingRow(stringResource(R.string.setting_add_server), stringResource(R.string.setting_add_server_sub), onClick = onImport) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Dim, modifier = Modifier.size(16.dp))
                }
            }
            SettingsSection(stringResource(R.string.section_general)) {
                SettingRow(stringResource(R.string.setting_disguise), onClick = onDisguiseSettings) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            when (disguise) {
                                "alfa_bank"  -> stringResource(R.string.setting_disguise_alfa)
                                "calculator" -> stringResource(R.string.setting_disguise_calc)
                                else         -> stringResource(R.string.setting_disguise_default)
                            },
                            fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
                            color = if (disguise == "default") Dim else Accent,
                        )
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Dim, modifier = Modifier.size(16.dp))
                    }
                }
                HorizontalDivider(color = Border)
                SettingRow(stringResource(R.string.setting_notifications)) { SmolToggle(notify, onNotify) }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    HorizontalDivider(color = Border)
                    SettingRow(
                        stringResource(R.string.setting_language),
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_LOCALE_SETTINGS,
                                    Uri.fromParts("package", context.packageName, null))
                            )
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Dim, modifier = Modifier.size(18.dp))
                    }
                }
                HorizontalDivider(color = Border)
                SettingRow(stringResource(R.string.setting_app_version)) {
                    Text(
                        BuildConfig.VERSION_NAME,
                        fontSize = 13.sp,
                        color = Dim,
                        fontFamily = FontFamily.Monospace
                    )
                }
                HorizontalDivider(color = Border)
                if (updateInfo != null) {
                    SettingRow(
                        stringResource(R.string.update_available),
                        onClick = onStartUpdate.takeIf { updateProgress == null },
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (updateProgress != null) {
                                Text(
                                    "${(updateProgress * 100).toInt()}%",
                                    fontSize = 13.sp,
                                    color = Dim,
                                    fontFamily = FontFamily.Monospace
                                )
                            } else {
                                Text(
                                    updateInfo.tag,
                                    fontSize = 13.sp,
                                    color = Accent,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(
                                    Icons.Default.Download,
                                    null,
                                    tint = Accent,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                } else {
                    SettingRow(stringResource(R.string.setting_up_to_date)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(stringResource(R.string.setting_up_to_date_value), fontSize = 13.sp, color = Dim, fontFamily = FontFamily.Monospace)
                            SmolIconBtn(onClick = onRecheckUpdates) {
                                Icon(Icons.Default.Refresh, null, tint = Dim, modifier = Modifier.size(15.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Subscriptions screen ──────────────────────────────────────────────────────

@Composable
fun SubscriptionsScreen(
    subscriptions: List<com.green.android.data.Subscription>,
    addError: String?,
    subscriptionImporting: Boolean,
    onAdd: (String) -> Unit,
    onDelete: (com.green.android.data.Subscription) -> Unit,
    onClearError: () -> Unit,
    onBack: () -> Unit,
) {
    var showAdd by remember { mutableStateOf(false) }
    var url by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        PushHeader(stringResource(R.string.screen_subscriptions), onBack) {
            SmolIconBtn(onClick = { showAdd = !showAdd; onClearError() }) {
                Icon(Icons.Default.Add, stringResource(R.string.cd_add), tint = TextPrimary, modifier = Modifier.size(20.dp))
            }
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            AnimatedVisibility(visible = showAdd) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel(stringResource(R.string.label_subscription_url))
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it; onClearError() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        placeholder = { Text(stringResource(R.string.placeholder_url), color = Dim, fontFamily = FontFamily.Monospace, fontSize = 12.5.sp) },
                        isError = addError != null,
                        supportingText = addError?.let { err -> { Text(err, color = Danger) } },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.5.sp),
                        colors = inputColors(),
                    )
                    Button(
                        onClick = { onAdd(url.trim()) },
                        enabled = url.isNotBlank() && !subscriptionImporting,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent),
                        contentPadding = PaddingValues(14.dp),
                    ) {
                        if (subscriptionImporting) {
                            CircularProgressIndicator(Modifier.size(16.dp), color = OnAccent, strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                        }
                        Text(
                            if (subscriptionImporting) stringResource(R.string.btn_importing) else stringResource(R.string.btn_import),
                            fontWeight = FontWeight.Bold, fontSize = 15.sp,
                        )
                    }
                }
            }

            if (subscriptions.isEmpty() && !showAdd) {
                Box(Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.subscriptions_empty), color = Dim, fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 22.sp)
                }
            } else if (subscriptions.isNotEmpty()) {
                SettingsSection(stringResource(R.string.section_saved)) {
                    subscriptions.forEachIndexed { i, sub ->
                        if (i > 0) HorizontalDivider(color = Border)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(Surface)
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(sub.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(sub.url, fontSize = 11.sp, color = Dim, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                            }
                            Box(
                                Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .clickable { onDelete(sub) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Default.Delete, stringResource(R.string.cd_delete), tint = Danger.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Geo settings screen ───────────────────────────────────────────────────────

private fun geoFileStatus(context: android.content.Context, filesDir: java.io.File, name: String): Pair<Boolean, String> {
    val f = java.io.File(filesDir, name)
    if (!f.exists()) return false to context.getString(R.string.geo_not_downloaded)
    val ageDays = (System.currentTimeMillis() - f.lastModified()) / (24 * 60 * 60 * 1000)
    val age = when {
        ageDays < 1L -> context.getString(R.string.geo_updated_today)
        ageDays == 1L -> context.getString(R.string.geo_updated_yesterday)
        ageDays < 7L -> context.getString(R.string.geo_updated_days_ago, ageDays.toInt())
        ageDays < 30L -> context.getString(R.string.geo_updated_weeks_ago, (ageDays / 7).toInt())
        else -> context.getString(R.string.geo_updated_months_ago, (ageDays / 30).toInt())
    }
    return true to age
}

@Composable
fun GeoSettingsScreen(
    geoEnabled: Boolean,
    onGeoEnabled: (Boolean) -> Unit,
    geoipUrl: String,
    onGeoipUrl: (String) -> Unit,
    geositeUrl: String,
    onGeositeUrl: (String) -> Unit,
    geoUpdating: Boolean,
    geoFilesVersion: Int,
    onUpdateNow: () -> Unit,
    onImport: (android.net.Uri, String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val filesDir = context.filesDir

    val geoipStatus by produceState(false to "…", geoFilesVersion) {
        value = withContext(Dispatchers.IO) { geoFileStatus(context, filesDir, "geoip.dat") }
    }
    val (geoipExists, geoipAge) = geoipStatus
    val geositeStatus by produceState(false to "…", geoFilesVersion) {
        value = withContext(Dispatchers.IO) { geoFileStatus(context, filesDir, "geosite.dat") }
    }
    val (geositeExists, geositeAge) = geositeStatus

    val geoipLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onImport(it, "geoip.dat") }
    }
    val geositeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onImport(it, "geosite.dat") }
    }

    Column(Modifier.fillMaxSize()) {
        PushHeader(stringResource(R.string.screen_geo_data), onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            SettingsSection(stringResource(R.string.section_filtering)) {
                SettingRow(stringResource(R.string.setting_enable_geo), stringResource(R.string.setting_enable_geo_sub)) {
                    SmolToggle(geoEnabled, onGeoEnabled)
                }
            }

            if (geoEnabled) {
                SettingsSection(stringResource(R.string.section_files)) {
                    GeoFileRow(
                        name = "geoip.dat", status = geoipAge, exists = geoipExists,
                        onImport = { geoipLauncher.launch(arrayOf("*/*")) },
                    )
                    HorizontalDivider(color = Border)
                    GeoFileRow(
                        name = "geosite.dat", status = geositeAge, exists = geositeExists,
                        onImport = { geositeLauncher.launch(arrayOf("*/*")) },
                    )
                }

                SettingsSection(stringResource(R.string.section_source)) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(Surface)
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        GeoUrlField(stringResource(R.string.label_geoip_url), geoipUrl, onGeoipUrl)
                        GeoUrlField(stringResource(R.string.label_geosite_url), geositeUrl, onGeositeUrl)
                        TextButton(
                            onClick = {
                                onGeoipUrl(GeoUpdater.DEFAULT_GEOIP_URL)
                                onGeositeUrl(GeoUpdater.DEFAULT_GEOSITE_URL)
                            },
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            Text(stringResource(R.string.btn_reset_defaults), color = Dim, fontSize = 13.sp)
                        }
                    }
                }

                Button(
                    onClick = onUpdateNow,
                    enabled = !geoUpdating,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Accent, contentColor = OnAccent,
                        disabledContainerColor = Accent.copy(alpha = 0.35f), disabledContentColor = OnAccent.copy(alpha = 0.5f),
                    ),
                    contentPadding = PaddingValues(vertical = 17.dp),
                    elevation = ButtonDefaults.buttonElevation(0.dp),
                ) {
                    if (geoUpdating) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = OnAccent, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (geoUpdating) stringResource(R.string.btn_updating) else stringResource(R.string.btn_update_now),
                        fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
fun GeoFileRow(name: String, status: String, exists: Boolean, onImport: () -> Unit) {
    SettingRow(
        title = name,
        sub = status,
        onClick = onImport,
    ) {
        Text(
            stringResource(R.string.btn_geo_import),
            fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            color = if (exists) Dim else Accent,
        )
    }
}

@Composable
fun GeoUrlField(label: String, value: String, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionLabel(label)
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 11.5.sp),
            colors = inputColors(),
            singleLine = true,
        )
    }
}

@Composable
fun SettingsSection(label: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        SectionLabel(label)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, Border, RoundedCornerShape(16.dp))
        ) { content() }
    }
}

@Composable
fun SettingRow(
    title: String,
    sub: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Surface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
            if (sub != null) Text(sub, fontSize = 12.sp, color = Dim, modifier = Modifier.padding(top = 2.dp))
        }
        trailing()
    }
}

// ── Disguise settings screen ──────────────────────────────────────────────────

@Composable
fun DisguiseSettingsScreen(
    disguise: String,
    onDisguise: (String) -> Unit,
    onBack: () -> Unit,
) {
    val options = listOf(
        "default"    to stringResource(R.string.disguise_option_default),
        "alfa_bank"  to stringResource(R.string.disguise_option_alfa),
        "calculator" to stringResource(R.string.disguise_option_calculator),
    )
    Column(Modifier.fillMaxSize()) {
        PushHeader(stringResource(R.string.screen_disguise), onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x22E8A24A))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    Icons.Filled.Info, null,
                    tint = Warn,
                    modifier = Modifier
                        .size(16.dp)
                        .padding(top = 1.dp),
                )
                Text(
                    stringResource(R.string.disguise_restart_warning),
                    fontSize = 13.sp,
                    color = Warn,
                    lineHeight = 18.sp,
                )
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, Border, RoundedCornerShape(16.dp))
            ) {
                options.forEachIndexed { index, (key, label) ->
                    if (index > 0) HorizontalDivider(color = Border)
                    SettingRow(label, onClick = { if (disguise != key) onDisguise(key) }) {
                        if (disguise == key) {
                            Icon(Icons.Filled.Check, null, tint = Accent, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SmolToggle(on: Boolean, onChange: (Boolean) -> Unit) {
    val thumbPad by animateDpAsState(if (on) 21.dp else 2.dp, tween(200), label = "toggle")
    Box(
        Modifier
            .size(width = 46.dp, height = 27.dp)
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, if (on) Accent else Border2, RoundedCornerShape(20.dp))
            .background(if (on) AccentSoft else Surface3)
            .clickable { onChange(!on) },
    ) {
        Box(
            Modifier
                .offset(x = thumbPad, y = 3.dp)
                .size(21.dp)
                .clip(CircleShape)
                .background(if (on) Accent else Dim)
        )
    }
}

// ── Edit server screen ────────────────────────────────────────────────────────

@Composable
fun EditServerScreen(config: Config, onSave: (Config) -> Unit, onDelete: () -> Unit, onBack: () -> Unit) {
    var name by remember(config.id) { mutableStateOf(config.name) }
    var uri by remember(config.id) { mutableStateOf(config.vlessLink ?: config.configJson ?: "") }

    Column(Modifier.fillMaxSize()) {
        PushHeader(stringResource(R.string.screen_edit_server), onBack) {
            Button(
                onClick = {
                    val updated = if (config.vlessLink != null) config.copy(name = name, vlessLink = uri)
                    else config.copy(name = name, configJson = uri)
                    onSave(updated)
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 9.dp),
            ) { Text(stringResource(R.string.btn_save), fontWeight = FontWeight.Bold) }
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column {
                SectionLabel(stringResource(R.string.label_name))
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), singleLine = true,
                    colors = inputColors(),
                )
            }
            Column {
                SectionLabel(stringResource(R.string.label_vless_uri_json))
                OutlinedTextField(
                    value = uri, onValueChange = { uri = it },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    minLines = 4, maxLines = 8,
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.5.sp),
                    colors = inputColors(),
                )
            }
            OutlinedButton(
                onClick = onDelete, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Danger.copy(0.4f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Danger),
                contentPadding = PaddingValues(14.dp),
            ) {
                Icon(Icons.Default.Delete, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.btn_delete_server), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun inputColors() = OutlinedTextFieldDefaults.colors(
    unfocusedBorderColor = Border, focusedBorderColor = Accent,
    unfocusedContainerColor = Surface, focusedContainerColor = Surface,
    unfocusedTextColor = TextPrimary, focusedTextColor = TextPrimary,
    errorBorderColor = Danger,
)

// ── Import screen ─────────────────────────────────────────────────────────────

@Composable
fun ImportScreen(
    addError: String?,
    subscriptionImporting: Boolean,
    onAdd: (String) -> Unit,
    onSubscription: (String) -> Unit,
    onBack: () -> Unit,
    onClearError: () -> Unit,
    onToast: (String) -> Unit,
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf<String?>(null) } // "manual" | "subscription"
    var manualInput by remember { mutableStateOf("") }
    var subscriptionUrl by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        PushHeader(stringResource(R.string.screen_add_server), onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            // QR placeholder
            Box(
                Modifier
                    .size(150.dp)
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, Border, RoundedCornerShape(16.dp))
                    .background(Surface)
                    .padding(14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.QrCode, null, tint = Dim, modifier = Modifier.size(72.dp))
            }
            Spacer(Modifier.height(14.dp))
            SectionLabel(stringResource(R.string.label_or_add_another_way), modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(4.dp))

            ImportOption(
                icon = { Icon(Icons.Default.QrCode, null, tint = Accent, modifier = Modifier.size(22.dp)) },
                title = stringResource(R.string.import_scan_qr_title), sub = stringResource(R.string.import_scan_qr_sub),
                onClick = { onToast(context.getString(R.string.toast_qr_coming_soon)) },
            )
            Spacer(Modifier.height(11.dp))
            ImportOption(
                icon = { Icon(Icons.Default.ContentPaste, null, tint = Accent, modifier = Modifier.size(20.dp)) },
                title = stringResource(R.string.import_paste_title), sub = stringResource(R.string.import_paste_sub),
                onClick = {
                    val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
                        ?.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                    if (clip.isNotBlank()) {
                        manualInput = clip; expanded = "manual"; onClearError()
                    }
                    else onToast(context.getString(R.string.toast_clipboard_empty))
                },
            )
            Spacer(Modifier.height(11.dp))
            ImportOption(
                icon = { Icon(Icons.Default.Link, null, tint = Accent, modifier = Modifier.size(20.dp)) },
                title = stringResource(R.string.import_subscription_title), sub = stringResource(R.string.import_subscription_sub),
                onClick = {
                    expanded =
                        if (expanded == "subscription") null else "subscription"; onClearError()
                },
            )
            Spacer(Modifier.height(11.dp))
            ImportOption(
                icon = { Icon(Icons.Default.Edit, null, tint = Accent, modifier = Modifier.size(19.dp)) },
                title = stringResource(R.string.import_manual_title), sub = stringResource(R.string.import_manual_sub),
                onClick = {
                    expanded = if (expanded == "manual") null else "manual"; onClearError()
                },
            )

            AnimatedVisibility(visible = expanded == "subscription") {
                Column(Modifier.padding(top = 18.dp)) {
                    SectionLabel(stringResource(R.string.label_subscription_url))
                    OutlinedTextField(
                        value = subscriptionUrl,
                        onValueChange = { subscriptionUrl = it; onClearError() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        placeholder = {
                            Text(
                                stringResource(R.string.placeholder_url),
                                color = Dim,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.5.sp
                            )
                        },
                        isError = addError != null,
                        supportingText = addError?.let { err -> { Text(err, color = Danger) } },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.5.sp
                        ),
                        colors = inputColors(),
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { onSubscription(subscriptionUrl.trim()) },
                        enabled = subscriptionUrl.isNotBlank() && !subscriptionImporting,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Accent,
                            contentColor = OnAccent
                        ),
                        contentPadding = PaddingValues(14.dp),
                    ) {
                        if (subscriptionImporting) {
                            CircularProgressIndicator(
                                Modifier.size(16.dp),
                                color = OnAccent,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(10.dp))
                        }
                        Text(
                            if (subscriptionImporting) stringResource(R.string.btn_importing) else stringResource(R.string.btn_import_servers),
                            fontWeight = FontWeight.Bold, fontSize = 15.sp,
                        )
                    }
                }
            }

            AnimatedVisibility(visible = expanded == "manual") {
                Column(Modifier.padding(top = 18.dp)) {
                    SectionLabel(stringResource(R.string.label_vless_or_json))
                    OutlinedTextField(
                        value = manualInput,
                        onValueChange = { manualInput = it; onClearError() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        isError = addError != null,
                        supportingText = addError?.let { err -> { Text(err, color = Danger) } },
                        minLines = 3, maxLines = 6,
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.5.sp),
                        colors = inputColors(),
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { onAdd(manualInput.trim()) },
                        enabled = manualInput.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent),
                        contentPadding = PaddingValues(14.dp),
                    ) { Text(stringResource(R.string.btn_add_server_action), fontWeight = FontWeight.Bold, fontSize = 15.sp) }
                }
            }
        }
    }
}

@Composable
fun ImportOption(icon: @Composable () -> Unit, title: String, sub: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, Border, RoundedCornerShape(16.dp))
            .background(Surface)
            .clickable(onClick = onClick)
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(AccentSoft), contentAlignment = Alignment.Center
        ) {
            icon()
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(sub, fontSize = 12.5.sp, color = Dim, modifier = Modifier.padding(top = 2.dp))
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Dim, modifier = Modifier.size(18.dp))
    }
}

// ── App picker dialog ─────────────────────────────────────────────────────────

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
                                androidx.compose.foundation.Image(painter = icon!!, contentDescription = null, modifier = Modifier.size(36.dp))
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

// ── Update banner ─────────────────────────────────────────────────────────────

@Composable
fun UpdateBanner(info: UpdateInfo, progress: Float?, onInstall: () -> Unit, onDismiss: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, Accent.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .background(AccentSoft)
            .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Default.Download, null, tint = Accent, modifier = Modifier.size(17.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.update_available), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            val sub = buildString {
                append(info.tag)
                if (info.sizeLabel.isNotEmpty()) append(" · ${info.sizeLabel}")
            }
            Text(sub, fontSize = 11.5.sp, color = Dim, fontFamily = FontFamily.Monospace)
        }
        TextButton(
            onClick = onInstall,
            enabled = progress == null,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
        ) {
            if (progress != null) {
                Text(
                    "${(progress * 100).toInt()}%",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Dim,
                    fontFamily = FontFamily.Monospace
                )
            } else {
                Text(
                    stringResource(R.string.update_install),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Accent
                )
            }
        }
        Box(
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(enabled = progress == null, onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Close, null, tint = Dim, modifier = Modifier.size(15.dp))
        }
    }
}

// ── Geo update banner ─────────────────────────────────────────────────────────

// GEO UPDATE — remove this composable with GeoUpdater
@Composable
fun GeoUpdateBanner(onSkip: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, Border, RoundedCornerShape(16.dp))
            .background(Surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(Modifier.size(20.dp), color = Accent, strokeWidth = 2.dp)
        Text(stringResource(R.string.geo_updating_banner), fontSize = 14.sp, color = Dim, modifier = Modifier.weight(1f))
        TextButton(onClick = onSkip) { Text(stringResource(R.string.btn_skip), color = Accent) }
    }
}
// END GEO UPDATE

// ── Toast ─────────────────────────────────────────────────────────────────────

@Composable
fun BoxScope.ToastOverlay(toast: ToastData?, onDismiss: () -> Unit) {
    // Keep last non-null value so content is correct throughout enter AND exit animations
    var display by remember { mutableStateOf<ToastData?>(null) }
    if (toast != null) display = toast

    LaunchedEffect(toast) {
        if (toast != null) { delay(2100); onDismiss() }
    }
    AnimatedVisibility(
        visible = toast != null,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 34.dp),
        enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 2 },
        exit = fadeOut(tween(200)),
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(30.dp))
                .border(1.dp, Border2, RoundedCornerShape(30.dp))
                .background(Color(0xFF06120C))
                .padding(horizontal = 17.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            if (display?.isWarn == true) Icon(Icons.Default.Lock, null, tint = Warn, modifier = Modifier.size(14.dp))
            Text(display?.message ?: "", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFFEAFBF1))
        }
    }
}
