package com.github.nexters.ppotto.global.retry

import org.slf4j.Logger

data class RetryPolicy(
    val maxAttempts: Int,
    val initialDelayMillis: Long = 0,
    val backoffMultiplier: Long = 1,
) {
    init {
        require(maxAttempts >= 1) { "재시도 횟수는 1 이상이어야 합니다: $maxAttempts" }
        require(initialDelayMillis >= 0) { "재시도 대기 시간은 0 이상이어야 합니다: $initialDelayMillis" }
        require(backoffMultiplier >= 1) { "백오프 배수는 1 이상이어야 합니다: $backoffMultiplier" }
    }
}

inline fun <T> retrying(
    policy: RetryPolicy,
    log: Logger,
    description: String,
    sleep: (Long) -> Unit = { Thread.sleep(it) },
    retryOn: (Throwable) -> Boolean = { true },
    block: () -> T,
): Result<T> {
    var delayMillis = policy.initialDelayMillis
    var attempt = 1
    while (true) {
        val result = runCatching(block)
        val failure = result.exceptionOrNull() ?: return result
        if (attempt >= policy.maxAttempts || !retryOn(failure)) {
            log.error("{} {}회 시도 후 실패했습니다.", description, attempt, failure)
            return result
        }
        log.warn("{} {}회차 시도가 실패해 {}ms 뒤에 재시도합니다.", description, attempt, delayMillis, failure)
        if (delayMillis > 0) sleep(delayMillis)
        delayMillis *= policy.backoffMultiplier
        attempt += 1
    }
}
