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

class RepairedThemeClassificationsTest :
    BehaviorSpec({
        Given("최대 개수보다 한 개 많은 테마를 Gemini가 반환했을 때") {
            val classifications =
                (0..ThemeClassificationValidator.MAX_THEME_COUNT).map { index ->
                    theme("테마$index", listOf(photoId()))
                }

            When("최대 개수로 자르면") {
                val capped = classifications.cappedToMaxThemeCount()

                Then("앞에서부터 최대 개수만큼만 남고 분석은 계속된다") {
                    capped.map { it.theme } shouldContainExactly
                        (0 until ThemeClassificationValidator.MAX_THEME_COUNT).map { "테마$it" }
                }

                Then("잘라낸 결과는 검증을 통과한다") {
                    shouldNotThrowAny {
                        ThemeClassificationValidator.validate(capped, capped.flatMap { it.categorizedPhotoIds }.toSet())
                    }
                }
            }
        }

        Given("최대 개수 이하의 테마가 주어졌을 때") {
            val classifications = listOf(theme("가을", listOf(photoId())))

            When("최대 개수로 자르면") {
                val capped = classifications.cappedToMaxThemeCount()

                Then("원본 목록을 그대로 돌려준다") {
                    capped shouldBe classifications
                }
            }
        }

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
                val repaired = classifications.repairCrossThemeDuplicates().classifications

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
                val repaired = classifications.repairCrossThemeDuplicates().classifications

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
                val repaired = classifications.repairCrossThemeDuplicates().classifications

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

        Given("사진 1장이 세 테마에 중복 분류되었을 때") {
            val classifications =
                listOf(
                    theme("가을", listOf(photo1, photo2)),
                    theme("겨울", listOf(photo2, photo3), stickerSourcePhotoId = photo3),
                    theme("봄", listOf(photo2), stickerSourcePhotoId = photo2),
                )

            When("교정하면") {
                val repair = classifications.repairCrossThemeDuplicates()

                Then("지운 사진 수를 함께 돌려준다") {
                    repair.removedPhotoCount shouldBe 2
                }
            }
        }

        Given("중복이 없을 때") {
            val classifications = listOf(theme("가을", listOf(photo1, photo2)))

            When("교정하면") {
                val repair = classifications.repairCrossThemeDuplicates()

                Then("지운 사진이 없다고 보고한다") {
                    repair.removedPhotoCount shouldBe 0
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
                val repaired = classifications.repairCrossThemeDuplicates().classifications
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
                val repaired = classifications.repairCrossThemeDuplicates().classifications
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
