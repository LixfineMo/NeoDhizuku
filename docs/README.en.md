# NeoDhizuku

A fork of [Dhizuku](https://github.com/iamr0s/Dhizuku) that shares Device Owner privileges with other apps in a controlled way.

[简体中文](../README.md) | [日本語](README.ja.md) | [한국어](README.ko.md) | [繁體中文](README.zh-rTW.md) | [Deutsch](README.de.md)

---

## ⚠️ Work in progress

**This build is under development — do not use it on a daily device or in production.**

The package name is now `com.neodhizuku.more`. It **cannot coexist** with the official app, and because it registers the official ContentProvider authority, you **must uninstall official Dhizuku first**. Data is not compatible — back up before migrating.

---

## Features

### Activation

- **Activate without removing accounts**: no longer requires upstream's "remove all accounts first" (needs Shizuku / root + Android 13+)
- **Device Admin mode**: grantable from system settings with the lowest barrier, but third-party clients cannot initialize (see Known limitations)
- **Activation error hints**: raw English errors mapped to plain-language cause + suggestion + one-tap copy of diagnostics

### Security

- **The authorization entry no longer trusts client-reported UIDs**: the server reads the real identity via `Binder.getCallingUid()` and issues a one-time token (valid 10 minutes)
- **Device Owner transfer blocking**: off by default; notifies when refused
- **Wipe forced confirmation**: `wipeData` is always intercepted and requires two warning dialogs plus a liability checkbox; the grant is one-time and valid for 5 minutes
- Provider authentication, `remoteProcess` command auditing, self signature check at startup

### Interface

Modeled on the KernelSU Manager layout, built with Material3 (**no miuix / coui**):

- **Floating bottom bar**: shared across four pages, icon + text, no emoji; pages can be swiped left/right or picked from the bar; hidden on sub-pages
- **Home status card**: when active, "status card on the left + two small cards on the right" — active + identity type + version, authorized app count top-right, service uptime bottom-right
- **App management**: all related apps in one list, switches show authorization state directly; apps that called without declaring the permission are registered automatically
- **Dedicated blacklist page**: unblock entries individually
- **Authorization dialog**: no progress bar; Allow in light green, Deny highlighted in red, Blacklist in black; countdown caption below the button row
- **Call log**: records which capabilities each app used, **described in plain language with arguments**; persisted for 30 days, each entry carries a server log snippet; includes a "Run log" tab
- **Service start/stop**: when stopped the uptime card turns solid red showing "Service refused"; viewing authorizations and call log still works
- **Theme page**: four dark mode options (follow system / light / dark / AMOLED black) + dynamic color; when dynamic color is off, a built-in palette is offered, plus a liquid glass bottom bar switch
- **About page**: animated icon, version and device info, GitHub / QQ links, update check (with a pre-release channel toggle)

### Service (Settings → Service)

| Section | Contents |
|---|---|
| API service | Master switch, whitelist mode, use legacy compatible version (on by default) |
| Behavior when service is off | Five-option drawer, takes effect only after the service is stopped |
| Device Owner transfer | The switch for the transfer blocking above |

"Use legacy compatible version" decides the version name returned to clients: on (default) returns upstream `2.12.0` — some clients validate the version; off returns this project's own version name.

Behavior when the service is off:

| Option | Behavior |
|---|---|
| Refuse dangerous service, return failure | Service is discoverable; non-read-only operations refused |
| Refuse dangerous service, return success | Passed through but not executed, returns success |
| Refuse service entirely, return failure | Returns null; other apps cannot find the service |
| Refuse service entirely, return success | Returns an empty result |
| Do nothing | Suspends the request; the client waits indefinitely |

"Do nothing" is implemented by **not calling back** rather than blocking a thread, so it does not affect other calls.

---

## Usage

### Activation

Shizuku, ADB, and the no-account-removal mode are supported. Once you become Device Owner, third-party apps can request Device Owner capabilities through the Dhizuku API.

### Activate without removing accounts

Upstream recommends configuring when **the device has no accounts**; this fork bypasses that restriction by calling with the app's own uid instead of going through the ADB branch, so the system only checks `hasUserSetupCompleted` and never reads accounts.

**Requirements**: Shizuku or root, Android 13 (API 33) or above, not already Device Owner.

**Side effect**: while set to 0, the Home and Recents keys of three-button navigation stop working (Back still works; gesture navigation is unaffected). The code always restores it in `finally`.

### Authorization management

- Third-party apps get an authorization prompt on first call: allow, deny, or blacklist
- **Once blacklisted, that app's authorization requests are no longer processed**
- Toggle any app's authorization anytime in App management; unblock in the Blacklist page
- With **whitelist mode** on, only whitelisted apps may call high-risk interfaces

---

## Known limitations

- The liquid glass bar is a translucent overlay, not real backdrop blur (Compose has no native backdrop blur API)
- **Third-party apps cannot initialize in Device Admin mode**: the client's `Dhizuku.init()` only accepts Device Owner / Profile Owner and fails outright for Device Admin. That code runs in the third-party process and cannot be influenced from here
- Activating without removing accounts requires Android 13+
- With USB debugging enabled, system settings pages may become inaccessible; turning off adb restores them
- Shizuku API 14 will remove `newProcess`; the no-account-removal activation must then migrate to User Service

---

## Building

Android Studio **Quail 4 (2026.1.4)** or newer (minimum for AGP 9.4.1), JDK 21. Install SDK Platform 37, Build Tools 37.0.0, NDK 29.0.14206865.

```
./gradlew assembleDebug      debug build
./gradlew assembleRelease    release build
```

Output is in `app/build/outputs/apk/debug/`.

The package name is fixed to `com.neodhizuku.more`, and the official authority `com.rosan.dhizuku.server.provider` is also registered so clients that hardcode the official package name (amarok, 爱玩机工具箱, etc.) can connect to this app.

The repository ships a debug keystore (`keystore/`); replace it with your own signing config before distributing.

---

## Roadmap

- Rework the authorization entry so all third-party apps can reliably request authorization
- Show the permission scope in the authorization dialog
- Tablet layout adaptation
- Dedicated notification channel for wipe operations

---

## License

This project is a derivative of Dhizuku (GPL-3.0) and is distributed under GPL-3.0; see `LICENSE` in the repository root.

| Upstream | License | Note |
|---|---|---|
| Dhizuku | GPL-3.0 | Direct upstream; this project follows its license |
| Dhizuku-API | MIT | Included as a local submodule |
| KernelSU | GPL-3.0-or-later | Interface and interaction inspiration only; no code reused |
| Shizuku API | Apache-2.0 | Maven dependency |

**Credits**: Dhizuku (iamr0s), Dhizuku-API (R0S), KernelSU, Shizuku (RikkaApps), and everyone who contributed code or translations upstream.
