package com.neodhizuku.more.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection

/**
 * combine
 * (other: PaddingValues)
 * 作用：执行 combine
 * 备注：Compose 组件
 */
@Composable
fun PaddingValues.combine(other: PaddingValues): PaddingValues {
    val layoutDirection = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(layoutDirection)
                + other.calculateStartPadding(layoutDirection),
        top = calculateTopPadding() + other.calculateTopPadding(),
        end = calculateEndPadding(layoutDirection)
                + other.calculateEndPadding(layoutDirection),
        bottom = calculateBottomPadding() + other.calculateBottomPadding()
    )
}

/**
 * combineBottom
 * (extra: androidx.compose.ui.unit.Dp)
 * 作用：执行 combineBottom
 * 备注：Compose 组件
 */
@Composable
fun PaddingValues.combineBottom(extra: androidx.compose.ui.unit.Dp): PaddingValues {
    val layoutDirection = LocalLayoutDirection.current
    return PaddingValues(
        start = calculateStartPadding(layoutDirection),
        top = calculateTopPadding(),
        end = calculateEndPadding(layoutDirection),
        bottom = calculateBottomPadding() + extra
    )
}