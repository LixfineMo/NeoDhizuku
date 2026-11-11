package com.neodhizuku.more.server

import android.content.Context
import com.neodhizuku.more.BuildConfig
import com.neodhizuku.more.NeoDhizuku
import com.neodhizuku.more.data.common.util.digest
import java.security.MessageDigest

/**
 * 启动时的自身完整性校验。
 * Dhizuku 以 DeviceOwner / ProfileOwner 身份运行，被二次打包重签名后同样能拿到设备所有者权限。
 * 这里在启动时比对自身签名 SHA-256 是否落在构建期注入的白名单内，
 * 不在白名单时通过 [IntegrityListener] 通知宿主（用于提示用户或记录日志），
 * 默认不阻断启动，避免误伤自行编译的 debug 版本。
 */
object ManagerIntegrity {

    private const val DEFAULT_ALLOWLIST = BuildConfig.SIGNATURE_SHA256_ALLOWLIST

    /** 构建期通过 SIGNATURE_SHA256_ALLOWLIST 注入，多个指纹用 ',' 分隔 */
    private fun allowlist(): List<String> =
        DEFAULT_ALLOWLIST.split(',', ';', ' ', '\n', '\r', '\t')
            .map { it.trim().uppercase() }
            .filter { it.isNotEmpty() }

    fun interface IntegrityListener {
        fun onUntrusted(reason: String)
    }

    @Volatile
    var listener: IntegrityListener? = null

    @Volatile
    var lastResult: Boolean = true
        private set

    fun verifyOnStartup(context: Context) {
        if (!NeoDhizuku.on(NeoDhizuku.HardenIntegrityCheck)) {
            lastResult = true
            return
        }
        val expected = allowlist()
        if (expected.isEmpty()) {
            // 未配置白名单（例如自行编译）时跳过校验
            lastResult = true
            return
        }
        val actual = runCatching { selfSignatureSha256(context) }.getOrNull()
        if (actual == null) {
            lastResult = false
            listener?.onUntrusted("无法读取自身签名")
            return
        }
        lastResult = expected.contains(actual)
        if (!lastResult) {
            listener?.onUntrusted("签名不在白名单内：$actual")
        }
    }

    private fun selfSignatureSha256(context: Context): String? {
        val info = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            context.packageManager.getPackageInfo(
                context.packageName,
                android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(
                context.packageName,
                android.content.pm.PackageManager.GET_SIGNATURES
            )
        }
        val signers = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            info.signatures
        }
        return signers?.firstOrNull()?.toByteArray()?.digest("sha256")
            ?.joinToString("") { "%02X".format(it) }
    }
}
