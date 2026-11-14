package com.neodhizuku.more.ui.page.settings

import android.os.Build

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
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
import com.neodhizuku.more.ui.page.settings.debug.DebugUiOverrides
import com.neodhizuku.more.ui.component.AppBackground
import com.neodhizuku.more.ui.component.LocalCardBlurDp
import com.neodhizuku.more.ui.component.LocalCardGlassBackdrop
import com.neodhizuku.more.ui.component.LocalCardSolidFallback
import com.neodhizuku.more.ui.component.FloatingNavBar
import com.neodhizuku.more.ui.component.NavDestination
import com.neodhizuku.more.ui.component.RailTotalWidth
import com.neodhizuku.more.ui.component.SideNavRail
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
import com.neodhizuku.more.ui.page.settings.experimental.Cve98432Page
import com.neodhizuku.more.ui.page.settings.experimental.ExperimentalPage
import com.neodhizuku.more.ui.page.settings.home.HomePage
import com.neodhizuku.more.ui.page.settings.owneridentity.OwnerIdentityPage
import com.neodhizuku.more.ui.page.settings.theme.ThemePage
import com.neodhizuku.more.ui.page.settings.apipolicy.ApiPolicyPage
import com.neodhizuku.more.ui.page.settings.hookconfig.HookConfigPage
import com.neodhizuku.more.ui.page.settings.whitelist.WhitelistPage
import com.neodhizuku.more.ui.theme.LocalUiEngine
import com.neodhizuku.more.ui.theme.ThemeConfig
import com.neodhizuku.more.ui.theme.ThemeSettings
import com.neodhizuku.more.ui.theme.navMotion

import kotlinx.coroutines.Job
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
    val isOwner = DebugUiOverrides.displayState().isActive

    val tabs = remember(isOwner) {
        NavDestination.entries.filter { !it.requiresOwner || isOwner }
    }

    // Pager 状态提在容器层：进入子页面时 tabs 组合会被移除，
    // 放在里面会导致返回后回到第 0 页。
    val pagerState = rememberPagerState(pageCount = { tabs.size })

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showNavBar = useNavBar && currentRoute == SettingsRoute.Tabs.route

    // 宽屏（平板 / 横屏）把底部悬浮栏换成左侧竖排导航栏。
    // 用 screenWidthDp 判断，不额外引入 window-size-class 依赖；
    // 与手机端一致：只在主 Tab 容器显示，二级页面保持全屏。
    // 侧栏不受「使用悬浮底栏」开关约束：那个开关管的是手机端那条横条，
    // 平板上的竖排导航是独立布局，不该被它一起关掉。
    val expanded = LocalConfiguration.current.screenWidthDp >= 600
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val railVisible = expanded && currentRoute == SettingsRoute.Tabs.route

    // 二级页面进出场按引擎分流；RTL 下推入方向整体翻转。
    // 规格只在引擎或布局方向变化时重建，避免每次重组都让 NavHost 重启动画。
    val uiEngine = LocalUiEngine.current
    val motion = remember(uiEngine, isLtr) { navMotion(uiEngine, isLtr) }

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
    // 翻页瞬时对齐：animateScrollToPage 跨多页会逐页跑弹簧动画并组合
    // 中间页，四张重页面叠在一起就是「切页卡半秒」的来源。
    val settleJobBox = remember { arrayOfNulls<Job>(1) }
    val settleTab: (Int) -> Unit = { index ->
        settleJobBox[0]?.cancel()
        settleJobBox[0] = scope.launch { pagerState.scrollToPage(index) }
    }

    val onSelectTab: (String) -> Unit = { route ->
        val index = tabs.indexOfFirst { it.route == route }
        if (index >= 0) {
            NeoLog.d(NeoLog.NAV) { "select tab -> $route (index=$index)" }
            settleTab(index)
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
    // lambda 是绘制作用域、不是组合作用域，里面读不到 MaterialTheme，
    // 所以兜底底色要在外面先取好。
    // 采样源要铺不透明底：主题层把 surface 做成半透明了，直接用会把透明区一起采进去。
    // 兜底一律用中性黑，浅色主题下不会把卡片 / 底栏洗成一片白。
    val backdropBase = Color.Black
    // 宽屏不渲染底栏，也就没有折射的消费者；保留 backdrop 只会让侧栏把自己
    // 录进采样源，每帧多一层绘制。关掉它是宽屏流畅度的一半。
    val glassBackdrop = if (glassNavBar && !expanded) {
        rememberLayerBackdrop {
            drawRect(backdropBase)
            drawContent()
        }
    } else {
        null
    }

    // 卡片玻璃：只把「背景图层」登记成采样源，避免把卡片自己的文字也录进 backdrop。
    // Android 13 以下 miuix 的 RuntimeShader 不可用，退回接近实色的卡片兜底。
    // 先铺一层不透明底色再记录背景图：模糊在圆角外沿取样时不会采到透明区，
    // 否则卡片四周会出现一圈发黑的边，中间又显得格外白。
    val cardBackdrop = rememberLayerBackdrop {
        drawRect(backdropBase)
        drawContent()
    }
    val cardGlassSupported = themeSettings.backgroundImageSet &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val cardGlassActive = cardGlassSupported && themeSettings.cardBlur > 0f
    val cardSolidFallback = themeSettings.backgroundImageSet && !cardGlassSupported

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            // 返回时底栏不能比下层的推入动画更早硬跳出来，淡入淡出与页面过渡同步。
            AnimatedVisibility(
                visible = !expanded && showNavBar,
                enter = fadeIn(animationSpec = tween(160, easing = FastOutSlowInEasing)),
                exit = fadeOut(animationSpec = tween(120, easing = FastOutSlowInEasing))
            ) {
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
                            // 拖动松手后直接对齐
                            onSettleTab = settleTab,
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
                            // 拖动松手后直接对齐
                            onSettleTab = settleTab,
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

        CompositionLocalProvider(
            LocalCardGlassBackdrop provides if (cardGlassActive) cardBackdrop else null,
            LocalCardBlurDp provides themeSettings.cardBlur,
            LocalCardSolidFallback provides cardSolidFallback
        ) {
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
                    // 采样源只登记图片层，遮罩在其后绘制：
                    // 四周被压暗、卡片采到的是原图，两者同模糊但深浅不同。
                    imageLayer = if (cardGlassActive) Modifier.layerBackdrop(cardBackdrop)
                    else Modifier,
                    blurRadiusDp = themeSettings.backgroundBlur,
                    dimAmount = themeSettings.backgroundDim,
                    imageScale = themeSettings.backgroundScale,
                    imageOffsetX = themeSettings.backgroundOffsetX,
                    imageOffsetY = themeSettings.backgroundOffsetY
                )
            }

            NavHost(
                        navController = navController,
                        startDestination = SettingsRoute.Tabs.route,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (showNavBar) Modifier else Modifier.padding(bottom = bottomSpace)),
                // 四段都显式给出：navigation-compose 会按预见式返回手势的进度
                // 逐帧定位 popEnter / popExit，缺一段就会退回生硬的瞬切。
                enterTransition = { motion.enter() },
                exitTransition = { motion.exit() },
                popEnterTransition = { motion.popEnter() },
                popExitTransition = { motion.popExit() }
            ) {
            composable(route = SettingsRoute.Tabs.route) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (railVisible) Modifier.padding(
                                start = if (isLtr) RailTotalWidth else 0.dp,
                                end = if (isLtr) 0.dp else RailTotalWidth
                            ) else Modifier
                        ),
                    // 相邻页保持已组合状态：翻页时不用现做一次整页组合，
                    // 否则每次切页都要在滚动的第一帧里把新页拼出来，必然掉帧
                    beyondViewportPageCount = 1
                ) { page ->
                    // 页签数量变化（激活态切换）与 Pager 页码错位时回落到首页，
                    // 不让 else 分支渲染出一张空白页。
                    val destination = tabs.getOrNull(page) ?: tabs.firstOrNull()
                    when (destination?.route) {
                        SettingsRoute.Home.route -> HomePage(
                            windowInsets = windowInsets,
                            navController = navController,
                            contentPadding = tabContentPadding,
                            onSelectTab = onSelectTab
                        )

                        SettingsRoute.AppManagement.route -> AppManagementPage(
                            windowInsets = windowInsets,
                            contentPadding = tabContentPadding
                        )

                        SettingsRoute.CallLog.route -> CallLogPage(
                            windowInsets = windowInsets,
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
            composable(route = SettingsRoute.HookConfig.route) {
                HookConfigPage(
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
            composable(route = SettingsRoute.Cve98432.route) {
                Cve98432Page(
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
            composable(route = SettingsRoute.OwnerIdentity.route) {
                OwnerIdentityPage(navController = navController)
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

            // 侧栏叠在内容之上，而不是和 NavHost 并列放进 Row。
            // 并列时 Pager 被 weight(1f) 钳在受限宽度里，每次翻页都要重测整条
            // 布局；叠层则让 Pager 保持 fillMaxSize，测量路径与手机端完全一致。
            if (railVisible) {
                Box(
                    modifier = Modifier.align(
                        if (isLtr) Alignment.CenterStart else Alignment.CenterEnd
                    )
                ) {
                    SideNavRail(
                        tabs = tabs,
                        selectedIndex = { pagerState.currentPage },
                        selectedPosition = {
                            (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                                .coerceIn(0f, (tabs.size - 1).coerceAtLeast(0).toFloat())
                        },
                        onNavigate = onSelectTab,
                        onSettleTab = settleTab,
                        showPill = themeSettings.tabletShowPill,
                    )
                }
            }
        }
        }
    }
}
