package com.github.nexters.ppotto.board.application

import com.github.nexters.ppotto.analysis.domain.AnalysisStatus
import com.github.nexters.ppotto.analysis.domain.PhotoContentType
import com.github.nexters.ppotto.analysis.infrastructure.persistence.AnalysisRepository
import com.github.nexters.ppotto.analysis.infrastructure.persistence.PhotoCreate
import com.github.nexters.ppotto.analysis.infrastructure.persistence.PhotoRepository
import com.github.nexters.ppotto.board.application.port.BoardAnalysisStatus
import com.github.nexters.ppotto.board.domain.BoardErrorCode
import com.github.nexters.ppotto.board.domain.BoardKind
import com.github.nexters.ppotto.board.domain.BoardRole
import com.github.nexters.ppotto.board.domain.DrawingScope
import com.github.nexters.ppotto.board.domain.NewDrawing
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.board.infrastructure.DrawingRepository
import com.github.nexters.ppotto.board.support.BoardTestConfig
import com.github.nexters.ppotto.board.support.FakeBoardStickerPort
import com.github.nexters.ppotto.board.support.addBoardMember
import com.github.nexters.ppotto.board.support.boardStickerItem
import com.github.nexters.ppotto.board.support.changeAnalysisStatus
import com.github.nexters.ppotto.board.support.uuidV7
import com.github.nexters.ppotto.global.error.NotFoundException
import com.github.nexters.ppotto.global.identifier.DrawingId
import com.github.nexters.ppotto.jooq.tables.references.BOARDS
import com.github.nexters.ppotto.sticker.infrastructure.StickerCommandRepository
import com.github.nexters.ppotto.sticker.infrastructure.StickerRepository
import com.github.nexters.ppotto.sticker.support.imageStickerCreation
import com.github.nexters.ppotto.sticker.support.textStickerCreation
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import org.jooq.DSLContext
import org.springframework.context.annotation.Import
import java.time.Instant

@Import(BoardTestConfig::class)
class BoardQueryServiceTest(
    boardQueryService: BoardQueryService,
    boardRepository: BoardRepository,
    drawingRepository: DrawingRepository,
    userRepository: UserRepository,
    stickerPort: FakeBoardStickerPort,
    dslContext: DSLContext,
    stickerRepository: StickerRepository,
    stickerCommandRepository: StickerCommandRepository,
    analysisRepository: AnalysisRepository,
    photoRepository: PhotoRepository,
) : IntegrationTest({
        Given("스티커와 드로잉이 있는 보드를 소유한 사용자가") {
            val user = userRepository.saveTestUser()
            val board = boardRepository.save(user.id, "여름 휴가")
            val sticker = boardStickerItem()
            val drawing =
                NewDrawing.Stroke(
                    id = DrawingId(uuidV7()),
                    boardId = board.id,
                    stickerId = sticker.id,
                    scope = DrawingScope.STICKER,
                    zIndex = 0,
                    stroke = mapOf("points" to listOf(listOf(10.5, 22.0))),
                    color = "#FFD400",
                    strokeWidth = 4.0,
                )
            stickerPort.stickersByBoardId[board.id] = listOf(sticker)
            drawingRepository.upsertAll(listOf(drawing))

            When("보드 상세를 조회하면") {
                val detail = boardQueryService.getDetail(board.id, user.id)

                Then("보드와 스티커와 드로잉을 함께 반환한다") {
                    detail.name shouldBe "여름 휴가"
                    detail.stickers.map { it.id } shouldContainExactly listOf(sticker.id)
                    detail.drawings.map { it.id } shouldContainExactly listOf(drawing.id)
                }
            }

            When("다른 사용자가 조회하면") {
                Then("BOARD-002 오류가 발생한다") {
                    val exception =
                        shouldThrow<NotFoundException> {
                            boardQueryService.getDetail(board.id, userRepository.saveTestUser().id)
                        }
                    exception.errorCode shouldBe BoardErrorCode.NOT_FOUND
                }
            }

            When("참여자로 등록된 사용자가 조회하면") {
                val member = userRepository.saveTestUser()
                dslContext.addBoardMember(board.id, member.id)
                val detail = boardQueryService.getDetail(board.id, member.id)

                Then("방장과 같은 보드 상세를 반환한다") {
                    detail.id shouldBe board.id
                    detail.stickers.map { it.id } shouldContainExactly listOf(sticker.id)
                    detail.drawings.map { it.id } shouldContainExactly listOf(drawing.id)
                }
            }
        }

        Given("내 개인 보드, 내 공동 보드, 참여한 보드, 남의 보드가 섞여 있을 때") {
            val me = userRepository.saveTestUser()
            val owner = userRepository.saveTestUser()
            val friend = userRepository.saveTestUser()

            val personal = boardRepository.save(me.id, "개인")
            val myShared = boardRepository.save(me.id, "내 공동")
            dslContext.addBoardMember(myShared.id, friend.id)
            val joined = boardRepository.save(owner.id, "참여 보드")
            dslContext.addBoardMember(joined.id, me.id)
            dslContext.addBoardMember(joined.id, friend.id)
            boardRepository.save(owner.id, "남의 보드")
            val deletedJoined = boardRepository.save(owner.id, "삭제 보드")
            dslContext.addBoardMember(deletedJoined.id, me.id)
            boardRepository.softDelete(deletedJoined.id, owner.id)

            val completedAnalysis = analysisRepository.save(me.id, myShared.id)
            dslContext.changeAnalysisStatus(completedAnalysis.id, AnalysisStatus.COMPLETED)
            val photos =
                photoRepository.saveAll(
                    completedAnalysis.id,
                    myShared.id,
                    (1..5).map { PhotoCreate(PhotoContentType.JPEG, Instant.parse("2026-07-0${it}T00:00:00Z")) },
                )
            val imageStickers =
                photos.mapIndexed { index, photo ->
                    stickerRepository.save(
                        completedAnalysis.id,
                        myShared.id,
                        imageStickerCreation(
                            sourcePhotoId = photo.id,
                            imageKey = "stickers/image-${index + 1}.png",
                            mainColor = "#00000${index + 1}",
                        ),
                    )
                }
            val textSticker = stickerRepository.save(completedAnalysis.id, myShared.id, textStickerCreation())
            val activeAnalysis = analysisRepository.save(me.id, myShared.id)
            val deletedPhoto =
                photoRepository
                    .saveAll(activeAnalysis.id, myShared.id, listOf(PhotoCreate(PhotoContentType.JPEG, Instant.now())))
                    .single()
            val deletedImageSticker =
                stickerRepository.save(
                    activeAnalysis.id,
                    myShared.id,
                    imageStickerCreation(sourcePhotoId = deletedPhoto.id, imageKey = "stickers/deleted.png", mainColor = "#FFFFFF"),
                )
            stickerCommandRepository.softDelete(deletedImageSticker.id, Instant.now())

            val ownerAnalysis = analysisRepository.save(owner.id, joined.id)
            dslContext.changeAnalysisStatus(ownerAnalysis.id, AnalysisStatus.COMPLETED)
            val joinedSticker = stickerRepository.save(ownerAnalysis.id, joined.id, textStickerCreation())
            analysisRepository.save(friend.id, joined.id)

            When("내가 보드 목록 v2를 조회하면") {
                val boards = boardQueryService.listSummaries(me.id)

                Then("내가 만든 보드와 참여한 보드만 최근 활동순으로 반환한다") {
                    boards.map { it.id } shouldContainExactly listOf(joined.id, myShared.id, personal.id)
                }

                Then("참여자가 있으면 공동 보드이고 내 역할과 참여 인원을 함께 준다") {
                    boards.associate { it.id to Triple(it.kind, it.role, it.participantCount) } shouldBe
                        mapOf(
                            joined.id to Triple(BoardKind.SHARED, BoardRole.MEMBER, 3),
                            myShared.id to Triple(BoardKind.SHARED, BoardRole.OWNER, 2),
                            personal.id to Triple(BoardKind.PERSONAL, BoardRole.OWNER, 1),
                        )
                }

                Then("삭제되지 않은 스티커만 세고 최근 이미지형 스티커 4개를 최신순으로 미리보기한다") {
                    val summary = boards.single { it.id == myShared.id }
                    summary.stickerCount shouldBe 6
                    summary.previewImageUrls.size shouldBe 4
                    summary.previewImageUrls.zip(listOf(5, 4, 3, 2)).forEach { (url, index) ->
                        url shouldStartWith "https://storage.googleapis.com/ppotto-test-bucket/stickers/image-$index.png?"
                    }
                    summary.mainColor shouldBe "#000005"
                    summary.lastActivityAt shouldBe (imageStickers + textSticker).maxOf { it.updatedAt }
                }

                Then("이미지형 스티커가 없으면 미리보기는 비고 메인 컬러는 null이다") {
                    val summary = boards.single { it.id == joined.id }
                    summary.stickerCount shouldBe 1
                    summary.previewImageUrls.shouldBeEmpty()
                    summary.mainColor.shouldBeNull()
                    summary.lastActivityAt shouldBe joinedSticker.updatedAt
                }

                Then("스티커가 없는 보드는 보드 생성 시각을 마지막 활동 시각으로 쓴다") {
                    val summary = boards.single { it.id == personal.id }
                    summary.stickerCount shouldBe 0
                    summary.previewImageUrls.shouldBeEmpty()
                    summary.mainColor.shouldBeNull()
                    summary.lastActivityAt shouldBe personal.createdAt
                }

                Then("내가 진행 중인 분석만 그 보드에 표시하고 다른 참여자의 분석은 보이지 않는다") {
                    boards.associate { it.id to it.analysisStatus } shouldBe
                        mapOf(
                            joined.id to null,
                            myShared.id to BoardAnalysisStatus.UPLOADING,
                            personal.id to null,
                        )
                }
            }

            When("다른 참여자가 보드 목록 v2를 조회하면") {
                val boards = boardQueryService.listSummaries(friend.id)

                Then("자기 분석이 진행 중인 참여 보드에만 분석 상태가 보인다") {
                    boards.associate { it.id to it.analysisStatus } shouldBe
                        mapOf(
                            joined.id to BoardAnalysisStatus.UPLOADING,
                            myShared.id to null,
                        )
                }
            }

            When("v1 보드 목록을 조회하면") {
                val boards = boardQueryService.list(me.id)

                Then("내가 만든 보드만 생성순으로 반환한다") {
                    boards.map { it.id } shouldContainExactly listOf(personal.id, myShared.id)
                }
            }
        }

        Given("스티커 없이 생성 시각이 같은 보드 두 개가 있을 때") {
            val user = userRepository.saveTestUser()
            val first = boardRepository.save(user.id)
            val second = boardRepository.save(user.id)
            dslContext
                .update(BOARDS)
                .set(BOARDS.CREATED_AT, first.createdAt)
                .where(BOARDS.ID.eq(second.id))
                .execute()

            When("보드 목록 v2를 조회하면") {
                val boards = boardQueryService.listSummaries(user.id)

                Then("id 내림차순으로 정렬한다") {
                    boards.map { it.id } shouldContainExactly listOf(second.id, first.id)
                }
            }
        }

        Given("다른 사용자가 참여했던 보드가 삭제된 경우") {
            val owner = userRepository.saveTestUser()
            val member = userRepository.saveTestUser()
            val board = boardRepository.save(owner.id)
            dslContext.addBoardMember(board.id, member.id)
            boardRepository.softDelete(board.id, owner.id)

            When("참여자가 조회하면") {
                Then("BOARD-002 오류가 발생한다") {
                    shouldThrow<NotFoundException> {
                        boardQueryService.getDetail(board.id, member.id)
                    }.errorCode shouldBe BoardErrorCode.NOT_FOUND
                }
            }
        }
    })
