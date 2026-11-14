package com.neodhizuku.more.ui.page.settings.experimental

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor

/**
 * 设备侧报文通道。tun 描述符交给 native 侧注入入站 IPv4 报文；
 * VPN 建立期间由系统保活，执行结束或用户撤销授权时立即关闭。
 */
class Cve98432VpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.neodhizuku.more.cve98432.START"
        const val ACTION_STOP = "com.neodhizuku.more.cve98432.STOP"
        const val EXTRA_PROFILE = "profile"

        @Volatile
        var active: Cve98432VpnService? = null

        fun start(context: Context, profilePath: String) {
            context.startService(
                Intent(context, Cve98432VpnService::class.java)
                    .setAction(ACTION_START)
                    .putExtra(EXTRA_PROFILE, profilePath)
            )
        }

        fun stop() {
            active?.shutdown()
        }
    }

    private var tun: ParcelFileDescriptor? = null
    private var released = false

    val tunFd: Int get() = tun?.fd ?: -1

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutdown()
            return START_NOT_STICKY
        }
        if (tun == null) {
            val pfd = Builder()
                .setSession("cve98432")
                .addAddress("10.7.7.1", 32)
                // 冷路由：伪造源地址落在私有段，迫使 inet_csk_route_req 每次走完整 FIB
                .addRoute("172.16.0.0", 12)
                .establish()
            if (pfd == null) {
                Cve98432Bridge.logSink?.invoke("tun establish returned null")
                stopSelfResult(startId)
                return START_NOT_STICKY
            }
            tun = pfd
            released = false
            val rc = Cve98432Bridge.prepare(pfd.fd, intent?.getStringExtra(EXTRA_PROFILE) ?: "")
            Cve98432Bridge.logSink?.invoke("tun fd=${pfd.fd} nativePrepare=$rc")
        }
        active = this
        return START_STICKY
    }

    /** ACTION_STOP / onRevoke / onDestroy / companion.stop() can all reach here. */
    @Synchronized
    fun shutdown() {
        if (released) return
        released = true
        if (active == this) active = null
        // nativeRelease closes only its own dup()s. This ParcelFileDescriptor is
        // the sole owner of the original tun fd and closes it exactly once here.
        Cve98432Bridge.release()
        runCatching { tun?.close() }
        tun = null
        stopSelf()
    }

    override fun onRevoke() {
        shutdown()
        super.onRevoke()
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }
}
