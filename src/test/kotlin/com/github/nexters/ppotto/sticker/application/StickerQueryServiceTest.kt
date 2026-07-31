package com.github.nexters.ppotto.sticker.application

import com.github.nexters.ppotto.analysis.domain.PhotoContentType
import com.github.nexters.ppotto.analysis.infrastructure.AnalysisRepository
import com.github.nexters.ppotto.analysis.infrastructure.PhotoCreate
import com.github.nexters.ppotto.analysis.infrastructure.PhotoRepository
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.sticker.application.port.RecapPhotoMetadata
import com.github.nexters.ppotto.sticker.domain.RecapCommentCreation
import com.github.nexters.ppotto.sticker.domain.StickerCreation
import com.github.nexters.ppotto.sticker.domain.StickerLayout
import com.github.nexters.ppotto.sticker.domain.StickerType
import com.github.nexters.ppotto.sticker.infrastructure.StickerRecapRepository
import com.github.nexters.ppotto.sticker.infrastructure.StickerRepository
import com.github.nexters.ppotto.sticker.support.FakeRecapPhotoQueryPort
import com.github.nexters.ppotto.sticker.support.StickerTestConfig
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.springframework.context.annotation.Import
import java.time.Instant

@Import(StickerTestConfig::class)
class StickerQueryServiceTest(
    service: StickerQueryService,
    stickerRepository: StickerRepository,
    stickerRecapRepository: StickerRecapRepository,
    fakePhotoPort: FakeRecapPhotoQueryPort,
    photoRepository: PhotoRepository,
    analysisRepository: AnalysisRepository,
    boardRepository: BoardRepository,
    userRepository: UserRepository,
) : IntegrationTest({
        Given("이미지 스티커와 리캡 데이터가 등록된 상태에서") {
            val board = boardRepository.save(userRepository.save().id)
            val analysis = analysisRepository.save(board.userId, board.id)
            val photos =
                photoRepository.saveAll(
                    analysis.id,
                    board.id,
                    listOf(
                        PhotoCreate(PhotoContentType.JPEG, Instant.parse("2026-07-02T00:00:00Z")),
                        PhotoCreate(PhotoContentType.JPEG, Instant.parse("2026-07-01T00:00:00Z")),
                    ),
                )
            val sticker =
                stickerRepository.save(
                    analysis.id,
                    board.id,
                    StickerCreation(
                        type = StickerType.IMAGE,
                        title = "리캡",
                        sourcePhotoId = photos.first().id,
                        imageKey = "stickers/recap.png",
                        textContent = null,
                        layout = queryLayout(),
                    ),
                )
            stickerRecapRepository.savePhotos(sticker.id, photos.map { it.id })
            stickerRecapRepository.saveComments(
                sticker.id,
                listOf(RecapCommentCreation("코멘트", true, 3.0, 4.0)),
            )
            fakePhotoPort.photos.putAll(
                photos.associate {
                    it.id to
                        RecapPhotoMetadata(
                            id = it.id,
                            imageUrl = "https://example.com/photos/${it.id}",
                            takenAt = requireNotNull(it.takenAt),
                        )
                },
            )

            When("보드 스티커를 조회하면") {
                val result = service.getByBoardId(board.id).single()

                Then("읽기용 이미지 URL과 배치를 반환한다") {
                    result.id shouldBe sticker.id
                    result.imageUrl shouldBe "https://example.com/stickers/recap.png"
                    result.isNew shouldBe true
                }
            }

            When("리캡 상세를 조회하면") {
                val result = service.getRecap(board.userId, sticker.id)

                Then("코멘트와 촬영 시각순 사진을 반환한다") {
                    result.comments.map { it.content } shouldContainExactly listOf("코멘트")
                    result.photos.map { it.id } shouldContainExactly photos.reversed().map { it.id }
                }
            }
        }
    })

private fun queryLayout() =
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
