package com.github.nexters.ppotto.image.infrastructure

import com.github.nexters.ppotto.board.infrastructure.BoardRepository
import com.github.nexters.ppotto.image.domain.UploadStatus
import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.util.UUID

class ImageRepositoryTest(
    imageRepository: ImageRepository,
    boardRepository: BoardRepository,
    userRepository: UserRepository,
) : IntegrationTest({
        Given("Board가 등록된 상태에서 Image를 저장하면") {
            val board = boardRepository.save(userRepository.save().id)
            val uploadSessionId = UUID.randomUUID()
            val saved = imageRepository.save(board.id, uploadSessionId)

            When("저장된 아이디로 조회하면") {
                val found = imageRepository.findById(saved.id)

                Then("PENDING 상태의 Image를 반환한다") {
                    found?.id shouldBe saved.id
                    found?.boardId shouldBe board.id
                    found?.uploadSessionId shouldBe uploadSessionId
                    found?.uploadStatus shouldBe UploadStatus.PENDING
                }
            }

            When("Board 아이디로 조회하면") {
                val found = imageRepository.findByBoardId(board.id)

                Then("해당 Board의 Image 목록을 반환한다") {
                    found.map { it.id } shouldContainExactly listOf(saved.id)
                }
            }
        }

        Given("존재하지 않는 아이디로") {
            When("조회하면") {
                val found = imageRepository.findById(UUID.randomUUID())

                Then("null을 반환한다") {
                    found.shouldBeNull()
                }
            }
        }
    })
