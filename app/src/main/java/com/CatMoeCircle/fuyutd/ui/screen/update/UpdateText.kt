package com.CatMoeCircle.fuyutd.ui.screen.update

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.CatMoeCircle.fuyutd.R
import com.CatMoeCircle.fuyutd.data.update.UpdateChannel

/** 通道名称。 */
@Composable
fun channelTitle(channel: UpdateChannel): String = stringResource(
    when (channel) {
        UpdateChannel.App -> R.string.update_app
    }
)

/** 副标题：检查中显示「正在检查…」，失败显示错误，其余显示「当前版本 x.y.z」。 */
@Composable
fun channelStatusText(state: UpdateChannelState): String = when {
    state.status == UpdateCheckStatus.Checking -> stringResource(R.string.update_checking)
    state.status == UpdateCheckStatus.Failed -> state.error
        ?.let { stringResource(R.string.update_check_failed, it) }
        ?: stringResource(R.string.update_never_checked)

    else -> stringResource(R.string.update_current_version, state.currentVersion)
}

/**
 * 卡片右侧的动作文案，是用户点下去会发生的事：
 * 检查中 → 正在检查…；有新版 → 最新 x.x.x 版本 点击更新；
 * 已是最新 → 已是最新版本；其余 → 检查更新。
 */
@Composable
fun channelActionText(state: UpdateChannelState): String = when {
    state.hasUpdate -> stringResource(
        R.string.update_new_version_tag,
        state.latest?.versionName.orEmpty(),
    )

    state.status == UpdateCheckStatus.Checking -> stringResource(R.string.update_checking)
    state.status == UpdateCheckStatus.Success && state.latest != null ->
        stringResource(R.string.update_up_to_date)

    else -> stringResource(R.string.update_check_now)
}

/** 设置页入口摘要用：软件是否存在新版本。 */
val UpdateUiState.hasAnyUpdate: Boolean
    get() = app.hasUpdate

/**
 * 设置页入口的摘要：有新版时列出「软件 版本号」，已检查过则提示已是最新，
 * 否则返回空串交给界面使用默认说明。
 */
@Composable
fun updateEntrySummary(state: UpdateUiState): String = when {
    state.app.hasUpdate ->
        "${stringResource(R.string.update_app)} ${state.app.latest?.versionName.orEmpty()}"

    state.app.status == UpdateCheckStatus.Success ->
        stringResource(R.string.update_up_to_date)

    state.app.status == UpdateCheckStatus.Checking ->
        stringResource(R.string.update_checking)

    else -> ""
}
