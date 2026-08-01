package com.github.nexters.ppotto.analysis.infrastructure.integration

import com.github.nexters.ppotto.analysis.domain.PhotoContentType
import com.github.nexters.ppotto.analysis.infrastructure.AnalysisRepository
import com.github.nexters.ppotto.analysis.infrastructure.PhotoCreate
import com.github.nexters.ppotto.analysis.infrastructure.PhotoRepository
import com.github.nexters.ppotto.analysis.infrastructure.StickerObjectKeys
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.global.error.NotFoundException
import com.github.nexters.ppotto.sticker.application.AnalysisResultSaveService
import com.github.nexters.ppotto.sticker.application.AnalysisStickerResult
import com.github.nexters.ppotto.sticker.application.SaveAnalysisResultCommand
import com.github.nexters.ppotto.sticker.application.StickerQueryService
import com.github.nexters.ppotto.sticker.application.port.AnalysisPhotoOwnershipPort
import com.github.nexters.ppotto.sticker.application.port.RecapPhotoQueryPort
import com.github.nexters.ppotto.sticker.application.port.StickerImageStoragePort
import com.github.nexters.ppotto.sticker.domain.RecapCommentCreation
import com.github.nexters.ppotto.sticker.domain.StickerType
import com.github.nexters.ppotto.sticker.infrastructure.GcsStickerImageStorage
import com.github.nexters.ppotto.sticker.infrastructure.StickerRepository
import com.github.nexters.ppotto.sticker.support.defaultStickerLayout
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.rawId
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeInstanceOf
import org.springframework.context.ApplicationContext
import java.time.Instant
import java.util.UUID

class AnalysisStickerIntegrationTest(
    applicationContext: ApplicationContext,
    analysisResultSaveService: AnalysisResultSaveService,
    stickerQueryService: StickerQueryService,
    stickerRepository: StickerRepository,
    photoRepository: PhotoRepository,
    analysisRepository: AnalysisRepository,
    boardRepository: BoardRepository,
    userRepository: UserRepository,
) : IntegrationTest({
        Given("분석과 스티커 연동 어댑터가 기동된 상태에서") {
            When("연동 port 빈을 조회하면") {
                Then("port마다 실패 대신 실제 어댑터가 정확히 하나씩 주입된다") {
                    applicationContext.getBeansOfType(AnalysisPhotoOwnershipPort::class.java).let {
                        it shouldHaveSize 1
                        it.values
                            .single()
                            .shouldBeInstanceOf<StickerAnalysisPhotoOwnershipAdapter>()
                    }
                    applicationContext.getBeansOfType(RecapPhotoQueryPort::class.java).let {
                        it shouldHaveSize 1
                        it.values
                            .single()
                            .shouldBeInstanceOf<StickerRecapPhotoAdapter>()
                    }
                    applicationContext.getBeansOfType(StickerImageStoragePort::class.java).let {
                        it shouldHaveSize 1
                        it.values
                            .single()
                            .shouldBeInstanceOf<GcsStickerImageStorage>()
                    }
                }
            }
        }

        Given("업로드된 사진을 가진 분석이 있는 상태에서") {
            val board = boardRepository.save(userRepository.saveTestUser().rawId)
            val analysis = analysisRepository.save(board.userId, board.id)
            val photos =
                photoRepository.saveAll(
                    analysis.id,
                    board.id,
                    listOf(
                        PhotoCreate(PhotoContentType.JPEG, Instant.parse("2026-07-02T00:00:00Z")),
                        PhotoCreate(PhotoContentType.PNG, Instant.parse("2026-07-01T00:00:00Z")),
                    ),
                )
            photoRepository.markCompletedBatch(photos.associate { it.id to Instant.now() })

            When("분석 결과를 저장하고 리캡을 조회하면") {
                val stickerKey = StickerObjectKeys.keyFor(analysis.id, 0, photos.first().id)
                val saved =
                    analysisResultSaveService.save(
                        SaveAnalysisResultCommand(
                            userId = board.userId,
                            analysisId = analysis.id,
                            boardId = board.id,
                            stickers = listOf(stickerResult(photos.first().id, photos.map { it.id }, stickerKey)),
                        ),
                    )
                val recap = stickerQueryService.getRecap(board.userId, saved.stickerIds.single())

                Then("파이프라인이 만든 스티커 오브젝트 키를 그대로 읽기용 signed URL로 서명한다") {
                    recap.sticker.id shouldBe saved.stickerIds.single()
                    recap.comments.map { it.content } shouldContainExactly listOf("리캡 코멘트")
                    recap.photos.map { it.id } shouldContainExactly photos.reversed().map { it.id }
                    requireNotNull(recap.sticker.imageUrl)
                        .shouldStartWith("https://storage.googleapis.com/ppotto-test-bucket/$stickerKey?")
                    recap.photos.first().imageUrl.shouldStartWith(
                        "https://storage.googleapis.com/ppotto-test-bucket/photos/${analysis.id}/${photos[1].id}.png?",
                    )
                    recap.photos.last().imageUrl.shouldStartWith(
                        "https://storage.googleapis.com/ppotto-test-bucket/photos/${analysis.id}/${photos[0].id}.jpg?",
                    )
                    recap.photos.forEach { it.imageUrl.shouldContain("X-Goog-Expires=3600") }
                }
            }
        }

        Given("서로 다른 사용자의 분석과 사진이 있는 상태에서") {
            val ownerBoard = boardRepository.save(userRepository.saveTestUser().rawId)
            val ownerAnalysis = analysisRepository.save(ownerBoard.userId, ownerBoard.id)
            val ownerPhoto =
                photoRepository
                    .saveAll(
                        ownerAnalysis.id,
                        ownerBoard.id,
                        listOf(PhotoCreate(PhotoContentType.JPEG, Instant.parse("2026-07-01T00:00:00Z"))),
                    ).single()
            val otherBoard = boardRepository.save(userRepository.saveTestUser().rawId)
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

            When("다른 사용자의 photoId를 섞어 저장하면") {
                Then("실제 소유권 어댑터가 저장 전에 거부한다") {
                    shouldThrow<NotFoundException> {
                        analysisResultSaveService.save(
                            SaveAnalysisResultCommand(
                                userId = ownerBoard.userId,
                                analysisId = ownerAnalysis.id,
                                boardId = ownerBoard.id,
                                stickers =
                                    listOf(
                                        stickerResult(ownerPhoto.id, listOf(ownerPhoto.id, otherPhoto.id)),
                                    ),
                            ),
                        )
                    }
                    stickerRepository.findAllByBoardId(ownerBoard.id).shouldBeEmpty()
                }
            }

            When("존재하지 않는 photoId로 저장하면") {
                Then("실제 소유권 어댑터가 저장 전에 거부한다") {
                    shouldThrow<NotFoundException> {
                        analysisResultSaveService.save(
                            SaveAnalysisResultCommand(
                                userId = ownerBoard.userId,
                                analysisId = ownerAnalysis.id,
                                boardId = ownerBoard.id,
                                stickers = listOf(stickerResult(ownerPhoto.id, listOf(UUID.randomUUID()))),
                            ),
                        )
                    }
                    stickerRepository.findAllByBoardId(ownerBoard.id).shouldBeEmpty()
                }
            }
        }

        Given("업로드가 완료되지 않은 사진이 남아 있는 분석에서") {
            val board = boardRepository.save(userRepository.saveTestUser().rawId)
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
            val completedPhoto = photos.first()
            val pendingPhoto = photos.last()
            photoRepository.markCompletedBatch(mapOf(completedPhoto.id to Instant.now()))

            When("PENDING 사진을 리캡 photoIds에 섞어 저장하면") {
                Then("실제 소유권 어댑터가 저장 전에 거부한다") {
                    shouldThrow<NotFoundException> {
                        analysisResultSaveService.save(
                            SaveAnalysisResultCommand(
                                userId = board.userId,
                                analysisId = analysis.id,
                                boardId = board.id,
                                stickers =
                                    listOf(
                                        stickerResult(completedPhoto.id, listOf(completedPhoto.id, pendingPhoto.id)),
                                    ),
                            ),
                        )
                    }
                    stickerRepository.findAllByBoardId(board.id).shouldBeEmpty()
                }
            }
        }
    })

private fun stickerResult(
    sourcePhotoId: UUID,
    photoIds: List<UUID>,
    imageKey: String = "stickers/result.png",
) = AnalysisStickerResult(
    type = StickerType.IMAGE,
    title = "분석 결과",
    summary = "웃기고 귀여우면 일단 주워요",
    sourcePhotoId = sourcePhotoId,
    imageKey = imageKey,
    textContent = null,
    layout = defaultStickerLayout(),
    photoIds = photoIds,
    comments = listOf(RecapCommentCreation("리캡 코멘트", null, null)),
)
