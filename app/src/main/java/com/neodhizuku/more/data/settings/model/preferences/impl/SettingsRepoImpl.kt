package com.neodhizuku.more.data.settings.model.preferences.impl

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.core.content.edit
import com.neodhizuku.more.NeoDhizuku
import com.neodhizuku.more.data.common.util.asFlow
import com.neodhizuku.more.data.settings.repo.DenyMode
import com.neodhizuku.more.data.settings.repo.InsufficientMode
import com.neodhizuku.more.data.settings.repo.SettingsRepo
import kotlinx.coroutines.flow.Flow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SettingsRepoImpl : SettingsRepo, KoinComponent {

    companion object {
        /** 默认走自建服务器 */
        const val DEFAULT_UPDATE_SOURCE: String = "official"

        /** 官方服务器默认地址；由主域名拼出，不是编译期常量 */
        val DEFAULT_DEBUG_OFFICIAL_BASE: String =
            com.neodhizuku.more.data.common.util.UpdateLinks.OFFICIAL_BASE_DEFAULT

        const val DEFAULT_DEBUG_TIMEOUT_MS: Int = 8_000

        /** 设置文件名；DE 迁移用的标记也放在同一份 prefs 里 */
        const val PREFS_NAME = "dhizuku_settings"
        const val KEY_MIGRATED = "neo_migrated_from_ce"
    }

    private val context by inject<Context>()

    /**
     * 设置与授权库统一存放在 DE（设备加密存储），否则 Direct Boot
     * 锁屏阶段读到的是默认值，安全策略在解锁前不生效。
     */
    private val prefs: SharedPreferences by lazy {
        val base = if (NeoDhizuku.on(NeoDhizuku.FixDbDowngradeAndDeStorage)
            && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
            && !context.isDeviceProtectedStorage
        ) {
            context.createDeviceProtectedStorageContext()
        } else {
            context
        }
        val target = base.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (NeoDhizuku.on(NeoDhizuku.FixDbDowngradeAndDeStorage)
            && base !== context
        ) {
            migrateFromCeOnce(target)
        }
        target
    }

    /** 一次性把旧的 CE 设置搬到 DE，避免用户升级后开关被重置 */
    private fun migrateFromCeOnce(target: SharedPreferences) {
        if (target.getBoolean(KEY_MIGRATED, false)) return
        val legacy = runCatching {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }.getOrNull() ?: return
        val snapshot = runCatching { legacy.all }.getOrNull()
        if (!snapshot.isNullOrEmpty()) {
            runCatching {
                target.edit(true) {
                    snapshot.entries.forEach { entry ->
                        val key = entry.key
                        val value = entry.value
                        when (value) {
                            is Boolean -> putBoolean(key, value)
                            is Int -> putInt(key, value)
                            is Long -> putLong(key, value)
                            is Float -> putFloat(key, value)
                            is String -> putString(key, value)
                            is Set<*> -> @Suppress("UNCHECKED_CAST") putStringSet(
                                key, value as Set<String>
                            )
                        }
                    }
                }
            }
        }
        target.edit(true) { putBoolean(KEY_MIGRATED, true) }
    }

    override fun flowWhitelistMode(): Flow<Boolean> =
        prefs.asFlow("whitelist_mode", false)

    override fun flowDhizukuEnabled(): Flow<Boolean> =
        prefs.asFlow("dhizuku_enabled", true)

    override fun flowDonateButtonHidden(): Flow<Boolean> =
        prefs.asFlow("donate_button_hidden", false)

    override fun flowAllowOwnerTransfer(): Flow<Boolean> =
        prefs.asFlow("allow_owner_transfer", false)

    override fun flowServiceStoppedByUser(): Flow<Boolean> =
        prefs.asFlow("service_stopped_by_user", false)

    override var isWhitelistMode: Boolean
        get() = prefs.getBoolean("whitelist_mode", false)
        set(value) = prefs.edit(true) {
            putBoolean("whitelist_mode", value)
        }

    override var isDhizukuEnabled: Boolean
        get() = prefs.getBoolean("dhizuku_enabled", true)
        set(value) = prefs.edit(true) {
            putBoolean("dhizuku_enabled", value)
        }

    override var isDonateButtonHidden: Boolean
        get() = prefs.getBoolean("donate_button_hidden", false)
        set(value) = prefs.edit(true) {
            putBoolean("donate_button_hidden", value)
        }

    override var isAllowOwnerTransfer: Boolean
        get() = prefs.getBoolean("allow_owner_transfer", false)
        set(value) = prefs.edit(true) {
            putBoolean("allow_owner_transfer", value)
        }

    override var isServiceStoppedByUser: Boolean
        get() = prefs.getBoolean("service_stopped_by_user", false)
        set(value) = prefs.edit(true) {
            putBoolean("service_stopped_by_user", value)
        }

    override var insufficientMode: String
        get() = prefs.getString("insufficient_mode", InsufficientMode.DEFAULT.name)
            ?: InsufficientMode.DEFAULT.name
        set(value) = prefs.edit(true) {
            putString("insufficient_mode", value)
        }

    override fun flowDenyMode(): Flow<String> =
        prefs.asFlow("deny_mode", DenyMode.DEFAULT.name)

    override fun flowUseLegacyVersion(): Flow<Boolean> =
        prefs.asFlow("use_legacy_version", true)

    override var isUseLegacyVersion: Boolean
        get() = prefs.getBoolean("use_legacy_version", true)
        set(value) = prefs.edit(true) {
            putBoolean("use_legacy_version", value)
        }

    override var denyMode: String
        get() = prefs.getString("deny_mode", DenyMode.DEFAULT.name)
            ?: DenyMode.DEFAULT.name
        set(value) = prefs.edit(true) {
            putString("deny_mode", value)
        }

    override fun flowExperimentalQuickActivateFix(): Flow<Boolean> =
        prefs.asFlow("exp_quick_activate_fix", false)

    override fun flowExperimentalDelegationCleanup(): Flow<Boolean> =
        prefs.asFlow("exp_delegation_cleanup", false)

    override fun flowExperimentalVerboseLog(): Flow<Boolean> =
        prefs.asFlow("exp_verbose_log", false)

    override fun flowUpdateAllowPreRelease(): Flow<Boolean> =
        prefs.asFlow("update_allow_pre_release", false)

    override fun flowStartupUpdateCheck(): Flow<Boolean> =
        prefs.asFlow("startup_update_check", true)

    override fun flowStrictServiceCheck(): Flow<Boolean> =
        prefs.asFlow("strict_service_check", false)

    override fun flowExperimentalStrictWhitelist(): Flow<Boolean> =
        prefs.asFlow("exp_strict_whitelist", false)

    override var isUpdateAllowPreRelease: Boolean
        get() = prefs.getBoolean("update_allow_pre_release", false)
        set(value) = prefs.edit(true) {
            putBoolean("update_allow_pre_release", value)
        }

    override var isStartupUpdateCheck: Boolean
        get() = prefs.getBoolean("startup_update_check", true)
        set(value) = prefs.edit(true) {
            putBoolean("startup_update_check", value)
        }

    override fun flowUpdateSource(): Flow<String> =
        prefs.asFlow("update_source", DEFAULT_UPDATE_SOURCE)

    override fun flowUpdateAllowInsecure(): Flow<Boolean> =
        prefs.asFlow("update_allow_insecure", false)

    override fun flowUpdateCustomUrl(): Flow<String> =
        prefs.asFlow("update_custom_url", "")

    override fun flowDebugOfficialBase(): Flow<String> =
        prefs.asFlow("debug_official_base", DEFAULT_DEBUG_OFFICIAL_BASE)

    override fun flowDebugBuildIndex(): Flow<String> =
        prefs.asFlow("debug_build_index", "")

    override fun flowDebugTimeoutMs(): Flow<Int> =
        prefs.asFlow("debug_timeout_ms", DEFAULT_DEBUG_TIMEOUT_MS)

    override var updateSource: String
        get() = prefs.getString("update_source", DEFAULT_UPDATE_SOURCE) ?: DEFAULT_UPDATE_SOURCE
        set(value) = prefs.edit(true) {
            putString("update_source", value)
        }

    override var isUpdateAllowInsecure: Boolean
        get() = prefs.getBoolean("update_allow_insecure", false)
        set(value) = prefs.edit(true) {
            putBoolean("update_allow_insecure", value)
        }

    override var updateCustomUrl: String
        get() = prefs.getString("update_custom_url", "").orEmpty()
        set(value) = prefs.edit(true) {
            putString("update_custom_url", value.trim())
        }

    override var debugOfficialBase: String
        get() = prefs.getString("debug_official_base", DEFAULT_DEBUG_OFFICIAL_BASE)
            ?: DEFAULT_DEBUG_OFFICIAL_BASE
        set(value) = prefs.edit(true) {
            putString("debug_official_base", value.trim())
        }

    override var debugBuildIndex: String
        get() = prefs.getString("debug_build_index", "").orEmpty()
        set(value) = prefs.edit(true) {
            putString("debug_build_index", value.trim())
        }

    override var debugTimeoutMs: Int
        get() = prefs.getInt("debug_timeout_ms", DEFAULT_DEBUG_TIMEOUT_MS)
        set(value) = prefs.edit(true) {
            putInt("debug_timeout_ms", value.coerceIn(1_000, 120_000))
        }

    override var isExperimentalQuickActivateFix: Boolean
        get() = prefs.getBoolean("exp_quick_activate_fix", false)
        set(value) = prefs.edit(true) {
            putBoolean("exp_quick_activate_fix", value)
        }

    override var isExperimentalDelegationCleanup: Boolean
        get() = prefs.getBoolean("exp_delegation_cleanup", false)
        set(value) = prefs.edit(true) {
            putBoolean("exp_delegation_cleanup", value)
        }

    override var isExperimentalVerboseLog: Boolean
        get() = prefs.getBoolean("exp_verbose_log", false)
        set(value) = prefs.edit(true) {
            putBoolean("exp_verbose_log", value)
        }

    override var isExperimentalStrictWhitelist: Boolean
        get() = prefs.getBoolean("exp_strict_whitelist", false)
        set(value) = prefs.edit(true) {
            putBoolean("exp_strict_whitelist", value)
        }

    override var isStrictServiceCheck: Boolean
        get() = prefs.getBoolean("strict_service_check", false)
        set(value) = prefs.edit(true) {
            putBoolean("strict_service_check", value)
        }

}
