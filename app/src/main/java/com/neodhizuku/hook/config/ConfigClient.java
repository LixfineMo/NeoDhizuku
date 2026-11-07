package com.neodhizuku.hook.config;

import android.content.Context;
import android.content.ContentResolver;
import android.net.Uri;
import android.os.Bundle;

import com.neodhizuku.hook.HookLog;

/** 只做 call()，不缓存强引用；null 与空 Bundle 语义按 NPatch 约定区分。 */
public final class ConfigClient {
    public static final String AUTHORITY = "content://com.neodhizuku.more.hook.config";
    public static final String M_SCOPE = "scope";
    public static final String M_REGISTER = "register";
    public static final String M_HEARTBEAT = "heartbeat";

    private ConfigClient() { }

    private static Bundle call(Context ctx, String method, Bundle extras) {
        try {
            ContentResolver cr = ctx.getContentResolver();
            return cr.call(Uri.parse(AUTHORITY), method, null, extras);
        } catch (Throwable t) {
            HookLog.w("config call " + method + " failed: " + t);
            return null;                       // null = 网络/进程不可达，不能用空覆盖快照
        }
    }

    private static Bundle base(String pkg) {
        Bundle b = new Bundle();
        b.putString("pkg", pkg);
        return b;
    }

    public static HookConfig scope(Context ctx, String pkg) {
        Bundle b = base(pkg);
        b.putInt("pid", android.os.Process.myPid());
        return HookConfig.from(call(ctx, M_SCOPE, b));
    }

    public static boolean register(Context ctx, String pkg, String processName, String framework) {
        Bundle b = base(pkg);
        b.putInt("pid", android.os.Process.myPid());
        b.putInt("uid", android.os.Process.myUid());
        b.putString("processName", processName == null ? pkg : processName);
        b.putString("framework", framework == null ? "xposed82" : framework);
        b.putInt("sdk", android.os.Build.VERSION.SDK_INT);
        Bundle r = call(ctx, M_REGISTER, b);
        return r != null && r.getBoolean("accepted", false);
    }

    public static long heartbeat(Context ctx, String pkg, long version) {
        Bundle b = base(pkg);
        b.putInt("pid", android.os.Process.myPid());
        b.putLong("configVersion", version);
        Bundle r = call(ctx, M_HEARTBEAT, b);
        if (r == null) return -1;              // -1 = 失联，本轮不重装
        return r.getLong("configVersion", version);
    }
}
