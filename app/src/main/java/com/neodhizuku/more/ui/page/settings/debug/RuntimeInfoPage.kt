package com.neodhizuku.more.ui.page.settings.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

import com.neodhizuku.more.R
import com.neodhizuku.more.ui.component.NeoCard
import com.neodhizuku.more.ui.theme.combine

import org.koin.androidx.compose.koinViewModel

/**
 * RuntimeInfoPage
 * (windowInsets: WindowInsets, navController: NavController, viewModel: RuntimeInfoViewModel = koinViewModel())
 * 作用：「详细运行信息」：按板块展出内存中的全部状态，点任意一行复制值。 首次进入与每次点刷新都会重新采集，不会缓存旧快照；搜索与折叠只作用于展示。
 * 备注：Compose 组件
 */
@Composable
fun RuntimeInfoPage(
    windowInsets: WindowInsets,
    navController: NavController,
    viewModel: RuntimeInfoViewModel = koinViewModel()
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.refresh(context.applicationContext)
    }

    val query = viewModel.query.trim()
    val sections = viewModel.sections.map { section ->
        val rows = if (query.isBlank()) section.rows else section.rows.filter { (label, value) ->
            label.contains(query, ignoreCase = true) || value.contains(query, ignoreCase = true)
        }
        section.copy(rows = rows)
    }.filter { it.rows.isNotEmpty() }
    val shownRows = sections.sumOf { it.rows.size }

    DebugScaffold(
        title = stringResource(R.string.debug_info_title),
        windowInsets = windowInsets,
        onBack = { navController.navigateUp() },
        actions = {
            TextButton(onClick = { viewModel.copyAll(context) }) {
                Text(stringResource(R.string.debug_info_copy_all))
            }
            TextButton(onClick = { viewModel.refresh(context.applicationContext) }) {
                Text(stringResource(R.string.debug_info_refresh))
            }
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp).combine(padding),
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item("info_notice") {
                Text(
                    stringResource(R.string.debug_info_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item("info_search") {
                OutlinedTextField(
                    value = viewModel.query,
                    onValueChange = { viewModel.updateQuery(it) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.debug_search)) }
                )
            }
            item("info_stats") {
                Text(
                    stringResource(
                        R.string.debug_info_stats,
                        sections.size,
                        shownRows,
                        viewModel.timeLabel()
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (viewModel.copied.isNotBlank()) {
                item("info_copied") {
                    Text(
                        stringResource(R.string.debug_info_copied, viewModel.copied),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (sections.isEmpty()) {
                item("info_empty") {
                    Text(
                        stringResource(R.string.debug_info_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            sections.forEach { section ->
                item("section_${section.name}") {
                    Surface(
                        onClick = { viewModel.toggle(section.name) },
                        color = Color.Transparent,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${section.name} (${section.rows.size})",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                if (section.name in viewModel.collapsed) "+" else "-",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (section.name !in viewModel.collapsed) {
                    item("card_${section.name}") {
                        NeoCard(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                section.rows.forEach { (label, value) ->
                                    DebugInfoRow(
                                        label = label,
                                        value = value,
                                        onCopy = { viewModel.copy(context, it) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
