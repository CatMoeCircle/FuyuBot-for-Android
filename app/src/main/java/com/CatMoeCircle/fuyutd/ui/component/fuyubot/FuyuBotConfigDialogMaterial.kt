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
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.CatMoeCircle.fuyutd.R

@Composable
fun FuyuBotConfigDialogMaterial(
    show: Boolean,
    apiId: TextFieldValue,
    apiHash: TextFieldValue,
    botToken: TextFieldValue,
    error: String?,
    onApiIdChange: (TextFieldValue) -> Unit,
    onApiHashChange: (TextFieldValue) -> Unit,
    onBotTokenChange: (TextFieldValue) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!show) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.fuyu_config_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.fuyu_config_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = apiId,
                    onValueChange = { value ->
                        if (value.text.all { it.isDigit() } || value.text.isEmpty()) {
                            onApiIdChange(value)
                        }
                    },
                    label = { Text(stringResource(R.string.fuyu_config_api_id)) },
                    placeholder = { Text(stringResource(R.string.fuyu_config_api_id_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = apiHash,
                    onValueChange = onApiHashChange,
                    label = { Text(stringResource(R.string.fuyu_config_api_hash)) },
                    placeholder = { Text(stringResource(R.string.fuyu_config_api_hash_hint)) },
                    singleLine = true,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = botToken,
                    onValueChange = onBotTokenChange,
                    label = { Text(stringResource(R.string.fuyu_config_bot_token)) },
                    placeholder = { Text(stringResource(R.string.fuyu_config_bot_token_hint)) },
                    singleLine = true,
                )
                if (error != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(
                            when (error) {
                                "id" -> R.string.fuyu_config_error_id
                                else -> R.string.fuyu_config_error_hash
                            }
                        ),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.fuyu_start))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}
