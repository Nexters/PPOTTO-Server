package com.github.nexters.ppotto.board.application

import com.github.nexters.ppotto.analysis.domain.AnalysisStatus
import com.github.nexters.ppotto.analysis.infrastructure.AnalysisRepository
import com.github.nexters.ppotto.analysis.infrastructure.integration.BoardAnalysisActivityAdapter
import com.github.nexters.ppotto.board.application.port.BoardAnalysisActivityPort
import com.github.nexters.ppotto.board.domain.BoardErrorCode
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.global.error.ConflictException
import com.github.nexters.ppotto.global.error.NotFoundException
import com.github.nexters.ppotto.global.identifier.AnalysisId
import com.github.nexters.ppotto.jooq.tables.references.ANALYSIS
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.jooq.DSLContext
import org.springframework.context.ApplicationContext
import java.util.UUID

class BoardAnalysisContractTest(
    applicationContext: ApplicationContext,
    boardCommandService: BoardCommandService,
    boardRepository: BoardRepository,
    analysisRepository: AnalysisRepository,
    userRepository: UserRepository,
    dslContext: DSLContext,
) : IntegrationTest({
        Given("보드와 분석 도메인이 함께 기동된 컨텍스트에서") {
            When("분석 활동 port를 조회하면") {
                val ports = applicationContext.getBeansOfType(BoardAnalysisActivityPort::class.java)

                Then("fail-closed fallback이 아니라 analysis 도메인의 실제 어댑터가 주입된다") {
                    ports.values
                        .single()
                        .shouldBeInstanceOf<BoardAnalysisActivityAdapter>()
                }

                Then("fallback 빈 이름은 등록되지 않는다") {
                    applicationContext.containsBean("boardAnalysisActivityPort") shouldBe false
                }
            }
        }

        Given("분석 진행 상태 정의를 검증할 때") {
            val indexDefinition = activeAnalysisIndexDefinition(dslContext)

            When("uk_analysis_active 부분 유니크 인덱스 정의를 읽으면") {
                Then("보드와 무관하게 유저 단위로 유일성을 강제한다") {
                    assertSoftly {
                        indexDefinition shouldContain "UNIQUE INDEX"
                        indexDefinition shouldContain "(user_id)"
                    }
                }

                Then("AnalysisStatus.ACTIVE와 같은 상태 집합을 사용한다") {
                    AnalysisStatus.entries
                        .filter { "'${it.name}'" in indexDefinition }
                        .toSet() shouldBe AnalysisStatus.ACTIVE
                }
            }
        }

        AnalysisStatus.ACTIVE.forEach { status ->
            Given("$status 분석이 대상인 삭제 가능한 보드에서") {
                val user = userRepository.saveTestUser()
                val board = boardRepository.save(user.id)
                boardRepository.save(user.id)
                analysisRepository.save(user.id.value, board.id.value).also { changeStatus(dslContext, it.id, status) }

                When("보드를 삭제하면") {
                    Then("실제 어댑터가 BOARD-005로 거부하고 보드를 유지한다") {
                        shouldThrow<ConflictException> {
                            boardCommandService.delete(board.id, user.id)
                        }.errorCode shouldBe BoardErrorCode.ACTIVE_ANALYSIS_EXISTS
                        boardRepository.findOwnedById(board.id, user.id)?.id shouldBe board.id
                    }
                }
            }
        }

        (AnalysisStatus.entries - AnalysisStatus.ACTIVE).forEach { status ->
            Given("$status 분석만 대상인 삭제 가능한 보드에서") {
                val user = userRepository.saveTestUser()
                val board = boardRepository.save(user.id)
                boardRepository.save(user.id)
                val analysis =
                    analysisRepository.save(user.id.value, board.id.value).also { changeStatus(dslContext, it.id, status) }

                When("보드를 삭제하면") {
                    boardCommandService.delete(board.id, user.id)

                    Then("삭제를 허용하고 종료된 분석 이력은 그대로 남긴다") {
                        boardRepository.findOwnedById(board.id, user.id).shouldBeNull()
                        analysisRepository.findById(analysis.id)?.boardId shouldBe board.id.value
                    }
                }
            }
        }

        Given("진행 중인 분석이 대상인 보드가 마지막 하나뿐인 사용자가") {
            val user = userRepository.saveTestUser()
            val board = boardRepository.save(user.id)
            analysisRepository.save(user.id.value, board.id.value)

            When("그 보드를 삭제하면") {
                Then("분석 확인보다 먼저 BOARD-004로 거부한다") {
                    shouldThrow<ConflictException> {
                        boardCommandService.delete(board.id, user.id)
                    }.errorCode shouldBe BoardErrorCode.LAST_BOARD_CANNOT_BE_DELETED
                }
            }
        }

        Given("다른 사용자의 보드에 진행 중인 분석이 있을 때") {
            val owner = userRepository.saveTestUser()
            val board = boardRepository.save(owner.id)
            boardRepository.save(owner.id)
            analysisRepository.save(owner.id.value, board.id.value)
            val stranger = userRepository.saveTestUser()

            When("소유자가 아닌 사용자가 삭제를 요청하면") {
                Then("분석 확인보다 먼저 BOARD-002로 거부한다") {
                    shouldThrow<NotFoundException> {
                        boardCommandService.delete(board.id, stranger.id)
                    }.errorCode shouldBe BoardErrorCode.NOT_FOUND
                }
            }
        }
    })

private fun activeAnalysisIndexDefinition(dslContext: DSLContext): String =
    dslContext
        .resultQuery("SELECT indexdef FROM pg_indexes WHERE indexname = ?", "uk_analysis_active")
        .fetchSingle()
        .get(0, String::class.java)

private fun changeStatus(
    dslContext: DSLContext,
    analysisId: UUID,
    status: AnalysisStatus,
) {
    dslContext
        .update(ANALYSIS)
        .set(ANALYSIS.STATUS, status.name)
        .where(ANALYSIS.ID.eq(AnalysisId(analysisId)))
        .execute()
}
