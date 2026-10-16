package com.neodhizuku.more.server

import com.neodhizuku.more.NeoLog
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
        return MyDhizukuService(context!!, DhizukuState.admin, client)
    }

    private val settingsRepo by inject<SettingsRepo>()
}
