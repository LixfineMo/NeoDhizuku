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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.neodhizuku.more.server.ClientApiVersion

/**
 * 客户端 API 版本标签（药丸）。
 *
 * - **old**（灰色）：对方用的是原版 Dhizuku-API，没有上报版本号。
 *   灰色而非主题色，是为了在视觉上退到次要层——它只是"能用"，不是"本应用推荐的状态"。
 * - **modern-N**（主题色）：对方用的是新版 API，N 为上报的版本号。
 *
 * 高度固定 24dp，与授权卡片里的应用名（titleMedium）行高一致，
 * 两者并排时视觉上等高。
 *
 * 底色上的文字固定用白色：主题色在浅色/深色下都足够深以承载白字，
 * 而 old 用的中性灰也在两个主题下都保持对比度，按主题切换反而会出现
 * "浅色主题下灰底白字看不清"的情况。
 */
@Composable
fun ClientApiBadge(
    version: Int,
    modifier: Modifier = Modifier,
) {
    val isModern = ClientApiVersion.isModern(version)
    val container = if (isModern) {
        MaterialTheme.colorScheme.primary
    } else {
        // 中性灰，不随主题漂移：old 表示"未识别到版本"，
        // 用主题里的 surfaceVariant 会在深色主题下过亮、浅色主题下过淡。
        Color(0xFF8A8A8A)
    }
    Box(
        modifier = modifier
            .height(24.dp)
            .background(color = container, shape = RoundedCornerShape(percent = 50))
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = ClientApiVersion.label(version),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
