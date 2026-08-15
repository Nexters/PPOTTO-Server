package com.github.nexters.ppotto.analysis.application

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class SimulatedProgressTicker(
    private val minIntervalMs: Long = MIN_INTERVAL_MS,
    private val maxIntervalMs: Long = MAX_INTERVAL_MS,
    private val minStep: Int = MIN_STEP,
    private val maxStep: Int = MAX_STEP,
    private val fillRatio: Double = FILL_RATIO,
) {
    fun <T> run(
        floor: Int,
        ceiling: Int,
        onProgress: (Int) -> Unit,
        block: () -> T,
    ): T {
        val cap = floor + ((ceiling - floor) * fillRatio).toInt()
        if (cap <= floor) return block()

        // block() 완료 신호는 Thread.interrupt()가 아니라 stopSignal로 전달한다. interrupt()는
        // 틱 스레드가 onProgress(DB 쓰기, JDBC 소켓 blocking I/O)를 실행하는 도중에 도착할 수도 있는데,
        // 그 경우 소켓 읽기가 강제로 끊기면서 커밋되지 않은 트랜잭션이 idle in transaction 상태로 남아
        // 해당 행의 락을 영구히 들고 있는 문제가 재현됐다(다른 쪽에서 같은 행을 갱신하려는 요청이 그 락을
        // 기다리며 함께 멈춤). stopSignal.await()는 onProgress 호출 사이(sleep 대체)에서만 대기하므로,
        // 이미 시작된 onProgress 호출은 절대 중간에 끊기지 않고 끝까지 실행된 뒤에만 루프를 빠져나간다.
        val stopSignal = CountDownLatch(1)
        val thread =
            Thread.ofVirtual().unstarted {
                var current = floor
                while (current < cap) {
                    val stopped = stopSignal.await(Random.nextLong(minIntervalMs, maxIntervalMs), TimeUnit.MILLISECONDS)
                    if (stopped) break
                    current = (current + Random.nextInt(minStep, maxStep + 1)).coerceAtMost(cap)
                    onProgress(current)
                }
            }
        thread.start()
        return try {
            block()
        } finally {
            stopSignal.countDown()
            thread.join()
        }
    }

    companion object {
        private const val MIN_INTERVAL_MS = 1000L
        private const val MAX_INTERVAL_MS = 3000L
        private const val MIN_STEP = 1
        private const val MAX_STEP = 3
        private const val FILL_RATIO = 0.7
    }
}
