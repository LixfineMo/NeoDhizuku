English | [简体中文](README_zh_rCN.md) | [繁体中文](README_zh_Hant.md) | [日本語](README_ja.md) | [Русский](README_ru.md)
|-|-|-|-|-|

# NeoDhizuku

A tool that shares DeviceOwner permissions with other applications.

> 🧪 marks a feature that is still in testing, disabled by default, or not integrated yet. Behaviour may change.

## What's new compared to upstream Dhizuku

### Ready to use

- **Original-client compatibility**: extra provider authorities and permission actions are registered, so apps that hard-code the upstream package name (Hail, and similar tooling) work out of the box.
- **Per-app system API policy**: 86 device-policy capabilities (disable camera, lock screen, hide apps, clear app data, wipe device, ...) can be switched individually under Settings - Service. Apps without their own rules follow the global policy; configured ones are pinned to the top with the number of allowed APIs. Blocked calls raise a notification, and logs use readable names instead of internal method codes.
- **Client API version detection**: the permission dialog shows an `old` / `modern-N` badge and dispatches by version, so legacy clients are never parsed with the new layout.
- **Capability tiers and forced confirmation**: device admin / profile owner / device owner are ranked; wipe and ownership transfer are intercepted separately, and wiping needs three confirmations.
- **Deny service / insufficient capability modes**: five deny modes and three insufficient modes, including silent success, hard failure and full invisibility.
- **Call log and runtime log**: persisted for 30 days, grouped by day / app / second, with a log excerpt captured at the moment of each call.
- **Delegated scope manager**: read, change and clear scopes delegated to other apps.
- **Whitelist and strict checking**: signature fingerprint recorded at add time, optional strict mode.
- **Interface**: switchable UI engines (Original / miuix) under Theme, miuix cards and dropdowns with tilt press feedback, floating navigation bar (regular / liquid glass), card-level blur that works in both engines, background image crop / pan / zoom, pure black mode, dynamic color, custom seed color, flowing liquid-wave app icon and credits on the About page.
- **Updates and feedback**: checks the official server on launch (can be turned off in update settings), four official domains with automatic failover, relative download URLs returned by the server; feedback can be submitted from the app and the website.
- **Ownership transfer**: third-party transfers are controlled by a single switch; transfers started inside the app always require two confirmations showing the target app icon, name, package name and a warning.
- **Module**: the Xposed configuration page is always available under Service and follows the framework scope; the app never asks users to pick target apps.
- **Robustness**: hide manager from recents, one self-restart after being swiped away, device identity status with "clear leftover identity", and UI strings in 34 locale folders (untranslated entries fall back to English).

### 🧪 In testing / not integrated

- Temporary activation without removing accounts: Android 13+ only, needs Shizuku or root; temporarily modifies `user_setup_complete` and must be restored manually if interrupted.
- Four experimental switches (quick-activate fix, delegation cleanup, full runtime log, strict whitelist), all off by default.
- Debug sub-pages: live parameter editing, detailed runtime info, service config files, and Reset NeoDhizuku — they write memory state and private config directly.
- Liquid-glass refraction uses `RuntimeShader` and is only visible on Android 13 (API 33) and above. The bottom-bar pill refracts only the bar layer and the page background above / below it; it stays transparent, does not magnify its content, and regains its damped spring feel on release.
- The Modern API (transactions 0-16 compatible with upstream) has not been published.
- For re-packaged builds with a changed package name, identity removal / transfer tools exist but have no UI entry yet.

## What it does

Following the idea behind Shizuku, it lends the DeviceOwner permission it holds to other apps. Every call from a third party passes through the authorization record, per-API policy, capability tier and audit trail.

## Supported versions

Android 8.0 (API 26) through Android 17 (compile target API 37).

## Activation

Open the app and follow the wizard on the home page: device admin, Shizuku / ADB, or (Android 13+) the no-account-removal mode. Traditional methods work best on a device without accounts; the no-removal mode does not require clearing them. Steps and risks are shown in the in-app wizard and the activation guide on the website.

## Updates and feedback

Every launch checks the official server once; a card appears at the top of the home page only when an update exists. Update settings can disable automatic checks, switch sources, or set a custom manifest URL. Feedback can be sent from the bottom of the About page and from the website navbar / footer; it is collected but never displayed.

## For developers

Third-party apps obtain a binder through the Dhizuku API. Each call is judged in order: authorization record -> per-API policy -> whitelist -> strict check -> signature. Rejections produce a notification and a readable log entry. Full integration examples, result conventions and edge-case handling rules are in the "Developer docs" on the website.

## Translations

UI strings ship in 34 locale folders; locales without a complete translation fall back to English.

## Terms and privacy

[Terms of Use](TERMS.md) · [Privacy Policy](PRIVACY.md)

## Donations

See [DONATE.md](DONATE.md).

## License

GPL-3.0, see [LICENSE](../LICENSE).

---

Current build: 0.10.6Beta (versionCode 26). See ../更新日志_0.10.6Beta.txt for the changelog.

Current build: 0.10.6Beta (versionCode 26). See ../更新日志_0.10.6Beta.txt for the changelog.

NeoDhizuku (https://github.com/LixfineMo/NeoDhizuku) builds on / refers to Dhizuku, Dhizuku-API, Shizuku-API, KernelSU, AndroidHiddenApiBypass, NPatch, miuix and Koin.
