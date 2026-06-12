package com.green.android.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.green.android.BuildConfig
import com.green.android.R
import com.green.android.UpdateInfo
import com.green.android.ui.components.PushHeader
import com.green.android.ui.components.SettingRow
import com.green.android.ui.components.SettingsSection
import com.green.android.ui.components.SmolIconBtn
import com.green.android.ui.components.SmolToggle
import com.green.android.ui.theme.Accent
import com.green.android.ui.theme.Border
import com.green.android.ui.theme.Dim
import androidx.compose.ui.res.pluralStringResource

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
