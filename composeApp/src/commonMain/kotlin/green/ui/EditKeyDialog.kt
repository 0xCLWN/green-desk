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
import green.model.VlessKey

@Composable
fun EditKeyDialog(
    key: VlessKey,
    onDismiss: () -> Unit,
    onConfirm: (name: String, uri: String) -> Unit,
) {
    var name by remember { mutableStateOf(key.name) }
    var uri by remember { mutableStateOf(key.uri) }
    val uriError = uri.trim().isNotEmpty() && !uri.trim().startsWith("vless://")
    val canSave = name.isNotBlank() && uri.trim().startsWith("vless://")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1C1E27),
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                "Edit key",
                color = TextPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "NAME",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.6.sp,
                    )
                    BasicTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp),
                        cursorBrush = SolidColor(AccentGreen),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, BorderInput, RoundedCornerShape(8.dp))
                            .background(BgInput, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "VLESS LINK",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.6.sp,
                    )
                    BasicTextField(
                        value = uri,
                        onValueChange = { uri = it },
                        textStyle = TextStyle(
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                        ),
                        cursorBrush = SolidColor(AccentGreen),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .border(
                                1.dp,
                                if (uriError) DestructiveRed else BorderInput,
                                RoundedCornerShape(8.dp),
                            )
                            .background(BgInput, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                    if (uriError) {
                        Text("Must start with vless://", color = DestructiveRed, fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name.trim(), uri.trim()) },
                enabled = canSave,
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
