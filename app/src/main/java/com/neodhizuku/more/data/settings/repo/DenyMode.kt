package com.neodhizuku.more.data.settings.repo

/**
 * 服务拒绝模式。
 *
 * 决定当第三方应用来调用本服务时，服务以什么方式回应。
 * 五种模式覆盖从"正常拒绝"到"完全隐身"再到"静默挂起"的区间。
 */
enum class DenyMode {
    /**
     * 拒绝危险服务，返回失败。
     *
     * 第三方仍可查询到服务（能确认"已授权"），但危险操作一律拒绝。
     * 这是默认模式，也是首页关闭服务后的状态。
     */
    DENY_DANGEROUS_FAIL,

    /**
     * 拒绝危险服务，返回成功。
     *
     * 查询与危险操作都返回成功，但危险操作并不真正执行 ——
     * 第三方以为操作生效了，实际什么都没发生。
     */
    DENY_DANGEROUS_SUCCESS,

    /**
     * 完全拒绝服务，返回失败。
     *
     * 第三方查询不到服务，等同于"未安装 / 未激活"。
     */
    DENY_ALL_FAIL,

    /**
     * 完全拒绝服务，返回成功。
     *
     * 第三方拿不到服务的实际能力，但得到的回应是成功而非失败。
     */
    DENY_ALL_SUCCESS,

    /**
     * 不做任何处理。
     *
     * 既不拒绝也不放行，调用被挂起 —— 第三方会一直等待，
     * 表现为获取授权时长时间转圈且得不到回应。
     */
    NO_ACTION;

    companion object {
        /** 默认值：拒绝危险服务并返回失败 */
        val DEFAULT = DENY_DANGEROUS_FAIL

        /** 从持久化值还原；无法识别时回退到 [DEFAULT] */
        fun from(value: String?): DenyMode =
            entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}
