package com.github.nexters.ppotto.sticker.application

import com.github.nexters.ppotto.analysis.domain.PhotoContentType
import com.github.nexters.ppotto.analysis.infrastructure.persistence.AnalysisRepository
import com.github.nexters.ppotto.analysis.infrastructure.persistence.PhotoCreate
import com.github.nexters.ppotto.analysis.infrastructure.persistence.PhotoRepository
import com.github.nexters.ppotto.board.application.BoardShareService
import com.github.nexters.ppotto.board.domain.BoardErrorCode
import com.github.nexters.ppotto.board.domain.BoardShareScope
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.board.support.addBoardMember
import com.github.nexters.ppotto.global.error.NotFoundException
import com.github.nexters.ppotto.global.identifier.BoardId
import com.github.nexters.ppotto.global.identifier.PhotoId
import com.github.nexters.ppotto.global.identifier.UserId
import com.github.nexters.ppotto.sticker.domain.RecapCommentCreation
import com.github.nexters.ppotto.sticker.domain.Sticker
import com.github.nexters.ppotto.sticker.infrastructure.StickerCommandRepository
import com.github.nexters.ppotto.sticker.infrastructure.StickerRecapRepository
import com.github.nexters.ppotto.sticker.infrastructure.StickerRepository
import com.github.nexters.ppotto.sticker.support.imageStickerCreation
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.jooq.DSLContext
import java.time.Instant

class SharedBoardRecapQueryServiceTest(
    service: SharedBoardRecapQueryService,
    boardShareService: BoardShareService,
    boardRepository: BoardRepository,
    userRepository: UserRepository,
    analysisRepository: AnalysisRepository,
    photoRepository: PhotoRepository,
    stickerRepository: StickerRepository,
    stickerCommandRepository: StickerCommandRepository,
    stickerRecapRepository: StickerRecapRepository,
    dslContext: DSLContext,
) : IntegrationTest({
        fun saveRecapSticker(
            userId: UserId,
            boardId: BoardId,
        ): Pair<Sticker, List<PhotoId>> {
            val analysis = analysisRepository.save(userId, boardId)
            val photos =
                photoRepository.saveAll(
                    analysis.id,
                    boardId,
                    listOf(PhotoCreate(PhotoContentType.JPEG, Instant.parse("2026-07-01T00:00:00Z"))),
                )
            photoRepository.markCompletedBatch(photos.associate { it.id to Instant.now() })
            val sticker =
                stickerRepository.save(
                    analysis.id,
                    boardId,
                    imageStickerCreation(
                        sourcePhotoId = photos.first().id,
                        imageKey = "stickers/recap.png",
                        title = "리캡",
                        summary = "웃기고 귀여우면 일단 주워요",
                    ),
                )
            stickerRecapRepository.savePhotos(sticker.id, photos.map { it.id })
            stickerRecapRepository.saveComments(sticker.id, listOf(RecapCommentCreation("키워드", null, null)))
            return sticker to photos.map { it.id }
        }

        Given("방장과 참여자가 각자 스티커를 올린 공동 보드를 방장이 공유했을 때") {
            val owner = userRepository.saveTestUser()
            val member = userRepository.saveTestUser()
            val board = boardRepository.save(owner.id, "제주 여행")
            dslContext.addBoardMember(board.id, member.id)
            val (ownerSticker, _) = saveRecapSticker(owner.id, board.id)
            val (memberSticker, memberPhotoIds) = saveRecapSticker(member.id, board.id)

            When("리캡까지 공유한 링크로 스티커 리캡을 조회하면") {
                val share = boardShareService.share(board.id, owner.id, BoardShareScope.RECAP)
                val result = service.get(share.shareToken, ownerSticker.id)

                Then("리캡 내용은 반환하되 사진은 내려주지 않는다") {
                    result.sticker.id shouldBe ownerSticker.id
                    result.comments.map { it.content } shouldContainExactly listOf("키워드")
                    result.photos.shouldBeEmpty()
                }

                Then("남의 읽음 상태와 공유 상태는 내려주지 않는다") {
                    result.sticker.isNew shouldBe false
                    result.share shouldBe null
                }
            }

            When("테마 속 사진까지 공유한 링크로 다른 참여자의 스티커 리캡을 조회하면") {
                val share = boardShareService.share(board.id, owner.id, BoardShareScope.PHOTOS)
                val result = service.get(share.shareToken, memberSticker.id)

                Then("같은 범위를 적용해 사진까지 반환한다") {
                    result.sticker.id shouldBe memberSticker.id
                    result.photos.map { it.id } shouldContainExactly memberPhotoIds
                }
            }

            When("보드만 공유한 링크로 리캡을 조회하면") {
                val share = boardShareService.share(board.id, owner.id, BoardShareScope.BOARD)

                Then("공유 범위 밖이라 리캡을 찾을 수 없다") {
                    val exception = shouldThrow<NotFoundException> { service.get(share.shareToken, ownerSticker.id) }
                    exception.errorCode shouldBe BoardErrorCode.SHARED_RECAP_NOT_FOUND
                }
            }

            When("다른 보드의 스티커로 조회하면") {
                val share = boardShareService.share(board.id, owner.id, BoardShareScope.PHOTOS)
                val otherOwner = userRepository.saveTestUser()
                val otherBoard = boardRepository.save(otherOwner.id, "다른 보드")
                val (otherSticker, _) = saveRecapSticker(otherOwner.id, otherBoard.id)

                Then("이 링크로는 리캡을 찾을 수 없다") {
                    val exception = shouldThrow<NotFoundException> { service.get(share.shareToken, otherSticker.id) }
                    exception.errorCode shouldBe BoardErrorCode.SHARED_RECAP_NOT_FOUND
                }
            }

            When("삭제된 스티커로 조회하면") {
                val share = boardShareService.share(board.id, owner.id, BoardShareScope.PHOTOS)
                stickerCommandRepository.softDelete(memberSticker.id, Instant.now())

                Then("리캡을 찾을 수 없다") {
                    val exception = shouldThrow<NotFoundException> { service.get(share.shareToken, memberSticker.id) }
                    exception.errorCode shouldBe BoardErrorCode.SHARED_RECAP_NOT_FOUND
                }
            }
        }

        Given("발급된 적 없는 토큰으로") {
            val owner = userRepository.saveTestUser()
            val board = boardRepository.save(owner.id, "제주 여행")
            val (sticker, _) = saveRecapSticker(owner.id, board.id)

            When("리캡을 조회하면") {
                Then("공유된 보드를 찾을 수 없다") {
                    val exception = shouldThrow<NotFoundException> { service.get("unknown-token", sticker.id) }
                    exception.errorCode shouldBe BoardErrorCode.SHARE_NOT_FOUND
                }
            }
        }
    })
