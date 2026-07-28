package com.github.nexters.ppotto.analysis.domain

import com.github.nexters.ppotto.global.storage.ObjectKeyGenerator
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import java.util.UUID

class PhotoObjectKeysTest :
    BehaviorSpec({
        val photoObjectKeys = PhotoObjectKeys(ObjectKeyGenerator())

        Given("analysisId와 photoId가 주어졌을 때") {
            val analysisId = UUID.randomUUID()
            val photoId = UUID.randomUUID()

            When("keyFor를 호출하면") {
                Then("photos/{analysisId}/{photoId}.{ext} 형태의 키를 반환한다") {
                    photoObjectKeys.keyFor(analysisId, photoId, PhotoContentType.JPEG) shouldBe
                        "photos/$analysisId/$photoId.jpg"
                }
            }

            When("prefixFor를 호출하면") {
                Then("keyFor 결과의 접두사와 일치한다") {
                    val prefix = photoObjectKeys.prefixFor(analysisId)
                    prefix shouldBe "photos/$analysisId/"
                    photoObjectKeys.keyFor(analysisId, photoId, PhotoContentType.PNG) shouldStartWith prefix
                }
            }
        }
    })
