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
