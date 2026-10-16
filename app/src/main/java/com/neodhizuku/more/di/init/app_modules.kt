package com.neodhizuku.more.di.init

import com.neodhizuku.more.di.dataModule
import com.neodhizuku.more.di.reflectModule
import com.neodhizuku.more.di.viewModelModule

val appModules = listOf(
    dataModule,
    viewModelModule,
    reflectModule
)