package com.github.nexters.ppotto.global.observability

import io.kotest.core.spec.style.BehaviorSpec
import io.sentry.Sentry
import io.sentry.SentryOptions
import io.sentry.protocol.SentryTransaction
import java.util.concurrent.CopyOnWriteArrayList

private const val TEST_DSN = "https://public@localhost/1"

/**
 * `Sentry.init`/`Sentry.close`는 JVM 전역 상태다. 그 전역을 건드리는 spec은 모두 이 helper 하나만 쓰고,
 * 직접 `Sentry.init`을 호출하지 않는다. 초기화 옵션이 spec마다 달라지면 먼저 도는 spec이 뒤 spec의 기대를 바꾼다.
 */
fun BehaviorSpec.withSentry(configure: (SentryOptions) -> Unit = {}): RecordedSentry {
    val recorded = RecordedSentry()

    beforeSpec {
        Sentry.init { options ->
            options.dsn = TEST_DSN
            options.tracesSampleRate = 1.0
            options.isEnableUncaughtExceptionHandler = false
            options.isEnableBackpressureHandling = false
            options.isEnableAutoSessionTracking = false
            options.beforeSend = SentryOptions.BeforeSendCallback { _, _ -> null }
            options.beforeSendTransaction =
                SentryOptions.BeforeSendTransactionCallback { transaction, _ ->
                    recorded.transactions.add(transaction)
                    null
                }
            configure(options)
        }
    }

    afterSpec { Sentry.close() }

    return recorded
}

class RecordedSentry {
    val transactions: MutableList<SentryTransaction> = CopyOnWriteArrayList()

    fun clear() = transactions.clear()

    fun singleTransaction(): SentryTransaction = transactions.single()

    fun singleTransactionData(): Map<String, Any> =
        singleTransaction()
            .contexts.trace
            ?.data
            ?: error("트랜잭션에 trace context가 없습니다.")
}
