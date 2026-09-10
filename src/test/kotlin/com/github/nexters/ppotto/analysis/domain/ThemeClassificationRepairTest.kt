package com.github.nexters.ppotto.analysis.domain

import com.github.nexters.ppotto.global.error.BusinessException
import com.github.nexters.ppotto.global.identifier.PhotoId
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.util.UUID

private fun photoId(): PhotoId = PhotoId(UUID.randomUUID())

private fun theme(
    name: String,
    categorizedPhotoIds: List<PhotoId>,
    stickerSourcePhotoId: PhotoId = categorizedPhotoIds.first(),
) = ThemeClassification(
    theme = name,
    categorizedPhotoIds = categorizedPhotoIds,
    recap = RecapContent(badge = "뱃지", text = "한 줄 요약"),
    stickerTargetSubject = "피사체",
    stickerSourcePhotoId = stickerSourcePhotoId,
    stickerMainColor = "#FF6B6B",
    comments = emptyList(),
)

class ThemeClassificationRepairTest :
    BehaviorSpec({
        val photo1 = photoId()
        val photo2 = photoId()
        val photo3 = photoId()

        Given("중복 없이 분류된 테마들이 주어졌을 때") {
            val classifications =
                listOf(
                    theme("가을", listOf(photo1, photo2)),
                    theme("겨울", listOf(photo3)),
                )

            When("교정하면") {
                val repaired = classifications.withoutCrossThemeDuplicates()

                Then("아무것도 바뀌지 않는다") {
                    repaired shouldBe classifications
                }
            }
        }

        Given("사진 1장이 두 테마에 중복 분류되었을 때") {
            val classifications =
                listOf(
                    theme("가을", listOf(photo1, photo2)),
                    theme("겨울", listOf(photo2, photo3), stickerSourcePhotoId = photo3),
                )

            When("교정하면") {
                val repaired = classifications.withoutCrossThemeDuplicates()

                Then("먼저 나온 테마가 그 사진을 갖는다") {
                    repaired[0].categorizedPhotoIds shouldContainExactly listOf(photo1, photo2)
                }

                Then("뒤 테마에서는 그 사진만 빠지고 나머지는 남는다") {
                    repaired[1].categorizedPhotoIds shouldContainExactly listOf(photo3)
                }

                Then("교정 결과는 검증을 통과해 분석이 계속된다") {
                    shouldNotThrowAny {
                        ThemeClassificationValidator.validate(repaired, setOf(photo1, photo2, photo3))
                    }
                }
            }
        }

        Given("중복된 사진을 뒤 테마가 스티커 소스로 쓰고 있을 때") {
            val classifications =
                listOf(
                    theme("가을", listOf(photo1, photo2)),
                    theme("겨울", listOf(photo2, photo3), stickerSourcePhotoId = photo2),
                )

            When("교정하면") {
                val repaired = classifications.withoutCrossThemeDuplicates()

                Then("소스로 쓰는 뒤 테마가 그 사진을 갖는다") {
                    repaired[1].categorizedPhotoIds shouldContainExactly listOf(photo2, photo3)
                }

                Then("먼저 나온 테마에서 빠진다") {
                    repaired[0].categorizedPhotoIds shouldContainExactly listOf(photo1)
                }

                Then("소스 사진이 제 테마에 남아 검증을 통과한다") {
                    shouldNotThrowAny {
                        ThemeClassificationValidator.validate(repaired, setOf(photo1, photo2, photo3))
                    }
                }
            }
        }

        Given("두 테마가 같은 사진을 스티커 소스로 지목했을 때") {
            val classifications =
                listOf(
                    theme("가을", listOf(photo1, photo2), stickerSourcePhotoId = photo2),
                    theme("겨울", listOf(photo2, photo3), stickerSourcePhotoId = photo2),
                )

            When("교정 후 검증하면") {
                val repaired = classifications.withoutCrossThemeDuplicates()
                val exception =
                    shouldThrow<BusinessException> {
                        ThemeClassificationValidator.validate(repaired, setOf(photo1, photo2, photo3))
                    }

                Then("교정할 수 없는 응답이므로 ANALYSIS-007로 실패한다") {
                    exception.errorCode shouldBe AnalysisErrorCode.INVALID_GEMINI_RESPONSE
                }
            }
        }

        Given("테마의 사진이 전부 앞 테마와 중복일 때") {
            val classifications =
                listOf(
                    theme("가을", listOf(photo1, photo2)),
                    theme("겨울", listOf(photo1, photo2)),
                )

            When("교정 후 검증하면") {
                val repaired = classifications.withoutCrossThemeDuplicates()
                val exception =
                    shouldThrow<BusinessException> {
                        ThemeClassificationValidator.validate(repaired, setOf(photo1, photo2))
                    }

                Then("빈 테마가 남으므로 ANALYSIS-007로 실패한다") {
                    exception.errorCode shouldBe AnalysisErrorCode.INVALID_GEMINI_RESPONSE
                }
            }
        }
    })
