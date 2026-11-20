package com.neodhizuku.more.data.common.util

import android.content.SharedPreferences
import com.neodhizuku.more.NeoDhizuku
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

/**
 * asFlow
 * (key: String, defaultValue: T, crossinline getter: () -> T = { if (NeoDhizuku.on(NeoDhizuku.FixPrefsFlow)) {
 * 作用：执行 asFlow
 * 备注：内联函数
 */
inline fun <reified T> SharedPreferences.asFlow(
    key: String,
    defaultValue: T,
    crossinline getter: () -> T = {
        if (NeoDhizuku.on(NeoDhizuku.FixPrefsFlow)) {
            // 按类型直接读取单个键，避免每次全量反序列化
            readTyped(this, key, defaultValue)
        } else {
            val value = all.getOrElse(key) { defaultValue }
            if (value is T) value
            else defaultValue
        }
    }
) = callbackFlow {
    // 初始值发射
    trySend(getter())

    // 监听数据变化
    val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
        if (changedKey == key) {
            trySend(getter())
        }
    }
    registerOnSharedPreferenceChangeListener(listener)

    // 取消监听
    awaitClose { unregisterOnSharedPreferenceChangeListener(listener) }
}

/**
 * readTyped
 * (prefs: SharedPreferences, key: String, defaultValue: T)
 * 作用：读取 Typed
 * 备注：内联函数
 */
inline fun <reified T> readTyped(prefs: SharedPreferences, key: String, defaultValue: T): T {
    @Suppress("UNCHECKED_CAST")
    return when (T::class) {
        Boolean::class -> prefs.getBoolean(key, defaultValue as Boolean) as T
        Int::class -> prefs.getInt(key, defaultValue as Int) as T
        Long::class -> prefs.getLong(key, defaultValue as Long) as T
        Float::class -> prefs.getFloat(key, defaultValue as Float) as T
        String::class -> (prefs.getString(key, defaultValue as? String) ?: defaultValue) as T
        else -> {
            val value = prefs.all.getOrElse(key) { defaultValue }
            if (value is T) value else defaultValue
        }
    }
}

/**
 * stringFlow
 * (key: String, defaultValue: String?)
 * 作用：执行 stringFlow
 * 备注：—
 */
fun SharedPreferences.stringFlow(key: String, defaultValue: String?) = asFlow(key, defaultValue)
