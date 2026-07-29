package com.github.nexters.ppotto.global.storage

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.util.UUID

class ObjectKeyGeneratorTest :
    BehaviorSpec({
        val objectKeyGenerator = ObjectKeyGenerator()

        Given("네임스페이스와 세그먼트가 주어졌을 때") {
            val id = UUID.randomUUID()

            When("prefix를 생성하면") {
                Then("세그먼트를 슬래시로 이어 붙이고 마지막에 슬래시를 붙인다") {
                    objectKeyGenerator.prefix("photos", "abc") shouldBe "photos/abc/"
                }
            }

            When("extension을 지정해 키를 생성하면") {
                Then("prefix + id + .extension 형태를 반환한다") {
                    objectKeyGenerator.generate("photos", "abc", id = id, extension = "jpg") shouldBe
                        "photos/abc/$id.jpg"
                }
            }
        }
    })
