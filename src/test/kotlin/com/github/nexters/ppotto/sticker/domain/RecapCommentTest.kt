package com.github.nexters.ppotto.sticker.domain

import com.github.nexters.ppotto.global.error.InvalidInputException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class RecapCommentTest :
    BehaviorSpec({
        Given("리캡 코멘트 생성값이 주어졌을 때") {
            When("좌표를 모두 채우면") {
                val creation = RecapCommentCreation("야옹~", -96.0, -150.0)

                Then("스티커 주변 말풍선으로 생성된다") {
                    creation.posX shouldBe -96.0
                    creation.posY shouldBe -150.0
                }
            }

            When("좌표를 모두 비우면") {
                val creation = RecapCommentCreation("냥집사", null, null)

                Then("하단 키워드 칩으로 생성된다") {
                    creation.posX shouldBe null
                    creation.posY shouldBe null
                }
            }

            When("좌표를 한쪽만 채우면") {
                Then("잘못된 입력 예외를 던진다") {
                    shouldThrow<InvalidInputException> { RecapCommentCreation("야옹~", -96.0, null) }
                    shouldThrow<InvalidInputException> { RecapCommentCreation("야옹~", null, -150.0) }
                }
            }

            When("공백뿐인 문구로 생성하면") {
                Then("잘못된 입력 예외를 던진다") {
                    shouldThrow<InvalidInputException> { RecapCommentCreation(" ", null, null) }
                }
            }
        }
    })
