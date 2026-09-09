package com.github.nexters.ppotto.global.logging

import org.slf4j.Logger

inline fun <T> bestEffort(
    log: Logger,
    description: String,
    block: () -> T,
): T? =
    runCatching(block)
        .onFailure { log.warn("{} 실패. 요청 결과에는 영향을 주지 않습니다.", description, it) }
        .getOrNull()
