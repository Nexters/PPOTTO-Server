package com.github.nexters.ppotto.sticker.application

import com.github.nexters.ppotto.analysis.domain.PhotoContentType
import com.github.nexters.ppotto.analysis.infrastructure.AnalysisRepository
import com.github.nexters.ppotto.analysis.infrastructure.PhotoCreate
import com.github.nexters.ppotto.analysis.infrastructure.PhotoRepository
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.global.error.NotFoundException
import com.github.nexters.ppotto.sticker.application.port.StickerImageStoragePort
import com.github.nexters.ppotto.sticker.domain.RecapCommentCreation
import com.github.nexters.ppotto.sticker.domain.StickerErrorCode
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
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.Instant
import java.util.UUID

class StickerQueryServiceTest(
    service: StickerQueryService,
    recapShareService: RecapShareService,
    stickerRepository: StickerRepository,
    stickerCommandRepository: StickerCommandRepository,
    stickerRecapRepository: StickerRecapRepository,
    stickerAccessService: StickerAccessService,
    photoRepository: PhotoRepository,
    analysisRepository: AnalysisRepository,
    boardRepository: BoardRepository,
    userRepository: UserRepository,
) : IntegrationTest({
        Given("이미지 스티커와 리캡 데이터가 등록된 상태에서") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
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
                    imageStickerCreation(
                        sourcePhotoId = photos.first().id,
                        imageKey = "stickers/recap.png",
                        title = "리캡",
                        summary = "웃기고 귀여우면 일단 주워요",
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
                    result.sticker.isNew shouldBe true
                    result.summary shouldBe "웃기고 귀여우면 일단 주워요"
                    result.comments.map { it.content } shouldContainExactly listOf("말풍선", "키워드")
                    result.photos.map { it.id } shouldContainExactly photos.reversed().map { it.id }
                }

                Then("연사 그룹이 아니므로 isGroup은 false이고 groupId, groupPhotos는 비어있다") {
                    result.photos.forEach {
                        it.isGroup shouldBe false
                        it.groupId shouldBe null
                        it.groupPhotos shouldBe emptyList()
                    }
                }

                Then("말풍선은 좌표를 갖고 키워드 칩은 좌표가 null이다") {
                    val (bubble, chip) = result.comments

                    bubble.posX shouldBe 3.0
                    bubble.posY shouldBe 4.0
                    chip.posX shouldBe null
                    chip.posY shouldBe null
                }
            }

            When("공유한 적 없는 리캡을 소유자가 조회하면") {
                val result = service.getRecap(board.userId, sticker.id)

                Then("공유 상태는 내려오지 않는다") {
                    result.share shouldBe null
                }
            }

            When("다른 사용자가 리캡 상세를 조회하면") {
                val otherUser = userRepository.saveTestUser()
                val exception = shouldThrow<NotFoundException> { service.getRecap(otherUser.id, sticker.id) }

                Then("STICKER-001 오류가 발생한다") {
                    exception.errorCode shouldBe StickerErrorCode.STICKER_NOT_FOUND
                }
            }

            When("없는 공유 토큰으로 조회하면") {
                val exception = shouldThrow<NotFoundException> { service.getSharedRecap(UUID.randomUUID().toString()) }

                Then("STICKER-001 오류가 발생한다") {
                    exception.errorCode shouldBe StickerErrorCode.STICKER_NOT_FOUND
                }
            }

            When("서명 시점의 트랜잭션 상태를 확인하면") {
                val signingPort = TransactionObservingStickerImageStorage()
                StickerQueryService(
                    stickerRepository,
                    stickerRecapRepository,
                    stickerAccessService,
                    emptyList(),
                    listOf(signingPort),
                ).getByBoardId(board.id)

                Then("RSA 서명은 열린 트랜잭션 없이 실행된다") {
                    signingPort.transactionActiveAtSigning shouldBe false
                }
            }

            When("리캡 사진의 읽기 URL을 확인하면") {
                val result = service.getRecap(board.userId, sticker.id)

                Then("각 사진의 오브젝트 키로 발급한 읽기 URL을 반환한다") {
                    result.photos.forEach {
                        it.imageUrl.shouldContain("photos/${analysis.id}/${it.id}.jpg")
                    }
                }
            }
        }

        Given("리캡이 사진 없이 공유된 상태에서") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val analysis = analysisRepository.save(board.userId, board.id)
            val photos =
                photoRepository.saveAll(
                    analysis.id,
                    board.id,
                    listOf(PhotoCreate(PhotoContentType.JPEG, Instant.parse("2026-07-01T00:00:00Z"))),
                )
            photoRepository.markCompletedBatch(photos.associate { it.id to Instant.now() })
            val sticker =
                stickerRepository.save(
                    analysis.id,
                    board.id,
                    imageStickerCreation(
                        sourcePhotoId = photos.first().id,
                        imageKey = "stickers/recap.png",
                        title = "리캡",
                        summary = "웃기고 귀여우면 일단 주워요",
                    ),
                )
            stickerRecapRepository.savePhotos(sticker.id, photos.map { it.id })
            stickerRecapRepository.saveComments(sticker.id, listOf(RecapCommentCreation("키워드", null, null)))
            val shareToken = recapShareService.share(board.userId, sticker.id, includePhotos = false)

            When("공유 토큰으로 조회하면") {
                val result = service.getSharedRecap(shareToken)

                Then("리캡 내용은 반환하되 사진은 한 장도 내려주지 않는다") {
                    result.sticker.id shouldBe sticker.id
                    result.comments.map { it.content } shouldContainExactly listOf("키워드")
                    result.photos.shouldBeEmpty()
                }

                Then("남의 읽음 상태가 새겨나가지 않게 isNew는 항상 false다") {
                    result.sticker.isNew shouldBe false
                }
            }

            When("사진을 포함하도록 다시 공유하면") {
                val reshareToken = recapShareService.share(board.userId, sticker.id, includePhotos = true)

                Then("이미 보낸 링크가 끊기지 않게 같은 토큰을 유지한다") {
                    reshareToken shouldBe shareToken
                }

                Then("같은 토큰으로 조회하면 사진이 채워진다") {
                    service
                        .getSharedRecap(shareToken)
                        .photos
                        .map { it.id } shouldContainExactly photos.map { it.id }
                }
            }

            When("소유자가 리캡 상세를 조회하면") {
                val result = service.getRecap(board.userId, sticker.id)

                Then("공유 중이라는 사실과 사진 포함 여부가 함께 내려온다") {
                    result.share shouldBe RecapShareResult(photos = false)
                }
            }

            When("공유 토큰으로 조회하면 (남의 눈)") {
                val result = service.getSharedRecap(shareToken)

                Then("공유 상태는 소유자만 알 수 있으므로 내려오지 않는다") {
                    result.share shouldBe null
                }
            }

            When("공유를 해제하면") {
                recapShareService.unshare(board.userId, sticker.id)
                val exception = shouldThrow<NotFoundException> { service.getSharedRecap(shareToken) }

                Then("STICKER-001 오류가 발생한다") {
                    exception.errorCode shouldBe StickerErrorCode.STICKER_NOT_FOUND
                }
            }

            When("공유된 스티커가 삭제되면") {
                val deletedShareToken = recapShareService.share(board.userId, sticker.id, includePhotos = true)
                stickerCommandRepository.softDelete(sticker.id, Instant.now())
                val exception = shouldThrow<NotFoundException> { service.getSharedRecap(deletedShareToken) }

                Then("STICKER-001 오류가 발생한다") {
                    exception.errorCode shouldBe StickerErrorCode.STICKER_NOT_FOUND
                }
            }
        }

        Given("연사 그룹 사진이 리캡에 연결된 상태에서") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
            val analysis = analysisRepository.save(board.userId, board.id)
            val burstGroupId = UUID.randomUUID()
            val photos =
                photoRepository.saveAll(
                    analysis.id,
                    board.id,
                    listOf(
                        PhotoCreate(
                            PhotoContentType.JPEG,
                            Instant.parse("2026-07-01T00:00:00Z"),
                            burstGroupId = burstGroupId,
                            isRepresentative = true,
                        ),
                        PhotoCreate(
                            PhotoContentType.JPEG,
                            Instant.parse("2026-07-01T00:00:01Z"),
                            burstGroupId = burstGroupId,
                            isRepresentative = false,
                        ),
                    ),
                )
            photoRepository.markCompletedBatch(photos.associate { it.id to Instant.now() })
            val representativePhoto = photos.single { it.isRepresentative }
            val sticker =
                stickerRepository.save(
                    analysis.id,
                    board.id,
                    imageStickerCreation(
                        sourcePhotoId = representativePhoto.id,
                        imageKey = "stickers/recap.png",
                        title = "리캡",
                        summary = "웃기고 귀여우면 일단 주워요",
                    ),
                )
            stickerRecapRepository.savePhotos(sticker.id, photos.map { it.id })

            When("리캡 상세를 조회하면") {
                val result = service.getRecap(board.userId, sticker.id)

                Then("연사 그룹의 대표 사진만 반환한다") {
                    result.photos.map { it.id } shouldContainExactly listOf(representativePhoto.id)
                }

                Then("대표 사진에 그룹 여부/ID와 나머지 사진 목록이 채워진다") {
                    val nonRepresentativePhoto = photos.single { !it.isRepresentative }
                    val photo = result.photos.single()

                    photo.isGroup shouldBe true
                    photo.groupId shouldBe burstGroupId
                    photo.groupPhotos.map { it.id } shouldContainExactly listOf(nonRepresentativePhoto.id)
                }
            }
        }

        Given("업로드가 완료되지 않은 사진이 리캡에 연결된 상태에서") {
            val board = boardRepository.save(userRepository.saveTestUser().id)
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
                    imageStickerCreation(
                        sourcePhotoId = pendingPhoto.id,
                        imageKey = "stickers/recap.png",
                        title = "리캡",
                        summary = "웃기고 귀여우면 일단 주워요",
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

        Given("스티커 조회 서비스의 트랜잭션 경계를 확인할 때") {
            When("선언된 트랜잭션 애노테이션을 모으면") {
                val declared =
                    StickerQueryService::class.java.declaredMethods
                        .filter { it.isAnnotationPresent(Transactional::class.java) }
                        .map { it.name } +
                        listOfNotNull(
                            StickerQueryService::class.java
                                .getAnnotation(Transactional::class.java)
                                ?.let { "class" },
                        )

                Then("읽기 경로가 커넥션을 물지 않도록 하나도 선언되어 있지 않다") {
                    declared.shouldBeEmpty()
                }
            }
        }
    })

private class TransactionObservingStickerImageStorage : StickerImageStoragePort {
    var transactionActiveAtSigning: Boolean? = null
        private set

    override fun issueReadUrls(imageKeys: Collection<String>): Map<String, String> {
        transactionActiveAtSigning = TransactionSynchronizationManager.isActualTransactionActive()
        return imageKeys.associateWith { "https://example.test/$it" }
    }

    override fun deleteAll(imageKeys: Collection<String>) = Unit
}
