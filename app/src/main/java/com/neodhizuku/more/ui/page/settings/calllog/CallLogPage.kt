package com.neodhizuku.more.ui.page.settings.calllog

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.twotone.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

import androidx.navigation.NavController

import com.neodhizuku.more.R
import com.neodhizuku.more.data.settings.model.room.entity.AuditEntity
import com.neodhizuku.more.data.settings.model.room.entity.RuntimeLogEntity
import com.neodhizuku.more.server.AuditLogStore

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 两个标签页 */
private enum class LogTab { Audit, Runtime }

/**
 * 调用记录页。
 *
 * 两个 Tab：调用记录（第三方应用调用过哪些能力）、运行日志（服务端自身日志）。
 *
 * 分组层级：
 * - 调用记录：按自然日 → **按应用**。一个应用一张卡，点开看该应用当天的全部调用
 * - 运行日志：按自然日 → **按秒**。同一秒的日志并成一张卡，避免刷屏
 *
 * 最新的日期在最上面；长按卡片可复制该卡片的全部文本。
 * 未激活时也能查看运行日志。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallLogPage(
    windowInsets: WindowInsets,
    navController: NavController,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    startOnRuntime: Boolean = false,
    viewModel: CallLogViewModel = org.koin.androidx.compose.koinViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.collect()
    }

    var tab by remember { mutableStateOf(if (startOnRuntime) LogTab.Runtime else LogTab.Audit) }
    var clearConfirm by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
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
                title = { Text(stringResource(R.string.nav_call_log)) },
                actions = {
                    IconButton(onClick = { clearConfirm = true }) {
                        Icon(
                            imageVector = Icons.TwoTone.Delete,
                            contentDescription = stringResource(R.string.call_log_clear)
                        )
                    }
                }
            )
        }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(contentPadding)
        ) {
            TabRow(selectedTabIndex = tab.ordinal) {
                Tab(
                    selected = tab == LogTab.Audit,
                    onClick = { tab = LogTab.Audit },
                    text = { Text(stringResource(R.string.call_log_tab_audit), maxLines = 1) }
                )
                Tab(
                    selected = tab == LogTab.Runtime,
                    onClick = { tab = LogTab.Runtime },
                    text = { Text(stringResource(R.string.call_log_tab_runtime), maxLines = 1) }
                )
            }

            when (tab) {
                LogTab.Audit -> AuditList(state.audit)
                LogTab.Runtime -> RuntimeList(state.runtime)
            }
        }
    }

    if (clearConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { clearConfirm = false },
            confirmButton = {
                TextButton(onClick = {
                    clearConfirm = false
                    viewModel.clearAll()
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { clearConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            title = { Text(stringResource(R.string.call_log_clear)) },
            text = { Text(stringResource(R.string.call_log_clear_confirm)) }
        )
    }
}

/* ------------------------------ 时间工具 ------------------------------ */

private fun dayStart(timestamp: Long): Long = Calendar.getInstance().apply {
    timeInMillis = timestamp
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun dayLabel(timestamp: Long): String {
    val today = dayStart(System.currentTimeMillis())
    val target = dayStart(timestamp)
    return when (target) {
        today -> "今天"
        today - 24 * 3600 * 1000L -> "昨天"
        else -> SimpleDateFormat("M月d日", Locale.getDefault()).format(Date(timestamp))
    }
}

private fun formatClock(timestamp: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))

private fun formatClockMs(timestamp: Long): String =
    SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))

/**
 * 两级分组：先按自然日，日內再按 `subKey` 分。
 *
 * 用显式的类而不是 Pair / 解构声明：Map.Entry 解构与 Pair 在部分工具链下
 * 会出现 component1() 歧义，显式类型更容易稳定编译。
 */
private class SubGroup<T>(val key: Long, val items: List<T>)

private class DayGroup<T>(val day: Long, val subGroups: List<SubGroup<T>>)

private fun <T> groupByDayThenBy(
    entries: List<T>,
    timeOf: (T) -> Long,
    subKey: (T) -> Long
): List<DayGroup<T>> {
    val byDay = LinkedHashMap<Long, MutableList<T>>()
    entries.forEach { item ->
        byDay.getOrPut(dayStart(timeOf(item))) { mutableListOf() }.add(item)
    }
    return byDay.map { entry ->
        val bySub = LinkedHashMap<Long, MutableList<T>>()
        entry.value.forEach { item ->
            bySub.getOrPut(subKey(item)) { mutableListOf() }.add(item)
        }
        DayGroup(entry.key, bySub.map { SubGroup(it.key, it.value) })
    }
}

/** 取应用名，取不到就退回包名 */
private fun appLabel(context: Context, packageName: String): String = runCatching {
    val pm = context.packageManager
    val info = pm.getApplicationInfo(packageName, 0)
    pm.getApplicationLabel(info).toString()
}.getOrNull() ?: packageName

/** 长按复制。用系统 ClipboardManager，不依赖 Compose 剪贴板 API */
private fun copyText(context: Context, text: String) {
    runCatching {
        val manager =
            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        manager.setPrimaryClip(ClipData.newPlainText("NeoDhizuku", text))
        Toast.makeText(context, context.getString(R.string.call_log_copy_done), Toast.LENGTH_SHORT)
            .show()
    }
}

/* ------------------------------ 调用记录 ------------------------------ */

/** 一条调用记录转成纯文本（含时间、包名、动作、详情、日志片段） */
private fun auditLine(entity: AuditEntity): String = buildString {
    append(formatClock(entity.timestamp)).append("  ").append(entity.packageName)
    append("\n  ").append(AuditLogStore.describe(entity.api))
    if (entity.detail.isNotBlank()) {
        append("\n  ").append(entity.detail)
    }
    if (entity.logExcerpt.isNotBlank()) {
        append("\n").append(entity.logExcerpt)
    }
}

private fun auditText(list: List<AuditEntity>): String =
    list.joinToString("\n") { auditLine(it) }

@Composable
private fun AuditList(entries: List<AuditEntity>) {
    if (entries.isEmpty()) {
        EmptyHint(stringResource(R.string.call_log_empty))
        return
    }
    val context = LocalContext.current

    // entries 已按时间倒序。groupBy 返回 LinkedHashMap，键顺序 = 首次出现顺序，
    // 因此「最新日期在最上」「当天内最近调用过的应用在最上」都天然成立，无需再排序。
    val grouped = remember(entries) {
        groupByDayThenBy(entries, { it.timestamp }) { it.uid.toLong() }
    }
    val today = dayStart(System.currentTimeMillis())

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        grouped.forEach { dayGroup ->
            val dayStartMs = dayGroup.day
            val dayCount = dayGroup.subGroups.sumOf { it.items.size }
            item(key = "aday_$dayStartMs") {
                DayCard(
                    label = dayLabel(dayStartMs),
                    count = dayCount,
                    defaultExpanded = dayStartMs == today,
                    copyText = auditText(dayGroup.subGroups.flatMap { it.items })
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        dayGroup.subGroups.forEach { appGroup ->
                            val list = appGroup.items
                            val packageName = list.first().packageName
                            AppCard(
                                title = appLabel(context, packageName),
                                subtitle = packageName,
                                count = list.size,
                                latest = formatClock(list.first().timestamp),
                                copyText = auditText(list)
                            ) {
                                Column {
                                    list.forEachIndexed { index, entity ->
                                        if (index > 0) {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(vertical = 8.dp),
                                                color = MaterialTheme.colorScheme.outlineVariant
                                            )
                                        }
                                        AuditRow(entity)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuditRow(entity: AuditEntity) {
    var showLog by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = formatClock(entity.timestamp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = AuditLogStore.describe(entity.api),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
        }
        if (entity.detail.isNotBlank()) {
            Text(
                text = entity.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 60.dp, top = 2.dp)
            )
        }
        if (entity.logExcerpt.isNotBlank()) {
            TextButton(
                onClick = { showLog = !showLog },
                modifier = Modifier.padding(start = 48.dp, top = 4.dp)
            ) {
                Text(stringResource(R.string.call_log_log_excerpt))
            }
            if (showLog) {
                Text(
                    text = entity.logExcerpt,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 60.dp, bottom = 4.dp)
                )
            }
        }
    }
}

/* ------------------------------ 运行日志 ------------------------------ */

/** 运行日志按秒归组：同一秒内的日志并成一张卡，避免逐行刷屏 */
private fun secondStart(timestamp: Long): Long = timestamp / 1000L * 1000L

private fun runtimeLine(entity: RuntimeLogEntity): String =
    "${formatClockMs(entity.timestamp)} ${entity.level}/${entity.sub} ${entity.message}"

private fun runtimeText(list: List<RuntimeLogEntity>): String =
    list.joinToString("\n") { runtimeLine(it) }

/**
 * 去重后的一行运行日志。
 *
 * 同一秒内完全相同的日志（同级别、同分类、同内容）只保留一条，
 * 用 [count] 记录重复次数 —— 例如心跳类日志一秒能刷出十几条完全一样的，
 * 全部展开既没法看也把真正有用的信息淹没了。
 */
private data class RuntimeLineItem(
    val entity: RuntimeLogEntity,
    val count: Int
)

/**
 * 按「级别 + 分类 + 内容」折叠重复项。
 *
 * 保留首次出现那条的时间戳，重复次数累加到 [RuntimeLineItem.count]。
 */
private fun dedupe(list: List<RuntimeLogEntity>): List<RuntimeLineItem> {
    val order = ArrayList<RuntimeLineItem>()
    val index = HashMap<String, Int>()
    list.forEach { entity ->
        val key = "${entity.level}|${entity.sub}|${entity.message}"
        val at = index[key]
        if (at == null) {
            index[key] = order.size
            order.add(RuntimeLineItem(entity, 1))
        } else {
            order[at] = order[at].copy(count = order[at].count + 1)
        }
    }
    return order
}

@Composable
private fun RuntimeList(entries: List<RuntimeLogEntity>) {
    if (entries.isEmpty()) {
        EmptyHint(stringResource(R.string.call_log_empty_runtime))
        return
    }

    val grouped = remember(entries) {
        groupByDayThenBy(entries, { it.timestamp }) { secondStart(it.timestamp) }
    }
    val today = dayStart(System.currentTimeMillis())

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        grouped.forEach { dayGroup ->
            val dayStartMs = dayGroup.day
            val dayCount = dayGroup.subGroups.sumOf { it.items.size }
            item(key = "rday_$dayStartMs") {
                DayCard(
                    label = dayLabel(dayStartMs),
                    count = dayCount,
                    defaultExpanded = dayStartMs == today,
                    copyText = runtimeText(dayGroup.subGroups.flatMap { it.items })
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        dayGroup.subGroups.forEach { secondGroup ->
                            val list = secondGroup.items
                            SecondCard(
                                label = formatClock(secondGroup.key),
                                count = list.size,
                                copyText = runtimeText(list)
                            ) {
                                Column {
                                    dedupe(list).forEach { item ->
                                        RuntimeRow(item.entity, item.count)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RuntimeRow(entity: RuntimeLogEntity, count: Int = 1) {
    val color = when (entity.level) {
        "E" -> MaterialTheme.colorScheme.error
        "W" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = formatClockMs(entity.timestamp),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "${entity.level}/${entity.sub}",
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace
            ),
            color = color
        )
        Text(
            text = entity.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        // 同一秒内重复出现的相同日志，折叠成 ×N
        if (count > 1) {
            Text(
                text = "×$count",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace
                ),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/* ------------------------------ 通用卡片 ------------------------------ */

/**
 * 可展开、可长按复制的分组卡片。
 *
 * 点击切换展开；长按把 `copyText` 整段复制到剪贴板。
 * 用系统 ClipboardManager 而非 Compose 剪贴板，避免版本差异。
 */
@Composable
private fun ExpandableCard(
    modifier: Modifier = Modifier,
    defaultExpanded: Boolean = false,
    copyText: String,
    header: @Composable (expanded: Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(defaultExpanded) }
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .combinedClickable(
                onClick = { expanded = !expanded },
                onLongClick = { copyText(context, copyText) }
            )
            .padding(12.dp)
    ) {
        header(expanded)
        if (expanded) {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                content()
            }
        }
    }
}

/** 日期分组卡 */
@Composable
private fun DayCard(
    label: String,
    count: Int,
    defaultExpanded: Boolean,
    copyText: String,
    content: @Composable () -> Unit
) {
    ExpandableCard(
        defaultExpanded = defaultExpanded,
        copyText = copyText,
        header = { expanded ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Filled.KeyboardArrowUp
                        else Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        content = content
    )
}

/** 调用记录的「单个应用」卡 */
@Composable
private fun AppCard(
    title: String,
    subtitle: String,
    count: Int,
    latest: String,
    copyText: String,
    content: @Composable () -> Unit
) {
    ExpandableCard(
        defaultExpanded = false,
        copyText = copyText,
        header = { expanded ->
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.call_log_times, count),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Icon(
                            imageVector = if (expanded) Icons.Filled.KeyboardArrowUp
                            else Icons.Filled.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = latest,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        content = content
    )
}

/** 运行日志的「某一秒」卡 */
@Composable
private fun SecondCard(
    label: String,
    count: Int,
    copyText: String,
    content: @Composable () -> Unit
) {
    ExpandableCard(
        defaultExpanded = false,
        copyText = copyText,
        header = { expanded ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.call_log_times, count),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Filled.KeyboardArrowUp
                        else Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        content = content
    )
}

@Composable
private fun EmptyHint(text: String) {
    Box(modifier = Modifier.fillMaxSize()) {
        Text(
            text = text,
            modifier = Modifier.align(Alignment.Center),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
