package com.neodhizuku.hook;

import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** 所有 Hook 的基类：统一 Class 查找与异常边界。 */
public abstract class Hook {
    protected final XC_LoadPackage.LoadPackageParam lpparam;

    protected Hook(XC_LoadPackage.LoadPackageParam lpparam) { this.lpparam = lpparam; }

    public static Class<?> findClass(String name, ClassLoader cl) {
        try { return XposedHelpers.findClassIfExists(name, cl); }
        catch (Throwable t) { HookLog.w("findClass " + name + " -> " + t); return null; }
    }

    public Class<?> findClass(String name) { return findClass(name, lpparam.classLoader); }

    public final void start() {
        try { if (beforeHook()) { hooking(); afterHook(); } }
        catch (Throwable t) { HookLog.e("start failed: " + lpparam.packageName, t); }
    }

    public boolean beforeHook() { return true; }
    public abstract void hooking();
    public void afterHook() { }
}
