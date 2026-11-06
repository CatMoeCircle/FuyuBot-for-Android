package com.CatMoeCircle.fuyutd.ui.screen.plugin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.CatMoeCircle.fuyutd.R
import com.CatMoeCircle.fuyutd.data.plugin.InstalledPlugin
import com.CatMoeCircle.fuyutd.ui.LocalUiMode
import com.CatMoeCircle.fuyutd.ui.UiMode
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 内核停止时删除插件的确认弹窗。
 *
 * 停止状态下点「卸载」只会把卡片划掉、按钮变成「确认卸载」；再点「确认卸载」才弹这个框，
 * 点「确认」后才真正删除（删掉后不可恢复）。内核运行中的删除走「待卸载 + 撤销」，不弹这个框。
 */
@Composable
fun PluginUninstallDialog(
    pending: InstalledPlugin?,
    visible: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (!visible || pending == null) return
    when (LocalUiMode.current) {
        UiMode.Miuix -> MiuixUninstallDialog(onDismiss = onDismiss, onConfirm = onConfirm)
        UiMode.Material -> MaterialUninstallDialog(onDismiss = onDismiss, onConfirm = onConfirm)
    }
}

@Composable
private fun MiuixUninstallDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    WindowDialog(
        show = true,
        title = stringResource(R.string.plugin_uninstall_confirm_title),
        onDismissRequest = onDismiss,
        content = {
            Column {
                MiuixText(
                    text = stringResource(R.string.plugin_uninstall_confirm_message),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
                Row {
                    MiuixTextButton(
                        text = stringResource(android.R.string.cancel),
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(20.dp))
                    MiuixTextButton(
                        text = stringResource(R.string.plugin_uninstall_confirm_action),
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            }
        },
    )
}

@Composable
private fun MaterialUninstallDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.plugin_uninstall_confirm_title)) },
        text = { Text(stringResource(R.string.plugin_uninstall_confirm_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.plugin_uninstall_confirm_action),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}
