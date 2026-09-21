package green.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import green.model.Subscription

@Composable
fun SubscriptionsDialog(
    subscriptions: List<Subscription>,
    addingSubscription: Boolean,
    subscriptionError: String?,
    refreshingSubscriptionIds: Set<String>,
    onAdd: (url: String) -> Unit,
    onRemove: (id: String) -> Unit,
    onRefreshAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    var url by remember { mutableStateOf("") }
    val trimmed = url.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1C1E27),
        shape = RoundedCornerShape(16.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Subscriptions",
                        color = TextPrimary,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Import servers from a subscription URL.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                    )
                }
                if (subscriptions.isNotEmpty()) {
                    if (refreshingSubscriptionIds.isNotEmpty()) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = AccentGreen,
                        )
                    } else {
                        TextButton(
                            onClick = onRefreshAll,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text("Refresh all", color = AccentGreen, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BorderInput, RoundedCornerShape(8.dp))
                        .background(BgInput, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    BasicTextField(
                        value = url,
                        onValueChange = { url = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                        ),
                        cursorBrush = SolidColor(AccentGreen),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            if (url.isEmpty()) {
                                Text(
                                    "https://.../subscription",
                                    color = TextSecondary,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                            inner()
                        },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (addingSubscription) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = AccentGreen,
                        )
                    } else {
                        Button(
                            onClick = { onAdd(trimmed); url = "" },
                            enabled = trimmed.isNotEmpty(),
                            shape = RoundedCornerShape(100.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentGreen,
                                contentColor = OnAccent,
                                disabledContainerColor = BorderCard,
                                disabledContentColor = TextSecondary,
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        ) { Text("Add", fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
                    }
                }
                if (subscriptionError != null) {
                    Text(subscriptionError, color = DestructiveRed, fontSize = 12.sp)
                }
                if (subscriptions.isNotEmpty()) {
                    HorizontalDivider(color = BorderCard, thickness = 1.dp)
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.heightIn(max = 220.dp),
                    ) {
                        items(subscriptions, key = { it.id }) { sub ->
                            SubscriptionRow(
                                subscription = sub,
                                refreshing = sub.id in refreshingSubscriptionIds,
                                onRemove = { onRemove(sub.id) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(100.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderCard),
            ) { Text("Done") }
        },
    )
}

@Composable
private fun SubscriptionRow(
    subscription: Subscription,
    refreshing: Boolean,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(BgCard)
            .border(1.dp, BorderCard, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = subscription.name,
                color = TextPrimary,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subscription.lastError
                    ?: subscription.lastUpdated?.let { "Synced" }
                    ?: subscription.url,
                color = if (subscription.lastError != null) DestructiveRed else TextMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (refreshing) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp).padding(end = 6.dp),
                strokeWidth = 2.dp,
                color = AccentGreen,
            )
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Remove subscription",
                tint = TextSecondary,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
