package com.github.nexters.ppotto.analysis.application

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

class SimulatedProgressTickerTest :
    BehaviorSpec({
        Given("작업이 짧은 tick 간격보다 오래 걸릴 때") {
            val ticker =
                SimulatedProgressTicker(
                    minIntervalMs = 5L,
                    maxIntervalMs = 15L,
                    minStep = 1,
                    maxStep = 2,
                    fillRatio = 1.0,
                )
            val observed = mutableListOf<Int>()

            When("run으로 감싼 블록이 진행되는 동안") {
                ticker.run(floor = 0, ceiling = 10, onProgress = { observed += it }) {
                    Thread.sleep(80)
                }

                Then("전달된 값은 floor 이상, ceiling 이하이며 단조 증가한다") {
                    observed.all { it in 0..10 }.shouldBeTrue()
                    observed
                        .zipWithNext { a, b -> a <= b }
                        .all { it }
                        .shouldBeTrue()
                }

                Then("블록 종료 후에는 더 이상 값이 전달되지 않는다") {
                    val countAfterCompletion = observed.size
                    Thread.sleep(50)
                    observed.size shouldBe countAfterCompletion
                }
            }
        }

        Given("block() 종료 시점이 onProgress 콜백 실행 도중과 겹칠 때") {
            // 예전 구현(Thread.interrupt() 기반)은 이 타이밍에서 onProgress(DB 호출 등 blocking I/O를
            // 포함할 수 있는 콜백) 도중에 인터럽트가 도착해 콜백이 중간에 끊길 수 있었다. 지금 구현은
            // CountDownLatch로 tick 사이(sleep 구간)에서만 정지 신호를 확인하므로, 이미 시작된 onProgress
            // 호출은 절대 중단되지 않고 끝까지 실행되어야 한다.
            val ticker =
                SimulatedProgressTicker(
                    minIntervalMs = 5L,
                    maxIntervalMs = 10L,
                    minStep = 1,
                    maxStep = 1,
                    fillRatio = 1.0,
                )
            val onProgressStarted = CountDownLatch(1)
            val onProgressCompleted = AtomicBoolean(false)

            When("onProgress 콜백이 느리게 끝나는 동안 block()이 먼저 종료되면") {
                ticker.run(
                    floor = 0,
                    ceiling = 100,
                    onProgress = {
                        onProgressStarted.countDown()
                        Thread.sleep(100)
                        onProgressCompleted.set(true)
                    },
                ) {
                    // 첫 tick이 onProgress를 호출할 때까지 기다린 뒤, 그 콜백이 끝나기 전에 block()을 반환한다.
                    onProgressStarted.await()
                    Thread.sleep(10)
                }

                Then("run()이 반환될 때 진행 중이던 onProgress 호출은 중단되지 않고 끝까지 실행되어 있다") {
                    onProgressCompleted.get().shouldBeTrue()
                }
            }
        }

        Given("floor와 ceiling의 차이가 매우 작아 캡이 floor 이하로 계산될 때") {
            val ticker = SimulatedProgressTicker(minIntervalMs = 5L, maxIntervalMs = 15L, fillRatio = 0.1)
            val observed = mutableListOf<Int>()

            When("run을 실행하면") {
                val result =
                    ticker.run(floor = 0, ceiling = 1, onProgress = { observed += it }) {
                        Thread.sleep(30)
                        "done"
                    }

                Then("백그라운드 tick 없이 블록 결과만 즉시 반환한다") {
                    result shouldBe "done"
                    observed shouldBe emptyList()
                }
            }
        }
    })
