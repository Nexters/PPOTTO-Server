package com.github.nexters.ppotto.sticker.application

import com.github.nexters.ppotto.analysis.domain.PhotoContentType
import com.github.nexters.ppotto.analysis.infrastructure.AnalysisRepository
import com.github.nexters.ppotto.analysis.infrastructure.PhotoCreate
import com.github.nexters.ppotto.analysis.infrastructure.PhotoRepository
import com.github.nexters.ppotto.board.application.BoardAccessService
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.global.error.InvalidInputException
import com.github.nexters.ppotto.global.error.NotFoundException
import com.github.nexters.ppotto.sticker.domain.StickerErrorCode
import com.github.nexters.ppotto.sticker.infrastructure.StickerCommandRepository
import com.github.nexters.ppotto.sticker.infrastructure.StickerRecapRepository
import com.github.nexters.ppotto.sticker.infrastructure.StickerRepository
import com.github.nexters.ppotto.sticker.support.imageStickerResult
import com.github.nexters.ppotto.sticker.support.textStickerResult
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.runConcurrently
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.springframework.dao.DataIntegrityViolationException
import java.time.Instant

class AnalysisResultSaveServiceTest(
    service: AnalysisResultSaveService,
    stickerRepository: StickerRepository,
    stickerCommandRepository: StickerCommandRepository,
    stickerRecapRepository: StickerRecapRepository,
    photoRepository: PhotoRepository,
    analysisRepository: AnalysisRepository,
    boardAccessService: BoardAccessService,
    boardRepository: BoardRepository,
    userRepository: UserRepository,
) : IntegrationTest({
        Given("분석 결과에 스티커와 리캡이 포함된 상태에서") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val analysis = analysisRepository.save(board.userId, board.id)
            val photo =
                photoRepository
                    .saveAll(
                        analysis.id,
                        board.id,
                        listOf(PhotoCreate(PhotoContentType.JPEG, Instant.parse("2026-07-01T00:00:00Z"))),
                    ).single()
            photoRepository.markCompletedBatch(mapOf(photo.id to Instant.now()))
            val command =
                SaveAnalysisResultCommand(
                    userId = board.userId,
                    analysisId = analysis.id,
                    boardId = board.id,
                    stickers =
                        listOf(
                            imageStickerResult(photo.id),
                            textStickerResult(),
                        ),
                )

            When("분석 결과를 저장하면") {
                val result = service.save(command)

                Then("스티커와 자식 데이터를 한 트랜잭션으로 저장한다") {
                    result.stickerIds.size shouldBe 2
                    stickerRepository.findAllByBoardId(board.id).map { it.id } shouldContainExactly result.stickerIds
                    stickerRecapRepository.findPhotoIds(result.stickerIds.first()) shouldContainExactly listOf(photo.id)
                    stickerRecapRepository.findComments(result.stickerIds.first()).map { it.content } shouldContainExactly
                        listOf("말풍선", "키워드 칩")
                }
            }
        }

        Given("분석 결과 스티커가 7개인 상태에서") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val analysis = analysisRepository.save(board.userId, board.id)

            When("저장 명령을 만들면") {
                val exception =
                    shouldThrow<InvalidInputException> {
                        SaveAnalysisResultCommand(
                            board.userId,
                            analysis.id,
                            board.id,
                            List(7) { textStickerResult() },
                        )
                    }

                Then("STICKER-003 오류가 발생한다") {
                    exception.errorCode shouldBe StickerErrorCode.ANALYSIS_STICKER_COUNT_EXCEEDED
                }

                Then("스티커를 하나도 저장하지 않는다") {
                    stickerRepository.findAllByBoardId(board.id).shouldBeEmpty()
                }
            }
        }

        Given("중복 사진 연결이 포함된 분석 결과에서") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val analysis = analysisRepository.save(board.userId, board.id)
            val photo =
                photoRepository
                    .saveAll(
                        analysis.id,
                        board.id,
                        listOf(PhotoCreate(PhotoContentType.JPEG, Instant.parse("2026-07-01T00:00:00Z"))),
                    ).single()
            photoRepository.markCompletedBatch(mapOf(photo.id to Instant.now()))
            val invalidResult = imageStickerResult(photo.id).copy(photoIds = listOf(photo.id, photo.id))

            When("자식 저장 중 DB 제약 위반이 발생하면") {
                Then("스티커 저장도 롤백한다") {
                    shouldThrow<DataIntegrityViolationException> {
                        service.save(
                            SaveAnalysisResultCommand(
                                board.userId,
                                analysis.id,
                                board.id,
                                listOf(invalidResult),
                            ),
                        )
                    }
                    stickerRepository.findAllByBoardId(board.id).shouldBeEmpty()
                }
            }
        }

        Given("동일한 분석 결과 저장 요청이 반복될 때") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val analysis = analysisRepository.save(board.userId, board.id)
            val command =
                SaveAnalysisResultCommand(
                    board.userId,
                    analysis.id,
                    board.id,
                    listOf(textStickerResult(), textStickerResult()),
                )

            When("같은 요청을 두 번 저장하면") {
                val first = service.save(command)
                val second = service.save(command)

                Then("최초 저장 결과를 반환하고 스티커를 추가하지 않는다") {
                    second.stickerIds shouldContainExactly first.stickerIds
                    stickerRepository.findAllByAnalysisId(analysis.id).map { it.id } shouldContainExactly first.stickerIds
                }
            }
        }

        Given("한 분석의 스티커 6개를 모두 소프트 삭제한 상태에서") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val analysis = analysisRepository.save(board.userId, board.id)
            val command =
                SaveAnalysisResultCommand(
                    board.userId,
                    analysis.id,
                    board.id,
                    List(6) { textStickerResult() },
                )
            val first = service.save(command)
            first.stickerIds.forEach { stickerCommandRepository.softDelete(it, Instant.now()) }

            When("같은 분석 결과를 다시 저장하면") {
                val second = service.save(command)

                Then("최초 저장 결과의 스티커 id를 그대로 반환한다") {
                    second.stickerIds shouldContainExactly first.stickerIds
                }

                Then("수명 6개를 소프트 삭제분까지 세어 새로 저장하지 않는다") {
                    stickerRepository.findAllByAnalysisId(analysis.id).map { it.id } shouldContainExactly first.stickerIds
                }
            }
        }

        Given("동일한 분석 결과 저장 요청이 동시에 도착할 때") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val analysis = analysisRepository.save(board.userId, board.id)
            val command =
                SaveAnalysisResultCommand(
                    board.userId,
                    analysis.id,
                    board.id,
                    List(6) { textStickerResult() },
                )

            When("두 트랜잭션이 동시에 저장하면") {
                val results = runConcurrently(2) { service.save(command) }.map { it.getOrThrow() }

                Then("두 요청이 같은 스티커 목록을 반환한다") {
                    results[1].stickerIds shouldContainExactly results[0].stickerIds
                }

                Then("스티커를 추가로 저장하지 않는다") {
                    stickerRepository.findAllByAnalysisId(analysis.id).size shouldBe 6
                }
            }
        }

        Given("다른 사용자의 분석과 사진이 존재하는 상태에서") {
            val ownerBoard = boardRepository.save(userRepository.saveTestUser().id)
            val ownerAnalysis = analysisRepository.save(ownerBoard.userId, ownerBoard.id)
            val ownerPhoto =
                photoRepository
                    .saveAll(
                        ownerAnalysis.id,
                        ownerBoard.id,
                        listOf(PhotoCreate(PhotoContentType.JPEG, Instant.parse("2026-07-01T00:00:00Z"))),
                    ).single()
            val otherBoard = boardRepository.save(userRepository.saveTestUser().id)
            val otherAnalysis = analysisRepository.save(otherBoard.userId, otherBoard.id)
            val otherPhoto =
                photoRepository
                    .saveAll(
                        otherAnalysis.id,
                        otherBoard.id,
                        listOf(PhotoCreate(PhotoContentType.JPEG, Instant.parse("2026-07-02T00:00:00Z"))),
                    ).single()
            photoRepository.markCompletedBatch(
                mapOf(ownerPhoto.id to Instant.now(), otherPhoto.id to Instant.now()),
            )

            When("소유한 보드에 다른 사용자의 analysisId로 저장하면") {
                Then("저장 전에 거부한다") {
                    shouldThrow<NotFoundException> {
                        service.save(
                            SaveAnalysisResultCommand(
                                ownerBoard.userId,
                                otherAnalysis.id,
                                ownerBoard.id,
                                listOf(textStickerResult()),
                            ),
                        )
                    }
                    stickerRepository.findAllByBoardId(ownerBoard.id).shouldBeEmpty()
                }
            }

            When("다른 사용자의 사진을 sourcePhotoId로 저장하면") {
                val result = imageStickerResult(ownerPhoto.id).copy(sourcePhotoId = otherPhoto.id)

                Then("저장 전에 거부한다") {
                    shouldThrow<NotFoundException> {
                        service.save(
                            SaveAnalysisResultCommand(
                                ownerBoard.userId,
                                ownerAnalysis.id,
                                ownerBoard.id,
                                listOf(result),
                            ),
                        )
                    }
                    stickerRepository.findAllByBoardId(ownerBoard.id).shouldBeEmpty()
                }
            }

            When("남의 보드에 자기 userId로 저장하면") {
                val exception =
                    shouldThrow<NotFoundException> {
                        service.save(
                            SaveAnalysisResultCommand(
                                otherBoard.userId,
                                ownerAnalysis.id,
                                ownerBoard.id,
                                listOf(textStickerResult()),
                            ),
                        )
                    }

                Then("STICKER-001 오류로 보드 소유권을 숨긴다") {
                    exception.errorCode shouldBe StickerErrorCode.STICKER_NOT_FOUND
                }

                Then("스티커를 하나도 저장하지 않는다") {
                    stickerRepository.findAllByBoardId(ownerBoard.id).shouldBeEmpty()
                }
            }

            When("다른 사용자의 사진을 리캡 photoIds로 저장하면") {
                val result = imageStickerResult(ownerPhoto.id).copy(photoIds = listOf(otherPhoto.id))

                Then("저장 전에 거부한다") {
                    shouldThrow<NotFoundException> {
                        service.save(
                            SaveAnalysisResultCommand(
                                ownerBoard.userId,
                                ownerAnalysis.id,
                                ownerBoard.id,
                                listOf(result),
                            ),
                        )
                    }
                    stickerRepository.findAllByBoardId(ownerBoard.id).shouldBeEmpty()
                }
            }
        }

        Given("분석과 사진 소유권 port가 없는 상태에서") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val analysis = analysisRepository.save(board.userId, board.id)
            val serviceWithoutPort =
                AnalysisResultSaveService(
                    stickerRepository,
                    stickerRecapRepository,
                    boardAccessService,
                    emptyList(),
                )

            When("분석 결과 저장을 요청하면") {
                Then("저장을 시작하지 않고 실패한다") {
                    shouldThrow<IllegalStateException> {
                        serviceWithoutPort.save(
                            SaveAnalysisResultCommand(
                                board.userId,
                                analysis.id,
                                board.id,
                                listOf(textStickerResult()),
                            ),
                        )
                    }
                    stickerRepository.findAllByBoardId(board.id).shouldBeEmpty()
                }
            }
        }
    })
