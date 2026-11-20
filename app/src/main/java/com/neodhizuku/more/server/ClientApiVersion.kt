package com.neodhizuku.more.server

import java.util.concurrent.ConcurrentHashMap

/**
 * 客户端 API 版本登记表。
 * 客户端在 Dhizuku.init() 的 extras 里带上自己编译所用的 API 版本号，
 * 服务端 Provider 收到后按 uid / 包名登记。界面据此渲染：
 * - 读不到版本号（原版 Dhizuku-API）→ Legacy
 * - 读得到 → Modern，并显示 API 版本（API 1 / API 2）
 * 只登记在内存里，是有意为之：这是"当前这条连接是什么版本"的运行时信息，
 * 客户端每次 init() 都会重新上报，服务端进程重启后客户端也必须重新 init，
 * 因此没有持久化到磁盘的必要，落盘反而会在应用卸载重装后留下脏数据。
 * 字段由调用方自行填写，属于**声明**而非**凭据**——服务端不会据此放宽
 * 任何身份判定，调用方身份一律以 Binder.getCallingUid() 为准。
 */
object ClientApiVersion {

    /** 未上报版本号（原版 Dhizuku-API）。 */
    const val LEGACY = 0

    /** 首版 NeoDhizuku API。 */
    const val V1 = 1

    /** uid -> 版本号 */
    private val byUid = ConcurrentHashMap<Int, Int>()

    /** 包名 -> 版本号（供白名单等按包名查询的界面使用） */
    private val byPackage = ConcurrentHashMap<String, Int>()

    /**
     * 登记一次上报。
     * @param uid 调用方 uid，由 Binder.getCallingUid() 取得，不取信自报
     * @param version 客户端上报的版本号；小于等于 0 视同未上报（Legacy）
     * @param packageName 若已能反查出包名则一并登记，可为空
     */
    fun report(uid: Int, version: Int, packageName: String? = null) {
        val normalized = if (version > 0) version else LEGACY
        byUid[uid] = normalized
        if (!packageName.isNullOrEmpty()) byPackage[packageName] = normalized
    }

    /**
     * of
     * (uid: Int)
     * 作用：查询某 uid 的客户端版本，未登记则按原版 API（Legacy）处理。
     * 备注：—
     */
    fun of(uid: Int): Int = byUid[uid] ?: LEGACY

    /**
     * ofPackage
     * (packageName: String)
     * 作用：查询某包名的客户端版本。用于只有包名、没有 uid 的界面。
     * 备注：—
     */
    fun ofPackage(packageName: String): Int = byPackage[packageName] ?: LEGACY

    /**
     * isModern
     * (version: Int)
     * 作用：是否为新版客户端。供需要按版本决定是否启用新逻辑的分支使用。
     * 备注：—
     */
    fun isModern(version: Int): Boolean = version > 0

    /**
     * typeLabel
     * (version: Int)
     * 作用：卡片右上角的类型标签。 Legacy / Modern 是 API 协商层面的技术标识，刻意不做国际化。
     * 备注：—
     */
    fun typeLabel(version: Int): String = if (isModern(version)) "Modern" else "Legacy"

    /**
     * apiLabel
     * (version: Int)
     * 作用：应用名下方的版本药丸文本；Legacy 返回空串，由调用方决定不渲染。
     * 备注：—
     */
    fun apiLabel(version: Int): String = if (isModern(version)) "API $version" else ""

    /**
     * label
     * (version: Int)
     * 作用：运行日志里读起来直观的写法。
     * 备注：—
     */
    fun label(version: Int): String =
        if (isModern(version)) apiLabel(version) else typeLabel(version)
}
