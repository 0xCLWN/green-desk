package com.green.android.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.green.android.R
import com.green.android.ui.components.PushHeader
import com.green.android.ui.components.SectionLabel
import com.green.android.ui.components.inputColors
import com.green.android.ui.theme.Accent
import com.green.android.ui.theme.AccentSoft
import com.green.android.ui.theme.Border
import com.green.android.ui.theme.Danger
import com.green.android.ui.theme.Dim
import com.green.android.ui.theme.OnAccent
import com.green.android.ui.theme.Surface
import com.green.android.ui.theme.TextPrimary

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
    val strQrComingSoon = stringResource(R.string.toast_qr_coming_soon)
    val strClipboardEmpty = stringResource(R.string.toast_clipboard_empty)
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
                onClick = { onToast(strQrComingSoon) },
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
                    else onToast(strClipboardEmpty)
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
