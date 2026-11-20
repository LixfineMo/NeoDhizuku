package com.neodhizuku.more.ui.component.glass

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.neodhizuku.more.ui.component.NavDestination
import com.neodhizuku.more.ui.component.floating.FloatingBottomBar
import com.neodhizuku.more.ui.component.floating.FloatingBottomBarItem
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

/**
 * GlassNavBar
 * (tabs: List<NavDestination>, selectedIndex: () -> Int, onNavigate: (String) -> Unit, selectedPosition: () -> Float = { selectedIndex().toFloat() }, onSettleTab: ((Int) -> Unit)? = null, backgroundTranslucency: Float = 1f, backdrop: Backdrop? = null, modifier: Modifier = Modifier)
 * 作用：底部悬浮选择栏。渲染、拖动状态机与 Q 弹反馈全部由 floating 包里的实现承担， 这里只负责三件事：把页签数据换成图标与文字、把选中态接进来、 按系统导航栏形态动态避让底部按键区。 参数保持与主容器调用点一致，不改动 SettingsPage。
 * 备注：Compose 组件
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun GlassNavBar(
    tabs: List<NavDestination>,
    selectedIndex: () -> Int,
    onNavigate: (String) -> Unit,
    selectedPosition: () -> Float = { selectedIndex().toFloat() },
    onSettleTab: ((Int) -> Unit)? = null,
    backgroundTranslucency: Float = 1f,
    backdrop: Backdrop? = null,
    modifier: Modifier = Modifier
) {
    if (tabs.isEmpty()) return

    // 三键导航会在屏幕底部占一整条，手势导航下这个 inset 为 0。
    // 用真实 inset 动态决定底栏离屏幕底的距离，避免底栏与三大金刚键重合；
    // Scaffold 会把这段高度计入内容区底部留白，列表不会被压住。
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomPadding = if (navBarBottom != 0.dp) 8.dp + navBarBottom else 28.dp

    // 低版本 / 未登记背景层时走纯色药丸分支，参数仍需要一个 Backdrop 占位。
    val fallbackBackdrop = rememberLayerBackdrop {
        drawRect(Color.Black)
        drawContent()
    }
    val effectiveBackdrop = backdrop ?: fallbackBackdrop
    val blurEnabled = backdrop != null &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    val currentIndex = selectedIndex().coerceIn(0, tabs.size - 1)

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        FloatingBottomBar(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = bottomPadding),
            selectedIndex = currentIndex,
            onSelected = { index ->
                val route = tabs.getOrNull(index)?.route
                if (route != null) {
                    val settle = onSettleTab
                    if (settle != null) settle(index) else onNavigate(route)
                }
            },
            backdrop = effectiveBackdrop,
            tabsCount = tabs.size,
            isBlurEnabled = blurEnabled,
            containerAlpha = 0.4f * backgroundTranslucency,
        ) { activateTab ->
            tabs.forEachIndexed { index, destination ->
                FloatingBottomBarItem(
                    selected = index == currentIndex,
                    onClick = { activateTab(index) },
                    modifier = Modifier.defaultMinSize(minWidth = 76.dp)
                ) {
                    Icon(
                        imageVector = if (index == currentIndex) {
                            destination.selectedIcon
                        } else {
                            destination.unselectedIcon
                        },
                        contentDescription = stringResource(destination.labelRes),
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = stringResource(destination.labelRes),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
