package com.github.nexters.ppotto.analysis.domain

import com.github.nexters.ppotto.global.error.InvalidInputException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class PhotoContentTypeTest :
    BehaviorSpec({
        Given("지원하는 mime-type 문자열이 주어졌을 때") {
            When("fromMimeType으로 변환하면") {
                Then("각 mime-type에 대응하는 enum과 확장자를 반환한다") {
                    PhotoContentType.fromMimeType("image/jpeg") shouldBe PhotoContentType.JPEG
                    PhotoContentType.fromMimeType("image/png") shouldBe PhotoContentType.PNG
                    PhotoContentType.fromMimeType("image/heic") shouldBe PhotoContentType.HEIC

                    PhotoContentType.JPEG.extension shouldBe "jpg"
                    PhotoContentType.PNG.extension shouldBe "png"
                    PhotoContentType.HEIC.extension shouldBe "heic"
                }
            }
        }

        Given("지원하지 않는 mime-type 문자열이 주어졌을 때") {
            When("fromMimeType으로 변환하면") {
                Then("InvalidInputException이 발생한다") {
                    shouldThrow<InvalidInputException> {
                        PhotoContentType.fromMimeType("image/gif")
                    }
                }
            }
        }
    })
