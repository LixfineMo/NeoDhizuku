package com.neodhizuku.more.ui.page.settings.activate

import android.content.ComponentName

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.ArrowDropDown
import androidx.compose.material.icons.twotone.Check
import androidx.compose.material.icons.twotone.Shield
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

import com.neodhizuku.more.NeoDhizuku
import com.neodhizuku.more.R
import com.neodhizuku.more.NeoLog
import com.neodhizuku.more.server.DhizukuState
import com.neodhizuku.more.ui.page.settings.SettingsRoute
import com.neodhizuku.more.ui.theme.AppIconCache
import com.neodhizuku.more.ui.theme.exclude
import com.neodhizuku.more.ui.theme.plus

import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivatePage(
    windowInsets: WindowInsets,
    navController: NavController,
    mode: SettingsRoute.Activate.Mode,
    viewModel: ActivateViewModel = koinViewModel()
) {
    LaunchedEffect(Unit) {
        viewModel.collect()
    }

    val compState = remember {
        mutableStateOf<ComponentName?>(null)
    }

    LaunchedEffect(viewModel.state.data) {
        compState.value = viewModel.state.data.find { it.enabled }?.admin?.component
    }

    val title = stringResource(
        when (mode) {
            SettingsRoute.Activate.Mode.Dhizuku -> R.string.home_dhizuku_title
            SettingsRoute.Activate.Mode.Shizuku -> R.string.home_shizuku_title
        }
    )

    Scaffold(
        modifier = Modifier
            .windowInsetsPadding(windowInsets.exclude(WindowInsetsSides.Bottom))
            .fillMaxSize(),
        // 外层 NavHost 已应用 Scaffold 的 padding（含系统 inset 与底栏高度），
        // 这里再处理一次 Bottom 会导致列表底部多出一块空白。
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = {
                        navController.navigateUp()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.TwoTone.ArrowBack,
                            contentDescription = null
                        )
                    }
                },
                title = {
                    Text(title)
                }
            )
        },
        bottomBar = {
            val comp by compState
            AnimatedVisibility(
                visible = comp != null,
                enter = scaleIn(),
                exit = scaleOut()
            ) {
                if (mode == SettingsRoute.Activate.Mode.Shizuku) {
                    var ownerType by remember { mutableStateOf(ActivateViewModel.OwnerType.Device) }
                    var menuExpanded by remember { mutableStateOf(false) }
                    var profileOwnerWarningShow by remember { mutableStateOf(false) }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box {
                            OutlinedButton(onClick = { menuExpanded = true }) {
                                Text(
                                    stringResource(
                                        when (ownerType) {
                                            ActivateViewModel.OwnerType.Profile -> R.string.confirm_profile_owner
                                            else -> R.string.confirm_device_owner
                                        }
                                    )
                                )
                                Icon(imageVector = Icons.TwoTone.ArrowDropDown, contentDescription = null)
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.confirm_device_owner)) },
                                    onClick = {
                                        ownerType = ActivateViewModel.OwnerType.Device
                                        menuExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.confirm_profile_owner)) },
                                    onClick = {
                                        menuExpanded = false
                                        profileOwnerWarningShow = true
                                    }
                                )
                            }
                        }

                        if (profileOwnerWarningShow) {
                            AlertDialog(
                                onDismissRequest = { profileOwnerWarningShow = false },
                                icon = {
                                    Icon(
                                        imageVector = Icons.TwoTone.Warning,
                                        contentDescription = null
                                    )
                                },
                                title = {
                                    Text(stringResource(R.string.profile_owner_warning_title))
                                },
                                text = {
                                    Text(stringResource(R.string.profile_owner_warning_dsp))
                                },
                                confirmButton = {
                                    TextButton(onClick = {
                                        ownerType = ActivateViewModel.OwnerType.Profile
                                        profileOwnerWarningShow = false
                                    }) {
                                        Text(stringResource(R.string.confirm))
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { profileOwnerWarningShow = false }) {
                                        Text(stringResource(R.string.cancel))
                                    }
                                }
                            )
                        }

                        ActivateFab(
                            textRes = R.string.confirm,
                            onClick = {
                                comp?.let {
                                    when (ownerType) {
                                        ActivateViewModel.OwnerType.Profile ->
                                            viewModel.activateAsProfileOwner(mode = mode, comp = it)

                                        else ->
                                            viewModel.activateAsDeviceOwner(mode = mode, comp = it)
                                    }
                                }
                            }
                        )
                    }
                } else {
                    ActivateFab(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(16.dp),
                        textRes = R.string.confirm,
                        onClick = {
                            comp?.let {
                                if (DhizukuState.state.isProfileOwner)
                                    viewModel.activateAsProfileOwner(mode = mode, comp = it)
                                else
                                    viewModel.activateAsDeviceOwner(mode = mode, comp = it)
                            }
                        }
                    )
                }
            }
        }) {
        val pullToRefreshState = rememberPullToRefreshState()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pullToRefresh(state = pullToRefreshState,
                    isRefreshing = viewModel.state.loading,
                    onRefresh = {
                        viewModel.collectData()
                    }
                )
        ) {
            ItemsWidget(viewModel = viewModel, contentPadding = it, state = compState)
            PullToRefreshDefaults.Indicator(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(it),
                state = pullToRefreshState,
                isRefreshing = viewModel.state.loading
            )
        }
    }

    if (viewModel.state.status == ActivateViewState.Status.Waiting) return
    AlertDialog(onDismissRequest = {
        viewModel.cancel()
    }, confirmButton = {
        val comp by compState
        val isRunning = viewModel.state.status !is ActivateViewState.Status.End
        val isSuccess =
            !isRunning && (viewModel.state.status as ActivateViewState.Status.End).error == null

        if (isRunning || !isSuccess) TextButton(onClick = { viewModel.cancel() }) {
            Text(stringResource(R.string.cancel))
        }
        if (isRunning) return@AlertDialog
        if (!isSuccess) TextButton(onClick = {
            if (NeoDhizuku.on(NeoDhizuku.FixActivateRetryNpe)) {
                // 列表刷新后 comp 可能为 null；
                // comp 是委托属性，无法智能转换为非空，这里先取局部快照。
                val compSnapshot = comp
                if (compSnapshot == null) viewModel.collectData()
                else when (viewModel.ownerType) {
                    ActivateViewModel.OwnerType.Profile ->
                        viewModel.activateAsProfileOwner(mode = mode, comp = compSnapshot)

                    else ->
                        viewModel.activateAsDeviceOwner(mode = mode, comp = compSnapshot)
                }
            } else {
                when (viewModel.ownerType) {
                    ActivateViewModel.OwnerType.Profile ->
                        viewModel.activateAsProfileOwner(mode = mode, comp = comp!!)

                    else ->
                        viewModel.activateAsDeviceOwner(mode = mode, comp = comp!!)
                }
            }
        }) {
            Text(stringResource(R.string.retry))
        } else TextButton(onClick = { navController.navigateUp() }) {
            Text(stringResource(R.string.finish))
        }
    }, title = {
        Text(title)
    }, text = {
        @Suppress("AnimatedContentLabel")
        AnimatedContent(
            viewModel.state.status,
            modifier = Modifier.verticalScroll(rememberScrollState()),
            label = "activate_status"
        ) {
            if (it == ActivateViewState.Status.Running) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            else if (it is ActivateViewState.Status.End) {
                val err = it.error
                if (err == null) {
                    Text(
                        stringResource(
                            when (mode) {
                                SettingsRoute.Activate.Mode.Dhizuku -> R.string.home_status_owner_transferred
                                SettingsRoute.Activate.Mode.Shizuku -> R.string.home_status_owner_granted
                            },
                            stringResource(
                                when (viewModel.ownerType) {
                                    ActivateViewModel.OwnerType.Profile -> R.string.confirm_profile_owner
                                    else -> R.string.confirm_device_owner
                                }
                            )
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else if (NeoDhizuku.on(NeoDhizuku.FixActivateErrorMapping)) {
                    // ActivateErrorText 是 @Composable，不能作为 Text() 的字符串实参
                    ActivateErrorText(error = err)
                } else {
                    Text(err.toString(), modifier = Modifier.fillMaxWidth())
                }
            }
        }
    })
}

@Composable
private fun ActivateFab(
    modifier: Modifier = Modifier,
    textRes: Int,
    onClick: () -> Unit
) {
    ExtendedFloatingActionButton(
        modifier = modifier,
        icon = {
            Icon(
                imageVector = Icons.TwoTone.Check,
                contentDescription = null
            )
        },
        text = {
            Text(stringResource(textRes))
        },
        onClick = onClick
    )
}

@Composable
private fun ItemsWidget(
    viewModel: ActivateViewModel,
    contentPadding: PaddingValues,
    state: MutableState<ComponentName?>
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp) + contentPadding,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 设备管理员入口：放在最上面，因为它门槛最低、全版本可用。
        // 只有在还没成为所有者时才显示 —— 已经是所有者就没必要再降级。
        if (!DhizukuState.state.isOwner) {
            item(key = "device_admin_entry") {
                DeviceAdminEntry()
            }
        }

        items(viewModel.state.data, key = { it.admin.component.flattenToShortString() }) {
            ItemWidget(
                viewModel = viewModel,
                data = it,
                state = state
            )
        }
    }
}

@Composable
private fun LazyItemScope.ItemWidget(
    viewModel: ActivateViewModel,
    data: ActivateViewData,
    state: MutableState<ComponentName?>
) {
    val interactionSource = remember { MutableInteractionSource() }

    fun setCheckedChange(bool: Boolean = state.value?.compareTo(data.admin.component) != 0) {
        state.value = if (bool) data.admin.component
        else null
    }

    ElevatedCard(
        onClick = ::setCheckedChange,
        interactionSource = interactionSource,
        modifier = Modifier
            .animateItem()
            .fillMaxSize()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = state.value?.compareTo(data.admin.component) == 0,
                onCheckedChange = ::setCheckedChange,
                interactionSource = interactionSource
            )

            val packageManager = LocalContext.current.packageManager
            val imageBitmap by AppIconCache.rememberImageBitmapState(data.admin)
            val label = data.admin.loadLabel(packageManager).toString()
            val text = data.admin.component.flattenToShortString()
            Image(
                bitmap = imageBitmap,
                modifier = Modifier
                    .size(40.dp)
                    .align(Alignment.CenterVertically),
                contentDescription = null
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleMedium)
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * 把异常翻译成中文原因 + 建议，并提供"查看技术详情"折叠区与复制诊断信息。
 */
@Composable
private fun ActivateErrorText(error: Throwable?) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activationError = ActivationError.from(error)

    var detailExpanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(activationError.titleRes), style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(activationError.adviceRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { detailExpanded = !detailExpanded }) {
                Text(stringResource(R.string.activate_err_view_detail))
            }
            TextButton(onClick = {
                // 使用系统剪贴板，避免依赖特定 Compose 版本的 ClipEntry API
                val manager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                        as? android.content.ClipboardManager
                manager?.setPrimaryClip(
                    android.content.ClipData.newPlainText(
                        "diagnostic",
                        ActivationError.diagnostic(context, error)
                    )
                )
            }) {
                Text(stringResource(R.string.copy))
            }
        }
        if (detailExpanded) {
            Text(
                error?.stackTraceToString() ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 以设备管理员身份激活。
 *
 * 设备所有者门槛高（ADB / Shizuku / 清账号 / Android 13+），
 * 而设备管理员只需系统设置里点一下，**全版本可用**。
 * 代价是能力有限，但"能跑一部分"好过"完全用不了"。
 *
 * 这里直接发起系统授权页；若设备上找不到该页面（少见），
 * 回退到打开设备管理器列表让用户手动勾选。
 */
@Composable
private fun DeviceAdminEntry() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isAdmin = DhizukuState.state.isDeviceAdmin

    ElevatedCard(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.TwoTone.AdminPanelSettingsIconSafe(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.activate_device_admin_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        stringResource(R.string.activate_device_admin_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isAdmin) {
                Text(
                    stringResource(R.string.activate_device_admin_already),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    androidx.compose.material3.FilledTonalButton(
                        onClick = { requestDeviceAdmin(context) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.activate_device_admin_action))
                    }
                    androidx.compose.material3.OutlinedButton(
                        onClick = { openDeviceAdminSettings(context) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.activate_device_admin_settings))
                    }
                }
            }
        }
    }
}

/** 图标按项目既有习惯统一取：没有先例的符号会编译不过，这里用已验证存在的 */
private val deviceAdminIcon = androidx.compose.material.icons.Icons.TwoTone.Shield

private fun Icons.TwoTone.AdminPanelSettingsIconSafe() = deviceAdminIcon

private fun requestDeviceAdmin(context: android.content.Context) {
    val admin = DhizukuState.admin
    val intent = android.content.Intent(
        android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN
    )
        .putExtra(android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
        .putExtra(
            android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION,
            context.getString(R.string.activate_device_admin_explanation)
        )
        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching {
        context.startActivity(intent)
        NeoLog.i(NeoLog.ACTIVATE) { "设备管理员：已发起系统授权页" }
    }.onFailure {
        NeoLog.w(NeoLog.ACTIVATE) { "设备管理员：发起授权页失败，回退到设置 ${it.message}" }
        openDeviceAdminSettings(context)
    }
}

private fun openDeviceAdminSettings(context: android.content.Context) {
    runCatching {
        context.startActivity(
            android.content.Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        NeoLog.i(NeoLog.ACTIVATE) { "设备管理员：已打开系统安全设置" }
    }.onFailure {
        NeoLog.w(NeoLog.ACTIVATE) { "设备管理员：打开设置失败 ${it.message}" }
    }
}
