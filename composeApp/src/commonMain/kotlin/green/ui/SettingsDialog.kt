package green.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun SettingsDialog(
    socksPort: Int,
    httpPort: Int,
    onDismiss: () -> Unit,
    onConfirm: (socksPort: Int, httpPort: Int) -> Unit,
) {
    var socksText by remember { mutableStateOf(socksPort.toString()) }
    var httpText by remember { mutableStateOf(httpPort.toString()) }

    val socksValid = socksText.toIntOrNull()?.let { it in 1..65535 } == true
    val httpValid = httpText.toIntOrNull()?.let { it in 1..65535 } == true

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Proxy Ports") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = socksText,
                    onValueChange = { socksText = it.filter(Char::isDigit).take(5) },
                    label = { Text("SOCKS5 Port") },
                    isError = !socksValid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = httpText,
                    onValueChange = { httpText = it.filter(Char::isDigit).take(5) },
                    label = { Text("HTTP Port") },
                    isError = !httpValid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val s = socksText.toIntOrNull() ?: return@TextButton
                    val h = httpText.toIntOrNull() ?: return@TextButton
                    onConfirm(s, h)
                },
                enabled = socksValid && httpValid,
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
