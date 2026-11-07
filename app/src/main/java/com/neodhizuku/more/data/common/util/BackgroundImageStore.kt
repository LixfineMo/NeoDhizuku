package com.neodhizuku.more.data.common.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log

import com.neodhizuku.more.NeoLog

import java.io.File
import java.io.FileOutputStream

/**
 * 自定义背景图的存放与导入。
 *
 * 图片复制到私有目录后再使用，而不是长期持有外部 Uri：
 * 外部 Uri 的读权限随时可能被回收（图库被卸载、权限被清），
 * 复制一份能保证设置完就一直可用。
 */
object BackgroundImageStore {

    private const val DIR_NAME = "background"
    private const val FILE_NAME = "background.img"

    /** 长边上限：超出就按比例缩小，避免大图常驻内存。 */
    private const val MAX_EDGE = 1440

    fun file(context: Context): File = File(File(context.filesDir, DIR_NAME), FILE_NAME)

    fun exists(context: Context): Boolean {
        val f = file(context)
        return f.exists() && f.length() > 0
    }

    /**
     * 从 [uri] 导入背景图。
     *
     * 分两步解码：先只读尺寸算出采样率，再按采样率真正解码，
     * 避免直接把原图整张读进内存。
     */
    fun import(context: Context, uri: Uri): Boolean {
        return runCatching {
            val resolver = context.contentResolver
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, boundsOptions) }
            val w = boundsOptions.outWidth
            val h = boundsOptions.outHeight
            if (w <= 0 || h <= 0) {
                Log.w("BackgroundImage", "无法读取图片尺寸: $uri")
                return false
            }

            val sample = (maxOf(w, h).toFloat() / MAX_EDGE.toFloat())
                .let { ratio -> if (ratio <= 1f) 1 else ratio.toInt() }
                .coerceAtLeast(1)

            val decoded = resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(
                    stream,
                    null,
                    BitmapFactory.Options().apply { inSampleSize = sample }
                )
            }
            if (decoded == null) {
                Log.w("BackgroundImage", "图片解码失败: $uri")
                return false
            }

            val scaled = scaleIfNeeded(decoded)
            val dir = File(context.filesDir, DIR_NAME)
            if (!dir.exists() && !dir.mkdirs()) {
                Log.w("BackgroundImage", "目录创建失败: ${dir.absolutePath}")
                return false
            }
            val target = File(dir, FILE_NAME)
            val tmp = File(dir, "$FILE_NAME.tmp")
            FileOutputStream(tmp).use { out ->
                scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.flush()
            }
            if (target.exists() && !target.delete()) {
                Log.w("BackgroundImage", "旧背景图删除失败")
            }
            if (!tmp.renameTo(target)) {
                Log.w("BackgroundImage", "背景图写入失败")
                return false
            }
            if (scaled !== decoded) scaled.recycle()
            decoded.recycle()
            NeoLog.d(NeoLog.THEME) { "背景图已导入 ${target.length()} 字节" }
            true
        }.getOrElse {
            Log.w("BackgroundImage", "导入背景图异常", it)
            false
        }
    }

    private fun scaleIfNeeded(src: Bitmap): Bitmap {
        val maxEdge = maxOf(src.width, src.height)
        if (maxEdge <= MAX_EDGE) return src
        val ratio = MAX_EDGE.toFloat() / maxEdge.toFloat()
        return Bitmap.createScaledBitmap(
            src,
            (src.width * ratio).toInt().coerceAtLeast(1),
            (src.height * ratio).toInt().coerceAtLeast(1),
            true
        )
    }

    fun clear(context: Context) {
        runCatching {
            val f = file(context)
            if (f.exists()) f.delete()
            File(context.filesDir, DIR_NAME).listFiles()?.forEach {
                if (it.name.startsWith(FILE_NAME)) it.delete()
            }
        }.onFailure {
            Log.w("BackgroundImage", "清除背景图异常", it)
        }
    }
}
