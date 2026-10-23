package com.neodhizuku.more.server

import android.os.Bundle

import com.neodhizuku.more.NeoLog
import com.neodhizuku.more.data.settings.repo.DenyMode
import com.neodhizuku.more.data.settings.repo.SettingsRepo
import com.rosan.dhizuku.aidl.IDhizukuClient
import com.rosan.dhizuku.server_api.DhizukuProvider
import com.rosan.dhizuku.server_api.DhizukuService

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
     * 「完全拒绝」模式在此处生效：直接不返回 binder，客户端查不到服务。
     *
     * ContentProvider 的 `call(authority, ...)`（API 30+）默认转发到本三参版本，
     * 因此只覆写这一个就够覆盖全部系统版本。
     *
     * 返回 null 表示失败，返回空 Bundle 表示调用成功但没有内容 ——
     * 两者都不含 binder，客户端都拿不到服务能力，区别在于它把结果判成失败还是成功。
     */
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val stopped = runCatching { settingsRepo.isServiceStoppedByUser }.getOrDefault(false)
        if (stopped) {
            val mode = runCatching { DenyMode.from(settingsRepo.denyMode) }
                .getOrDefault(DenyMode.DEFAULT)
            if (mode == DenyMode.DENY_ALL_FAIL || mode == DenyMode.DENY_ALL_SUCCESS) {
                NeoLog.i(NeoLog.SERVER) { "provider: 完全拒绝，不返回 binder（$mode）" }
                return if (mode == DenyMode.DENY_ALL_FAIL) null else Bundle()
            }
        }
        return super.call(method, arg, extras)
    }

    private val settingsRepo by inject<SettingsRepo>()
}
