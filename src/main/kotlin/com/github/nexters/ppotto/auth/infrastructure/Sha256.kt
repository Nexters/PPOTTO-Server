package com.github.nexters.ppotto.auth.infrastructure

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.HexFormat

internal fun String.sha256Hex(): String =
    HexFormat.of().formatHex(
        MessageDigest
            .getInstance("SHA-256")
            .digest(toByteArray(StandardCharsets.UTF_8)),
    )
