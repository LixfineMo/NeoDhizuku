package com.neodhizuku.more.ui.activity

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.text.HtmlCompat
import com.neodhizuku.more.NeoDhizuku
import com.neodhizuku.more.R
import com.neodhizuku.more.data.common.util.asFlow
import com.neodhizuku.more.ui.page.settings.SettingsPage
import com.neodhizuku.more.ui.theme.DhizukuTheme
import org.koin.core.component.KoinComponent
import androidx.core.net.toUri

class SettingsActivity : ComponentActivity(), KoinComponent {
    private val appPrefs by lazy {
        getSharedPreferences("app", MODE_PRIVATE)
    }

    private val agreementFlow by lazy {
        appPrefs.asFlow("agreement", false)
    }

    private lateinit var stringLauncher: ActivityResultLauncher<String>

    /** 电池优化弹窗只在首次（用户未曾处理过）时引导一次，不再每次冷启动都弹 */
    private val batteryPromptShownKey = "battery_optimization_prompted"

    // 语言必须在界面建立之前套上，否则切换后要等下次冷启动才生效。
    // 放在这里而不是 onCreate 里创建 Context，是因为 attachBaseContext
    // 是唯一早于资源加载的时机。
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(
            com.neodhizuku.more.data.common.util.LocaleHelper.wrap(newBase)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()

        // ActivityResultLauncher 必须在 Activity 进入 CREATED 之前注册，
        // 放在 setContent 之后的话，部分 androidx 版本会直接抛 IllegalStateException。
        if (NeoDhizuku.on(NeoDhizuku.FixLauncherRegisterOrder)) {
            stringLauncher = registerForActivityResult(RequestPermission()) { _: Boolean -> }
        }

        super.onCreate(savedInstanceState)
        setContent {
            DhizukuTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    AgreementDialog()
                    SettingsPage(windowInsets = WindowInsets.safeDrawing)
                }
            }
        }

        if (!this::stringLauncher.isInitialized) {
            stringLauncher = registerForActivityResult(
                RequestPermission()
            ) { _: Boolean -> }
        }
        requestPostNotificationPermission()
        requestIgnoreBatteryOptimization()
    }

    fun requestPostNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                stringLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    @SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimization() {
        if (NeoDhizuku.on(NeoDhizuku.FixBatteryOptimizationPrompt)) {
            // 只引导一次；用户从系统设置里改回来后我们也不再反复打扰。
            if (appPrefs.getBoolean(batteryPromptShownKey, false)) return
            appPrefs.edit { putBoolean(batteryPromptShownKey, true) }
        }
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        ("package:$packageName").toUri()
                    )
                )
            } catch (_: ActivityNotFoundException) {
                // Some TV and embedded system images omit this optional settings activity.
                return
            }
        }
    }

    @Composable
    private fun AgreementDialog() {
        val agreement by agreementFlow.collectAsState(false)
        if (agreement) return

        AlertDialog(onDismissRequest = { }, title = {
            Text(text = stringResource(id = R.string.agreement_title))
        }, text = {
            val textColor = AlertDialogDefaults.textContentColor.toArgb()
            AndroidView(factory = {
                TextView(it).apply {
                    setTextColor(textColor)
                    movementMethod = LinkMovementMethod.getInstance()
                    text = HtmlCompat.fromHtml(
                        context.getString(R.string.agreement_text),
                        HtmlCompat.FROM_HTML_MODE_COMPACT
                    )
                }
            })
        }, dismissButton = {
            TextButton(onClick = {
                this@SettingsActivity.finish()
            }) {
                Text(text = stringResource(id = R.string.cancel))
            }
        }, confirmButton = {
            TextButton(onClick = {
                appPrefs.edit(true) {
                    putBoolean("agreement", true)
                }
            }) {
                Text(text = stringResource(id = R.string.agree_text))
            }
        })
    }
}