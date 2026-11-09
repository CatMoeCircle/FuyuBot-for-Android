package com.CatMoeCircle.fuyutd.ui.screen.log

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.CatMoeCircle.fuyutd.R
import com.CatMoeCircle.fuyutd.data.kernel.LogLevel
import androidx.compose.material3.MaterialTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 「更多」弹窗内部的层级：在小窗口里就地切换，不跳页面。 */
enum class LogMorePage { Root, Level, RuntimeLevel, Module }

/** 「日志级别」页里可选的最小级别；`TRACE` 表示不过滤。 */
val LogLevels: List<LogLevel> = listOf(
    LogLevel.TRACE,
    LogLevel.DEBUG,
    LogLevel.INFO,
    LogLevel.WARN,
    LogLevel.ERROR,
)

/** 运行期插件日志级别可选项；内核对大小写不敏感，写小写即可。 */
val RuntimeLogLevels: List<LogLevel> = listOf(
    LogLevel.ERROR,
    LogLevel.WARN,
    LogLevel.INFO,
    LogLevel.DEBUG,
    LogLevel.TRACE,
)

@Composable
fun LogLevel.label(): String = stringResource(
    when (this) {
        LogLevel.TRACE, LogLevel.UNKNOWN -> R.string.log_level_trace
        LogLevel.DEBUG -> R.string.log_level_debug
        LogLevel.INFO -> R.string.log_level_info
        LogLevel.WARN -> R.string.log_level_warn
        LogLevel.ERROR -> R.string.log_level_error
    }
)

/** 运行期级别用的名字：这里 `TRACE` 是「跟踪」，不是「全部」。 */
@Composable
fun LogLevel.runtimeLabel(): String = stringResource(
    when (this) {
        LogLevel.TRACE, LogLevel.UNKNOWN -> R.string.log_level_trace_name
        LogLevel.DEBUG -> R.string.log_level_debug
        LogLevel.INFO -> R.string.log_level_info
        LogLevel.WARN -> R.string.log_level_warn
        LogLevel.ERROR -> R.string.log_level_error
    }
)

/** 级别在 Miuix 主题下的强调色。 */
@Composable
fun LogLevel.miuixColor(): Color = when (this) {
    LogLevel.ERROR -> MiuixTheme.colorScheme.error
    LogLevel.WARN -> MiuixTheme.colorScheme.primary
    LogLevel.INFO -> MiuixTheme.colorScheme.onSurface
    LogLevel.DEBUG, LogLevel.TRACE, LogLevel.UNKNOWN -> MiuixTheme.colorScheme.onSurfaceVariantSummary
}

/** 级别在 Material 主题下的强调色。 */
@Composable
fun LogLevel.materialColor(): Color = when (this) {
    LogLevel.ERROR -> MaterialTheme.colorScheme.error
    LogLevel.WARN -> MaterialTheme.colorScheme.primary
    LogLevel.INFO -> MaterialTheme.colorScheme.onSurface
    LogLevel.DEBUG, LogLevel.TRACE, LogLevel.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
}
