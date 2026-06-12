package com.green.android.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.green.android.R
import com.green.android.data.Subscription
import com.green.android.ui.components.PushHeader
import com.green.android.ui.components.SectionLabel
import com.green.android.ui.components.SettingsSection
import com.green.android.ui.components.SmolIconBtn
import com.green.android.ui.components.inputColors
import com.green.android.ui.theme.Accent
import com.green.android.ui.theme.Border
import com.green.android.ui.theme.Danger
import com.green.android.ui.theme.Dim
import com.green.android.ui.theme.OnAccent
import com.green.android.ui.theme.Surface
import com.green.android.ui.theme.TextPrimary

@Composable
fun SubscriptionsScreen(
    subscriptions: List<Subscription>,
    addError: String?,
    subscriptionImporting: Boolean,
    onAdd: (String) -> Unit,
    onDelete: (Subscription) -> Unit,
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
