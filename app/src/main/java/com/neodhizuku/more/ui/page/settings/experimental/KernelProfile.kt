package com.neodhizuku.more.ui.page.settings.experimental

/**
 * 目标内核的静态几何。
 *
 * 地址一律相对 _text，符号偏移由同一份 boot.img 内嵌 kallsyms 提取；
 * 结构体内部偏移按内核主版本给出候选默认值，口径与 GhostLock 的
 * kernel_profiles / target.h 一致。提取结果是候选 profile，执行前逐项校验。
 */
data class KernelProfile(
    val release: String,
    val kernelMajor: Int,
    val textBase: Long,
    val selinuxState: Long,
    val initTask: Long,
    val initCred: Long,
    val taskCred: Long,
    val credUid: Long,
    val structPageSize: Long,
    val pageRefcount: Long,
    val shinfoOffset: Long,
    val shinfoNrFrags: Long,
    val shinfoFrags: Long,
    val ipOptionsRcuSize: Long,
    val ipOptionsOptlen: Long,
    val ipOptionsSrr: Long,
    val ipOptionsData: Long,
    val textSymbols: List<Long>
) {
    /** 对应 GhostLock report.rs::require_fields：缺一项就拒绝执行。 */
    fun missing(): List<String> = buildList {
        if (selinuxState <= 0L) add("selinux_state")
        if (structPageSize <= 0L) add("struct_page_size")
        if (pageRefcount < 0L) add("page_refcount")
        if (shinfoOffset <= 0L) add("skb_shared_info")
        if (shinfoFrags < 0L) add("shinfo_frags")
        if (ipOptionsRcuSize <= 0L) add("ip_options_rcu_size")
        if (textSymbols.isEmpty()) add("text_symbols")
    }

    fun describe(): List<String> = listOf(
        "release       : $release",
        "kernel_major  : $kernelMajor",
        "_text         : 0x${hex(textBase)}",
        "selinux_state : +0x${hex(selinuxState)}",
        "init_task     : +0x${hex(initTask)}",
        "init_cred     : +0x${hex(initCred)}",
        "task.cred     : 0x${hex(taskCred)}",
        "cred.uid      : 0x${hex(credUid)}",
        "struct page   : size=0x${hex(structPageSize)} refcount=0x${hex(pageRefcount)}",
        "shinfo        : head+0x${hex(shinfoOffset)} nr_frags=+${shinfoNrFrags} frags=+${shinfoFrags}",
        "ip_options    : rcu=${ipOptionsRcuSize} optlen=+${ipOptionsOptlen} srr=+${ipOptionsSrr} data=+${ipOptionsData}",
        "text symbols  : ${textSymbols.size} anchors",
        "candidate     : struct fields use per-branch defaults, verify against this build"
    )

    /** 平面键值，native 侧用 strtoull(base=0) 读取。 */
    fun toConf(): String = buildString {
        appendLine("# Generated from the exact boot.img imported by the user.")
        appendLine("# Addresses are relative to _text; struct fields are per-branch candidates.")
        append("release=").appendLine(release.replace('\n', ' '))
        append("kernel_major=").appendLine(kernelMajor.toString())
        fun kv(key: String, value: Long) {
            append(key).append("=0x").appendLine(java.lang.Long.toHexString(value))
        }
        kv("text_base", textBase)
        kv("selinux_state", selinuxState)
        kv("init_task", initTask)
        kv("init_cred", initCred)
        kv("task_cred", taskCred)
        kv("cred_uid", credUid)
        kv("struct_page_size", structPageSize)
        kv("page_refcount", pageRefcount)
        kv("shinfo_offset", shinfoOffset)
        kv("shinfo_nr_frags", shinfoNrFrags)
        kv("shinfo_frags", shinfoFrags)
        kv("ip_options_rcu_size", ipOptionsRcuSize)
        kv("ip_options_optlen", ipOptionsOptlen)
        kv("ip_options_srr", ipOptionsSrr)
        kv("ip_options_data", ipOptionsData)
        append("text_symbols=").appendLine(textSymbols.joinToString(",") { "0x" + java.lang.Long.toHexString(it) })
    }

    companion object {
        fun defaultsFor(major: Int, release: String): KernelProfile = KernelProfile(
            release = release,
            kernelMajor = major,
            textBase = 0L,
            selinuxState = 0L,
            initTask = 0L,
            initCred = 0L,
            taskCred = 0x820L,
            credUid = 8L,
            structPageSize = 0x40L,
            pageRefcount = 0x34L,
            shinfoOffset = 384L,
            shinfoNrFrags = 2L,
            shinfoFrags = 48L,
            ipOptionsRcuSize = 72L,
            ipOptionsOptlen = 24L,
            ipOptionsSrr = 0L,
            ipOptionsData = 40L,
            textSymbols = emptyList()
        )

        private fun hex(value: Long): String = java.lang.Long.toHexString(value)
    }
}
