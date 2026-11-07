package com.neodhizuku.more.ui.page.settings.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neodhizuku.more.R
import com.neodhizuku.more.data.common.util.openUrlInBrowser

/** 一个被引用的开源项目：名字、一句说明、仓库地址。 */
data class Credit(
    val name: String,
    val descRes: Int,
    val url: String
)

/** 关于页列出的引用项目。新增或删除条目只改这里。 */
val CREDITS: List<Credit> = listOf(
    Credit(
        "Dhizuku",
        R.string.about_credit_dhizuku,
        "https://github.com/iamr0s/Dhizuku"
    ),
    Credit(
        "Dhizuku-API",
        R.string.about_credit_dhizuku_api,
        "https://github.com/iamr0s/Dhizuku-API"
    ),
    Credit(
        "Shizuku-API",
        R.string.about_credit_shizuku_api,
        "https://github.com/RikkaApps/Shizuku-API"
    ),
    Credit(
        "AndroidHiddenApiBypass",
        R.string.about_credit_hidden_api,
        "https://github.com/LSPosed/AndroidHiddenApiBypass"
    ),
    Credit(
        "NPatch",
        R.string.about_credit_npatch,
        "https://github.com/7723mod/NPatch"
    ),
    Credit(
        "KernelSU",
        R.string.about_credit_kernelsu,
        "https://github.com/tiann/KernelSU"
    ),
    Credit(
        "miuix",
        R.string.about_credit_miuix,
        "https://github.com/compose-miuix-ui/miuix"
    ),
    Credit(
        "Koin",
        R.string.about_credit_koin,
        "https://insert-koin.io"
    )
)

/** 引用项目卡：整张卡可点，弹窗里列出全部项目与鸣谢。 */
@Composable
fun CreditsCard() {
    var showDialog by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { showDialog = true }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.about_credits_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.about_credits_entry_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (showDialog) {
        CreditsDialog(onDismiss = { showDialog = false })
    }
}

@Composable
private fun CreditsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.about_credits_dialog_title),
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                CREDITS.forEach { credit ->
                    CreditRow(credit) { context.openUrlInBrowser(credit.url) }
                }
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                CreditRow(
                    name = stringResource(R.string.about_credit_ai_translation),
                    desc = stringResource(R.string.about_credit_ai_translation_desc),
                    onClick = null
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun CreditRow(credit: Credit, onClick: () -> Unit) {
    CreditRow(
        name = credit.name,
        desc = stringResource(credit.descRes),
        onClick = onClick
    )
}

@Composable
private fun CreditRow(name: String, desc: String, onClick: (() -> Unit)?) {
    val modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(8.dp))
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(horizontal = 4.dp, vertical = 7.dp)

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                desc,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (onClick == null) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = null,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
