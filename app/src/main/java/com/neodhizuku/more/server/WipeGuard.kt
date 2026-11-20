package com.neodhizuku.more.server

import android.os.SystemClock
import com.neodhizuku.more.NeoLog

/**
 * 擦除设备数据的放行授权。
 * `DevicePolicyManager.wipeData` 是不可逆操作，且设备管理员身份下同样可以执行，
 * 因此不能只依赖能力分级 —— 即使当前是设备所有者，第三方借用身份发起的擦除
 * 也必须经用户明确确认。
 * 流程：
 * 1. 服务端检测到擦除请求 → 默认拦截 + 发通知 + 记录日志（意图完整留痕）
 * 2. 用户点通知进入确认页，连续通过两级警告（共三次点击）
 * 3. 确认后写入一个**有时效**的放行标记，用户回到第三方重新触发才生效
 * 标记有有效期且一次性：既能防止误触后长期敞口，也避免被反复利用。
 */
object WipeGuard {

    /** 放行标记的有效期 */
    private const val GRANT_TTL_MILLIS = 5 * 60 * 1000L

    /** uid -> 放行到期时间（uptime 毫秒） */
    private val grants = HashMap<Int, Long>()

    /** 最近一次被拦截的擦除请求来源，供确认页展示 */
    @Volatile
    var lastRejectedUid: Int = -1
        private set

    @Volatile
    var lastRejectedLabel: String = ""
        private set

    @Volatile
    var lastRejectedAt: Long = 0L
        private set

    /**
     * noteRejected
     * (uid: Int, label: String)
     * 作用：记录一次被拦截的请求
     * 备注：—
     */
    fun noteRejected(uid: Int, label: String) {
        lastRejectedUid = uid
        lastRejectedLabel = label
        lastRejectedAt = System.currentTimeMillis()
    }

    /**
     * isGranted
     * (uid: Int)
     * 作用：该 uid 当前是否持有有效的放行标记
     * 备注：—
     */
    @Synchronized
    fun isGranted(uid: Int): Boolean {
        // 顺带回收全部过期标记：只清当前 uid 的话，其它 uid 的过期标记会一直
        // 留在表里（放行标记是一次性的，用过即应移除，实际残留的都是没用过的）
        purgeExpired()
        val until = grants[uid] ?: return false
        if (SystemClock.uptimeMillis() > until) return false
        return true
    }

    /**
     * purgeExpired
     * ()
     * 作用：回收过期标记。擦除放行属于高风险状态，不该比需要它的时候活得更久
     * 备注：—
     */
    @Synchronized
    fun purgeExpired() {
        val now = SystemClock.uptimeMillis()
        val before = grants.size
        grants.entries.removeIf { now > it.value }
        if (grants.size != before) {
            NeoLog.d(NeoLog.SERVER) { "wipe guard: 回收过期放行标记 ${before - grants.size} 条" }
        }
    }

    /**
     * grant
     * (uid: Int)
     * 作用：写入放行标记（用户在确认页通过全部警告后调用）
     * 备注：—
     */
    @Synchronized
    fun grant(uid: Int) {
        grants[uid] = SystemClock.uptimeMillis() + GRANT_TTL_MILLIS
        NeoLog.w(NeoLog.SERVER) { "wipe guard: 已授予放行 uid=$uid（5 分钟内有效，一次性）" }
    }

    /**
     * consume
     * (uid: Int)
     * 作用：用掉放行标记
     * 备注：—
     */
    @Synchronized
    fun consume(uid: Int) {
        grants.remove(uid)
    }

    /**
     * revokeAll
     * ()
     * 作用：撤销全部放行标记
     * 备注：—
     */
    @Synchronized
    fun revokeAll() {
        grants.clear()
    }
}
