package com.CatMoeCircle.fuyutd.ui.screen.log

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.CatMoeCircle.fuyutd.data.kernel.LogLevel
import com.CatMoeCircle.fuyutd.ui.LocalUiMode
import com.CatMoeCircle.fuyutd.ui.UiMode
import com.CatMoeCircle.fuyutd.ui.viewmodel.LogViewModel

/** 日志页的动作集合，两个风格共用。 */
class LogScreenActions(
    val onQueryChange: (String) -> Unit,
    val onSetMinLevel: (LogLevel) -> Unit,
    val onToggleModule: (String) -> Unit,
    val onClearModules: () -> Unit,
    val onClear: () -> Unit,
)

@Composable
fun LogPage(bottomInnerPadding: Dp) {
    val context = LocalContext.current
    val viewModel = viewModel<LogViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.bind(context)
    }
    // 切到别的 tab 后停掉轮询，回来时恢复。
    DisposableEffect(Unit) {
        onDispose { viewModel.stopPolling() }
    }

    val actions = LogScreenActions(
        onQueryChange = viewModel::setQuery,
        onSetMinLevel = viewModel::setMinLevel,
        onToggleModule = { module ->
            val next = uiState.moduleFilter.toMutableSet()
            if (!next.add(module)) next.remove(module)
            viewModel.setModuleFilter(next)
        },
        onClearModules = { viewModel.setModuleFilter(emptySet()) },
        onClear = viewModel::clear,
    )

    when (LocalUiMode.current) {
        UiMode.Miuix -> LogPageMiuix(uiState, actions, bottomInnerPadding)
        UiMode.Material -> LogPageMaterial(uiState, actions, bottomInnerPadding)
    }
}
