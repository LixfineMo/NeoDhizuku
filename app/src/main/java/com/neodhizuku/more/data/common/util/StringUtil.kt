package com.neodhizuku.more.data.common.util

import java.security.MessageDigest

fun ByteArray.digest(algorithm: String): ByteArray =
    MessageDigest.getInstance(algorithm).digest(this)
