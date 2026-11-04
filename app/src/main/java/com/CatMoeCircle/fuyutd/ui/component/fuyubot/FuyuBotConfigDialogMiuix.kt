package com.CatMoeCircle.fuyutd.ui.component.fuyubot

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.CatMoeCircle.fuyutd.R
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

@Composable
fun FuyuBotConfigDialogMiuix(
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
    OverlayDialog(
        show = show,
        title = stringResource(R.string.fuyu_config_title),
        summary = stringResource(R.string.fuyu_config_summary),
        onDismissRequest = onDismiss,
        content = {
            Column {
                TextField(
                    modifier = Modifier.padding(bottom = 12.dp),
                    value = apiId,
                    singleLine = true,
                    label = stringResource(R.string.fuyu_config_api_id_hint),
                    useLabelAsPlaceholder = true,
                    onValueChange = { value ->
                        if (value.text.all { it.isDigit() } || value.text.isEmpty()) {
                            onApiIdChange(value)
                        }
                    },
                )
                TextField(
                    modifier = Modifier.padding(bottom = 12.dp),
                    value = apiHash,
                    singleLine = true,
                    label = stringResource(R.string.fuyu_config_api_hash_hint),
                    useLabelAsPlaceholder = true,
                    onValueChange = onApiHashChange,
                )
                TextField(
                    modifier = Modifier.padding(bottom = 12.dp),
                    value = botToken,
                    singleLine = true,
                    label = stringResource(R.string.fuyu_config_bot_token_hint),
                    useLabelAsPlaceholder = true,
                    onValueChange = onBotTokenChange,
                )
                if (error != null) {
                    Text(
                        text = stringResource(
                            when (error) {
                                "id" -> R.string.fuyu_config_error_id
                                else -> R.string.fuyu_config_error_hash
                            }
                        ),
                        color = colorScheme.error,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
                Row {
                    TextButton(
                        text = stringResource(android.R.string.cancel),
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(20.dp))
                    TextButton(
                        text = stringResource(R.string.fuyu_start),
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            }
        },
    )
}
