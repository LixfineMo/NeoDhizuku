package com.neodhizuku.more.ui.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 宽屏下侧边导航栏的整体宽度。 */
private val RailWidth: Dp = 88.dp

/** 每一页签占的高度，也是药丸高度。 */
private val RailItemHeight: Dp = 64.dp

/** 胶囊与药丸之间的内缩。 */
private val RailInset: Dp = 4.dp

/**
 * 平板 / 横屏宽屏时替代底部悬浮底栏的竖排导航栏。
 *
 * 与 [FloatingNavBar] 同源：同一个 [NavDestination] 列表、同一套配色，
 * 选中项由一枚胶囊药丸指示，点击交给同一个 onSelectTab（走 Pager 翻页）。
 * 不接管手势拖动，也不做背景采样 —— 宽屏下把复杂折射留给内容区，
 * 避免把 backdrop 录进导航条自己。
 *
 * RTL 下由外层 Row 自动放到右侧，药丸本身水平居中，无需镜像。
 */
@Composable
fun SideNavRail(
    tabs: List<NavDestination>,
    selectedIndex: () -> Int,
    onNavigate: (String) -> Unit,
    liquidGlass: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (tabs.isEmpty()) return

    val scheme = MaterialTheme.colorScheme
    val surface = scheme.surface
    val dark = (surface.red + surface.green + surface.blue) < 1.5f

    val index = selectedIndex().coerceIn(0, tabs.size - 1)
    // 与 Pager 翻页同规格的慢弹簧：药丸不会比页面先到。
    val pillOffset by animateDpAsState(
        targetValue = RailItemHeight * index,
        animationSpec = spring<Dp>(stiffness = 200f),
        label = "railPill"
    )

    val contentHeight = RailItemHeight * tabs.size + RailInset * 2

    val barSurface: Modifier = if (liquidGlass) {
        Modifier
            .shadow(elevation = 16.dp, shape = CircleShape, clip = false)
            .glassSurface(
                tintTop = scheme.surfaceContainer.copy(alpha = if (dark) 0.34f else 0.42f),
                tintBottom = scheme.surfaceContainer.copy(alpha = if (dark) 0.22f else 0.30f),
                specularAlpha = if (dark) 0.45f else 0.80f,
                rimAlpha = if (dark) 0.14f else 0.26f,
                shadeAlpha = if (dark) 0.22f else 0.08f,
                borderColors = glassBorderColors(dark)
            )
    } else {
        Modifier
            .shadow(elevation = 6.dp, shape = CircleShape, clip = false)
            .clip(CircleShape)
            .background(scheme.surfaceContainer)
    }

    val pillSurface: Modifier = if (liquidGlass) {
        Modifier
            .background(scheme.primary.copy(alpha = if (dark) 0.28f else 0.20f), CircleShape)
            .glassSurface(
                tintTop = scheme.primary.copy(alpha = if (dark) 0.28f else 0.20f),
                tintBottom = scheme.primary.copy(alpha = if (dark) 0.18f else 0.12f),
                specularAlpha = if (dark) 0.40f else 0.70f,
                rimAlpha = if (dark) 0.10f else 0.20f,
                shadeAlpha = if (dark) 0.16f else 0.05f,
                borderColors = glassBorderColors(dark),
                specularHeight = 2.dp
            )
    } else {
        Modifier.background(scheme.secondaryContainer, CircleShape)
    }

    Box(
        modifier = modifier
            .padding(start = 12.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)
            .fillMaxHeight()
            .width(RailWidth),
        contentAlignment = Alignment.Center
    ) {
        Box(modifier = Modifier.width(RailWidth).height(contentHeight)) {
            // 竖排胶囊本体
            Box(
                modifier = Modifier
                    .width(RailWidth)
                    .height(contentHeight)
                    .then(barSurface)
            )

            // 选中药丸：沿 Y 轴滑动，与 Pager 同一节奏
            Box(
                modifier = Modifier
                    .padding(horizontal = RailInset)
                    .offset(y = RailInset + pillOffset)
                    .width(RailWidth - RailInset * 2)
                    .height(RailItemHeight)
                    .clip(CircleShape)
                    .then(pillSurface)
            )

            // 页签内容铺在药丸之上，颜色随选中态切换
            Column(
                modifier = Modifier
                    .width(RailWidth)
                    .height(contentHeight)
                    .padding(vertical = RailInset),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                tabs.forEachIndexed { i, destination ->
                    RailItem(
                        destination = destination,
                        current = i == index,
                        onSelect = { onNavigate(destination.route) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RailItem(
    destination: NavDestination,
    current: Boolean,
    onSelect: () -> Unit
) {
    val contentColor = if (current) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Column(
            modifier = Modifier
                .width(RailWidth - RailInset * 2)
                .height(RailItemHeight)
                .clip(CircleShape)
                .clickable(onClick = onSelect)
                .semantics(mergeDescendants = true) {
                    role = Role.Tab
                    selected = current
                },
            verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = if (current) destination.selectedIcon else destination.unselectedIcon,
                contentDescription = stringResource(destination.labelRes)
            )
            Text(
                text = stringResource(destination.labelRes),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
