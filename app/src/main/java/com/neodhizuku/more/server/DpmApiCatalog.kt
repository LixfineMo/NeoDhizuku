package com.neodhizuku.more.server

import android.content.Context
import android.os.Binder
import android.os.Parcel

/**
 * 设备策略接口（DevicePolicyManager）的口语化对照表。
 * 背景：`remote_transact` 是第三方拿到授权后最常用也最危险的一条路径 ——
 * 它把 DPM 的 binder 包装起来，以本应用的设备所有者身份调用其任意方法。
 * 但服务端能看到的只有接口描述符和事务码，日志里形如
 * 「调用 android.app.admin.IDevicePolicyManager（事务码 47）」，
 * 既不知道在干什么，也不知道想干什么。
 * 这里做两件事：
 * 1. 把事务码翻译回方法名（反射系统 Stub 的 TRANSACTION_* 常量，与
 *    [OwnerTransferGuard] 同一套做法，不硬编码）；
 * 2. 把方法名映射成一句人话，并尽量带上参数 ——
 *    「把密码最小长度改为 6」比「调用了 setPasswordMinimumLength」有用得多。
 * 对照表只覆盖常见方法；未收录的回退到「调用系统接口 xxx」，
 * 仍然比只给事务码可读。
 */
object DpmApiCatalog {

    /** 目标接口描述符 */
    internal const val DPM_DESCRIPTOR = "android.app.admin.IDevicePolicyManager"

    private const val STUB_CLASS = "android.app.admin.IDevicePolicyManager\$Stub"

    private const val TRANSACTION_PREFIX = "TRANSACTION_"

    /** 事务码 -> 方法名，懒加载并缓存 */
    @Volatile
    private var codeToName: Map<Int, String>? = null

    /** 方法名 -> 事务码，用于反查（wipeData 拦截要用） */
    @Volatile
    private var nameToCode: Map<String, Int>? = null

    /**
     * 单个参数的类型。
     * DPM 的 AIDL 参数顺序并不统一：多数方法第一个参数是 ComponentName
     * （本应用自己），第二个才是真正的目标；且 ComponentName 在 Parcel 中的
     * 写入格式随版本有差异。因此这里不做精确定位，而是按顺序宽容扫描，
     * 见 [collectValues]。
     */
    private enum class ArgType { STRING, INT, BOOL, SENSITIVE }

    /**
     * 方法级描述规则。
     * [pattern] 支持一个 `%1` 占位符，按顺序填入读取到的参数值。
     */
    private data class MethodSpec(
        /** 一句话人话描述 */
        val pattern: String,
        /** 需要读取并展示的参数；为空则只显示固定描述 */
        val args: List<ArgType> = emptyList(),
        /** 是否为高危操作 */
        val dangerous: Boolean = false
    )

    /**
     * 常见方法的对照表。
     * [MethodSpec.pattern] 支持 %1 / %2 占位符，按顺序填入扫描到的参数值；
     * [MethodSpec.args] 描述期望读到的参数类型，用于决定怎么解释读到的值。
     */
    private val CATALOG: Map<String, MethodSpec> = mapOf(
        // —— 锁屏与密码 ——
        "lockNow" to MethodSpec("立即锁定屏幕"),
        "lockNowWithOptions" to MethodSpec("立即锁定屏幕"),
        "resetPassword" to MethodSpec(
            "重置锁屏密码",
            args = listOf(ArgType.SENSITIVE),
            dangerous = true
        ),
        "setPasswordQuality" to MethodSpec(
            "把密码质量要求改为等级 %1",
            args = listOf(ArgType.INT)
        ),
        "getPasswordQuality" to MethodSpec("读取密码质量要求"),
        "setPasswordMinimumLength" to MethodSpec(
            "把密码最小长度改为 %1",
            args = listOf(ArgType.INT)
        ),
        "getPasswordMinimumLength" to MethodSpec("读取密码最小长度"),
        "setPasswordMinimumUpperCase" to MethodSpec(
            "把密码最少大写字母数改为 %1",
            args = listOf(ArgType.INT)
        ),
        "setPasswordMinimumLowerCase" to MethodSpec(
            "把密码最少小写字母数改为 %1",
            args = listOf(ArgType.INT)
        ),
        "setPasswordMinimumNumeric" to MethodSpec(
            "把密码最少数字数改为 %1",
            args = listOf(ArgType.INT)
        ),
        "setPasswordMinimumSymbols" to MethodSpec(
            "把密码最少符号数改为 %1",
            args = listOf(ArgType.INT)
        ),
        "setPasswordMinimumNonLetter" to MethodSpec(
            "把密码最少非字母字符数改为 %1",
            args = listOf(ArgType.INT)
        ),
        "setPasswordExpirationTimeout" to MethodSpec(
            "把密码有效期改为 %1 毫秒",
            args = listOf(ArgType.INT)
        ),
        "setMaximumTimeToLock" to MethodSpec(
            "把自动锁屏等待时间改为 %1 毫秒",
            args = listOf(ArgType.INT)
        ),
        "getMaximumTimeToLock" to MethodSpec("读取自动锁屏等待时间"),
        "setMaximumFailedPasswordsForWipe" to MethodSpec(
            "把「密码连续错误 %1 次后擦除设备」的阈值改为 %1",
            args = listOf(ArgType.INT),
            dangerous = true
        ),
        "getMaximumFailedPasswordsForWipe" to MethodSpec("读取密码错误擦除阈值"),
        "setKeyguardDisabledFeatures" to MethodSpec(
            "把锁屏上被禁用的功能改为 %1",
            args = listOf(ArgType.INT)
        ),
        "getKeyguardDisabledFeatures" to MethodSpec("读取锁屏禁用功能"),

        // —— 应用管理 ——
        "setApplicationHidden" to MethodSpec(
            "把应用「%1」%2",
            args = listOf(ArgType.STRING, ArgType.BOOL)
        ),
        "isApplicationHidden" to MethodSpec(
            "查询应用「%1」是否被隐藏",
            args = listOf(ArgType.STRING)
        ),
        "setApplicationRestrictions" to MethodSpec(
            "修改应用「%1」的运行限制",
            args = listOf(ArgType.STRING)
        ),
        "getApplicationRestrictions" to MethodSpec(
            "读取应用「%1」的运行限制",
            args = listOf(ArgType.STRING)
        ),
        "setApplicationSuspended" to MethodSpec("暂停 / 恢复应用"),
        "isApplicationSuspended" to MethodSpec("查询应用是否被暂停"),
        "setUninstallBlocked" to MethodSpec(
            "禁止 / 允许卸载应用「%1」",
            args = listOf(ArgType.STRING)
        ),
        "isUninstallBlocked" to MethodSpec("查询应用是否被禁止卸载"),
        "enableSystemApp" to MethodSpec("启用系统应用"),
        "installSystemUpdate" to MethodSpec("安装系统更新", dangerous = true),
        "clearApplicationUserData" to MethodSpec(
            "清除应用「%1」的数据",
            args = listOf(ArgType.STRING),
            dangerous = true
        ),

        // —— 权限与账户 ——
        "setPermissionGrantState" to MethodSpec(
            "修改应用「%1」的权限授予状态",
            args = listOf(ArgType.STRING)
        ),
        "getPermissionGrantState" to MethodSpec("读取应用权限授予状态"),
        "setPermittedInputMethods" to MethodSpec("限制可用的输入法"),
        "getPermittedInputMethods" to MethodSpec("读取可用输入法限制"),
        "setAccountManagementDisabled" to MethodSpec("禁止 / 允许某类账户的自动同步"),

        // —— 设备限制 ——
        "setCameraDisabled" to MethodSpec(
            "把摄像头设为%1",
            args = listOf(ArgType.BOOL)
        ),
        "getCameraDisabled" to MethodSpec("读取摄像头禁用状态"),
        "setScreenCaptureDisabled" to MethodSpec(
            "把屏幕截图设为%1",
            args = listOf(ArgType.BOOL)
        ),
        "getScreenCaptureDisabled" to MethodSpec("读取截屏禁用状态"),
        "setStatusBarDisabled" to MethodSpec("禁用 / 启用状态栏"),
        "setAutoTimeRequired" to MethodSpec("强制 / 取消强制自动时间"),
        "addUserRestriction" to MethodSpec(
            "添加用户限制：%1",
            args = listOf(ArgType.STRING)
        ),
        "clearUserRestriction" to MethodSpec(
            "清除用户限制：%1",
            args = listOf(ArgType.STRING)
        ),
        "setUserRestriction" to MethodSpec(
            "把用户限制「%1」设为启用=%2",
            args = listOf(ArgType.STRING, ArgType.BOOL)
        ),
        "getUserRestrictions" to MethodSpec("读取用户限制列表"),

        // —— 存储与加密 ——
        "setStorageEncryption" to MethodSpec(
            "把「要求存储加密」设为%1",
            args = listOf(ArgType.BOOL),
            dangerous = true
        ),
        "getStorageEncryption" to MethodSpec("读取存储加密要求"),

        // —— 用户与资料 ——
        "createAndManageUser" to MethodSpec("创建并管理一个新用户", dangerous = true),
        "removeUser" to MethodSpec("删除用户", dangerous = true),
        "switchUser" to MethodSpec("切换当前用户", dangerous = true),
        "setProfileName" to MethodSpec("修改资料名称"),
        "setProfileEnabled" to MethodSpec("启用 / 停用工作资料"),
        "setProfileOwner" to MethodSpec("设置资料管理员", dangerous = true),
        "setDeviceOwner" to MethodSpec("设置设备所有者", dangerous = true),
        "transferOwnership" to MethodSpec("转移设备所有者", dangerous = true),
        "clearDeviceOwnerApp" to MethodSpec("清除设备所有者", dangerous = true),

        // —— 系统操作 ——
        "reboot" to MethodSpec("重启设备", dangerous = true),
        "wipeData" to MethodSpec("擦除设备数据", dangerous = true),
        "wipeDataWithReason" to MethodSpec("擦除设备数据", dangerous = true),
        "setSystemUpdatePolicy" to MethodSpec("修改系统更新策略"),
        "getSystemUpdatePolicy" to MethodSpec("读取系统更新策略"),
        "setGlobalSetting" to MethodSpec(
            "修改系统全局设置「%1」",
            args = listOf(ArgType.STRING)
        ),
        "setSecureSetting" to MethodSpec(
            "修改系统安全设置「%1」",
            args = listOf(ArgType.STRING)
        ),
        "setOrganizationName" to MethodSpec("修改组织名称"),
        "getDeviceOwnerComponent" to MethodSpec("读取当前设备所有者"),
        "isDeviceOwnerApp" to MethodSpec("查询某个应用是否为设备所有者"),
        "isProfileOwnerApp" to MethodSpec("查询某个应用是否为资料管理员"),
        "isAdminActive" to MethodSpec("查询某个应用是否为设备管理员"),
        "getActiveAdmins" to MethodSpec("读取已激活的设备管理员列表"),

        "getDeviceOwnerComponentOnUser" to MethodSpec("读取指定用户的设备所有者"),
        "getProfileOwnerAsUser" to MethodSpec("读取指定用户的资料管理员"),
        "getDelegatePackages" to MethodSpec("读取已委派的应用列表"),
        "getDelegatedScopes" to MethodSpec("读取已委派的能力"),
        "isProvisioningAllowed" to MethodSpec("查询是否允许初始化设备"),
        "isCallingUserAffiliated" to MethodSpec("查询当前用户是否隶属于设备所有者")
    )

    /**
     * 按 [MethodSpec.args] 的顺序从 Parcel 里取值并填入占位符。
     * 两个必须守住的点：
     * 1. **读完必须恢复 dataPosition**。这次读取发生在真正的 transact 之前，
     *    位置没恢复会导致后续调用读到错位的数据，后果远大于日志不好看。
     * 2. 读到一半出错就放弃参数解析，退回固定描述 —— 展示问题不应影响调用。
     * DPM 多数方法第一个参数是 ComponentName（本应用自己），写入格式随版本有
     * 差异，无法精确定位。因此这里只按类型顺序读取并过滤掉本应用包名，
     * 取到的第一个"像目标"的值作为填充内容。
     */
    private fun fillPattern(spec: MethodSpec, data: Parcel): String {
        if (spec.args.isEmpty()) return spec.pattern

        val saved = data.dataPosition()
        val values = try {
            // 跳过 AIDL 头部的 interface token
            data.setDataPosition(0)
            runCatching { data.enforceInterface(DPM_DESCRIPTOR) }
            data.setDataPosition(saved)
            spec.args.map { type -> readOne(data, type) }
        } catch (_: Throwable) {
            null
        } finally {
            // 无论成败都复位，避免影响紧接着的真正调用
            runCatching { data.setDataPosition(saved) }
        } ?: return spec.pattern

        var out = spec.pattern
        values.forEachIndexed { index, value ->
            out = out.replace("%${index + 1}", value)
        }
        // 有占位符没填上（参数没读到）就退回固定描述，不留半截句子
        if (out.contains('%')) return spec.pattern
        return out
    }

    /**
     * readOne
     * (data: Parcel, type: ArgType)
     * 作用：按类型读一个值；读不到时返回 "?"，由调用方判定后回退
     * 备注：仅内部使用
     */
    private fun readOne(data: Parcel, type: ArgType): String = when (type) {
        ArgType.STRING -> runCatching { data.readString() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() && it != DPM_DESCRIPTOR }
            ?.takeIf { it != com.neodhizuku.more.BuildConfig.APPLICATION_ID }
            ?: "?"
        ArgType.INT -> runCatching { data.readInt().toString() }.getOrNull() ?: "?"
        ArgType.BOOL -> runCatching {
            if (data.readInt() != 0) "启用" else "停用"
        }.getOrNull() ?: "?"
        ArgType.SENSITIVE -> "<已隐藏>"
    }

    private fun Map<Int, String>?.orElseLoadCodeToName(): Map<Int, String> {
        this?.let { return it }
        val loaded = loadCodeMap()
        codeToName = loaded
        nameToCode = loaded.entries.associate { it.value to it.key }
        return loaded
    }

    private fun Map<String, Int>?.orElseLoadNameToCode(): Map<String, Int> {
        nameToCode?.let { return it }
        val loaded = loadCodeMap()
        codeToName = loaded
        nameToCode = loaded.entries.associate { it.value to it.key }
        return nameToCode!!
    }

    /**
     * loadCodeMap
     * ()
     * 作用：加载 CodeMap
     * 备注：仅内部使用
     */
    private fun loadCodeMap(): Map<Int, String> {
        val result = LinkedHashMap<Int, String>()
        runCatching {
            HiddenApiExempt.once()
            val clazz = Class.forName(STUB_CLASS)
            clazz.declaredFields.forEach { field ->
                if (!field.name.startsWith(TRANSACTION_PREFIX)) return@forEach
                if (field.type != Int::class.javaPrimitiveType &&
                    field.type != Integer.TYPE
                ) return@forEach
                field.isAccessible = true
                val value = field.getInt(null)
                val name = field.name.removePrefix(TRANSACTION_PREFIX)
                // 同一事务码可能被多个常量引用，保留先遇到的
                if (!result.containsKey(value)) result[value] = name
            }
        }
        com.neodhizuku.more.NeoLog.i(com.neodhizuku.more.NeoLog.SERVER) {
            "[DpmCatalog] 载入设备策略方法对照 ${result.size} 条"
        }
        return result
    }

    /**
     * methodName
     * (code: Int)
     * 作用：事务码 -> 方法名。 反射的是运行期的 Stub 常量，因此能跟上系统版本变化； 反射失败时返回 null，调用方会退回显示事务码。
     * 备注：—
     */
    fun methodName(code: Int): String? = codeToName.orElseLoadCodeToName()[code]

    /**
     * 一句人话描述。
     * @param descriptor 目标 binder 的接口描述符；不是设备策略接口时直接回退
     * @param code 事务码
     * @param data 调用参数，可为空；为空时只显示固定描述，不带实参
     */
    fun describe(
        descriptor: String?,
        code: Int,
        data: Parcel? = null,
        context: Context? = null
    ): String {
        if (descriptor != DPM_DESCRIPTOR) {
            return "调用 ${targetLabel(descriptor)} 的事务 $code"
        }
        val name = methodName(code)
            ?: return "调用设备策略接口的事务 $code"
        val spec = CATALOG[name]
        if (spec == null) {
            // 未写进参数对照表的方法，优先用清单里的本地化名称；
            // 连清单都没收录时也要说明白，而不是只丢一个裸方法名
            val human = context?.let { DpmPolicyStrings.name(it, name) } ?: name
            return if (human == name) "调用系统接口「$name」"
            else "调用系统接口「$human」"
        }
        return if (data == null) spec.pattern
        else fillPattern(spec, data)
    }

    /**
     * targetLabel
     * (descriptor: String?)
     * 作用：目标 binder 的人话短名，用于日志而不是丢一长串接口描述符
     * 备注：—
     */
    fun targetLabel(descriptor: String?): String = when {
        descriptor == null -> "未知接口"
        descriptor == DPM_DESCRIPTOR -> "设备策略接口"
        else -> descriptor.substringAfterLast('.')
    }

    /**
     * interfaceLabel
     * (context: Context, descriptor: String?, code: Int)
     * 作用：日志里的接口标识：人话名 + 方法名括注；都认不出时至少给事务码
     * 备注：—
     */
    fun interfaceLabel(context: Context, descriptor: String?, code: Int): String {
        if (descriptor != DPM_DESCRIPTOR) {
            return "${targetLabel(descriptor)}（事务 $code）"
        }
        val name = methodName(code)
            ?: return "设备策略接口（事务 $code）"
        val label = DpmPolicyStrings.readable(context, name)
        if (label != name) return label
        // 清单未收录但已补进人话对照表的方法（多为查询类，无参数占位符）
        val spec = CATALOG[name] ?: return "系统接口「$name」"
        return spec.pattern.replace(Regex("「%\\d+」"), "").replace(Regex("%\\d+"), "").trim()
    }

    /**
     * 供外部判定：某次调用是否为擦除设备数据。
     * **无法判定时按「是」处理**（fail-closed）。
     * 原实现在 `methodName(code)` 返回 null 时返回 false，等于"认不出就放行" ——
     * 反射一旦失效（Android 版本差异 / hidden API 豁免失败 / 方法改名），
     * 擦除守卫会**静默失效**，调用方完全不知情。
     * 擦除是不可逆操作，认不出时应当拦下来交给用户确认，而不是默认放行。
     * 代价是反射失效期间设备策略调用需要逐次确认，用户可关闭擦除守卫恢复。
     */
    fun isWipe(binder: android.os.IBinder?, code: Int): Boolean {
        val descriptor = runCatching { binder?.interfaceDescriptor }.getOrNull()
        if (descriptor != DPM_DESCRIPTOR) return false
        val method = methodName(code) ?: return true
        return method == "wipeData" || method == "wipeDataWithReason"
    }

    /**
     * 反射出的对照表是否可用。
     * 为空说明反射失败 —— 此时 [isWipe] 会对所有设备策略调用返回 true，
     * 守卫进入保守模式。调用方应据此提示用户，而不是让用户莫名发现"都用不了了"。
     */
    /**
     * invalidateCache
     * ()
     * 作用：调试用：丢弃事务码映射缓存，下次访问时重新反射系统 Stub。
     * 备注：—
     */
    fun invalidateCache() {
        codeToName = null
        nameToCode = null
    }

    /**
     * isCatalogLoaded
     * ()
     * 作用：判断 CatalogLoaded
     * 备注：—
     */
    fun isCatalogLoaded(): Boolean = codeToName.orElseLoadCodeToName().isNotEmpty()
}
