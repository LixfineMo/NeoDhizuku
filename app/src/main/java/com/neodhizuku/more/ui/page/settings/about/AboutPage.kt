package com.neodhizuku.more.ui.page.settings.about

import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.automirrored.twotone.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import com.neodhizuku.more.server.DhizukuState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.neodhizuku.more.BuildConfig
import com.neodhizuku.more.R
import com.neodhizuku.more.data.common.util.UpdateLinks
import com.neodhizuku.more.ui.page.settings.SettingsRoute
import com.neodhizuku.more.data.common.util.openUrlInBrowser
import com.neodhizuku.more.data.common.util.toast
import com.neodhizuku.more.server.ServiceStatus
import org.koin.androidx.compose.koinViewModel

/** 关于页：顶部动态图标、两张信息卡、底部外链。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutPage(
    windowInsets: WindowInsets,
    navController: NavController
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            Icons.AutoMirrored.TwoTone.ArrowBack,
                            contentDescription = null
                        )
                    }
                },
                windowInsets = windowInsets.only(WindowInsetsSides.Top)
            )
        },
        // 让出系统栏，否则内容会被状态栏与手势条遮住
        modifier = Modifier.windowInsetsPadding(
            windowInsets.only(WindowInsetsSides.Bottom)
        )
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                // 与 Scaffold 传回的内边距叠加，避免依赖 PaddingValues.plus 扩展
                .padding(it)
                .padding(PaddingValues(16.dp)),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item("about_icon") {
                var taps by remember { mutableStateOf(0) }
                var lastTapAt by remember { mutableLongStateOf(0L) }
                WaveAppIcon(
                    onTap = {
                        val now = System.currentTimeMillis()
                        if (now - lastTapAt > 2_000L) taps = 0
                        lastTapAt = now
                        taps += 1
                        if (taps >= 5) {
                            taps = 0
                            navController.navigate(SettingsRoute.Debug.route)
                        }
                    }
                )
            }

            item("about_cards") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    VersionCard(modifier = Modifier.weight(1f))
                    DetailCard(modifier = Modifier.weight(1f))
                }
            }

            item("about_links") {
                LinkRow()
            }

            item("about_credits") {
                CreditsCard()
            }

            item("about_update") {
                UpdateCard()
            }

            item("about_feedback") {
                FeedbackCard()
            }

            item("about_admin_residue") {
                AdminResidueCard(navController)
            }
        }
    }
}

/**
 * 设备身份状态卡：显示系统报告的三项身份，并提供强制清除入口。
 *
 * 存在的理由：把设备所有者转移给别的应用之后，部分机型不会顺带卸掉
 * 本应用的设备管理员身份，于是出现"设置里显示未激活、卸载却报设备管理
 * 器阻止"的错位状态。此时 [DhizukuState.state] 三项往往全为 false，
 * 按状态判断会以为"没有可清理的东西"，因此这张卡无条件显示，
 * 并且清除动作不看状态、直接调清理接口。
 */
@Composable
private fun AdminResidueCard(navController: NavController) {
    val context = LocalContext.current
    var result by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val state = DhizukuState.state

    LaunchedEffect(Unit) { runCatching { DhizukuState.sync(context) } }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                stringResource(R.string.about_admin_residue_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                stringResource(R.string.about_admin_residue_dsp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                stringResource(
                    R.string.about_admin_residue_state,
                    state.isDeviceOwner.toString(),
                    state.isProfileOwner.toString(),
                    state.isDeviceAdmin.toString()
                ),
                style = MaterialTheme.typography.bodySmall
            )
            result?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { confirmClear = true }) {
                Text(stringResource(R.string.about_admin_residue_clean))
            }
            TextButton(onClick = { navController.navigate(SettingsRoute.OwnerIdentity.route) }) {
                Text(stringResource(R.string.owner_identity_entry))
            }
        }
    }

    // 清除动作不看当前状态、直接调三个清理接口，属于不可逆操作，必须二次确认。
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.about_admin_residue_confirm_title)) },
            text = { Text(stringResource(R.string.about_admin_residue_confirm_desc)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    val r = runCatching { DhizukuState.forceCleanup(context) }
                        .getOrDefault(emptyMap())
                    runCatching { DhizukuState.sync(context) }
                    result = if (r.isEmpty()) {
                        context.getString(R.string.about_admin_residue_failed)
                    } else {
                        r.entries.joinToString("  ") {
                            "${it.key}=${if (it.value) "ok" else "fail"}"
                        }
                    }
                }) {
                    Text(stringResource(R.string.about_admin_residue_confirm_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

/**
 * 底部反馈卡：点开弹窗填表，发到更新配置里那一台服务器。
 *
 * 整张卡片就是一个点击区，不再内嵌按钮 —— 一行标题加一行说明已经够长，
 * 再放一个按钮会把它挤成三行。
 */
@Composable
private fun FeedbackCard(viewModel: AboutViewModel = koinViewModel()) {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.collect()
    }

    // 提交结束后 toast 一次，成功关窗、失败留着让用户改
    LaunchedEffect(viewModel.feedbackState) {
        when (val state = viewModel.feedbackState) {
            is FeedbackState.Success -> {
                context.toast(R.string.feedback_success_toast)
                showDialog = false
                viewModel.resetFeedback()
            }

            is FeedbackState.Empty -> {
                context.toast(R.string.feedback_empty)
                viewModel.resetFeedback()
            }

            is FeedbackState.Failed -> {
                context.toast(
                    state.message?.takeIf { it.isNotBlank() }
                        ?: context.getString(R.string.feedback_failed)
                )
                viewModel.resetFeedback()
            }

            else -> Unit
        }
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { showDialog = true }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.TwoTone.Send,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.about_feedback_title),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    stringResource(R.string.about_feedback_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showDialog) {
        FeedbackDialog(
            sending = viewModel.feedbackState is FeedbackState.Sending,
            onDismiss = { showDialog = false },
            onSend = { title, content, contact ->
                viewModel.submitFeedback(title, content, contact)
            }
        )
    }
}

/** 反馈弹窗：标题、内容、联系方式（可选）。 */
@Composable
private fun FeedbackDialog(
    sending: Boolean,
    onDismiss: () -> Unit,
    onSend: (String, String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        title = { Text(stringResource(R.string.feedback_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.feedback_field_title)) },
                    singleLine = true,
                    enabled = !sending,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text(stringResource(R.string.feedback_field_content)) },
                    minLines = 4,
                    maxLines = 8,
                    enabled = !sending,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = contact,
                    onValueChange = { contact = it },
                    label = { Text(stringResource(R.string.feedback_field_contact)) },
                    singleLine = true,
                    enabled = !sending,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSend(title, content, contact) },
                enabled = !sending
            ) {
                Text(
                    stringResource(
                        if (sending) R.string.feedback_sending else R.string.feedback_send
                    )
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !sending) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/** 底部更新卡：开关、检查按钮与结果提示。 */
@Composable
private fun UpdateCard(viewModel: AboutViewModel = koinViewModel()) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.collect()
    }

    val checking = viewModel.updateState is UpdateState.Checking

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                stringResource(R.string.update_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            InfoRow(
                label = stringResource(R.string.update_current),
                value = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.update_gray_switch),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        stringResource(R.string.update_gray_switch_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = viewModel.allowPreRelease,
                    onCheckedChange = { viewModel.updateAllowPreRelease(it) }
                )
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 结果提示占左侧，避免和按钮挤在一起
                Box(modifier = Modifier.weight(1f)) {
                    when (val state = viewModel.updateState) {
                        is UpdateState.Checking ->
                            Text(
                                stringResource(R.string.update_checking),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                        is UpdateState.UpToDate ->
                            Text(
                                stringResource(R.string.update_no_update),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                        is UpdateState.Failed ->
                            Text(
                                stringResource(R.string.update_failed),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )

                        else -> Unit
                    }
                }
                TextButton(
                    onClick = { viewModel.check() },
                    enabled = !checking
                ) {
                    Text(stringResource(R.string.update_check))
                }
            }
        }
    }

    when (val state = viewModel.updateState) {
        is UpdateState.Available ->
            UpdateDialog(
                title = stringResource(R.string.update_found_title),
                version = state.info.version,
                changelog = state.changelog,
                confirmLabel = stringResource(R.string.update_download),
                onDismiss = { viewModel.dismissUpdate() },
                onDownload = {
                    // 清单里没给地址时退回仓库主页，总比点了没反应好
                    val url = state.info.downloadUrl.ifBlank {
                        UpdateLinks.REPO_HOME
                    }
                    context.openUrlInBrowser(url)
                }
            )

        is UpdateState.PreReleaseBlocked ->
            UpdateDialog(
                title = stringResource(R.string.update_prerelease_title),
                version = state.info.version,
                changelog = stringResource(R.string.update_prerelease_dsp),
                confirmLabel = stringResource(R.string.update_open_repo),
                onDismiss = { viewModel.dismissUpdate() },
                onDownload = {
                    context.openUrlInBrowser(
                        UpdateLinks.REPO_HOME
                    )
                }
            )

        else -> Unit
    }
}

@Composable
private fun UpdateDialog(
    title: String,
    version: String,
    changelog: String,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    confirmLabel: String
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    version,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                if (changelog.isNotBlank()) {
                    Text(
                        changelog,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDownload) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}


/** 左卡：版本相关 */
@Composable
private fun VersionCard(modifier: Modifier = Modifier) {
    InfoCard(
        modifier = modifier,
        title = stringResource(R.string.about_card_version_title)
    ) {
        InfoRow(
            label = stringResource(R.string.about_version_name),
            value = BuildConfig.VERSION_NAME
        )
        InfoRow(
            label = stringResource(R.string.about_version_code),
            value = BuildConfig.VERSION_CODE.toString()
        )
        InfoRow(
            label = stringResource(R.string.about_build_type),
            value = if (BuildConfig.DEBUG) {
                stringResource(R.string.about_build_type_debug)
            } else {
                stringResource(R.string.about_build_type_release)
            }
        )
        InfoRow(
            label = stringResource(R.string.about_package_name),
            value = BuildConfig.APPLICATION_ID
        )
    }
}

/** 右卡：设备与运行状态 */
@Composable
private fun DetailCard(modifier: Modifier = Modifier) {
    val activation = stringResource(
        when {
            DhizukuState.state.isDeviceOwner -> R.string.confirm_device_owner
            DhizukuState.state.isProfileOwner -> R.string.confirm_profile_owner
            else -> R.string.about_not_activated
        }
    )

    InfoCard(
        modifier = modifier,
        title = stringResource(R.string.about_card_detail_title)
    ) {
        InfoRow(
            label = stringResource(R.string.about_android_version),
            value = Build.VERSION.RELEASE
        )
        InfoRow(
            label = stringResource(R.string.about_api_level),
            value = Build.VERSION.SDK_INT.toString()
        )
        InfoRow(
            label = stringResource(R.string.about_device),
            value = "${Build.MANUFACTURER} ${Build.MODEL}"
        )
        InfoRow(
            label = stringResource(R.string.about_activation),
            value = activation
        )
        InfoRow(
            label = stringResource(R.string.about_service_state),
            value = stringResource(
                if (ServiceStatus.isRunning()) R.string.about_service_running
                else R.string.about_service_stopped
            )
        )
    }
}

@Composable
private fun InfoCard(
    modifier: Modifier = Modifier,
    title: String,
    content: @Composable () -> Unit
) {
    ElevatedCard(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/** 底部外链：GitHub、QQ 反馈群与 Telegram 群 */
@Composable
private fun LinkRow() {
    val context = LocalContext.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        if (AboutLinks.GITHUB.isNotEmpty()) {
            LinkButton(
                painter = painterResource(R.drawable.ic_github),
                label = stringResource(R.string.about_github),
                // 单色图标，跟随主题前景色
                tint = MaterialTheme.colorScheme.onSurface,
                onClick = { context.openUrlInBrowser(AboutLinks.GITHUB) }
            )
        }
        if (AboutLinks.QQ_GROUP.isNotEmpty()) {
            Spacer(Modifier.width(24.dp))
            LinkButton(
                painter = null,
                letter = "Q",
                label = stringResource(R.string.about_qq_group),
                onClick = { context.openUrlInBrowser(AboutLinks.QQ_GROUP) }
            )
        }
        if (AboutLinks.TELEGRAM.isNotEmpty()) {
            Spacer(Modifier.width(24.dp))
            LinkButton(
                painter = null,
                letter = "T",
                label = stringResource(R.string.about_telegram),
                onClick = { context.openUrlInBrowser(AboutLinks.TELEGRAM) }
            )
        }
    }
}

@Composable
private fun LinkButton(
    painter: androidx.compose.ui.graphics.painter.Painter? = null,
    letter: String? = null,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHighest,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (letter != null) {
                    // 群图标不跟品牌走：单色字母，跟随前景色，日间/夜间都只有黑白两态
                    Text(
                        letter,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                } else if (painter != null) {
                    Icon(
                        painter = painter,
                        contentDescription = label,
                        tint = tint,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}
