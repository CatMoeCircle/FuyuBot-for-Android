package com.CatMoeCircle.fuyutd

import android.Manifest
import android.os.Bundle
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults.flingBehavior
import androidx.compose.foundation.pager.PagerDefaults.pageNestedScrollConnection
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.CatMoeCircle.fuyutd.data.AppPrefs
import com.CatMoeCircle.fuyutd.ui.LocalUiMode
import com.CatMoeCircle.fuyutd.ui.UiMode
import com.CatMoeCircle.fuyutd.ui.component.bottombar.BottomBar
import com.CatMoeCircle.fuyutd.ui.component.bottombar.LocalMainPagerState
import com.CatMoeCircle.fuyutd.ui.component.bottombar.MainPagerState
import com.CatMoeCircle.fuyutd.ui.component.bottombar.SideRail
import com.CatMoeCircle.fuyutd.ui.component.bottombar.rememberMainPagerState
import com.CatMoeCircle.fuyutd.ui.component.bottombar.useNavigationRail
import com.CatMoeCircle.fuyutd.ui.navigation3.LocalNavigator
import com.CatMoeCircle.fuyutd.ui.navigation3.Route
import com.CatMoeCircle.fuyutd.ui.navigation3.rememberNavigator
import com.CatMoeCircle.fuyutd.ui.component.fuyubot.FuyuBotAskDialog
import com.CatMoeCircle.fuyutd.ui.component.fuyubot.FuyuBotConfigDialog
import com.CatMoeCircle.fuyutd.ui.component.fuyubot.FuyuBotErrorDialog
import com.CatMoeCircle.fuyutd.ui.screen.colorpalette.ColorPaletteScreen
import com.CatMoeCircle.fuyutd.ui.screen.home.HomeScreen
import com.CatMoeCircle.fuyutd.ui.screen.home.HomeUiState
import com.CatMoeCircle.fuyutd.ui.screen.log.LogPage
import com.CatMoeCircle.fuyutd.ui.screen.plugin.PluginPage
import com.CatMoeCircle.fuyutd.ui.screen.plugin.PluginRepoScreen
import com.CatMoeCircle.fuyutd.ui.screen.settings.SettingPager
import com.CatMoeCircle.fuyutd.ui.screen.config.TomlConfigScreen
import com.CatMoeCircle.fuyutd.ui.screen.update.UpdateCheckScreen
import com.CatMoeCircle.fuyutd.ui.screen.update.UpdatePromptDialog
import com.CatMoeCircle.fuyutd.ui.theme.FuyuTDTheme
import com.CatMoeCircle.fuyutd.ui.theme.LocalColorMode
import com.CatMoeCircle.fuyutd.ui.theme.LocalEnableBlur
import com.CatMoeCircle.fuyutd.ui.theme.LocalEnableFloatingBottomBar
import com.CatMoeCircle.fuyutd.ui.theme.LocalEnableFloatingBottomBarBlur
import com.CatMoeCircle.fuyutd.ui.theme.ThemeController
import com.CatMoeCircle.fuyutd.ui.util.rememberBlurBackdrop
import com.CatMoeCircle.fuyutd.ui.viewmodel.HomeViewModel
import com.CatMoeCircle.fuyutd.ui.viewmodel.LocalPluginViewModel
import com.CatMoeCircle.fuyutd.ui.viewmodel.LocalSettingsViewModel
import com.CatMoeCircle.fuyutd.ui.viewmodel.PluginViewModel
import com.CatMoeCircle.fuyutd.ui.viewmodel.SettingsViewModel
import com.CatMoeCircle.fuyutd.ui.viewmodel.UpdateViewModel
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PagerGestureNestedScrollConnection
import top.yukonga.miuix.kmp.utils.PagerInterceptionMode
import top.yukonga.miuix.kmp.utils.PagerNavigationSpringSpec
import top.yukonga.miuix.kmp.utils.pagerGestureOverride

private const val PAGE_COUNT = 4

class MainActivity : ComponentActivity() {

    /** 通知权限申请结果：拒绝也不影响内核运行，只是通知栏看不到常驻通知。 */
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppPrefs.init(this)
        requestNotificationPermissionIfNeeded()
        enableEdgeToEdge()
        setContent {
            val settingsViewModel = viewModel<SettingsViewModel>()
            val uiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
            val appSettings = remember(
                uiState.uiMode,
                uiState.themeMode,
                uiState.keyColor,
                uiState.colorStyle,
                uiState.colorSpec,
                uiState.miuixMonet,
            ) { ThemeController.getAppSettings() }
            val uiMode = UiMode.fromValue(uiState.uiMode)
            val darkMode = appSettings.colorMode.isDark ||
                    (appSettings.colorMode.isSystem && isSystemInDarkTheme())

            DisposableEffect(darkMode) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT
                    ) { darkMode },
                    navigationBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT
                    ) { darkMode },
                )
                window.isNavigationBarContrastEnforced = false
                onDispose { }
            }

            val navigator = rememberNavigator(Route.Main)
            val updateViewModel = viewModel<UpdateViewModel>()
            val updateState by updateViewModel.uiState.collectAsStateWithLifecycle()
            val homeViewModel = viewModel<HomeViewModel>()
            val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
            // 插件页与插件仓库页是两条导航条目，各自 viewModel() 会拿到不同实例；
            // 统一在这里创建，保证在仓库页安装后插件页能立刻看到最新的已安装列表。
            val pluginViewModel = viewModel<PluginViewModel>()
            val configDialog by homeViewModel.configDialog.collectAsStateWithLifecycle()
            val homeError by homeViewModel.error.collectAsStateWithLifecycle()
            val homeAsk by homeViewModel.ask.collectAsStateWithLifecycle()
            val context = LocalContext.current

            // 自动检查：受「自动检查更新」开关与「今天不再提醒」的暂缓时间约束，
            // 首次进入与每次回到前台都尝试一次（内部有 6 小时间隔节流）。
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                updateViewModel.bind(context)
                updateViewModel.autoCheckIfNeeded()
                // 内核可能在别的页面被安装/更新，回到前台时重读插件数量。
                homeViewModel.bind(context)
            }
            val systemDensity = LocalDensity.current
            val scaledDensity = remember(systemDensity, uiState.pageScale) {
                Density(systemDensity.density * uiState.pageScale, systemDensity.fontScale)
            }

            CompositionLocalProvider(
                LocalNavigator provides navigator,
                LocalDensity provides scaledDensity,
                LocalSettingsViewModel provides settingsViewModel,
                LocalPluginViewModel provides pluginViewModel,
                LocalColorMode provides appSettings.colorMode.value,
                LocalEnableBlur provides uiState.enableBlur,
                LocalEnableFloatingBottomBar provides uiState.enableFloatingBottomBar,
                LocalEnableFloatingBottomBarBlur provides uiState.enableFloatingBottomBarBlur,
                LocalUiMode provides uiMode,
            ) {
                // 主分页状态必须放在 FuyuTDTheme 之外：FuyuTDTheme 内部按 uiMode 在
                // MiuixTheme / MaterialTheme 之间切换，其 content 子组合会被整体销毁重建，
                // 只有这个稳定的 CompositionLocalProvider 子树里的 remember 才能跨风格切换保留。
                val mainPagerState = rememberMainPagerState(
                    pagerState = rememberPagerState(initialPage = 0, pageCount = { PAGE_COUNT }),
                    animatePageChanges = !useNavigationRail(LocalEnableFloatingBottomBar.current),
                )

                FuyuTDTheme(appSettings = appSettings, uiMode = uiMode) {
                    val swipeDismiss = if (uiState.enableSwipeDismiss) {
                        if (LocalLayoutDirection.current == LayoutDirection.Rtl) {
                            NavSwipeDirection.RightToLeft
                        } else {
                            NavSwipeDirection.LeftToRight
                        }
                    } else {
                        NavSwipeDirection.None
                    }

                    val navDisplay = @Composable {
                        NavDisplay(
                            backStack = navigator.backStack,
                            effects = NavDisplayEffects(
                                cornerClipRadius = rememberNavSystemCornerRadius(),
                            ),
                            onBack = { navigator.pop() },
                        ) {
                            entry<Route.Main>(swipeDismiss = swipeDismiss) {
                                MainScreen(
                                    mainPagerState = mainPagerState,
                                    pagerInterceptionMode = uiState.pagerInterceptionMode,
                                    homeState = homeState,
                                    onHomeStart = { homeViewModel.onStart(context) },
                                    onHomeStop = { homeViewModel.stop() },
                                    onHomeReboot = { homeViewModel.restartKernel() },
                                    onHomeVisible = { homeViewModel.refreshPluginCount() },
                                )
                            }
                            entry<Route.ColorPalette>(swipeDismiss = swipeDismiss) {
                                ColorPaletteScreen()
                            }
                            entry<Route.TomlConfig>(swipeDismiss = swipeDismiss) {
                                TomlConfigScreen(
                                    kernelRunning = homeState.isRunning,
                                    onRestartKernel = homeViewModel::restartKernel,
                                )
                            }
                            entry<Route.UpdateCheck>(swipeDismiss = swipeDismiss) {
                                UpdateCheckScreen()
                            }
                            entry<Route.PluginRepo>(swipeDismiss = swipeDismiss) {
                                PluginRepoScreen(isRunning = homeState.isRunning)
                            }
                        }
                    }

                    // miuix 的 OverlayDialog 依赖根 Scaffold 提供的 popupHost，
                    // 因此弹窗必须放在 Scaffold 内容里才能渲染出来。
                    val updatePrompt = @Composable {
                        UpdatePromptDialog(
                            prompt = updateState.prompt,
                            onDismiss = updateViewModel::dismissPrompt,
                            onSnoozeToday = updateViewModel::snoozeToday,
                            onOpenUrl = { url ->
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            },
                        )
                    }

                    // 内核配置 / 登录提问 / 错误弹窗：跟随首页启动流程。
                    val botDialogs = @Composable {
                        FuyuBotConfigDialog(
                            show = configDialog,
                            onConfirm = { id, hash, token ->
                                homeViewModel.saveConfig(context, id, hash, token)
                            },
                            onDismiss = homeViewModel::dismissConfigDialog,
                        )
                        FuyuBotAskDialog(
                            ask = homeAsk,
                            onAnswer = { id, text -> homeViewModel.answer(id, text) },
                        )
                        FuyuBotErrorDialog(
                            error = homeError,
                            onDismiss = homeViewModel::dismissError,
                        )
                    }

                    when (uiMode) {
                        UiMode.Material -> androidx.compose.material3.Scaffold(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ) {
                            navDisplay()
                            updatePrompt()
                            botDialogs()
                        }

                        UiMode.Miuix -> Scaffold {
                            navDisplay()
                            updatePrompt()
                            botDialogs()
                        }
                    }
                }
            }
        }
    }

    /**
     * Android 13+ 的通知权限：内核前台服务的常驻通知需要它。
     *
     * 只在**第一次启动**时申请一次（结果记在设置里），被拒后不再反复弹窗——用户仍可在
     * 系统设置里打开；拒绝本身不影响内核运行，只是通知栏看不到常驻通知。
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) return
        val prefs = AppPrefs.prefs()
        if (prefs.getBoolean(KEY_NOTIFICATION_PERMISSION_ASKED, false)) return
        prefs.edit { putBoolean(KEY_NOTIFICATION_PERMISSION_ASKED, true) }
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private companion object {
        /** 「已经弹过一次通知权限申请」的标记，避免每次启动都打扰用户。 */
        const val KEY_NOTIFICATION_PERMISSION_ASKED = "notification_permission_asked"
    }
}

@Composable
fun MainScreen(
    mainPagerState: MainPagerState,
    pagerInterceptionMode: Int = PagerInterceptionMode.CrossAxisInterceptor.ordinal,
    homeState: HomeUiState = HomeUiState(),
    onHomeStart: () -> Unit = {},
    onHomeStop: () -> Unit = {},
    onHomeReboot: () -> Unit = {},
    onHomeVisible: () -> Unit = {},
) {
    val enableFloatingBottomBar = LocalEnableFloatingBottomBar.current
    val enableFloatingBottomBarBlur = LocalEnableFloatingBottomBarBlur.current
    val useNavigationRail = useNavigationRail(enableFloatingBottomBar)
    val pagerMode = PagerInterceptionMode.entries.getOrElse(pagerInterceptionMode) {
        PagerInterceptionMode.Native
    }
    val interceptPagerGestures = pagerMode == PagerInterceptionMode.CrossAxisInterceptor
    val uiMode = LocalUiMode.current

    val surfaceColor = when (uiMode) {
        UiMode.Material -> MaterialTheme.colorScheme.surface
        UiMode.Miuix -> MiuixTheme.colorScheme.surface
    }
    val enableBlur = LocalEnableBlur.current
    val blurBackdrop = rememberBlurBackdrop(enableBlur)
    val backdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }

    val currentPage = mainPagerState.pagerState.currentPage
    LaunchedEffect(currentPage) {
        mainPagerState.syncPage()
        // 回到首页（第 0 页）时重读插件数量，反映在插件页增删 / 启停后的最新值。
        if (currentPage == 0) onHomeVisible()
    }

    // 主页内按返回：先回到第 1 页，再交给系统退出。
    val navigator = LocalNavigator.current
    val isPagerBackEnabled by remember {
        derivedStateOf {
            navigator.current() == Route.Main &&
                    navigator.backStackSize() == 1 &&
                    mainPagerState.selectedPage != 0
        }
    }
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = isPagerBackEnabled,
        onBackCompleted = { mainPagerState.animateToPage(0) },
    )

    CompositionLocalProvider(LocalMainPagerState provides mainPagerState) {
        val pagerContent = @Composable { bottomInnerPadding: Dp ->
            Box(
                modifier = if (blurBackdrop != null) {
                    Modifier.layerBackdrop(blurBackdrop)
                } else {
                    Modifier
                }
            ) {
                HorizontalPager(
                    modifier = Modifier
                        .pagerGestureOverride(
                            pagerState = mainPagerState.pagerState,
                            mode = pagerMode,
                            enabled = true,
                        )
                        .then(
                            if (enableFloatingBottomBar && enableFloatingBottomBarBlur) {
                                Modifier.layerBackdrop(backdrop)
                            } else {
                                Modifier
                            }
                        ),
                    state = mainPagerState.pagerState,
                    overscrollEffect = null,
                    userScrollEnabled = !interceptPagerGestures,
                    pageNestedScrollConnection = if (interceptPagerGestures) {
                        PagerGestureNestedScrollConnection
                    } else {
                        pageNestedScrollConnection(
                            state = mainPagerState.pagerState,
                            orientation = androidx.compose.foundation.gestures.Orientation.Horizontal,
                        )
                    },
                    flingBehavior = flingBehavior(
                        state = mainPagerState.pagerState,
                        snapAnimationSpec = PagerNavigationSpringSpec,
                    ),
                ) { page ->
                    when (page) {
                        0 -> HomeScreen(
                            state = homeState,
                            onStart = onHomeStart,
                            onStop = onHomeStop,
                            bottomInnerPadding = bottomInnerPadding,
                        )
                        1 -> LogPage(bottomInnerPadding)
                        2 -> PluginPage(
                            isRunning = homeState.isRunning,
                            bottomInnerPadding = bottomInnerPadding,
                            onRebootKernel = onHomeReboot,
                        )
                        3 -> SettingPager(bottomInnerPadding)
                    }
                }
            }
        }

        if (useNavigationRail) {
            val startInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
                .only(WindowInsetsSides.Start)
            val navBarBottomPadding =
                WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()

            when (uiMode) {
                UiMode.Material -> androidx.compose.material3.Scaffold(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Row {
                        SideRail()
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .consumeWindowInsets(startInsets)
                        ) {
                            pagerContent(navBarBottomPadding)
                        }
                    }
                }

                UiMode.Miuix -> Scaffold {
                    Row {
                        SideRail()
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .consumeWindowInsets(startInsets)
                        ) {
                            pagerContent(navBarBottomPadding)
                        }
                    }
                }
            }
        } else {
            val bottomBar = @Composable {
                Box(modifier = Modifier.fillMaxWidth()) {
                    BottomBar(
                        blurBackdrop = blurBackdrop,
                        backdrop = backdrop,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }

            when (uiMode) {
                UiMode.Material -> androidx.compose.material3.Scaffold(
                    bottomBar = bottomBar,
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ) { innerPadding ->
                    pagerContent(innerPadding.calculateBottomPadding())
                }

                UiMode.Miuix -> Scaffold(bottomBar = bottomBar) { innerPadding ->
                    pagerContent(innerPadding.calculateBottomPadding())
                }
            }
        }
    }
}
