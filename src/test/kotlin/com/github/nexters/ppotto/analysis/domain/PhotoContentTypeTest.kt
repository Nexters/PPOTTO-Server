package com.github.nexters.ppotto.analysis.domain

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class PhotoContentTypeTest :
    BehaviorSpec({
        Given("지원하는 사진 형식이 주어졌을 때") {
            Then("전송 값과 오브젝트 키 확장자가 짝을 이룬다") {
                PhotoContentType.JPEG.mimeType shouldBe "image/jpeg"
                PhotoContentType.JPEG.extension shouldBe "jpg"

                PhotoContentType.PNG.mimeType shouldBe "image/png"
                PhotoContentType.PNG.extension shouldBe "png"

                PhotoContentType.HEIC.mimeType shouldBe "image/heic"
                PhotoContentType.HEIC.extension shouldBe "heic"

                PhotoContentType.WEBP.mimeType shouldBe "image/webp"
                PhotoContentType.WEBP.extension shouldBe "webp"
            }

            Then("지원 형식은 네 가지뿐이다") {
                PhotoContentType.entries.map(PhotoContentType::mimeType) shouldBe
                    listOf("image/jpeg", "image/png", "image/heic", "image/webp")
            }
        }
    })
