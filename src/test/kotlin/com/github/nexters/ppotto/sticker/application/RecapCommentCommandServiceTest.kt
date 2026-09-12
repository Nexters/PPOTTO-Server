package com.github.nexters.ppotto.sticker.application

import com.github.nexters.ppotto.analysis.infrastructure.persistence.AnalysisRepository
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.board.support.uuidV7
import com.github.nexters.ppotto.global.error.InvalidInputException
import com.github.nexters.ppotto.global.error.NotFoundException
import com.github.nexters.ppotto.sticker.domain.RecapCommentCreation
import com.github.nexters.ppotto.sticker.domain.RecapCommentPosition
import com.github.nexters.ppotto.sticker.domain.StickerErrorCode
import com.github.nexters.ppotto.sticker.infrastructure.StickerRecapRepository
import com.github.nexters.ppotto.sticker.infrastructure.StickerRepository
import com.github.nexters.ppotto.sticker.support.textStickerCreation
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe

class RecapCommentCommandServiceTest(
    service: RecapCommentCommandService,
    stickerRepository: StickerRepository,
    stickerRecapRepository: StickerRecapRepository,
    analysisRepository: AnalysisRepository,
    boardRepository: BoardRepository,
    userRepository: UserRepository,
) : IntegrationTest({
        Given("스티커에 말풍선과 키워드 칩 코멘트가 있는 상태에서") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val analysis = analysisRepository.save(board.userId, board.id)
            val sticker = stickerRepository.save(analysis.id, board.id, textStickerCreation())
            val comments =
                stickerRecapRepository.saveComments(
                    sticker.id,
                    listOf(
                        RecapCommentCreation("말풍선", 3.0, 4.0),
                        RecapCommentCreation("키워드", null, null),
                    ),
                )
            val bubbleComment = comments.first { it.content == "말풍선" }
            val chipComment = comments.first { it.content == "키워드" }

            fun bubblePosition() = stickerRecapRepository.findComments(sticker.id).first { it.id == bubbleComment.id }

            When("말풍선 코멘트 위치를 수정하면") {
                service.updatePositions(
                    board.userId,
                    sticker.id,
                    listOf(RecapCommentPosition(bubbleComment.id, 10.5, -20.5)),
                )

                Then("바뀐 위치가 저장된다") {
                    bubblePosition().posX shouldBe 10.5
                    bubblePosition().posY shouldBe -20.5
                }
            }

            When("정상 말풍선과 키워드 칩을 한 배치로 보내면") {
                val exception =
                    shouldThrow<InvalidInputException> {
                        service.updatePositions(
                            board.userId,
                            sticker.id,
                            listOf(
                                RecapCommentPosition(bubbleComment.id, 99.0, 98.0),
                                RecapCommentPosition(chipComment.id, 1.0, 2.0),
                            ),
                        )
                    }

                Then("STICKER-004 오류로 배치 전체를 거부한다") {
                    exception.errorCode shouldBe StickerErrorCode.UNEDITABLE_RECAP_COMMENT
                }

                Then("함께 보낸 정상 말풍선도 원래 위치 그대로다") {
                    bubblePosition().posX shouldBe 3.0
                    bubblePosition().posY shouldBe 4.0
                }
            }

            When("키워드 칩 코멘트의 위치를 수정하려 하면") {
                val exception =
                    shouldThrow<InvalidInputException> {
                        service.updatePositions(
                            board.userId,
                            sticker.id,
                            listOf(RecapCommentPosition(chipComment.id, 1.0, 2.0)),
                        )
                    }

                Then("STICKER-004 오류로 거부한다") {
                    exception.errorCode shouldBe StickerErrorCode.UNEDITABLE_RECAP_COMMENT
                }
            }

            When("존재하지 않는 코멘트 id로 위치를 수정하려 하면") {
                val exception =
                    shouldThrow<InvalidInputException> {
                        service.updatePositions(
                            board.userId,
                            sticker.id,
                            listOf(RecapCommentPosition(uuidV7(), 1.0, 2.0)),
                        )
                    }

                Then("STICKER-004 오류로 거부한다") {
                    exception.errorCode shouldBe StickerErrorCode.UNEDITABLE_RECAP_COMMENT
                }
            }

            When("중복된 코멘트 id로 위치를 수정하려 하면") {
                val exception =
                    shouldThrow<InvalidInputException> {
                        service.updatePositions(
                            board.userId,
                            sticker.id,
                            listOf(
                                RecapCommentPosition(bubbleComment.id, 1.0, 2.0),
                                RecapCommentPosition(bubbleComment.id, 5.0, 6.0),
                            ),
                        )
                    }

                Then("STICKER-004 오류로 거부한다") {
                    exception.errorCode shouldBe StickerErrorCode.UNEDITABLE_RECAP_COMMENT
                }

                Then("말풍선 위치는 원래 그대로다") {
                    bubblePosition().posX shouldBe 3.0
                }
            }

            When("비유한(NaN) 좌표로 위치를 수정하려 하면") {
                val exception =
                    shouldThrow<InvalidInputException> {
                        service.updatePositions(
                            board.userId,
                            sticker.id,
                            listOf(RecapCommentPosition(bubbleComment.id, Double.NaN, 2.0)),
                        )
                    }

                Then("STICKER-004 오류로 거부한다") {
                    exception.errorCode shouldBe StickerErrorCode.UNEDITABLE_RECAP_COMMENT
                }
            }

            When("다른 사용자가 코멘트 위치를 수정하려 하면") {
                val otherUser = userRepository.saveTestUser()
                val exception =
                    shouldThrow<NotFoundException> {
                        service.updatePositions(
                            otherUser.id,
                            sticker.id,
                            listOf(RecapCommentPosition(bubbleComment.id, 1.0, 2.0)),
                        )
                    }

                Then("STICKER-001 오류로 소유권을 숨긴다") {
                    exception.errorCode shouldBe StickerErrorCode.STICKER_NOT_FOUND
                }

                Then("말풍선 위치는 원래 그대로다") {
                    bubblePosition().posX shouldBe 3.0
                }
            }
        }
    })
