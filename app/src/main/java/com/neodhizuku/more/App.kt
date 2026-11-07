package com.neodhizuku.more

import android.app.Application
import android.util.Log

import com.google.android.material.color.DynamicColors

import com.neodhizuku.more.di.init.appModules
import com.neodhizuku.more.data.common.util.StartupUpdateChecker
import com.neodhizuku.more.data.settings.repo.SettingsRepo
import com.neodhizuku.more.server.DhizukuState
import com.neodhizuku.more.server.ManagerIntegrity

import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.context.startKoin
import org.koin.dsl.module

import rikka.sui.Sui

class App : Application(), KoinComponent {
    override fun onCreate() {
        super.onCreate()
        com.neodhizuku.more.data.settings.UiFlags.init(this)
        startKoin {
            androidLogger()
            androidContext(this@App)
            modules(appModules)
            modules(module { single { this@App } })
        }
        // 把应用层开关同步给 server_api / client api 两个模块，
        // 这样只需要改 NeoDhizuku.UseNeoDhizuku 一个常量即可全局启停。
        syncNeoDhizukuSwitches()
        // 「完整运行日志」开关先挂上，否则日志会一直走精简模式
        NeoLog.bindVerboseLog()
        NeoLog.dumpSwitches()
        NeoLog.startFlushLoop()
        // 免删模式标记先落内存，首页身份标签才能立刻显示"设备所有者（临时）"
        com.neodhizuku.more.data.common.util.DpDirectActivation.refreshTemporary(this)
        DhizukuState.sync(this)
        NeoLog.i(NeoLog.BOOT) {
            "能力等级=${DhizukuState.state.level} " +
                "isOwner=${DhizukuState.state.isOwner} " +
                "isActive=${DhizukuState.state.isActive} " +
                "临时所有者=${com.neodhizuku.more.data.common.util.DpDirectActivation.isTemporaryCached()}"
        }
        Sui.init(packageName)
        // 每次打开都从官网查一次有没有新版本；只提示不打扰，失败也不影响使用。
        // 「启动时检查更新」关掉时整段跳过，且不会占用进程内那一次检查机会。
        runCatching {
            val repo = get<SettingsRepo>()
            if (repo.isStartupUpdateCheck) {
                StartupUpdateChecker.checkOnce(repo)
            } else {
                NeoLog.i(NeoLog.UI) { "启动检查：已在更新设置中关闭" }
            }
        }.onFailure {
            NeoLog.w(NeoLog.UI) { "启动更新检查未能启动：${it.message}" }
        }
        DynamicColors.applyToActivitiesIfAvailable(this)
        exemptHiddenApiIfNeeded()
        ManagerIntegrity.verifyOnStartup(this)
    }

    /**
     * 新的激活路径直接调用 android.app.admin.IDevicePolicyManager（隐藏接口）。
     * Android 9+ 的非 SDK 接口限制会拦截它，这里沿用项目已引入的 HiddenApiBypass 解除限制。
     */
    private fun exemptHiddenApiIfNeeded() {
        if (!NeoDhizuku.on(NeoDhizuku.FixReflectionDpm)) return
        runCatching {
            com.neodhizuku.more.server.HiddenApiExempt.once()
        }.onFailure {
            Log.w("Dhizuku", "hidden api exemption failed", it)
        }
    }

    /**
     * 把总开关与分项开关同步到 server_api（Variables）与 client api（DhizukuVariables）。
     *
     * 这里**刻意使用反射**而不是直接赋值：
     * app 可能编译/运行在两种模式下：
     *   1) 使用本地 Dhizuku-API 源码（composite build）—— 这些字段存在，同步生效；
     *   2) 使用 Maven 上的官方包 —— 这些字段不存在，直接赋值会导致
     *      "Unresolved reference" 编译失败。反射在字段缺失时静默跳过。
     *
     * 在模式 2 下，API 侧的改动本来就不存在（settings.gradle.kts 会打印警告），
     * 因此跳过同步是正确的行为。
     */
    private fun syncNeoDhizukuSwitches() {
        val values = mapOf(
            "UseNeoDhizuku" to NeoDhizuku.UseNeoDhizuku,
            "FixUserServiceBindArgs" to NeoDhizuku.FixUserServiceBindArgs,
            "FixUserServiceLifecycle" to NeoDhizuku.FixUserServiceLifecycle,
            "FixApiVersionName" to NeoDhizuku.FixApiVersionName,
            "HardenProviderAuth" to NeoDhizuku.HardenProviderAuth,
            "HardenRemoteProcessAudit" to NeoDhizuku.HardenRemoteProcessAudit,
            "HardenPermissionEntry" to NeoDhizuku.HardenPermissionEntry
        )
        applySwitches("com.rosan.dhizuku.server_api.Variables", values)
        // client api 侧目前只有 HardenPermissionEntry 与 UseNeoDhizuku 两个开关
        applySwitches(
            "com.rosan.dhizuku.shared.DhizukuVariables",
            mapOf(
                "UseNeoDhizuku" to NeoDhizuku.UseNeoDhizuku,
                "HardenPermissionEntry" to NeoDhizuku.HardenPermissionEntry
            )
        )
    }

    private fun applySwitches(className: String, values: Map<String, Boolean>) {
        val clazz = runCatching { Class.forName(className) }.getOrNull() ?: return
        values.forEach { (name, value) ->
            runCatching {
                val field = clazz.getDeclaredField(name)
                field.isAccessible = true
                // 只写 boolean 静态字段，避免误改同名常量
                if (field.type == java.lang.Boolean.TYPE) field.setBoolean(null, value)
            }
        }
    }
}
