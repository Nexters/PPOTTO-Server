package com.github.nexters.ppotto.analysis.infrastructure

import com.github.nexters.ppotto.analysis.domain.PhotoContentType
import com.github.nexters.ppotto.analysis.domain.UploadStatus
import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.rawId
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.time.temporal.ChronoUnit

class PhotoRepositoryTest(
    photoRepository: PhotoRepository,
    analysisRepository: AnalysisRepository,
    boardRepository: BoardRepository,
    userRepository: UserRepository,
) : IntegrationTest({
        Given("Analysis가 등록된 상태에서 여러 Photo를 배치로 저장하면") {
            val board = boardRepository.save(userRepository.saveTestUser().rawId)
            val analysis = analysisRepository.save(board.userId, board.id)
            val items =
                listOf(
                    PhotoCreate(PhotoContentType.JPEG, Instant.parse("2026-07-01T00:00:00Z")),
                    PhotoCreate(PhotoContentType.PNG, Instant.parse("2026-07-02T00:00:00Z")),
                )

            val saved = photoRepository.saveAll(analysis.id, board.id, items)

            When("입력 순서와 저장된 Photo를 비교하면") {
                Then("요청 순서와 동일한 순서로 반환된다") {
                    saved shouldHaveSize 2
                    saved.map { it.contentType } shouldBe listOf(PhotoContentType.JPEG, PhotoContentType.PNG)
                    saved.map { it.takenAt } shouldBe items.map { it.takenAt }
                }
            }

            When("analysisId로 PENDING 상태 Photo를 조회하면") {
                val found = photoRepository.findPendingByAnalysisId(analysis.id)

                Then("저장된 Photo 전부를 반환한다") {
                    found.map { it.id } shouldContainExactlyInAnyOrder saved.map { it.id }
                    found.forEach { it.uploadStatus shouldBe UploadStatus.PENDING }
                }
            }
        }

        Given("빈 아이템 목록으로 배치 저장을 호출하면") {
            val board = boardRepository.save(userRepository.saveTestUser().rawId)
            val analysis = analysisRepository.save(board.userId, board.id)

            When("저장을 수행하면") {
                val saved = photoRepository.saveAll(analysis.id, board.id, emptyList())

                Then("빈 목록을 반환한다") {
                    saved.shouldBeEmpty()
                }
            }
        }

        Given("PENDING 상태의 Photo가 저장된 상태에서") {
            val board = boardRepository.save(userRepository.saveTestUser().rawId)
            val analysis = analysisRepository.save(board.userId, board.id)

            When("기대 상태(PENDING)를 걸고 COMPLETED로 배치 갱신하면") {
                val saved =
                    photoRepository.saveAll(analysis.id, board.id, listOf(PhotoCreate(PhotoContentType.JPEG, Instant.now())))
                val microsecondPrecisionUploadedAt = Instant.now().truncatedTo(ChronoUnit.MICROS)
                val updated =
                    photoRepository.markCompletedBatch(
                        saved.associate { it.id to microsecondPrecisionUploadedAt },
                    )

                Then("갱신된 Photo를 반환한다") {
                    updated shouldHaveSize 1
                    updated.first().uploadStatus shouldBe UploadStatus.COMPLETED
                    updated.first().uploadedAt shouldBe microsecondPrecisionUploadedAt
                }
            }

            When("이미 다른 상태로 바뀐 뒤 다시 기대 상태(PENDING)를 걸고 갱신하면") {
                val saved =
                    photoRepository.saveAll(analysis.id, board.id, listOf(PhotoCreate(PhotoContentType.JPEG, Instant.now())))
                photoRepository.markCompletedBatch(saved.associate { it.id to Instant.now() })
                photoRepository.markFailedBatch(saved.map { it.id })

                Then("기존 COMPLETED를 FAILED로 갱신하지 않는다 (PENDING이 아니므로)") {
                    photoRepository
                        .findAllByAnalysisId(analysis.id)
                        .first()
                        .uploadStatus shouldBe UploadStatus.COMPLETED
                }
            }

            When("빈 id 목록으로 갱신하면") {
                val updated = photoRepository.markCompletedBatch(emptyMap())

                Then("빈 목록을 반환한다") {
                    updated.shouldBeEmpty()
                }
            }
        }

        Given("서로 다른 분석에 완료 사진과 대기 사진이 저장된 상태에서") {
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
            val otherBoard = boardRepository.save(userRepository.saveTestUser().rawId)
            val otherAnalysis = analysisRepository.save(otherBoard.userId, otherBoard.id)
            val otherPhoto =
                photoRepository
                    .saveAll(
                        otherAnalysis.id,
                        otherBoard.id,
                        listOf(PhotoCreate(PhotoContentType.JPEG, Instant.parse("2026-07-03T00:00:00Z"))),
                    ).single()
            photoRepository.markCompletedBatch(mapOf(otherPhoto.id to Instant.now()))

            When("분석 스코프로 완료 사진을 조회하면") {
                val found =
                    photoRepository.findCompletedByIds(
                        analysis.id,
                        board.id,
                        listOf(completedPhoto.id, pendingPhoto.id, otherPhoto.id),
                    )

                Then("해당 분석의 완료 사진만 반환하고 대기 사진과 다른 분석 사진은 제외한다") {
                    found.map { it.id } shouldContainExactlyInAnyOrder listOf(completedPhoto.id)
                }
            }

            When("대기 사진이 섞인 목록으로 소유 수를 세면") {
                val count =
                    photoRepository.countOwnedByAnalysis(
                        analysis.id,
                        board.id,
                        listOf(completedPhoto.id, pendingPhoto.id),
                    )

                Then("완료 사진만 소유로 인정한다") {
                    count shouldBe 1
                }
            }

            When("다른 분석의 완료 사진 id로 소유 수를 세면") {
                val count = photoRepository.countOwnedByAnalysis(analysis.id, board.id, listOf(otherPhoto.id))

                Then("분석 스코프 밖 사진은 세지 않는다") {
                    count shouldBe 0
                }
            }
        }
    })
