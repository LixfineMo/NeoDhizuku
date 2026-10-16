package com.neodhizuku.more.di

import com.neodhizuku.more.data.settings.model.preferences.impl.SettingsRepoImpl
import com.neodhizuku.more.data.settings.model.room.DhizukuRoom
import com.neodhizuku.more.data.settings.model.room.impl.AppRepoImpl
import com.neodhizuku.more.data.settings.model.room.impl.LogRepoImpl
import com.neodhizuku.more.data.settings.repo.AppRepo
import com.neodhizuku.more.data.settings.repo.LogRepo
import com.neodhizuku.more.data.settings.repo.SettingsRepo

import org.koin.dsl.module

val dataModule = module {
    single {
        DhizukuRoom.createInstance()
    }

    single<AppRepo> {
        val room = get<DhizukuRoom>()
        AppRepoImpl(room.appDao)
    }

    single<LogRepo> {
        LogRepoImpl()
    }

    single<SettingsRepo> {
        SettingsRepoImpl()
    }
}