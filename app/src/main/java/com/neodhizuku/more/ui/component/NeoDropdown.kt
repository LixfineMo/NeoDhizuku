package com.neodhizuku.more.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon as MaterialIcon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text as MaterialText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neodhizuku.more.ui.theme.LocalUiEngine
import com.neodhizuku.more.ui.theme.UiEngine
import top.yukonga.miuix.kmp.basic.DropdownDefaults
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowListPopup

/**
 * NeoDropdownMenu
 * (expanded: Boolean, onDismissRequest: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit)
 * 作用：全应用统一的下拉菜单。 miuix 引擎走组件库的 WindowListPopup + ListPopupColumn，条目也是 miuix 的 Text / Icon / DropdownDefaults 配色；原版引擎保留自绘的深色大圆角菜单。
 * 备注：Compose 组件
 */
@Composable
fun NeoDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    if (LocalUiEngine.current == UiEngine.MIUIX) {
        // WindowListPopup 是窗口级弹层，不依赖 miuix 的 Scaffold / MiuixPopupHost，
        // 正好适配本工程的 Material3 Scaffold；OverlayListPopup 在这里会不渲染。
        WindowListPopup(
            show = expanded,
            onDismissRequest = onDismissRequest
        ) {
            ListPopupColumn {
                content()
            }
        }
        return
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier
            .widthIn(min = 220.dp)
            .heightIn(max = 420.dp),
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        shadowElevation = 10.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)),
        content = { content() }
    )
}

/**
 * 统一的下拉项。miuix 引擎下文字、对勾、配色全部来自组件库。
 * @param selected 是否选中，选中时显示右侧对勾并给文字上主色
 * @param badge 右侧辅助说明（例如「翻译不完整」），与对勾可同时存在
 */
@Composable
fun NeoDropdownItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    badge: String? = null,
    enabled: Boolean = true
) {
    val miuix = LocalUiEngine.current == UiEngine.MIUIX
    // 容器与列由组件库提供，条目内只做排版与点击。
    val dropdownColors = if (miuix) DropdownDefaults.dropdownColors() else null
    val primary = if (miuix) MiuixTheme.colorScheme.primary else MaterialTheme.colorScheme.primary
    val onSurface = dropdownColors?.contentColor ?: MaterialTheme.colorScheme.onSurface
    // miuix 的 Colors 没有 onSurfaceVariant；用基础角色 onSurface 淡化，
    // 不依赖 0.9.4 才新增的语义槽位。
    val onSurfaceVariant = if (miuix) MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    else MaterialTheme.colorScheme.onSurfaceVariant
    val selectedColor = if (selected) primary else onSurface

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (miuix) 48.dp else 54.dp)
            .clip(RoundedCornerShape(if (miuix) 14.dp else 12.dp))
            .then(
                if (dropdownColors != null) Modifier.background(dropdownColors.containerColor)
                else Modifier
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = LocalIndication.current,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (miuix) {
            MiuixText(
                text = text,
                modifier = Modifier.weight(1f),
                fontSize = 17.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = selectedColor
            )
        } else {
            MaterialText(
                text = text,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 18.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                ),
                color = selectedColor
            )
        }
        if (!badge.isNullOrEmpty()) {
            Spacer(Modifier.width(10.dp))
            if (miuix) {
                MiuixText(text = badge, fontSize = 12.sp, color = onSurfaceVariant)
            } else {
                MaterialText(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = onSurfaceVariant
                )
            }
        }
        if (selected) {
            Spacer(Modifier.width(10.dp))
            if (miuix) {
                MiuixIcon(
                    imageVector = Icons.TwoTone.Check,
                    contentDescription = null,
                    tint = primary,
                    modifier = Modifier.size(22.dp)
                )
            } else {
                MaterialIcon(
                    imageVector = Icons.TwoTone.Check,
                    contentDescription = null,
                    tint = primary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
