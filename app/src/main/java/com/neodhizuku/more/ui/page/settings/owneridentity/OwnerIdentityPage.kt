package com.neodhizuku.more.ui.page.settings.owneridentity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import com.neodhizuku.more.ui.component.NeoAlertDialog as AlertDialog
import com.neodhizuku.more.ui.component.NeoCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

import com.neodhizuku.more.R
import com.neodhizuku.more.server.OwnerIdentityTools
import com.neodhizuku.more.ui.component.TransferTargetInfo
import com.neodhizuku.more.ui.theme.combine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 设备身份：对被改过包名的同项目实例做「移除 / 转移」。
 * 只做这两件事，不猜当前 applicationId —— 候选全部来自系统实际声明的
 * 设备管理员组件，所以两个实例都显示「未激活」时也能列出来。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerIdentityPage(
    navController: NavController,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf(emptyList<OwnerIdentityTools.Entry>()) }
    var result by remember { mutableStateOf("") }
    var raw by remember { mutableStateOf("") }
    // 待确认的不可逆动作：(组件, 动作名)
    var pending by remember { mutableStateOf<Pair<OwnerIdentityTools.Entry, String>?>(null) }
    // 1 = 第一道确认，2 = 第二道确认
    var pendingStage by remember { mutableIntStateOf(1) }

    fun reload() {
        scope.launch {
            val list = withContext(Dispatchers.IO) { OwnerIdentityTools.candidates(context) }
            entries = list
        }
    }
    LaunchedEffect(Unit) { reload() }

    fun show(r: OwnerIdentityTools.ActionResult) {
        result = (if (r.ok) "OK  " else "FAIL  ") + r.detail
        reload()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.owner_identity_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.TwoTone.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp).combine(padding).combine(contentPadding),
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item("desc") {
                Text(
                    stringResource(R.string.owner_identity_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (result.isNotBlank()) item("result") {
                Text(result, style = MaterialTheme.typography.bodySmall)
            }
            item("input") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(
                        value = raw,
                        onValueChange = { raw = it },
                        label = { Text(stringResource(R.string.owner_identity_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    TextButton(onClick = {
                        val c = OwnerIdentityTools.parse(raw)
                        if (c == null) {
                            result = "invalid component"
                        } else {
                            entries = entries + OwnerIdentityTools.Entry(
                                component = c,
                                label = c.packageName,
                                isSameProject = c.className.endsWith(".DhizukuDAReceiver"),
                                isSelfPackage = c.packageName == context.packageName,
                                active = false
                            )
                            result = "added " + c.flattenToShortString()
                            raw = ""
                        }
                    }) { Text(stringResource(R.string.owner_identity_add)) }
                }
            }
            if (entries.isEmpty()) item("empty") {
                Text(stringResource(R.string.owner_identity_empty))
            }
            items(entries, key = { it.component.flattenToShortString() }) { e ->
                EntryCard(
                    entry = e,
                    onRemove = { pending = e to "remove"; pendingStage = 1 },
                    onShell = { pending = e to "shell"; pendingStage = 1 },
                    onTransfer = { pending = e to "transfer"; pendingStage = 1 }
                )
            }
        }
    }

    // 确认后才执行：移除 / 强清 / 转移都是不可逆的设备身份变更。
    fun runAction(e: OwnerIdentityTools.Entry, action: String) {
        scope.launch {
            val r = when (action) {
                "remove" -> withContext(Dispatchers.IO) {
                    OwnerIdentityTools.remove(context, e.component)
                }
                "shell" -> OwnerIdentityTools.removeViaShell(context, e.component)
                else -> withContext(Dispatchers.IO) {
                    OwnerIdentityTools.transfer(context, e.component)
                }
            }
            show(r)
        }
    }

    pending?.let { (e, action) ->
        val isTransfer = action == "transfer"
        val title = if (pendingStage == 1) {
            stringResource(
                if (isTransfer) R.string.owner_identity_transfer_title
                else R.string.owner_identity_confirm_title
            )
        } else {
            stringResource(R.string.owner_identity_confirm_again_title)
        }
        val confirmText = if (pendingStage == 1) {
            stringResource(R.string.owner_transfer_confirm1_continue)
        } else {
            stringResource(R.string.owner_identity_confirm_ok)
        }
        AlertDialog(
            onDismissRequest = {
                pending = null
                pendingStage = 1
            },
            title = { Text(title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (isTransfer) {
                        // 只显示包名，不带接收器名；名称与图标取自系统
                        TransferTargetInfo(
                            packageName = e.component.packageName,
                            appLabel = e.label
                        )
                        Text(
                            stringResource(
                                if (pendingStage == 1) R.string.owner_identity_transfer_warning
                                else R.string.owner_identity_transfer_final_warning
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Text(
                            stringResource(R.string.owner_identity_confirm_desc) + "\n" +
                                e.component.flattenToShortString()
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (pendingStage == 1) {
                        pendingStage = 2
                    } else {
                        pending = null
                        pendingStage = 1
                        runAction(e, action)
                    }
                }) { Text(confirmText) }
            },
            dismissButton = {
                TextButton(onClick = {
                    pending = null
                    pendingStage = 1
                }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun EntryCard(
    entry: OwnerIdentityTools.Entry,
    onRemove: () -> Unit,
    onShell: () -> Unit,
    onTransfer: () -> Unit
) {
    NeoCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(entry.label, style = MaterialTheme.typography.titleSmall)
            Text(
                entry.component.flattenToShortString(),
                style = MaterialTheme.typography.bodySmall
            )
            val marks = mutableListOf<String>()
            if (entry.isSameProject) marks += stringResource(R.string.owner_identity_same_project)
            if (entry.isSelfPackage) marks += stringResource(R.string.owner_identity_self)
            marks += stringResource(
                if (entry.active) R.string.owner_identity_active
                else R.string.owner_identity_inactive
            )
            Text(
                marks.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onRemove) {
                    Text(stringResource(R.string.owner_identity_remove))
                }
                TextButton(onClick = onShell) {
                    Text(stringResource(R.string.owner_identity_remove_shell))
                }
            }
            TextButton(onClick = onTransfer) {
                Text(stringResource(R.string.owner_identity_transfer))
            }
        }
    }
}
