package com.github.nexters.ppotto.board.infrastructure

import com.github.nexters.ppotto.support.IntegrationTest
import com.github.nexters.ppotto.support.saveTestUser
import com.github.nexters.ppotto.user.infrastructure.UserRepository
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.util.UUID

class BoardRepositoryTest(
    boardRepository: BoardRepository,
    userRepository: UserRepository,
) : IntegrationTest({
        Given("User가 등록된 상태에서 Board를 저장하면") {
            val user = userRepository.saveTestUser()
            val saved = boardRepository.save(user.id)

            When("저장된 아이디로 조회하면") {
                val found = boardRepository.findById(saved.id)

                Then("저장한 Board를 반환한다") {
                    found?.id shouldBe saved.id
                    found?.userId shouldBe user.id
                }
            }

            When("User 아이디로 조회하면") {
                val found = boardRepository.findByUserId(user.id)

                Then("해당 User의 Board 목록을 반환한다") {
                    found.map { it.id } shouldContainExactly listOf(saved.id)
                }
            }
        }

        Given("존재하지 않는 아이디로") {
            When("조회하면") {
                val found = boardRepository.findById(UUID.randomUUID())

                Then("null을 반환한다") {
                    found.shouldBeNull()
                }
            }
        }
    })
