package com.neodhizuku.more.ui.component.glass

import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 折射：把面板自身绘制的内容按圆角矩形的距离场做坐标偏移采样。
 * 着色器是一段圆角矩形距离场折射（ROUNDED_RECT_REFRACTION_WITH_DISPERSION_SHADER），
 * 调用方式走 Android 自带的 [AndroidRenderEffect.createRuntimeShaderEffect]：
 * 它把该层已绘制的内容绑到着色器的 `content` 上，不需要任何第三方库。
 * 注意这里用的是 android.graphics 下的 RuntimeShader / RenderEffect，
 * 而不是 androidx.compose.ui.graphics 下的同名类：Compose 侧的 RenderEffect
 * 只是包装类型，其 companion 没有 createRuntimeShaderEffect，需要先在平台侧
 * 构造好再用 asComposeRenderEffect() 转成 Compose 可赋给 graphicsLayer 的类型。
 * 折射强度在圆角附近最大、往中心衰减到 0，这是玻璃与塑料的分界：
 * 整块均匀偏移看着像贴纸，只有边缘弯曲才有厚度感。
 * [chromaticAberration] 大于 0 时开启色散，沿对角线把 RGB 拆成七次采样，
 * 边缘出现彩虹边。这是 iOS 液态玻璃最标志性的一层，也是唯一能让人一眼
 * 认出「这是玻璃而不是半透明矩形」的细节。
 * 需要 API 33（AGSL / RuntimeShader）。低版本直接返回原 [Modifier]，
 * 由 [com.neodhizuku.more.ui.component.glass.glassPanel] 里的几何折射兜底。
 */
/**
 * 真 AGSL 折射的总开关。
 * 默认关闭：`graphicsLayer { renderEffect }` 一旦工作异常，表现不是"效果差"
 * 而是**整层内容变透明**（着色器采样失败时返回空色），底栏会整块消失。
 * 关闭时由 [glassPanel] 的几何折射兜底，视觉损失有限但不会丢内容。
 * 置为 true 可开启调试效果。
 */
internal const val GLASS_REFRACTION_SHADER_ENABLED: Boolean = false

@Composable
internal fun Modifier.glassLens(
    refractionHeight: Dp = 24.dp,
    refractionAmount: Dp = 24.dp,
    depthEffect: Boolean = false,
    chromaticAberration: () -> Float = { 0f },
    progress: () -> Float = { 1f },
): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return this
    if (refractionHeight <= 0.dp || refractionAmount <= 0.dp) return this

    if (!GLASS_REFRACTION_SHADER_ENABLED) return this

    val shader = remember { RuntimeShader(REFRACTION_SHADER) }
    val effect = remember(shader) {
        AndroidRenderEffect.createRuntimeShaderEffect(shader, "content")
            .asComposeRenderEffect()
    }

    return this.graphicsLayer {
        // 尺寸还没测出来就挂上 renderEffect，着色器里 halfSize 是 (0,0)，
        // normalize(零向量) 会得到 NaN，采样坐标变成 NaN —— 表现为整层透明。
        // 未测量前一律不挂。
        if (size.width <= 0f || size.height <= 0f) {
            renderEffect = null
            return@graphicsLayer
        }

        // 着色器用的坐标系以面板中心为原点，圆角半径按最短边收敛。
        // 强度按按压进度缩放：手指按下去时玻璃被"压"得更弯。
        val radius = size.minDimension * 0.5f
        val p = progress()
        shader.setFloatUniform("size", size.width, size.height)
        shader.setFloatUniform("offset", 0f, 0f)
        shader.setFloatUniform(
            "cornerRadii",
            floatArrayOf(radius, radius, radius, radius)
        )
        shader.setFloatUniform("refractionHeight", refractionHeight.toPx() * p)
        shader.setFloatUniform("refractionAmount", -refractionAmount.toPx() * p)
        shader.setFloatUniform("depthEffect", if (depthEffect) 1f else 0f)
        shader.setFloatUniform("chromaticAberration", chromaticAberration())
        renderEffect = effect
    }
}

/** 圆角矩形的有向距离场：折射只发生在 `sd` 为负（形状内部）的一侧。 */
private const val ROUNDED_RECT_SDF = """
float radiusAt(float2 coord, float4 radii) {
    if (coord.x >= 0.0) {
        if (coord.y <= 0.0) return radii.y;
        else return radii.z;
    } else {
        if (coord.y <= 0.0) return radii.x;
        else return radii.w;
    }
}

float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    float outside = length(max(cornerCoord, 0.0)) - radius;
    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);
    return outside + inside;
}

float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));
    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {
        return sign(coord) * normalize(max(cornerCoord, 0.0));
    } else {
        float gradX = step(cornerCoord.y, cornerCoord.x);
        return sign(coord) * float2(gradX, 1.0 - gradX);
    }
}
"""

private const val REFRACTION_SHADER = """
uniform shader content;

uniform float2 size;
uniform float2 offset;
uniform float4 cornerRadii;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;
uniform float chromaticAberration;

$ROUNDED_RECT_SDF

float circleMap(float x) {
    return 1.0 - sqrt(1.0 - x * x);
}

half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centeredCoord = (coord + offset) - halfSize;
    float radius = radiusAt(coord, cornerRadii);

    float sd = sdRoundedRect(centeredCoord, halfSize, radius);
    if (-sd >= refractionHeight) {
        return content.eval(coord);
    }
    sd = min(sd, 0.0);

    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + depthEffect * normalize(centeredCoord));

    float2 refractedCoord = coord + d * grad;
    float dispersionIntensity = chromaticAberration * ((centeredCoord.x * centeredCoord.y) / (halfSize.x * halfSize.y));
    float2 dispersedCoord = d * grad * dispersionIntensity;

    half4 color = half4(0.0);

    half4 red = content.eval(refractedCoord + dispersedCoord);
    color.r += red.r / 3.5;
    color.a += red.a / 7.0;

    half4 orange = content.eval(refractedCoord + dispersedCoord * (2.0 / 3.0));
    color.r += orange.r / 3.5;
    color.g += orange.g / 7.0;
    color.a += orange.a / 7.0;

    half4 yellow = content.eval(refractedCoord + dispersedCoord * (1.0 / 3.0));
    color.r += yellow.r / 3.5;
    color.g += yellow.g / 3.5;
    color.a += yellow.a / 7.0;

    half4 green = content.eval(refractedCoord);
    color.g += green.g / 3.5;
    color.a += green.a / 7.0;

    half4 cyan = content.eval(refractedCoord - dispersedCoord * (1.0 / 3.0));
    color.g += cyan.g / 3.5;
    color.b += cyan.b / 3.0;
    color.a += cyan.a / 7.0;

    half4 blue = content.eval(refractedCoord - dispersedCoord * (2.0 / 3.0));
    color.b += blue.b / 3.0;
    color.a += blue.a / 7.0;

    half4 purple = content.eval(refractedCoord - dispersedCoord);
    color.r += purple.r / 7.0;
    color.b += purple.b / 3.0;
    color.a += purple.a / 7.0;

    return color;
}
"""
