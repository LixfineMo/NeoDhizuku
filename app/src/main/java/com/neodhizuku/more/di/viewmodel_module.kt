package com.neodhizuku.more.di

import com.neodhizuku.more.ui.page.settings.about.AboutViewModel
import com.neodhizuku.more.ui.page.settings.activate.ActivateViewModel
import com.neodhizuku.more.ui.page.settings.app_management.AppManagementViewModel
import com.neodhizuku.more.ui.page.settings.blocklist.BlocklistViewModel
import com.neodhizuku.more.ui.page.settings.calllog.CallLogViewModel
import com.neodhizuku.more.ui.page.settings.delegation.DelegationViewModel
import com.neodhizuku.more.ui.page.settings.experimental.ExperimentalViewModel
import com.neodhizuku.more.ui.page.settings.security.SecurityViewModel
import com.neodhizuku.more.ui.page.settings.settings.SettingsViewModel

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val viewModelModule = module {
    viewModel {
        AppManagementViewModel()
    }
    viewModel {
        ActivateViewModel()
    }
    viewModel {
        SettingsViewModel()
    }
    viewModel {
        BlocklistViewModel()
    }
    viewModel {
        CallLogViewModel()
    }
    viewModel {
        SecurityViewModel()
    }
    viewModel {
        ExperimentalViewModel()
    }
    viewModel {
        DelegationViewModel()
    }
    viewModel {
        AboutViewModel()
    }
}