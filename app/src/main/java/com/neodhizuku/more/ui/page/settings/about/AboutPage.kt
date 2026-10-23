package com.neodhizuku.more.ui.page.settings.about

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.neodhizuku.more.BuildConfig
import com.neodhizuku.more.R
import com.neodhizuku.more.data.common.util.UpdateLinks
import com.neodhizuku.more.data.common.util.openUrlInBrowser
import com.neodhizuku.more.server.DhizukuState
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
                AnimatedAppIcon()
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

            item("about_update") {
                UpdateCard()
            }
        }
    }
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
                    val url = state.info.apkUrl.ifBlank {
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

/**
 * 顶部图标：应用图标 + 缓慢旋转的渐变环。
 *
 * 环用 sweepGradient 配 infiniteRepeatable 旋转，随主题色变化，
 * 静止状态下也能看出"活着"。
 */
@Composable
private fun AnimatedAppIcon() {
    val context = LocalContext.current
    val icon = remember(context) { loadSelfIcon(context) }

    val infiniteTransition = rememberInfiniteTransition(label = "about_icon_rotation")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "about_icon_angle"
    )

    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary

    Box(
        modifier = Modifier.size(140.dp),
        contentAlignment = Alignment.Center
    ) {
        // 旋转的渐变环
        Box(
            modifier = Modifier
                .size(128.dp)
                .rotate(angle)
                .border(
                    width = 3.dp,
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            primary,
                            tertiary,
                            primary.copy(alpha = 0.15f),
                            primary
                        )
                    ),
                    shape = CircleShape
                )
        )

        // 应用图标本身
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(104.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (icon != null) {
                    androidx.compose.foundation.Image(
                        painter = rememberDrawablePainter(drawable = icon),
                        contentDescription = null,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Text(
                        text = "N",
                        style = MaterialTheme.typography.displaySmall,
                        color = primary
                    )
                }
            }
        }
    }
}

private fun loadSelfIcon(context: Context) = try {
    context.packageManager.getApplicationIcon(BuildConfig.APPLICATION_ID)
} catch (_: PackageManager.NameNotFoundException) {
    null
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

/** 底部外链：GitHub 与 QQ 反馈群 */
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
                onClick = { context.openUrlInBrowser(AboutLinks.GITHUB) }
            )
            Spacer(Modifier.width(24.dp))
        }
        if (AboutLinks.QQ_GROUP.isNotEmpty()) {
            LinkButton(
                painter = painterResource(R.drawable.ic_qq),
                label = stringResource(R.string.about_qq_group),
                onClick = { context.openUrlInBrowser(AboutLinks.QQ_GROUP) }
            )
        }
    }
}

@Composable
private fun LinkButton(
    painter: androidx.compose.ui.graphics.painter.Painter,
    label: String,
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
                Icon(
                    painter = painter,
                    contentDescription = label,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}
