package com.neodhizuku.more.ui.theme

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.only

/**
 * exclude
 * (sides: WindowInsetsSides)
 * 作用：执行 exclude
 * 备注：—
 */
fun WindowInsets.exclude(sides: WindowInsetsSides): WindowInsets =
    this.exclude(this.only(sides))
