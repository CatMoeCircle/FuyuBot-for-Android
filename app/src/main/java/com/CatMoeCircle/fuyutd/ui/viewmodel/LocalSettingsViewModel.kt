package com.CatMoeCircle.fuyutd.ui.viewmodel

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 全局唯一的设置 ViewModel。
 *
 * NavDisplay 会为每个导航条目单独创建一份 ViewModelStore，因此页面内直接调用
 * `viewModel<SettingsViewModel>()` 拿到的是**各自独立**的实例：主题页改了设置，
 * 只更新它自己那份 StateFlow，根部的 [com.CatMoeCircle.fuyutd.MainActivity] 收不到，
 * 表现为主题 / 底栏 / 模糊等开关「不生效」。
 *
 * 所以统一由 MainActivity 创建并通过本 CompositionLocal 下发，所有页面共用同一实例。
 */
val LocalSettingsViewModel = staticCompositionLocalOf<SettingsViewModel> {
    error("LocalSettingsViewModel not provided")
}
