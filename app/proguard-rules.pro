# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
-renamesourcefileattribute SourceFile

# C-08：原先保留所有 android 框架类引用，严重削弱 R8 优化，已移除
-keep public class com.rosan.neodhizuku.api.Dhizuku { *; }
-keep public class com.rosan.neodhizuku.shared.DhizukuVariables { *; }
-keep public class com.rosan.neodhizuku.App { *; }
-keep public class com.rosan.neodhizuku.ui.activity.** extends android.app.Activity
-keep public class com.rosan.neodhizuku.data.process.model.impl.** {
    public static void main(java.lang.String[]);
}

#-keep public class com.rosan.installer.data.process.model.impl.** extends com.rosan.dhizuku.data.process.repo.ProcessRepo {
#    public static void main(java.lang.String[]);
#}
#-keep public class com.rosan.installer.** extends android.app.Service
#-keep public class com.rosan.installer.** extends android.content.BroadcastReceiver
#-keep public class com.rosan.installer.** extends android.content.ContentProvider

-keep class androidx.core.content.FileProvider { *; }
-keep interface androidx.core.content.FileProvider$PathStrategy { *; }

-keep class rikka.shizuku.ShizukuProvider

-dontwarn **

# C-01：hidden-api 为 compileOnly stub，运行时会解析到真实框架类，禁止被混淆丢弃引用
-keep class android.app.admin.IDevicePolicyManager { *; }
-keep class android.os.ServiceManager { *; }

# AIDL 生成类与 Koin 模块
-keep class com.rosan.dhizuku.aidl.** { *; }
-keep class com.rosan.neodhizuku.server_api.** { *; }
-keep class com.rosan.neodhizuku.shared.** { *; }


# DAX / Xposed module: assets/xposed_init 里写的是类名字符串，
# release 开了 minify，类名一旦被 R8 改掉 LSPosed 就 ClassNotFoundException。
# 同时保留客户端 API 与 AIDL 生成类——模块代码运行在目标进程，
# 它加载的是**本 APK 的 dex**，这些类必须真实存在且名字不变。
-keep class com.neodhizuku.hook.HookInit { *; }
-keep class com.neodhizuku.hook.** { *; }
-keep class com.rosan.dhizuku.api.** { *; }
-keep class com.rosan.dhizuku.shared.** { *; }

# CVE-2026-98432 JNI: native method names and the static log callback must survive R8.
-keep class com.neodhizuku.more.ui.page.settings.experimental.Cve98432Bridge { *; }
-keepclasseswithmembernames class * { native <methods>; }
