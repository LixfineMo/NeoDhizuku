package com.neodhizuku.more.ui.theme

import android.app.admin.DeviceAdminInfo
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import com.neodhizuku.more.NeoDhizuku

import androidx.collection.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

object AppIconCache : KoinComponent {
    private val context by inject<Context>()

    private val defaultImageBitMap by lazy {
        ContextCompat.getDrawable(context, android.R.drawable.sym_def_app_icon)!!
            .toBitmap()
            .asImageBitmap()
    }

    private val lruCache by lazy {
        val maxMemory = Runtime.getRuntime().maxMemory() / 1024
        if (NeoDhizuku.on(NeoDhizuku.FixAppIconCache)) {
            // maxSize 单位是 KB，sizeOf 也必须以 KB 计，两者单位要一致，
            // 否则缓存只能放下 1 个图标，等于完全没有缓存。
            // 这里统一成 KB，并留出 1/8 堆给图标。
            val availableCacheSize = (maxMemory / 8).toInt()
            object : LruCache<String, Bitmap>(availableCacheSize) {
                override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024

                override fun entryRemoved(
                    evicted: Boolean,
                    key: String,
                    oldValue: Bitmap,
                    newValue: Bitmap?
                ) {
                    if (evicted) oldValue.recycle()
                }
            }
        } else {
            val availableCacheSize = (maxMemory / 4).toInt()
            object : LruCache<String, Bitmap>(availableCacheSize) {
                override fun sizeOf(key: String, value: Bitmap): Int {
                    return value.byteCount / 4
                }
            }
        }
    }

    @Composable
    fun rememberImageBitmapState(applicationInfo: ApplicationInfo): MutableState<ImageBitmap> {
        val key = "${applicationInfo.packageName}_${applicationInfo.uid}"
        val state = remember(key) { mutableStateOf(defaultImageBitMap) }

        LaunchedEffect(key) {
            val bitmap = withContext(Dispatchers.IO) {
                lruCache[key] ?: applicationInfo.loadIcon(context.packageManager)?.toBitmap()?.also {
                    lruCache.put(key, it)
                }
            }
            bitmap?.let {
                state.value = it.asImageBitmap()
            }
        }

        return state
    }

    @Composable
    fun rememberImageBitmapState(admin: DeviceAdminInfo): MutableState<ImageBitmap> {
        val key = "${admin.component.flattenToShortString()}_${admin.activityInfo.applicationInfo.uid}"
        val state = remember(key) { mutableStateOf(defaultImageBitMap) }

        LaunchedEffect(key) {
            val bitmap = withContext(Dispatchers.IO) {
                lruCache[key] ?: admin.loadIcon(context.packageManager)?.toBitmap()?.also {
                    lruCache.put(key, it)
                }
            }
            bitmap?.let {
                state.value = it.asImageBitmap()
            }
        }

        return state
    }
}