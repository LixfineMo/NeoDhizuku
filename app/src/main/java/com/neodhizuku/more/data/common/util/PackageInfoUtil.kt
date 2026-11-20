/**
 * Removing the warning is not possible for compatibility reasons.
 */
@file:Suppress("DEPRECATION")

package com.neodhizuku.more.data.common.util

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build

/**
 * getPackageNameForUid
 * (uid: Int)
 * 作用：读取 PackageNameForUid
 * 备注：—
 */
fun PackageManager.getPackageNameForUid(uid: Int): String? =
    getPackagesForUid(uid)?.first()

/**
 * getPackageInfoForUid
 * (uid: Int, flags: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES)
 * 作用：读取 PackageInfoForUid
 * 备注：—
 */
fun PackageManager.getPackageInfoForUid(
    uid: Int,
    flags: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES
    else PackageManager.GET_SIGNATURES
): PackageInfo? = kotlin.runCatching {
    getPackageInfo(getPackageNameForUid(uid) ?: return null, flags)
}.getOrNull()

@OptIn(ExperimentalStdlibApi::class)
val PackageInfo.signature: String?
    get() = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) signingInfo?.apkContentsSigners
    else signatures)?.firstOrNull()?.toByteArray()?.digest("sha256")
        ?.toHexString(HexFormat.UpperCase)

/**
 * 取某个 UID 下的**全部**签名指纹。
 * 只取首个签名者是不够的：
 * - v3 签名轮换后首签名者可能变化 → 已授权 App 静默失效；
 * - 多签名（hasMultipleSigners）时只校验一个 → 其余签名被替换也无法察觉。
 * 注意：Android 平台强制 sharedUserId 的应用使用相同签名，
 * 因此这里不需要（也无法）防"同 UID 不同签名"的冒用场景。
 */
@OptIn(ExperimentalStdlibApi::class)
fun getSignaturesForUid(packageManager: PackageManager, uid: Int): Set<String> {
    val packages = packageManager.getPackagesForUid(uid) ?: return emptySet()
    val result = LinkedHashSet<String>()
    packages.forEach { pkg ->
        val info = kotlin.runCatching {
            packageManager.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
        }.getOrNull() ?: return@forEach
        val signers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = info.signingInfo ?: return@forEach
            if (signingInfo.hasMultipleSigners()) signingInfo.apkContentsSigners
            else signingInfo.signingCertificateHistory
        } else {
            @Suppress("DEPRECATION")
            info.signatures
        }
        signers?.forEach { cert ->
            val digest = kotlin.runCatching {
                cert.toByteArray().digest("sha256").toHexString(HexFormat.UpperCase)
            }.getOrNull() ?: return@forEach
            result.add(digest)
        }
    }
    return result
}
