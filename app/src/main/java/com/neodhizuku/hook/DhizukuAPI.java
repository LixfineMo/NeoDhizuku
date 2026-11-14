package com.neodhizuku.hook;

import android.content.ComponentName;
import android.content.Context;

import com.rosan.dhizuku.api.Dhizuku;
import com.rosan.dhizuku.api.DhizukuRequestPermissionListener;
import com.rosan.dhizuku.shared.DhizukuVariables;
import com.neodhizuku.hook.config.ConfigClient;
import com.neodhizuku.hook.config.HookConfig;
import com.neodhizuku.hook.runtime.Handshake;

import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** 设备策略代理入口：握手成功后初始化，未授权时原样放行。 */
public class DhizukuAPI extends Hook {
    private static volatile ComponentName serverComponentName;
    private static volatile boolean requesting;
    private static volatile HookConfig activeConfig;
    private static volatile Context appContext;
    private static volatile String appPkg;
    private final HookConfig config;

    public DhizukuAPI(XC_LoadPackage.LoadPackageParam lpparam, HookConfig config) {
        super(lpparam);
        this.config = config == null ? HookConfig.disabled() : config;
    }

    public interface Action<T> { T run(); }

    public boolean init(Context context) {
        try {
            // hook 模块与宿主应用共用 uid：若上报本项目 API 版本，服务端会把
            // 「用原版 Dhizuku-API 的宿主」误标成 Modern，并让普通授权走进加固路径。
            // 握手只取 binder，不声明版本，宿主按 Legacy 走原版授权流程。
            DhizukuVariables.ReportClientApiVersion = false;
            if (!Dhizuku.init(context)) return false;
            ComponentName owner = Dhizuku.getOwnerComponent();
            if (owner == null) return false;
            serverComponentName = owner;
            activeConfig = config;
            appContext = context.getApplicationContext();
            appPkg = lpparam.packageName;
            HookLog.i("owner=" + owner.flattenToString());
            return true;
        } catch (Throwable t) {
            HookLog.e("Dhizuku.init", t);
            return false;
        }
    }

    public static ComponentName getServerComponentName() { return serverComponentName; }

    public static String getServerPackageName() {
        ComponentName c = serverComponentName;
        return c == null ? null : c.getPackageName();
    }

    private static boolean isPermissionGranted() {
        try { return Dhizuku.isPermissionGranted(); }
        catch (Throwable t) { return false; }
    }

    public static <T> T whenGranted(Action<T> action) {
        HookConfig c = activeConfig;
        if (c == null || !c.enabled) return null;
        if (Handshake.HookConfigHolder.consumeDirty()) refreshConfig();   // 心跳发现新版本
        if (!Handshake.isAlive()) return null;          // 心跳丢失 -> 本次不接管
        if (!isPermissionGranted()) { requestOnce(); return null; }
        try { return action.run(); }
        catch (Throwable t) { HookLog.e("hook action", t); return null; }
    }

    /** 只更新行为参数；hook 组的启用与否需重启目标进程（Xposed 无可靠动态装卸）。 */
    private static void refreshConfig() {
        try {
            HookConfig c = activeConfig;
            if (c == null || appContext == null || appPkg == null) return;
            HookConfig fresh = ConfigClient.scope(appContext, appPkg);
            c.updateFrom(fresh);
            HookLog.i("config refreshed -> v" + c.configVersion + " enabled=" + c.enabled);
        } catch (Throwable t) { HookLog.e("refreshConfig", t); }
    }

    private static void requestOnce() {
        if (requesting) return;
        synchronized (DhizukuAPI.class) {
            if (requesting) return;
            requesting = true;
            new Thread(() -> {
                try {
                    Dhizuku.requestPermission(new DhizukuRequestPermissionListener() {
                        @Override public void onRequestPermission(int grantResult) { requesting = false; }
                    });
                } catch (Throwable t) { requesting = false; HookLog.e("requestPermission", t); }
            }, "nhook-request").start();
        }
    }

    public HookConfig config() { return config; }

    @Override public void hooking() { /* init 由 HookInit 在 attach 后调用 */ }

}
