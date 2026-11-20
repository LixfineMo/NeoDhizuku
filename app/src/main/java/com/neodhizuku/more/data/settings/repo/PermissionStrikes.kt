package com.neodhizuku.more.data.settings.repo

import com.neodhizuku.more.NeoLog
import org.json.JSONObject
import java.io.File

/**
 * 授权请求「弹了但没人管」的连击计数。
 * 规则：同一个应用连续 [LIMIT] 次弹窗都因倒计时结束而默认拒绝（用户一次都没表态），
 * 第 LIMIT+1 次弹窗升级为「黑名单倒计时」—— 倒计时结束时直接拉黑，
 * 之后该应用不会反复打扰用户。
 * 用户只要做过一次明确选择（同意 / 拒绝 / 加入黑名单），连击清零。
 * 为什么不放 Room：这只是一份与授权记录正交的计数，加列要动数据库版本与迁移；
 * 放私有目录的 json 与白名单口径一致，改结构也不需要迁移。
 */
class PermissionStrikes(private val dir: File) {

    private val file get() = File(dir, FILE_NAME)

    /**
     * count
     * (pkg: String)
     * 作用：该应用当前累计的「超时未处理」次数
     * 备注：—
     */
    fun count(pkg: String): Int = read().optInt(pkg, 0).coerceAtLeast(0)

    /**
     * increment
     * (pkg: String)
     * 作用：记一次超时未处理，返回累加后的次数
     * 备注：—
     */
    fun increment(pkg: String): Int {
        val obj = read()
        val next = obj.optInt(pkg, 0) + 1
        obj.put(pkg, next)
        write(obj)
        return next
    }

    /**
     * reset
     * (pkg: String)
     * 作用：清零：用户已明确表态，或该应用已被拉黑
     * 备注：—
     */
    fun reset(pkg: String) {
        val obj = read()
        if (obj.has(pkg)) {
            obj.remove(pkg)
            write(obj)
        }
    }

    /**
     * read
     * ()
     * 作用：读取
     * 备注：仅内部使用
     */
    private fun read(): JSONObject {
        val f = file
        if (!f.exists()) return JSONObject()
        return runCatching { JSONObject(f.readText()) }
            .onFailure { NeoLog.w(NeoLog.PERM) { "strikes: 读取失败，按空处理 ${it.message}" } }
            .getOrDefault(JSONObject())
    }

    /**
     * write
     * (obj: JSONObject)
     * 作用：写入
     * 备注：仅内部使用
     */
    private fun write(obj: JSONObject) {
        runCatching {
            dir.mkdirs()
            val f = file
            val tmp = File(dir, "$FILE_NAME.tmp")
            tmp.writeText(obj.toString())
            // 先写临时文件再改名：写一半进程被杀不会留下半截 json
            if (!tmp.renameTo(f)) {
                f.writeText(obj.toString())
                tmp.delete()
            }
        }.onFailure { NeoLog.e(NeoLog.PERM, { "strikes: 写入失败" }, it) }
    }

    companion object {
        private const val FILE_NAME = "permission_strikes.json"

        /** 连击上限：达到后下一次弹窗即为「黑名单倒计时」 */
        const val LIMIT = 5
    }
}
