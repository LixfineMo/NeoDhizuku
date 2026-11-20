package com.neodhizuku.more.data.settings.repo

/**
 * 服务拒绝模式。
 * 决定当第三方应用来调用本服务时，服务以什么方式回应。
 * 五种模式覆盖从"正常拒绝"到"完全隐身"再到"静默挂起"的区间。
 */
enum class DenyMode {
    /**
     * 拒绝危险服务，返回失败。
     * 第三方仍可查询到服务（能确认"已授权"），但危险操作一律拒绝。
     * 这是默认模式，也是首页关闭服务后的状态。
     */
    DENY_DANGEROUS_FAIL,

    /**
     * 拒绝危险服务，返回成功。
     * 查询与危险操作都返回成功，但危险操作并不真正执行 ——
     * 第三方以为操作生效了，实际什么都没发生。
     */
    DENY_DANGEROUS_SUCCESS,

    /**
     * 静默拒绝，返回失败。
     * 第三方**能**拿到 binder（Dhizuku.init() 成功），但随后所有调用一律失败 ——
     * 包括 `isPermissionGranted()` 之类的只读查询，危险 / 只读一律拒绝。
     * 全程不弹任何界面（连授权弹窗也不拉起），第三方只能看到调用失败，
     * 无从判断是"被拒"还是"服务本身出错"。
     * 与 [DENY_ALL_SUCCESS] 的区别正是返回失败还是成功 —— 两者都能拿到 binder，
     * 因此这一次级差异能真正传给第三方。
     */
    DENY_ALL_FAIL,

    /**
     * 静默拒绝，返回成功。
     * 第三方能拿到 binder，`isPermissionGranted()` 也返回 true，
     * 看起来"服务可用且已授权"；但所有操作都不真正执行，只回一个成功结果 ——
     * 同样不区分"危险 / 只读"，只读查询也返回空结果而非真实值。
     * 不拉起授权弹窗：`requestPermission` 直接回调"已授权"。
     */
    DENY_ALL_SUCCESS,

    /**
     * 不做任何处理。
     * 既不拒绝也不放行，调用被挂起 —— 第三方会一直等待，
     * 表现为获取授权时长时间转圈且得不到回应。
     */
    NO_ACTION;

    companion object {
        /** 默认值：拒绝危险服务并返回失败 */
        val DEFAULT = DENY_DANGEROUS_FAIL

        /**
         * from
         * (value: String?)
         * 作用：从持久化值还原；无法识别时回退到 [DEFAULT]
         * 备注：—
         */
        fun from(value: String?): DenyMode =
            entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}
