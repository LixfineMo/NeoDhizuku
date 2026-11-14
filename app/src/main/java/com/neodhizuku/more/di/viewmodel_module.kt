package com.neodhizuku.more.di

import com.neodhizuku.more.ui.page.settings.about.AboutViewModel
import com.neodhizuku.more.ui.page.settings.apipolicy.ApiPolicyViewModel
import com.neodhizuku.more.ui.page.settings.activate.ActivateViewModel
import com.neodhizuku.more.ui.page.settings.app_management.AppManagementViewModel
import com.neodhizuku.more.ui.page.settings.blocklist.BlocklistViewModel
import com.neodhizuku.more.ui.page.settings.calllog.CallLogViewModel
import com.neodhizuku.more.ui.page.settings.debug.DebugViewModel
import com.neodhizuku.more.ui.page.settings.debug.RuntimeInfoViewModel
import com.neodhizuku.more.ui.page.settings.debug.RuntimeParamViewModel
import com.neodhizuku.more.ui.page.settings.debug.ServiceConfigViewModel
import com.neodhizuku.more.ui.page.settings.debug.ServiceFileEditViewModel
import com.neodhizuku.more.ui.page.settings.delegation.DelegationViewModel
import com.neodhizuku.more.ui.page.settings.experimental.Cve98432ViewModel
import com.neodhizuku.more.ui.page.settings.experimental.ExperimentalViewModel
import com.neodhizuku.more.ui.page.settings.security.SecurityViewModel
import com.neodhizuku.more.ui.page.settings.settings.SettingsViewModel
import com.neodhizuku.more.ui.page.settings.updatesettings.UpdateSettingsViewModel
import com.neodhizuku.more.ui.page.settings.whitelist.WhitelistViewModel

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
    // CVE98432 实验页用 koinViewModel()，漏注册会在进页面时抛
    // NoDefinitionFoundException 并崩掉整个 Composition。
    viewModel {
        Cve98432ViewModel()
    }
    viewModel {
        DelegationViewModel()
    }
    viewModel {
        AboutViewModel()
    }
    viewModel {
        DebugViewModel()
    }
    // 白名单管理页。漏注册会让页面一进入就抛
    // NoDefinitionFoundException 并崩掉整个 Composition。
    viewModel {
        WhitelistViewModel()
    }
    viewModel {
        UpdateSettingsViewModel()
    }
    // 单项接口配置页。漏注册会让页面一进入就抛
    // NoDefinitionFoundException 并崩掉整个 Composition。
    viewModel {
        ApiPolicyViewModel()
    }
    // 调试分区的四个二级页面统一用 koinViewModel()，必须逐个注册，
    // 否则一进入就抛 NoDefinitionFoundException。
    viewModel {
        RuntimeParamViewModel()
    }
    viewModel {
        RuntimeInfoViewModel()
    }
    viewModel {
        ServiceConfigViewModel()
    }
    viewModel {
        ServiceFileEditViewModel()
    }
}