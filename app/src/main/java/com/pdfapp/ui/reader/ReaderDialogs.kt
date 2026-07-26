package com.pdfapp.ui.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.pdfapp.ui.common.touchTargetFloor
import kotlin.math.roundToInt

/** "Page X of N — go to page" jump dialog (plan 2.1). */
@Composable
fun GoToPageDialog(
    currentPage: Int,
    pageCount: Int,
    onDismiss: () -> Unit,
    onGo: (pageIndex: Int) -> Unit,
) {
    var input by remember { mutableStateOf("${currentPage + 1}") }
    val parsed = input.toIntOrNull()?.takeIf { it in 1..pageCount }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Go to page") },
        text = {
            Column {
                Text("Page ${currentPage + 1} of $pageCount")
                if (pageCount > 1) {
                    Slider(
                        value = (parsed ?: currentPage + 1).toFloat(),
                        onValueChange = { input = it.roundToInt().toString() },
                        valueRange = 1f..pageCount.toFloat(),
                    )
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    label = { Text("Page number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = parsed != null,
                onClick = { parsed?.let { onGo(it - 1) } },
                modifier = Modifier.touchTargetFloor(),
            ) { Text("Go") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.touchTargetFloor()) { Text("Cancel") }
        },
    )
}

/** Password prompt for encrypted PDFs (plan 2.5). */
@Composable
fun PasswordDialog(
    wrongPassword: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Password required") },
        text = {
            Column {
                Text("This PDF is password-protected.")
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = wrongPassword,
                    supportingText =
                        if (wrongPassword) {
                            { Text("Wrong password, try again", color = MaterialTheme.colorScheme.error) }
                        } else {
                            null
                        },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = password.isNotEmpty(),
                onClick = { onSubmit(password) },
                modifier = Modifier.touchTargetFloor(),
            ) { Text("Unlock") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.touchTargetFloor()) { Text("Cancel") }
        },
    )
}
