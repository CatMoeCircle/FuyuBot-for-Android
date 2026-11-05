package com.CatMoeCircle.fuyutd.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.CatMoeCircle.fuyutd.data.kernel.LogEntry
import com.CatMoeCircle.fuyutd.data.kernel.LogLevel
import com.CatMoeCircle.fuyutd.data.kernel.LogRepository
import com.CatMoeCircle.fuyutd.ui.screen.log.LogUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 日志页状态。
 *
 * 内核把日志追加写到 `filesDir/logs/fuyubot.log`，没有回调，所以这里定时轮询文件尾部。
 * 页面不可见时（[stopPolling]）停掉轮询，避免后台空转。
 */
class LogViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(LogUiState())
    val uiState: StateFlow<LogUiState> = _uiState.asStateFlow()

    private lateinit var appContext: Context
    private var pollJob: Job? = null

    fun bind(context: Context) {
        appContext = context.applicationContext
        reload()
        startPolling()
    }

    fun startPolling() {
        if (!::appContext.isInitialized) return
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                reload()
            }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    /** 重新读文件并重算过滤结果。 */
    fun reload() {
        if (!::appContext.isInitialized) return
        viewModelScope.launch {
            val (entries, fileExists) = withContext(Dispatchers.IO) {
                LogRepository.read(appContext) to LogRepository.logFile(appContext).isFile
            }
            // 去重 + 排序是 O(N·logN) 的字符串操作，日志最多上千条；
            // 放到默认线程池，避免它在主线程里拖慢滑动经过日志页时的首帧。
            val modules = withContext(Dispatchers.Default) {
                entries.mapNotNull { it.module.ifEmpty { null } }.distinct().sorted()
            }
            _uiState.update { current ->
                current.copy(
                    entries = entries,
                    availableModules = modules,
                    visibleEntries = filter(
                        entries = entries,
                        minLevel = current.minLevel,
                        modules = current.moduleFilter,
                        query = current.query,
                    ),
                    fileMissing = !fileExists,
                )
            }
        }
    }

    /** 清空日志文件。 */
    fun clear() {
        if (!::appContext.isInitialized) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { LogRepository.clear(appContext) }
            _uiState.update {
                it.copy(
                    entries = emptyList(),
                    visibleEntries = emptyList(),
                    availableModules = emptyList(),
                )
            }
        }
    }

    fun setMinLevel(level: LogLevel) {
        _uiState.update {
            it.copy(
                minLevel = level,
                visibleEntries = filter(it.entries, level, it.moduleFilter, it.query),
            )
        }
    }

    /** 勾选 / 取消一个模块；传空集合表示不限模块。 */
    fun setModuleFilter(modules: Set<String>) {
        _uiState.update {
            it.copy(
                moduleFilter = modules,
                visibleEntries = filter(it.entries, it.minLevel, modules, it.query),
            )
        }
    }

    fun setQuery(query: String) {
        _uiState.update {
            it.copy(
                query = query,
                visibleEntries = filter(it.entries, it.minLevel, it.moduleFilter, query),
            )
        }
    }

    private fun filter(
        entries: List<LogEntry>,
        minLevel: LogLevel,
        modules: Set<String>,
        query: String,
    ): List<LogEntry> {
        val trimmed = query.trim()
        return entries.filter { entry ->
            entry.level.severity >= minLevel.severity &&
                    (modules.isEmpty() || entry.module in modules) &&
                    (trimmed.isEmpty() || entry.matches(trimmed))
        }
    }

    private fun LogEntry.matches(query: String): Boolean =
        message.contains(query, ignoreCase = true) ||
                module.contains(query, ignoreCase = true)

    override fun onCleared() {
        stopPolling()
        super.onCleared()
    }

    private companion object {
        /** 轮询间隔：日志页只是给人看，1 秒足够，也不会明显耗电。 */
        const val POLL_INTERVAL_MS = 1_000L
    }
}
