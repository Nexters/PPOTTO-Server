package com.github.nexters.ppotto.sticker.infrastructure

import com.github.nexters.ppotto.analysis.domain.PhotoContentType
import com.github.nexters.ppotto.analysis.infrastructure.AnalysisRepository
import com.github.nexters.ppotto.analysis.infrastructure.PhotoCreate
import com.github.nexters.ppotto.analysis.infrastructure.PhotoRepository
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.sticker.domain.RecapCommentCreation
import com.github.nexters.ppotto.sticker.domain.RecapCommentPosition
import com.github.nexters.ppotto.sticker.support.imageStickerCreation
import com.github.nexters.ppotto.sticker.support.textStickerCreation
import com.github.nexters.ppotto.support.IntegrationTest
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
            val board = boardRepository.save(userRepository.saveTestUser().id)
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
                    imageStickerCreation(
                        sourcePhotoId = photos.first().id,
                        imageKey = "stickers/summer.png",
                        title = "여름 사진",
                        summary = "여름 내내 바다만 찍었어요",
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

            When("삭제된 스티커를 삭제 이전에 읽은 aggregate로 수정하면") {
                val staleSticker = checkNotNull(stickerRepository.findById(saved.id))
                stickerCommandRepository.softDelete(saved.id, Instant.now())
                staleSticker.rename("삭제 이후 제목")
                val renamed = stickerCommandRepository.updateTitle(staleSticker.id, staleSticker.title)

                Then("수정이 한 건도 반영되지 않는다") {
                    renamed shouldBe false
                }

                Then("삭제 상태가 그대로 유지된다") {
                    stickerRepository.findById(saved.id).shouldBeNull()
                }
            }

            When("리캡 자식 데이터를 제거하면") {
                stickerRecapRepository.deleteByStickerIds(listOf(saved.id))

                Then("사진 링크와 코멘트가 함께 사라진다") {
                    stickerRecapRepository.findPhotoIds(saved.id) shouldBe emptyList()
                    stickerRecapRepository.findComments(saved.id) shouldBe emptyList()
                }
            }
        }

        Given("스티커에 말풍선과 키워드 칩 코멘트가 등록된 상태에서") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val analysis = analysisRepository.save(board.userId, board.id)
            val sticker = stickerRepository.save(analysis.id, board.id, textStickerCreation(title = "코멘트 위치 테스트"))
            val comments =
                stickerRecapRepository.saveComments(
                    sticker.id,
                    listOf(
                        RecapCommentCreation("키워드 칩", null, null),
                        RecapCommentCreation("말풍선", 1.0, 2.0),
                    ),
                )
            val chipComment = comments.first { it.content == "키워드 칩" }
            val bubbleComment = comments.first { it.content == "말풍선" }

            When("말풍선과 키워드 칩 id로 위치 수정을 시도하면") {
                val updatedCount =
                    stickerRecapRepository.updatePositions(
                        sticker.id,
                        listOf(
                            RecapCommentPosition(bubbleComment.id, 9.0, 8.0),
                            RecapCommentPosition(chipComment.id, 5.0, 6.0),
                        ),
                    )

                Then("말풍선만 갱신되고 키워드 칩은 건너뛴다") {
                    updatedCount shouldBe 1
                    val found = stickerRecapRepository.findComments(sticker.id)
                    found.first { it.id == bubbleComment.id }.posX shouldBe 9.0
                    found
                        .first { it.id == chipComment.id }
                        .posX
                        .shouldBeNull()
                }
            }
        }

        Given("두 보드에 스티커가 등록된 상태에서") {
            val firstUser = userRepository.saveTestUser()
            val secondUser = userRepository.saveTestUser()
            val firstBoard = boardRepository.save(firstUser.id)
            val secondBoard = boardRepository.save(secondUser.id)
            val firstAnalysis = analysisRepository.save(firstUser.id, firstBoard.id)
            val secondAnalysis = analysisRepository.save(secondUser.id, secondBoard.id)
            val firstSticker =
                stickerRepository.save(firstAnalysis.id, firstBoard.id, textStickerCreation(title = "첫 스티커"))
            val secondSticker =
                stickerRepository.save(secondAnalysis.id, secondBoard.id, textStickerCreation(title = "둘째 스티커"))

            When("첫 보드의 스티커 소유 여부를 검증하면") {
                Then("첫 보드 스티커만 통과한다") {
                    stickerRepository.validateOwnedByBoard(firstBoard.id, listOf(firstSticker.id)) shouldBe true
                    stickerRepository.validateOwnedByBoard(firstBoard.id, listOf(secondSticker.id)) shouldBe false
                }
            }
        }

        Given("한 분석에 스티커가 6개 저장된 상태에서") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val analysis = analysisRepository.save(board.userId, board.id)
            val stickers = List(6) { stickerRepository.save(analysis.id, board.id, textStickerCreation(title = "스티커 $it")) }

            When("일곱 번째 스티커를 직접 저장하면") {
                shouldThrow<DataIntegrityViolationException> {
                    stickerRepository.save(analysis.id, board.id, textStickerCreation(title = "일곱 번째"))
                }

                Then("DB 제약이 저장을 거부한다") {
                    stickerRepository.findAllByAnalysisId(analysis.id).map { it.id } shouldContainExactly stickers.map { it.id }
                }
            }

            When("3개를 소프트 삭제한 뒤 일곱 번째 스티커를 저장하면") {
                stickers.take(3).forEach { stickerCommandRepository.softDelete(it.id, Instant.now()) }
                shouldThrow<DataIntegrityViolationException> {
                    stickerRepository.save(analysis.id, board.id, textStickerCreation(title = "일곱 번째"))
                }

                Then("소프트 삭제분도 수명 6개에 포함되어 거부한다") {
                    stickerRepository.findAllByAnalysisId(analysis.id).map { it.id } shouldContainExactly stickers.map { it.id }
                }
            }
        }

        Given("트랜잭션 없이 분석 결과 잠금을 시도할 때") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val analysis = analysisRepository.save(board.userId, board.id)

            When("advisory lock 을 요청하면") {
                val exception = shouldThrow<IllegalStateException> { stickerRepository.lockAnalysisResult(analysis.id) }

                Then("잠금이 조용히 무시되지 않고 즉시 실패한다") {
                    exception.message shouldBe "분석 결과 잠금은 트랜잭션 안에서만 획득할 수 있습니다."
                }
            }
        }
    })
