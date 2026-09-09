package com.github.nexters.ppotto.board.support

import org.jooq.DSLContext
import org.jooq.impl.DSL
import java.util.concurrent.TimeUnit

private const val UNGRANTED_LOCK_COUNT = "(select count(*) from pg_locks where not granted)"

private const val AWAIT_SECONDS = 10L

fun DSLContext.awaitBlockedLock() {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(AWAIT_SECONDS)
    while (System.nanoTime() < deadline) {
        val blocked = fetchValue(DSL.field(UNGRANTED_LOCK_COUNT, Long::class.java)) ?: 0L
        if (blocked > 0) return
        Thread.onSpinWait()
    }
    error("두 번째 요청이 잠금에서 대기하지 않아 직렬화를 확인할 수 없습니다.")
}
