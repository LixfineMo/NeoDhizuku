package com.neodhizuku.more.ui.page.settings.activate

import android.content.Context
import android.os.Build
import androidx.annotation.StringRes
import com.neodhizuku.more.R

/**
 * A-10 修复：把激活过程中的异常映射成"中文原因 + 处理建议"。
 *
 * 原实现直接把 `exception.toString()` 显示给用户，用户看到的是
 * `java.lang.SecurityException: ...` 之类的英文堆栈首行，既不知道原因也不知道该怎么办。
 */
enum class ActivationError(
    @StringRes val titleRes: Int,
    @StringRes val adviceRes: Int
) {
    ACCOUNT_EXISTS(R.string.activate_err_account_exists, R.string.activate_err_account_exists_advice),
    OWNER_ALREADY_SET(R.string.activate_err_owner_exists, R.string.activate_err_owner_exists_advice),
    SHIZUKU_UNAVAILABLE(R.string.activate_err_shizuku_down, R.string.activate_err_shizuku_down_advice),
    HIDDEN_API_BLOCKED(R.string.activate_err_hidden_api, R.string.activate_err_hidden_api_advice),
    NOT_DEVICE_ADMIN(R.string.activate_err_not_admin, R.string.activate_err_not_admin_advice),
    USER_CANCELLED(R.string.activate_err_cancelled, R.string.activate_err_cancelled_advice),
    UNKNOWN(R.string.activate_err_unknown, R.string.activate_err_unknown_advice);

    companion object {
        fun from(throwable: Throwable?): ActivationError {
            if (throwable == null) return UNKNOWN
            val message = (throwable.message ?: "") + "\n" + throwable::class.java.name
            val lower = message.lowercase()
            return when {
                throwable is kotlinx.coroutines.CancellationException -> USER_CANCELLED
                "account" in lower || "账户" in message -> ACCOUNT_EXISTS
                "already" in lower && ("owner" in lower || "provision" in lower) -> OWNER_ALREADY_SET
                "device owner" in lower && "already" in lower -> OWNER_ALREADY_SET
                "shizuku" in lower || "sui" in lower -> SHIZUKU_UNAVAILABLE
                throwable is NoSuchMethodException
                        || throwable is NoSuchFieldException
                        || throwable is ClassNotFoundException
                        || "nosuch" in lower
                        || "hidden" in lower -> HIDDEN_API_BLOCKED
                throwable is SecurityException -> NOT_DEVICE_ADMIN
                else -> UNKNOWN
            }
        }

        /** 供用户复制的技术诊断信息（保留原始异常，便于提 issue） */
        fun diagnostic(context: Context, throwable: Throwable?): String = buildString {
            append("Dhizuku activation failure\n")
            append("device: ").append(Build.MANUFACTURER).append(' ')
                .append(Build.MODEL).append('\n')
            append("android: ").append(Build.VERSION.RELEASE).append(" (API ")
                .append(Build.VERSION.SDK_INT).append(")\n")
            append("error: ").append(from(throwable).name).append('\n')
            append("detail: ").append(throwable?.stackTraceToString() ?: "none")
        }
    }
}
