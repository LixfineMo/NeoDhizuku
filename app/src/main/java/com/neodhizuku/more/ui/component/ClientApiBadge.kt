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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.neodhizuku.more.server.ClientApiVersion

/**
 * 客户端类型标签，放在应用卡片右上角。
 * Modern 用主题主色，Legacy 用主题的中性容器色，两者都跟随动态取色；
 * 不再用写死的灰，避免浅色/深色主题下与卡片底色混成一片。
 */
@Composable
fun ClientApiBadge(
    version: Int,
    modifier: Modifier = Modifier,
) {
    val modern = ClientApiVersion.isModern(version)
    val container = if (modern) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val content = if (modern) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = modifier
            .height(24.dp)
            .background(color = container, shape = RoundedCornerShape(percent = 50))
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = ClientApiVersion.typeLabel(version),
            style = MaterialTheme.typography.labelSmall,
            color = content,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
