package com.CatMoeCircle.fuyutd.ui.component.fuyubot

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import com.CatMoeCircle.fuyutd.R
import com.CatMoeCircle.fuyutd.data.kernel.AskKind
import com.CatMoeCircle.fuyutd.data.kernel.FuyuBotAsk
import com.CatMoeCircle.fuyutd.ui.LocalUiMode
import com.CatMoeCircle.fuyutd.ui.UiMode

/**
 * 登录弹窗：展示内核给的提示语，让用户输入手机号 / 验证码 / 两步验证密码。
 *
 * 内核在 `authorizationStateWaitPhoneNumber` / `WaitCode` / `WaitPassword` 时通过
 * `onAsk` 抛提问，`prompt` 本身已经是一句中文说明（例如「手机号（带国家码，例如 …）」），
 * 这里只按 [AskKind] 决定标题、键盘类型与是否掩码，不再自己拼提示语。
 *
 * 「取消」/ 点弹窗外部都走 `onAnswer(id, null)`，内核据此放弃这次登录；
 * 需要输入的提问在没有内容时不放开「确定」，避免把空串当成答案发过去。
 */
@Composable
fun FuyuBotAskDialog(
    ask: FuyuBotAsk?,
    onAnswer: (id: Int, text: String?) -> Unit,
) {
    val current = ask ?: return
    var text by rememberSaveable(current.id, stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    val spec = current.inputSpec()
    // 需要输入的提问，空内容不允许提交；纯提示类（OTHER）没有输入框。
    val canSubmit = !spec.expectsInput || text.text.isNotBlank()
    val submit = { if (canSubmit) onAnswer(current.id, text.text.trim()) }

    when (LocalUiMode.current) {
        UiMode.Miuix -> FuyuBotAskDialogMiuix(
            title = spec.title,
            prompt = current.prompt,
            text = text,
            keyboardType = spec.keyboardType,
            secret = spec.secret,
            expectsInput = spec.expectsInput,
            canSubmit = canSubmit,
            onTextChange = { text = it },
            onConfirm = submit,
            onCancel = { onAnswer(current.id, null) },
        )

        UiMode.Material -> FuyuBotAskDialogMaterial(
            title = spec.title,
            prompt = current.prompt,
            text = text,
            keyboardType = spec.keyboardType,
            secret = spec.secret,
            expectsInput = spec.expectsInput,
            canSubmit = canSubmit,
            onTextChange = { text = it },
            onConfirm = submit,
            onCancel = { onAnswer(current.id, null) },
        )
    }
}

/**
 * 输入框形态：由提问类型决定标题、软键盘与是否掩码。
 *
 * [expectsInput] 为 false 表示这条提问只是让用户知道「内核这一步不支持」，
 * 只给一个关闭按钮——输入什么都不会被内核接受。
 */
internal data class AskInputSpec(
    val title: String,
    val keyboardType: KeyboardType,
    val secret: Boolean,
    val expectsInput: Boolean,
)

@Composable
internal fun FuyuBotAsk.inputSpec(): AskInputSpec = when (kind) {
    AskKind.PHONE_NUMBER -> AskInputSpec(
        title = stringResource(R.string.fuyu_login_title_phone_number),
        keyboardType = KeyboardType.Phone,
        secret = false,
        expectsInput = true,
    )

    AskKind.VERIFICATION_CODE -> AskInputSpec(
        title = stringResource(R.string.fuyu_login_title_code),
        keyboardType = KeyboardType.Number,
        secret = false,
        expectsInput = true,
    )

    AskKind.TWO_STEP_PASSWORD -> AskInputSpec(
        title = stringResource(R.string.fuyu_login_title_password),
        keyboardType = KeyboardType.Password,
        secret = true,
        expectsInput = true,
    )

    AskKind.BOT_TOKEN -> AskInputSpec(
        title = stringResource(R.string.fuyu_login_title_bot_token),
        keyboardType = KeyboardType.Text,
        secret = false,
        expectsInput = true,
    )

    // 登录方式（kind 4）由 HomeViewModel 自动回「phone」，正常不会显示；
    // OTHER 是内核明确表示「本版不支持」的提示，只需展示。
    AskKind.LOGIN_METHOD, AskKind.OTHER -> AskInputSpec(
        title = stringResource(R.string.fuyu_login_title_other),
        keyboardType = KeyboardType.Text,
        secret = false,
        expectsInput = false,
    )
}
