package com.neodhizuku.more.server

import java.util.concurrent.atomic.AtomicBoolean

/**
 * 非 SDK 接口豁免的一次性入口。
 *
 * 项目里有四处各自调用 `HiddenApiBypass.addHiddenApiExemptions("")`
 * （App、reflect_module、DpmApiCatalog、OwnerTransferGuard）。豁免是**进程级
 * 全局生效**的，调一次就够；重复调用会在 logcat 打印：
 *
 * ```
 * VMRuntime.setHiddenApiExemptions() was called more than once.
 * In a future Android release that will either be no-op or throw an exception.
 * ```
 *
 * 文档明确该行为将来可能变成 no-op 或直接抛异常，届时分散的调用点会一起失效。
 * 这里收敛成单点：真实 API 只在进程内第一次调用时执行，之后直接返回。
 */
object HiddenApiExempt {

    private val done = AtomicBoolean(false)

    /**
     * 确保豁免已生效。可重复调用，但底层 API 只会真正执行一次。
     *
     * @return 本次是否真的执行了豁免（供日志判断，无实际用途）
     */
    fun once(): Boolean {
        if (!done.compareAndSet(false, true)) return false
        return runCatching {
            org.lsposed.hiddenapibypass.HiddenApiBypass.addHiddenApiExemptions("")
            true
        }.getOrDefault(false)
    }
}
