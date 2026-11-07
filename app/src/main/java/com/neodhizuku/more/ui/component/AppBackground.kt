package com.neodhizuku.more.ui.component

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RenderEffect as PlatformRenderEffect
import android.graphics.Shader
import android.os.Build

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

import com.neodhizuku.more.data.common.util.BackgroundImageStore

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 自定义背景图。
 *
 * 图片本身做一次整体模糊，再压一层与主题背景同色的遮罩，
 * 保证叠在上面的文字仍然可读。
 * 模糊走 RenderEffect（Android 12 起可用），更低版本就不做模糊。
 */
@Composable
fun AppBackground(
    blurRadiusDp: Float,
    dimAmount: Float,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val bitmap by produceState<Bitmap?>(initialValue = null, context) {
        value = withContext(Dispatchers.IO) {
            if (!BackgroundImageStore.exists(context)) null
            else BitmapFactory.decodeFile(BackgroundImageStore.file(context).absolutePath)
        }
    }

    val source = bitmap ?: return

    val density = LocalDensity.current
    val radiusPx = remember(blurRadiusDp, density) {
        with(density) { blurRadiusDp.coerceAtLeast(0f).dp.toPx() }
    }

    val imageModifier = if (radiusPx > 0f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                renderEffect = PlatformRenderEffect.createBlurEffect(
                    radiusPx,
                    radiusPx,
                    Shader.TileMode.CLAMP
                ).asComposeRenderEffect()
            }
    } else {
        Modifier.fillMaxSize()
    }

    Box(modifier = modifier.fillMaxSize()) {
        Image(
            bitmap = remember(source) { source.asImageBitmap() },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = imageModifier
        )
        if (dimAmount > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        MaterialTheme.colorScheme.background.copy(
                            alpha = dimAmount.coerceIn(0f, 1f)
                        )
                    )
            )
        }
    }
}
