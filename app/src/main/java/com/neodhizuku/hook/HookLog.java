package com.neodhizuku.hook;

import android.util.Log;
import de.robv.android.xposed.XposedBridge;

public final class HookLog {
    private static final String TAG = "NHook";
    private HookLog() { }
    private static void write(String level, String msg) {
        try { Log.i(TAG, level + " " + msg); } catch (Throwable ignored) { }
        try { XposedBridge.log(TAG + "/" + level + ": " + msg); } catch (Throwable ignored) { }
    }
    public static void i(String m) { write("I", m); }
    public static void w(String m) { write("W", m); }
    public static void e(String m, Throwable t) { write("E", m + " " + (t == null ? "" : t.toString())); }
}
