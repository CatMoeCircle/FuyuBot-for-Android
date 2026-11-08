package com.CatMoeCircle.fuyutd.ui.component.fuyubot

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import com.CatMoeCircle.fuyutd.ui.LocalUiMode
import com.CatMoeCircle.fuyutd.ui.UiMode

/** 首次授权弹窗底部的文档链接：「起一个 bot 项目」一节讲怎么拿到 api_id / api_hash。 */
private const val CONFIG_DOCS_URL =
    "https://catmoecircle.github.io/FuyuBot-docs/docs/usage/" +
        "#%E8%B5%B7%E4%B8%80%E4%B8%AA-bot-%E9%A1%B9%E7%9B%AE"

/**
 * 首次授权配置弹窗：账号信息最终写入官方 `fuyubot.toml`。
 *
 * `api_id` / `api_hash` 必填；Bot Token 可选，只用于本次启动，不写入 TOML。
 *
 * 装配函数：管理输入状态，分发到 Miuix / Material 两套实现。
 */
@Composable
fun FuyuBotConfigDialog(
    show: Boolean,
    onConfirm: (apiId: Int, apiHash: String, botToken: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    // 底部文档链接：没有浏览器之类的接收方时静默失败，不影响弹窗本身。
    val openDocs: () -> Unit = {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(CONFIG_DOCS_URL))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

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
            onOpenDocs = openDocs,
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
            onOpenDocs = openDocs,
            onConfirm = confirm,
            onDismiss = onDismiss,
        )
    }
}
