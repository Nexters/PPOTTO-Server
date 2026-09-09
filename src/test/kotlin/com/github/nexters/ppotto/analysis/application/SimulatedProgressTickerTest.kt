package com.github.nexters.ppotto.analysis.application

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

private const val TICK_COUNT = 10

class SimulatedProgressTickerTest :
    BehaviorSpec({
        Given("한 tick당 1씩 올려 floor 0에서 상한 10까지 채우는 ticker가") {
            val ticker =
                SimulatedProgressTicker(
                    minIntervalMs = 1L,
                    maxIntervalMs = 2L,
                    minStep = 1,
                    maxStep = 1,
                    fillRatio = 1.0,
                )
            val observed = CopyOnWriteArrayList<Int>()
            val completedCallbacks = AtomicInteger(0)
            val tickingThread = AtomicReference<Thread>()
            val allTicksEmitted = CountDownLatch(TICK_COUNT)

            When("tick 10회가 모두 전달될 때까지 블록이 기다리면") {
                ticker.run(
                    floor = 0,
                    ceiling = TICK_COUNT,
                    onProgress = { progress ->
                        tickingThread.set(Thread.currentThread())
                        observed += progress
                        allTicksEmitted.countDown()
                        completedCallbacks.incrementAndGet()
                    },
                ) {
                    allTicksEmitted.await(10, TimeUnit.SECONDS).shouldBeTrue()
                }

                Then("floor+1부터 상한까지 1씩 증가한 값을 빠짐없이 전달한다") {
                    observed shouldBe (1..TICK_COUNT).toList()
                }

                Then("run()이 반환될 때 tick 스레드는 이미 종료되어 있다") {
                    tickingThread
                        .get()
                        .isAlive
                        .shouldBeFalse()
                }

                Then("진행 중이던 onProgress 콜백은 중단되지 않고 끝까지 실행된다") {
                    completedCallbacks.get() shouldBe observed.size
                }
            }
        }

        Given("floor와 ceiling의 차이가 매우 작아 캡이 floor 이하로 계산되는 ticker가") {
            val ticker = SimulatedProgressTicker(minIntervalMs = 1L, maxIntervalMs = 2L, fillRatio = 0.1)
            val observed = CopyOnWriteArrayList<Int>()

            When("run을 실행하면") {
                val result = ticker.run(floor = 0, ceiling = 1, onProgress = { observed += it }) { "done" }

                Then("블록 결과를 그대로 반환한다") {
                    result shouldBe "done"
                }

                Then("백그라운드 tick을 한 번도 돌리지 않는다") {
                    observed.shouldBeEmpty()
                }
            }
        }
    })
