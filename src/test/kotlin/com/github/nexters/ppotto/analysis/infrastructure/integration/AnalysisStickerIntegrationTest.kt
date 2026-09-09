package com.github.nexters.ppotto.analysis.infrastructure.integration

import com.github.nexters.ppotto.analysis.domain.PhotoContentType
import com.github.nexters.ppotto.analysis.infrastructure.AnalysisRepository
import com.github.nexters.ppotto.analysis.infrastructure.PhotoCreate
import com.github.nexters.ppotto.analysis.infrastructure.PhotoRepository
import com.github.nexters.ppotto.analysis.infrastructure.StickerObjectKeys
import com.github.nexters.ppotto.analysis.support.FakeStickerGenerator
import com.github.nexters.ppotto.analysis.support.FakeStickerStorage
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.global.error.BusinessException
import com.github.nexters.ppotto.global.error.NotFoundException
import com.github.nexters.ppotto.global.identifier.PhotoId
import com.github.nexters.ppotto.global.identifier.StickerId
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
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeInstanceOf
import org.springframework.context.ApplicationContext
import org.springframework.http.HttpStatus
import org.springframework.web.client.RestClientException
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
    private val stickerRegenerationAdapter: AnalysisStickerRegenerationAdapter,
    private val stickerGenerator: FakeStickerGenerator,
    private val stickerStorage: FakeStickerStorage,
) : IntegrationTest({
        Given("분석과 스티커 연동 어댑터가 기동된 상태에서") {
            When("연동 port 빈을 조회하면") {
                Then("port마다 실패 대신 실제 어댑터가 정확히 하나씩 주입된다") {
                    val ownershipPorts = applicationContext.getBeansOfType(AnalysisPhotoOwnershipPort::class.java)
                    ownershipPorts shouldHaveSize 1
                    ownershipPorts.values
                        .single()
                        .shouldBeInstanceOf<StickerAnalysisPhotoOwnershipAdapter>()

                    val recapPhotoPorts = applicationContext.getBeansOfType(RecapPhotoQueryPort::class.java)
                    recapPhotoPorts shouldHaveSize 1
                    recapPhotoPorts.values
                        .single()
                        .shouldBeInstanceOf<StickerRecapPhotoAdapter>()

                    val imageStoragePorts = applicationContext.getBeansOfType(StickerImageStoragePort::class.java)
                    imageStoragePorts shouldHaveSize 1
                    imageStoragePorts.values
                        .single()
                        .shouldBeInstanceOf<GcsStickerImageStorage>()
                }
            }
        }

        Given("업로드된 사진을 가진 분석이 있는 상태에서") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
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
                    requireNotNull(recap.sticker.imageUrl)
                        .shouldStartWith("https://storage.googleapis.com/ppotto-test-bucket/$stickerKey?")
                    requireNotNull(recap.sticker.imageUrl).shouldContain("X-Goog-Expires=3600")
                }

                Then("리캡 사진은 촬영 시각 오름차순으로 각자의 사진 오브젝트 키를 서명한다") {
                    recap.photos.map { it.id } shouldContainExactly photos.reversed().map { it.id }
                    recap.photos.first().imageUrl.shouldStartWith(
                        "https://storage.googleapis.com/ppotto-test-bucket/photos/${analysis.id}/${photos[1].id}.png?",
                    )
                    recap.photos.last().imageUrl.shouldStartWith(
                        "https://storage.googleapis.com/ppotto-test-bucket/photos/${analysis.id}/${photos[0].id}.jpg?",
                    )
                    recap.photos.forEach { it.imageUrl.shouldContain("X-Goog-Expires=3600") }
                }

                Then("리캡 코멘트를 그대로 반환한다") {
                    recap.comments.map { it.content } shouldContainExactly listOf("리캡 코멘트")
                }
            }
        }

        Given("서로 다른 사용자의 분석과 사진이 있는 상태에서") {
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
                                stickers = listOf(stickerResult(ownerPhoto.id, listOf(PhotoId(UUID.randomUUID())))),
                            ),
                        )
                    }
                    stickerRepository.findAllByBoardId(ownerBoard.id).shouldBeEmpty()
                }
            }
        }

        Given("업로드된 사진을 가진 분석에서 스티커 재생성을 요청할 때") {
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
            val stickerId = StickerId(UUID.randomUUID())

            When("배경 제거가 실패하면") {
                stickerGenerator.onGenerate = { throw RestClientException("Pixian 502") }
                val exception =
                    shouldThrow<BusinessException> {
                        stickerRegenerationAdapter.regenerate(analysis.id, board.id, stickerId, listOf(photo.id), photo.id)
                    }

                Then("파이프라인처럼 삼키지 않고 ANALYSIS-011을 클라이언트까지 올린다") {
                    exception.errorCode.code shouldBe "ANALYSIS-011"
                    exception.errorCode.status shouldBe HttpStatus.BAD_GATEWAY
                }

                Then("스티커 이미지는 업로드하지 않는다") {
                    stickerStorage.uploaded.keys
                        .shouldBeEmpty()
                }
            }

            When("생성된 스티커 업로드가 실패하면") {
                stickerStorage.uploadFailure = IllegalStateException("업로드 실패")
                val exception =
                    shouldThrow<BusinessException> {
                        stickerRegenerationAdapter.regenerate(analysis.id, board.id, stickerId, listOf(photo.id), photo.id)
                    }

                Then("같은 ANALYSIS-011로 매핑한다") {
                    exception.errorCode.code shouldBe "ANALYSIS-011"
                }
            }

            When("배경 제거와 업로드가 모두 성공하면") {
                val result = stickerRegenerationAdapter.regenerate(analysis.id, board.id, stickerId, listOf(photo.id), photo.id)

                Then("스티커 아이디로 묶은 재생성 오브젝트 키를 반환하고 그 키로 업로드한다") {
                    result.shouldNotBeNull()
                    result.sourcePhotoId shouldBe photo.id
                    result.imageKey shouldStartWith "stickers/$stickerId/${photo.id}-"
                    stickerStorage.uploaded.keys shouldContainExactly listOf(result.imageKey)
                }
            }

            When("사용할 수 있는 사진이 하나도 없으면") {
                val result =
                    stickerRegenerationAdapter.regenerate(
                        analysis.id,
                        board.id,
                        stickerId,
                        listOf(PhotoId(UUID.randomUUID())),
                        photo.id,
                    )

                Then("Gemini를 부르지 않고 null을 돌려준다") {
                    result.shouldBeNull()
                    stickerStorage.uploaded.keys
                        .shouldBeEmpty()
                }
            }
        }

        Given("업로드가 완료되지 않은 사진이 남아 있는 분석에서") {
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
                                        stickerResult(
                                            completedPhoto.id,
                                            listOf(completedPhoto.id, pendingPhoto.id),
                                        ),
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
    sourcePhotoId: PhotoId,
    photoIds: List<PhotoId>,
    imageKey: String = "stickers/result.png",
) = AnalysisStickerResult(
    type = StickerType.IMAGE,
    title = "분석 결과",
    summary = "웃기고 귀여우면 일단 주워요",
    sourcePhotoId = sourcePhotoId,
    imageKey = imageKey,
    textContent = null,
    mainColor = "#FF6B6B",
    photoIds = photoIds,
    comments = listOf(RecapCommentCreation("리캡 코멘트", null, null)),
)
