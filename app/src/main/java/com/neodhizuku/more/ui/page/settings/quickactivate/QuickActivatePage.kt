package com.neodhizuku.more.ui.page.settings.quickactivate

import android.content.ClipData

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.Check
import androidx.compose.material.icons.twotone.Code
import androidx.compose.material.icons.twotone.Warning
import com.neodhizuku.more.ui.component.NeoAlertDialog as AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import com.neodhizuku.more.ui.component.NeoCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

import com.neodhizuku.more.NeoLog
import com.neodhizuku.more.R
import com.neodhizuku.more.data.common.util.DpDirectActivation
import com.neodhizuku.more.data.common.util.ShizukuShell
import com.neodhizuku.more.data.common.util.requireShizukuPermissionGranted
import com.neodhizuku.more.data.common.util.toast
import com.neodhizuku.more.server.DhizukuState
import com.neodhizuku.more.ui.theme.exclude

import kotlinx.coroutines.launch

/**
 * QuickActivatePage
 * (windowInsets: WindowInsets, navController: NavController)
 * 作用：执行 QuickActivatePage
 * 备注：Compose 组件；使用了实验性 API
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickActivatePage(
    windowInsets: WindowInsets,
    navController: NavController
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var running by remember { mutableStateOf(false) }
    var resultText by remember { mutableStateOf<String?>(null) }
    var resultOk by remember { mutableStateOf(false) }
    var commandDialogShow by remember { mutableStateOf(false) }

    // 向导被置 0 后是否还没恢复。为 true 时只允许「手动恢复」。
    var pendingRestore by remember {
        mutableStateOf(DpDirectActivation.isPendingRestore(context))
    }

    // 进入页面时用真实值校正一次：标记可能因进程被杀而与实际情况不符
    LaunchedEffect(Unit) {
        val actual = runCatching {
            requireShizukuPermissionGranted {
                val out = ShizukuShell.exec(
                    DpDirectActivation.buildReadSetupCompleteScript()
                )
                DpDirectActivation.parseSetupComplete(out.output)
            }
        }.getOrNull()
        if (actual != null) {
            val pending = actual != 1
            pendingRestore = pending
            DpDirectActivation.markPendingRestore(context, pending)
        }
    }

    // 三个步骤的执行骨架完全相同，抽出来避免重复三遍。
    // 显式声明为函数类型变量，避免作为参数传递时类型推断失败。
    val runStep: (String, suspend () -> DpDirectActivation.StepResult) -> Unit = { tag, action ->
        // 开启实验性修复时立即置位；未开启保持原行为，便于对照。
        if (DpDirectActivation.isFixEnabled()) running = true
        scope.launch {
            running = true
            val res: DpDirectActivation.StepResult = runCatching {
                requireShizukuPermissionGranted { action() }
            }.getOrElse {
                DpDirectActivation.StepResult(false, "执行失败", it.message ?: "")
            }
            running = false
            resultOk = res.success
            resultText = if (res.detail.isBlank()) res.message
            else "${res.message}\n\n${res.detail}"
            if (res.success && tag == "activate") {
                context.toast(R.string.quick_activate_success)
            }
            NeoLog.i(NeoLog.ACTIVATE) { "$tag：$resultOk ${res.message}" }
        }
    }

    Scaffold(
        modifier = Modifier
            .windowInsetsPadding(windowInsets.exclude(WindowInsetsSides.Bottom))
            .fillMaxSize(),
        // 外层 NavHost 已应用 padding，这里再处理 Bottom 会多出一块空白
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.TwoTone.ArrowBack,
                            contentDescription = null
                        )
                    }
                },
                title = {
                    Text(stringResource(R.string.quick_activate_title))
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            WarningCard()

            if (!DpDirectActivation.isSupported()) {
                UnsupportedCard()
                return@Column
            }

            if (pendingRestore) {
                Text(
                    text = stringResource(R.string.quick_activate_pending_restore),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (running) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            StepCard(
                index = 1,
                title = stringResource(R.string.quick_activate_step_open_title),
                description = stringResource(R.string.quick_activate_step_open_dsp),
                buttonText = stringResource(R.string.quick_activate_step_open_btn),
                enabled = !running && !pendingRestore,
                onClick = {
                    runStep("openGate") { DpDirectActivation.openSetupGate(context) }
                }
            )

            StepCard(
                index = 2,
                title = stringResource(R.string.quick_activate_step_activate_title),
                description = stringResource(R.string.quick_activate_step_activate_dsp),
                buttonText = stringResource(R.string.quick_activate_step_activate_btn),
                enabled = !running && !pendingRestore,
                onClick = {
                    runStep("activate") { DpDirectActivation.activate(context) }
                }
            )

            StepCard(
                index = 3,
                title = stringResource(R.string.quick_activate_step_restore_title),
                description = stringResource(R.string.quick_activate_step_restore_dsp),
                buttonText = stringResource(R.string.quick_activate_step_restore_btn),
                enabled = !running,
                onClick = {
                    runStep("restore") { DpDirectActivation.restore(context) }
                }
            )

            OutlinedButton(
                onClick = { commandDialogShow = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(imageVector = Icons.TwoTone.Code, contentDescription = null)
                androidx.compose.foundation.layout.Spacer(
                    Modifier.size(ButtonDefaults.IconSpacing)
                )
                Text(stringResource(R.string.quick_activate_view_command))
            }
        }
    }

    // resultText 是委托属性，无法智能转换为非空，先取局部快照
    val resultSnapshot = resultText
    if (resultSnapshot != null) {
        ResultDialog(
            ok = resultOk,
            text = resultSnapshot,
            onDismiss = { resultText = null }
        )
    }

    if (commandDialogShow) {
        CommandDialog(onDismiss = { commandDialogShow = false })
    }
}

/**
 * WarningCard
 * ()
 * 作用：执行 WarningCard
 * 备注：Compose 组件；仅内部使用
 */
@Composable
private fun WarningCard() {
    NeoCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.TwoTone.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.quick_activate_warning_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = stringResource(R.string.quick_activate_warning_dsp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = stringResource(R.string.quick_activate_limit_dsp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

/**
 * UnsupportedCard
 * ()
 * 作用：执行 UnsupportedCard
 * 备注：Compose 组件；仅内部使用
 */
@Composable
private fun UnsupportedCard() {
    NeoCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.quick_activate_unsupported_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = stringResource(
                    R.string.quick_activate_unsupported_dsp,
                    androidVersionLabel()
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

/**
 * StepCard
 * (index: Int, title: String, description: String, buttonText: String, enabled: Boolean, onClick: () -> Unit)
 * 作用：执行 StepCard
 * 备注：Compose 组件；仅内部使用
 */
@Composable
private fun StepCard(
    index: Int,
    title: String,
    description: String,
    buttonText: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    NeoCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(40.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Box(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                        Text(index.toString())
                    }
                }
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(buttonText)
            }
        }
    }
}

/**
 * ResultDialog
 * (ok: Boolean, text: String, onDismiss: () -> Unit)
 * 作用：执行 ResultDialog
 * 备注：Compose 组件；仅内部使用
 */
@Composable
private fun ResultDialog(ok: Boolean, text: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = if (ok) Icons.TwoTone.Check else Icons.TwoTone.Warning,
                contentDescription = null
            )
        },
        title = {
            Text(
                stringResource(
                    if (ok) R.string.quick_activate_result_ok
                    else R.string.quick_activate_result_fail
                )
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                SelectionContainer { Text(text) }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.confirm))
            }
        }
    )
}

/**
 * CommandDialog
 * (onDismiss: () -> Unit)
 * 作用：执行 CommandDialog
 * 备注：Compose 组件；仅内部使用
 */
@Composable
private fun CommandDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val commands = remember {
        DpDirectActivation.buildManualCommands(
            context.packageName,
            DhizukuState.admin
        )
    }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.quick_activate_view_command)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                SelectionContainer {
                    Text(
                        commands,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    clipboard.setClipEntry(
                        ClipEntry(ClipData.newPlainText("command", commands))
                    )
                    onDismiss()
                }
            }) {
                Text(stringResource(R.string.copy))
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
 * 把 [android.os.Build.VERSION.SDK_INT] 转成人类可读的版本名。
 * 形如 `Android 12（API 31）`。用于提示文案——只报 API 数字（31）对用户没有意义，
 * 且与同一段文案里的「Android 13」不是一个口径。
 * 未收录的版本（含未来的新版本）降级为 `API xx`，避免给出错误的版本号。
 */
private fun androidVersionLabel(): String {
    val code = android.os.Build.VERSION.SDK_INT
    val name = when (code) {
        37 -> "17"
        36 -> "16"
        35 -> "15"
        34 -> "14"
        33 -> "13"
        32 -> "12L"
        31 -> "12"
        30 -> "11"
        29 -> "10"
        28 -> "9"
        27 -> "8.1"
        26 -> "8.0"
        else -> null
    }
    return if (name != null) "Android $name（API $code）" else "API $code"
}
