package com.neodhizuku.more.ui.page.settings.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.automirrored.twotone.ArrowForward
import androidx.compose.material3.AlertDialog
import com.neodhizuku.more.ui.component.NeoCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

import com.neodhizuku.more.ui.theme.exclude

/**
 * 调试分区共用的页面骨架。
 * 统一处理 windowInsets、返回键、标题，避免每个二级页面各写一遍 Scaffold。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScaffold(
    title: String,
    windowInsets: WindowInsets,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        modifier = Modifier
            .windowInsetsPadding(windowInsets.exclude(WindowInsetsSides.Bottom))
            .fillMaxSize(),
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.TwoTone.ArrowBack,
                            contentDescription = null
                        )
                    }
                },
                title = { Text(title) },
                actions = actions
            )
        }
    ) { padding ->
        content(padding)
    }
}

/** 调试主页的跳转卡片；[danger] 为真时整张卡用错误色标红。 */
@Composable
fun DebugMenuCard(
    title: String,
    desc: String,
    onClick: () -> Unit,
    danger: Boolean = false
) {
    val tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    NeoCard(modifier = Modifier.fillMaxWidth()) {
        Surface(
            onClick = onClick,
            color = Color.Transparent,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (danger) tint else Color.Unspecified
                    )
                    Text(
                        desc,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (danger) tint else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!danger) {
                    Icon(
                        imageVector = Icons.AutoMirrored.TwoTone.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** 详细运行信息里的一行：变量名保持原样，值可点击复制。 */
@Composable
fun DebugInfoRow(
    label: String,
    value: String,
    onCopy: ((String) -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Surface(
            onClick = { onCopy?.invoke(value) },
            enabled = onCopy != null,
            color = Color.Transparent,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    value.ifBlank { "-" },
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

/**
 * 修改运行参数的弹窗。
 * 标题直接用变量名（不翻译），输入框预填当前值。确认才写，取消不写。
 */
@Composable
fun DebugValueDialog(
    name: String,
    value: String,
    hint: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var draft by remember(name, value) { mutableStateOf(value) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(name) },
        text = {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("value") },
                // 直接给可组合 lambda：写成 `if (...) null else { { ... } }`
                // 会让 @Composable 函数类型的推断在两个分支间摇摆，编译不过。
                placeholder = { Text(hint) }
            )
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(draft)
                onDismiss()
            }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
