package com.green.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.green.android.R
import com.green.android.UpdateInfo
import com.green.android.VpnStatus
import com.green.android.data.Config
import com.green.android.data.Subscription
import com.green.android.ui.components.AddServerCard
import com.green.android.ui.components.GeoUpdateBanner
import com.green.android.ui.components.SectionLabel
import com.green.android.ui.components.ServerCard
import com.green.android.ui.components.SmolIconBtn
import com.green.android.ui.components.SplitTunnelLine
import com.green.android.ui.components.UpdateBanner
import com.green.android.ui.theme.Accent
import com.green.android.ui.theme.AccentSoft
import com.green.android.ui.theme.Border
import com.green.android.ui.theme.Border2
import com.green.android.ui.theme.Dim
import com.green.android.ui.theme.OnAccent
import com.green.android.ui.theme.TextPrimary

@Composable
fun HomeContent(
    configs: List<Config>,
    subscriptions: List<Subscription>,
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
