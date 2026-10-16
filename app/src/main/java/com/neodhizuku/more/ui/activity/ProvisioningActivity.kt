package com.neodhizuku.more.ui.activity

import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.os.Build
import android.os.Bundle

import androidx.activity.ComponentActivity

import com.neodhizuku.more.NeoDhizuku

import org.koin.core.component.KoinComponent

class ProvisioningActivity : ComponentActivity(), KoinComponent {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = Intent()
        val mode = if (NeoDhizuku.on(NeoDhizuku.FixProvisioningFinalize)) {
            // 按系统允许的模式优先选择受管配置文件
            val allowed: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                intent.getIntExtra(
                    // 部分 compileSdk 上该常量不可用，直接用其字面值
                    "android.app.extra.PROVISIONING_ALLOWED_MODES",
                    DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE
                )
            } else {
                DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE
            }
            val profileFlag: Int = DevicePolicyManager.PROVISIONING_MODE_MANAGED_PROFILE
            val wantProfile = (allowed and profileFlag) != 0
            if (wantProfile) DevicePolicyManager.PROVISIONING_MODE_MANAGED_PROFILE
            else DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE
        } else {
            DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE
        }
        intent.putExtra(DevicePolicyManager.EXTRA_PROVISIONING_MODE, mode)
        setResult(RESULT_OK, intent)
        finish()
    }
}
