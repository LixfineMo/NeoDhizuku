package com.neodhizuku.more.data.common.util

import android.content.pm.PackageManager

import com.neodhizuku.more.BuildConfig
import com.neodhizuku.more.data.common.model.exception.ShizukuNotWorkException

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first

import rikka.shizuku.Shizuku
import rikka.sui.Sui

private suspend fun blockingRequestShizukuPermission() = callbackFlow {
    val requestCode = (Int.MIN_VALUE..Int.MAX_VALUE).random()
    val listener =
        Shizuku.OnRequestPermissionResultListener { _requestCode, grantResult ->
            if (_requestCode != requestCode) return@OnRequestPermissionResultListener
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED)
                trySend(Unit)
            else close(ShizukuNotWorkException("sui/shizuku permission denied"))
        }
    Shizuku.addRequestPermissionResultListener(listener)
    Shizuku.requestPermission(requestCode)
    awaitClose { Shizuku.removeRequestPermissionResultListener(listener) }
}.catch {
    throw it as? ShizukuNotWorkException ?: ShizukuNotWorkException(cause = it)
}.first()

suspend fun <T> requireShizukuPermissionGranted(action: suspend () -> T): T {
    // NeoDhizuku：包名改为 com.neodhizuku.more 后，这里应传自身包名，
    // 与 App.onCreate() 中的 Sui.init(packageName) 保持一致。
    Sui.init(BuildConfig.APPLICATION_ID)
    val binder = Shizuku.getBinder()
    if (binder == null || !binder.pingBinder())
        throw ShizukuNotWorkException("sui/shizuku isn't activated")
    if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED)
        blockingRequestShizukuPermission()
    return action()
}

fun checkShizukuWorked(): Boolean {
    // NeoDhizuku：同上，使用自身包名
    val sui: Boolean = Sui.init(BuildConfig.APPLICATION_ID)
    val binder = Shizuku.getBinder()
    return (sui || binder != null)
}