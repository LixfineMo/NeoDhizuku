package com.neodhizuku.more.server

import android.os.RemoteException

import com.rosan.dhizuku.aidl.IDhizukuRequestPermissionListener

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 授权请求的一次性令牌（**本类位于 app 模块**）。
 *
 * 为什么放在 app 模块而不是 server_api：
 * app 可能编译/运行在「本地 Dhizuku-API 源码」或「Maven 上的官方包」两种模式下，
 * 若引用 server_api 中新增的类，在 Maven 模式下会直接编译失败。
 * 放在 app 模块可以保证两种模式都能编译，加固逻辑在两种模式下都生效。
 *
 * 授权入口的 uid 不信任客户端传入，改由 MyDhizukuService.onTransact() 拦截
 * requestPermission 事务，用 Binder.getCallingUid() 取真实 uid 并签发 token；
 * Activity 校验 token 通过后才认可该 uid。无 token 或 token 无效（例如第三方
 * App 伪造的 Intent）走兼容路径，且不会覆盖已有授权。
 */
object RequestPermissionToken {
    /** token 有效期 */
    private const val TTL_MILLIS = 60_000L

    /** 与 DhizukuVariables.PARAM_SERVER_TOKEN 保持一致 */
    const val PARAM_SERVER_TOKEN = "server_token"

    /** AIDL 中 requestPermission 的事务码 */
    const val TRANSACT_REQUEST_PERMISSION = 17

    /** IDhizuku 的 Binder descriptor */
    const val IDHIZUKU_DESCRIPTOR = "com.rosan.dhizuku.aidl.IDhizuku"

    private class Record(
        val uid: Int,
        val listener: IDhizukuRequestPermissionListener?
    ) {
        val issuedAt = System.currentTimeMillis()
    }

    private val tokens = ConcurrentHashMap<String, Record>()

    fun issue(uid: Int, listener: IDhizukuRequestPermissionListener?): String {
        val token = UUID.randomUUID().toString()
        tokens[token] = Record(uid, listener)
        return token
    }

    /**
     * 校验并消费 token（单次有效）。
     * @return token 对应的 uid；无效或过期返回 -1。
     */
    fun consume(token: String?): Int {
        if (token.isNullOrEmpty()) return -1
        val record = tokens.remove(token) ?: return -1
        if (System.currentTimeMillis() - record.issuedAt > TTL_MILLIS) return -1
        return record.uid
    }

    fun revoke(token: String?) {
        if (token.isNullOrEmpty()) return
        tokens.remove(token)
    }

    fun dispatchResult(token: String?, grantResult: Int) {
        val record = tokens.remove(token ?: "")
        if (record == null) return
        try {
            record.listener?.onRequestPermission(grantResult)
        } catch (ignored: RemoteException) {
        }
    }
}
