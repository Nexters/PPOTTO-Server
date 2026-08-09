package com.github.nexters.ppotto.analysis.domain

import com.github.nexters.ppotto.global.error.BusinessException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.string.shouldContain
import java.util.UUID

class ThemeClassificationValidatorTest :
    BehaviorSpec({
        val photo1 = UUID.randomUUID()
        val photo2 = UUID.randomUUID()
        val photo3 = UUID.randomUUID()

        Given("유효한 테마 분류가 주어졌을 때") {
            When("검증을 실행하면") {
                Then("1개 테마 분류는 통과한다") {
                    val classifications =
                        listOf(
                            ThemeClassification(
                                theme = "가을",
                                categorizedPhotoIds = listOf(photo1),
                                recap = RecapContent(badge = "단풍", text = "가을의 정취"),
                                stickerTargetSubject = "단풍잎",
                                stickerSourcePhotoId = photo1,
                                stickerMainColor = "#FF6B6B",
                                comments = emptyList(),
                            ),
                        )
                    val inputPhotoIds = setOf(photo1)

                    ThemeClassificationValidator.validate(classifications, inputPhotoIds)
                }
            }
        }

        Given("최대개수의 테마 분류가 주어졌을 때") {
            When("검증을 실행하면") {
                Then("4개 테마 분류는 통과한다") {
                    val photos = List(4) { UUID.randomUUID() }
                    val classifications =
                        photos.mapIndexed { idx, photoId ->
                            ThemeClassification(
                                theme = "테마$idx",
                                categorizedPhotoIds = listOf(photoId),
                                recap = RecapContent(badge = "뱃지$idx", text = "텍스트$idx"),
                                stickerTargetSubject = "피사체$idx",
                                stickerSourcePhotoId = photoId,
                                stickerMainColor = "#FF6B6B",
                                comments = emptyList(),
                            )
                        }
                    val inputPhotoIds = photos.toSet()

                    ThemeClassificationValidator.validate(classifications, inputPhotoIds)
                }
            }
        }

        Given("입력 사진 일부만 분류된 테마가 주어졌을 때") {
            When("검증을 실행하면") {
                Then("분류되지 않은 사진이 있어도 통과한다") {
                    val classifications =
                        listOf(
                            ThemeClassification(
                                theme = "여름",
                                categorizedPhotoIds = listOf(photo1, photo2),
                                recap = RecapContent(badge = "뜨거움", text = "한여름"),
                                stickerTargetSubject = "해",
                                stickerSourcePhotoId = photo1,
                                stickerMainColor = "#FF6B6B",
                                comments = emptyList(),
                            ),
                        )
                    val inputPhotoIds = setOf(photo1, photo2, photo3) // photo3는 분류되지 않음

                    ThemeClassificationValidator.validate(classifications, inputPhotoIds)
                }
            }
        }

        Given("테마가 0개인 분류가 주어졌을 때") {
            When("검증을 실행하면") {
                Then("예외를 발생한다") {
                    val exception =
                        shouldThrow<BusinessException> {
                            ThemeClassificationValidator.validate(emptyList(), setOf(photo1))
                        }
                    exception.message shouldContain "테마 개수는 1 개 이상 4 개 이하여야 합니다"
                    exception.message shouldContain "실제: 0개"
                }
            }
        }

        Given("테마가 5개 이상인 분류가 주어졌을 때") {
            When("검증을 실행하면") {
                Then("예외를 발생한다") {
                    val photos = List(5) { UUID.randomUUID() }
                    val classifications =
                        photos.mapIndexed { idx, photoId ->
                            ThemeClassification(
                                theme = "테마$idx",
                                categorizedPhotoIds = listOf(photoId),
                                recap = RecapContent(badge = "뱃지$idx", text = "텍스트$idx"),
                                stickerTargetSubject = "피사체$idx",
                                stickerSourcePhotoId = photoId,
                                stickerMainColor = "#FF6B6B",
                                comments = emptyList(),
                            )
                        }
                    val inputPhotoIds = photos.toSet()

                    val exception =
                        shouldThrow<BusinessException> {
                            ThemeClassificationValidator.validate(classifications, inputPhotoIds)
                        }
                    exception.message shouldContain "테마 개수는 1 개 이상 4 개 이하여야 합니다"
                    exception.message shouldContain "실제: 5개"
                }
            }
        }

        Given("빈 theme 필드를 가진 분류가 주어졌을 때") {
            When("검증을 실행하면") {
                Then("예외를 발생한다") {
                    val classifications =
                        listOf(
                            ThemeClassification(
                                theme = "",
                                categorizedPhotoIds = listOf(photo1),
                                recap = RecapContent(badge = "뱃지", text = "텍스트"),
                                stickerTargetSubject = "피사체",
                                stickerSourcePhotoId = photo1,
                                stickerMainColor = "#FF6B6B",
                                comments = emptyList(),
                            ),
                        )

                    val exception =
                        shouldThrow<BusinessException> {
                            ThemeClassificationValidator.validate(classifications, setOf(photo1))
                        }
                    exception.message shouldContain "테마 #1: theme이 비어있습니다"
                }
            }
        }

        Given("빈 recap.badge 필드를 가진 분류가 주어졌을 때") {
            When("검증을 실행하면") {
                Then("예외를 발생한다") {
                    val classifications =
                        listOf(
                            ThemeClassification(
                                theme = "겨울",
                                categorizedPhotoIds = listOf(photo1),
                                recap = RecapContent(badge = "", text = "텍스트"),
                                stickerTargetSubject = "피사체",
                                stickerSourcePhotoId = photo1,
                                stickerMainColor = "#FF6B6B",
                                comments = emptyList(),
                            ),
                        )

                    val exception =
                        shouldThrow<BusinessException> {
                            ThemeClassificationValidator.validate(classifications, setOf(photo1))
                        }
                    exception.message shouldContain "테마 #1: recap.badge가 비어있습니다"
                }
            }
        }

        Given("빈 recap.text 필드를 가진 분류가 주어졌을 때") {
            When("검증을 실행하면") {
                Then("예외를 발생한다") {
                    val classifications =
                        listOf(
                            ThemeClassification(
                                theme = "봄",
                                categorizedPhotoIds = listOf(photo1),
                                recap = RecapContent(badge = "뱃지", text = ""),
                                stickerTargetSubject = "피사체",
                                stickerSourcePhotoId = photo1,
                                stickerMainColor = "#FF6B6B",
                                comments = emptyList(),
                            ),
                        )

                    val exception =
                        shouldThrow<BusinessException> {
                            ThemeClassificationValidator.validate(classifications, setOf(photo1))
                        }
                    exception.message shouldContain "테마 #1: recap.text가 비어있습니다"
                }
            }
        }

        Given("빈 stickerTargetSubject 필드를 가진 분류가 주어졌을 때") {
            When("검증을 실행하면") {
                Then("예외를 발생한다") {
                    val classifications =
                        listOf(
                            ThemeClassification(
                                theme = "여름",
                                categorizedPhotoIds = listOf(photo1),
                                recap = RecapContent(badge = "뱃지", text = "텍스트"),
                                stickerTargetSubject = "",
                                stickerSourcePhotoId = photo1,
                                stickerMainColor = "#FF6B6B",
                                comments = emptyList(),
                            ),
                        )

                    val exception =
                        shouldThrow<BusinessException> {
                            ThemeClassificationValidator.validate(classifications, setOf(photo1))
                        }
                    exception.message shouldContain "테마 #1: stickerTargetSubject가 비어있습니다"
                }
            }
        }

        Given("빈 categorizedPhotoIds를 가진 분류가 주어졌을 때") {
            When("검증을 실행하면") {
                Then("예외를 발생한다") {
                    val classifications =
                        listOf(
                            ThemeClassification(
                                theme = "겨울",
                                categorizedPhotoIds = emptyList(),
                                recap = RecapContent(badge = "뱃지", text = "텍스트"),
                                stickerTargetSubject = "피사체",
                                stickerSourcePhotoId = UUID.randomUUID(),
                                stickerMainColor = "#FF6B6B",
                                comments = emptyList(),
                            ),
                        )

                    val exception =
                        shouldThrow<BusinessException> {
                            ThemeClassificationValidator.validate(classifications, setOf(photo1))
                        }
                    exception.message shouldContain "테마 #1: categorizedPhotoIds가 비어있습니다"
                }
            }
        }

        Given("테마 내부에 중복된 photo id가 있는 분류가 주어졌을 때") {
            When("검증을 실행하면") {
                Then("예외를 발생한다") {
                    val classifications =
                        listOf(
                            ThemeClassification(
                                theme = "봄",
                                categorizedPhotoIds = listOf(photo1, photo1),
                                recap = RecapContent(badge = "뱃지", text = "텍스트"),
                                stickerTargetSubject = "피사체",
                                stickerSourcePhotoId = photo1,
                                stickerMainColor = "#FF6B6B",
                                comments = emptyList(),
                            ),
                        )

                    val exception =
                        shouldThrow<BusinessException> {
                            ThemeClassificationValidator.validate(classifications, setOf(photo1))
                        }
                    exception.message shouldContain "테마 #1: 같은 사진 ID가 여러 번 포함되었습니다"
                }
            }
        }

        Given("입력에 없는 photo id가 포함된 분류가 주어졌을 때") {
            When("검증을 실행하면") {
                Then("예외를 발생한다") {
                    val unknownPhotoId = UUID.randomUUID()
                    val classifications =
                        listOf(
                            ThemeClassification(
                                theme = "여름",
                                categorizedPhotoIds = listOf(photo1, unknownPhotoId),
                                recap = RecapContent(badge = "뱃지", text = "텍스트"),
                                stickerTargetSubject = "피사체",
                                stickerSourcePhotoId = photo1,
                                stickerMainColor = "#FF6B6B",
                                comments = emptyList(),
                            ),
                        )

                    val exception =
                        shouldThrow<BusinessException> {
                            ThemeClassificationValidator.validate(classifications, setOf(photo1))
                        }
                    exception.message shouldContain "테마 #1: 입력에 없는 사진 ID가 포함되었습니다"
                }
            }
        }

        Given("테마 간 중복된 photo id가 있는 분류가 주어졌을 때") {
            When("검증을 실행하면") {
                Then("예외를 발생한다") {
                    val classifications =
                        listOf(
                            ThemeClassification(
                                theme = "봄",
                                categorizedPhotoIds = listOf(photo1),
                                recap = RecapContent(badge = "뱃지1", text = "텍스트1"),
                                stickerTargetSubject = "피사체1",
                                stickerSourcePhotoId = photo1,
                                stickerMainColor = "#FF6B6B",
                                comments = emptyList(),
                            ),
                            ThemeClassification(
                                theme = "여름",
                                categorizedPhotoIds = listOf(photo1, photo2),
                                recap = RecapContent(badge = "뱃지2", text = "텍스트2"),
                                stickerTargetSubject = "피사체2",
                                stickerSourcePhotoId = photo1,
                                stickerMainColor = "#FF6B6B",
                                comments = emptyList(),
                            ),
                        )

                    val exception =
                        shouldThrow<BusinessException> {
                            ThemeClassificationValidator.validate(classifications, setOf(photo1, photo2))
                        }
                    exception.message shouldContain "여러 테마에 중복 분류"
                }
            }
        }

        Given("stickerSourcePhotoId가 categorizedPhotoIds 밖인 분류가 주어졌을 때") {
            When("검증을 실행하면") {
                Then("예외를 발생한다") {
                    val classifications =
                        listOf(
                            ThemeClassification(
                                theme = "가을",
                                categorizedPhotoIds = listOf(photo1),
                                recap = RecapContent(badge = "뱃지", text = "텍스트"),
                                stickerTargetSubject = "피사체",
                                stickerSourcePhotoId = photo2,
                                stickerMainColor = "#FF6B6B",
                                comments = emptyList(),
                            ),
                        )

                    val exception =
                        shouldThrow<BusinessException> {
                            ThemeClassificationValidator.validate(classifications, setOf(photo1, photo2))
                        }
                    exception.message shouldContain "테마 #1: stickerSourcePhotoId"
                    exception.message shouldContain "categorizedPhotoIds에 없습니다"
                }
            }
        }
    })
