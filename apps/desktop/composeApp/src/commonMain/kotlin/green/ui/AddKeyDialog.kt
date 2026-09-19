package green.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AddKeyDialog(
    onDismiss: () -> Unit,
    onConfirm: (uri: String, name: String) -> Unit,
) {
    var uri by remember { mutableStateOf("") }
    val trimmed = uri.trim()
    val uriError = trimmed.isNotEmpty() && !trimmed.startsWith("vless://")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1C1E27),
        shape = RoundedCornerShape(16.dp),
        title = {
            Column {
                Text(
                    "Add key",
                    color = TextPrimary,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Paste a vless:// link to import a server.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                )
            }
        },
        text = {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(88.dp)
                        .border(
                            1.dp,
                            if (uriError) DestructiveRed else BorderInput,
                            RoundedCornerShape(8.dp),
                        )
                        .background(BgInput, RoundedCornerShape(8.dp))
                        .padding(10.dp),
                ) {
                    BasicTextField(
                        value = uri,
                        onValueChange = { uri = it },
                        textStyle = TextStyle(
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                        ),
                        cursorBrush = SolidColor(AccentGreen),
                        modifier = Modifier.fillMaxSize(),
                        decorationBox = { inner ->
                            if (uri.isEmpty()) {
                                Text(
                                    "vless://...",
                                    color = TextSecondary,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                            inner()
                        },
                    )
                }
                if (uriError) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Must start with vless://",
                        color = DestructiveRed,
                        fontSize = 12.sp,
                    )
                } else {
                    Spacer(Modifier.height(20.dp))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val t = trimmed
                    val idx = t.lastIndexOf("#")
                    val name = if (idx >= 0) t.substring(idx + 1).ifBlank { "New key" } else "New key"
                    onConfirm(t, name)
                },
                enabled = trimmed.startsWith("vless://"),
                shape = RoundedCornerShape(100.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentGreen,
                    contentColor = OnAccent,
                    disabledContainerColor = BorderCard,
                    disabledContentColor = TextSecondary,
                ),
            ) { Text("Add", fontWeight = FontWeight.SemiBold) }
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
