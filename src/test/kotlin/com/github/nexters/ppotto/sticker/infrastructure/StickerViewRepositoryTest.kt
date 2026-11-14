package com.github.nexters.ppotto.sticker.infrastructure

import com.github.nexters.ppotto.analysis.infrastructure.persistence.AnalysisRepository
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.sticker.support.textStickerCreation
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.matchers.shouldBe

class StickerViewRepositoryTest(
    stickerViewRepository: StickerViewRepository,
    stickerRepository: StickerRepository,
    boardRepository: BoardRepository,
    analysisRepository: AnalysisRepository,
    userRepository: UserRepository,
) : IntegrationTest({
        Given("두 보드의 스티커를 두 사용자가 열람했을 때") {
            val firstUser = userRepository.saveTestUser()
            val secondUser = userRepository.saveTestUser()
            val firstBoard = boardRepository.save(firstUser.id)
            val secondBoard = boardRepository.save(secondUser.id)
            val firstAnalysis = analysisRepository.save(firstUser.id, firstBoard.id)
            val secondAnalysis = analysisRepository.save(secondUser.id, secondBoard.id)
            val firstSticker = stickerRepository.save(firstAnalysis.id, firstBoard.id, textStickerCreation())
            val secondSticker = stickerRepository.save(secondAnalysis.id, secondBoard.id, textStickerCreation())

            When("같은 사용자와 스티커의 열람을 두 번 저장하면") {
                val first = stickerViewRepository.saveIfAbsent(firstSticker.id, firstUser.id)
                val second = stickerViewRepository.saveIfAbsent(firstSticker.id, firstUser.id)

                Then("열람 기록은 한 번만 생긴다") {
                    first shouldBe true
                    second shouldBe false
                    stickerViewRepository.findViewedStickerIds(firstUser.id, listOf(firstSticker.id, secondSticker.id)) shouldBe
                        setOf(firstSticker.id)
                    stickerViewRepository.findViewedStickerIds(secondUser.id, listOf(firstSticker.id)) shouldBe emptySet()
                }
            }

            When("첫 사용자의 첫 보드 열람 기록만 지우면") {
                stickerViewRepository.saveIfAbsent(firstSticker.id, firstUser.id)
                stickerViewRepository.saveIfAbsent(firstSticker.id, secondUser.id)
                stickerViewRepository.saveIfAbsent(secondSticker.id, firstUser.id)
                stickerViewRepository.deleteAllByBoardAndUser(firstBoard.id, firstUser.id)

                Then("다른 사용자와 다른 보드의 열람 기록은 남는다") {
                    stickerViewRepository.findViewedStickerIds(firstUser.id, listOf(firstSticker.id)) shouldBe emptySet()
                    stickerViewRepository.findViewedStickerIds(secondUser.id, listOf(firstSticker.id)) shouldBe setOf(firstSticker.id)
                    stickerViewRepository.findViewedStickerIds(firstUser.id, listOf(secondSticker.id)) shouldBe setOf(secondSticker.id)
                }
            }

            When("사용자의 모든 열람 기록을 지우면") {
                stickerViewRepository.saveIfAbsent(firstSticker.id, firstUser.id)
                stickerViewRepository.saveIfAbsent(firstSticker.id, secondUser.id)
                stickerViewRepository.saveIfAbsent(secondSticker.id, firstUser.id)
                stickerViewRepository.deleteAllByUser(firstUser.id)

                Then("다른 사용자의 기록만 남는다") {
                    stickerViewRepository.findViewedStickerIds(firstUser.id, listOf(firstSticker.id, secondSticker.id)) shouldBe emptySet()
                    stickerViewRepository.findViewedStickerIds(secondUser.id, listOf(firstSticker.id)) shouldBe setOf(firstSticker.id)
                }
            }
        }
    })
