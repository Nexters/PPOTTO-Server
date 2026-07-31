package com.github.nexters.ppotto.analysis.infrastructure

import com.github.nexters.ppotto.analysis.domain.AnalysisStatus
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.jooq.tables.references.ANALYSIS
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.jooq.DSLContext
import org.springframework.dao.DataIntegrityViolationException
import java.time.Instant
import java.time.temporal.ChronoUnit

class AnalysisRepositoryTest(
    analysisRepository: AnalysisRepository,
    boardRepository: BoardRepository,
    userRepository: UserRepository,
    dslContext: DSLContext,
) : IntegrationTest({
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
                val found =
                    analysisRepository.findById(
                        java.util.UUID
                            .randomUUID(),
                    )

                Then("null을 반환한다") {
                    found.shouldBeNull()
                }
            }
        }

        Given("사용자별로 활성 분석 존재 여부를 조회할 때") {
            val user = userRepository.saveTestUser()
            val board = boardRepository.save(user.id)

            Then("초기에는 활성 분석이 없다") {
                analysisRepository.findActiveByUserId(user.id).shouldBeNull()
            }

            val analysis = analysisRepository.save(user.id, board.id)

            Then("UPLOADING 상태에서는 활성 분석이 있다") {
                analysisRepository.findActiveByUserId(user.id)?.id shouldBe analysis.id
            }

            dslContext
                .update(ANALYSIS)
                .set(ANALYSIS.STATUS, AnalysisStatus.ANALYZING.name)
                .where(ANALYSIS.ID.eq(analysis.id))
                .execute()

            Then("ANALYZING 상태에서도 활성 분석이 있다") {
                analysisRepository.findActiveByUserId(user.id)?.id shouldBe analysis.id
            }

            dslContext
                .update(ANALYSIS)
                .set(ANALYSIS.STATUS, AnalysisStatus.COMPLETED.name)
                .where(ANALYSIS.ID.eq(analysis.id))
                .execute()

            Then("COMPLETED 상태에서는 활성 분석이 없다") {
                analysisRepository.findActiveByUserId(user.id).shouldBeNull()
            }

            dslContext
                .update(ANALYSIS)
                .set(ANALYSIS.STATUS, AnalysisStatus.FAILED.name)
                .where(ANALYSIS.ID.eq(analysis.id))
                .execute()

            Then("FAILED 상태에서도 활성 분석이 없다") {
                analysisRepository.findActiveByUserId(user.id).shouldBeNull()
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
