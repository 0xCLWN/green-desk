package green.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import green.model.UpdateInfo

@Composable
fun UpdateBanner(
    info: UpdateInfo,
    progress: Float?,
    error: String?,
    onInstall: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, AccentGreen.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Default.Download, null, tint = AccentGreen, modifier = Modifier.size(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Update available",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = buildString {
                        append(info.tag)
                        if (info.sizeLabel.isNotEmpty()) append(" · ${info.sizeLabel}")
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                )
            }
            if (onInstall != null) {
                TextButton(
                    onClick = onInstall,
                    enabled = progress == null,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    if (progress != null) {
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace,
                        )
                    } else {
                        Text(
                            text = "Install",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentGreen,
                        )
                    }
                }
            }
            IconButton(
                onClick = onDismiss,
                enabled = progress == null,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        if (error != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = error,
                color = DestructiveRed,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
        }
    }
}
