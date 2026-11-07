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
import com.neodhizuku.more.ui.activity.WipeConfirmActivity

/**
 * 安全警示通知。
 *
 * 与守护服务那条常驻通知分开：本渠道是 IMPORTANCE_HIGH，
 * 用于设备所有者转移这类高风险事件，必须能立刻引起注意。
 */
object AlertNotifier {

    const val CHANNEL_ID = "neo_security_alert"

    /**
     * 渠道名。
     *
     * 刻意不用 `const val`：`const val` 会把 `R.string.*` 的值在编译期内联，
     * 若代码与资源不同步（只覆盖了源码没覆盖 res），内联的旧 ID 在运行期
     * 可能解析不到，`getString()` 会抛 Resources$NotFoundException。
     * 改成运行期读取即可随资源同步。
     */
    private val CHANNEL_NAME_RES = R.string.notifier_channel_security

    /** 资源缺失时的兜底渠道名，保证通知渠道一定能建起来。 */
    private const val CHANNEL_NAME_FALLBACK = "NeoDhizuku 安全警示"

    /** 安全地取字符串：取不到就用兜底值，绝不抛异常。 */
    private fun str(context: Context, res: Int, fallback: String, vararg args: Any): String =
        runCatching {
            if (args.isEmpty()) context.getString(res) else context.getString(res, *args)
        }.getOrDefault(fallback)

    /** 转移被拦截 */
    private const val ID_TRANSFER_BLOCKED = 2001

    /** 转移已放行（警示） */
    private const val ID_TRANSFER_ALLOWED = 2002

    /** 授权请求（弹窗未出现时的兜底入口） */
    private const val ID_PERMISSION_REQUEST = 2100
    private const val ID_WIPE_BLOCKED = 2200

    /** 守卫降级（反射失效，进入保守拦截） */
    private const val ID_GUARD_DEGRADED = 2300

    /** 单项接口策略拒绝 */
    private const val ID_API_DENIED = 2400

    fun ensureChannel(context: Context) {
        // 通知是辅助功能，任何一步失败都只记录、不外抛
        runCatching {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                str(context, CHANNEL_NAME_RES, CHANNEL_NAME_FALLBACK),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = str(context, R.string.notifier_channel_security_desc, "")
            }
            manager.createNotificationChannel(channel)
        }.onFailure {
            android.util.Log.w("Dhizuku", "[Alert] 创建通知渠道失败：${it.message}")
        }
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
        runCatching {
            ensureChannel(context)
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.round_hourglass_empty_black_24)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
            if (contentIntent != null) builder.setContentIntent(contentIntent)
            NotificationManagerCompat.from(context).notify(id, builder.build())
        }.onFailure {
            android.util.Log.w("Dhizuku", "[Alert] 发送通知失败：${it.message}")
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
            str(context, R.string.alert_permission_request_title, "授权请求"),
            str(context, R.string.alert_permission_request_text, "$label 请求授权", label),
            pending
        )
    }

    /** 转移被拦截 */
    fun transferBlocked(context: Context, appLabel: String) {
        post(
            context,
            ID_TRANSFER_BLOCKED,
            str(context, R.string.alert_transfer_blocked_title, "已拦截转移请求"),
            str(context, R.string.alert_transfer_blocked_text, "已拦截 $appLabel 转移设备所有者", appLabel)
        )
    }

    /**
     * 擦除设备数据的请求被拦截。
     *
     * 这条通知必须能点进去 —— 用户确认后才可能放行，因此带 PendingIntent
     * 打开确认页。用 FLAG_ACTIVITY_NEW_TASK：通知栏发起不在任何任务栈内。
     */
    fun wipeBlocked(context: Context, appLabel: String) {
        val uid = WipeGuard.lastRejectedUid
        val intent = Intent(context, WipeConfirmActivity::class.java)
            .putExtra(WipeConfirmActivity.EXTRA_UID, uid)
            .putExtra(WipeConfirmActivity.EXTRA_LABEL, appLabel)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        val pending = runCatching {
            PendingIntent.getActivity(context, ID_WIPE_BLOCKED, intent, flags)
        }.getOrNull()

        post(
            context,
            ID_WIPE_BLOCKED,
            str(context, R.string.alert_wipe_blocked_title, "已拦截擦除请求"),
            str(
                context, R.string.alert_wipe_blocked_text,
                "已拦截 $appLabel 擦除设备数据的请求", appLabel
            ),
            pending
        )
    }

    /**
     * 守卫降级：反射不到方法对照表，守卫进入保守模式（一律拦截）。
     *
     * 这条通知必须发。守卫失效后用户会发现"设备策略类操作全都用不了了"，
     * 但不说清楚原因，用户只会以为是 bug —— 最终往往是关掉加固了事，
     * 反而退回到无保护状态。
     */
    fun guardDegraded(context: Context) {
        post(
            context,
            ID_GUARD_DEGRADED,
            str(context, R.string.alert_guard_degraded_title, "安全守卫已降级"),
            str(
                context, R.string.alert_guard_degraded_text,
                "无法载入设备策略方法对照表，当前所有设备策略调用都会被拦截"
            )
        )
    }

    /**
     * 某个接口被单项策略拒绝。
     *
     * 与其它拦截不同，这条**不带** PendingIntent —— 被拒的是第三方的一次调用，
     * 用户此时没有必须马上处理的事；通知的作用只是让用户知道「它试过、被挡了」，
     * 免得用户奇怪某个应用为什么突然不工作。
     *
     * @param pkg 调用方包名，取不到时传 null
     * @param apiLabel 被拒接口的人话名称
     */
    fun apiDenied(context: Context, pkg: String?, apiLabel: String) {
        val who = pkg?.takeIf { it.isNotBlank() }
            ?: runCatching { context.packageName }.getOrDefault("未知应用")
        post(
            context,
            ID_API_DENIED,
            str(context, R.string.alert_api_denied_title, "已阻止一次接口调用"),
            str(
                context, R.string.alert_api_denied_text,
                "已阻止 %1\$s 调用「%2\$s」", who, apiLabel
            )
        )
    }

    /** 转移已放行，提示所有者可能已变更 */
    fun transferAllowed(context: Context, appLabel: String) {
        post(
            context,
            ID_TRANSFER_ALLOWED,
            str(context, R.string.alert_transfer_allowed_title, "设备所有者已转移"),
            str(context, R.string.alert_transfer_allowed_text, "$appLabel 已转移设备所有者", appLabel)
        )
    }
}
