package com.neodhizuku.more.ui.page.settings.whitelist

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material3.Checkbox
import com.neodhizuku.more.ui.component.NeoCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

import com.neodhizuku.more.R
import com.neodhizuku.more.server.ClientApiVersion
import com.neodhizuku.more.ui.component.ClientApiBadge
import com.neodhizuku.more.ui.theme.AppIconCache
import com.neodhizuku.more.ui.theme.exclude

import org.koin.androidx.compose.koinViewModel

/**
 * WhitelistPage
 * (windowInsets: WindowInsets, navController: NavController, viewModel: WhitelistViewModel = koinViewModel())
 * 作用：白名单管理：手动勾选允许使用本服务的应用。 分两组展示 —— 已加入的在前面便于复查，其余候选在后面供勾选。
 * 备注：Compose 组件；使用了实验性 API
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhitelistPage(
    windowInsets: WindowInsets,
    navController: NavController,
    viewModel: WhitelistViewModel = koinViewModel()
) {
    LaunchedEffect(Unit) {
        viewModel.collect()
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
                title = { Text(stringResource(R.string.whitelist_manage_title)) }
            )
        }
    ) { padding ->
        val state = viewModel.state
        if (state.loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Text(
                    stringResource(R.string.whitelist_loading),
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            return@Scaffold
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item("whitelist_note") {
                Text(
                    stringResource(R.string.whitelist_manage_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item("whitelisted_title") {
                SectionTitle(stringResource(R.string.whitelist_section_whitelisted))
            }

            if (state.whitelisted.isEmpty()) {
                item("whitelisted_empty") {
                    Text(
                        stringResource(R.string.whitelist_empty_whitelisted),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            } else {
                items(state.whitelisted, key = { "w_${it.packageName}" }) { item ->
                    AppCheckCard(
                        item = item,
                        onToggle = { viewModel.setWhitelisted(item, !item.whitelisted) }
                    )
                }
            }

            item("others_title") {
                SectionTitle(stringResource(R.string.whitelist_section_others))
            }

            if (state.others.isEmpty()) {
                item("others_empty") {
                    Text(
                        stringResource(R.string.whitelist_empty_others),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            } else {
                items(state.others, key = { "o_${it.packageName}" }) { item ->
                    AppCheckCard(
                        item = item,
                        onToggle = { viewModel.setWhitelisted(item, !item.whitelisted) }
                    )
                }
            }
        }
    }
}

/**
 * SectionTitle
 * (text: String)
 * 作用：执行 SectionTitle
 * 备注：Compose 组件；仅内部使用
 */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp)
    )
}

/**
 * AppCheckCard
 * (item: WhitelistAppItem, onToggle: () -> Unit)
 * 作用：一张应用卡片：图标 + 名称 + 包名，最右侧是勾选框，整行可点。
 * 备注：Compose 组件；仅内部使用
 */
@Composable
private fun AppCheckCard(
    item: WhitelistAppItem,
    onToggle: () -> Unit
) {
    NeoCard(modifier = Modifier.fillMaxWidth()) {
        // 不用 Modifier.clickable：该符号在部分 Compose foundation 版本下解析不到，
        // 改用 material3 的 Surface(onClick=...)。
        Surface(
            onClick = onToggle,
            color = androidx.compose.ui.graphics.Color.Transparent,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    bitmap = AppIconCache.rememberImageBitmapState(item.applicationInfo).value,
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        ClientApiBadge(version = ClientApiVersion.ofPackage(item.packageName))
                    }
                    Text(
                        text = item.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                // 勾选框自身也响应点击：它与整行是同一个动作，
                // 由 Compose 的事件消费规则保证不会触发两次。
                Checkbox(
                    checked = item.whitelisted,
                    onCheckedChange = { onToggle() }
                )
            }
        }
    }
}
