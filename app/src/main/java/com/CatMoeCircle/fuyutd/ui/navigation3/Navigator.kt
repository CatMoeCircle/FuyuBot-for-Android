package com.CatMoeCircle.fuyutd.ui.navigation3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.staticCompositionLocalOf
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.navBackStackOf

/**
 * 简单导航封装：push / pop / replace，底层是 miuix-nav 的 [NavBackStack]。
 *
 * 用 [navBackStackOf] 而不是 `rememberNavBackStack`：后者要求路由类型实现 kotlinx.serialization。
 * 导航栈改用 [NavBackStackSaver] 存成字符串列表，这样 Activity 重建（例如主题设置里切换
 * 预测性返回时的 `Activity.recreate()`）不会把导航栈重置回主界面。
 */
class Navigator(
    val backStack: NavBackStack,
) {

    fun push(key: NavKey) {
        if (key !in backStack) {
            backStack.add(key)
        }
    }

    fun replace(key: NavKey) {
        if (backStack.isNotEmpty()) {
            backStack[backStack.lastIndex] = key
        } else {
            backStack.add(key)
        }
    }

    fun pop() {
        if (backStack.size > 1) {
            backStack.removeLastOrNull()
        }
    }

    fun current(): NavKey? = backStack.lastOrNull()

    fun backStackSize(): Int = backStack.size
}

private const val PLUGIN_VERSIONS_PREFIX = "pluginVersions:"

private fun Route.encode(): String = when (this) {
    Route.Main -> "main"
    Route.ColorPalette -> "colorPalette"
    Route.TomlConfig -> "tomlConfig"
    Route.UpdateCheck -> "updateCheck"
    Route.PluginRepo -> "pluginRepo"
    is Route.PluginVersions -> PLUGIN_VERSIONS_PREFIX + pluginId
}

private fun decodeRoute(encoded: String): Route? = when {
    encoded == "main" -> Route.Main
    encoded == "colorPalette" -> Route.ColorPalette
    encoded == "tomlConfig" -> Route.TomlConfig
    encoded == "updateCheck" -> Route.UpdateCheck
    encoded == "pluginRepo" -> Route.PluginRepo
    encoded.startsWith(PLUGIN_VERSIONS_PREFIX) ->
        Route.PluginVersions(encoded.removePrefix(PLUGIN_VERSIONS_PREFIX))
    else -> null
}

/**
 * 把导航栈存成字符串列表：路由类不必实现 @Serializable 也能随 Activity 的 saveable 状态恢复。
 */
private val NavBackStackSaver: Saver<NavBackStack, List<String>> = Saver(
    save = { stack -> stack.mapNotNull { (it as? Route)?.encode() } },
    restore = { encoded ->
        val routes = encoded.mapNotNull(::decodeRoute).ifEmpty { listOf(Route.Main) }
        navBackStackOf().apply { addAll(routes) }
    },
)

@Composable
fun rememberNavigator(startRoute: Route): Navigator {
    val backStack = rememberSaveable(saver = NavBackStackSaver) { navBackStackOf(startRoute) }
    return remember(backStack) { Navigator(backStack) }
}

val LocalNavigator = staticCompositionLocalOf<Navigator> {
    error("LocalNavigator not provided")
}
