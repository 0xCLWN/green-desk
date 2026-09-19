package com.green.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.green.android.R
import com.green.android.UpdateInfo
import com.green.android.ui.theme.Accent
import com.green.android.ui.theme.AccentSoft
import com.green.android.ui.theme.Border
import com.green.android.ui.theme.Dim
import com.green.android.ui.theme.Surface
import com.green.android.ui.theme.TextPrimary
import androidx.compose.ui.res.stringResource

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
