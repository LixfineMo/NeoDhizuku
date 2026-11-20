package com.neodhizuku.more.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.neodhizuku.more.ui.theme.LocalUiEngine
import com.neodhizuku.more.ui.theme.UiEngine
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.CardDefaults as MiuixCardDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * NeoAlertDialog
 * (onDismissRequest: () -> Unit, confirmButton: @Composable () -> Unit, modifier: Modifier = Modifier, dismissButton: @Composable (() -> Unit)? = null, icon: @Composable (() -> Unit)? = null, title: @Composable (() -> Unit)? = null, text: @Composable (() -> Unit)? = null, shape: Shape = AlertDialogDefaults.shape, containerColor: Color = AlertDialogDefaults.containerColor, iconContentColor: Color = AlertDialogDefaults.iconContentColor, titleContentColor: Color = AlertDialogDefaults.titleContentColor, textContentColor: Color = AlertDialogDefaults.textContentColor, tonalElevation: Dp = AlertDialogDefaults.TonalElevation, properties: DialogProperties = DialogProperties())
 * 作用：全应用统一的确认弹窗。原版引擎原样转发 Material3；miuix 引擎用组件库的 Card 做大圆角容器，并把配色灌回 MaterialTheme，弹窗里的文字与按钮也跟着走。
 * 备注：Compose 组件
 */
@Composable
fun NeoAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = AlertDialogDefaults.containerColor,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties()
) {
    if (LocalUiEngine.current != UiEngine.MIUIX) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = confirmButton,
            modifier = modifier,
            dismissButton = dismissButton,
            icon = icon,
            title = title,
            text = text,
            shape = shape,
            containerColor = containerColor,
            iconContentColor = iconContentColor,
            titleContentColor = titleContentColor,
            textContentColor = textContentColor,
            tonalElevation = tonalElevation,
            properties = properties
        )
        return
    }

    val miuix = MiuixTheme.colorScheme
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties
    ) {
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme.copy(
                primary = miuix.primary,
                surface = miuix.surface,
                onSurface = miuix.onSurface,
                onSurfaceVariant = miuix.onSurface.copy(alpha = 0.6f),
                surfaceContainerHigh = miuix.surface,
                surfaceContainer = miuix.surface
            )
        ) {
            MiuixCard(
                // 兜底：内容再长也不把底部按钮行挤出屏幕（具体列表页仍应自行
                // 限高 + 滚动，这里只防止其它弹窗出现同类溢出）。
                modifier = modifier
                    .widthIn(min = 280.dp)
                    .widthIn(max = 420.dp)
                    .heightIn(max = 560.dp),
                cornerRadius = 28.dp,
                insideMargin = PaddingValues(24.dp),
                colors = MiuixCardDefaults.defaultColors(
                    color = if (containerColor == AlertDialogDefaults.containerColor) {
                        miuix.surface
                    } else {
                        containerColor
                    }
                )
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (icon != null) {
                        Box(modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            CompositionLocalProvider(LocalContentColor provides iconContentColor) {
                                icon()
                            }
                        }
                    }
                    if (title != null) {
                        CompositionLocalProvider(LocalContentColor provides titleContentColor) {
                            title()
                        }
                    }
                    if (text != null) {
                        CompositionLocalProvider(LocalContentColor provides textContentColor) {
                            text()
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (dismissButton != null) {
                            dismissButton()
                            Spacer(Modifier.width(8.dp))
                        }
                        confirmButton()
                    }
                }
            }
        }
    }
}

/**
 * NeoDialog
 * (onDismissRequest: () -> Unit, properties: DialogProperties = DialogProperties(), content: @Composable () -> Unit)
 * 作用：全屏 / 自定义内容弹窗，miuix 引擎下把主题色灌回内容树。
 * 备注：Compose 组件
 */
@Composable
fun NeoDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit
) {
    if (LocalUiEngine.current != UiEngine.MIUIX) {
        Dialog(
            onDismissRequest = onDismissRequest,
            properties = properties,
            content = content
        )
        return
    }

    val miuix = MiuixTheme.colorScheme
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties
    ) {
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme.copy(
                primary = miuix.primary,
                surface = miuix.surface,
                onSurface = miuix.onSurface,
                onSurfaceVariant = miuix.onSurface.copy(alpha = 0.6f),
                surfaceContainerHigh = miuix.surface,
                surfaceContainer = miuix.surface
            )
        ) {
            content()
        }
    }
}
