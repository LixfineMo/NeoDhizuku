package com.neodhizuku.more.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.neodhizuku.more.ui.theme.AppIconCache

/** 转移确认弹窗里的目标应用信息：图标、应用名称、包名（不含接收器名）。 */
@Composable
fun TransferTargetInfo(
    packageName: String,
    appLabel: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appInfo = remember(packageName) {
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getApplicationInfo(packageName, 0)
        }.getOrNull()
    }
    val fallback = remember {
        runCatching {
            ContextCompat.getDrawable(context, android.R.drawable.sym_def_app_icon)
                ?.toBitmap()?.asImageBitmap()
        }.getOrNull()
    }
    val icon: ImageBitmap? = if (appInfo != null) {
        AppIconCache.rememberImageBitmapState(appInfo).value
    } else {
        fallback
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = null,
                modifier = Modifier.size(44.dp)
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(appLabel, style = MaterialTheme.typography.titleMedium)
            Text(
                packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
