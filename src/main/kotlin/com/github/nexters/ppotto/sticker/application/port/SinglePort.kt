package com.github.nexters.ppotto.sticker.application.port

internal fun <T : Any> List<T>.singlePort(name: String): T = singleOrNull() ?: error("$name application port 구현이 정확히 하나 필요합니다.")
