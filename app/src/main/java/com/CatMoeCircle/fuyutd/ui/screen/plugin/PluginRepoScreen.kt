package com.CatMoeCircle.fuyutd.ui.screen.plugin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.CatMoeCircle.fuyutd.ui.LocalUiMode
import com.CatMoeCircle.fuyutd.ui.UiMode
import com.CatMoeCircle.fuyutd.ui.navigation3.LocalNavigator
import com.CatMoeCircle.fuyutd.ui.viewmodel.PluginViewModel

/**
 * 插件仓库页：商城插件（点击展开详情 + 安装），由插件页顶栏的下载图标进入。
 *
 * 与 [PluginPage] 共用同一个 [PluginViewModel]，因此已安装状态与安装进度保持一致。
 */
@Composable
fun PluginRepoScreen(
    isRunning: Boolean,
) {
    val context = LocalContext.current
    val viewModel = viewModel<PluginViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current

    LaunchedEffect(Unit) {
        viewModel.bind(context, kernelRunning = isRunning)
    }
    LaunchedEffect(isRunning) {
        viewModel.setKernelRunning(isRunning)
    }

    val actions = PluginScreenActions(
        onQueryChange = viewModel::setQuery,
        onRefresh = viewModel::reload,
        onReboot = {},
        onOpenRepo = {},
        onToggleEnabled = viewModel::setEnabled,
        onUninstall = viewModel::uninstall,
        onUndoUninstall = viewModel::undoUninstall,
        onToggleExpanded = viewModel::toggleExpanded,
        onInstall = viewModel::install,
        onToggleRepoOnlySupported = viewModel::toggleRepoOnlySupported,
        onToggleRepoSortByName = viewModel::toggleRepoSortByName,
        onOpenDetail = viewModel::openDetail,
        onDismissDetail = viewModel::dismissDetail,
    )
    val onBack = { navigator.pop() }

    when (LocalUiMode.current) {
        UiMode.Miuix -> PluginRepoScreenMiuix(uiState, actions, onBack)
        UiMode.Material -> PluginRepoScreenMaterial(uiState, actions, onBack)
    }

    val detailItem = uiState.detailItem
    if (detailItem != null) {
        PluginRepoDetailDialog(
            item = detailItem,
            markdown = uiState.detailMarkdown,
            installed = detailItem.installed,
            onDismiss = actions.onDismissDetail,
            onInstall = {
                actions.onInstall(detailItem)
                actions.onDismissDetail()
            },
            onUninstall = {
                detailItem.installed?.let { actions.onUninstall(it) }
                actions.onDismissDetail()
            },
        )
    }
}
