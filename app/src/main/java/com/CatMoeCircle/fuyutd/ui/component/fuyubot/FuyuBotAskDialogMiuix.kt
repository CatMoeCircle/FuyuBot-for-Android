package com.CatMoeCircle.fuyutd.ui.component.fuyubot

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.CatMoeCircle.fuyutd.R
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayDialog

@Composable
fun FuyuBotAskDialogMiuix(
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
    OverlayDialog(
        show = true,
        title = title,
        onDismissRequest = onCancel,
        content = {
            Column {
                Text(
                    text = prompt,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                if (expectsInput) {
                    TextField(
                        modifier = Modifier.padding(bottom = 16.dp),
                        value = text,
                        singleLine = true,
                        label = prompt,
                        useLabelAsPlaceholder = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = keyboardType,
                        ),
                        visualTransformation = if (secret) {
                            PasswordVisualTransformation()
                        } else {
                            VisualTransformation.None
                        },
                        onValueChange = onTextChange,
                    )
                }
                Row {
                    TextButton(
                        text = stringResource(
                            if (expectsInput) R.string.fuyu_ask_cancel else R.string.fuyu_ask_close
                        ),
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                    )
                    if (expectsInput) {
                        Spacer(Modifier.width(20.dp))
                        TextButton(
                            text = stringResource(R.string.fuyu_ask_answer),
                            onClick = onConfirm,
                            enabled = canSubmit,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                        )
                    }
                }
            }
        },
    )
}
