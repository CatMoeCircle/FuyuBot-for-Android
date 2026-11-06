package com.CatMoeCircle.fuyutd.ui.viewmodel

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 全局唯一的插件 ViewModel（插件页 + 插件仓库页共用）。
 *
 * NavDisplay 会为每个导航条目单独创建一份 ViewModelStore，因此插件页与插件仓库页各自
 * 调用 `viewModel<PluginViewModel>()` 拿到的是**两个独立**的实例：在仓库页安装完插件，
 * 只会更新仓库页那份状态，退回插件页时已安装列表仍是旧的，必须手动刷新才更新。
 * 所以统一由 MainActivity 创建并通过本 CompositionLocal 下发，两个页面共用同一实例。
 */
val LocalPluginViewModel = staticCompositionLocalOf<PluginViewModel> {
    error("LocalPluginViewModel not provided")
}
