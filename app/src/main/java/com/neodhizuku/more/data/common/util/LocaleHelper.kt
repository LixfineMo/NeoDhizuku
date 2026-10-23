package com.neodhizuku.more.data.common.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * 应用内语言切换。
 *
 * 项目没有引入 appcompat，因此不走 AppCompatDelegate，而是自己维护
 * Configuration：在 attachBaseContext 阶段套一层已设置好语言的 Context，
 * 切换后重建 Activity 即可生效。
 *
 * 选择持久化在 SharedPreferences("app") 的 "app_language" 键，
 * 空字符串或未设置表示跟随系统。
 */
object LocaleHelper {

    private const val PREFS_NAME = "app"
    private const val KEY_LANGUAGE = "app_language"

    /** 供语言列表展示用的一项 */
    data class Language(
        /** 资源目录限定符，如 zh-rCN；跟随系统为空字符串 */
        val tag: String,
        /** 列表里显示的名称（用该语言自身的写法，便于用户认出） */
        val displayName: String,
        /** 翻译是否完整；不完整的会回退到英文，列表里给出提示 */
        val complete: Boolean
    )

    /**
     * 已有的语言。限定符与 res/values-* 目录一一对应，
     * 不完整的是上游遗留、只有部分键的目录。
     */
    val languages: List<Language> = listOf(
        Language("", "Follow system", true),
        Language("en", "English", true),
        Language("zh-rCN", "简体中文", true),
        Language("zh-rTW", "繁體中文", true),
        Language("ja", "日本語", true),
        Language("ko", "한국어", true),
        Language("de", "Deutsch", true),
        Language("fr", "Français", true),
        Language("es", "Español", true),
        Language("pt-rBR", "Português (Brasil)", true),
        Language("ru", "Русский", true),
        Language("it", "Italiano", true),
        Language("tr", "Türkçe", true),
        Language("vi", "Tiếng Việt", true),
        Language("in", "Bahasa Indonesia", true),
        Language("pl", "Polski", true),
        Language("ar", "العربية", true),
        // 以下为上游遗留，只有部分键，缺失处会回退到英文
        Language("pt", "Português", false),
        Language("cs", "Čeština", false),
        Language("el", "Ελληνικά", false),
        Language("hu", "Magyar", false),
        Language("iw", "עברית", false),
        Language("nb-rNO", "Norsk bokmål", false),
        Language("ro", "Română", false),
        Language("sk", "Slovenčina", false),
        Language("uk", "Українська", false),
        Language("bg", "Български", false),
        Language("fa", "فارسی", false),
        Language("fil", "Filipino", false),
        Language("lv", "Latviešu", false),
        Language("ta", "தமிழ்", false),
        Language("uz", "Oʻzbekcha", false)
    )

    fun current(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, "") ?: ""

    fun save(context: Context, tag: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, tag)
            .apply()
    }

    /** 把 tag（如 zh-rCN）解析成 Locale，无法解析则返回 null */
    fun toLocale(tag: String): Locale? {
        if (tag.isEmpty()) return null
        // Android 资源目录用 -r 分隔地区（zh-rCN），Locale 用下划线（zh_CN）
        val normalized = tag.replace("-r", "-")
        val parts = normalized.split("-")
        return when (parts.size) {
            1 -> Locale(parts[0])
            2 -> Locale(parts[0], parts[1])
            else -> Locale(parts[0], parts[1])
        }
    }

    /** 生成已应用语言的 Context，供 attachBaseContext 使用 */
    fun wrap(context: Context): Context {
        val locale = toLocale(current(context)) ?: return context
        val config = context.resources.configuration

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(locale))
            // deprecated 但仍是与 setLocales/LocaleList 配套生效的写法；
            // 项目未引入 appcompat，没有 AppCompatDelegate 可用
            @Suppress("DEPRECATION")
            context.createConfigurationContext(config)
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
            @Suppress("DEPRECATION")
            context.createConfigurationContext(config)
        }
    }

    /** 从 Context 里往上找 Activity，用于切换语言后重建界面 */
    fun findActivity(context: Context): Activity? {
        var current = context
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }
}
