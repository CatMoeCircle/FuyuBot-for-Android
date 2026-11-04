package com.CatMoeCircle.fuyutd.ui.component.fuyubot

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import com.CatMoeCircle.fuyutd.ui.LocalUiMode
import com.CatMoeCircle.fuyutd.ui.UiMode

/**
 * 首次授权配置弹窗：api_id / api_hash 必填，bot_token 可选。
 *
 * 装配函数：管理输入状态，分发到 Miuix / Material 两套实现。
 */
@Composable
fun FuyuBotConfigDialog(
    show: Boolean,
    onConfirm: (apiId: Int, apiHash: String, botToken: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var apiId by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var apiHash by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var botToken by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var error by remember { mutableStateOf<String?>(null) }

    val confirm: () -> Unit = {
        val id = apiId.text.trim().toIntOrNull()
        error = when {
            id == null || id <= 0 -> "id"
            apiHash.text.isBlank() -> "hash"
            else -> {
                onConfirm(id, apiHash.text.trim(), botToken.text.trim())
                null
            }
        }
    }

    when (LocalUiMode.current) {
        UiMode.Miuix -> FuyuBotConfigDialogMiuix(
            show = show,
            apiId = apiId,
            apiHash = apiHash,
            botToken = botToken,
            error = error,
            onApiIdChange = { apiId = it },
            onApiHashChange = { apiHash = it },
            onBotTokenChange = { botToken = it },
            onConfirm = confirm,
            onDismiss = onDismiss,
        )

        UiMode.Material -> FuyuBotConfigDialogMaterial(
            show = show,
            apiId = apiId,
            apiHash = apiHash,
            botToken = botToken,
            error = error,
            onApiIdChange = { apiId = it },
            onApiHashChange = { apiHash = it },
            onBotTokenChange = { botToken = it },
            onConfirm = confirm,
            onDismiss = onDismiss,
        )
    }
}
