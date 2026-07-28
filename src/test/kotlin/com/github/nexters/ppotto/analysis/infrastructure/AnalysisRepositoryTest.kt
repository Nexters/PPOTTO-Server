package com.github.nexters.ppotto.analysis.infrastructure

import com.github.nexters.ppotto.analysis.domain.AnalysisStatus
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class AnalysisRepositoryTest(
    analysisRepository: AnalysisRepository,
    boardRepository: BoardRepository,
    userRepository: UserRepository,
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
    })
