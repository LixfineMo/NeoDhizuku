package com.neodhizuku.more.ui.page.settings

import android.os.Build

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

import com.neodhizuku.more.NeoDhizuku
import com.neodhizuku.more.NeoLog
import com.neodhizuku.more.server.DhizukuState
import com.neodhizuku.more.ui.component.AppBackground
import com.neodhizuku.more.ui.component.FloatingNavBar
import com.neodhizuku.more.ui.component.NavDestination
import com.neodhizuku.more.ui.component.glass.GlassNavBar
import com.neodhizuku.more.ui.page.settings.activate.ActivatePage
import com.neodhizuku.more.ui.page.settings.security.SecurityPage
import com.neodhizuku.more.ui.page.settings.quickactivate.QuickActivatePage
import com.neodhizuku.more.ui.page.settings.about.AboutPage
import com.neodhizuku.more.ui.page.settings.debug.DebugPage
import com.neodhizuku.more.ui.page.settings.debug.DebugUpdateConfigPage
import com.neodhizuku.more.ui.page.settings.debug.RuntimeInfoPage
import com.neodhizuku.more.ui.page.settings.debug.RuntimeParamPage
import com.neodhizuku.more.ui.page.settings.debug.ServiceConfigPage
import com.neodhizuku.more.ui.page.settings.debug.ServiceFileEditPage
import com.neodhizuku.more.ui.page.settings.updatesettings.UpdateSettingsPage

import com.neodhizuku.more.ui.page.settings.app_management.AppManagementPage
import com.neodhizuku.more.ui.page.settings.blocklist.BlocklistPage
import com.neodhizuku.more.ui.page.settings.calllog.CallLogPage
import com.neodhizuku.more.ui.page.settings.delegation.DelegationPage
import com.neodhizuku.more.ui.page.settings.experimental.ExperimentalPage
import com.neodhizuku.more.ui.page.settings.home.HomePage
import com.neodhizuku.more.ui.page.settings.theme.ThemePage
import com.neodhizuku.more.ui.page.settings.apipolicy.ApiPolicyPage
import com.neodhizuku.more.ui.page.settings.whitelist.WhitelistPage
import com.neodhizuku.more.ui.theme.ThemeConfig
import com.neodhizuku.more.ui.theme.ThemeSettings

import kotlinx.coroutines.launch

// 容器页本身也叫 SettingsPage，与子页同名；用别名避免解析歧义
import com.neodhizuku.more.ui.page.settings.settings.SettingsPage as SettingsScreen

/** 应用主容器：底栏四页用 Pager 承载，其余页面仍是独立路由。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsPage(windowInsets: WindowInsets) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current.applicationContext

    val useNavBar = NeoDhizuku.on(NeoDhizuku.UseFloatingNavBar)
    // 设备管理员也算已激活，底栏与管理入口同样可用
    val isOwner = DhizukuState.state.isActive

    val tabs = remember(isOwner) {
        NavDestination.entries.filter { !it.requiresOwner || isOwner }
    }

    // Pager 状态提在容器层：进入子页面时 tabs 组合会被移除，
    // 放在里面会导致返回后回到第 0 页。
    val pagerState = rememberPagerState(pageCount = { tabs.size })

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showNavBar = useNavBar && currentRoute == SettingsRoute.Tabs.route

    // 激活状态变化会让页数量变动，越界时收回最后一页
    LaunchedEffect(tabs.size) {
        if (tabs.isNotEmpty() && pagerState.currentPage >= tabs.size) {
            pagerState.scrollToPage(tabs.size - 1)
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        NeoLog.d(NeoLog.NAV) {
            "page -> ${tabs.getOrNull(pagerState.currentPage)?.route}"
        }
    }

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

    // 底栏四页之间走 Pager 翻页，其余路由照旧用 NavController。
    // 这样首页/设置页里的入口点进去才会带着底栏，而不是跳成没有底栏的独立页。
    val onSelectTab: (String) -> Unit = { route ->
        val index = tabs.indexOfFirst { it.route == route }
        if (index >= 0) {
            NeoLog.d(NeoLog.NAV) { "select tab -> $route (index=$index)" }
            scope.launch { pagerState.animateScrollToPage(index) }
        } else {
            runCatching { navController.navigate(route) }.onFailure {
                NeoLog.e(NeoLog.NAV, { "navigate failed: $route" }, it)
            }
        }
    }

    val themeSettings by remember(context) { ThemeConfig.getThemeFlow(context) }
        .collectAsState(initial = ThemeSettings())

    // 液态玻璃底栏：开启后走 miuix 的真实背景采样 + 折射，关闭时用常规悬浮底栏。
    // miuix-blur 内部依赖 RuntimeShader（API 33 起），低版本一律退回常规底栏，
    // 否则访问不到 android.graphics.RuntimeShader 会直接崩溃。
    val glassNavBar = themeSettings.useLiquidGlass &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    // 折射依赖 RuntimeShader（API 33 起）。低于 33 时也能开玻璃，只是没有折射，
    // 走自绘半透明那一套——关键是不能退回实心板子。
    LaunchedEffect(glassNavBar, themeSettings.useLiquidGlass) {
        NeoLog.d(NeoLog.NAV) {
            "底栏模式 sdk=${Build.VERSION.SDK_INT} 开关=${themeSettings.useLiquidGlass} " +
                    "miuix折射=$glassNavBar"
        }
    }

    // 折射需要真实背景：把页面内容登记成 miuix 的采样源，
    // 底栏再按同一个 backdrop 取背景做折射 / 模糊。
    //
    // lambda 是绘制作用域、不是组合作用域，里面读不到 MaterialTheme，
    // 所以兜底底色要在外面先取好。
    val surfaceColor = MaterialTheme.colorScheme.surface
    val glassBackdrop = if (glassNavBar) {
        rememberLayerBackdrop {
            drawRect(surfaceColor)
            drawContent()
        }
    } else {
        null
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showNavBar) {
                // 撑满宽度，否则底栏只占内容宽度，两侧点不到
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (glassNavBar) {
                        GlassNavBar(
                            tabs = tabs,
                            selectedIndex = { pagerState.currentPage },
                            // 连续页码：药丸逐帧跟着 Pager 走，而不是对每个整数页
                            // 单独跑一段动画（那样会在中间页停住）
                            selectedPosition = {
                                (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                                    .coerceIn(0f, (tabs.size - 1).coerceAtLeast(0).toFloat())
                            },
                            onNavigate = onSelectTab,
                            // 拖动松手后直接对齐，不再走一次滑动动画
                            onSettleTab = { index ->
                                scope.launch { pagerState.scrollToPage(index) }
                            },
                            backdrop = glassBackdrop,
                        )
                    } else {
                        FloatingNavBar(
                            tabs = tabs,
                            selectedIndex = { pagerState.currentPage },
                            // 连续页码：药丸逐帧跟着 Pager 走，而不是对每个整数页
                            // 单独跑一段动画（那样会在中间页顿住）
                            selectedPosition = {
                                (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                                    .coerceIn(0f, (tabs.size - 1).coerceAtLeast(0).toFloat())
                            },
                            onNavigate = onSelectTab,
                            // 拖动松手后直接对齐，不再走一次滑动动画
                            onSettleTab = { index ->
                                scope.launch { pagerState.scrollToPage(index) }
                            },
                            liquidGlass = themeSettings.useLiquidGlass
                        )
                    }
                }
            }
        }
    ) { padding ->
        // 背景图与页面内容放在同一个容器里：玻璃底栏从这个容器取背景像素，
        // 放在容器外的东西折射不出来。
        val bottomSpace = padding.calculateBottomPadding()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .then(if (glassBackdrop != null) Modifier.layerBackdrop(glassBackdrop) else Modifier)
        ) {
            // 底栏页把底部留白交给列表自己滚（contentPadding），
            // 容器不裁高度——裁了底栏背后就永远采不到内容，只剩一块纯色。
            val tabContentPadding = if (showNavBar) PaddingValues(bottom = bottomSpace) else PaddingValues(0.dp)

            if (themeSettings.backgroundImageSet) {
                AppBackground(
                    blurRadiusDp = themeSettings.backgroundBlur,
                    dimAmount = themeSettings.backgroundDim
                )
            }

            NavHost(
                navController = navController,
                startDestination = SettingsRoute.Tabs.route,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (showNavBar) Modifier else Modifier.padding(bottom = bottomSpace))
            ) {
            composable(route = SettingsRoute.Tabs.route) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    // 相邻页保持已组合状态：翻页时不用现做一次整页组合，
                    // 否则每次切页都要在滚动的第一帧里把新页拼出来，必然掉帧
                    beyondViewportPageCount = 1
                ) { page ->
                    when (tabs.getOrNull(page)?.route) {
                        SettingsRoute.Home.route -> HomePage(
                            windowInsets = windowInsets,
                            navController = navController,
                            contentPadding = tabContentPadding,
                            onSelectTab = onSelectTab
                        )

                        SettingsRoute.AppManagement.route -> AppManagementPage(
                            windowInsets = windowInsets,
                            navController = navController,
                            contentPadding = tabContentPadding
                        )

                        SettingsRoute.CallLog.route -> CallLogPage(
                            windowInsets = windowInsets,
                            navController = navController,
                            contentPadding = tabContentPadding,
                            // 未激活时没有调用记录，默认落在「运行日志」标签
                            startOnRuntime = !isOwner
                        )

                        SettingsRoute.Settings.route -> SettingsScreen(
                            windowInsets = windowInsets,
                            navController = navController,
                            contentPadding = tabContentPadding,
                            onSelectTab = onSelectTab
                        )

                        else -> Unit
                    }
                }
            }
            composable(route = SettingsRoute.Blocklist.route) {
                BlocklistPage(
                    windowInsets = windowInsets,
                    navController = navController
                )
            }
            composable(route = SettingsRoute.Security.route) {
                SecurityPage(
                    windowInsets = windowInsets,
                    navController = navController
                )
            }
            composable(route = SettingsRoute.Whitelist.route) {
                WhitelistPage(
                    windowInsets = windowInsets,
                    navController = navController
                )
            }
            composable(route = SettingsRoute.ApiPolicy.route) {
                ApiPolicyPage(
                    windowInsets = windowInsets,
                    navController = navController
                )
            }
            composable(route = SettingsRoute.Experimental.route) {
                ExperimentalPage(
                    windowInsets = windowInsets,
                    navController = navController
                )
            }
            composable(route = SettingsRoute.Delegation.route) {
                DelegationPage(
                    windowInsets = windowInsets,
                    navController = navController
                )
            }
            composable(route = SettingsRoute.Theme.route) {
                ThemePage(
                    context = context,
                    navController = navController
                )
            }
            composable(route = SettingsRoute.About.route) {
                AboutPage(
                    windowInsets = windowInsets,
                    navController = navController
                )
            }
            composable(route = SettingsRoute.UpdateSettings.route) {
                UpdateSettingsPage(
                    windowInsets = windowInsets,
                    navController = navController
                )
            }
            composable(route = SettingsRoute.Debug.route) {
                DebugPage(
                    windowInsets = windowInsets,
                    navController = navController
                )
            }
            // 调试分区：五个二级页面。原来单页里的内容全部搬到
            // DebugUpdateConfigPage，本页只留菜单与重置入口。
            composable(route = SettingsRoute.DebugUpdateConfig.route) {
                DebugUpdateConfigPage(
                    windowInsets = windowInsets,
                    navController = navController
                )
            }
            composable(route = SettingsRoute.DebugRuntimeParams.route) {
                RuntimeParamPage(
                    windowInsets = windowInsets,
                    navController = navController
                )
            }
            composable(route = SettingsRoute.DebugRuntimeInfo.route) {
                RuntimeInfoPage(
                    windowInsets = windowInsets,
                    navController = navController
                )
            }
            composable(route = SettingsRoute.DebugServiceFiles.route) {
                ServiceConfigPage(
                    windowInsets = windowInsets,
                    navController = navController
                )
            }
            composable(route = SettingsRoute.DebugServiceFileEdit.route) { entry ->
                ServiceFileEditPage(
                    windowInsets = windowInsets,
                    navController = navController,
                    key = entry.arguments?.getString("key").orEmpty()
                )
            }
            composable(route = SettingsRoute.QuickActivate.route) {
                QuickActivatePage(
                    windowInsets = windowInsets,
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
}
