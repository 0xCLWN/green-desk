package com.green.android.ui.components

import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.green.android.R
import com.green.android.data.Config
import com.green.android.nameWithFlag
import com.green.android.ui.theme.Accent
import com.green.android.ui.theme.AccentSoft
import com.green.android.ui.theme.Border
import com.green.android.ui.theme.Border2
import com.green.android.ui.theme.Dim
import com.green.android.ui.theme.Dim2
import com.green.android.ui.theme.Danger
import com.green.android.ui.theme.OnAccent
import com.green.android.ui.theme.Surface
import com.green.android.ui.theme.Surface3
import com.green.android.ui.theme.TextPrimary

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
fun inputColors() = OutlinedTextFieldDefaults.colors(
    unfocusedBorderColor = Border, focusedBorderColor = Accent,
    unfocusedContainerColor = Surface, focusedContainerColor = Surface,
    unfocusedTextColor = TextPrimary, focusedTextColor = TextPrimary,
    errorBorderColor = Danger,
)

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
