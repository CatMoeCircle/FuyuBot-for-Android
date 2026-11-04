package com.CatMoeCircle.fuyutd.ui.navigation3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.navBackStackOf

/**
 * 简单导航封装：push / pop / replace，底层是 miuix-nav 的 [NavBackStack]。
 *
 * 用 [navBackStackOf] 而不是 `rememberNavBackStack`：前者只建内存栈，不要求路由类型
 * 实现 kotlinx.serialization，本项目不需要跨进程恢复导航栈。
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

@Composable
fun rememberNavigator(startRoute: Route): Navigator {
    val backStack = remember { navBackStackOf(startRoute) }
    return remember(backStack) { Navigator(backStack) }
}

val LocalNavigator = staticCompositionLocalOf<Navigator> {
    error("LocalNavigator not provided")
}
