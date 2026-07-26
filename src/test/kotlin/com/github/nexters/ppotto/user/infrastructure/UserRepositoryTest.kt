package com.github.nexters.ppotto.user.infrastructure

import com.github.nexters.ppotto.support.IntegrationTest
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.util.UUID

class UserRepositoryTest(
    userRepository: UserRepository,
) : IntegrationTest({
        Given("User를 저장하면") {
            val saved = userRepository.save()

            When("저장된 아이디로 조회하면") {
                val found = userRepository.findById(saved.id)

                Then("저장한 User를 반환한다") {
                    found?.id shouldBe saved.id
                }
            }
        }

        Given("존재하지 않는 아이디로") {
            When("조회하면") {
                val found = userRepository.findById(UUID.randomUUID())

                Then("null을 반환한다") {
                    found.shouldBeNull()
                }
            }
        }
    })
