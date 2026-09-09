package com.github.nexters.ppotto.support

import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

fun <T> runConcurrently(
    taskCount: Int,
    task: (Int) -> T,
): List<Result<T>> {
    val ready = CountDownLatch(taskCount)
    val start = CountDownLatch(1)
    val executor = Executors.newFixedThreadPool(taskCount)
    return try {
        val futures =
            List(taskCount) { index ->
                executor.submit(
                    Callable {
                        ready.countDown()
                        start.await()
                        runCatching { task(index) }
                    },
                )
            }
        check(ready.await(10, TimeUnit.SECONDS)) { "동시 실행 태스크 ${ready.count}개가 10초 안에 준비되지 않았습니다." }
        start.countDown()
        futures.map { it.get(30, TimeUnit.SECONDS) }
    } finally {
        start.countDown()
        executor.shutdownNow()
    }
}
