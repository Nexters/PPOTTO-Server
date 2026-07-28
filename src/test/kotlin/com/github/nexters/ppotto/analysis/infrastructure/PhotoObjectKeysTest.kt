package com.github.nexters.ppotto.analysis.infrastructure

import com.github.nexters.ppotto.analysis.domain.PhotoContentType
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import java.util.UUID

class PhotoObjectKeysTest :
    BehaviorSpec({
        Given("analysisId와 photoId가 주어졌을 때") {
            val analysisId = UUID.randomUUID()
            val photoId = UUID.randomUUID()

            When("keyFor를 호출하면") {
                Then("photos/{analysisId}/{photoId}.{ext} 형태의 키를 반환한다") {
                    PhotoObjectKeys.keyFor(analysisId, photoId, PhotoContentType.JPEG) shouldBe
                        "photos/$analysisId/$photoId.jpg"
                }
            }

            When("prefixFor를 호출하면") {
                Then("keyFor 결과의 접두사와 일치한다") {
                    val prefix = PhotoObjectKeys.prefixFor(analysisId)
                    prefix shouldBe "photos/$analysisId/"
                    PhotoObjectKeys.keyFor(analysisId, photoId, PhotoContentType.PNG) shouldStartWith prefix
                }
            }
        }
    })
