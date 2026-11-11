package com.neodhizuku.more.ui.page.settings.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import com.neodhizuku.more.ui.component.NeoCard
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neodhizuku.more.R
import com.neodhizuku.more.data.common.util.openUrlInBrowser

/** 引用项目：名称、说明、仓库地址。 */
data class Credit(
    val name: String,
    val descRes: Int,
    val url: String
)

/** 关于页列出的引用项目。 */
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

/** 引用项目卡：点击后列出全部项目。 */
@Composable
fun CreditsCard() {
    var showDialog by remember { mutableStateOf(false) }

    NeoCard(
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
private fun CreditRow(name: String, desc: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
    }
}
