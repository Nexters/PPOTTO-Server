package com.github.nexters.ppotto.image.application

import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.global.error.ConflictException
import com.github.nexters.ppotto.global.error.InvalidInputException
import com.github.nexters.ppotto.global.error.NotFoundException
import com.github.nexters.ppotto.image.domain.UploadStatus
import com.github.nexters.ppotto.image.support.ImageUploadTestConfig
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.springframework.context.annotation.Import
import java.util.UUID

@Import(ImageUploadTestConfig::class)
class ImageUploadServiceTest(
    imageUploadService: ImageUploadService,
    boardRepository: BoardRepository,
    userRepository: UserRepository,
) : IntegrationTest({
        Given("Board가 등록된 상태에서 여러 파일명으로 signed URL 발급을 요청하면") {
            val board = boardRepository.save(userRepository.save().id)
            val fileNames = listOf("IMG_0001.jpg", "IMG_0002.png")

            val result = imageUploadService.issueUploadUrls(board.id, fileNames)

            Then("파일명 개수만큼 Image가 생성되고 같은 uploadSessionId를 공유한다") {
                val imageIdList = result.items.map { it.imageId }
                val imageIds = imageIdList.toSet()
                val resultFileNames = result.items.map { it.fileName }

                result.items shouldHaveSize fileNames.size
                imageIds shouldHaveSize fileNames.size
                resultFileNames shouldContainExactlyInAnyOrder fileNames
            }

            Then("응답의 signed URL은 발급된 issuer 구현체가 만든 값이다") {
                result.items.forEach { it.uploadUrl shouldBe "https://fake-signed-url/images/${it.imageId}/${it.fileName}" }
            }
        }

        Given("지원하지 않는 확장자가 섞여 있으면") {
            val board = boardRepository.save(userRepository.save().id)

            When("signed URL 발급을 요청하면") {
                Then("InvalidInputException이 발생한다") {
                    shouldThrow<InvalidInputException> {
                        imageUploadService.issueUploadUrls(board.id, listOf("IMG_0001.jpg", "malware.exe"))
                    }
                }
            }
        }

        Given("존재하지 않는 boardId로") {
            When("signed URL 발급을 요청하면") {
                Then("NotFoundException이 발생한다") {
                    shouldThrow<NotFoundException> {
                        imageUploadService.issueUploadUrls(UUID.randomUUID(), listOf("IMG_0001.jpg"))
                    }
                }
            }
        }

        Given("PENDING 상태의 Image가 발급된 상태에서") {
            val board = boardRepository.save(userRepository.save().id)

            When("COMPLETED로 업로드 완료를 보고하면") {
                val imageId =
                    imageUploadService
                        .issueUploadUrls(board.id, listOf("IMG_0001.jpg"))
                        .items
                        .single()
                        .imageId
                val updated = imageUploadService.completeUpload(board.id, imageId, UploadStatus.COMPLETED)

                Then("Image의 uploadStatus가 COMPLETED로 바뀐다") {
                    updated.uploadStatus shouldBe UploadStatus.COMPLETED
                }
            }

            When("같은 Image에 대해 완료 보고를 두 번 하면") {
                val imageId =
                    imageUploadService
                        .issueUploadUrls(board.id, listOf("IMG_0001.jpg"))
                        .items
                        .single()
                        .imageId
                imageUploadService.completeUpload(board.id, imageId, UploadStatus.COMPLETED)

                Then("두 번째 요청은 ConflictException이 발생한다") {
                    shouldThrow<ConflictException> {
                        imageUploadService.completeUpload(board.id, imageId, UploadStatus.FAILED)
                    }
                }
            }

            When("다른 board의 id로 완료를 보고하면") {
                val imageId =
                    imageUploadService
                        .issueUploadUrls(board.id, listOf("IMG_0001.jpg"))
                        .items
                        .single()
                        .imageId
                val otherBoard = boardRepository.save(userRepository.save().id)

                Then("NotFoundException이 발생한다") {
                    shouldThrow<NotFoundException> {
                        imageUploadService.completeUpload(otherBoard.id, imageId, UploadStatus.COMPLETED)
                    }
                }
            }
        }

        Given("존재하지 않는 imageId로") {
            val board = boardRepository.save(userRepository.save().id)

            When("완료를 보고하면") {
                Then("NotFoundException이 발생한다") {
                    shouldThrow<NotFoundException> {
                        imageUploadService.completeUpload(board.id, UUID.randomUUID(), UploadStatus.COMPLETED)
                    }
                }
            }
        }
    })
