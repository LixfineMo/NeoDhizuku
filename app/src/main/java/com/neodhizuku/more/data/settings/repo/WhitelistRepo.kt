package com.neodhizuku.more.data.settings.repo

import kotlinx.coroutines.flow.Flow

/**
 * 白名单里的一条记录。
 *
 * [signature] 是加入白名单那一刻采集的签名指纹，属于「固定且不可篡改」的信息：
 * 开启严格校验后它会一并参与比对，防止同一个包名被重新签名后冒用白名单。
 */
data class WhitelistEntry(
    val packageName: String,
    val signature: String? = null,
    val addedAt: Long = System.currentTimeMillis()
)

/**
 * 白名单存储。
 *
 * 与授权记录（[AppRepo]，存放在 Room）分开，两者的性质不同：
 * - 授权记录是**调用发生后**自动产生的，可被用户撤销、可被清理逻辑回收；
 * - 白名单是**用户手工维护**的名单，写进去就该一直在，不该被授权表的变动波及。
 *
 * 因此白名单统一存放在应用私有目录的独立文件里。
 */
interface WhitelistRepo {
    /** 全部记录，按包名排序 */
    fun flowAll(): Flow<List<WhitelistEntry>>

    /**
     * 同步快照。
     *
     * 服务端在 binder 线程上判定，不能挂起等待，因此单独提供一个同步读口。
     */
    fun snapshot(): List<WhitelistEntry>

    /** 包名是否已在白名单中 */
    fun contains(packageName: String): Boolean

    /**
     * 判定某个调用方是否算白名单内。
     *
     * @param packageName 调用方包名
     * @param signatures 调用方当前的全部签名指纹；为空时无法做严格比对，
     *                   这时只按包名判定（严格模式下降级为拒绝更安全，
     *                   但取不到签名多数是系统限制而非攻击，故仍放行并记日志）
     * @param strict 是否启用严格校验：true 时要求包名与签名都匹配
     */
    fun isWhitelisted(
        packageName: String,
        signatures: Set<String>,
        strict: Boolean
    ): Boolean

    suspend fun add(packageName: String, signature: String?)

    suspend fun remove(packageName: String)
}
