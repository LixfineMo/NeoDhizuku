package com.neodhizuku.more.ui.page.settings.theme

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.neodhizuku.more.ui.component.NeoDialog as Dialog
import androidx.compose.ui.window.DialogProperties
import com.neodhizuku.more.R
import com.neodhizuku.more.data.common.util.BackgroundImageStore
import com.neodhizuku.more.ui.theme.ThemeConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 与 ThemeConfig 保存变换时使用的边界保持一致；Slider 的 valueRange 必须是 Float 区间。 */
private const val CROP_MIN_SCALE = 1f
private const val CROP_MAX_SCALE = 3f

/**
 * BackgroundCropDialog
 * (initialScale: Float, initialOffsetX: Float, initialOffsetY: Float, onDismiss: () -> Unit)
 * 作用：背景图显示调整：拖动平移、双指或滑杆缩放，预览与实际背景使用同一组参数。 偏移量按预览尺寸归一化保存，换分辨率的设备上不需要重新调整。
 * 备注：Compose 组件
 */
@Composable
fun BackgroundCropDialog(
    initialScale: Float,
    initialOffsetX: Float,
    initialOffsetY: Float,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, context) {
        value = withContext(Dispatchers.IO) {
            val file = BackgroundImageStore.file(context)
            if (!file.exists()) null else BitmapFactory.decodeFile(file.absolutePath)
        }
    }

    var scale by remember {
        mutableFloatStateOf(initialScale.coerceIn(CROP_MIN_SCALE, CROP_MAX_SCALE))
    }
    var offsetX by remember { mutableFloatStateOf(initialOffsetX.coerceIn(-1f, 1f)) }
    var offsetY by remember { mutableFloatStateOf(initialOffsetY.coerceIn(-1f, 1f)) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF101114)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.theme_background_crop_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
                Text(
                    text = stringResource(R.string.theme_background_crop_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF1C1E22))
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                // 首帧尺寸可能为 0，取下限避免除零得到 NaN。
                                val halfW = (size.width * 0.5f).coerceAtLeast(1f)
                                val halfH = (size.height * 0.5f).coerceAtLeast(1f)
                                scale = (scale * zoom)
                                    .coerceIn(CROP_MIN_SCALE, CROP_MAX_SCALE)
                                offsetX = (offsetX + pan.x / halfW).coerceIn(-1f, 1f)
                                offsetY = (offsetY + pan.y / halfH).coerceIn(-1f, 1f)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val source = bitmap
                    if (source != null) {
                        Image(
                            bitmap = remember(source) { source.asImageBitmap() },
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    translationX = offsetX * size.width * 0.5f
                                    translationY = offsetY * size.height * 0.5f
                                    clip = true
                                }
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.theme_background_zoom),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
                Slider(
                    value = scale,
                    onValueChange = { scale = it },
                    valueRange = CROP_MIN_SCALE..CROP_MAX_SCALE,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = {
                        scale = 1f
                        offsetX = 0f
                        offsetY = 0f
                    }) {
                        Text(stringResource(R.string.theme_background_reset))
                    }
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.cancel))
                        }
                        Button(onClick = {
                            ThemeConfig.setBackgroundTransform(context, scale, offsetX, offsetY)
                            onDismiss()
                        }) {
                            Text(stringResource(R.string.confirm))
                        }
                    }
                }
            }
        }
    }
}
