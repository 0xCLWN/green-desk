package com.green.android

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.green.android.data.Config
import com.green.android.ui.components.AppPickerDialog
import com.green.android.ui.screens.ConnectedLayer
import com.green.android.ui.screens.DisguiseSettingsScreen
import com.green.android.ui.screens.EditServerScreen
import com.green.android.ui.screens.GeoSettingsScreen
import com.green.android.ui.screens.HomeContent
import com.green.android.ui.screens.ImportScreen
import com.green.android.ui.screens.SettingsScreen
import com.green.android.ui.screens.SubscriptionsScreen
import com.green.android.ui.theme.Border2
import com.green.android.ui.theme.Warn
import kotlinx.coroutines.delay

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

// ── Root ─────────────────────────────────────────────────────────────────────

@Composable
fun VpnApp(viewModel: VpnViewModel) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val strUpToDate = stringResource(R.string.toast_up_to_date)
    val strDisconnectBack = stringResource(R.string.toast_disconnect_to_go_back)
    val strDisconnectSettings = stringResource(R.string.toast_disconnect_to_change_settings)
    val strDisconnectSplit = stringResource(R.string.toast_disconnect_to_change_split)
    val strServerSaved = stringResource(R.string.toast_server_saved)
    val strServerDeleted = stringResource(R.string.toast_server_deleted)
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
        viewModel.noUpdateSignal.collect { showToast(strUpToDate) }
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
        showToast(strDisconnectBack, warn = true)
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
            onLocked = { shake = true; showToast(strDisconnectSettings, warn = true) },
            onSplitTap = { shake = true; showToast(strDisconnectSplit, warn = true) },
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
                    onSave = { viewModel.updateConfig(it); screen = NavScreen.Home; showToast(strServerSaved) },
                    onDelete = { viewModel.deleteConfig(cfg); screen = NavScreen.Home; showToast(strServerDeleted) },
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
