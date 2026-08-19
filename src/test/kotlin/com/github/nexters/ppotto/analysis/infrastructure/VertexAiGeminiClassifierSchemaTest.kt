package com.github.nexters.ppotto.analysis.infrastructure

import com.github.nexters.ppotto.analysis.domain.PhotoRef
import com.github.nexters.ppotto.global.error.BusinessException
import com.google.genai.types.Schema
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.util.UUID

class VertexAiGeminiClassifierSchemaTest :
    BehaviorSpec({
        val photo1 = UUID.fromString("550e8400-e29b-41d4-a716-446655440001")
        val photo2 = UUID.fromString("550e8400-e29b-41d4-a716-446655440002")
        val photo3 = UUID.fromString("550e8400-e29b-41d4-a716-446655440003")
        val photos =
            listOf(
                PhotoRef(photo1, "gs://bucket/1.jpg", "image/jpeg"),
                PhotoRef(photo2, "gs://bucket/2.jpg", "image/jpeg"),
                PhotoRef(photo3, "gs://bucket/3.jpg", "image/jpeg"),
            )

        Given("Gemini 분류 응답 schema가 주어졌을 때") {
            When("schema를 확인하면") {
                Then("동적 enum 제약을 포함하지 않는다") {
                    VertexAiGeminiSchemas.classificationResponseSchema().containsEnum() shouldBe false
                }
            }
        }

        Given("Gemini 스티커 재생성 응답 schema가 주어졌을 때") {
            When("schema를 확인하면") {
                Then("동적 enum 제약을 포함하지 않는다") {
                    VertexAiGeminiSchemas.stickerResponseSchema().containsEnum() shouldBe false
                }

                Then("sourcePhotoId를 targetSubject보다 먼저 생성하도록 순서를 강제한다") {
                    VertexAiGeminiSchemas
                        .stickerResponseSchema()
                        .propertyOrdering()
                        .get() shouldContainExactly listOf("sourcePhotoId", "targetSubject", "mainColor")
                }
            }
        }

        Given("Gemini 스티커 대상 재확인 응답 schema가 주어졌을 때") {
            When("schema를 확인하면") {
                Then("동적 enum 제약을 포함하지 않는다") {
                    VertexAiGeminiSchemas.verificationResponseSchema().containsEnum() shouldBe false
                }
            }
        }

        Given("사진 목록이 주어졌을 때") {
            When("Gemini 사진 alias를 만들면") {
                val aliases = GeminiPhotoAliases.from(photos)

                Then("입력 순서대로 짧은 alias를 만든다") {
                    aliases.aliases shouldContainExactly listOf("P001", "P002", "P003")
                    aliases.photoId("P001") shouldBe photo1
                    aliases.photoId("P002") shouldBe photo2
                    aliases.photoId("P003") shouldBe photo3
                    aliases.aliasFor(photo2) shouldBe "P002"
                }
            }
        }

        Given("Gemini 분류 응답이 alias를 사용할 때") {
            val aliases = GeminiPhotoAliases.from(photos)

            When("모든 alias가 유효하면") {
                val classifications =
                    VertexAiGeminiClassifier.toClassifications(
                        listOf(
                            themeResponse(
                                categorizedPhotoIds = listOf("P001", "P002"),
                                sourcePhotoId = "P001",
                            ),
                        ),
                        aliases,
                    )

                Then("도메인 UUID로 변환한다") {
                    classifications.size shouldBe 1
                    classifications.first().categorizedPhotoIds shouldContainExactly listOf(photo1, photo2)
                    classifications.first().stickerSourcePhotoId shouldBe photo1
                }
            }

            When("categorizedPhotoIds에 알 수 없는 alias가 섞이면") {
                val classifications =
                    VertexAiGeminiClassifier.toClassifications(
                        listOf(
                            themeResponse(
                                categorizedPhotoIds = listOf("P001", "PX99", "P002"),
                                sourcePhotoId = "P002",
                            ),
                        ),
                        aliases,
                    )

                Then("알 수 없는 alias만 제거한다") {
                    classifications.size shouldBe 1
                    classifications.first().categorizedPhotoIds shouldContainExactly listOf(photo1, photo2)
                    classifications.first().stickerSourcePhotoId shouldBe photo2
                }
            }

            When("categorizedPhotoIds의 모든 alias를 찾을 수 없으면") {
                val classifications =
                    VertexAiGeminiClassifier.toClassifications(
                        listOf(
                            themeResponse(
                                categorizedPhotoIds = listOf("PX98", "PX99"),
                                sourcePhotoId = "P001",
                            ),
                            themeResponse(
                                theme = "유효한 테마",
                                categorizedPhotoIds = listOf("P003"),
                                sourcePhotoId = "P003",
                            ),
                        ),
                        aliases,
                    )

                Then("사진 근거가 없는 테마만 건너뛴다") {
                    classifications.size shouldBe 1
                    classifications.first().theme shouldBe "유효한 테마"
                    classifications.first().categorizedPhotoIds shouldContainExactly listOf(photo3)
                }
            }

            When("sourcePhotoId alias를 찾을 수 없으면") {
                val classifications =
                    VertexAiGeminiClassifier.toClassifications(
                        listOf(
                            themeResponse(
                                categorizedPhotoIds = listOf("P001", "P002"),
                                sourcePhotoId = "PX99",
                            ),
                            themeResponse(
                                theme = "유효한 테마",
                                categorizedPhotoIds = listOf("P003"),
                                sourcePhotoId = "P003",
                            ),
                        ),
                        aliases,
                    )

                Then("해당 테마만 건너뛴다") {
                    classifications.size shouldBe 1
                    classifications.first().theme shouldBe "유효한 테마"
                    classifications.first().stickerSourcePhotoId shouldBe photo3
                }
            }

            When("sourcePhotoId가 남은 categorizedPhotoIds 밖이면") {
                val classifications =
                    VertexAiGeminiClassifier.toClassifications(
                        listOf(
                            themeResponse(
                                categorizedPhotoIds = listOf("P001"),
                                sourcePhotoId = "P002",
                            ),
                        ),
                        aliases,
                    )

                Then("해당 테마를 건너뛴다") {
                    classifications shouldBe emptyList()
                }
            }

            When("keywordChips에 해시태그 접두사가 붙어있으면") {
                val classifications =
                    VertexAiGeminiClassifier.toClassifications(
                        listOf(
                            themeResponse(
                                categorizedPhotoIds = listOf("P001"),
                                sourcePhotoId = "P001",
                                keywordChips = listOf("#미식", "## 여행", "일상"),
                            ),
                        ),
                        aliases,
                    )

                Then("맨 앞의 #을 제거하고 저장한다") {
                    classifications
                        .first()
                        .comments
                        .map { it.content } shouldContainExactly listOf("미식", "여행", "일상")
                }
            }
        }

        Given("Gemini 스티커 재생성 응답이 alias를 사용할 때") {
            val aliases = GeminiPhotoAliases.from(photos)

            When("sourcePhotoId alias가 유효하면") {
                val target =
                    VertexAiGeminiClassifier.toRegenerationTarget(
                        GeminiStickerResponse(
                            targetSubject = "고양이",
                            sourcePhotoId = "P002",
                            mainColor = "#123456",
                        ),
                        aliases,
                        photos.map { it.photoId }.toSet(),
                    )

                Then("도메인 UUID로 변환한다") {
                    target.stickerSourcePhotoId shouldBe photo2
                    target.stickerTargetSubject shouldBe "고양이"
                    target.stickerMainColor shouldBe "#123456"
                }
            }

            When("sourcePhotoId alias를 찾을 수 없으면") {
                val exception =
                    shouldThrow<BusinessException> {
                        VertexAiGeminiClassifier.toRegenerationTarget(
                            GeminiStickerResponse(
                                targetSubject = "고양이",
                                sourcePhotoId = "PX99",
                                mainColor = "#123456",
                            ),
                            aliases,
                            photos.map { it.photoId }.toSet(),
                        )
                    }

                Then("명확한 Gemini 응답 오류를 반환한다") {
                    exception.message shouldContain "입력 사진 alias 목록에 없습니다"
                }
            }
        }

        Given("Gemini 스티커 대상 재확인 응답이 주어졌을 때") {
            When("대상이 존재하고 mainColor가 유효하면") {
                val verification =
                    VertexAiGeminiClassifier.toVerification(
                        GeminiSubjectVerificationResponse(
                            subjectPresent = true,
                            targetSubject = "고양이",
                            mainColor = "#123456",
                        ),
                    )

                Then("보정된 targetSubject/mainColor를 반환한다") {
                    verification.shouldNotBeNull()
                    verification.targetSubject shouldBe "고양이"
                    verification.mainColor shouldBe "#123456"
                }
            }

            When("대상이 존재하지만 mainColor 형식이 잘못되면") {
                val verification =
                    VertexAiGeminiClassifier.toVerification(
                        GeminiSubjectVerificationResponse(
                            subjectPresent = true,
                            targetSubject = "고양이",
                            mainColor = "not-a-color",
                        ),
                    )

                Then("기본 색상으로 대체한다") {
                    verification.shouldNotBeNull()
                    verification.mainColor shouldBe "#222222"
                }
            }

            When("subjectPresent가 false면") {
                val verification =
                    VertexAiGeminiClassifier.toVerification(
                        GeminiSubjectVerificationResponse(
                            subjectPresent = false,
                            targetSubject = null,
                            mainColor = null,
                        ),
                    )

                Then("null을 반환한다") {
                    verification.shouldBeNull()
                }
            }

            When("subjectPresent는 true인데 targetSubject가 비어있으면") {
                val verification =
                    VertexAiGeminiClassifier.toVerification(
                        GeminiSubjectVerificationResponse(
                            subjectPresent = true,
                            targetSubject = "  ",
                            mainColor = "#123456",
                        ),
                    )

                Then("null을 반환한다") {
                    verification.shouldBeNull()
                }
            }
        }
    })

private fun Schema.containsEnum(): Boolean {
    if (enum_().isPresent) return true
    if (anyOf().map { schemas -> schemas.any { it.containsEnum() } }.orElse(false)) return true
    if (items().map { it.containsEnum() }.orElse(false)) return true
    if (properties().map { properties -> properties.values.any { it.containsEnum() } }.orElse(false)) return true
    return false
}

private fun themeResponse(
    theme: String = "테마",
    categorizedPhotoIds: List<String>,
    sourcePhotoId: String,
    keywordChips: List<String> = emptyList(),
) = GeminiThemeResponse(
    theme = theme,
    categorizedPhotoIds = categorizedPhotoIds,
    recap = GeminiRecapResponse(badge = "뱃지", text = "리캡"),
    sticker =
        GeminiStickerResponse(
            targetSubject = "피사체",
            sourcePhotoId = sourcePhotoId,
            mainColor = "#FF6B6B",
        ),
    comments = GeminiCommentsResponse(speechBubbles = emptyList(), keywordChips = keywordChips),
)
