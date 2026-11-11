package com.neodhizuku.hook.hook.api;

import android.app.Application;
import android.app.admin.DevicePolicyManager;
import android.app.admin.IDevicePolicyManager;
import android.content.ComponentName;
import android.content.pm.IPackageInstaller;
import android.content.pm.IPackageInstallerSession;
import android.content.pm.PackageInstaller;
import android.os.IBinder;

import com.rosan.dhizuku.api.Dhizuku;
import com.rosan.dhizuku.api.DhizukuBinderWrapper;
import com.neodhizuku.hook.DhizukuAPI;
import com.neodhizuku.hook.Hook;
import com.neodhizuku.hook.HookLog;
import com.neodhizuku.hook.config.HookConfig;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 把 DPM.mService 换成 Dhizuku.binderWrapper(binder)，服务端以真 DO 身份把
 * 原始 Parcel 原样重放到系统 binder；Binder / FD / Parcelable 参数全部保留。
 * 每个 hook 独立 try/catch，找不到方法或字段就跳过，不让宿主启动崩溃。
 */
public class AndroidM extends Hook {
    private final HookConfig config;

    public AndroidM(XC_LoadPackage.LoadPackageParam lpparam, HookConfig config) {
        super(lpparam);
        this.config = config == null ? HookConfig.disabled() : config;
    }

    @Override public void hooking() {
        if (config.hijackQuery) {
            safe("hookIsOwner", this::hookIsOwner);
            safe("hookPackageNameGet", this::hookPackageNameGet);
            safe("hookQueryExtras", this::hookQueryExtras);
            safe("hookOwnerPermission", this::hookOwnerPermission);
        }
        if (config.hijackCall) {
            safe("hookDeviceOwnerComponentName", this::hookDeviceOwnerComponentName);
            if (config.allowPackageInstaller) safe("hookPackageInstaller", this::hookPackageInstaller);
        }
    }

    private void safe(String name, Runnable r) {
        try { r.run(); } catch (Throwable t) { HookLog.e(name, t); }
    }

    // 查询面

    private void hookIsOwner() {
        XC_MethodHook hook = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) throws Throwable {
                super.beforeHookedMethod(p);
                if (p == null || p.args.length == 0) return;
                DhizukuAPI.whenGranted(() -> {
                    String owner = DhizukuAPI.getServerPackageName();
                    if (owner != null) p.args[0] = owner;      // 查"真 DO 是不是 DO" -> true
                    return null;
                });
            }
            @Override protected void afterHookedMethod(MethodHookParam p) throws Throwable {
                super.afterHookedMethod(p);
                if (config.forceOwnerTrue && DhizukuAPI.getServerPackageName() != null)
                    p.setResult(true);                          // 兜底：始终 true
            }
        };
        hookReal(DevicePolicyManager.class, "isDeviceOwnerApp", new Class<?>[]{String.class}, hook);
        hookReal(DevicePolicyManager.class, "isProfileOwnerApp", new Class<?>[]{String.class}, hook);
    }

    private static Object[] buildArgs(Class<?>[] args, XC_MethodHook hook) {
        List<Object> l = new ArrayList<>();
        if (args != null) for (Class<?> c : args) l.add(c);
        if (hook != null) l.add(hook);
        return l.toArray();
    }

    private void hookReal(Class<?> cls, String name, Class<?>[] args, XC_MethodHook hook) {
        if (cls == null) return;
        try { XposedHelpers.findAndHookMethod(cls, name, buildArgs(args, hook)); }
        catch (Throwable t) { HookLog.w("skip " + cls.getSimpleName() + "." + name + ": " + t); }
    }

    private void hookPackageNameGet() {
        XC_MethodHook hook = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) throws Throwable {
                super.afterHookedMethod(p);
                if (p == null || !lpparam.packageName.equals(p.getResult())) return;
                boolean hookIt = false;
                for (StackTraceElement s : new Exception().getStackTrace()) {
                    if (s.getClassName().equals(Dhizuku.class.getName())) { hookIt = false; break; }
                    if (s.getClassName().equals(DevicePolicyManager.class.getName())) { hookIt = true; break; }
                }
                if (!hookIt) return;
                DhizukuAPI.whenGranted(() -> {
                    String owner = DhizukuAPI.getServerPackageName();
                    if (owner != null) p.setResult(owner);
                    return null;
                });
            }
        };
        Class<?> ctxImpl = findClass("android.app.ContextImpl");
        hookReal(ctxImpl, "getPackageName", null, hook);
        hookReal(ctxImpl, "getOpPackageName", null, hook);
    }

    /** DAX 没有覆盖的查询面：isAdminActive / getActiveAdmins / 隐藏无参 owner 查询。 */
    private void hookQueryExtras() {
        if (config.forceOwnerTrue) {
            hookReal(DevicePolicyManager.class, "isAdminActive",
                    new Class<?>[]{ComponentName.class}, new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam p) throws Throwable {
                            super.afterHookedMethod(p);
                            if (DhizukuAPI.getServerComponentName() != null) p.setResult(true);
                        }
                    });
        }
        if (config.appendActiveAdmins) {
            hookReal(DevicePolicyManager.class, "getActiveAdmins", null, new XC_MethodHook() {
                @Override @SuppressWarnings("unchecked")
                protected void afterHookedMethod(MethodHookParam p) throws Throwable {
                    super.afterHookedMethod(p);
                    ComponentName owner = DhizukuAPI.getServerComponentName();
                    Object r = p.getResult();
                    if (owner == null) return;
                    if (r instanceof List) {
                        List<ComponentName> l = (List<ComponentName>) r;
                        if (!l.contains(owner)) {
                            List<ComponentName> copy = new ArrayList<>(l);
                            copy.add(owner);
                            p.setResult(copy);                  // 追加，不整体替换
                        }
                    } else if (r == null) {
                        List<ComponentName> one = new ArrayList<>();
                        one.add(owner); p.setResult(one);
                    }
                }
            });
        }
        if (config.allowHiddenOwnerMethods) {
            // 各版本签名不同，找不到就跳过
            String[] names = {"isDeviceOwner", "isProfileOwner", "isDeviceOwnerApp",
                    "getDeviceOwnerComponentOnAnyUser", "getProfileOwner"};
            for (String n : names) {
                try {
                    for (Method m : DevicePolicyManager.class.getDeclaredMethods()) {
                        if (!m.getName().equals(n) || m.getParameterTypes().length != 0) continue;
                        XposedBridge.hookMethod(m, new XC_MethodHook() {
                            @Override protected void afterHookedMethod(MethodHookParam p) throws Throwable {
                                super.afterHookedMethod(p);
                                if (config.forceOwnerTrue && p.thisObject != null
                                        && DhizukuAPI.getServerPackageName() != null) p.setResult(true);
                            }
                        });
                    }
                } catch (Throwable t) { HookLog.w("hidden owner query " + n + ": " + t); }
            }
        }
    }

    private void hookOwnerPermission() {
        hookReal(DevicePolicyManager.class, "isActivePasswordSufficient", null,
                new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) throws Throwable {
                        super.beforeHookedMethod(p);
                        DhizukuAPI.whenGranted(() -> { p.setResult(true); return null; });
                    }
                });
    }

    // 调用面：binder 透明代理

    private void hookDeviceOwnerComponentName() {
        for (final Method method : DevicePolicyManager.class.getDeclaredMethods()) {
            safe("hookDPM." + method.getName(), () -> {
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) throws Throwable {
                        super.beforeHookedMethod(p);
                        if (p == null || !(p.thisObject instanceof DevicePolicyManager)) return; // DAX 缺这行
                        DevicePolicyManager service = (DevicePolicyManager) p.thisObject;
                        DhizukuAPI.whenGranted(() -> {
                            proxyDevicePolicyManager(service);
                            ComponentName owner = DhizukuAPI.getServerComponentName();
                            for (int i = 0; i < p.args.length; i++) {
                                if (p.args[i] instanceof ComponentName && owner != null) p.args[i] = owner;
                            }
                            if (method.getName().contains("Delegated") && p.args.length > 1) {
                                String op = DhizukuAPI.getServerPackageName();
                                if (op != null) p.args[1] = op;
                            }
                            return null;
                        });
                    }
                });
            });
        }
    }

    private void hookPackageInstaller() {
        hookReal(findClass("android.app.ApplicationPackageManager"), "getPackageInstaller", null,
                new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) throws Throwable {
                        super.afterHookedMethod(p);
                        if (p.getResult() instanceof PackageInstaller)
                            proxyPackageInstaller((PackageInstaller) p.getResult());
                    }
                });
        XC_MethodHook sessionHook = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) throws Throwable {
                super.afterHookedMethod(p);
                if (p.getResult() instanceof PackageInstaller.Session)
                    proxyPackageInstallerSession((PackageInstaller.Session) p.getResult());
            }
        };
        hookReal(PackageInstaller.class, "createSession",
                new Class<?>[]{PackageInstaller.SessionParams.class}, sessionHook);
        hookReal(PackageInstaller.class, "openSession", new Class<?>[]{int.class}, sessionHook);
        try {
            XposedBridge.hookAllConstructors(PackageInstaller.Session.class, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) throws Throwable {
                    super.afterHookedMethod(p);
                    if (p.thisObject instanceof PackageInstaller.Session)
                        proxyPackageInstallerSession((PackageInstaller.Session) p.thisObject);
                }
            });
        } catch (Throwable t) { HookLog.e("hookAllConstructors Session", t); }
    }

    private Field packageInstallerSessionField;
    private void proxyPackageInstallerSession(PackageInstaller.Session s) {
        try {
            Field f = packageInstallerSessionField;
            if (f == null) { f = s.getClass().getDeclaredField("mSession"); packageInstallerSessionField = f; }
            f.setAccessible(true);
            IPackageInstallerSession session = (IPackageInstallerSession) f.get(s);
            IBinder binder = session.asBinder();
            if (binder instanceof DhizukuBinderWrapper) return;
            f.set(s, IPackageInstallerSession.Stub.asInterface(Dhizuku.binderWrapper(binder)));
        } catch (Throwable t) { HookLog.w("proxySession: " + t); }
    }

    private Field packageInstallerServiceField, packageInstallerNameField;
    private void proxyPackageInstaller(PackageInstaller pi) {
        try {
            Field sf = packageInstallerServiceField;
            if (sf == null) { sf = pi.getClass().getDeclaredField("mInstaller"); packageInstallerServiceField = sf; }
            sf.setAccessible(true);
            IPackageInstaller installer = (IPackageInstaller) sf.get(pi);
            IBinder binder = installer.asBinder();
            if (!(binder instanceof DhizukuBinderWrapper))
                sf.set(pi, IPackageInstaller.Stub.asInterface(Dhizuku.binderWrapper(binder)));
            Field nf = packageInstallerNameField;
            if (nf == null) { nf = pi.getClass().getDeclaredField("mInstallerPackageName"); packageInstallerNameField = nf; }
            nf.setAccessible(true);
            if (!lpparam.packageName.equals(nf.get(pi))) return;
            String op = DhizukuAPI.getServerPackageName();
            if (op != null) nf.set(pi, op);
        } catch (Throwable t) { HookLog.w("proxyInstaller: " + t); }
    }

    private Field devicePolicyManagerServiceField;
    private void proxyDevicePolicyManager(DevicePolicyManager service) {
        try {
            Field f = devicePolicyManagerServiceField;
            if (f == null) { f = service.getClass().getDeclaredField("mService"); devicePolicyManagerServiceField = f; }
            f.setAccessible(true);
            IDevicePolicyManager manager = (IDevicePolicyManager) f.get(service);
            IBinder binder = manager.asBinder();
            if (binder instanceof DhizukuBinderWrapper) return;
            f.set(service, IDevicePolicyManager.Stub.asInterface(Dhizuku.binderWrapper(binder)));
        } catch (Throwable t) { HookLog.w("proxyDPM: " + t); }
    }
}
