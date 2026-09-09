package com.github.nexters.ppotto.analysis.domain

import com.github.nexters.ppotto.global.error.BusinessException
import com.github.nexters.ppotto.global.identifier.PhotoId
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.util.UUID

private fun photoId(): PhotoId = PhotoId(UUID.randomUUID())

private fun classification(
    theme: String = "가을",
    categorizedPhotoIds: List<PhotoId>,
    badge: String = "단풍",
    text: String = "가을의 정취",
    stickerTargetSubject: String = "단풍잎",
    stickerSourcePhotoId: PhotoId = categorizedPhotoIds.first(),
) = ThemeClassification(
    theme = theme,
    categorizedPhotoIds = categorizedPhotoIds,
    recap = RecapContent(badge = badge, text = text),
    stickerTargetSubject = stickerTargetSubject,
    stickerSourcePhotoId = stickerSourcePhotoId,
    stickerMainColor = "#FF6B6B",
    comments = emptyList(),
)

class ThemeClassificationValidatorTest :
    BehaviorSpec({
        val photo1 = photoId()
        val photo2 = photoId()
        val photo3 = photoId()

        Given("사진 1장을 담은 테마 1개가 주어졌을 때") {
            val classifications = listOf(classification(categorizedPhotoIds = listOf(photo1)))

            When("검증하면") {
                Then("하한 개수인 1개 테마는 통과한다") {
                    shouldNotThrowAny { ThemeClassificationValidator.validate(classifications, setOf(photo1)) }
                }
            }
        }

        Given("서로 다른 사진을 담은 테마 6개가 주어졌을 때") {
            val photos = List(6) { photoId() }
            val classifications = photos.map { classification(categorizedPhotoIds = listOf(it)) }

            When("검증하면") {
                Then("상한 개수인 6개 테마는 통과한다") {
                    shouldNotThrowAny { ThemeClassificationValidator.validate(classifications, photos.toSet()) }
                }
            }
        }

        Given("입력 사진 3장 중 2장만 분류된 테마가 주어졌을 때") {
            val classifications = listOf(classification(categorizedPhotoIds = listOf(photo1, photo2)))

            When("검증하면") {
                Then("어느 테마에도 속하지 않은 사진이 남아 있어도 통과한다") {
                    shouldNotThrowAny {
                        ThemeClassificationValidator.validate(classifications, setOf(photo1, photo2, photo3))
                    }
                }
            }
        }

        Given("테마가 0개인 분류가 주어졌을 때") {
            When("검증하면") {
                val exception =
                    shouldThrow<BusinessException> {
                        ThemeClassificationValidator.validate(emptyList(), setOf(photo1))
                    }

                Then("테마 개수 하한 위반으로 실제 개수까지 알려준다") {
                    exception.errorCode.code shouldBe "ANALYSIS-007"
                    exception.message shouldContain "테마 개수는 1 개 이상 6 개 이하여야 합니다"
                    exception.message shouldContain "실제: 0개"
                }
            }
        }

        Given("테마가 7개인 분류가 주어졌을 때") {
            val photos = List(7) { photoId() }
            val classifications = photos.map { classification(categorizedPhotoIds = listOf(it)) }

            When("검증하면") {
                val exception =
                    shouldThrow<BusinessException> {
                        ThemeClassificationValidator.validate(classifications, photos.toSet())
                    }

                Then("테마 개수 상한 위반으로 실제 개수까지 알려준다") {
                    exception.errorCode.code shouldBe "ANALYSIS-007"
                    exception.message shouldContain "테마 개수는 1 개 이상 6 개 이하여야 합니다"
                    exception.message shouldContain "실제: 7개"
                }
            }
        }

        Given("theme이 빈 문자열인 분류가 주어졌을 때") {
            val classifications = listOf(classification(theme = "", categorizedPhotoIds = listOf(photo1)))

            When("검증하면") {
                val exception =
                    shouldThrow<BusinessException> {
                        ThemeClassificationValidator.validate(classifications, setOf(photo1))
                    }

                Then("몇 번째 테마의 어떤 필드가 비었는지 알려준다") {
                    exception.errorCode.code shouldBe "ANALYSIS-007"
                    exception.message shouldContain "테마 #1: theme이 비어있습니다"
                }
            }
        }

        Given("recap.badge가 빈 문자열인 분류가 주어졌을 때") {
            val classifications = listOf(classification(badge = "", categorizedPhotoIds = listOf(photo1)))

            When("검증하면") {
                val exception =
                    shouldThrow<BusinessException> {
                        ThemeClassificationValidator.validate(classifications, setOf(photo1))
                    }

                Then("몇 번째 테마의 어떤 필드가 비었는지 알려준다") {
                    exception.errorCode.code shouldBe "ANALYSIS-007"
                    exception.message shouldContain "테마 #1: recap.badge가 비어있습니다"
                }
            }
        }

        Given("recap.text가 빈 문자열인 분류가 주어졌을 때") {
            val classifications = listOf(classification(text = "", categorizedPhotoIds = listOf(photo1)))

            When("검증하면") {
                val exception =
                    shouldThrow<BusinessException> {
                        ThemeClassificationValidator.validate(classifications, setOf(photo1))
                    }

                Then("몇 번째 테마의 어떤 필드가 비었는지 알려준다") {
                    exception.errorCode.code shouldBe "ANALYSIS-007"
                    exception.message shouldContain "테마 #1: recap.text가 비어있습니다"
                }
            }
        }

        Given("stickerTargetSubject가 빈 문자열인 분류가 주어졌을 때") {
            val classifications = listOf(classification(stickerTargetSubject = "", categorizedPhotoIds = listOf(photo1)))

            When("검증하면") {
                val exception =
                    shouldThrow<BusinessException> {
                        ThemeClassificationValidator.validate(classifications, setOf(photo1))
                    }

                Then("몇 번째 테마의 어떤 필드가 비었는지 알려준다") {
                    exception.errorCode.code shouldBe "ANALYSIS-007"
                    exception.message shouldContain "테마 #1: stickerTargetSubject가 비어있습니다"
                }
            }
        }

        Given("categorizedPhotoIds가 빈 테마가 주어졌을 때") {
            val classifications =
                listOf(classification(categorizedPhotoIds = emptyList(), stickerSourcePhotoId = photo1))

            When("검증하면") {
                val exception =
                    shouldThrow<BusinessException> {
                        ThemeClassificationValidator.validate(classifications, setOf(photo1))
                    }

                Then("사진이 하나도 없는 테마는 저장하지 않는다") {
                    exception.errorCode.code shouldBe "ANALYSIS-007"
                    exception.message shouldContain "테마 #1: categorizedPhotoIds가 비어있습니다"
                }
            }
        }

        Given("한 테마 안에 같은 사진 ID가 두 번 들어간 분류가 주어졌을 때") {
            val classifications = listOf(classification(categorizedPhotoIds = listOf(photo1, photo1)))

            When("검증하면") {
                val exception =
                    shouldThrow<BusinessException> {
                        ThemeClassificationValidator.validate(classifications, setOf(photo1))
                    }

                Then("테마 내 사진 중복을 중복 ID와 함께 거부한다") {
                    exception.errorCode.code shouldBe "ANALYSIS-007"
                    exception.message shouldContain "테마 #1: 같은 사진 ID가 여러 번 포함되었습니다"
                }
            }
        }

        Given("입력에 없는 사진 ID가 섞인 분류가 주어졌을 때") {
            val classifications = listOf(classification(categorizedPhotoIds = listOf(photo1, photoId())))

            When("검증하면") {
                val exception =
                    shouldThrow<BusinessException> {
                        ThemeClassificationValidator.validate(classifications, setOf(photo1))
                    }

                Then("요청하지 않은 사진 ID를 거부한다") {
                    exception.errorCode.code shouldBe "ANALYSIS-007"
                    exception.message shouldContain "테마 #1: 입력에 없는 사진 ID가 포함되었습니다"
                }
            }
        }

        Given("같은 사진 ID가 두 테마에 모두 들어간 분류가 주어졌을 때") {
            val classifications =
                listOf(
                    classification(theme = "봄", categorizedPhotoIds = listOf(photo1)),
                    classification(theme = "여름", categorizedPhotoIds = listOf(photo1, photo2)),
                )

            When("검증하면") {
                val exception =
                    shouldThrow<BusinessException> {
                        ThemeClassificationValidator.validate(classifications, setOf(photo1, photo2))
                    }

                Then("사진 한 장은 테마 하나에만 속해야 한다는 규칙으로 거부한다") {
                    exception.errorCode.code shouldBe "ANALYSIS-007"
                    exception.message shouldContain "여러 테마에 중복 분류"
                }
            }
        }

        Given("stickerSourcePhotoId가 그 테마의 categorizedPhotoIds 밖인 분류가 주어졌을 때") {
            val classifications =
                listOf(classification(categorizedPhotoIds = listOf(photo1), stickerSourcePhotoId = photo2))

            When("검증하면") {
                val exception =
                    shouldThrow<BusinessException> {
                        ThemeClassificationValidator.validate(classifications, setOf(photo1, photo2))
                    }

                Then("스티커 원본은 그 테마가 분류한 사진 중에서만 고를 수 있다") {
                    exception.errorCode.code shouldBe "ANALYSIS-007"
                    exception.message shouldContain "테마 #1: stickerSourcePhotoId"
                    exception.message shouldContain "categorizedPhotoIds에 없습니다"
                }
            }
        }
    })
