package com.CatMoeCircle.fuyutd.ui.screen.plugin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.CatMoeCircle.fuyutd.data.plugin.PluginVersion
import com.CatMoeCircle.fuyutd.ui.LocalUiMode
import com.CatMoeCircle.fuyutd.ui.UiMode
import com.CatMoeCircle.fuyutd.ui.navigation3.LocalNavigator
import com.CatMoeCircle.fuyutd.ui.viewmodel.LocalPluginViewModel
import java.util.Locale

/**
 * 「所有版本」页：商城插件某个插件的历史版本表。
 *
 * 新版索引（schema 2）是轻索引，版本表在 `plugins/<id>.json` 分片里，
 * 进入本页时才拉；列表按「新 → 旧」展示，标出最新版、已装版本、已下架，
 * 以及 ABI 高于内核 / 没有本平台包的不可用版本。安装按钮只对可装版本出现。
 */
@Composable
fun PluginVersionsScreen(pluginId: String) {
    val viewModel = LocalPluginViewModel.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current

    LaunchedEffect(pluginId) {
        viewModel.loadVersions(pluginId)
    }

    val actions = PluginVersionsActions(
        onBack = { navigator.pop() },
        onInstall = viewModel::installVersion,
    )

    when (LocalUiMode.current) {
        UiMode.Miuix -> PluginVersionsMiuix(uiState, actions)
        UiMode.Material -> PluginVersionsMaterial(uiState, actions)
    }
}

/**
 * 版本页的动作集合，两套主题共用。
 */
data class PluginVersionsActions(
    val onBack: () -> Unit,
    val onInstall: (PluginVersion) -> Unit,
)

/** 资产大小的人读格式。 */
internal fun formatPluginSize(size: Long): String = when {
    size >= 1024L * 1024L -> String.format(Locale.ROOT, "%.1f MB", size / (1024f * 1024f))
    size >= 1024L -> String.format(Locale.ROOT, "%.0f KB", size / 1024f)
    else -> "$size B"
}
