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
 * 把两个背景层叠成一个。
 *
 * 药丸要透出两层东西，先画的在下、后画的在上：
 *   first  —— 页面内容（由页面级 rememberLayerBackdrop 提供）。
 *             它带一张不透明的 surface 兜底矩形，所以必须垫在最底下。
 *   second —— 药丸内部的图标 + 文字（由 .alpha(0f).layerBackdrop(tabsBackdrop) 提供）。
 *             这一层除了图标本身都是透明的，叠在页面层上面才看得见。
 * 两者叠加后做折射，才会出现「药丸边缘能看到图标被折弯」的效果。
 */
@Stable
class CombinedBackdrop(
    val first: Backdrop,
    val second: Backdrop,
) : Backdrop {

    override val isCoordinatesDependent: Boolean = first.isCoordinatesDependent || second.isCoordinatesDependent

    override val offsetResidualX: Float get() = first.offsetResidualX
    override val offsetResidualY: Float get() = first.offsetResidualY

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

@Composable
fun rememberCombinedBackdrop(first: Backdrop, second: Backdrop): Backdrop =
    remember(first, second) { CombinedBackdrop(first, second) }
