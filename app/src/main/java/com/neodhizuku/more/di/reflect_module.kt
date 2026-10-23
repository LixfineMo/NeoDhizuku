package com.neodhizuku.more.di

import android.os.Build

import org.koin.dsl.module


val reflectModule = module {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
        com.neodhizuku.more.server.HiddenApiExempt.once()
}