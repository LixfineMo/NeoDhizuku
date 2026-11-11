package com.neodhizuku.hook.runtime;

import android.content.Context;

import com.neodhizuku.hook.HookLog;
import com.neodhizuku.hook.config.ConfigClient;
import com.neodhizuku.hook.config.HookConfig;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 心跳用于检测 configVersion 变化与连续失联。模块不主动卸载 hook
 * （Xposed 没有可靠的 unhook 时点），由 whenGranted() 读取最新配置后降级为不干预。
 */
public final class Handshake {
    private static final AtomicBoolean started = new AtomicBoolean(false);
    private static volatile long currentVersion = 0;
    private static volatile long lastSeen = 0;
    private static volatile boolean alive = true;

    private Handshake() { }

    public static void start(final Context ctx, final String pkg, HookConfig cfg) {
        if (!started.compareAndSet(false, true)) return;
        currentVersion = cfg.configVersion;
        lastSeen = System.currentTimeMillis();
        final int period = Math.max(1000, cfg.heartbeatMs);
        ScheduledExecutorService pool = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "nhook-heartbeat"); t.setDaemon(true); return t;
        });
        pool.scheduleAtFixedRate(() -> {
            try {
                long v = ConfigClient.heartbeat(ctx, pkg, currentVersion);
                if (v < 0) {
                    if (alive) HookLog.w("heartbeat lost, degrade to no-op");
                    alive = false;
                    return;
                }
                alive = true;
                lastSeen = System.currentTimeMillis();
                if (v != currentVersion) {
                    HookLog.i("configVersion " + currentVersion + " -> " + v + ", reload on next call");
                    currentVersion = v;
                    HookConfigHolder.markDirty();
                }
            } catch (Throwable t) { HookLog.e("heartbeat", t); }
        }, period, period, TimeUnit.MILLISECONDS);
    }

    public static boolean isAlive() { return alive; }
    public static long version() { return currentVersion; }
    public static long lastSeen() { return lastSeen; }

    /** 供门控读取：失联或配置变脏时返回 null，调用方据此放弃本次接管。 */
    public static final class HookConfigHolder {
        private static final AtomicBoolean dirty = new AtomicBoolean(false);
        public static void markDirty() { dirty.set(true); }
        public static boolean consumeDirty() { return dirty.compareAndSet(true, false); }
    }
}
