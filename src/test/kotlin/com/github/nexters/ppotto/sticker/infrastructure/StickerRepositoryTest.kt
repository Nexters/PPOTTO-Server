package com.github.nexters.ppotto.sticker.infrastructure

import com.github.nexters.ppotto.analysis.domain.PhotoContentType
import com.github.nexters.ppotto.analysis.infrastructure.AnalysisRepository
import com.github.nexters.ppotto.analysis.infrastructure.PhotoCreate
import com.github.nexters.ppotto.analysis.infrastructure.PhotoRepository
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.sticker.domain.RecapCommentCreation
import com.github.nexters.ppotto.sticker.domain.StickerCreation
import com.github.nexters.ppotto.sticker.domain.StickerLayout
import com.github.nexters.ppotto.sticker.domain.StickerType
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.rawId
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.springframework.dao.DataIntegrityViolationException
import java.time.Instant

class StickerRepositoryTest(
    stickerRepository: StickerRepository,
    stickerCommandRepository: StickerCommandRepository,
    stickerRecapRepository: StickerRecapRepository,
    photoRepository: PhotoRepository,
    analysisRepository: AnalysisRepository,
    boardRepository: BoardRepository,
    userRepository: UserRepository,
) : IntegrationTest({
        Given("분석과 사진이 등록된 상태에서 스티커 리캡을 저장하면") {
            val board = boardRepository.save(userRepository.saveTestUser().rawId)
            val analysis = analysisRepository.save(board.userId, board.id)
            val photos =
                photoRepository.saveAll(
                    analysis.id,
                    board.id,
                    listOf(
                        PhotoCreate(PhotoContentType.JPEG, Instant.parse("2026-07-01T00:00:00Z")),
                        PhotoCreate(PhotoContentType.PNG, Instant.parse("2026-07-02T00:00:00Z")),
                    ),
                )
            val saved =
                stickerRepository.save(
                    analysis.id,
                    board.id,
                    StickerCreation(
                        type = StickerType.IMAGE,
                        title = "여름 사진",
                        summary = "여름 내내 바다만 찍었어요",
                        sourcePhotoId = photos.first().id,
                        imageKey = "stickers/summer.png",
                        textContent = null,
                        layout = layout(),
                    ),
                )
            stickerRecapRepository.savePhotos(saved.id, photos.map { it.id })
            stickerRecapRepository.saveComments(
                saved.id,
                listOf(
                    RecapCommentCreation("키워드 칩", null, null),
                    RecapCommentCreation("말풍선", 1.0, 2.0),
                ),
            )

            When("스티커와 리캡 데이터를 조회하면") {
                val sticker = stickerRepository.findById(saved.id)
                val photoIds = stickerRecapRepository.findPhotoIds(saved.id)
                val comments = stickerRecapRepository.findComments(saved.id)

                Then("저장한 aggregate 데이터를 반환한다") {
                    sticker?.title shouldBe "여름 사진"
                    sticker?.summary shouldBe "여름 내내 바다만 찍었어요"
                    photoIds shouldContainExactly photos.map { it.id }
                    comments.map { it.content } shouldContainExactly listOf("키워드 칩", "말풍선")
                }
            }

            When("스티커를 삭제하고 리캡 자식을 제거하면") {
                val staleSticker = checkNotNull(stickerRepository.findById(saved.id))
                saved.delete(Instant.now())
                stickerCommandRepository.softDelete(saved.id, checkNotNull(saved.deletedAt))
                staleSticker.rename("삭제 이후 제목")
                val renamed = stickerCommandRepository.updateTitle(staleSticker.id, staleSticker.title)
                stickerRecapRepository.deleteByStickerIds(listOf(saved.id))

                Then("오래된 aggregate가 삭제를 되돌리지 않고 리캡 자식도 조회되지 않는다") {
                    renamed shouldBe false
                    stickerRepository.findById(saved.id).shouldBeNull()
                    stickerRecapRepository.findPhotoIds(saved.id) shouldBe emptyList()
                    stickerRecapRepository.findComments(saved.id) shouldBe emptyList()
                }
            }
        }

        Given("두 보드에 스티커가 등록된 상태에서") {
            val firstUser = userRepository.saveTestUser()
            val secondUser = userRepository.saveTestUser()
            val firstBoard = boardRepository.save(firstUser.rawId)
            val secondBoard = boardRepository.save(secondUser.rawId)
            val firstAnalysis = analysisRepository.save(firstUser.rawId, firstBoard.id)
            val secondAnalysis = analysisRepository.save(secondUser.rawId, secondBoard.id)
            val firstSticker =
                stickerRepository.save(
                    firstAnalysis.id,
                    firstBoard.id,
                    textCreation("첫 스티커"),
                )
            val secondSticker =
                stickerRepository.save(
                    secondAnalysis.id,
                    secondBoard.id,
                    textCreation("둘째 스티커"),
                )

            When("첫 보드의 스티커 소유 여부를 검증하면") {
                Then("첫 보드 스티커만 통과한다") {
                    stickerRepository.validateOwnedByBoard(firstBoard.id, listOf(firstSticker.id)) shouldBe true
                    stickerRepository.validateOwnedByBoard(firstBoard.id, listOf(secondSticker.id)) shouldBe false
                }
            }
        }

        Given("한 분석에 스티커가 6개 저장된 상태에서") {
            val board = boardRepository.save(userRepository.saveTestUser().rawId)
            val analysis = analysisRepository.save(board.userId, board.id)
            repeat(6) {
                stickerRepository.save(analysis.id, board.id, textCreation("스티커 $it"))
            }

            When("일곱 번째 스티커를 직접 저장하면") {
                Then("DB 제약이 저장을 거부한다") {
                    shouldThrow<DataIntegrityViolationException> {
                        stickerRepository.save(analysis.id, board.id, textCreation("일곱 번째"))
                    }
                    stickerRepository.findAllByAnalysisId(analysis.id).size shouldBe 6
                }
            }
        }
    })

private fun textCreation(title: String) =
    StickerCreation(
        type = StickerType.TEXT,
        title = title,
        summary = "한 줄 요약",
        sourcePhotoId = null,
        imageKey = null,
        textContent = "텍스트",
        layout = layout(),
    )

private fun layout() =
    StickerLayout(
        posX = 10.0,
        posY = 20.0,
        scale = 1.0,
        rotation = 0.0,
        zIndex = 1,
        badgeOffsetX = 0.0,
        badgeOffsetY = 0.0,
        badgeRotation = 0.0,
    )
