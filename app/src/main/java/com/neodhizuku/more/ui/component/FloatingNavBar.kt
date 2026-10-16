package com.neodhizuku.more.ui.component

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

import com.neodhizuku.more.NeoLog
import com.neodhizuku.more.R

/** 底部悬浮导航栏的四个目标页。 */
enum class NavDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    /** 仅在已激活（isOwner）时显示 */
    val requiresOwner: Boolean = false
) {
    Home(
        route = "home",
        labelRes = R.string.nav_home,
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home
    ),
    AppManagement(
        route = "app_management",
        labelRes = R.string.nav_app_management,
        selectedIcon = Icons.Filled.Apps,
        unselectedIcon = Icons.Outlined.Apps,
        requiresOwner = true
    ),
    CallLog(
        route = "call_log",
        labelRes = R.string.nav_call_log,
        selectedIcon = Icons.Filled.List,
        unselectedIcon = Icons.Outlined.List
    ),
    Settings(
        route = "settings",
        labelRes = R.string.nav_settings,
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )
}

@Composable
fun FloatingNavBar(
    isOwner: Boolean,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = NavDestination.entries.filter { !it.requiresOwner || isOwner }

    // 记录底栏实际占位，用于判断它是否真的落在可点击范围内。
    // 记录底栏实际尺寸：若 width 远小于屏幕宽度，说明只占了内容宽度，两侧点不到。
    var barWidth by remember { mutableStateOf(0) }
    var barHeight by remember { mutableStateOf(0) }

    Surface(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = 12.dp)
            .onGloballyPositioned { coords ->
                if (coords.size.width != barWidth || coords.size.height != barHeight) {
                    barWidth = coords.size.width
                    barHeight = coords.size.height
                    NeoLog.d(NeoLog.NAV) { "bar size -> ${barWidth}x${barHeight}" }
                }
            },
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp
    ) {
        NavigationBar(
            // NavigationBar 默认按内容测量，不撑满父容器，
            // 会让外层 Surface 跟着变窄，两侧就点不到了。
            modifier = Modifier.fillMaxWidth(),
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            tonalElevation = 0.dp,
            windowInsets = WindowInsets(0.dp),
            content = {
                items.forEach { destination ->
                    val selected = currentRoute == destination.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            NeoLog.d(NeoLog.NAV) {
                                "item click: ${destination.route}, selected=$selected"
                            }
                            if (!selected) onNavigate(destination.route)
                        },
                        icon = {
                            Icon(
                                imageVector = if (selected) destination.selectedIcon
                                else destination.unselectedIcon,
                                contentDescription = stringResource(destination.labelRes)
                            )
                        },
                        label = {
                            Text(
                                text = stringResource(destination.labelRes),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    )
                }
            }
        )
    }
}
