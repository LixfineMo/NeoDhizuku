package com.neodhizuku.more.ui.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * 兼容旧版通知的入口：第三方转移现在由服务页的开关控制，
 * 这里只把用户带到设置页，不再写入任何一次性放行标记。
 */
class OwnerTransferConfirmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching {
            startActivity(
                Intent(this, SettingsActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        finish()
    }
}
