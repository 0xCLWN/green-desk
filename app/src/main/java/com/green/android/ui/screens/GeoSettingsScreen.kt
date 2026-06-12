package com.green.android.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.green.android.GeoUpdater
import com.green.android.R
import com.green.android.ui.components.PushHeader
import com.green.android.ui.components.SectionLabel
import com.green.android.ui.components.SettingRow
import com.green.android.ui.components.SettingsSection
import com.green.android.ui.components.SmolToggle
import com.green.android.ui.components.inputColors
import com.green.android.ui.theme.Accent
import com.green.android.ui.theme.Border
import com.green.android.ui.theme.Dim
import com.green.android.ui.theme.OnAccent
import com.green.android.ui.theme.Surface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun geoFileStatus(context: android.content.Context, filesDir: java.io.File, name: String): Pair<Boolean, String> {
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
    onImport: (Uri, String) -> Unit,
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
