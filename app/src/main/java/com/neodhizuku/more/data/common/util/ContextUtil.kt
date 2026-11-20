package com.neodhizuku.more.data.common.util

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast

import androidx.annotation.StringRes
import androidx.core.net.toUri

/**
 * openUrlInBrowser
 * (url: String)
 * 作用：打开 UrlInBrowser
 * 备注：—
 */
fun Context.openUrlInBrowser(url: String) {
    val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
}

/**
 * toast
 * (text: CharSequence, duration: Int = Toast.LENGTH_SHORT)
 * 作用：执行 toast
 * 备注：—
 */
fun Context.toast(text: CharSequence, duration: Int = Toast.LENGTH_SHORT) {
    Handler(Looper.getMainLooper()).post {
        Toast.makeText(this, text, duration).show()
    }
}

/**
 * toast
 * (@StringRes resId: Int, duration: Int = Toast.LENGTH_SHORT)
 * 作用：执行 toast
 * 备注：—
 */
fun Context.toast(@StringRes resId: Int, duration: Int = Toast.LENGTH_SHORT) {
    toast(getString(resId), duration)
}