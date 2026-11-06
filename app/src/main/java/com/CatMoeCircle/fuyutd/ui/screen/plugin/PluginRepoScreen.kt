package com.CatMoeCircle.fuyutd.ui.screen.plugin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.CatMoeCircle.fuyutd.ui.LocalUiMode
import com.CatMoeCircle.fuyutd.ui.UiMode
import com.CatMoeCircle.fuyutd.ui.navigation3.LocalNavigator
import com.CatMoeCircle.fuyutd.ui.viewmodel.LocalPluginViewModel
import com.CatMoeCircle.fuyutd.ui.viewmodel.PluginViewModel

/**
 * 插件仓库页：商城插件（点击展开详情 + 安装），由插件页顶栏的下载图标进入。
 *
 * 与 [PluginPage] 共用 MainActivity 下发的同一个 [PluginViewModel]，
 * 因此已安装状态与安装进度保持一致。
 */
@Composable
fun PluginRepoScreen(
    isRunning: Boolean,
) {
    val context = LocalContext.current
    val viewModel = LocalPluginViewModel.current
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
        onRequestConfirmUninstall = viewModel::requestUninstallConfirm,
        onRestoreUninstall = viewModel::restoreUninstall,
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
                detailItem.installed?.let { plugin ->
                    actions.onUninstall(plugin)
                    // 停止时没有卡片可点「确认卸载」，直接接着弹确认窗（等价于划掉后再确认）。
                    if (!isRunning) viewModel.requestUninstallConfirm()
                }
                actions.onDismissDetail()
            },
        )
    }

    // 内核停止时删除不可恢复：先划掉，再点「确认卸载」弹窗确认；运行中删除走「待卸载 + 撤销」。
    PluginUninstallDialog(
        pending = uiState.uninstallPending,
        visible = uiState.uninstallDialogVisible,
        onDismiss = viewModel::dismissUninstallDialog,
        onConfirm = viewModel::confirmUninstall,
    )
}
