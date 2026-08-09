package com.github.nexters.ppotto.sticker.application

import com.github.nexters.ppotto.analysis.domain.PhotoContentType
import com.github.nexters.ppotto.analysis.infrastructure.AnalysisRepository
import com.github.nexters.ppotto.analysis.infrastructure.PhotoCreate
import com.github.nexters.ppotto.analysis.infrastructure.PhotoRepository
import com.github.nexters.ppotto.analysis.support.AnalysisTestConfig
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.board.infrastructure.DrawingRepository
import com.github.nexters.ppotto.board.support.newDrawing
import com.github.nexters.ppotto.board.support.uuidV7
import com.github.nexters.ppotto.global.error.ConflictException
import com.github.nexters.ppotto.global.error.InvalidInputException
import com.github.nexters.ppotto.global.error.NotFoundException
import com.github.nexters.ppotto.global.identifier.AnalysisId
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.DrawingId
import com.github.nexters.ppotto.global.identifier.PhotoId
import com.github.nexters.ppotto.global.identifier.StickerId
import com.github.nexters.ppotto.sticker.application.port.StickerRegenerationPort
import com.github.nexters.ppotto.sticker.application.port.StickerRegenerationResult
import com.github.nexters.ppotto.sticker.infrastructure.StickerCommandRepository
import com.github.nexters.ppotto.sticker.infrastructure.StickerRecapRepository
import com.github.nexters.ppotto.sticker.infrastructure.StickerRepository
import com.github.nexters.ppotto.sticker.support.imageStickerCreation
import com.github.nexters.ppotto.sticker.support.textStickerCreation
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.RecordingObjectStorageCleaner
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Import
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant

@Import(AnalysisTestConfig::class)
class StickerCommandServiceTest(
    service: StickerCommandService,
    stickerRepository: StickerRepository,
    stickerCommandRepository: StickerCommandRepository,
    stickerRecapRepository: StickerRecapRepository,
    stickerAccessService: StickerAccessService,
    analysisRepository: AnalysisRepository,
    boardRepository: BoardRepository,
    drawingRepository: DrawingRepository,
    userRepository: UserRepository,
    photoRepository: PhotoRepository,
    transactionTemplate: TransactionTemplate,
    eventPublisher: ApplicationEventPublisher,
    objectStorageCleaner: RecordingObjectStorageCleaner,
) : IntegrationTest({
        Given("사용자 보드에 스티커가 등록된 상태에서") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val analysis = analysisRepository.save(board.userId.value, board.id.value)
            val sticker = stickerRepository.save(AnalysisId(analysis.id), board.id, textStickerCreation())

            When("제목을 변경하고 열람 처리하면") {
                val renamed = service.rename(board.userId, sticker.id, "새 제목")
                service.markViewed(board.userId, sticker.id)
                service.markViewed(board.userId, sticker.id)

                Then("제목과 열람 상태가 저장된다") {
                    renamed.title shouldBe "새 제목"
                    val found = stickerRepository.findById(sticker.id)
                    found?.title shouldBe "새 제목"
                    found?.viewedAt.shouldNotBeNull()
                }
            }

            When("보드 배치를 변경하면") {
                service.updateLayouts(
                    board.id,
                    listOf(
                        StickerLayoutCommand(
                            id = sticker.id,
                            title = "배치 제목",
                            posX = 11.0,
                            posY = 12.0,
                            scale = 0.7,
                            rotation = 3.0,
                            zIndex = 5,
                            badgeOffsetX = 4.0,
                            badgeOffsetY = 6.0,
                            badgeRotation = 8.0,
                        ),
                    ),
                )

                Then("도메인 규칙을 거쳐 모든 배치값이 저장된다") {
                    val found = stickerRepository.findById(sticker.id)
                    found?.title shouldBe "배치 제목"
                    found?.posX shouldBe 11.0
                    found?.zIndex shouldBe 5
                    found?.badgeRotation shouldBe 8.0
                }
            }

            When("다른 사용자가 제목을 변경하면") {
                val otherUser = userRepository.saveTestUser()

                Then("스티커 없음 예외로 소유권을 숨긴다") {
                    shouldThrow<NotFoundException> {
                        service.rename(otherUser.id, sticker.id, "침범")
                    }
                }
            }

            When("스티커를 삭제하면") {
                val stickerDrawingId = DrawingId(uuidV7())
                val boardDrawingId = DrawingId(uuidV7())
                drawingRepository.upsertAll(
                    listOf(
                        newDrawing(boardId = board.id, stickerId = sticker.id, id = stickerDrawingId),
                        newDrawing(boardId = board.id, id = boardDrawingId),
                    ),
                )
                service.delete(board.userId, sticker.id)

                Then("활성 스티커에서 제외하고 연결 드로잉을 삭제한다") {
                    stickerRepository.findById(sticker.id).shouldBeNull()
                    drawingRepository.findByBoardId(board.id).map { it.id } shouldBe listOf(boardDrawingId)
                }
            }
        }

        Given("이미지 스티커와 사진 구성이 있는 상태에서") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val analysis = analysisRepository.save(board.userId.value, board.id.value)
            val photos =
                photoRepository.saveAll(
                    analysis.id,
                    board.id.value,
                    listOf(
                        PhotoCreate(PhotoContentType.JPEG, Instant.parse("2026-07-01T00:00:00Z")),
                        PhotoCreate(PhotoContentType.JPEG, Instant.parse("2026-07-02T00:00:00Z")),
                    ),
                )
            photoRepository.markCompletedBatch(photos.associate { it.id to Instant.now() })
            val photoIds = photos.map { PhotoId(it.id) }
            val sticker =
                stickerRepository.save(
                    AnalysisId(analysis.id),
                    board.id,
                    imageStickerCreation(sourcePhotoId = photoIds.first()),
                )
            stickerRecapRepository.savePhotos(sticker.id, photoIds)

            When("재생성을 요청하면") {
                objectStorageCleaner.clear()
                service.regenerate(board.userId, sticker.id)

                Then("스티커 이미지만 바뀌고 리캡 문구와 사진 구성은 그대로다") {
                    val found = requireNotNull(stickerRepository.findById(sticker.id))
                    found.title shouldBe "원래 제목"
                    found.summary shouldBe "한 줄 요약"
                    found.imageKey shouldNotBe "stickers/original.png"
                    eventually {
                        objectStorageCleaner.deletedObjectKeys shouldContain "stickers/original.png"
                    }
                    stickerRecapRepository.findPhotoIds(sticker.id).toSet() shouldBe photoIds.toSet()
                }
            }

            When("새 이미지 업로드 후 DB 반영 전에 스티커가 삭제되면") {
                val conflictedSticker =
                    stickerRepository.save(
                        AnalysisId(analysis.id),
                        board.id,
                        imageStickerCreation(sourcePhotoId = photoIds.first()),
                    )
                stickerRecapRepository.savePhotos(conflictedSticker.id, photoIds)
                val uploadedImageKey = "stickers/regenerated-after-delete.png"
                val serviceWithDeletingRegenerationPort =
                    StickerCommandService(
                        stickerRepository,
                        stickerCommandRepository,
                        stickerRecapRepository,
                        stickerAccessService,
                        emptyList(),
                        listOf(
                            object : StickerRegenerationPort {
                                override fun regenerate(
                                    analysisId: AnalysisId,
                                    boardId: BoardId,
                                    stickerId: StickerId,
                                    photoIds: Collection<PhotoId>,
                                    previousSourcePhotoId: PhotoId,
                                ): StickerRegenerationResult {
                                    stickerCommandRepository.softDelete(stickerId, Instant.now())
                                    return StickerRegenerationResult(
                                        sourcePhotoId = previousSourcePhotoId,
                                        imageKey = uploadedImageKey,
                                        mainColor = "#FF6B6B",
                                    )
                                }
                            },
                        ),
                        transactionTemplate,
                        eventPublisher,
                    )
                objectStorageCleaner.clear()

                Then("새 이미지 삭제만 요청하고 기존 이미지는 유지한다") {
                    shouldThrow<NotFoundException> {
                        serviceWithDeletingRegenerationPort.regenerate(board.userId, conflictedSticker.id)
                    }
                    eventually {
                        objectStorageCleaner.deletedObjectKeys shouldContain uploadedImageKey
                    }
                    objectStorageCleaner.deletedObjectKeys shouldNotContain "stickers/original.png"
                }
            }

            When("이미 재생성이 진행 중인 스티커에 재생성을 요청하면") {
                val lockedSticker =
                    stickerRepository.save(
                        AnalysisId(analysis.id),
                        board.id,
                        imageStickerCreation(sourcePhotoId = photoIds.first()),
                    )
                stickerRecapRepository.savePhotos(lockedSticker.id, photoIds)
                val now = Instant.now()
                stickerCommandRepository.tryClaimRegenerationLock(lockedSticker.id, now, now.plusSeconds(300))
                var portCalled = false
                val serviceWithRecordingPort =
                    StickerCommandService(
                        stickerRepository,
                        stickerCommandRepository,
                        stickerRecapRepository,
                        stickerAccessService,
                        emptyList(),
                        listOf(
                            object : StickerRegenerationPort {
                                override fun regenerate(
                                    analysisId: AnalysisId,
                                    boardId: BoardId,
                                    stickerId: StickerId,
                                    photoIds: Collection<PhotoId>,
                                    previousSourcePhotoId: PhotoId,
                                ): StickerRegenerationResult {
                                    portCalled = true
                                    return StickerRegenerationResult(previousSourcePhotoId, "stickers/should-not-happen.png", "#FF6B6B")
                                }
                            },
                        ),
                        transactionTemplate,
                        eventPublisher,
                    )

                Then("409로 거부하고 외부 재생성 호출은 하지 않는다") {
                    shouldThrow<ConflictException> {
                        serviceWithRecordingPort.regenerate(board.userId, lockedSticker.id)
                    }
                    portCalled shouldBe false
                }
            }

            When("재생성 성공 직후 다시 재생성을 요청하면") {
                Then("쿨다운 없이 즉시 성공한다") {
                    service.regenerate(board.userId, sticker.id)
                    shouldNotThrowAny {
                        service.regenerate(board.userId, sticker.id)
                    }
                }
            }

            When("재생성 중 예외가 발생한 후 다시 재생성을 요청하면") {
                val failingSticker =
                    stickerRepository.save(
                        AnalysisId(analysis.id),
                        board.id,
                        imageStickerCreation(sourcePhotoId = photoIds.first()),
                    )
                stickerRecapRepository.savePhotos(failingSticker.id, photoIds)
                var callCount = 0
                val serviceWithFlakyPort =
                    StickerCommandService(
                        stickerRepository,
                        stickerCommandRepository,
                        stickerRecapRepository,
                        stickerAccessService,
                        emptyList(),
                        listOf(
                            object : StickerRegenerationPort {
                                override fun regenerate(
                                    analysisId: AnalysisId,
                                    boardId: BoardId,
                                    stickerId: StickerId,
                                    photoIds: Collection<PhotoId>,
                                    previousSourcePhotoId: PhotoId,
                                ): StickerRegenerationResult {
                                    callCount += 1
                                    if (callCount == 1) error("일시적인 재생성 실패")
                                    return StickerRegenerationResult(previousSourcePhotoId, "stickers/retry-success.png", "#FF6B6B")
                                }
                            },
                        ),
                        transactionTemplate,
                        eventPublisher,
                    )

                Then("finally에서 락이 풀려 즉시 재시도가 성공한다") {
                    shouldThrow<IllegalStateException> {
                        serviceWithFlakyPort.regenerate(board.userId, failingSticker.id)
                    }
                    shouldNotThrowAny {
                        serviceWithFlakyPort.regenerate(board.userId, failingSticker.id)
                    }
                }
            }

            When("만료된(stale) 재생성 락이 남아있는 상태에서 재요청하면") {
                val staleLockedSticker =
                    stickerRepository.save(
                        AnalysisId(analysis.id),
                        board.id,
                        imageStickerCreation(sourcePhotoId = photoIds.first()),
                    )
                stickerRecapRepository.savePhotos(staleLockedSticker.id, photoIds)
                val past = Instant.now().minusSeconds(600)
                stickerCommandRepository.tryClaimRegenerationLock(staleLockedSticker.id, past, past)

                Then("재선점해 재생성에 성공한다") {
                    shouldNotThrowAny {
                        service.regenerate(board.userId, staleLockedSticker.id)
                    }
                }
            }

            When("텍스트형 스티커를 재생성 요청하면") {
                val textSticker =
                    stickerRepository.save(AnalysisId(analysis.id), board.id, textStickerCreation())

                Then("이미지형이 아니라서 거부한다") {
                    shouldThrow<InvalidInputException> {
                        service.regenerate(board.userId, textSticker.id)
                    }
                }
            }

            When("사진 구성이 없는 스티커를 재생성 요청하면") {
                val orphanSticker =
                    stickerRepository.save(
                        AnalysisId(analysis.id),
                        board.id,
                        imageStickerCreation(sourcePhotoId = photoIds.first()),
                    )

                Then("재생성할 사진이 없어서 거부한다") {
                    shouldThrow<InvalidInputException> {
                        service.regenerate(board.userId, orphanSticker.id)
                    }
                }
            }
        }

        Given("이미지 스티커의 사진 구성 중 일부만 업로드 완료된 상태에서") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val analysis = analysisRepository.save(board.userId.value, board.id.value)
            val photos =
                photoRepository.saveAll(
                    analysis.id,
                    board.id.value,
                    listOf(
                        PhotoCreate(PhotoContentType.JPEG, Instant.parse("2026-07-03T00:00:00Z")),
                        PhotoCreate(PhotoContentType.JPEG, Instant.parse("2026-07-04T00:00:00Z")),
                    ),
                )
            photoRepository.markCompletedBatch(mapOf(photos.first().id to Instant.now()))
            val photoIds = photos.map { PhotoId(it.id) }
            val sticker =
                stickerRepository.save(
                    AnalysisId(analysis.id),
                    board.id,
                    imageStickerCreation(sourcePhotoId = photoIds.first()),
                )
            stickerRecapRepository.savePhotos(sticker.id, photoIds)

            When("재생성을 요청하면") {
                service.regenerate(board.userId, sticker.id)

                Then("조회 가능한 사진으로 재생성하고 기존 사진 구성은 유지한다") {
                    val found = requireNotNull(stickerRepository.findById(sticker.id))
                    found.imageKey shouldNotBe "stickers/original.png"
                    found.sourcePhotoId shouldBe photoIds.first()
                    stickerRecapRepository.findPhotoIds(sticker.id).toSet() shouldBe photoIds.toSet()
                }
            }
        }

        Given("이미지 스티커의 사진 구성이 모두 업로드 완료되지 않은 상태에서") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val analysis = analysisRepository.save(board.userId.value, board.id.value)
            val photos =
                photoRepository.saveAll(
                    analysis.id,
                    board.id.value,
                    listOf(
                        PhotoCreate(PhotoContentType.JPEG, Instant.parse("2026-07-05T00:00:00Z")),
                        PhotoCreate(PhotoContentType.JPEG, Instant.parse("2026-07-06T00:00:00Z")),
                    ),
                )
            val photoIds = photos.map { PhotoId(it.id) }
            val sticker =
                stickerRepository.save(
                    AnalysisId(analysis.id),
                    board.id,
                    imageStickerCreation(sourcePhotoId = photoIds.first()),
                )
            stickerRecapRepository.savePhotos(sticker.id, photoIds)

            When("재생성을 요청하면") {
                Then("재생성할 수 있는 사진이 없어서 거부한다") {
                    shouldThrow<InvalidInputException> {
                        service.regenerate(board.userId, sticker.id)
                    }
                }
            }
        }

        Given("드로잉 삭제 port가 없는 상태에서") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val analysis = analysisRepository.save(board.userId.value, board.id.value)
            val sticker = stickerRepository.save(AnalysisId(analysis.id), board.id, textStickerCreation())
            val serviceWithoutPort =
                StickerCommandService(
                    stickerRepository,
                    stickerCommandRepository,
                    stickerRecapRepository,
                    stickerAccessService,
                    emptyList(),
                    emptyList(),
                    transactionTemplate,
                    eventPublisher,
                )

            When("스티커 삭제를 요청하면") {
                Then("삭제를 시작하지 않고 실패한다") {
                    shouldThrow<IllegalStateException> {
                        serviceWithoutPort.delete(board.userId, sticker.id)
                    }
                    stickerRepository.findById(sticker.id).shouldNotBeNull()
                }
            }
        }
    })

private fun eventually(assertion: () -> Unit) {
    val deadline = System.nanoTime() + 5_000_000_000

    while (true) {
        try {
            assertion()
            return
        } catch (e: AssertionError) {
            if (System.nanoTime() >= deadline) throw e
            Thread.sleep(50)
        } catch (e: NoSuchElementException) {
            if (System.nanoTime() >= deadline) throw e
            Thread.sleep(50)
        }
    }
}
