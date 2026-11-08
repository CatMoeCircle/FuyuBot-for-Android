package com.CatMoeCircle.fuyutd.ui.screen.update

import androidx.compose.runtime.Immutable
import com.CatMoeCircle.fuyutd.data.update.ReleaseInfo
import com.CatMoeCircle.fuyutd.data.update.UpdateChannel
import com.CatMoeCircle.fuyutd.data.update.compareVersion

/** 单条更新通道的检查状态。 */
enum class UpdateCheckStatus {
    Idle,
    Checking,
    Success,
    Failed,
}

@Immutable
data class UpdateChannelState(
    val channel: UpdateChannel,
    val status: UpdateCheckStatus = UpdateCheckStatus.Idle,
    /** 已安装的版本号。 */
    val currentVersion: String = "",
    val latest: ReleaseInfo? = null,
    /** 按当前设备 ABI 从 Release 资产中选出的推荐 APK 文件名。 */
    val recommendedApkName: String? = null,
    val error: String? = null,
) {

    val hasUpdate: Boolean
        get() = latest != null &&
                currentVersion.isNotEmpty() &&
                compareVersion(latest.versionName, currentVersion) > 0
}

/** 自动检查发现新版本后，待用户确认的一条提示。 */
@Immutable
data class UpdatePromptEntry(
    val channel: UpdateChannel,
    val latest: ReleaseInfo,
    val currentVersion: String,
    val recommendedApkName: String?,
)

/**
 * 自动检查的汇总提示。
 */
@Immutable
data class UpdatePrompt(
    val entries: List<UpdatePromptEntry>,
)

@Immutable
data class UpdateUiState(
    val app: UpdateChannelState = UpdateChannelState(UpdateChannel.App),
    val autoCheck: Boolean = true,
    val prompt: UpdatePrompt? = null,
) {

    fun stateOf(channel: UpdateChannel): UpdateChannelState = when (channel) {
        UpdateChannel.App -> app
    }
}

@Immutable
data class UpdateScreenActions(
    val onBack: () -> Unit,
    val onAppAction: () -> Unit,
    val onSetAutoCheck: (Boolean) -> Unit,
    val onOpenUrl: (String) -> Unit,
)
