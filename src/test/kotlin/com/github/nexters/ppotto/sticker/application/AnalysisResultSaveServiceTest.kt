package com.github.nexters.ppotto.sticker.application

import com.github.nexters.ppotto.analysis.domain.PhotoContentType
import com.github.nexters.ppotto.analysis.infrastructure.AnalysisRepository
import com.github.nexters.ppotto.analysis.infrastructure.PhotoCreate
import com.github.nexters.ppotto.analysis.infrastructure.PhotoRepository
import com.github.nexters.ppotto.board.application.BoardAccessService
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.global.error.InvalidInputException
import com.github.nexters.ppotto.global.error.NotFoundException
import com.github.nexters.ppotto.sticker.domain.RecapCommentCreation
import com.github.nexters.ppotto.sticker.domain.StickerLayout
import com.github.nexters.ppotto.sticker.domain.StickerType
import com.github.nexters.ppotto.sticker.infrastructure.StickerRecapRepository
import com.github.nexters.ppotto.sticker.infrastructure.StickerRepository
import com.github.nexters.ppotto.sticker.support.FakeAnalysisPhotoOwnershipPort
import com.github.nexters.ppotto.sticker.support.StickerTestConfig
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

@Import(StickerTestConfig::class)
class AnalysisResultSaveServiceTest(
    service: AnalysisResultSaveService,
    stickerRepository: StickerRepository,
    stickerRecapRepository: StickerRecapRepository,
    photoRepository: PhotoRepository,
    analysisRepository: AnalysisRepository,
    boardAccessService: BoardAccessService,
    boardRepository: BoardRepository,
    userRepository: UserRepository,
    fakeOwnershipPort: FakeAnalysisPhotoOwnershipPort,
) : IntegrationTest({
        Given("분석 결과에 스티커와 리캡이 포함된 상태에서") {
            val board = boardRepository.save(userRepository.save().id)
            val analysis = analysisRepository.save(board.userId, board.id)
            val photo =
                photoRepository
                    .saveAll(
                        analysis.id,
                        board.id,
                        listOf(PhotoCreate(PhotoContentType.JPEG, Instant.parse("2026-07-01T00:00:00Z"))),
                    ).single()
            fakeOwnershipPort.allow(board.userId, board.id, analysis.id, listOf(photo.id))
            val command =
                SaveAnalysisResultCommand(
                    userId = board.userId,
                    analysisId = analysis.id,
                    boardId = board.id,
                    stickers =
                        listOf(
                            imageResult(photo.id),
                            textResult(),
                        ),
                )

            When("분석 결과를 저장하면") {
                val result = service.save(command)

                Then("스티커와 자식 데이터를 한 트랜잭션으로 저장한다") {
                    result.stickerIds.size shouldBe 2
                    stickerRepository.findAllByBoardId(board.id).map { it.id } shouldContainExactly result.stickerIds
                    stickerRecapRepository.findPhotoIds(result.stickerIds.first()) shouldContainExactly listOf(photo.id)
                    stickerRecapRepository.findComments(result.stickerIds.first()).map { it.content } shouldContainExactly
                        listOf("리캡 코멘트")
                }
            }
        }

        Given("분석 결과 스티커가 7개인 상태에서") {
            val board = boardRepository.save(userRepository.save().id)
            val analysis = analysisRepository.save(board.userId, board.id)
            val command =
                SaveAnalysisResultCommand(
                    board.userId,
                    analysis.id,
                    board.id,
                    List(7) { textResult() },
                )

            When("분석 결과를 저장하면") {
                Then("잘못된 입력 예외를 던지고 아무것도 저장하지 않는다") {
                    shouldThrow<InvalidInputException> {
                        service.save(command)
                    }
                    stickerRepository.findAllByBoardId(board.id).shouldBeEmpty()
                }
            }
        }

        Given("중복 사진 연결이 포함된 분석 결과에서") {
            val board = boardRepository.save(userRepository.save().id)
            val analysis = analysisRepository.save(board.userId, board.id)
            val photo =
                photoRepository
                    .saveAll(
                        analysis.id,
                        board.id,
                        listOf(PhotoCreate(PhotoContentType.JPEG, Instant.parse("2026-07-01T00:00:00Z"))),
                    ).single()
            val invalidResult = imageResult(photo.id).copy(photoIds = listOf(photo.id, photo.id))
            fakeOwnershipPort.allow(board.userId, board.id, analysis.id, listOf(photo.id))

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
            val board = boardRepository.save(userRepository.save().id)
            val analysis = analysisRepository.save(board.userId, board.id)
            fakeOwnershipPort.allow(board.userId, board.id, analysis.id, emptyList())
            val command =
                SaveAnalysisResultCommand(
                    board.userId,
                    analysis.id,
                    board.id,
                    listOf(textResult(), textResult()),
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

        Given("동일한 분석 결과 저장 요청이 동시에 도착할 때") {
            val board = boardRepository.save(userRepository.save().id)
            val analysis = analysisRepository.save(board.userId, board.id)
            fakeOwnershipPort.allow(board.userId, board.id, analysis.id, emptyList())
            val command =
                SaveAnalysisResultCommand(
                    board.userId,
                    analysis.id,
                    board.id,
                    List(6) { textResult() },
                )

            When("두 트랜잭션이 동시에 저장하면") {
                val ready = CountDownLatch(2)
                val start = CountDownLatch(1)
                val executor = Executors.newFixedThreadPool(2)
                val futures =
                    List(2) {
                        executor.submit(
                            Callable {
                                ready.countDown()
                                start.await()
                                service.save(command)
                            },
                        )
                    }
                ready.await()
                start.countDown()
                val results = futures.map { it.get() }
                executor.shutdownNow()

                Then("두 요청이 같은 6개를 반환하고 추가 저장하지 않는다") {
                    results[1].stickerIds shouldContainExactly results[0].stickerIds
                    stickerRepository.findAllByAnalysisId(analysis.id).size shouldBe 6
                }
            }
        }

        Given("다른 사용자의 분석과 사진이 존재하는 상태에서") {
            val ownerBoard = boardRepository.save(userRepository.save().id)
            val ownerAnalysis = analysisRepository.save(ownerBoard.userId, ownerBoard.id)
            val ownerPhoto =
                photoRepository
                    .saveAll(
                        ownerAnalysis.id,
                        ownerBoard.id,
                        listOf(PhotoCreate(PhotoContentType.JPEG, Instant.parse("2026-07-01T00:00:00Z"))),
                    ).single()
            val otherBoard = boardRepository.save(userRepository.save().id)
            val otherAnalysis = analysisRepository.save(otherBoard.userId, otherBoard.id)
            val otherPhoto =
                photoRepository
                    .saveAll(
                        otherAnalysis.id,
                        otherBoard.id,
                        listOf(PhotoCreate(PhotoContentType.JPEG, Instant.parse("2026-07-02T00:00:00Z"))),
                    ).single()
            fakeOwnershipPort.allow(ownerBoard.userId, ownerBoard.id, ownerAnalysis.id, listOf(ownerPhoto.id))
            fakeOwnershipPort.allow(otherBoard.userId, otherBoard.id, otherAnalysis.id, listOf(otherPhoto.id))

            When("소유한 보드에 다른 사용자의 analysisId로 저장하면") {
                Then("저장 전에 거부한다") {
                    shouldThrow<NotFoundException> {
                        service.save(
                            SaveAnalysisResultCommand(
                                ownerBoard.userId,
                                otherAnalysis.id,
                                ownerBoard.id,
                                listOf(textResult()),
                            ),
                        )
                    }
                    stickerRepository.findAllByBoardId(ownerBoard.id).shouldBeEmpty()
                }
            }

            When("다른 사용자의 사진을 sourcePhotoId로 저장하면") {
                val result = imageResult(ownerPhoto.id).copy(sourcePhotoId = otherPhoto.id)

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

            When("다른 사용자의 사진을 리캡 photoIds로 저장하면") {
                val result = imageResult(ownerPhoto.id).copy(photoIds = listOf(otherPhoto.id))

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
            val board = boardRepository.save(userRepository.save().id)
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
                                listOf(textResult()),
                            ),
                        )
                    }
                    stickerRepository.findAllByBoardId(board.id).shouldBeEmpty()
                }
            }
        }
    })

private fun imageResult(photoId: UUID) =
    AnalysisStickerResult(
        type = StickerType.IMAGE,
        title = "이미지 스티커",
        sourcePhotoId = photoId,
        imageKey = "stickers/image.png",
        textContent = null,
        layout = analysisLayout(),
        photoIds = listOf(photoId),
        comments = listOf(RecapCommentCreation("리캡 코멘트", false, null, null)),
    )

private fun textResult() =
    AnalysisStickerResult(
        type = StickerType.TEXT,
        title = "텍스트 스티커",
        sourcePhotoId = null,
        imageKey = null,
        textContent = "텍스트",
        layout = analysisLayout(),
        photoIds = emptyList(),
        comments = emptyList(),
    )

private fun analysisLayout() =
    StickerLayout(
        posX = 1.0,
        posY = 2.0,
        scale = 1.0,
        rotation = 0.0,
        zIndex = 0,
        badgeOffsetX = 0.0,
        badgeOffsetY = 0.0,
        badgeRotation = 0.0,
    )
