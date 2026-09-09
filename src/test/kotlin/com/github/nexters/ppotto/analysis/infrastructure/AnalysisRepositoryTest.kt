package com.github.nexters.ppotto.analysis.infrastructure

import com.github.nexters.ppotto.analysis.domain.AnalysisStatus
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.global.identifier.AnalysisId
import com.github.nexters.ppotto.jooq.tables.references.ANALYSIS
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.jooq.DSLContext
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class AnalysisRepositoryTest(
    analysisRepository: AnalysisRepository,
    boardRepository: BoardRepository,
    userRepository: UserRepository,
    dslContext: DSLContext,
    transactionTemplate: TransactionTemplate,
    transactionManager: PlatformTransactionManager,
) : IntegrationTest({
        val progressTransactionTemplate =
            TransactionTemplate(transactionManager).apply {
                propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
            }

        Given("Board가 등록된 상태에서 Analysis를 저장하면") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val saved = analysisRepository.save(board.userId, board.id)

            When("저장된 아이디로 조회하면") {
                val found = analysisRepository.findById(saved.id)

                Then("UPLOADING 상태의 Analysis를 반환한다") {
                    found?.id shouldBe saved.id
                    found?.userId shouldBe board.userId
                    found?.boardId shouldBe board.id
                    found?.status shouldBe AnalysisStatus.UPLOADING
                    found?.progress shouldBe 0
                }
            }
        }

        Given("UPLOADING 상태의 Analysis를 ANALYZING으로 변경하면") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val saved = analysisRepository.save(board.userId, board.id)
            val startedAt = Instant.now().truncatedTo(ChronoUnit.MICROS)

            When("markAnalyzing을 호출하면") {
                analysisRepository.markAnalyzing(saved.id, startedAt)

                Then("진행률 10과 시작 시각을 기록한다") {
                    val found = analysisRepository.findById(saved.id)

                    found?.status shouldBe AnalysisStatus.ANALYZING
                    found?.progress shouldBe 10
                    found?.startedAt shouldBe startedAt
                }
            }
        }

        Given("ANALYZING 상태의 Analysis가 있을 때") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val saved = analysisRepository.save(board.userId, board.id)
            analysisRepository.markAnalyzing(saved.id, Instant.now().truncatedTo(ChronoUnit.MICROS))

            When("중간 진행률을 갱신하면") {
                analysisRepository.updateProgress(saved.id, 45)

                Then("progress가 갱신된다") {
                    analysisRepository.findById(saved.id)?.progress shouldBe 45
                }
            }

            When("100 이상의 진행률로 갱신하면") {
                analysisRepository.updateProgress(saved.id, 100)

                Then("완료 전 최대 진행률 99로 보정된다") {
                    analysisRepository.findById(saved.id)?.progress shouldBe 99
                }
            }
        }

        Given("ANALYZING 상태의 Analysis 행이 다른 트랜잭션에서 잠겨 있을 때") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val saved = analysisRepository.save(board.userId, board.id)
            analysisRepository.markAnalyzing(saved.id, Instant.now().truncatedTo(ChronoUnit.MICROS))

            When("중간 진행률 갱신을 시도하면") {
                val executor = Executors.newSingleThreadExecutor()
                val lockAcquired = CountDownLatch(1)
                val releaseLock = CountDownLatch(1)
                val lockFuture =
                    executor.submit(
                        Callable {
                            transactionTemplate.executeWithoutResult {
                                dslContext
                                    .selectFrom(ANALYSIS)
                                    .where(ANALYSIS.ID.eq(saved.id))
                                    .forUpdate()
                                    .fetchOne()
                                lockAcquired.countDown()
                                check(releaseLock.await(10, TimeUnit.SECONDS))
                            }
                        },
                    )

                check(lockAcquired.await(10, TimeUnit.SECONDS))
                val startedAt = System.nanoTime()
                val result =
                    runCatching {
                        progressTransactionTemplate.execute { analysisRepository.updateProgress(saved.id, 45) }
                    }
                val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
                releaseLock.countDown()
                lockFuture.get(10, TimeUnit.SECONDS)
                executor.shutdownNow()

                Then("짧은 lock timeout 뒤 실패하고 진행률을 바꾸지 않는다") {
                    result.isFailure.shouldBeTrue()
                    elapsedMs shouldBeLessThan 3_000
                    analysisRepository.findById(saved.id)?.progress shouldBe 10
                }
            }
        }

        Given("UPLOADING 상태의 Analysis가 있을 때") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val saved = analysisRepository.save(board.userId, board.id)

            When("중간 진행률 갱신을 시도하면") {
                val updatedCount = analysisRepository.updateProgress(saved.id, 45)

                Then("변경하지 않는다") {
                    updatedCount shouldBe 0
                    analysisRepository.findById(saved.id)?.progress shouldBe 0
                }
            }
        }

        Given("ANALYZING 상태의 Analysis를 완료 처리하면") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val saved = analysisRepository.save(board.userId, board.id)
            val completedAt = Instant.now().truncatedTo(ChronoUnit.MICROS)
            analysisRepository.markAnalyzing(saved.id, Instant.now().truncatedTo(ChronoUnit.MICROS))
            analysisRepository.updateProgress(saved.id, 60)

            When("markCompleted를 호출하면") {
                analysisRepository.markCompleted(saved.id, completedAt)

                Then("COMPLETED 상태와 진행률 100을 기록한다") {
                    val found = analysisRepository.findById(saved.id)

                    found?.status shouldBe AnalysisStatus.COMPLETED
                    found?.progress shouldBe 100
                    found?.completedAt shouldBe completedAt
                }
            }
        }

        Given("ANALYZING 상태의 Analysis가 실패하면") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val saved = analysisRepository.save(board.userId, board.id)
            analysisRepository.markAnalyzing(saved.id, Instant.now().truncatedTo(ChronoUnit.MICROS))
            analysisRepository.updateProgress(saved.id, 60)

            When("markFailed를 호출하면") {
                analysisRepository.markFailed(saved.id, "실패")

                Then("마지막 진행률을 유지한다") {
                    val found = analysisRepository.findById(saved.id)

                    found?.status shouldBe AnalysisStatus.FAILED
                    found?.progress shouldBe 60
                    found?.failedReason shouldBe "실패"
                }
            }
        }

        Given("존재하지 않는 아이디로") {
            When("조회하면") {
                val found = analysisRepository.findById(AnalysisId(UUID.randomUUID()))

                Then("null을 반환한다") {
                    found.shouldBeNull()
                }
            }
        }

        Given("분석을 한 번도 만든 적 없는 사용자가") {
            val user = userRepository.saveTestUser()
            boardRepository.save(user.id)

            When("활성 분석을 조회하면") {
                val found = analysisRepository.findActiveByUserId(user.id)

                Then("활성 분석이 없다") {
                    found.shouldBeNull()
                }
            }
        }

        Given("UPLOADING 상태의 분석을 가진 사용자가") {
            val user = userRepository.saveTestUser()
            val board = boardRepository.save(user.id)
            val analysis = analysisRepository.save(user.id, board.id)

            When("활성 분석을 조회하면") {
                val found = analysisRepository.findActiveByUserId(user.id)

                Then("UPLOADING은 활성 상태로 본다") {
                    found?.id shouldBe analysis.id
                }
            }
        }

        Given("ANALYZING 상태의 분석을 가진 사용자가") {
            val user = userRepository.saveTestUser()
            val board = boardRepository.save(user.id)
            val analysis = analysisRepository.save(user.id, board.id)
            changeStatus(dslContext, analysis.id, AnalysisStatus.ANALYZING)

            When("활성 분석을 조회하면") {
                val found = analysisRepository.findActiveByUserId(user.id)

                Then("ANALYZING도 활성 상태로 본다") {
                    found?.id shouldBe analysis.id
                }
            }
        }

        Given("COMPLETED 상태의 분석만 가진 사용자가") {
            val user = userRepository.saveTestUser()
            val board = boardRepository.save(user.id)
            val analysis = analysisRepository.save(user.id, board.id)
            changeStatus(dslContext, analysis.id, AnalysisStatus.COMPLETED)

            When("활성 분석을 조회하면") {
                val found = analysisRepository.findActiveByUserId(user.id)

                Then("COMPLETED는 활성 상태로 보지 않는다") {
                    found.shouldBeNull()
                }
            }
        }

        Given("FAILED 상태의 분석만 가진 사용자가") {
            val user = userRepository.saveTestUser()
            val board = boardRepository.save(user.id)
            val analysis = analysisRepository.save(user.id, board.id)
            changeStatus(dslContext, analysis.id, AnalysisStatus.FAILED)

            When("활성 분석을 조회하면") {
                val found = analysisRepository.findActiveByUserId(user.id)

                Then("FAILED는 활성 상태로 보지 않는다") {
                    found.shouldBeNull()
                }
            }
        }

        Given("사용자 소유 분석을 조회할 때") {
            val owner = userRepository.saveTestUser()
            val board = boardRepository.save(owner.id)
            val analysis = analysisRepository.save(owner.id, board.id)
            val otherUser = userRepository.saveTestUser()

            When("소유자 아이디로 조회하면") {
                val found = analysisRepository.findByIdAndUserId(analysis.id, owner.id)

                Then("분석을 반환한다") {
                    found?.id shouldBe analysis.id
                }
            }

            When("다른 사용자 아이디로 조회하면") {
                val found = analysisRepository.findByIdAndUserId(analysis.id, otherUser.id)

                Then("null을 반환한다") {
                    found.shouldBeNull()
                }
            }
        }

        Given("UPLOADING 상태의 Analysis가 있을 때") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val saved = analysisRepository.save(board.userId, board.id)

            When("markFailed(취소 사유)를 호출하면") {
                val updatedCount = analysisRepository.markFailed(saved.id, "CANCELED")

                Then("UPLOADING에서도 FAILED로 전이되고 사유를 기록한다") {
                    updatedCount shouldBe 1
                    val found = analysisRepository.findById(saved.id)
                    found?.status shouldBe AnalysisStatus.FAILED
                    found?.failedReason shouldBe "CANCELED"
                }
            }
        }

        Given("이미 COMPLETED 상태로 전이된 Analysis가 있을 때") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val saved = analysisRepository.save(board.userId, board.id)
            analysisRepository.markAnalyzing(saved.id, Instant.now().truncatedTo(ChronoUnit.MICROS))
            analysisRepository.markCompleted(saved.id, Instant.now().truncatedTo(ChronoUnit.MICROS))

            When("markFailed를 호출하면") {
                val updatedCount = analysisRepository.markFailed(saved.id, "CANCELED")

                Then("이미 종료된 분석은 변경하지 않는다") {
                    updatedCount shouldBe 0
                    analysisRepository.findById(saved.id)?.status shouldBe AnalysisStatus.COMPLETED
                }
            }
        }

        Given("부분 유니크 인덱스 uk_analysis_active 제약 검증") {
            val user = userRepository.saveTestUser()
            val board = boardRepository.save(user.id)
            analysisRepository.save(user.id, board.id)

            When("동일 사용자로 활성 상태의 분석을 다시 생성하려 하면") {
                Then("DataIntegrityViolationException이 발생한다") {
                    shouldThrow<DataIntegrityViolationException> {
                        analysisRepository.save(user.id, board.id)
                    }
                }
            }
        }
    })

private fun changeStatus(
    dslContext: DSLContext,
    analysisId: AnalysisId,
    status: AnalysisStatus,
) {
    dslContext
        .update(ANALYSIS)
        .set(ANALYSIS.STATUS, status.name)
        .where(ANALYSIS.ID.eq(analysisId))
        .execute()
}
