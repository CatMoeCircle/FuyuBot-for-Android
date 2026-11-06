package com.CatMoeCircle.fuyutd.ui.screen.plugin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.CatMoeCircle.fuyutd.ui.LocalUiMode
import com.CatMoeCircle.fuyutd.ui.UiMode
import com.CatMoeCircle.fuyutd.ui.navigation3.LocalNavigator
import com.CatMoeCircle.fuyutd.ui.navigation3.Route
import com.CatMoeCircle.fuyutd.ui.viewmodel.LocalPluginViewModel

/**
 * 插件页：本地已安装插件（带启用开关 / 卸载 / 重启）。
 *
 * [isRunning] 表示内核是否在运行，决定增删 / 启停后是否需要「重启生效」标记；
 * [onRebootKernel] 由外部（HomeViewModel）提供，点击顶部「重启内核」时调用。
 * 顶栏左侧的下载图标跳转到独立的「插件仓库」页（[PluginRepoScreen]）。
 */
@Composable
fun PluginPage(
    isRunning: Boolean,
    bottomInnerPadding: Dp,
    onRebootKernel: () -> Unit = {},
) {
    val context = LocalContext.current
    val viewModel = LocalPluginViewModel.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current

    LaunchedEffect(Unit) {
        viewModel.bind(context, isRunning)
    }
    LaunchedEffect(isRunning) {
        viewModel.setKernelRunning(isRunning)
    }

    val actions = PluginScreenActions(
        onQueryChange = viewModel::setQuery,
        onRefresh = viewModel::reload,
        onReboot = onRebootKernel,
        onOpenRepo = { navigator.push(Route.PluginRepo) },
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

    when (LocalUiMode.current) {
        UiMode.Miuix -> PluginPageMiuix(uiState, actions, bottomInnerPadding)
        UiMode.Material -> PluginPageMaterial(uiState, actions, bottomInnerPadding)
    }

    // 内核停止时删除不可恢复：先划掉，再点「确认卸载」弹窗确认；运行中删除走「待卸载 + 撤销」。
    PluginUninstallDialog(
        pending = uiState.uninstallPending,
        visible = uiState.uninstallDialogVisible,
        onDismiss = viewModel::dismissUninstallDialog,
        onConfirm = viewModel::confirmUninstall,
    )
}
