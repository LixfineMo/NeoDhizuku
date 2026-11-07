package com.neodhizuku.more.data.settings.repo

import kotlinx.coroutines.flow.Flow

/**
 * 一次单项接口判定的结果。
 *
 * @param allowed 是否放行
 * @param fromApp true 表示取自该应用的单独配置，false 表示取自全局配置
 */
data class ApiDecision(val allowed: Boolean, val fromApp: Boolean)

/**
 * 单项接口策略存储。
 *
 * 与拒绝服务（[DenyMode]）是两个维度：拒绝服务是整体开关，
 * 这里是「同一个应用能用到哪一步」。两者独立配置，但拒绝服务优先 ——
 * 首页停服时按拒绝模式统一处理，单项配置不参与（见调用处的判断）。
 *
 * 判定规则：应用有单独配置就用应用的，否则跟随全局；
 * 全局也没有配置过就取该接口的默认值（高危默认关闭）。
 */
interface ApiPolicyRepo {
    /** 全局配置：接口 id -> 是否允许 */
    fun flowGlobal(): Flow<Map<String, Boolean>>

    /** 全部应用的单独配置：包名 -> (接口 id -> 是否允许) */
    fun flowApps(): Flow<Map<String, Map<String, Boolean>>>

    /** 全局配置同步快照 */
    fun globalSnapshot(): Map<String, Boolean>

    /** 某个应用的单独配置同步快照；无配置时返回空表 */
    fun appSnapshot(packageName: String): Map<String, Boolean>

    /**
     * 判定某个应用能否调用某个接口。
     *
     * 服务端在 binder 线程上调用，不能挂起等待，因此是同步方法。
     * [defaultAllowed] 由调用方从接口定义传入，避免这里反向依赖 server 层。
     */
    fun decide(
        packageName: String?,
        apiId: String,
        defaultAllowed: Boolean
    ): ApiDecision

    suspend fun setGlobal(apiId: String, allowed: Boolean)

    suspend fun setApp(packageName: String, apiId: String, allowed: Boolean)

    /** 撤掉某个应用的某一项配置，使其回到跟随全局 */
    suspend fun clearApp(packageName: String, apiId: String)

    /** 撤掉某个应用的全部单独配置 */
    suspend fun clearAppAll(packageName: String)

    /** 清空全局配置，全部回到默认值 */
    suspend fun resetGlobal()
}
