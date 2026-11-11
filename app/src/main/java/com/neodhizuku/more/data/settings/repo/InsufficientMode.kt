package com.neodhizuku.more.data.settings.repo

/**
 * 能力不足时的处理模式。
 * 与 [DenyMode] 的区别：**触发场景互斥**。
 * - [DenyMode] 作用于「服务被用户停止」—— 此时服务已关闭，
 *   第三方拿不到 binder，"完全拒绝"才有意义；
 * - 本模式作用于「服务运行中，但当前身份等级不足以执行该操作」——
 *   第三方正常拿到 binder，只是部分方法调不动，
 *   因此不存在"查不到服务"这回事，只有"这个动作做不了"。
 * 典型场景：仅拥有设备管理员时，第三方请求 `setApplicationHidden`
 * （需要设备所有者）。此时可选返回失败、假装成功、或不回应。
 * 注意：本模式的定位是「给想测试但不愿真给权限的用户」用的，
 * 因此**即使返回失败，操作意图仍会完整记录在调用日志里** ——
 * 用户可以通过日志看到对方到底想做什么。
 */
enum class InsufficientMode {
    /**
     * 返回失败。
     * 默认项。客户端会收到 SecurityException，通常会显式报错或降级。
     * 明确告知失败可以避免"以为生效了实际没有"这类难以排查的问题。
     */
    RETURN_FAIL,

    /**
     * 返回成功但不执行。
     * 客户端以为操作生效了，实际什么都没发生。
     * 适用于对异常响应很差（直接崩溃）的客户端。
     */
    RETURN_SUCCESS,

    /** 不做任何处理：挂起，客户端无限等待。 */
    NO_ACTION;

    companion object {
        /** 默认值：返回失败 */
        val DEFAULT = RETURN_FAIL

        /** 从持久化值还原；无法识别时回退到 [DEFAULT] */
        fun from(value: String?): InsufficientMode =
            entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}
