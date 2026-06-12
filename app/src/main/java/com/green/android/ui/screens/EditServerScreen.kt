package com.green.android.ui.screens

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.green.android.R
import com.green.android.data.Config
import com.green.android.ui.components.PushHeader
import com.green.android.ui.components.SectionLabel
import com.green.android.ui.components.inputColors
import com.green.android.ui.theme.Accent
import com.green.android.ui.theme.Danger
import com.green.android.ui.theme.OnAccent

@Composable
fun EditServerScreen(config: Config, onSave: (Config) -> Unit, onDelete: () -> Unit, onBack: () -> Unit) {
    var name by remember(config.id) { mutableStateOf(config.name) }
    var uri by remember(config.id) { mutableStateOf(config.vlessLink ?: config.configJson ?: "") }

    Column(Modifier.fillMaxSize()) {
        PushHeader(stringResource(R.string.screen_edit_server), onBack) {
            Button(
                onClick = {
                    val updated = if (config.vlessLink != null) config.copy(name = name, vlessLink = uri)
                    else config.copy(name = name, configJson = uri)
                    onSave(updated)
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 9.dp),
            ) { Text(stringResource(R.string.btn_save), fontWeight = FontWeight.Bold) }
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column {
                SectionLabel(stringResource(R.string.label_name))
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), singleLine = true,
                    colors = inputColors(),
                )
            }
            Column {
                SectionLabel(stringResource(R.string.label_vless_uri_json))
                OutlinedTextField(
                    value = uri, onValueChange = { uri = it },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    minLines = 4, maxLines = 8,
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.5.sp),
                    colors = inputColors(),
                )
            }
            OutlinedButton(
                onClick = onDelete, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Danger.copy(0.4f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Danger),
                contentPadding = PaddingValues(14.dp),
            ) {
                Icon(Icons.Default.Delete, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.btn_delete_server), fontWeight = FontWeight.Bold)
            }
        }
    }
}
