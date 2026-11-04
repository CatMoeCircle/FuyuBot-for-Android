package com.CatMoeCircle.fuyutd.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.CatMoeCircle.fuyutd.data.SettingsRepository
import com.CatMoeCircle.fuyutd.data.update.UpdateChannel
import com.CatMoeCircle.fuyutd.data.update.UpdateRepository
import com.CatMoeCircle.fuyutd.ui.screen.update.UpdateCheckStatus
import com.CatMoeCircle.fuyutd.ui.screen.update.UpdatePrompt
import com.CatMoeCircle.fuyutd.ui.screen.update.UpdatePromptEntry
import com.CatMoeCircle.fuyutd.ui.screen.update.UpdateUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * 更新检查的状态中枢：设置页入口摘要、二级「检查更新」页、启动自动检查共用同一份状态。
 *
 * 内核已随 App 内置，本 ViewModel 只负责**软件**更新检查。
 *
 * 自动检查受两条规则约束：
 * 1. 用户可关闭「自动检查更新」；
 * 2. 用户在弹窗里选择稍后提醒后，会写入暂缓截止时间，期间连网络请求都不发。
 */
class UpdateViewModel(
    private val repo: SettingsRepository = SettingsRepository(),
    private val updateRepository: UpdateRepository = UpdateRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(UpdateUiState(autoCheck = repo.autoCheckUpdate))
    val uiState: StateFlow<UpdateUiState> = _uiState.asStateFlow()

    private var appVersionLoaded = false
    private var lastAutoCheckAt = 0L
    private var checkJob: Job? = null
    private val promptedChannels = mutableSetOf<UpdateChannel>()

    /** 读取 App 版本号，进入页面时调用一次。 */
    fun bind(context: Context) {
        if (appVersionLoaded) return
        appVersionLoaded = true
        val versionName = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty().removePrefix("v")
        _uiState.update { it.copy(app = it.app.copy(currentVersion = versionName)) }
    }

    fun setAutoCheck(enabled: Boolean) {
        repo.autoCheckUpdate = enabled
        _uiState.update { it.copy(autoCheck = enabled) }
    }

    fun dismissPrompt() {
        _uiState.update { it.copy(prompt = null) }
    }

    /** 弹窗里选择「今天不再提醒」：今天之内不再自动请求，用户仍可手动检查。 */
    fun snoozeToday() {
        repo.updateSnoozeUntil = startOfTomorrow()
        _uiState.update { it.copy(prompt = null) }
    }

    /**
     * 应用进入前台时调用；只有满足条件才真正联网。
     *
     * 每次回到前台都会走到这里，因此用 [AUTO_CHECK_INTERVAL_MS] 节流，
     * 避免频繁切后台时反复打 GitHub 接口。
     */
    fun autoCheckIfNeeded() {
        if (!repo.autoCheckUpdate) return
        val now = System.currentTimeMillis()
        if (repo.updateSnoozeUntil > now) return
        if (now - lastAutoCheckAt < AUTO_CHECK_INTERVAL_MS) return
        lastAutoCheckAt = now
        check(UpdateChannel.App, promptWhenFound = true)
    }

    fun check(channel: UpdateChannel, promptWhenFound: Boolean = false) {
        if (checkJob?.isActive == true) return

        val state = _uiState.value.stateOf(channel)
        _uiState.update {
            it.copy(app = state.copy(status = UpdateCheckStatus.Checking, error = null))
        }

        checkJob = viewModelScope.launch {
            updateRepository.fetchLatest(channel).fold(
                onSuccess = { release ->
                    _uiState.update { current ->
                        val updatedState = current.stateOf(channel).copy(
                            status = UpdateCheckStatus.Success,
                            latest = release,
                            error = null,
                        )
                        val updated = current.copy(app = updatedState)
                        if (promptWhenFound &&
                            updatedState.hasUpdate &&
                            channel !in promptedChannels
                        ) {
                            promptedChannels += channel
                            updated.enqueuePrompt(
                                UpdatePromptEntry(
                                    channel = channel,
                                    latest = release,
                                    currentVersion = updatedState.currentVersion,
                                )
                            )
                        } else {
                            updated
                        }
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            app = state.copy(
                                status = UpdateCheckStatus.Failed,
                                error = error.message ?: error.javaClass.simpleName,
                            )
                        )
                    }
                },
            )
        }
    }

    private fun UpdateUiState.enqueuePrompt(entry: UpdatePromptEntry): UpdateUiState {
        val existing = prompt?.entries.orEmpty().filterNot { it.channel == entry.channel }
        return copy(prompt = UpdatePrompt(existing + entry))
    }

    private fun startOfTomorrow(): Long = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private companion object {
        /** 自动检查的最小间隔：切回前台不至于每次都联网。 */
        const val AUTO_CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
    }
}
