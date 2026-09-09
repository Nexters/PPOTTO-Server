package com.github.nexters.ppotto.sticker.application

import com.github.nexters.ppotto.analysis.domain.PhotoContentType
import com.github.nexters.ppotto.analysis.infrastructure.AnalysisRepository
import com.github.nexters.ppotto.analysis.infrastructure.PhotoCreate
import com.github.nexters.ppotto.analysis.infrastructure.PhotoRepository
import com.github.nexters.ppotto.analysis.support.FakeStickerStorage
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.board.infrastructure.DrawingRepository
import com.github.nexters.ppotto.board.support.newDrawing
import com.github.nexters.ppotto.board.support.uuidV7
import com.github.nexters.ppotto.global.error.ConflictException
import com.github.nexters.ppotto.global.error.InvalidInputException
import com.github.nexters.ppotto.global.error.NotFoundException
import com.github.nexters.ppotto.global.identifier.DrawingId
import com.github.nexters.ppotto.global.identifier.StickerId
import com.github.nexters.ppotto.jooq.tables.references.STICKERS
import com.github.nexters.ppotto.sticker.application.port.StickerDrawingCommandPort
import com.github.nexters.ppotto.sticker.application.port.StickerRegenerationPort
import com.github.nexters.ppotto.sticker.application.port.StickerRegenerationResult
import com.github.nexters.ppotto.sticker.domain.StickerErrorCode
import com.github.nexters.ppotto.sticker.domain.StickerLayout
import com.github.nexters.ppotto.sticker.infrastructure.StickerCommandRepository
import com.github.nexters.ppotto.sticker.infrastructure.StickerRecapRepository
import com.github.nexters.ppotto.sticker.infrastructure.StickerRepository
import com.github.nexters.ppotto.sticker.support.imageStickerCreation
import com.github.nexters.ppotto.sticker.support.textStickerCreation
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.RecordingObjectStorageCleaner
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.jooq.DSLContext
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant

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
    stickerStorage: FakeStickerStorage,
    dslContext: DSLContext,
) : IntegrationTest({
        fun imageKeyOf(stickerId: StickerId) = stickerRepository.findById(stickerId)?.imageKey

        fun regenerationLockOf(stickerId: StickerId) =
            dslContext
                .select(STICKERS.REGENERATION_LOCKED_UNTIL)
                .from(STICKERS)
                .where(STICKERS.ID.eq(stickerId))
                .fetchOne(STICKERS.REGENERATION_LOCKED_UNTIL)

        fun serviceWith(
            drawingCommandPorts: List<StickerDrawingCommandPort> = emptyList(),
            regenerationPorts: List<StickerRegenerationPort> = emptyList(),
        ) = StickerCommandService(
            stickerRepository,
            stickerCommandRepository,
            stickerRecapRepository,
            stickerAccessService,
            drawingCommandPorts,
            regenerationPorts,
            transactionTemplate,
            eventPublisher,
        )

        Given("사용자 보드에 스티커가 등록된 상태에서") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val analysis = analysisRepository.save(board.userId, board.id)
            val sticker = stickerRepository.save(analysis.id, board.id, textStickerCreation())

            When("제목을 변경하면") {
                val renamed = service.rename(board.userId, sticker.id, "새 제목")

                Then("변경한 제목을 반환한다") {
                    renamed.title shouldBe "새 제목"
                }

                Then("변경한 제목이 저장된다") {
                    stickerRepository.findById(sticker.id)?.title shouldBe "새 제목"
                }
            }

            When("두 번 열람 처리하면") {
                service.markViewed(board.userId, sticker.id)
                val firstViewedAt = checkNotNull(stickerRepository.findById(sticker.id)?.viewedAt)
                service.markViewed(board.userId, sticker.id)

                Then("최초 열람 시각을 덮어쓰지 않는다") {
                    stickerRepository.findById(sticker.id)?.viewedAt shouldBe firstViewedAt
                }
            }

            When("보드 배치를 변경하면") {
                service.updateLayouts(
                    board.id,
                    listOf(
                        StickerLayoutCommand(
                            id = sticker.id,
                            layout =
                                StickerLayout(
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

                Then("STICKER-001 오류로 소유권을 숨긴다") {
                    val exception =
                        shouldThrow<NotFoundException> {
                            service.rename(otherUser.id, sticker.id, "침범")
                        }
                    exception.errorCode shouldBe StickerErrorCode.STICKER_NOT_FOUND
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

                Then("활성 스티커에서 제외한다") {
                    stickerRepository.findById(sticker.id).shouldBeNull()
                }

                Then("스티커에 붙은 드로잉만 함께 삭제한다") {
                    drawingRepository.findByBoardId(board.id).map { it.id } shouldBe listOf(boardDrawingId)
                }
            }
        }

        Given("이미지 스티커와 사진 구성이 있는 상태에서") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val analysis = analysisRepository.save(board.userId, board.id)
            val photos =
                photoRepository.saveAll(
                    analysis.id,
                    board.id,
                    listOf(
                        PhotoCreate(PhotoContentType.JPEG, Instant.parse("2026-07-01T00:00:00Z")),
                        PhotoCreate(PhotoContentType.JPEG, Instant.parse("2026-07-02T00:00:00Z")),
                    ),
                )
            photoRepository.markCompletedBatch(photos.associate { it.id to Instant.now() })
            val photoIds = photos.map { it.id }
            val sticker =
                stickerRepository.save(
                    analysis.id,
                    board.id,
                    imageStickerCreation(sourcePhotoId = photoIds.first()),
                )
            stickerRecapRepository.savePhotos(sticker.id, photoIds)

            When("재생성을 요청하면") {
                service.regenerate(board.userId, sticker.id)

                Then("스티커 이미지만 바뀌고 리캡 문구는 그대로다") {
                    val found = requireNotNull(stickerRepository.findById(sticker.id))
                    found.title shouldBe "원래 제목"
                    found.summary shouldBe "한 줄 요약"
                    found.imageKey shouldNotBe "stickers/original.png"
                }

                Then("이전 이미지 오브젝트를 삭제한다") {
                    objectStorageCleaner.deletedObjectKeys shouldContain "stickers/original.png"
                }

                Then("사진 구성은 유지한다") {
                    stickerRecapRepository.findPhotoIds(sticker.id).toSet() shouldBe photoIds.toSet()
                }
            }

            When("새 이미지 업로드 후 DB 반영 전에 스티커가 삭제되면") {
                val conflictedSticker =
                    stickerRepository.save(
                        analysis.id,
                        board.id,
                        imageStickerCreation(sourcePhotoId = photoIds.first()),
                    )
                stickerRecapRepository.savePhotos(conflictedSticker.id, photoIds)
                val uploadedImageKey = "stickers/regenerated-after-delete.png"
                val deletingService =
                    serviceWith(
                        regenerationPorts =
                            listOf(
                                StickerRegenerationPort { _, _, stickerId, _, previousSourcePhotoId ->
                                    stickerCommandRepository.softDelete(stickerId, Instant.now())
                                    StickerRegenerationResult(previousSourcePhotoId, uploadedImageKey, "#FF6B6B")
                                },
                            ),
                    )
                val exception =
                    shouldThrow<NotFoundException> {
                        deletingService.regenerate(board.userId, conflictedSticker.id)
                    }

                Then("STICKER-001 오류를 반환한다") {
                    exception.errorCode shouldBe StickerErrorCode.STICKER_NOT_FOUND
                }

                Then("새로 올린 이미지만 삭제 요청한다") {
                    objectStorageCleaner.deletedObjectKeys shouldContain uploadedImageKey
                }

                Then("기존 이미지는 그대로 둔다") {
                    objectStorageCleaner.deletedObjectKeys shouldNotContain "stickers/original.png"
                }
            }

            When("이미 재생성이 진행 중인 스티커에 재생성을 요청하면") {
                val lockedSticker =
                    stickerRepository.save(
                        analysis.id,
                        board.id,
                        imageStickerCreation(sourcePhotoId = photoIds.first()),
                    )
                stickerRecapRepository.savePhotos(lockedSticker.id, photoIds)
                val lockUntil = Instant.parse("2126-01-01T00:00:00Z")
                stickerCommandRepository.tryClaimRegenerationLock(lockedSticker.id, Instant.now(), lockUntil)
                val exception =
                    shouldThrow<ConflictException> {
                        service.regenerate(board.userId, lockedSticker.id)
                    }

                Then("STICKER-002 오류로 거부한다") {
                    exception.errorCode shouldBe StickerErrorCode.STICKER_REGENERATION_IN_PROGRESS
                }

                Then("스티커 이미지는 그대로다") {
                    imageKeyOf(lockedSticker.id) shouldBe "stickers/original.png"
                }

                Then("새 이미지를 업로드하지 않는다") {
                    stickerStorage.uploaded.keys
                        .none { it.startsWith("stickers/${lockedSticker.id}/") } shouldBe true
                }

                Then("먼저 잡힌 락을 건드리지 않는다") {
                    regenerationLockOf(lockedSticker.id) shouldBe lockUntil
                }
            }

            When("재생성에 성공한 직후 다시 요청하면") {
                service.regenerate(board.userId, sticker.id)
                val firstImageKey = imageKeyOf(sticker.id)
                service.regenerate(board.userId, sticker.id)

                Then("쿨다운 없이 이미지를 한 번 더 교체한다") {
                    imageKeyOf(sticker.id) shouldNotBe firstImageKey
                    imageKeyOf(sticker.id) shouldNotBe "stickers/original.png"
                }

                Then("재생성 락을 남기지 않는다") {
                    regenerationLockOf(sticker.id).shouldBeNull()
                }
            }

            When("재생성 중 예외가 발생한 뒤 다시 요청하면") {
                val failingSticker =
                    stickerRepository.save(
                        analysis.id,
                        board.id,
                        imageStickerCreation(sourcePhotoId = photoIds.first()),
                    )
                stickerRecapRepository.savePhotos(failingSticker.id, photoIds)
                var callCount = 0
                val flakyService =
                    serviceWith(
                        regenerationPorts =
                            listOf(
                                StickerRegenerationPort { _, _, _, _, previousSourcePhotoId ->
                                    callCount += 1
                                    if (callCount == 1) error("일시적인 재생성 실패")
                                    StickerRegenerationResult(previousSourcePhotoId, "stickers/retry-success.png", "#FF6B6B")
                                },
                            ),
                    )
                shouldThrow<IllegalStateException> {
                    flakyService.regenerate(board.userId, failingSticker.id)
                }
                flakyService.regenerate(board.userId, failingSticker.id)

                Then("재시도가 이미지를 교체한다") {
                    imageKeyOf(failingSticker.id) shouldBe "stickers/retry-success.png"
                }

                Then("재생성 락을 남기지 않는다") {
                    regenerationLockOf(failingSticker.id).shouldBeNull()
                }
            }

            When("만료된 재생성 락이 남아있는 상태에서 재요청하면") {
                val staleLockedSticker =
                    stickerRepository.save(
                        analysis.id,
                        board.id,
                        imageStickerCreation(sourcePhotoId = photoIds.first()),
                    )
                stickerRecapRepository.savePhotos(staleLockedSticker.id, photoIds)
                val past = Instant.now().minusSeconds(600)
                stickerCommandRepository.tryClaimRegenerationLock(staleLockedSticker.id, past, past)
                service.regenerate(board.userId, staleLockedSticker.id)

                Then("만료된 락을 재선점해 이미지를 교체한다") {
                    imageKeyOf(staleLockedSticker.id) shouldNotBe "stickers/original.png"
                }

                Then("재생성 락을 남기지 않는다") {
                    regenerationLockOf(staleLockedSticker.id).shouldBeNull()
                }
            }

            When("텍스트형 스티커를 재생성 요청하면") {
                val textSticker =
                    stickerRepository.save(analysis.id, board.id, textStickerCreation())

                Then("STICKER-005 오류로 거부한다") {
                    val exception =
                        shouldThrow<InvalidInputException> {
                            service.regenerate(board.userId, textSticker.id)
                        }
                    exception.errorCode shouldBe StickerErrorCode.NOT_REGENERATABLE_STICKER_TYPE
                }
            }

            When("사진 구성이 없는 스티커를 재생성 요청하면") {
                val orphanSticker =
                    stickerRepository.save(
                        analysis.id,
                        board.id,
                        imageStickerCreation(sourcePhotoId = photoIds.first()),
                    )

                Then("STICKER-006 오류로 거부한다") {
                    val exception =
                        shouldThrow<InvalidInputException> {
                            service.regenerate(board.userId, orphanSticker.id)
                        }
                    exception.errorCode shouldBe StickerErrorCode.REGENERATION_PHOTOS_NOT_FOUND
                }
            }
        }

        Given("이미지 스티커의 사진 구성 중 일부만 업로드 완료된 상태에서") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val analysis = analysisRepository.save(board.userId, board.id)
            val photos =
                photoRepository.saveAll(
                    analysis.id,
                    board.id,
                    listOf(
                        PhotoCreate(PhotoContentType.JPEG, Instant.parse("2026-07-03T00:00:00Z")),
                        PhotoCreate(PhotoContentType.JPEG, Instant.parse("2026-07-04T00:00:00Z")),
                    ),
                )
            photoRepository.markCompletedBatch(mapOf(photos.first().id to Instant.now()))
            val photoIds = photos.map { it.id }
            val sticker =
                stickerRepository.save(
                    analysis.id,
                    board.id,
                    imageStickerCreation(sourcePhotoId = photoIds.first()),
                )
            stickerRecapRepository.savePhotos(sticker.id, photoIds)

            When("재생성을 요청하면") {
                service.regenerate(board.userId, sticker.id)

                Then("조회 가능한 사진으로 재생성한다") {
                    val found = requireNotNull(stickerRepository.findById(sticker.id))
                    found.imageKey shouldNotBe "stickers/original.png"
                    found.sourcePhotoId shouldBe photoIds.first()
                }

                Then("기존 사진 구성은 유지한다") {
                    stickerRecapRepository.findPhotoIds(sticker.id).toSet() shouldBe photoIds.toSet()
                }
            }
        }

        Given("이미지 스티커의 사진 구성이 모두 업로드 완료되지 않은 상태에서") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val analysis = analysisRepository.save(board.userId, board.id)
            val photos =
                photoRepository.saveAll(
                    analysis.id,
                    board.id,
                    listOf(
                        PhotoCreate(PhotoContentType.JPEG, Instant.parse("2026-07-05T00:00:00Z")),
                        PhotoCreate(PhotoContentType.JPEG, Instant.parse("2026-07-06T00:00:00Z")),
                    ),
                )
            val photoIds = photos.map { it.id }
            val sticker =
                stickerRepository.save(
                    analysis.id,
                    board.id,
                    imageStickerCreation(sourcePhotoId = photoIds.first()),
                )
            stickerRecapRepository.savePhotos(sticker.id, photoIds)

            When("재생성을 요청하면") {
                val exception =
                    shouldThrow<InvalidInputException> {
                        service.regenerate(board.userId, sticker.id)
                    }

                Then("STICKER-006 오류로 거부한다") {
                    exception.errorCode shouldBe StickerErrorCode.REGENERATION_PHOTOS_NOT_FOUND
                }

                Then("스티커 이미지는 그대로다") {
                    imageKeyOf(sticker.id) shouldBe "stickers/original.png"
                }
            }
        }

        Given("드로잉 삭제 port가 없는 상태에서") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val analysis = analysisRepository.save(board.userId, board.id)
            val sticker = stickerRepository.save(analysis.id, board.id, textStickerCreation())
            val serviceWithoutPort = serviceWith()

            When("스티커 삭제를 요청하면") {
                val exception =
                    shouldThrow<IllegalStateException> {
                        serviceWithoutPort.delete(board.userId, sticker.id)
                    }

                Then("연동 누락 오류를 던진다") {
                    exception.message shouldBe "스티커 드로잉 삭제 application port 구현이 정확히 하나 필요합니다."
                }

                Then("삭제를 시작하지 않아 스티커가 남는다") {
                    stickerRepository.findById(sticker.id).shouldNotBeNull()
                }
            }
        }
    })
