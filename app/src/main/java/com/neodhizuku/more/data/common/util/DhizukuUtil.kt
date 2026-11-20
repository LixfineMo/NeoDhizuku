package com.neodhizuku.more.data.common.util

import android.content.pm.PackageManager

import com.rosan.dhizuku.api.Dhizuku
import com.rosan.dhizuku.api.DhizukuRequestPermissionListener
import com.neodhizuku.more.data.common.model.exception.DhizukuNotWorkException

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first

/**
 * blockingRequestDhizukuPermission
 * ()
 * 作用：执行 blockingRequestDhizukuPermission
 * 备注：挂起函数；仅内部使用
 */
private suspend fun blockingRequestDhizukuPermission() = callbackFlow {
    Dhizuku.requestPermission(object : DhizukuRequestPermissionListener() {
        /**
         * onRequestPermission
         * (grantResult: Int)
         * 作用：处理 RequestPermission 回调
         * 备注：重写父类 / 接口方法
         */
        override fun onRequestPermission(grantResult: Int) {
            if (grantResult == PackageManager.PERMISSION_GRANTED) trySend(Unit)
            else close(DhizukuNotWorkException("dhizuku permission denied"))
        }
    })
    awaitClose()
}.catch {
    throw if (it !is DhizukuNotWorkException) DhizukuNotWorkException(cause = it)
    else it
}.first()

/**
 * requireDhizukuPermissionGranted
 * (action: suspend () -> T)
 * 作用：执行 requireDhizukuPermissionGranted
 * 备注：挂起函数
 */
suspend fun <T> requireDhizukuPermissionGranted(action: suspend () -> T): T {
    if (!Dhizuku.init()) throw DhizukuNotWorkException("dhizuku isn't activated")
    if (!Dhizuku.isPermissionGranted())
        blockingRequestDhizukuPermission()
    return action()
}