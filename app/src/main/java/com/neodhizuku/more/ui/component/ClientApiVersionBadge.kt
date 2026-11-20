package com.neodhizuku.more.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.neodhizuku.more.R
import com.neodhizuku.more.server.ClientApiVersion

/**
 * ClientApiVersionBadge
 * (version: Int, modifier: Modifier = Modifier,)
 * 作用：应用名下方的 API 版本药丸：API 1 / API 2。 Legacy（原版 Dhizuku 客户端）没有上报版本号，直接不渲染，也不占位。
 * 备注：Compose 组件
 */
@Composable
fun ClientApiVersionBadge(
    version: Int,
    modifier: Modifier = Modifier,
) {
    val text = ClientApiVersion.apiLabel(version)
    if (text.isEmpty()) return
    Box(
        modifier = modifier
            .height(22.dp)
            .background(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(percent = 50)
            )
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/**
 * FullAccessBadge
 * (modifier: Modifier = Modifier)
 * 作用：「完全」药丸：共享服务页勾选后的标识，跟随主题主色与动态取色。 用于应用管理页应用名旁、单项接口页应用卡片上。
 * 备注：Compose 组件
 */
@Composable
fun FullAccessBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(22.dp)
            .background(
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(percent = 50)
            )
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.full_access_badge),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
