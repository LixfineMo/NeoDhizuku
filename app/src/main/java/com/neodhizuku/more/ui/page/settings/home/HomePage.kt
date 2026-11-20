package com.neodhizuku.more.ui.page.settings.home

import android.app.admin.DevicePolicyManager
import android.content.ClipData
import android.content.Context
import android.content.Intent

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.twotone.Adb
import androidx.compose.material.icons.twotone.ArrowDropDown
import androidx.compose.material.icons.twotone.AttachMoney
import androidx.compose.material.icons.twotone.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.twotone.Cancel
import androidx.compose.material.icons.twotone.Code
import androidx.compose.material.icons.twotone.DoNotDisturbOn
import androidx.compose.material.icons.twotone.RoomPreferences
import androidx.compose.material.icons.twotone.SentimentVeryDissatisfied
import androidx.compose.material.icons.twotone.SentimentVerySatisfied
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material.icons.twotone.SwapHorizontalCircle
import androidx.compose.material.icons.twotone.Terminal
import androidx.compose.material.icons.twotone.Warning
import com.neodhizuku.more.ui.component.NeoAlertDialog as AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import com.neodhizuku.more.ui.component.NeoDropdownMenu
import com.neodhizuku.more.ui.component.NeoDropdownItem
import com.neodhizuku.more.ui.component.NeoCard
import com.neodhizuku.more.ui.component.LocalCardBlurDp
import com.neodhizuku.more.ui.component.LocalCardGlassBackdrop
import com.neodhizuku.more.ui.component.LocalCardSolidFallback
import com.neodhizuku.more.ui.component.MiuixGlassLayer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.CardDefaults as MiuixCardDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import androidx.navigation.NavController

import com.neodhizuku.more.NeoDhizuku
import com.neodhizuku.more.NeoLog
import com.neodhizuku.more.R
import com.neodhizuku.more.ui.theme.combine
import com.neodhizuku.more.data.common.util.DpDirectActivation
import com.neodhizuku.more.data.common.util.StartupUpdateChecker
import com.neodhizuku.more.data.common.util.checkShizukuWorked
import com.neodhizuku.more.data.common.util.openUrlInBrowser
import com.neodhizuku.more.data.common.util.toast
import com.neodhizuku.more.data.settings.repo.SettingsRepo
import com.neodhizuku.more.server.DaemonService
import com.neodhizuku.more.server.DhizukuState
import com.neodhizuku.more.ui.page.settings.debug.DebugUiOverrides
import com.neodhizuku.more.server.ServiceStatus
import com.neodhizuku.more.server.HookConfigStore
import com.neodhizuku.more.ui.page.settings.SettingsRoute
import com.neodhizuku.more.ui.theme.LocalUiEngine
import com.neodhizuku.more.ui.theme.UiEngine
import com.neodhizuku.more.ui.theme.exclude

import kotlin.system.exitProcess

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import org.koin.compose.koinInject

/**
 * HomePage
 * (windowInsets: WindowInsets, navController: NavController, contentPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(0.dp), onSelectTab: (String) -> Unit = { route -> runCatching { navController.navigate(route) } })
 * 作用：执行 HomePage
 * 备注：Compose 组件；使用了实验性 API
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomePage(
    windowInsets: WindowInsets,
    navController: NavController,
    contentPadding: androidx.compose.foundation.layout.PaddingValues =
        androidx.compose.foundation.layout.PaddingValues(0.dp),
    /** 底部四页之间用翻页切换，其余路由交给 NavController */
    onSelectTab: (String) -> Unit = { route ->
        runCatching { navController.navigate(route) }
    }
) {
    // 显示层读取：调试页的「强制显示已激活」只影响这里，不碰执行链
    val dhizukuState = DebugUiOverrides.displayState()
    val context = LocalContext.current
    // LazyColumn 的 content 是 LazyListScope.() -> Unit（非 @Composable），
    // CompositionLocal 只能在可组合上下文里读取，再把普通值带进去。
    val uiEngine = LocalUiEngine.current

    // 冷启动时已在 Application 里查过一次官网清单，这里只负责把结果显示出来
    val startupUpdate by StartupUpdateChecker.state.collectAsState()

    // 向导被置 0 后若还没恢复，即使已激活也要保留免删激活入口，
    // 否则用户找不到「手动恢复」的地方。
    var setupPendingRestore by remember {
        mutableStateOf(DpDirectActivation.isPendingRestore(context))
    }
    LaunchedEffect(dhizukuState.isActive) {
        setupPendingRestore = DpDirectActivation.isPendingRestore(context)
    }

    Scaffold(
        modifier = Modifier
            .windowInsetsPadding(windowInsets.exclude(WindowInsetsSides.Bottom))
            .fillMaxSize(),
        // 外层 NavHost 已应用 Scaffold 的 padding（含系统 inset 与底栏高度），
        // 这里再处理一次 Bottom 会导致列表底部多出一块空白。
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.app_name))
                },
                actions = {
                    TopBarActions(navController = navController)
                }
            )
        }) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp).combine(contentPadding),
            modifier = Modifier
                .fillMaxSize()
                .padding(it),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (startupUpdate is StartupUpdateChecker.State.Available) item("startup_update") {
                StartupUpdateWidget(startupUpdate as StartupUpdateChecker.State.Available, navController)
            }
            item("dhizuku_state") {
                DhizukuStateWidget(navController)
            }
            // 状态卡没显示时（未激活 / 关了状态卡），徽标仍需可见。
            // miuix 引擎的状态卡自带身份与框架药丸，不单独占一行。
            val showStatusCard = NeoDhizuku.on(NeoDhizuku.UseHomeStatusCard) && dhizukuState.isActive
            if (!showStatusCard && uiEngine != UiEngine.MIUIX) item("status_badges") {
                StatusBadges(includeActivation = false)
            }
            if (dhizukuState.isActive) item("settings") {
                SettingsWidget(navController, onSelectTab)
            }
            if (dhizukuState.isActive) item("app_management") {
                AppManagementWidget(navController, onSelectTab)
            }
            // 激活入口按"是否已是所有者"而不是"是否可用"来分流。
            // 仅设备管理员时 isActive 为 true，但那不是真正的所有者：
            // 用 isActive 判断会立刻把 Shizuku / 免删 / ADB 三条升级路径藏掉，
            // 只留下"通过其他实现转移"这一条在设备管理员下根本走不通的入口。
            // 因此这几项统一改用 isOwner。
            if (dhizukuState.isOwner) item("dhizuku") {
                DhizukuWidget(navController)
            }
            if (!dhizukuState.isOwner) item("shizuku") {
                ShizukuWidget(navController)
            }
            // 未成为所有者时始终显示；已成为所有者但向导未恢复时也保留
            if (!dhizukuState.isOwner || setupPendingRestore) item("quick_activate") {
                QuickActivateWidget(navController)
            }
            if (!dhizukuState.isOwner) item("adb") {
                AdbWidget()
            }
            if (dhizukuState.isActive) item("home_deactivate_title") {
                DeactivateWidget()
            }
        }
    }
}

/**
 * StartupUpdateWidget
 * (state: StartupUpdateChecker.State.Available, navController: NavController)
 * 作用：启动检查发现新版本时的提示卡。 只提示不打断：点整卡直接下载，点"详情"去关于页看完整更新日志。 下载地址为空（清单里只给了详情链接）时退化成跳关于页。
 * 备注：Compose 组件；仅内部使用
 */
@Composable
private fun LazyItemScope.StartupUpdateWidget(
    state: StartupUpdateChecker.State.Available,
    navController: NavController
) {
    val context = LocalContext.current
    CardWidget(
        onClick = {
            if (state.downloadUrl.isBlank()) {
                navController.navigate(SettingsRoute.About.route)
            } else {
                context.openUrlInBrowser(state.downloadUrl)
            }
        },
        icon = {
            Icon(imageVector = Icons.TwoTone.Bolt, contentDescription = null)
        },
        title = {
            Text(stringResource(R.string.home_update_available_title, state.version))
        },
        text = {
            val brief = state.changelog.lineSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .take(3)
                .joinToString("\n")
            Text(brief.ifBlank { stringResource(R.string.home_update_available_dsp) })
        },
        content = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { navController.navigate(SettingsRoute.About.route) }) {
                    Text(stringResource(R.string.home_update_detail))
                }
            }
        }
    )
}

/**
 * TopBarActions
 * (navController: NavController)
 * 作用：执行 TopBarActions
 * 备注：Compose 组件；使用了实验性 API；仅内部使用
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBarActions(navController: NavController) {
    val context = LocalContext.current
    val settingsRepo = koinInject<SettingsRepo>()
    val scope = rememberCoroutineScope()

    var shutdownDialogShow by remember { mutableStateOf(false) }
    var donateMenuExpanded by remember { mutableStateOf(false) }
    var donateHideConfirmShow by remember { mutableStateOf(false) }

    val donateButtonHidden by settingsRepo.flowDonateButtonHidden()
        .collectAsState(initial = settingsRepo.isDonateButtonHidden)

    // 左上角入口为「关于」：跳转到关于页。
    // 长按仍可隐藏 —— 复用原来的开关与确认弹窗，不新增状态。
    if (!donateButtonHidden) {
        Box {
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
                tooltip = { PlainTooltip { Text(stringResource(R.string.about_title)) } },
                state = rememberTooltipState()
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .combinedClickable(
                            onClick = {
                                NeoLog.d(NeoLog.NAV) { "home -> about" }
                                navController.navigate(SettingsRoute.About.route)
                            },
                            onLongClick = { donateMenuExpanded = true }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Info, contentDescription = stringResource(R.string.about_title))
                }
            }
            NeoDropdownMenu(
                expanded = donateMenuExpanded,
                onDismissRequest = { donateMenuExpanded = false }
            ) {
                NeoDropdownItem(
                    text = stringResource(R.string.donate_hide_action),
                    onClick = {
                        donateMenuExpanded = false
                        donateHideConfirmShow = true
                    }
                )
            }
        }
    }

    // 服务启停入口只在已激活时提供。
    // 未激活时服务本来就不该运行，若照常显示按钮，会落到"服务未运行"分支
    // 展示重启图标；此时点击会去 startForegroundService，而守护服务在未成为
    // 所有者时的启动路径会抛异常导致崩溃。
    // 判据用 isActive 而非 isOwner：仅设备管理员时服务同样可以运行，
    // 需要能停能启。
    // DhizukuState.state 是 mutableStateOf，读取即自动订阅
    if (DhizukuState.state.isActive) {
        var serviceRunning by remember { mutableStateOf(ServiceStatus.isRunning()) }
        LaunchedEffect(Unit) {
            while (true) {
                serviceRunning = ServiceStatus.isRunning()
                kotlinx.coroutines.delay(500)
            }
        }

        if (serviceRunning) {
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
                tooltip = { PlainTooltip { Text(stringResource(R.string.home_stop_service)) } },
                state = rememberTooltipState()
            ) {
                IconButton(onClick = { shutdownDialogShow = true }) {
                    Icon(
                        Icons.TwoTone.Cancel,
                        contentDescription = stringResource(R.string.home_stop_service)
                    )
                }
            }
        } else {
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
                tooltip = { PlainTooltip { Text(stringResource(R.string.home_restart_service)) } },
                state = rememberTooltipState()
            ) {
                IconButton(onClick = {
                    scope.launch {
                        val appContext = context.applicationContext
                        settingsRepo.isServiceStoppedByUser = false
                        ServiceStatus.markStarted()
                        runCatching {
                            appContext.startForegroundService(
                                Intent(appContext, DaemonService::class.java)
                            )
                        }
                        NeoLog.i(NeoLog.HOME) { "restart service requested" }
                    }
                }) {
                    Icon(
                        Icons.Filled.RestartAlt,
                        contentDescription = stringResource(R.string.home_restart_service)
                    )
                }
            }
        }
    }

    if (shutdownDialogShow) {
        AlertDialog(onDismissRequest = {
            shutdownDialogShow = false
        }, confirmButton = {
            TextButton(onClick = {
                shutdownDialogShow = false
            }) {
                Text(stringResource(R.string.cancel))
            }
            TextButton(onClick = {
                shutdownDialogShow = false
                if (NeoDhizuku.on(NeoDhizuku.FixShutdownAction)) {
                    // 只停服务，不动 API 开关：改了 isDhizukuEnabled 会让首页
                    // 显示「已激活」却把第三方全部拒掉，表现为「识别不到服务」。
                    // 服务是 START_STICKY，因此同时写入"用户已停止"标记，
                    // 系统重建时 DaemonService 会自行再停一次。
                    val appContext = context.applicationContext
                    scope.launch {
                        settingsRepo.isServiceStoppedByUser = true
                        ServiceStatus.markStopped()
                        runCatching {
                            appContext.stopService(Intent(appContext, DaemonService::class.java))
                        }
                        NeoLog.i(NeoLog.HOME) { "stop service requested" }
                    }
                } else {
                    exitProcess(0)
                }
            }) {
                Text(stringResource(R.string.confirm))
            }
        }, title = {
            Text(stringResource(R.string.home_shutdown_title))
        }, text = {
            Text(stringResource(R.string.home_shutdown_dsp))
        })
    }

    if (donateHideConfirmShow) {
        AlertDialog(onDismissRequest = {
            donateHideConfirmShow = false
        }, confirmButton = {
            TextButton(onClick = {
                donateHideConfirmShow = false
            }) {
                Text(stringResource(R.string.cancel))
            }
            TextButton(onClick = {
                settingsRepo.isDonateButtonHidden = true
                donateHideConfirmShow = false
            }) {
                Text(stringResource(R.string.confirm))
            }
        }, title = {
            Text(stringResource(R.string.donate_hide_confirm_title))
        }, text = {
            Text(stringResource(R.string.donate_hide_confirm_dsp))
        })
    }
}

/**
 * DhizukuStateWidget
 * (navController: NavController)
 * 作用：执行 DhizukuStateWidget
 * 备注：Compose 组件；仅内部使用
 */
@Composable
private fun LazyItemScope.DhizukuStateWidget(navController: NavController) {
    // 用 isActive 而非 isOwner：仅设备管理员也算已激活，
    // 应走新的状态卡布局，身份由 [activationLabel] 显示为「设备管理员」。
    val isActive = DebugUiOverrides.displayState().isActive
    val context = LocalContext.current

    // miuix 引擎：状态区用整宽激活卡 + 下方并排数值卡。
    if (LocalUiEngine.current == UiEngine.MIUIX) {
        MiuixStatusRow(navController = navController)
        return
    }

    // 激活态用左侧状态卡 + 右侧两张服务状态卡的布局，未激活保留原版样式。
    if (NeoDhizuku.on(NeoDhizuku.UseHomeStatusCard) && isActive) {
        ActivationStatusRow(navController = navController)
        return
    }

    @Suppress("AnimateAsStateLabel")
    val iconContainerColor by animateColorAsState(
        if (isActive) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.errorContainer
    )

    @Suppress("AnimateAsStateLabel")
    val iconColor by animateColorAsState(
        if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onErrorContainer
    )
    CardWidget(colors = CardDefaults.cardColors(
        containerColor = iconContainerColor
    ), icon = {
        Icon(
            imageVector = Icons.TwoTone.run {
                if (isActive) SentimentVerySatisfied
                else SentimentVeryDissatisfied
            },
            contentDescription = null
        )
    }, iconColors = IconButtonDefaults.iconButtonColors(
        containerColor = iconContainerColor,
        contentColor = iconColor
    ), title = {
        @Suppress("AnimatedContentLabel")
        AnimatedContent(targetState = isActive) {
            Text(
                if (it) stringResource(
                    R.string.home_status_owner_granted,
                    // 仅设备管理员时显示「设备管理员」，
                    // 避免沿用旧的二选一逻辑把它误标成「设备所有者」
                    stringResource(
                        when {
                            DebugUiOverrides.displayState().isProfileOwner -> R.string.confirm_profile_owner
                            DebugUiOverrides.displayState().isDeviceOwner -> R.string.confirm_device_owner
                            else -> R.string.capability_device_admin
                        }
                    )
                )
                else stringResource(R.string.home_status_owner_denied)
            )
        }
    }, onClick = { DhizukuState.sync(context) })
}

/**
 * 激活后的首页顶部区域：左侧一张竖向状态卡，右侧上下两张小卡。
 * - 左卡：对勾图标 + 「已激活」+ 所有者类型标签 + 版本号，点击进入激活页
 * - 右上：授权应用数量
 * - 右下：服务运行时间（HH:MM:SS，服务每次启动归零重算）
 */
@Composable
private fun LazyItemScope.ActivationStatusRow(navController: NavController) {
    val authorizedCount = ServiceStatus.authorizedCount
    var uptime by remember { mutableLongStateOf(ServiceStatus.uptimeMillis()) }

    LaunchedEffect(Unit) {
        NeoLog.d(NeoLog.HOME) {
            "status row: authorized=$authorizedCount, " +
                "uptime=${ServiceStatus.formatUptime(ServiceStatus.uptimeMillis())}, " +
                "isDeviceOwner=${DhizukuState.state.isDeviceOwner}, " +
                "isProfileOwner=${DhizukuState.state.isProfileOwner}"
        }
    }

    // 运行时间每秒刷新一次
    LaunchedEffect(Unit) {
        while (true) {
            uptime = ServiceStatus.uptimeMillis()
            kotlinx.coroutines.delay(1000)
        }
    }

    // 行高由右侧两张固定高度的数值卡决定，左卡补满剩余空间，底端与运行时间卡对齐。
    val statCardHeight = 96.dp
    val statGap = 12.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(statCardHeight * 2 + statGap)
            .animateItem(),
        horizontalArrangement = Arrangement.spacedBy(statGap)
    ) {
        StatusCard(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            onClick = {
                navController.navigate(
                    SettingsRoute.Activate.route(SettingsRoute.Activate.Mode.Dhizuku)
                )
            }
        )

        Column(
            modifier = Modifier.fillMaxWidth(0.42f),
            verticalArrangement = Arrangement.spacedBy(statGap)
        ) {
            ServiceStatCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(statCardHeight),
                label = stringResource(R.string.home_stat_authorized_apps),
                value = authorizedCount.toString()
            )
            ServiceStatCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(statCardHeight),
                label = stringResource(R.string.home_stat_uptime),
                value = ServiceStatus.formatUptime(uptime),
                stopped = uptime < 0
            )
        }
    }

    // 仅设备管理员时，第三方应用的标准 Dhizuku 初始化必然失败：
    // 客户端 API 只认可设备所有者 / 资料管理员，设备管理员不在其中。
    if (DebugUiOverrides.displayState().level ==
        com.neodhizuku.more.server.CapabilityLevel.DEVICE_ADMIN
    ) {
        Text(
            text = stringResource(R.string.home_device_admin_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .fillMaxWidth()
                .animateItem()
                .padding(horizontal = 4.dp)
        )
    }
}

/**
 * MiuixStatusRow
 * (navController: NavController)
 * 作用：miuix 引擎的首页状态区。未激活时只有顶部提示条。
 * 备注：Compose 组件；仅内部使用
 */
@Composable
private fun LazyItemScope.MiuixStatusRow(navController: NavController) {
    val state = DebugUiOverrides.displayState()
    val isActive = state.isActive
    val authorizedCount = ServiceStatus.authorizedCount
    var uptime by remember { mutableLongStateOf(ServiceStatus.uptimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            uptime = ServiceStatus.uptimeMillis()
            delay(1000)
        }
    }

    // 未激活时不显示状态卡与数值卡。
    if (!isActive) {
        MiuixWarningBanner()
        return
    }

    // 激活卡占满整行，下方两张数值卡横向并排一行，不再做左右分栏。
    val statusCardHeight = 160.dp
    val statCardHeight = 88.dp
    val statGap = 12.dp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateItem(),
        verticalArrangement = Arrangement.spacedBy(statGap)
    ) {
        MiuixStatusCard(
            modifier = Modifier
                .fillMaxWidth()
                .height(statusCardHeight),
            onClick = {
                navController.navigate(
                    SettingsRoute.Activate.route(SettingsRoute.Activate.Mode.Dhizuku)
                )
            }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(statGap)
        ) {
            MiuixStatCard(
                modifier = Modifier
                    .weight(1f)
                    .height(statCardHeight),
                label = stringResource(R.string.home_stat_authorized_apps),
                value = authorizedCount.toString()
            )
            MiuixStatCard(
                modifier = Modifier
                    .weight(1f)
                    .height(statCardHeight),
                label = stringResource(R.string.home_stat_uptime),
                value = if (uptime < 0) "--:--:--" else ServiceStatus.formatUptime(uptime)
            )
        }
    }

    if (state.level == com.neodhizuku.more.server.CapabilityLevel.DEVICE_ADMIN) {
        Text(
            text = stringResource(R.string.home_device_admin_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .fillMaxWidth()
                .animateItem()
                .padding(horizontal = 4.dp)
        )
    }
}

/**
 * MiuixWarningBanner
 * ()
 * 作用：未激活时的顶部警告横幅。
 * 备注：Compose 组件；仅内部使用
 */
@Composable
private fun MiuixWarningBanner() {
    val dark = isSystemInDarkTheme()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (dark) Color(0xFF3A1F20) else Color(0xFFFDE9E9))
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text(
            text = stringResource(R.string.home_status_owner_denied),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (dark) Color(0xFFFF8A80) else Color(0xFFD32F2F)
        )
    }
}

/**
 * miuixGlassActive
 * ()
 * 作用：首页 miuix 卡的局部模糊是否生效。
 * 备注：Compose 组件；仅内部使用
 */
@Composable
private fun miuixGlassActive(): Boolean {
    return LocalCardGlassBackdrop.current != null
        && LocalCardBlurDp.current > 0f
        && !LocalCardSolidFallback.current
}

/**
 * MiuixCardGlass
 * (tint: Color)
 * 作用：首页 miuix 卡的玻璃层。组件库底色会盖住挂在 Card 外层的 drawBackdrop， 因此画在内容最底层；[tint] 半透明叠在模糊结果上，夜间仍保留激活态的绿色。
 * 备注：Compose 组件；仅内部使用
 */
@Composable
private fun BoxScope.MiuixCardGlass(tint: Color) {
    val backdrop = LocalCardGlassBackdrop.current ?: return
    val blurDp = LocalCardBlurDp.current
    if (blurDp <= 0f || LocalCardSolidFallback.current) return
    val radiusPx = with(LocalDensity.current) { blurDp.dp.toPx() }
    MiuixGlassLayer(
        backdrop = backdrop,
        shape = RoundedCornerShape(24.dp),
        radiusPx = radiusPx,
        tint = tint,
        modifier = Modifier.matchParentSize()
    )
}

/**
 * MiuixStatusCard
 * (modifier: Modifier = Modifier, onClick: () -> Unit)
 * 作用：激活卡：带按压下沉反馈的交互卡。
 * 备注：Compose 组件；仅内部使用
 */
@Composable
private fun MiuixStatusCard(modifier: Modifier = Modifier, onClick: () -> Unit) {
    val dark = isSystemInDarkTheme()
    // 夜间用深绿而不是组件库的黑色：保留激活态的绿色识别度。
    val cardColor = if (dark) Color(0xFF1A3825) else Color(0xFFE8F8EC)
    val titleColor = if (dark) Color(0xFFE8F2EA) else Color(0xFF111318)
    val subColor = if (dark) Color(0xFFB7C7BC) else Color(0xFF4A4F58)
    // 绿勾圆环日夜都保持亮绿，不随卡片底色变暗。
    val ringColor = Color(0xFF2FCB63)
    val glassActive = miuixGlassActive()

    MiuixCard(
        modifier = modifier,
        onClick = onClick,
        showIndication = true,
        pressFeedbackType = PressFeedbackType.Tilt,
        cornerRadius = 24.dp,
        insideMargin = PaddingValues(0.dp),
        colors = MiuixCardDefaults.defaultColors(
            color = if (glassActive) Color.Transparent else cardColor
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            MiuixCardGlass(cardColor.copy(alpha = 0.38f))
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(20.dp)
            ) {
                Text(
                    text = stringResource(R.string.home_status_owner_granted_short),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = titleColor
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MiuixTag(label = activationLabel(), color = MiuixTheme.colorScheme.primary)
                    val framework = hookFrameworkLabel()
                    if (framework != null) {
                        MiuixTag(label = framework, color = MaterialTheme.colorScheme.tertiary)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.home_status_version, displayVersionName()),
                    fontSize = 14.sp,
                    color = subColor
                )
                // 卡片剩余空间留给右下角的圆环。
                Spacer(Modifier.weight(1f))
            }
            MiuixCheckRing(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 10.dp, y = 26.dp)
                    .size(108.dp),
                color = ringColor
            )
        }
    }
}

/**
 * MiuixStatCard
 * (modifier: Modifier = Modifier, label: String, value: String)
 * 作用：下方数值卡：跟随明暗配色的标签与大号数值，两张等宽并排。
 * 备注：Compose 组件；仅内部使用
 */
@Composable
private fun MiuixStatCard(modifier: Modifier = Modifier, label: String, value: String) {
    val dark = isSystemInDarkTheme()
    val cardColor = if (dark) Color(0xFF24262B) else Color(0xFFF4F5F7)
    val labelColor = if (dark) Color(0xFF9AA1AB) else Color(0xFF6B7280)
    val valueColor = if (dark) Color(0xFFE9EBEF) else Color(0xFF111318)
    val glassActive = miuixGlassActive()
    MiuixCard(
        modifier = modifier,
        cornerRadius = 24.dp,
        insideMargin = PaddingValues(0.dp),
        colors = MiuixCardDefaults.defaultColors(
            color = if (glassActive) Color.Transparent else cardColor
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            MiuixCardGlass(cardColor.copy(alpha = 0.30f))
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = label,
                    fontSize = 14.sp,
                    color = labelColor,
                    maxLines = 1
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = value,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = valueColor,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * MiuixTag
 * (label: String, color: Color)
 * 作用：身份标签胶囊。
 * 备注：Compose 组件；仅内部使用
 */
@Composable
private fun MiuixTag(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(color)
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White
        )
    }
}

/**
 * MiuixCheckRing
 * (modifier: Modifier = Modifier, color: Color)
 * 作用：右下角圆环 + 对勾；开口留在右下，由卡片边缘裁切。
 * 备注：Compose 组件；仅内部使用
 */
@Composable
private fun MiuixCheckRing(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val stroke = 14.dp.toPx()
        drawArc(
            color = color,
            startAngle = 60f,
            sweepAngle = 300f,
            useCenter = false,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        val cx = size.width / 2f
        val cy = size.height / 2f
        val path = Path().apply {
            moveTo(cx - size.width * 0.21f, cy + size.height * 0.01f)
            lineTo(cx - size.width * 0.04f, cy + size.height * 0.17f)
            lineTo(cx + size.width * 0.25f, cy - size.height * 0.17f)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = stroke,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

/**
 * displayVersionName
 * ()
 * 作用：状态卡上显示的版本名，跟随「使用旧的兼容版本号」开关： 开启时与对外返回的兼容版本保持一致，关闭时显示本应用自身版本。
 * 备注：Compose 组件；仅内部使用
 */
@Composable
private fun displayVersionName(): String {
    val repo = koinInject<SettingsRepo>()
    val useLegacy by repo.flowUseLegacyVersion()
        .collectAsState(initial = repo.isUseLegacyVersion)
    return if (useLegacy) {
        com.rosan.dhizuku.server_api.Variables.SERVICE_VERSION_NAME
    } else {
        com.neodhizuku.more.BuildConfig.VERSION_NAME
    }
}

/**
 * StatusCard
 * (modifier: Modifier = Modifier, onClick: () -> Unit)
 * 作用：左侧状态卡：对勾图标 + 已激活 + 所有者类型 + 版本。
 * 备注：Compose 组件；仅内部使用
 */
@Composable
private fun StatusCard(modifier: Modifier = Modifier, onClick: () -> Unit) {
    val versionName = displayVersionName()
    NeoCard(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 描边对勾图标，颜色跟随卡片的 onSecondaryContainer
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = stringResource(R.string.home_status_owner_granted_short),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )

            Column {
                Text(
                    text = stringResource(R.string.home_status_owner_granted_short),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(Modifier.height(6.dp))
                StatusBadges(includeActivation = true)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(
                        R.string.home_status_version,
                        versionName
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            // 卡片被右侧两张小卡撑高时，多出的高度落在这里（下方留白），
            // 内容保持靠上，底端与「运行时间」卡对齐。
            Spacer(Modifier.weight(1f))
        }
    }
}

/**
 * activationLabel
 * ()
 * 作用：激活卡片上的身份标签，五种状态选一。 放在这里而不是另起一张卡：平板上首页右侧没有多余空间， 单独加卡会导致平板与手机布局不一致。
 * 备注：Compose 组件；仅内部使用
 */
@Composable
private fun activationLabel(): String {
    // 读取 displayState() 即订阅；调试覆盖打开时按伪装身份显示
    val state = DebugUiOverrides.displayState()
    val temporary =
        com.neodhizuku.more.data.common.util.DpDirectActivation.isTemporaryCached()

    // 免删模式取得的所有者加"临时"后缀，能力完全相同，只是区分获取途径
    if (state.isDeviceOwner && temporary) {
        return stringResource(
            R.string.capability_device_owner_temp,
            stringResource(R.string.capability_device_owner)
        )
    }

    val res = when (state.level) {
        com.neodhizuku.more.server.CapabilityLevel.DEVICE_OWNER ->
            R.string.capability_device_owner
        com.neodhizuku.more.server.CapabilityLevel.PROFILE_OWNER ->
            R.string.confirm_profile_owner
        com.neodhizuku.more.server.CapabilityLevel.DEVICE_ADMIN ->
            R.string.capability_device_admin
        com.neodhizuku.more.server.CapabilityLevel.NONE ->
            R.string.home_status_inactive
    }
    return stringResource(res)
}

/**
 * ServiceStatCard
 * (modifier: Modifier = Modifier, label: String, value: String, stopped: Boolean = false)
 * 作用：右侧小卡：一个标签 + 一个数值
 * 备注：Compose 组件；仅内部使用
 */
@Composable
private fun ServiceStatCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    stopped: Boolean = false
) {
    // 停止时整卡纯红，且只显示「拒绝服务」
    if (stopped) {
        NeoCard(
            modifier = modifier,
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFD32F2F),
                contentColor = Color.White
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = stringResource(R.string.home_service_stopped),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
        return
    }

    // 正常运行：保持默认卡片配色（不额外着色）
    NeoCard(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * StatusBadges
 * (includeActivation: Boolean)
 * 作用：状态卡上并排的纯色药丸：身份标签 + 模块框架。 模块框架只在真的有进程报到（或模块被加载过）时出现， 没有 LSPosed / LSPatch 的设备完全看不到它。 TEST ONLY 与设备状态无关，不在此显示。
 * 备注：Compose 组件；仅内部使用
 */
@Composable
private fun StatusBadges(includeActivation: Boolean) {
    val framework = hookFrameworkLabel()
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (includeActivation) StatusTag(label = activationLabel())
        if (framework != null) StatusTag(
            label = framework,
            color = MaterialTheme.colorScheme.tertiary
        )
    }
}

/**
 * hookFrameworkLabel
 * ()
 * 作用：每 2 秒读一次报到表；只存内存，重启即清空，与「启动后才生效」一致。
 * 备注：Compose 组件；仅内部使用
 */
@Composable
private fun hookFrameworkLabel(): String? {
    var label by remember { mutableStateOf(HookConfigStore.frameworkLabel()) }
    LaunchedEffect(Unit) {
        while (true) {
            label = HookConfigStore.frameworkLabel()
            delay(2000L)
        }
    }
    return label
}

/**
 * StatusTag
 * (label: String, color: Color? = null)
 * 作用：执行 StatusTag
 * 备注：Compose 组件；仅内部使用
 */
@Composable
private fun StatusTag(label: String, color: Color? = null) {
    androidx.compose.foundation.layout.Box(
        modifier = androidx.compose.ui.Modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
            .background(color ?: MaterialTheme.colorScheme.primary)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}

/**
 * SettingsWidget
 * (navController: NavController, onSelectTab: (String) -> Unit)
 * 作用：执行 SettingsWidget
 * 备注：Compose 组件；仅内部使用
 */
@Composable
private fun LazyItemScope.SettingsWidget(
    navController: NavController,
    onSelectTab: (String) -> Unit
) {
    CardWidget(onClick = {
        onSelectTab(SettingsRoute.Settings.route)
    }, icon = {
        Icon(imageVector = Icons.TwoTone.Settings, contentDescription = null)
    }, title = {
        Text(stringResource(R.string.settings))
    }, text = {
        Text(stringResource(R.string.settings_desc))
    })
}

/**
 * AppManagementWidget
 * (navController: NavController, onSelectTab: (String) -> Unit)
 * 作用：执行 AppManagementWidget
 * 备注：Compose 组件；仅内部使用
 */
@Composable
private fun LazyItemScope.AppManagementWidget(
    navController: NavController,
    onSelectTab: (String) -> Unit
) {
    CardWidget(onClick = {
        onSelectTab(SettingsRoute.AppManagement.route)
    }, icon = {
        Icon(imageVector = Icons.TwoTone.RoomPreferences, contentDescription = null)
    }, title = {
        Text(stringResource(R.string.home_app_management_title))
    }, text = {
        Text(stringResource(R.string.home_app_management_dsp))
    })
}

/**
 * DhizukuWidget
 * (navController: NavController)
 * 作用：执行 DhizukuWidget
 * 备注：Compose 组件；仅内部使用
 */
@Composable
private fun LazyItemScope.DhizukuWidget(navController: NavController) {
    CardWidget(onClick = {
        navController.navigate(SettingsRoute.Activate.route(SettingsRoute.Activate.Mode.Dhizuku))
    }, icon = {
        Icon(imageVector = Icons.TwoTone.SwapHorizontalCircle, contentDescription = null)
    }, title = {
        Text(stringResource(R.string.home_dhizuku_title))
    }, text = {
        HtmlText(stringResource(R.string.home_dhizuku_dsp, "https://github.com/iamr0s/Dhizuku"))
    })
}

/**
 * ShizukuWidget
 * (navController: NavController)
 * 作用：执行 ShizukuWidget
 * 备注：Compose 组件；仅内部使用
 */
@Composable
private fun LazyItemScope.ShizukuWidget(navController: NavController) {
    val context = LocalContext.current
    CardWidget(onClick = {
        // 检测不到 Shizuku 服务时仍进入激活界面：用户可能只是暂时没启动服务，
        // 直接甩到下载页既打断了流程，也无法在服务已装但未运行时给出引导。
        if (!checkShizukuWorked() && NeoDhizuku.on(NeoDhizuku.FixShizukuEntryFeedback)) {
            context.toast(R.string.home_shizuku_not_installed)
        }
        navController.navigate(SettingsRoute.Activate.route(SettingsRoute.Activate.Mode.Shizuku))
    }, icon = {
        Icon(imageVector = Icons.TwoTone.Terminal, contentDescription = null)
    }, title = {
        Text(stringResource(R.string.home_shizuku_title))
    }, text = {
        HtmlText(stringResource(R.string.home_shizuku_dsp, "https://shizuku.rikka.app/"))
    })
}

/**
 * QuickActivateWidget
 * (navController: NavController)
 * 作用：执行 QuickActivateWidget
 * 备注：Compose 组件；仅内部使用
 */
@Composable
private fun LazyItemScope.QuickActivateWidget(navController: NavController) {
    CardWidget(onClick = {
        navController.navigate(SettingsRoute.QuickActivate.route)
    }, icon = {
        Icon(imageVector = Icons.TwoTone.Bolt, contentDescription = null)
    }, title = {
        Text(stringResource(R.string.quick_activate_entry_title))
    }, text = {
        Text(stringResource(R.string.quick_activate_entry_dsp))
    })
}

/**
 * AdbWidget
 * ()
 * 作用：执行 AdbWidget
 * 备注：Compose 组件；使用了实验性 API；仅内部使用
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LazyItemScope.AdbWidget() {
    val deviceOwnerCommand =
        "adb shell dpm set-device-owner ${DhizukuState.admin.flattenToShortString()}"
    val profileOwnerCommand =
        "adb shell dpm set-profile-owner ${DhizukuState.admin.flattenToShortString()}"

    var commandDialogShow by remember { mutableStateOf(false) }
    var useProfileOwner by remember { mutableStateOf(false) }
    var ownerMenuExpanded by remember { mutableStateOf(false) }
    var profileOwnerWarningShow by remember { mutableStateOf(false) }

    CardWidget(icon = {
        Icon(imageVector = Icons.TwoTone.Adb, contentDescription = null)
    }, title = {
        Text(stringResource(R.string.home_adb_title))
    }, content = {
        HtmlText(
            stringResource(
                R.string.home_adb_dsp,
                "https://developer.android.com/tools/adb"
            )
        )
        TextButton(onClick = { commandDialogShow = true }) {
            Icon(imageVector = Icons.TwoTone.Code, contentDescription = null)
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.home_adb_btn_view_command))
        }
    })

    if (profileOwnerWarningShow) {
        AlertDialog(onDismissRequest = {
            profileOwnerWarningShow = false
        }, icon = {
            Icon(imageVector = Icons.TwoTone.Warning, contentDescription = null)
        }, confirmButton = {
            TextButton(onClick = {
                useProfileOwner = true
                profileOwnerWarningShow = false
            }) {
                Text(stringResource(R.string.confirm))
            }
        }, dismissButton = {
            TextButton(onClick = { profileOwnerWarningShow = false }) {
                Text(stringResource(R.string.cancel))
            }
        }, title = {
            Text(stringResource(R.string.profile_owner_warning_title))
        }, text = {
            Text(stringResource(R.string.profile_owner_warning_dsp))
        })
    }

    if (!commandDialogShow) return
    val command = if (useProfileOwner) profileOwnerCommand else deviceOwnerCommand
    AlertDialog(onDismissRequest = {
        commandDialogShow = false
    }, confirmButton = {
        TextButton(onClick = {
            commandDialogShow = false
        }) {
            Text(stringResource(R.string.cancel))
        }
        val manager = LocalClipboard.current
        val scope = rememberCoroutineScope()
        TextButton(onClick = {
            scope.launch {
                manager.setClipEntry(ClipEntry(ClipData.newPlainText("command", command)))
                commandDialogShow = false
            }
        }) {
            Text(stringResource(R.string.copy))
        }
    }, title = {
        Text(stringResource(R.string.home_adb_btn_view_command))
    }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box {
                OutlinedButton(onClick = { ownerMenuExpanded = true }) {
                    Text(
                        stringResource(
                            if (useProfileOwner) R.string.confirm_profile_owner
                            else R.string.confirm_device_owner
                        )
                    )
                    Icon(imageVector = Icons.TwoTone.ArrowDropDown, contentDescription = null)
                }
                NeoDropdownMenu(
                    expanded = ownerMenuExpanded,
                    onDismissRequest = { ownerMenuExpanded = false }
                ) {
                    NeoDropdownItem(
                        text = stringResource(R.string.confirm_device_owner),
                        selected = !useProfileOwner,
                        onClick = {
                            useProfileOwner = false
                            ownerMenuExpanded = false
                        }
                    )
                    NeoDropdownItem(
                        text = stringResource(R.string.confirm_profile_owner),
                        selected = useProfileOwner,
                        onClick = {
                            ownerMenuExpanded = false
                            profileOwnerWarningShow = true
                        }
                    )
                }
            }
            SelectionContainer {
                Text(command)
            }
        }
    })
}

/**
 * DeactivateWidget
 * ()
 * 作用：执行 DeactivateWidget
 * 备注：Compose 组件；仅内部使用
 */
@Composable
private fun LazyItemScope.DeactivateWidget() {
    val context = LocalContext.current
    val manager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    var state by remember {
        mutableStateOf(false)
    }

    // 仅设备管理员（没有所有者身份）时，"取消激活"要移除的是设备管理员，
    // 而不是所有者。
    // clearProfileOwner / clearDeviceOwnerApp 只对所有者有效，在设备管理员
    // 下调用必然抛异常，界面上就表现为一直提示"移除所有者失败"。
    val adminOnly = !DhizukuState.state.isOwner && DhizukuState.state.isDeviceAdmin

    CardWidget(onClick = {
        state = true
    }, icon = {
        Icon(imageVector = Icons.TwoTone.DoNotDisturbOn, contentDescription = null)
    }, title = {
        Text(stringResource(R.string.home_deactivate_title))
    })
    if (!state) return
    AlertDialog(onDismissRequest = {
        state = false
    }, confirmButton = {
        TextButton(onClick = {
            state = false
        }) {
            Text(stringResource(R.string.cancel))
        }
        TextButton(onClick = {
            if (NeoDhizuku.on(NeoDhizuku.FixDeactivateRefresh)) {
                // 成功后要刷新状态并停服务，否则界面仍显示已激活、通知残留
                val appContext = context.applicationContext
                // 身份在点击时重新判定：弹窗停留期间状态可能已经变了
                val current = DhizukuState.state

                // 成败不能只看"有没有抛异常"。
                // 移除所有者是系统侧的多步操作（清 owner 标记 → 撤销 admin → 通知各用户），
                // 权限由别的实现（Shizuku / 其他 Dhizuku 实现）写入时尤其常见：
                // 前几步已经生效、后面某一步回抛异常，于是表现为「提示失败、实际已经成功」。
                // 因此每一步各自吞掉异常，最后重新查询系统，用真实身份是否消失来判定。
                val thrown = ArrayList<String>()

                // 所有命中的分支都要跑：转移过来的权限可能同时残留
                // 资料所有者与设备所有者两种身份，只清一种会留下另一种。
                if (current.isProfileOwner) {
                    runCatching {
                        @Suppress("DEPRECATION")
                        manager.clearProfileOwner(DhizukuState.admin)
                    }.onFailure {
                        thrown.add("clearProfileOwner:${it.javaClass.simpleName}")
                    }
                }
                if (current.isDeviceOwner) {
                    runCatching {
                        @Suppress("DEPRECATION")
                        manager.clearDeviceOwnerApp(appContext.packageName)
                    }.onFailure {
                        thrown.add("clearDeviceOwnerApp:${it.javaClass.simpleName}")
                    }
                }
                if (current.isDeviceAdmin) {
                    runCatching {
                        @Suppress("DEPRECATION")
                        manager.removeActiveAdmin(DhizukuState.admin)
                    }.onFailure {
                        thrown.add("removeActiveAdmin:${it.javaClass.simpleName}")
                    }
                }
                runCatching {
                    appContext.stopService(Intent(appContext, DaemonService::class.java))
                }
                DhizukuState.sync(appContext)

                val after = DhizukuState.state
                val removed = if (current.isOwner) {
                    // 所有者：两种所有者身份都要消失
                    !after.isProfileOwner && !after.isDeviceOwner
                } else if (current.isDeviceAdmin) {
                    !after.isDeviceAdmin
                } else {
                    // 本来就没有身份，没有可移除的东西
                    true
                }
                if (!removed) {
                    context.toast(
                        if (!current.isOwner && current.isDeviceAdmin) R.string.home_deactivate_admin_failed
                        else R.string.home_deactivate_failed
                    )
                }
                NeoLog.i(NeoLog.HOME) {
                    "取消激活: profileOwner=${current.isProfileOwner} " +
                        "deviceOwner=${current.isDeviceOwner} " +
                        "deviceAdmin=${current.isDeviceAdmin} -> removed=$removed " +
                        "(after: profileOwner=${after.isProfileOwner} " +
                        "deviceOwner=${after.isDeviceOwner} " +
                        "deviceAdmin=${after.isDeviceAdmin}) thrown=$thrown"
                }
            } else {
                try {
                    @Suppress("DEPRECATION")
                    manager.clearProfileOwner(DhizukuState.admin)
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
                try {
                    @Suppress("DEPRECATION")
                    manager.clearDeviceOwnerApp(context.packageName)
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }
            state = false
        }) {
            Text(stringResource(R.string.confirm))
        }
    }, title = {
        Text(stringResource(R.string.home_deactivate_title))
    }, text = {
        Text(
            stringResource(
                if (adminOnly) R.string.home_deactivate_admin_dsp
                else R.string.home_deactivate_dsp
            )
        )
    })
}

/**
 * HtmlText
 * (text: String)
 * 作用：执行 HtmlText
 * 备注：Compose 组件；仅内部使用
 */
@Composable
private fun HtmlText(text: String) {
    Text(
        AnnotatedString.fromHtml(
            text,
            TextLinkStyles(
                SpanStyle(
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    textDecoration = TextDecoration.Underline
                )
            )
        )
    )
}

/**
 * CardWidget
 * (modifier: Modifier = Modifier, colors: CardColors = CardDefaults.elevatedCardColors(), onClick: () -> Unit = {}, icon: @Composable () -> Unit, iconColors: IconButtonColors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer), title: @Composable () -> Unit, text: (@Composable () -> Unit)? = null, content: (@Composable () -> Unit)? = null)
 * 作用：执行 CardWidget
 * 备注：Compose 组件；仅内部使用
 */
@Composable
private fun LazyItemScope.CardWidget(
    modifier: Modifier = Modifier,
    colors: CardColors = CardDefaults.elevatedCardColors(),
    onClick: () -> Unit = {},
    icon: @Composable () -> Unit,
    iconColors: IconButtonColors = IconButtonDefaults.iconButtonColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer
    ),
    title: @Composable () -> Unit,
    text: (@Composable () -> Unit)? = null,
    content: (@Composable () -> Unit)? = null
) {
    NeoCard(
        modifier = modifier.animateItem(),
        colors = colors, onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(40.dp),
                    color = iconColors.containerColor,
                    contentColor = iconColors.contentColor
                ) {
                    Box(Modifier.padding(8.dp)) {
                        icon.invoke()
                    }
                }
                Column {
                    ProvideTextStyle(MaterialTheme.typography.titleMedium) {
                        title.invoke()
                    }
                    ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                        @Suppress("AnimatedContentLabel")
                        AnimatedContent(targetState = text) {
                            it?.invoke()
                        }
                    }
                }
            }
            ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                content?.invoke()
            }
        }
    }
}