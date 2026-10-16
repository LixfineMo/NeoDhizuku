package com.neodhizuku.more.server

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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

    /** 授权请求（弹窗未出现时的兜底入口） */
    private const val ID_PERMISSION_REQUEST = 2100

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
        text: String,
        contentIntent: PendingIntent? = null
    ) {
        if (!canPost(context)) {
            android.util.Log.w("Dhizuku", "[Alert] 无通知权限，跳过：$title")
            return
        }
        ensureChannel(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.round_hourglass_empty_black_24)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
        if (contentIntent != null) builder.setContentIntent(contentIntent)
        runCatching {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        }
    }

    /**
     * 授权请求兜底通知。
     *
     * 弹窗因后台启动限制没能直接拉起时，改由用户点通知进入授权页 ——
     * 用户点击属于用户发起，不受 Android 10+ 后台启动 Activity 的限制。
     */
    fun permissionRequest(context: Context, uid: Int, token: String?) {
        val label = runCatching {
            val pm = context.packageManager
            val name = pm.getNameForUid(uid)
            if (name.isNullOrBlank()) uid.toString()
            else pm.getApplicationLabel(pm.getApplicationInfo(name, 0)).toString()
        }.getOrNull() ?: uid.toString()

        val intent = Intent(
            com.rosan.dhizuku.shared.DhizukuVariables.getActionRequestPermission(
                context.packageName
            )
        )
            .setPackage(context.packageName)
            .putExtra(com.rosan.dhizuku.shared.DhizukuVariables.PARAM_CLIENT_UID, uid)
            .putExtra(RequestPermissionToken.PARAM_SERVER_TOKEN, token)
            .putExtra(RequestPermissionToken.PARAM_SELF_LAUNCH, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        val pending = runCatching {
            PendingIntent.getActivity(context, ID_PERMISSION_REQUEST, intent, flags)
        }.getOrNull()

        post(
            context,
            ID_PERMISSION_REQUEST,
            context.getString(R.string.alert_permission_request_title),
            context.getString(R.string.alert_permission_request_text, label),
            pending
        )
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
