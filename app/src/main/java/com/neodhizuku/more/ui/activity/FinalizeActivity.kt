package com.neodhizuku.more.ui.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.neodhizuku.more.NeoDhizuku
import com.neodhizuku.more.R
import com.neodhizuku.more.data.common.util.toast
import com.neodhizuku.more.server.DhizukuState
import org.koin.core.component.KoinComponent

class FinalizeActivity : ComponentActivity(), KoinComponent {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (NeoDhizuku.on(NeoDhizuku.FixProvisioningFinalize)) {
            // provisioning 完成后刷新状态并提示，否则打开 App 可能仍显示未激活
            DhizukuState.sync(applicationContext)
            runCatching { toast(R.string.provisioning_finalized) }
        }
        setResult(RESULT_OK)
        finish()
    }
}
