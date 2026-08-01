package com.github.nexters.ppotto.sticker.application

import com.github.nexters.ppotto.analysis.domain.PhotoContentType
import com.github.nexters.ppotto.analysis.infrastructure.AnalysisRepository
import com.github.nexters.ppotto.analysis.infrastructure.PhotoCreate
import com.github.nexters.ppotto.analysis.infrastructure.PhotoRepository
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.sticker.domain.RecapCommentCreation
import com.github.nexters.ppotto.sticker.domain.StickerCreation
import com.github.nexters.ppotto.sticker.domain.StickerType
import com.github.nexters.ppotto.sticker.infrastructure.StickerRecapRepository
import com.github.nexters.ppotto.sticker.infrastructure.StickerRepository
import com.github.nexters.ppotto.sticker.support.defaultStickerLayout
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.rawId
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import java.time.Instant

class StickerQueryServiceTest(
    service: StickerQueryService,
    stickerRepository: StickerRepository,
    stickerRecapRepository: StickerRecapRepository,
    photoRepository: PhotoRepository,
    analysisRepository: AnalysisRepository,
    boardRepository: BoardRepository,
    userRepository: UserRepository,
) : IntegrationTest({
        Given("이미지 스티커와 리캡 데이터가 등록된 상태에서") {
            val board = boardRepository.save(userRepository.saveTestUser().rawId)
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
            photoRepository.markCompletedBatch(photos.associate { it.id to Instant.now() })
            val sticker =
                stickerRepository.save(
                    analysis.id,
                    board.id,
                    StickerCreation(
                        type = StickerType.IMAGE,
                        title = "리캡",
                        summary = "웃기고 귀여우면 일단 주워요",
                        sourcePhotoId = photos.first().id,
                        imageKey = "stickers/recap.png",
                        textContent = null,
                        layout = defaultStickerLayout(),
                    ),
                )
            stickerRecapRepository.savePhotos(sticker.id, photos.map { it.id })
            stickerRecapRepository.saveComments(
                sticker.id,
                listOf(
                    RecapCommentCreation("말풍선", 3.0, 4.0),
                    RecapCommentCreation("키워드", null, null),
                ),
            )

            When("보드 스티커를 조회하면") {
                val result = service.getByBoardId(board.id).single()

                Then("읽기용 이미지 URL과 배치를 반환한다") {
                    result.id shouldBe sticker.id
                    result.imageUrl.shouldStartWith("https://storage.googleapis.com/ppotto-test-bucket/stickers/recap.png?")
                    result.imageUrl.shouldContain("X-Goog-Expires=3600")
                    result.isNew shouldBe true
                }
            }

            When("리캡 상세를 조회하면") {
                val result = service.getRecap(board.userId, sticker.id)

                Then("한 줄 요약과 코멘트와 촬영 시각순 사진을 반환한다") {
                    result.summary shouldBe "웃기고 귀여우면 일단 주워요"
                    result.comments.map { it.content } shouldContainExactly listOf("말풍선", "키워드")
                    result.photos.map { it.id } shouldContainExactly photos.reversed().map { it.id }
                }

                Then("말풍선은 좌표를 갖고 키워드 칩은 좌표가 null이다") {
                    val (bubble, chip) = result.comments

                    bubble.posX shouldBe 3.0
                    bubble.posY shouldBe 4.0
                    chip.posX shouldBe null
                    chip.posY shouldBe null
                }
            }

            When("리캡 사진의 읽기 URL을 확인하면") {
                val result = service.getRecap(board.userId, sticker.id)

                Then("사진 오브젝트 키로 서명한 1시간 만료 URL을 반환한다") {
                    result.photos.forEach {
                        it.imageUrl.shouldStartWith(
                            "https://storage.googleapis.com/ppotto-test-bucket/photos/${analysis.id}/${it.id}.jpg?",
                        )
                        it.imageUrl.shouldContain("X-Goog-Expires=3600")
                    }
                }
            }
        }

        Given("업로드가 완료되지 않은 사진이 리캡에 연결된 상태에서") {
            val board = boardRepository.save(userRepository.saveTestUser().rawId)
            val analysis = analysisRepository.save(board.userId, board.id)
            val pendingPhoto =
                photoRepository
                    .saveAll(
                        analysis.id,
                        board.id,
                        listOf(PhotoCreate(PhotoContentType.JPEG, Instant.parse("2026-07-01T00:00:00Z"))),
                    ).single()
            val sticker =
                stickerRepository.save(
                    analysis.id,
                    board.id,
                    StickerCreation(
                        type = StickerType.IMAGE,
                        title = "리캡",
                        summary = "웃기고 귀여우면 일단 주워요",
                        sourcePhotoId = pendingPhoto.id,
                        imageKey = "stickers/recap.png",
                        textContent = null,
                        layout = defaultStickerLayout(),
                    ),
                )
            stickerRecapRepository.savePhotos(sticker.id, listOf(pendingPhoto.id))

            When("리캡 상세를 조회하면") {
                Then("완료되지 않은 사진을 제외하고 계약 불일치로 실패한다") {
                    shouldThrow<IllegalStateException> {
                        service.getRecap(board.userId, sticker.id)
                    }
                }
            }
        }
    })
