package com.github.nexters.ppotto.analysis.infrastructure

import com.github.nexters.ppotto.analysis.domain.AnalysisStatus
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.jooq.tables.references.ANALYSIS
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.jooq.DSLContext
import org.springframework.dao.DataIntegrityViolationException

class AnalysisRepositoryTest(
    analysisRepository: AnalysisRepository,
    boardRepository: BoardRepository,
    userRepository: UserRepository,
    dslContext: DSLContext,
) : IntegrationTest({
        Given("Board가 등록된 상태에서 Analysis를 저장하면") {
            val board = boardRepository.save(userRepository.save().id)
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
            val user = userRepository.save()
            val board = boardRepository.save(user.id)

            Then("초기에는 활성 분석이 없다") {
                analysisRepository.existsActiveByUserId(user.id) shouldBe false
            }

            val analysis = analysisRepository.save(user.id, board.id)

            Then("UPLOADING 상태에서는 활성 분석이 있다") {
                analysisRepository.existsActiveByUserId(user.id) shouldBe true
            }

            dslContext
                .update(ANALYSIS)
                .set(ANALYSIS.STATUS, AnalysisStatus.ANALYZING.name)
                .where(ANALYSIS.ID.eq(analysis.id))
                .execute()

            Then("ANALYZING 상태에서도 활성 분석이 있다") {
                analysisRepository.existsActiveByUserId(user.id) shouldBe true
            }

            dslContext
                .update(ANALYSIS)
                .set(ANALYSIS.STATUS, AnalysisStatus.COMPLETED.name)
                .where(ANALYSIS.ID.eq(analysis.id))
                .execute()

            Then("COMPLETED 상태에서는 활성 분석이 없다") {
                analysisRepository.existsActiveByUserId(user.id) shouldBe false
            }

            dslContext
                .update(ANALYSIS)
                .set(ANALYSIS.STATUS, AnalysisStatus.FAILED.name)
                .where(ANALYSIS.ID.eq(analysis.id))
                .execute()

            Then("FAILED 상태에서도 활성 분석이 없다") {
                analysisRepository.existsActiveByUserId(user.id) shouldBe false
            }
        }

        Given("부분 유니크 인덱스 uk_analysis_active 제약 검증") {
            val user = userRepository.save()
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
