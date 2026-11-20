package com.neodhizuku.more.ui.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.neodhizuku.more.ui.component.NeoAlertDialog as AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import com.neodhizuku.more.ui.component.NeoCard
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Warning
import com.neodhizuku.more.NeoDhizuku
import com.neodhizuku.more.NeoLog
import com.neodhizuku.more.R
import com.neodhizuku.more.server.WipeGuard
import com.neodhizuku.more.ui.theme.DhizukuTheme

/**
 * 擦除设备数据的确认页。
 * 第三方借用本应用身份发起 `wipeData` 时，服务端一律先拦截，
 * 用户必须在这里**连续通过三次确认**才会拿到一次临时放行：
 * 1. 第一级警告弹窗：说明"擦除会清空整机数据且不可逆"
 * 2. 第二级警告弹窗：再次确认是否真的要执行
 * 3. 页面上勾选"我已知晓并自愿承担后果"后点最终按钮
 * 放行是一次性且 5 分钟有效的（见 [WipeGuard]），
 * 避免误确认后长期敞口。
 */
class WipeConfirmActivity : ComponentActivity() {

    companion object {
        const val EXTRA_UID = "uid"
        const val EXTRA_LABEL = "label"
    }

    /**
     * onCreate
     * (savedInstanceState: Bundle?)
     * 作用：处理 Create 回调
     * 备注：重写父类 / 接口方法
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val uid = intent.getIntExtra(EXTRA_UID, -1)
        val label = intent.getStringExtra(EXTRA_LABEL) ?: uid.toString()

        NeoLog.w(NeoLog.UI) { "wipe confirm: 打开 uid=$uid label=$label" }

        setContent {
            DhizukuTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    WipeConfirmScreen(
                        uid = uid,
                        label = label,
                        onFinish = { finish() }
                    )
                }
            }
        }
    }
}

/**
 * WipeConfirmScreen
 * (uid: Int, label: String, onFinish: () -> Unit)
 * 作用：执行 WipeConfirmScreen
 * 备注：Compose 组件；仅内部使用
 */
@Composable
private fun WipeConfirmScreen(
    uid: Int,
    label: String,
    onFinish: () -> Unit
) {
    // 0=未确认 1=已过第一级 2=已过第二级
    var stage by remember { mutableIntStateOf(0) }
    var confirmed by remember { mutableStateOf(false) }

    // 第一级警告
    if (stage == 0) {
        WarnDialog(
            title = stringResource(R.string.wipe_warn1_title),
            text = stringResource(R.string.wipe_warn1_text, label),
            confirmText = stringResource(R.string.wipe_warn_continue),
            dismissText = stringResource(R.string.wipe_warn_cancel),
            onConfirm = { stage = 1 },
            onDismiss = onFinish
        )
    }

    // 第二级警告
    if (stage == 1) {
        WarnDialog(
            title = stringResource(R.string.wipe_warn2_title),
            text = stringResource(R.string.wipe_warn2_text, label),
            confirmText = stringResource(R.string.wipe_warn_i_confirm),
            dismissText = stringResource(R.string.wipe_warn_cancel),
            onConfirm = { stage = 2 },
            onDismiss = onFinish
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))

        Icon(
            imageVector = Icons.TwoTone.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(56.dp)
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.wipe_confirm_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error
        )

        Spacer(Modifier.height(20.dp))

        NeoCard(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.wipe_confirm_requester, label),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.wipe_confirm_detail),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // 第三级：勾选后才可提交
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = confirmed,
                onCheckedChange = { confirmed = it },
                enabled = stage >= 2
            )
            Text(
                text = stringResource(R.string.wipe_confirm_checkbox),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onFinish,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.wipe_confirm_reject))
            }
            Button(
                onClick = {
                    WipeGuard.grant(uid)
                    NeoLog.w(NeoLog.UI) { "wipe confirm: 用户确认放行 uid=$uid" }
                    onFinish()
                },
                enabled = stage >= 2 && confirmed,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.wipe_confirm_allow))
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.wipe_confirm_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * WarnDialog
 * (title: String, text: String, confirmText: String, dismissText: String, onConfirm: () -> Unit, onDismiss: () -> Unit)
 * 作用：执行 WarnDialog
 * 备注：Compose 组件；仅内部使用
 */
@Composable
private fun WarnDialog(
    title: String,
    text: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.TwoTone.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissText) }
        }
    )
}
