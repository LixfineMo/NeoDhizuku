package com.neodhizuku.more.ui.page.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

import com.neodhizuku.more.NeoDhizuku
import com.neodhizuku.more.NeoLog
import com.neodhizuku.more.server.DhizukuState
import com.neodhizuku.more.ui.component.FloatingNavBar
import com.neodhizuku.more.ui.component.NavDestination
import com.neodhizuku.more.ui.page.settings.activate.ActivatePage
import com.neodhizuku.more.ui.page.settings.app_management.AppManagementPage
import com.neodhizuku.more.ui.page.settings.blocklist.BlocklistPage
import com.neodhizuku.more.ui.page.settings.calllog.CallLogPage
import com.neodhizuku.more.ui.page.settings.home.HomePage
import com.neodhizuku.more.ui.page.settings.theme.ThemePage
// 容器页本身也叫 SettingsPage，与子页同名；用别名避免解析歧义
import com.neodhizuku.more.ui.page.settings.settings.SettingsPage as SettingsScreen

/** 应用主容器，承载底部导航的四个页面以及激活、主题等子页面。 */
@Composable
fun SettingsPage(windowInsets: WindowInsets) {
    val navController = rememberNavController()

    val useNavBar = NeoDhizuku.on(NeoDhizuku.UseFloatingNavBar)
    val isOwner = DhizukuState.state.isOwner

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showNavBar = useNavBar && NavDestination.entries.any { it.route == currentRoute }

    LaunchedEffect(currentRoute) {
        NeoLog.d(NeoLog.NAV) { "route -> $currentRoute, showNavBar=$showNavBar" }
    }

    // 直接监听 NavController 的实际跳转结果：
    // 若日志里只有 navigate 而没有 destination changed，说明导航被吞掉了
    DisposableEffect(navController) {
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            NeoLog.d(NeoLog.NAV) { "destination changed -> ${destination.route}" }
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }

    androidx.compose.material3.Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showNavBar) {
                // 撑满宽度，否则底栏只占内容宽度，两侧点不到
                Box(modifier = Modifier.fillMaxWidth()) {
                    FloatingNavBar(
                        isOwner = isOwner,
                        currentRoute = currentRoute,
                        onNavigate = { route ->
                            NeoLog.d(NeoLog.NAV) { "navigate -> $route (from=$currentRoute)" }
                            runCatching {
                                navController.navigate(route) {
                                    // 用 route 字符串而非 graph.startDestinationId，
                                    // 避免 id 解析差异导致弹出范围不对
                                    popUpTo(SettingsRoute.Home.route) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    // 不启用 restoreState：它与 popUpTo(saveState) 组合时
                                    // 会把刚弹出的回退栈片段恢复回来，
                                    // 结果是 navigate 后 destination 停在上一页
                                }
                            }.onFailure {
                                NeoLog.e(NeoLog.NAV, { "navigate failed: $route" }, it)
                            }
                            // 导航后立刻回读实际栈，便于确认是否真的切过去了
                            NeoLog.d(NeoLog.NAV) {
                                "backStack -> " +
                                    navController.currentBackStack.value
                                        .joinToString(" > ") { it.destination.route.toString() }
                            }
                        }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = SettingsRoute.Home.route,
            // 让出底栏与系统栏的高度，否则页面铺满全屏会盖住底栏
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            composable(route = SettingsRoute.Home.route) {
                HomePage(
                    windowInsets = windowInsets,
                    navController = navController
                )
            }
            composable(route = SettingsRoute.AppManagement.route) {
                AppManagementPage(
                    windowInsets = windowInsets,
                    navController = navController
                )
            }
            composable(route = SettingsRoute.Blocklist.route) {
                BlocklistPage(
                    windowInsets = windowInsets,
                    navController = navController
                )
            }
            composable(route = SettingsRoute.CallLog.route) {
                // 未激活时没有调用记录，默认落在「运行日志」标签
                CallLogPage(
                    windowInsets = windowInsets,
                    navController = navController,
                    startOnRuntime = !isOwner
                )
            }
            composable(route = SettingsRoute.Settings.route) {
                SettingsScreen(
                    windowInsets = windowInsets,
                    navController = navController
                )
            }
            composable(route = SettingsRoute.Theme.route) {
                ThemePage(
                    context = LocalContext.current.applicationContext,
                    navController = navController
                )
            }
            composable(route = SettingsRoute.Activate.route) {
                val mode = it.arguments?.getString("mode")?.let { name ->
                    SettingsRoute.Activate.Mode.valueOf(name)
                } ?: SettingsRoute.Activate.Mode.Dhizuku

                ActivatePage(
                    windowInsets = windowInsets,
                    navController = navController,
                    mode = mode
                )
            }
        }
    }
}
