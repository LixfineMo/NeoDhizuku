package com.neodhizuku.hook.config;

import android.os.Bundle;

/** 由 NeoDhizuku Provider 下发的配置。IPC 不可用时一律用 disabled()。 */
public final class HookConfig {
    public boolean enabled = false;
    public long configVersion = 0;
    public boolean hijackQuery = true;
    public boolean hijackCall = true;
    public boolean forceOwnerTrue = false;
    public boolean allowPackageInstaller = true;
    public boolean allowHiddenOwnerMethods = true;
    public boolean appendActiveAdmins = true;
    public int heartbeatMs = 5000;
    public String ownerPackage = "";
    public String ownerComponent = "";

    public static HookConfig disabled() { return new HookConfig(); }

    /** 热更新时原地覆盖，保证 DhizukuAPI / AndroidM 持有的是同一个对象引用。 */
    public void updateFrom(HookConfig o) {
        if (o == null) return;
        enabled = o.enabled; configVersion = o.configVersion;
        hijackQuery = o.hijackQuery; hijackCall = o.hijackCall;
        forceOwnerTrue = o.forceOwnerTrue; allowPackageInstaller = o.allowPackageInstaller;
        allowHiddenOwnerMethods = o.allowHiddenOwnerMethods; appendActiveAdmins = o.appendActiveAdmins;
        heartbeatMs = o.heartbeatMs; ownerPackage = o.ownerPackage; ownerComponent = o.ownerComponent;
    }

    public static HookConfig from(Bundle b) {
        HookConfig c = new HookConfig();
        if (b == null) return c;                 // null = 保留上一份快照（NPatch 约定）
        c.enabled = b.getBoolean("enabled", false);
        if (b.containsKey("empty") && b.getBoolean("empty")) return c.disabled(); // 空 Cursor = 权威"无模块"
        c.configVersion = b.getLong("configVersion", 0);
        c.hijackQuery = b.getBoolean("hijackQuery", true);
        c.hijackCall = b.getBoolean("hijackCall", true);
        c.forceOwnerTrue = b.getBoolean("forceOwnerTrue", false);
        c.allowPackageInstaller = b.getBoolean("allowPackageInstaller", true);
        c.allowHiddenOwnerMethods = b.getBoolean("allowHiddenOwnerMethods", true);
        c.appendActiveAdmins = b.getBoolean("appendActiveAdmins", true);
        c.heartbeatMs = b.getInt("heartbeatMs", 5000);
        c.ownerPackage = b.getString("ownerPackage", "");
        c.ownerComponent = b.getString("ownerComponent", "");
        return c;
    }
}
