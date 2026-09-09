package com.github.nexters.ppotto.analysis.domain

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class PhotoContentTypeTest :
    BehaviorSpec({
        Given("저장용 사진 형식 enum이 주어졌을 때") {
            When("각 형식의 전송 값과 오브젝트 키 확장자를 확인하면") {
                Then("전송 값과 확장자가 형식마다 짝을 이룬다") {
                    PhotoContentType.JPEG.mimeType shouldBe "image/jpeg"
                    PhotoContentType.JPEG.extension shouldBe "jpg"

                    PhotoContentType.PNG.mimeType shouldBe "image/png"
                    PhotoContentType.PNG.extension shouldBe "png"

                    PhotoContentType.HEIC.mimeType shouldBe "image/heic"
                    PhotoContentType.HEIC.extension shouldBe "heic"

                    PhotoContentType.WEBP.mimeType shouldBe "image/webp"
                    PhotoContentType.WEBP.extension shouldBe "webp"
                }
            }

            When("지원 형식 목록을 확인하면") {
                Then("네 가지 형식만 존재한다") {
                    PhotoContentType.entries.map(PhotoContentType::mimeType) shouldBe
                        listOf("image/jpeg", "image/png", "image/heic", "image/webp")
                }
            }
        }
    })
