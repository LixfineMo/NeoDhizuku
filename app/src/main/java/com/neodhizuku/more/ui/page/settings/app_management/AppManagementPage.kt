package com.neodhizuku.more.ui.page.settings.app_management

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Block
import androidx.compose.material.icons.twotone.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import com.neodhizuku.more.ui.component.NeoCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

import com.neodhizuku.more.NeoDhizuku
import com.neodhizuku.more.R
import com.neodhizuku.more.server.ClientApiVersion
import com.neodhizuku.more.ui.component.ClientApiBadge
import com.neodhizuku.more.ui.component.ClientApiVersionBadge
import com.neodhizuku.more.ui.component.FullAccessBadge
import com.neodhizuku.more.ui.theme.AppIconCache
import com.neodhizuku.more.ui.theme.exclude
import com.neodhizuku.more.ui.theme.combine

import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppManagementPage(
    windowInsets: WindowInsets,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: AppManagementViewModel = koinViewModel()
) {
    LaunchedEffect(Unit) {
        viewModel.collect()
    }
    Scaffold(
        modifier = Modifier
            .windowInsetsPadding(windowInsets.exclude(WindowInsetsSides.Bottom))
            .fillMaxSize(),
        // 外层 NavHost 已应用 Scaffold 的 padding（含系统 inset 与底栏高度），
        // 这里再处理一次 Bottom 会导致列表底部多出一块空白。
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.home_app_management_title))
                }
            )
        }) {
        @Suppress("AnimatedContentLabel")
        AnimatedContent(targetState = viewModel.state.data.isEmpty()) { isEmpty ->
            val pullToRefreshState = rememberPullToRefreshState()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pullToRefresh(state = pullToRefreshState,
                        isRefreshing = viewModel.state.loading,
                        onRefresh = {
                            viewModel.collectRepo()
                        }
                    )
            ) {
                if (isEmpty) Text(
                    stringResource(R.string.home_app_management_dsp),
                    modifier = Modifier
                        .padding(it.combine(PaddingValues(16.dp)))
                        .align(Alignment.Center)
                )
                // `it` 是 Scaffold 自己的内边距（顶栏高度），页面外面传进来的
                // contentPadding（底栏让位）必须叠上去，否则列表最后一项会压在底栏下。
                else ItemsWidget(
                    viewModel = viewModel,
                    contentPadding = it.combine(contentPadding)
                )
                PullToRefreshDefaults.Indicator(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(it),
                    state = pullToRefreshState,
                    isRefreshing = viewModel.state.loading
                )
            }
        }
    }
}

@Composable
private fun ItemsWidget(
    viewModel: AppManagementViewModel,
    contentPadding: PaddingValues
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp).combine(contentPadding),
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(viewModel.state.data, key = { it.applicationInfo.uid }) {
            ItemWidget(
                viewModel = viewModel,
                data = it
            )
        }
    }
}

@Composable
private fun LazyItemScope.ItemWidget(
    viewModel: AppManagementViewModel,
    data: AppManagementViewData
) {
    NeoCard(
        modifier = Modifier
            .animateItem()
            .fillMaxWidth(),
        colors = if (data.blocked) CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        ) else CardDefaults.cardColors()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                val applicationInfo = data.applicationInfo
                val imageBitmap = AppIconCache.rememberImageBitmapState(applicationInfo)
                val packageName = applicationInfo.packageName
                val label = applicationInfo.loadLabel(LocalContext.current.packageManager).toString()
                val version = ClientApiVersion.ofPackage(packageName)

                Image(
                    bitmap = imageBitmap.value,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentDescription = null
                )

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Medium
                            ),
                            color = if (data.blocked) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (data.fullAccess) FullAccessBadge()
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    ClientApiVersionBadge(version = version)
                    Text(
                        packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (data.blocked) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                ClientApiBadge(
                    version = version,
                    modifier = Modifier.align(Alignment.Top)
                )
            }

            AnimatedVisibility(
                visible = !data.blocked,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.allow_dhizuku_access),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Switch(
                            checked = data.enabled,
                            onCheckedChange = { enabled ->
                                viewModel.setEnabled(data.applicationInfo.uid, enabled)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                                uncheckedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )
                    }
                }
            }

            AnimatedContent(
                targetState = data.blocked,
                label = "block_button"
            ) { isBlocked ->
                if (isBlocked) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                            tonalElevation = 1.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.TwoTone.Block,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    stringResource(R.string.app_blocked_notice),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }

                        Button(
                            onClick = {
                                viewModel.setBlocked(data.applicationInfo.uid, false)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.TwoTone.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
                            Text(
                                stringResource(R.string.unblock_app),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            viewModel.setBlocked(data.applicationInfo.uid, true)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.TwoTone.Block,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
                        Text(
                            stringResource(R.string.block_app),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

