package com.github.nexters.ppotto.global.storage

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.util.UUID

class ObjectKeyGeneratorTest :
    BehaviorSpec({
        val objectKeyGenerator = ObjectKeyGenerator()
        val id = UUID.fromString("0198c0f0-0000-7000-8000-000000000000")

        Given("네임스페이스와 세그먼트가 주어졌을 때") {
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

        Given("세그먼트가 하나뿐일 때") {
            When("prefix를 생성하면") {
                Then("세그먼트 뒤에 슬래시만 붙인다") {
                    objectKeyGenerator.prefix("stickers") shouldBe "stickers/"
                }
            }

            When("키를 생성하면") {
                Then("한 단계 prefix 아래에 키를 만든다") {
                    objectKeyGenerator.generate("stickers", id = id, extension = "png") shouldBe "stickers/$id.png"
                }
            }
        }

        Given("세그먼트가 셋 이상일 때") {
            When("키를 생성하면") {
                Then("세그먼트 순서를 그대로 유지한 채 이어 붙인다") {
                    objectKeyGenerator.generate("photos", "analysis", "thumb", id = id, extension = "webp") shouldBe
                        "photos/analysis/thumb/$id.webp"
                }
            }
        }

        Given("세그먼트가 하나도 없을 때") {
            When("prefix를 생성하면") {
                Then("루트를 뜻하는 슬래시 하나만 반환한다") {
                    objectKeyGenerator.prefix() shouldBe "/"
                }
            }
        }
    })
