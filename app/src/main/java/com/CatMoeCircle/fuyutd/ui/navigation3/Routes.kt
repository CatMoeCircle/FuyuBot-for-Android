package com.CatMoeCircle.fuyutd.ui.navigation3

import top.yukonga.miuix.kmp.nav.core.NavKey

/**
 * 应用内的页面标识。主界面（底部栏 + 分页）之外的页面都是压栈显示的。
 *
 * 用 [navBackStackOf] 构建导航栈，因此不需要 `@Serializable`。
 */
sealed interface Route : NavKey {
    data object Main : Route
    data object ColorPalette : Route
    data object TomlConfig : Route
    data object UpdateCheck : Route
    data object PluginRepo : Route

    /** 商城插件的「所有版本」页；[pluginId] 是索引里的插件 id。 */
    data class PluginVersions(val pluginId: String) : Route
}
