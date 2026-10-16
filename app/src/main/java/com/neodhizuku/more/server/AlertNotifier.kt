package com.neodhizuku.more.server

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

import com.neodhizuku.more.R

/**
 * 安全警示通知。
 *
 * 与守护服务那条常驻通知分开：本渠道是 IMPORTANCE_HIGH，
 * 用于设备所有者转移这类高风险事件，必须能立刻引起注意。
 */
object AlertNotifier {

    const val CHANNEL_ID = "neo_security_alert"
    private const val CHANNEL_NAME_RES = R.string.notifier_channel_security

    /** 转移被拦截 */
    private const val ID_TRANSFER_BLOCKED = 2001

    /** 转移已放行（警示） */
    private const val ID_TRANSFER_ALLOWED = 2002

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(CHANNEL_NAME_RES),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notifier_channel_security_desc)
        }
        manager.createNotificationChannel(channel)
    }

    private fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun post(
        context: Context,
        id: Int,
        title: String,
        text: String
    ) {
        if (!canPost(context)) {
            android.util.Log.w("Dhizuku", "[Alert] 无通知权限，跳过：$title")
            return
        }
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.round_hourglass_empty_black_24)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(id, notification)
        }
    }

    /** 转移被拦截 */
    fun transferBlocked(context: Context, appLabel: String) {
        post(
            context,
            ID_TRANSFER_BLOCKED,
            context.getString(R.string.alert_transfer_blocked_title),
            context.getString(R.string.alert_transfer_blocked_text, appLabel)
        )
    }

    /** 转移已放行，提示所有者可能已变更 */
    fun transferAllowed(context: Context, appLabel: String) {
        post(
            context,
            ID_TRANSFER_ALLOWED,
            context.getString(R.string.alert_transfer_allowed_title),
            context.getString(R.string.alert_transfer_allowed_text, appLabel)
        )
    }
}
