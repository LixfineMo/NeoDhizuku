package com.neodhizuku.more.ui.page.settings.debug

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

import com.neodhizuku.more.server.DhizukuState

/**
 * 调试用「强制显示已激活」覆盖层。
 * 与 [DhizukuState.state] **完全隔离**：displayState() 只被首页与设置页的
 * 文案 / 可见性分支读取，不进入任何执行链 ——
 * 服务启停、能力分级、Provider 判定、取消激活、委派、白名单一律仍读真值。
 * 因此：
 * - sync() 按系统真值重算时不会把显示打回原形；
 * - 在未激活设备上打开它也不会让第三方调用被抬到 DO 级权限；
 * - 只存内存、不落盘，进程重启自动复位，不会残留到正式行为里。
 * 需要改「全局真值」的高危场景仍用 RuntimeParamCatalog 里的
 * DhizukuState.isDeviceOwner / isProfileOwner / isDeviceAdmin 三项。
 */
object DebugUiOverrides {

    /** 总开关：打开后首页与设置页按 [forceActivationKind] 显示已激活。 */
    var forceActivatedDisplay by mutableStateOf(false)
        private set

    /** device_owner / profile_owner / device_admin，决定徽标文案与分支。 */
    var forceActivationKind by mutableStateOf("device_owner")
        private set

    // 名称避开委托属性自动生成的 JVM setter，防止签名冲突
    fun applyForceActivatedDisplay(value: Boolean) {
        forceActivatedDisplay = value
    }

    // 同理，保留 trim / lowercase / 白名单归一化，不能直接赋值
    fun applyForceActivationKind(value: String) {
        val v = value.trim().lowercase()
        forceActivationKind = if (v in setOf("device_owner", "profile_owner", "device_admin"))
            v else "device_owner"
    }

    fun reset() {
        forceActivatedDisplay = false
        forceActivationKind = "device_owner"
    }

    /** 显示层专用：伪装状态或真实状态。读取即订阅，改开关会立即重组。 */
    fun displayState(): DhizukuState.State {
        if (!forceActivatedDisplay) return DhizukuState.state
        return when (forceActivationKind) {
            "profile_owner" -> DhizukuState.State(isProfileOwner = true)
            "device_admin" -> DhizukuState.State(isDeviceAdmin = true)
            else -> DhizukuState.State(isDeviceOwner = true)
        }
    }
}
