package com.neodhizuku.more.ui.component.liquid

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import top.yukonga.miuix.kmp.blur.Backdrop

/**
 * 把两个背景层按顺序叠成一个：先画的在下，后画的在上。
 * 与 KernelSU / NPatch 的 FloatingBottomBar 一致，药丸从
 * 「页面层 + 不可见页签层」一起采样，边缘折射才会倒转底栏及其上下背景。
 * 药丸内部不再单独渲染可见内容，因此不会出现两份图标并存的重影。
 */
@Stable
class CombinedBackdrop(
    val first: Backdrop,
    val second: Backdrop,
) : Backdrop {

    override val isCoordinatesDependent: Boolean = first.isCoordinatesDependent || second.isCoordinatesDependent

    override val offsetResidualX: Float get() = first.offsetResidualX
    override val offsetResidualY: Float get() = first.offsetResidualY

    /**
     * drawBackdrop
     * (density: Density, coordinates: LayoutCoordinates?, layerBlock: (GraphicsLayerScope.() -> Unit)?, downscaleFactor: Int,)
     * 作用：执行 drawBackdrop
     * 备注：重写父类 / 接口方法
     */
    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
        downscaleFactor: Int,
    ) {
        with(first) { drawBackdrop(density, coordinates, layerBlock, downscaleFactor) }
        with(second) { drawBackdrop(density, coordinates, layerBlock, downscaleFactor) }
    }
}

/**
 * rememberCombinedBackdrop
 * (first: Backdrop, second: Backdrop)
 * 作用：执行 rememberCombinedBackdrop
 * 备注：Compose 组件
 */
@Composable
fun rememberCombinedBackdrop(first: Backdrop, second: Backdrop): Backdrop =
    remember(first, second) { CombinedBackdrop(first, second) }
