package com.neodhizuku.more.server

/**
 * 第三方能调用的全部接口清单。
 *
 * 清单来源是 IDhizuku.aidl（事务码 0/1/2/11/12/13/14/15/16/17）与
 * DhizukuService 里 `enforceCallingPermission(...)` 实际传入的 func 字符串 ——
 * 后者才是服务端真正能看见的东西，两者并不一一对应：
 *
 * - `getVersionCode` / `getVersionName` / `requestPermission` 三个方法
 *   上游**没有**调用 enforceCallingPermission，因此不会走到权限判定；
 *   要纳管必须在各自的 override 里单独判一次。
 * - `unbindUserService`（13）与 `unbindUserServiceByConnection`（14）
 *   传的是同一个 func 字符串，服务端无从区分，只能作为一项处理。
 *
 * 这里同时承担日志可读性：[labelOf] 把 func 翻译成人话，
 * 替换掉日志里原有的 `func=remote_transact` 这类内部名。
 */
object ApiPolicyCatalog {

    /**
     * 上游没有调用 enforceCallingPermission、因而不会走到权限判定的三个接口。
     * 要纳管必须在各自的 override 里单独判一次。
     */
    const val ID_GET_VERSION_CODE = "get_version_code"
    const val ID_GET_VERSION_NAME = "get_version_name"
    const val ID_REQUEST_PERMISSION = "request_permission"

    /**
     * 一个可单独配置的接口项。
     *
     * @param id 配置键，持久化与界面都用它
     * @param func 服务端判定函数收到的 func 字符串；
     *             [gated] 为 false 时这里是补上的占位值，不会自然出现
     * @param label 人话名称，界面与日志共用
     * @param desc 一句话说明
     * @param dangerous 高危：默认不勾选
     * @param gated 上游是否会对它调用 enforceCallingPermission
     */
    data class ApiDef(
        val id: String,
        val func: String?,
        val label: String,
        val desc: String,
        val dangerous: Boolean = false,
        val gated: Boolean = true
    ) {
        /** 未配置时的默认取值：高危一律不允许 */
        val defaultAllowed: Boolean get() = !dangerous
    }

    val ALL: List<ApiDef> = listOf(
        ApiDef(
            id = "get_version_code",
            func = "get_version_code",
            label = "读取服务版本码",
            desc = "获取本服务接口的版本号（纯数字）。不涉任何操作，但可用于探测服务是否存在。"
        ),
        ApiDef(
            id = "get_version_name",
            func = "get_version_name",
            label = "读取服务版本名",
            desc = "获取本服务接口的版本名称。不涉任何操作。"
        ),
        ApiDef(
            id = "is_permission_granted",
            func = null,
            label = "查询是否已授权",
            desc = "第三方用它判断能不能用本服务，即「已授权 / 未授权」的查询入口。",
            gated = true
        ),
        ApiDef(
            id = "remote_transact",
            func = "remote_transact",
            label = "调用系统接口",
            desc = "以本应用的设备所有者身份调用任意系统接口，含设备策略（锁屏、改密码、隐藏应用、清除数据、擦除设备）。能力最强也最危险。",
            dangerous = true
        ),
        ApiDef(
            id = "remote_process",
            func = "remote_process",
            label = "执行命令",
            desc = "以 shell 身份执行任意命令并可取回输出。等同于把命令行交给第三方。",
            dangerous = true
        ),
        ApiDef(
            id = "bind_user_service",
            func = "bind_user_service",
            label = "绑定用户服务",
            desc = "让第三方在本服务进程内常驻一段自己的代码，持续具备本应用的权限。",
            dangerous = true
        ),
        ApiDef(
            id = "unbind_user_service",
            func = "unbind_user_service",
            label = "解绑用户服务",
            desc = "解除已绑定的常驻代码。事务码 13 与 14 共用同一 func，无法分开配置。"
        ),
        ApiDef(
            id = "get_delegated_scopes",
            func = "get_delegated_scopes",
            label = "读取已委派能力",
            desc = "查询某个应用被委派了哪些设备管理能力，只读。"
        ),
        ApiDef(
            id = "set_delegated_scopes",
            func = "set_delegated_scopes",
            label = "修改委派能力",
            desc = "给任意应用授予或收回设备管理能力（如冻结应用、禁止卸载、锁定权限）。",
            dangerous = true
        ),
        ApiDef(
            id = "request_permission",
            func = "request_permission",
            label = "请求授权",
            desc = "拉起本应用的授权弹窗。关闭后第三方无法发起授权，只能等用户手动授权。",
            dangerous = true,
            gated = false
        )
    )

    private val BY_ID: Map<String, ApiDef> = ALL.associateBy { it.id }

    /**
     * func -> 定义。
     *
     * key 可为 null（对应 is_permission_granted），HashMap 支持 null 键。
     */
    private val BY_FUNC: Map<String?, ApiDef> = HashMap<String?, ApiDef>().apply {
        ALL.forEach { def ->
            // gated 为 false 的项，func 是补上的占位值，
            // 不能放进来 —— 否则会与自然出现的同名调用混淆。
            if (def.gated) put(def.func, def)
        }
    }

    fun byId(id: String): ApiDef? = BY_ID[id]

    /** 按 func 反查；未纳管的 func 返回 null（保持放行，不以未知为由拦截） */
    fun byFunc(func: String?): ApiDef? = BY_FUNC[func]

    /** 未纳管的 func 返回 null（保持放行，不以未知为由拦截） */
    fun defaultAllowed(id: String): Boolean = BY_ID[id]?.defaultAllowed ?: true

    /**
     * 日志用的人话名。
     *
     * 认不出时原样返回 func，保证日志里总能看出在调什么。
     */
    fun labelOf(func: String?): String = BY_FUNC[func]?.label ?: (func ?: "查询授权状态")
}
