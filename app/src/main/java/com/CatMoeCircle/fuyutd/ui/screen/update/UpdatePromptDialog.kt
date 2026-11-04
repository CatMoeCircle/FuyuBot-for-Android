package com.CatMoeCircle.fuyutd.ui.screen.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.CatMoeCircle.fuyutd.R
import com.CatMoeCircle.fuyutd.data.update.UpdateChannel
import com.CatMoeCircle.fuyutd.ui.LocalUiMode
import com.CatMoeCircle.fuyutd.ui.UiMode
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog

/**
 * 自动检查发现新版本后的提示弹窗。三条出路：立即去更新、今天不再提醒、稍后再说。
 *
 * 多条通道同时有新版时合并成一条消息，下载按钮优先跳内核所在的 Release。
 */
@Composable
fun UpdatePromptDialog(
    prompt: UpdatePrompt?,
    onDismiss: () -> Unit,
    onSnoozeToday: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val current = prompt ?: return
    val entry = current.entries.firstOrNull() ?: return

    val title = stringResource(R.string.update_prompt_title)
    val message = buildString {
        current.entries.forEachIndexed { index, item ->
            if (index > 0) append('\n')
            val channelName = when (item.channel) {
                UpdateChannel.App -> stringResource(R.string.update_app)
            }
            append(
                stringResource(
                    R.string.update_prompt_message,
                    channelName,
                    item.latest.versionName,
                    item.currentVersion,
                )
            )
        }
    }
    val downloadUrl = entry.latest.downloadUrlFor(".apk")
    val confirm = {
        onDismiss()
        onOpenUrl(downloadUrl)
    }

    when (LocalUiMode.current) {
        UiMode.Miuix -> MiuixUpdatePromptDialog(
            title = title,
            message = message,
            onDismiss = onDismiss,
            onSnoozeToday = onSnoozeToday,
            onConfirm = confirm,
        )

        UiMode.Material -> MaterialUpdatePromptDialog(
            title = title,
            message = message,
            onDismiss = onDismiss,
            onSnoozeToday = onSnoozeToday,
            onConfirm = confirm,
        )
    }
}

@Composable
private fun MiuixUpdatePromptDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onSnoozeToday: () -> Unit,
    onConfirm: () -> Unit,
) {
    OverlayDialog(
        show = true,
        title = title,
        onDismissRequest = onDismiss,
        content = {
            Column(
                modifier = Modifier
                    .heightIn(max = 240.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 12.dp),
            ) {
                MiuixText(text = message)
            }
            Row {
                TextButton(
                    text = stringResource(R.string.update_prompt_snooze),
                    onClick = {
                        onSnoozeToday()
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                TextButton(
                    text = stringResource(R.string.update_prompt_confirm),
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        },
    )
}

@Composable
private fun MaterialUpdatePromptDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onSnoozeToday: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 240.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(message)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.update_prompt_confirm))
            }
        },
        dismissButton = {
            Column {
                TextButton(
                    onClick = {
                        onSnoozeToday()
                        onDismiss()
                    }
                ) {
                    Text(stringResource(R.string.update_prompt_snooze))
                }
                TextButton(onClick = onDismiss) {
                    Text(
                        text = stringResource(R.string.update_prompt_later),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}
