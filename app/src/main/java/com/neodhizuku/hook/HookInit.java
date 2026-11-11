package com.neodhizuku.hook;

import android.app.Application;
import android.content.Context;

import com.neodhizuku.hook.config.ConfigClient;
import com.neodhizuku.hook.config.HookConfig;
import com.neodhizuku.hook.hook.api.AndroidM;
import com.neodhizuku.hook.runtime.Handshake;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 模块加载时只挂 attach 观察者；目标进程起来后先握手，
 * 未启用或未报到时保持 no-op，不进入 system_server。
 */
public class HookInit implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) {
        if (lpparam == null || !lpparam.isFirstApplication || lpparam.classLoader == null) return;
        final String pkg = lpparam.packageName;
        // 排除自身与真 DO，防递归 / 防把管理器自己也代理掉
        // 排除系统框架：即使用户在 LSPosed 里误勾 android，也不进 system_server。
        if (pkg == null || pkg.equals("android")
                || pkg.equals("com.android.systemui")
                || pkg.equals("com.neodhizuku.more")
                || pkg.equals("com.neodhizuku.hook")) return;
        try {
            XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            super.afterHookedMethod(param);
                            try { onAttach(lpparam, (Context) param.args[0]); }
                            catch (Throwable t) { HookLog.e("onAttach " + pkg, t); }
                        }
                    });
        } catch (Throwable t) { HookLog.e("handleLoadPackage " + pkg, t); }
    }

    /** LSPatch 把 loader 注入了目标进程，靠特征类把两种框架区分开。 */
    private static String detectFramework(ClassLoader cl) {
        String[] marks = {
            "org.lsposed.lspatch.loader.LSPApplication",
            "org.lsposed.lspatch.Loader",
            "org.lsposed.lspatch.LoaderHook",
            "org.lsposed.lspatch.util.ModuleLoader",
            "org.lsposed.lspatch.service.LocalApplicationService"
        };
        for (String m : marks) {
            try { Class.forName(m, false, cl); return "LSPatch"; }
            catch (Throwable ignored) { }
        }
        return "LSPosed";
    }

    private void onAttach(XC_LoadPackage.LoadPackageParam lpparam, Context ctx) {
        HookConfig cfg = ConfigClient.scope(ctx, lpparam.packageName);
        if (!cfg.enabled) {
            HookLog.i("noop pkg=" + lpparam.packageName + " (not in scope / manager not ready)");
            return;                                   // 加载了，但完全不生效
        }
        boolean accepted = ConfigClient.register(ctx, lpparam.packageName,
                lpparam.processName, detectFramework(lpparam.classLoader));
        if (!accepted) { HookLog.w("register rejected pkg=" + lpparam.packageName); return; }

        Handshake.start(ctx, lpparam.packageName, cfg);

        DhizukuAPI api = new DhizukuAPI(lpparam, cfg);
        if (!api.init(ctx)) {
            HookLog.w("Dhizuku unavailable, keep no-op pkg=" + lpparam.packageName);
            return;
        }
        new AndroidM(lpparam, cfg).start();
        HookLog.i("ACTIVE pkg=" + lpparam.packageName + " v=" + cfg.configVersion
                + " query=" + cfg.hijackQuery + " call=" + cfg.hijackCall);
    }
}
