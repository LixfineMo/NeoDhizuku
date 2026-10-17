# NeoDhizuku

A fork of [Dhizuku](https://github.com/iamr0s/Dhizuku) that shares Device Owner (DeviceOwner) permissions with other applications in a controlled manner.

---

## ⚠️ Work in Progress

**This version is under active development — do not use it on your daily device or in production.**
Some features have just landed and some have not been fully validated on real devices. The package name has been changed to `com.neodhizuku.more`, which **cannot coexist** with the official version (they would become different device owners). Please validate on a test device first. It is not data-compatible with the official version — back up your data before migrating.

---

## 1. Modifications and New Features

All changes are gated behind the `NeoDhizuku.UseNeoDhizuku` master switch (`app/src/main/java/com/neodhizuku/more/NeoDhizuku.kt`).

> When set to `false`, every sub-feature is disabled and the behavior **falls back completely to the original upstream implementation**. Individual sub-features can also be turned off separately, which is useful for A/B debugging.

### Basic Identity

| Item | Upstream | This project |
|---|---|---|
| Package name | `com.rosan.dhizuku` | `com.neodhizuku.more` |
| App name | Dhizuku | NeoDhizuku |
| Android support | Android 8.0+ (compiled against API 37) | Same |

> The API permission name and AIDL package name in the manifest are **kept unchanged** so that existing third-party clients can recognize this app without modification.

### Security Hardening

- **The authorization entry no longer trusts the UID reported by the client**: the server obtains the real identity via `Binder.getCallingUid()` and issues a one-time token (valid for 10 minutes). The UID is only accepted after the Activity validates the token. Older clients take a compatibility path and will not overwrite existing grants.
- Provider authentication, `remoteProcess` command auditing, and self-signature verification at startup.
- The signature allowlist is injected at **build time** and silently skipped when not configured, so you can build and debug freely.

### UI Rework

Following the layout ideas of the KernelSU manager, implemented with Material3 (**miuix / coui are not used**):

- **Floating bottom navigation bar**: shared by four pages, icon + text, **no emoji**. When not activated, only "Home / Settings" are shown; sub-pages hide the bar.
- **Home status card**: once activated, it becomes "one status card on the left + two small cards on the right" — checkmark icon + activated + owner type + version number (tap to open the activation page), the number of authorized apps at the top-right, and service uptime at the bottom-right (`HH:MM:SS`, reset when the service restarts). **When not activated, the original upstream style is preserved.**
- **App management**: no tabs — every app related to Dhizuku appears in a single list, with the switch showing the grant state directly. Apps that do not declare the permission in their manifest but have issued calls (such as some installers) are registered automatically and can be granted directly.
- **Dedicated blocklist page**: split out of app management; individual entries can be unblocked.
- **Authorization dialog**: no progress bar; "Allow" light green, "Deny" red and prominent, "Add to blocklist" black — all plain text with no background box; the countdown hint sits below the button row.
- **Call log**: records which capabilities each app invoked, all described in short Chinese sentences. **Persisted, retained for 30 days by default**; each entry carries the server-side runtime log fragment from that moment. A separate "Runtime log" tab shows the server's own log and is **available even when not activated**.
- **Service start/stop**: the top-right button shows a cross while running and turns into a restart icon after stopping; the uptime card turns solid red and reads "Service refused" when stopped. The ability to view grants and the call log is retained after stopping; all other operations are refused.
- **Device owner transfer interception**: once granted, a third-party app can use `binderWrapper` to call any DPM method as the device owner; if it provides no way to grant back, the owner cannot be recovered after a transfer. The setting "Allow third-party apps to transfer the device owner" is **off by default**; when off, the transfer is refused directly and a notification is posted.
- **Service policy page**: a second-level menu inside Settings that centrally manages the API service master switch, allowlist mode, the handling mode applied after the service is stopped, and the device owner transfer switch.
- **Temporary activation without removing accounts**: removes the upstream requirement to delete all accounts first. See the dedicated section below.
- **Theme page**: four dark mode options (follow system / light / dark / AMOLED pure black) plus a dynamic color switch; dark mode uses a collapsible selector.
- **Settings page**: a new "Management" section entry, **hidden entirely when not activated**.
- **Activation error hints**: raw English errors are mapped to a Chinese cause + suggested fix + copyable diagnostics.

### Debug Logging

Unified tag `NeoDhizuku` with categorized sub-tags (`Nav` / `Theme` / `Perm` / `Home` / `Apps` / `Daemon` / `Server` / `Activate` / `UI` / `Boot`). Controlled by `NeoDhizuku.DebugLog`; when off it is completely silent with no string concatenation cost.

```bash
adb logcat -s NeoDhizuku:V              # everything
adb logcat -s NeoDhizuku:V | grep Nav   # navigation only
```

---

## 2. Notes and Usage

### Activation

Same as upstream: Shizuku, ADB, and the no-account-removal mode can all make the app the device owner.
**Note the upstream advice: it is recommended to set up the device without any accounts.** If accounts already exist, you may need to remove them first.

Once the app is the device owner, third-party applications can request Device Owner capabilities through the Dhizuku API.

### Temporary Activation Without Removing Accounts

Upstream recommends configuring the device **without any accounts**, otherwise they must be removed first. This approach bypasses that restriction.

**How it works**: in `DevicePolicyManagerService`, the account check `hasIncompatibleAccountsOrNonAdb` is only read inside the `isAdb` branch. The original app goes through ADB (`dpm set-device-owner`), i.e. `isAdb=true`, so all three gates are evaluated. This approach calls directly with **the app's own uid**, so `isAdb=false` and only `hasUserSetupCompleted` is checked — **accounts are never read**.

**Seven steps**:

| Step | Executor | Action |
|---|---|---|
| ① | shell | `cmd role set-bypassing-role-qualification true` |
| ② | shell | `cmd role add-role-holder --user 0 android.app.role.DEVICE_POLICY_MANAGEMENT <pkg>` |
| ③ | shell | `settings put secure user_setup_complete 0` |
| ④ | app itself | reflect `forceUpdateUserSetupComplete(0)` to move the value from ③ into DPM's in-memory state |
| ⑤ | app itself | reflect `setActiveAdmin` |
| ⑥ | app itself | reflect `setDeviceOwner` |
| ⑦ | shell | `settings put secure user_setup_complete 1` |

③ and ④ **must be paired**: doing only ③ has no effect — the in-memory state does not change and the gate stays closed. ④'s parameter is a userId, not a status value; it copies the real value from Settings into memory bidirectionally, so ③ cannot be skipped.

**Three prerequisites**:

- Shizuku or root (to run the shell commands in ①③⑦; a normal app cannot obtain `WRITE_SECURE_SETTINGS`)
- Android 13 (API 33) or above (the `DEVICE_POLICY_MANAGEMENT` role was introduced in that version)
- Not already the device owner

**Side effect**: while the flag is 0, the Home and Recents keys of three-button navigation stop working (Back still works; gesture navigation is unaffected). The code always restores it in a `finally` block, so the device is never left in an "incomplete setup" state.

**Troubleshooting**: if the role command returns `RC=0` but the permission is still DENIED, it is most likely because the manifest does not declare `MANAGE_DEVICE_ADMINS` / `MANAGE_PROFILE_AND_DEVICE_OWNERS` — these are signature-level permissions and must be declared before the system will grant them along with the role. The page will show this hint directly.

If the setup wizard has not been restored, the page shows a red hint and disables the first two steps, leaving only "Manual restore".

### Grant Management

- The first call from a third-party app pops up an authorization request; you can allow, deny, or add it to the blocklist.
- **Once blocklisted, that app's authorization requests are no longer handled.**
- In "App management" you can toggle any app's grant at any time; the "Blocklist" page lets you unblock entries.
- With **allowlist mode** on, only apps on the allowlist may call high-risk interfaces.

### Using the Switches

Edit `NeoDhizuku.kt`:

```kotlin
const val UseNeoDhizuku: Boolean = true   // master switch; false = fall back to upstream behavior entirely
const val UseThemePage: Boolean = true    // sub-feature: theme page
const val DebugLog: Boolean = true        // sub-feature: debug logging
```

Troubleshooting tip: build a baseline with `UseNeoDhizuku = false`, then switch it to `true` and compare; if a specific sub-feature misbehaves, turn just that one off to isolate it.

### Service Policy

Settings → "Service policy", three sections:

**① API service**: master switch (allow other apps to use the API service) and allowlist mode (only allow allowlisted apps to use the API service).

**② Handling after the service is stopped** (collapsible selector; only takes effect once the service has been stopped):

| Option | Behavior |
|---|---|
| Refuse dangerous services and return failure | The service is still discoverable; non-read-only operations are refused |
| Refuse dangerous services and return success | Allowed through but not actually executed; only success is returned |
| Refuse the service entirely and return failure | Returns null; other apps cannot discover the service |
| Refuse the service entirely and return success | Returns an empty result |
| Do nothing | The authorization request hangs; the client waits indefinitely |

"Do nothing" is implemented by **not invoking the callback** rather than blocking a thread, so it does not affect the service's handling of other calls.

**③ Device owner transfer**: the switch described above in "Device owner transfer interception".

### Known Limitations

- The floating bottom bar has not been converted into a swipeable Pager; pages can only be switched by tapping.
- Theming uses Material3's native `dynamicColorScheme` (system support on Android 12+); no third-party palette library is used.
- **When coexisting with the official Dhizuku**: clients that hardcode the official package authority (such as amarok and 爱玩机工具箱) connect to the official app instead of this one, producing the "official dialog pops up while this app does not respond" symptom. A ContentProvider authority is globally unique and cannot be hijacked; the way out is to uninstall the official app and switch to the `neoCompat` or `official` flavor.
- **Activation without removing accounts requires Android 13+**; on lower versions the page shows "not supported" and hides the steps.
- Shizuku API 14 will remove `newProcess`; at that point the shell execution used by this activation path must be migrated to a User Service.

---

## 3. Building

### Requirements

Android Studio **Quail 4 (2026.1.4)** or newer (the minimum required by AGP 9.4.1), JDK 21.
Install SDK Platform 37, Build Tools 37.0.0, and NDK 29.0.14206865 from the SDK Manager.

### Build

The project has three flavors. The `namespace` and source package name are fixed to `com.neodhizuku.more`;
only the `applicationId` and the provider authorities vary per flavor:

| Flavor | applicationId | Registers official authority | Can coexist with official Dhizuku | Works with clients hardcoding the official package |
|---|---|---|---|---|
| `neo` | `com.neodhizuku.more` | No | **Yes** | No |
| `neoCompat` | `com.neodhizuku.more` | Yes | No | **Yes** |
| `official` | `com.rosan.dhizuku` | — it *is* the official one | No (same package) | **Yes** |

`neo` and `neoCompat` share the same applicationId, so only one can be installed at a time — but you can build both for comparison testing.

```
./gradlew assembleNeoDebug        Coexistence build (recommended for daily use)
./gradlew assembleNeoCompatDebug  Takeover build (uninstall official Dhizuku first)
./gradlew assembleOfficialDebug   Official-package build (uninstall official Dhizuku first)

./gradlew assembleBothDebug       Builds neo + neoCompat in one go
./gradlew assembleAllDebug        Builds all three in one go
```

For release builds, replace `Debug` with `Release` (there is also `assembleAllRelease` for all three).

> **Important**: the device owner can only belong to one package at a time. Both `neoCompat` and `official`
> require **uninstalling the official Dhizuku first**, otherwise the provider authority conflicts and
> installation fails (`INSTALL_FAILED_CONFLICTING_PROVIDER`). You will need to activate again afterwards.

The repository ships with a debug signing config (`keystore/`); replace it with your own before distributing.

---

## 4. Roadmap (a Release version will be published once complete and tested)

- [-] **Authorization entry rework**: make every third-party app able to reliably initiate an authorization request. Today some clients (mostly those depending on the old API) neither show a dialog nor issue a request; the tried server-side takeover approach proved ineffective in testing, so the real failure point must be located first (whether the client calls `requestPermission`, whether the listener receives the callback, whether the notification is actually posted) before redesigning.
- [√] ~~Device owner activation without removing accounts~~ — implemented (see "Temporary Activation Without Removing Accounts")
- [-] Verify the real-world exploitability of the "self-reported UID" authorization entry with a demo (the attack surface exists statically; actual impact unconfirmed).
- [-] Display the permission scope in the authorization dialog.
- [-] Delegated scopes configuration entry — supported by the API but with no UI entry.

---

## License

This project is a derivative of Dhizuku (GPL-3.0) and is distributed under GPL-3.0 as a whole; the full license is in the repository root as `LICENSE`.

This project is modified from [Dhizuku](https://github.com/iamr0s/Dhizuku); it changes the package name, the authorization and activation flows, the security hardening, and the UI implementation. The full change history is in the repository commits.

| Upstream project | License | Notes |
|---|---|---|
| Dhizuku | GPL-3.0 | Direct upstream; this project carries over its license |
| Dhizuku-API | MIT | Included as a local submodule; copyright and license notices retained in `Dhizuku-API/LICENSE` |
| KernelSU | GPL-3.0-or-later (`kernel/` is GPL-2.0-only) | UI and interaction inspiration only; no code reused |
| Shizuku API | Apache-2.0 | Maven dependency |

**Acknowledgements**: Dhizuku (iamr0s), Dhizuku-API (R0S), KernelSU, Shizuku (RikkaApps), and everyone who contributed code and translations upstream.
