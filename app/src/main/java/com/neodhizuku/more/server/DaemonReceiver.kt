package com.neodhizuku.more.server

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.neodhizuku.more.NeoLog

class DaemonReceiver : BroadcastReceiver() {
    /**
     * onReceive
     * (context: Context?, intent: Intent?)
     * 作用：处理 Receive 回调
     * 备注：重写父类 / 接口方法
     */
    override fun onReceive(context: Context?, intent: Intent?) {
        val ctx = context ?: return
        val action = intent?.action
        NeoLog.i(NeoLog.BOOT) { "收到广播: ${action ?: "(无 action)"}" }

        // onReceive 返回后 BroadcastReceiver 的生命周期即结束。
        // 冷启动时进程是专门为这条广播拉起的，一旦返回就可能被立刻回收，
        // 因此尽量用 goAsync() 把生命周期延长到服务启动请求发出之后。
        // 注意：goAsync() 并不保证返回非空——当本次派发没有 PendingResult 时
        // （例如被 DeviceAdminReceiver 之类父类转发的场景）会返回 null。
        // 拿不到就按普通同步广播处理，
        // 下面的启动动作本就在 onReceive 内同步完成，不依赖它。
        val pending = goAsync()
        try {
            // 刷新 Dhizuku 的状态
            DhizukuState.sync(ctx)

            // 关键：在这里同步把守护服务拉起来，不能只依赖 sync() 的异步副作用。
            // sync() 的副作用（含 autoDaemonService）跑在 IO 协程上，
            // 开机场景下协程可能还没轮到执行，进程就已经被回收，
            // 表现就是"开机后服务没起来"。
            // BOOT_COMPLETED / LOCKED_BOOT_COMPLETED 属于前台服务后台启动的豁免场景，
            // 此处调用 startForegroundService() 是合法的。
            DhizukuState.startDaemonNow(ctx)
        } catch (t: Throwable) {
            NeoLog.w(NeoLog.BOOT) { "处理广播失败: ${t::class.java.simpleName}: ${t.message}" }
        } finally {
            runCatching { pending?.finish() }
                .onFailure { NeoLog.w(NeoLog.BOOT) { "结束广播失败: ${it.message}" } }
        }
    }
}
