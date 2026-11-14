package com.neodhizuku.more.ui.page.settings.experimental

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.neodhizuku.more.R
import com.neodhizuku.more.ui.component.NeoAlertDialog as AlertDialog
import com.neodhizuku.more.ui.component.NeoCard
import com.neodhizuku.more.ui.theme.exclude
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

/** 执行前的强制等待（秒），按钮到 0 才可点。 */
private const val RISK_CONFIRM_SECONDS = 5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Cve98432Page(
    windowInsets: WindowInsets,
    navController: NavController,
    viewModel: Cve98432ViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val openBoot = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importBoot(context, uri)
    }
    // 系统 VPN 确认框要求 startActivityForResult：普通 startActivity 拿不到 getCallingPackage，
    // NEW_TASK 又会取消 result，所以必须由 Activity 的 launcher 启动。
    var startAfterVpn by remember { mutableStateOf(false) }
    val vpnConsent = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.prepare(context, result.resultCode)
        if (startAfterVpn && Cve98432Engine.vpnConsentIntent(context) == null) {
            viewModel.requestStart()
        }
        startAfterVpn = false
    }
    val logScroll = rememberScrollState()
    val logCount = viewModel.logs.size
    LaunchedEffect(logCount) { logScroll.scrollTo(logScroll.maxValue) }

    if (viewModel.showConfirm) {
        RiskConfirmDialog(
            onDismiss = viewModel::dismissConfirm,
            onAccept = { viewModel.start(context) }
        )
    }

    Scaffold(
        modifier = Modifier
            .windowInsetsPadding(windowInsets.exclude(WindowInsetsSides.Bottom))
            .fillMaxSize(),
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
                title = { Text(stringResource(R.string.cve_98432_title)) }
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. 详细声明
            NeoCard {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.TwoTone.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = stringResource(R.string.cve_98432_danger_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    Declaration(R.string.cve_98432_danger_text)
                    Declaration(R.string.cve_98432_danger_what)
                    Declaration(R.string.cve_98432_danger_not)
                    Declaration(R.string.cve_98432_danger_risk)
                    Declaration(R.string.cve_98432_danger_duty)
                }
            }

            if (viewModel.handoff) {
                NeoCard {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.cve_98432_handoff_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.cve_98432_handoff_text),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = {
                            if (!viewModel.openKsu(context)) {
                                Toast.makeText(context, R.string.cve_98432_handoff_no_ksu, Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Text(stringResource(R.string.cve_98432_handoff_open_ksu))
                        }
                    }
                }
            }

            // 2. 导入 boot.img 镜像
            NeoCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.cve_98432_import_boot),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = viewModel.bootInfo?.let {
                            stringResource(R.string.cve_98432_boot_ready, it.name)
                        } ?: stringResource(R.string.cve_98432_boot_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = viewModel.profile?.let {
                            stringResource(R.string.cve_98432_profile_ready, it.textSymbols.size)
                        } ?: stringResource(R.string.cve_98432_profile_none),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    viewModel.profile?.let { p ->
                        Text(
                            text = p.describe().joinToString("\n"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = { openBoot.launch(arrayOf("*/*")) },
                        enabled = !viewModel.running,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.cve_98432_import_boot))
                    }
                }
            }

            // 3. 环境准备按钮
            NeoCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Button(
                        onClick = {
                            startAfterVpn = false
                            val pending = Cve98432Engine.vpnConsentIntent(context)
                            if (pending == null) viewModel.prepare(context)
                            else vpnConsent.launch(pending)
                        },
                        enabled = !viewModel.running,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.cve_98432_prepare))
                    }
                }
            }

            // 4. 开始执行按钮
            NeoCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Button(
                        onClick = {
                            val pending = Cve98432Engine.vpnConsentIntent(context)
                            if (pending == null) {
                                viewModel.requestStart()
                            } else {
                                startAfterVpn = true
                                vpnConsent.launch(pending)
                            }
                        },
                        enabled = !viewModel.running,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.cve_98432_start))
                    }
                    if (viewModel.running) {
                        TextButton(
                            onClick = viewModel::cancel,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.cve_98432_cancel),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // 5. 完整流程开关（仅至 SELinux 宽容，不提权）
            NeoCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.cve_98432_full_root),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = stringResource(R.string.cve_98432_full_root_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = viewModel.permissiveMode,
                        onCheckedChange = viewModel::setExecutionMode,
                        enabled = !viewModel.running
                    )
                }
            }

            // 6. 日志框
            NeoCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.cve_98432_log),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Row {
                            TextButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                    as ClipboardManager
                                clipboard.setPrimaryClip(
                                    ClipData.newPlainText("log", viewModel.logText)
                                )
                                Toast.makeText(context, R.string.cve_98432_log_copy, Toast.LENGTH_SHORT).show()
                            }) {
                                Text(stringResource(R.string.cve_98432_log_copy))
                            }
                            TextButton(onClick = viewModel::clearLogs) {
                                Text(stringResource(R.string.cve_98432_log_clear))
                            }
                        }
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            )
                            .padding(12.dp)
                            .verticalScroll(logScroll)
                    ) {
                        if (viewModel.logs.isEmpty()) {
                            Text(
                                text = stringResource(R.string.cve_98432_log_empty),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            viewModel.logs.forEach { line ->
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Declaration(textRes: Int) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun RiskConfirmDialog(
    onDismiss: () -> Unit,
    onAccept: () -> Unit
) {
    var remaining by remember { mutableIntStateOf(RISK_CONFIRM_SECONDS) }
    LaunchedEffect(Unit) {
        while (remaining > 0) {
            delay(1000)
            remaining--
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cve_98432_confirm_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.cve_98432_confirm_text))
                Text(
                    text = if (remaining > 0) {
                        stringResource(R.string.cve_98432_confirm_wait, remaining)
                    } else {
                        stringResource(R.string.cve_98432_confirm_ready)
                    },
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onAccept, enabled = remaining == 0) {
                Text(stringResource(R.string.cve_98432_confirm_accept))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cve_98432_confirm_cancel))
            }
        }
    )
}
