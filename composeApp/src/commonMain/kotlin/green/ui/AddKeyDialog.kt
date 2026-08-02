package green.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AddKeyDialog(
    onDismiss: () -> Unit,
    onConfirm: (uri: String, name: String) -> Unit,
) {
    var uri by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    val uriError = uri.isNotEmpty() && !uri.startsWith("vless://")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Key") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = uri,
                    onValueChange = { uri = it.trim() },
                    label = { Text("vless:// link") },
                    isError = uriError,
                    supportingText = if (uriError) {
                        { Text("Must start with vless://") }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val resolvedName = name.ifBlank { uri.substringAfterLast("#").ifBlank { "Key" } }
                    onConfirm(uri, resolvedName)
                },
                enabled = uri.startsWith("vless://"),
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
