package com.CatMoeCircle.fuyutd.ui.component.fuyubot

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.CatMoeCircle.fuyutd.R

@Composable
fun FuyuBotAskDialogMaterial(
    title: String,
    prompt: String,
    text: TextFieldValue,
    keyboardType: KeyboardType,
    secret: Boolean,
    expectsInput: Boolean,
    canSubmit: Boolean,
    onTextChange: (TextFieldValue) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    text = prompt,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (expectsInput) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = text,
                        onValueChange = onTextChange,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                        visualTransformation = if (secret) {
                            PasswordVisualTransformation()
                        } else {
                            VisualTransformation.None
                        },
                    )
                }
            }
        },
        confirmButton = {
            if (expectsInput) {
                TextButton(onClick = onConfirm, enabled = canSubmit) {
                    Text(stringResource(R.string.fuyu_ask_answer))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(
                    stringResource(
                        if (expectsInput) R.string.fuyu_ask_cancel else R.string.fuyu_ask_close
                    )
                )
            }
        },
    )
}
