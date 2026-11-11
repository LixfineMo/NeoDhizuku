package com.neodhizuku.more.server

import android.os.Binder
import android.os.Bundle

import com.neodhizuku.more.NeoLog
import com.neodhizuku.more.data.settings.repo.DenyMode
import com.neodhizuku.more.data.settings.repo.SettingsRepo
import com.rosan.dhizuku.aidl.IDhizukuClient
import com.rosan.dhizuku.server_api.DhizukuProvider
import com.rosan.dhizuku.server_api.DhizukuService
import com.rosan.dhizuku.shared.DhizukuVariables

import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class MyDhizukuProvider : DhizukuProvider(), KoinComponent {

    /**
     * 客户端通过 ContentProvider.call() 拿 IDhizuku 时的入口。
     * 这里是"识别不到服务"链路的第一站，打点便于区分：
     * 客户端根本没来（Provider 没被调用） vs 来了但后面某一步失败。
     */
    override fun onCreateService(client: IDhizukuClient): DhizukuService {
        val state = DhizukuState.state
        val stopped = runCatching { settingsRepo.isServiceStoppedByUser }.getOrDefault(false)
        NeoLog.i(NeoLog.SERVER) {
            "provider: 客户端请求 binder owner=${state.isOwner} " +
                "deviceOwner=${state.isDeviceOwner} profileOwner=${state.isProfileOwner} " +
                "serviceStopped=$stopped admin=${DhizukuState.admin.flattenToString()}"
        }
        // 客户端 API 的 init() 只认可设备所有者 / 资料管理员。
        // 仅设备管理员身份时能走到这里，但 init() 在此之前已经返回 false，
        // 因此第三方会报"初始化失败"，而不是本服务的故障。
        if (!state.isOwner) {
            NeoLog.i(NeoLog.SERVER) {
                "provider: 当前不是所有者身份，标准客户端的 init() 会先失败，无法走到这一步"
            }
        }
        return MyDhizukuService(context!!, DhizukuState.admin, client)
    }

    /**
     * 两种「静默拒绝」模式**刻意不在此处拦截**：照常返回 binder。
     * 早先的实现在此处直接返回 null / 空 Bundle（都不含 binder），
     * 结果客户端的 Dhizuku.init() 一律判为"服务不可用"，两种模式完全等价，
     * "返回成功"这一次级差异根本传不到第三方手里。
     * 现在改为放行 binder：第三方能正常初始化、能查到"已授权"，
     * 失败还是成功由 MyDhizukuService 在接口层面决定（见 DenyMode 的说明）。
     * ContentProvider 的 `call(authority, ...)`（API 30+）默认转发到本三参版本，
     * 因此只覆写这一个就够覆盖全部系统版本。
     */
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        // 登记客户端 API 版本：新版 Dhizuku-API 会在 init() 的 extras 里带上版本号，
        // 原版不会带，读不到即按 old 处理（ClientApiVersion.LEGACY）。
        // 身份一律用 Binder.getCallingUid() 判定，不取信 extras 里任何自报字段；
        // 这里读到的只是"对方用什么版本的 API 编译"，用于界面标注与后续功能协商。
        runCatching {
            // 只在取 binder 这一次登记。若不限定 method，后续任何一次不带该字段的
            // call 都会把已登记的版本覆盖回 old，标签会闪回。
            if (method != DhizukuVariables.PROVIDER_METHOD_CLIENT) return@runCatching
            val uid = Binder.getCallingUid()
            val version = extras?.getInt(DhizukuVariables.PARAM_CLIENT_API_VERSION, ClientApiVersion.LEGACY)
                ?: ClientApiVersion.LEGACY
            val packageName = runCatching {
                context?.packageManager?.getPackagesForUid(uid)?.firstOrNull()
            }.getOrNull()
            ClientApiVersion.report(uid, version, packageName)
            NeoLog.i(NeoLog.SERVER) {
                "provider: 客户端 API 版本 uid=$uid version=${ClientApiVersion.label(version)} pkg=$packageName"
            }
        }
        val stopped = runCatching { settingsRepo.isServiceStoppedByUser }.getOrDefault(false)
        if (stopped) {
            val mode = runCatching { DenyMode.from(settingsRepo.denyMode) }
                .getOrDefault(DenyMode.DEFAULT)
            if (mode == DenyMode.DENY_ALL_FAIL || mode == DenyMode.DENY_ALL_SUCCESS) {
                NeoLog.i(NeoLog.SERVER) {
                    "provider: 静默模式（$mode），照常返回 binder，操作由服务层拦截"
                }
            }
        }
        return super.call(method, arg, extras)
    }

    private val settingsRepo by inject<SettingsRepo>()
}
