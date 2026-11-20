package com.neodhizuku.more.data.common.util

import java.security.MessageDigest

/**
 * digest
 * (algorithm: String)
 * 作用：执行 digest
 * 备注：—
 */
fun ByteArray.digest(algorithm: String): ByteArray =
    MessageDigest.getInstance(algorithm).digest(this)
