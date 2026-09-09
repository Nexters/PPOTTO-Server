package com.github.nexters.ppotto.analysis.infrastructure

import com.github.nexters.ppotto.global.identifier.AnalysisId
import com.github.nexters.ppotto.global.identifier.PhotoId
import com.github.nexters.ppotto.global.identifier.StickerId
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldStartWith
import java.util.UUID

class StickerObjectKeysTest :
    BehaviorSpec({
        val analysisId = AnalysisId(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"))
        val otherAnalysisId = AnalysisId(UUID.fromString("550e8400-e29b-41d4-a716-446655440099"))
        val sourcePhotoId = PhotoId(UUID.fromString("550e8400-e29b-41d4-a716-446655440001"))
        val otherSourcePhotoId = PhotoId(UUID.fromString("550e8400-e29b-41d4-a716-446655440099"))

        Given("같은 분석과 테마 인덱스, 같은 원본 사진으로") {
            When("초기 분석 스티커 키를 두 번 만들면") {
                val first = StickerObjectKeys.keyFor(analysisId, 0, sourcePhotoId)
                val second = StickerObjectKeys.keyFor(analysisId, 0, sourcePhotoId)

                Then("같은 키를 반환해 같은 오브젝트를 가리킨다") {
                    first shouldBe second
                }
            }
        }

        Given("테마 인덱스와 원본 사진은 같고 분석만 다를 때") {
            When("초기 분석 스티커 키를 각각 만들면") {
                val first = StickerObjectKeys.keyFor(analysisId, 0, sourcePhotoId)
                val second = StickerObjectKeys.keyFor(otherAnalysisId, 0, sourcePhotoId)

                Then("분석별로 키가 갈라져 서로 덮어쓰지 않는다") {
                    first shouldNotBe second
                }
            }
        }

        Given("분석과 원본 사진은 같고 테마 인덱스만 다를 때") {
            When("초기 분석 스티커 키를 각각 만들면") {
                val first = StickerObjectKeys.keyFor(analysisId, 0, sourcePhotoId)
                val second = StickerObjectKeys.keyFor(analysisId, 1, sourcePhotoId)

                Then("테마별로 키가 갈라져 서로 덮어쓰지 않는다") {
                    first shouldNotBe second
                }
            }
        }

        Given("분석과 테마 인덱스는 같고 원본 사진만 다를 때") {
            When("초기 분석 스티커 키를 각각 만들면") {
                val first = StickerObjectKeys.keyFor(analysisId, 0, sourcePhotoId)
                val second = StickerObjectKeys.keyFor(analysisId, 0, otherSourcePhotoId)

                Then("원본 사진별로 키가 갈라져 서로 덮어쓰지 않는다") {
                    first shouldNotBe second
                }
            }
        }

        Given("분석 스티커 오브젝트 키 형식을 확인할 때") {
            When("테마 인덱스 2로 키를 만들면") {
                val key = StickerObjectKeys.keyFor(analysisId, 2, sourcePhotoId)

                Then("stickers/{analysisId}/{themeIndex}-{sourcePhotoId}.png 형식을 따른다") {
                    key shouldBe "stickers/$analysisId/2-$sourcePhotoId.png"
                    key shouldStartWith "stickers/"
                    key shouldContain "$analysisId"
                    key shouldEndWith ".png"
                }
            }
        }

        Given("같은 스티커와 원본 사진, 같은 재생성 식별자로") {
            val stickerId = StickerId(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"))
            val regenerationId = UUID.fromString("550e8400-e29b-41d4-a716-446655440002")

            When("재생성 키를 두 번 만들면") {
                val first = StickerObjectKeys.keyForRegeneration(stickerId, sourcePhotoId, regenerationId)
                val second = StickerObjectKeys.keyForRegeneration(stickerId, sourcePhotoId, regenerationId)

                Then("같은 키를 반환해 같은 오브젝트를 가리킨다") {
                    first shouldBe second
                }
            }
        }

        Given("스티커와 원본 사진은 같고 재생성 식별자만 다를 때") {
            val stickerId = StickerId(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"))
            val firstRegenerationId = UUID.fromString("550e8400-e29b-41d4-a716-446655440002")
            val secondRegenerationId = UUID.fromString("550e8400-e29b-41d4-a716-446655440003")

            When("재생성 키를 각각 만들면") {
                val first = StickerObjectKeys.keyForRegeneration(stickerId, sourcePhotoId, firstRegenerationId)
                val second = StickerObjectKeys.keyForRegeneration(stickerId, sourcePhotoId, secondRegenerationId)

                Then("재생성마다 키가 갈라져 이전 업로드를 덮어쓰지 않는다") {
                    first shouldNotBe second
                }
            }
        }

        Given("재생성 스티커 오브젝트 키 형식을 확인할 때") {
            val stickerId = StickerId(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"))
            val regenerationId = UUID.fromString("550e8400-e29b-41d4-a716-446655440002")

            When("재생성 키를 만들면") {
                val key = StickerObjectKeys.keyForRegeneration(stickerId, sourcePhotoId, regenerationId)

                Then("stickers/{stickerId}/{sourcePhotoId}-{regenerationId}.png 형식을 따른다") {
                    key shouldBe "stickers/$stickerId/$sourcePhotoId-$regenerationId.png"
                }
            }
        }
    })
