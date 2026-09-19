package green.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsDialog(
    socksPort: Int,
    httpPort: Int,
    checkingUpdate: Boolean,
    updateCheckResult: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (socksPort: Int, httpPort: Int) -> Unit,
    onCheckUpdate: () -> Unit,
) {
    var socksText by remember { mutableStateOf(socksPort.toString()) }
    var httpText by remember { mutableStateOf(httpPort.toString()) }

    val socksValid = socksText.toIntOrNull()?.let { it in 1..65535 } == true
    val httpValid = httpText.toIntOrNull()?.let { it in 1..65535 } == true

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1C1E27),
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                "Settings",
                color = TextPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PortField(
                    label = "SOCKS5 PORT",
                    value = socksText,
                    isError = !socksValid,
                    onValueChange = { socksText = it.filter(Char::isDigit).take(5) },
                )
                PortField(
                    label = "HTTP PORT",
                    value = httpText,
                    isError = !httpValid,
                    onValueChange = { httpText = it.filter(Char::isDigit).take(5) },
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Check for updates",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f),
                        )
                        if (checkingUpdate) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = AccentGreen,
                            )
                        } else {
                            TextButton(
                                onClick = onCheckUpdate,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Text("Check", color = AccentGreen, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    if (updateCheckResult != null) {
                        val isError = updateCheckResult.startsWith("Failed")
                        Text(
                            text = updateCheckResult,
                            color = if (isError) DestructiveRed else AccentGreen,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 2.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val s = socksText.toIntOrNull() ?: return@Button
                    val h = httpText.toIntOrNull() ?: return@Button
                    onConfirm(s, h)
                },
                enabled = socksValid && httpValid,
                shape = RoundedCornerShape(100.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentGreen,
                    contentColor = OnAccent,
                    disabledContainerColor = BorderCard,
                    disabledContentColor = TextSecondary,
                ),
            ) { Text("Save", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(100.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderCard),
            ) { Text("Cancel") }
        },
    )
}

@Composable
private fun PortField(
    label: String,
    value: String,
    isError: Boolean,
    onValueChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.6.sp,
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            textStyle = TextStyle(
                color = TextPrimary,
                fontSize = 14.sp,
            ),
            cursorBrush = SolidColor(AccentGreen),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    1.dp,
                    if (isError) DestructiveRed else BorderInput,
                    RoundedCornerShape(8.dp),
                )
                .background(BgInput, RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}
