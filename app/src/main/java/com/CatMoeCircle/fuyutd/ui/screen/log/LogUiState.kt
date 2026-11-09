package com.CatMoeCircle.fuyutd.ui.screen.log

import com.CatMoeCircle.fuyutd.data.kernel.LogEntry
import com.CatMoeCircle.fuyutd.data.kernel.LogLevel

/**
 * 日志页状态。
 *
 * [entries] 是原始数据，[visibleEntries] 是按级别与关键词过滤后的结果，
 * 由 ViewModel 统一算好，界面直接消费即可。
 */
data class LogUiState(
    val entries: List<LogEntry> = emptyList(),
    val visibleEntries: List<LogEntry> = emptyList(),
    /** 只显示该级别及以上的日志。 */
    val minLevel: LogLevel = LogLevel.TRACE,
    /** 只显示这些模块的日志；为空表示不限模块。 */
    val moduleFilter: Set<String> = emptySet(),
    /** 当前日志里出现过的全部模块，供「过滤」页选择。 */
    val availableModules: List<String> = emptyList(),
    val query: String = "",
    /** 日志文件不存在时为 true（还没启动过内核）。 */
    val fileMissing: Boolean = false,
    /** 运行期插件日志级别（`.fuyubot/log-level`）；文件不存在 / 不可识别时为 null。 */
    val pluginLogLevel: LogLevel? = null,
)
