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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

import com.neodhizuku.more.data.common.util.BackgroundImageStore

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 自定义背景图。
 * 图片本身做一次整体模糊，再压一层中性黑遮罩让四周略深、文字仍可读。
 * 卡片采样的是遮罩之前的图片层，所以同是模糊玻璃但比四周亮一点。
 * 模糊走 RenderEffect（Android 12 起可用），更低版本就不做模糊。
 */
@Composable
fun AppBackground(
    blurRadiusDp: Float,
    dimAmount: Float,
    modifier: Modifier = Modifier,
    /** 由调用方把图片层登记进卡片采样源（Modifier.layerBackdrop）；遮罩在其后绘制。 */
    imageLayer: Modifier = Modifier
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

    // 采样源里也压一层中性黑：卡片与四周都从同一张偏暗的图取样，
    // 只是深浅差一点，浅色主题下不会再透出一块白。四周补剩下的差值，
    // 总暗化量与之前一致。
    val cardScrimAlpha = (dimAmount * 0.30f).coerceIn(0f, 0.16f)
    val outerScrimAlpha = (dimAmount * 0.55f).coerceIn(0f, 0.32f)
    val sampledImageModifier = imageModifier
        .then(
            if (cardScrimAlpha > 0f) Modifier.drawWithContent {
                drawContent()
                drawRect(Color.Black.copy(alpha = cardScrimAlpha))
            } else Modifier
        )
        .then(imageLayer)

    Box(modifier = modifier.fillMaxSize()) {
        Image(
            bitmap = remember(source) { source.asImageBitmap() },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = sampledImageModifier
        )
        // 四周用中性黑压暗，而不是主题背景色：浅色主题下那层是白的，
        // 会把整片背景洗成纯白，只剩卡片里透得出颜色。
        // 卡片采样的是遮罩之前的图片层，所以自然比四周亮一点。
        // 采样源已含 cardScrimAlpha，这里只补差值即可。
        val scrimAlpha = (outerScrimAlpha - cardScrimAlpha).coerceAtLeast(0f)
        if (scrimAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha))
            )
        }
    }
}
