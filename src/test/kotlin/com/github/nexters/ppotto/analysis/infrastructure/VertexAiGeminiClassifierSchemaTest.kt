package com.github.nexters.ppotto.analysis.infrastructure

import com.github.nexters.ppotto.analysis.domain.PhotoRef
import com.github.nexters.ppotto.analysis.domain.ThemeClassificationValidator
import com.github.nexters.ppotto.global.error.BusinessException
import com.github.nexters.ppotto.global.identifier.PhotoId
import com.github.nexters.ppotto.sticker.domain.Sticker
import com.google.genai.types.Schema
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.util.UUID

class VertexAiGeminiClassifierSchemaTest :
    BehaviorSpec({
        val photo1 = PhotoId(UUID.fromString("550e8400-e29b-41d4-a716-446655440001"))
        val photo2 = PhotoId(UUID.fromString("550e8400-e29b-41d4-a716-446655440002"))
        val photo3 = PhotoId(UUID.fromString("550e8400-e29b-41d4-a716-446655440003"))
        val photos =
            listOf(
                PhotoRef(photo1, "gs://bucket/1.jpg", "image/jpeg"),
                PhotoRef(photo2, "gs://bucket/2.jpg", "image/jpeg"),
                PhotoRef(photo3, "gs://bucket/3.jpg", "image/jpeg"),
            )

        Given("Gemini 분류 응답 schema가 주어졌을 때") {
            When("schema를 확인하면") {
                Then("동적 enum 제약을 포함하지 않는다") {
                    VertexAiGeminiSchemas.CLASSIFICATION_RESPONSE_SCHEMA.containsEnum() shouldBe false
                }

                Then("모든 필드가 디코딩 직전에 읽히는 설명을 갖는다") {
                    VertexAiGeminiSchemas.CLASSIFICATION_RESPONSE_SCHEMA
                        .missingDescriptionPaths()
                        .shouldBeEmpty()
                }

                Then("말풍선과 키워드칩 개수를 schema가 강제한다") {
                    val comments =
                        VertexAiGeminiSchemas.CLASSIFICATION_RESPONSE_SCHEMA
                            .items()
                            .get()
                            .properties()
                            .get()["comments"]!!
                            .properties()
                            .get()
                    comments["speechBubbles"]!!.minItems().get() shouldBe 2L
                    comments["speechBubbles"]!!.maxItems().get() shouldBe 4L
                    comments["keywordChips"]!!.minItems().get() shouldBe 4L
                    comments["keywordChips"]!!.maxItems().get() shouldBe 8L
                }
            }
        }

        Given("Gemini 스티커 재생성 응답 schema가 주어졌을 때") {
            When("schema를 확인하면") {
                Then("동적 enum 제약을 포함하지 않는다") {
                    VertexAiGeminiSchemas.STICKER_RESPONSE_SCHEMA.containsEnum() shouldBe false
                }

                Then("모든 필드가 디코딩 직전에 읽히는 설명을 갖는다") {
                    VertexAiGeminiSchemas.STICKER_RESPONSE_SCHEMA
                        .missingDescriptionPaths()
                        .shouldBeEmpty()
                }

                Then("sourcePhotoId를 targetSubject보다 먼저 생성하도록 순서를 강제한다") {
                    VertexAiGeminiSchemas.STICKER_RESPONSE_SCHEMA
                        .propertyOrdering()
                        .get() shouldContainExactly listOf("sourcePhotoId", "targetSubject", "mainColor")
                }
            }
        }

        Given("Gemini 스티커 대상 재확인 응답 schema가 주어졌을 때") {
            When("schema를 확인하면") {
                Then("동적 enum 제약을 포함하지 않는다") {
                    VertexAiGeminiSchemas.VERIFICATION_RESPONSE_SCHEMA.containsEnum() shouldBe false
                }

                Then("모든 필드가 디코딩 직전에 읽히는 설명을 갖는다") {
                    VertexAiGeminiSchemas.VERIFICATION_RESPONSE_SCHEMA
                        .missingDescriptionPaths()
                        .shouldBeEmpty()
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

            When("mainColor가 hex 형식이 아니면") {
                val classifications =
                    VertexAiGeminiClassifier.toClassifications(
                        listOf(
                            themeResponse(
                                categorizedPhotoIds = listOf("P001"),
                                sourcePhotoId = "P001",
                                mainColor = "코랄색",
                            ),
                        ),
                        aliases,
                    )

                Then("분류를 실패시키지 않고 기본 색상으로 낮춘다") {
                    classifications.single().stickerMainColor shouldBe "#222222"
                }
            }

            When("mainColor가 아예 없으면") {
                val classifications =
                    VertexAiGeminiClassifier.toClassifications(
                        listOf(
                            themeResponse(
                                categorizedPhotoIds = listOf("P001"),
                                sourcePhotoId = "P001",
                                mainColor = null,
                            ),
                        ),
                        aliases,
                    )

                Then("분류를 실패시키지 않고 기본 색상으로 낮춘다") {
                    classifications.single().stickerMainColor shouldBe "#222222"
                }
            }

            When("speechBubble의 좌표나 내용이 반쪽이면") {
                val classifications =
                    VertexAiGeminiClassifier.toClassifications(
                        listOf(
                            themeResponse(
                                categorizedPhotoIds = listOf("P001"),
                                sourcePhotoId = "P001",
                                speechBubbles =
                                    listOf(
                                        GeminiSpeechBubbleResponse(content = "정상 말풍선", posX = -96.0, posY = -150.0),
                                        GeminiSpeechBubbleResponse(content = "좌표 없음", posX = null, posY = -150.0),
                                        GeminiSpeechBubbleResponse(content = "  ", posX = -10.0, posY = -20.0),
                                        GeminiSpeechBubbleResponse(content = "가".repeat(21), posX = 10.0, posY = 20.0),
                                    ),
                                keywordChips = listOf("여행", "   ", "나".repeat(21)),
                            ),
                        ),
                        aliases,
                    )

                Then("분류를 실패시키지 않고 화면에 못 담을 항목만 버린다") {
                    classifications
                        .single()
                        .comments
                        .map { it.content to (it.posX to it.posY) } shouldContainExactly
                        listOf(
                            "정상 말풍선" to (-96.0 to -150.0),
                            "여행" to (null to null),
                        )
                }
            }

            When("recap.badge가 스티커 제목 저장 한계를 넘으면") {
                val classifications =
                    VertexAiGeminiClassifier.toClassifications(
                        listOf(
                            themeResponse(
                                badge = "가".repeat(16),
                                categorizedPhotoIds = listOf("P001"),
                                sourcePhotoId = "P001",
                            ),
                        ),
                        aliases,
                    )

                Then("분석을 실패시키지 않고 저장 규칙에 맞게 잘라낸다") {
                    Sticker.isValidTitle(
                        classifications
                            .single()
                            .recap.badge,
                    ) shouldBe true
                }
            }

            When("recap.badge가 이모지 때문에 한계를 넘으면") {
                val classifications =
                    VertexAiGeminiClassifier.toClassifications(
                        listOf(
                            themeResponse(
                                badge = "아주아주아주긴뱃지문구입니다\uD83E\uDDF3",
                                categorizedPhotoIds = listOf("P001"),
                                sourcePhotoId = "P001",
                            ),
                        ),
                        aliases,
                    )

                Then("잘린 끝에 깨진 문자를 남기지 않는다") {
                    val badge =
                        classifications
                            .single()
                            .recap.badge
                    Sticker.isValidTitle(badge) shouldBe true
                    badge.last().isHighSurrogate() shouldBe false
                }
            }

            When("recap.text가 한 줄 요약 저장 한계를 넘으면") {
                val classifications =
                    VertexAiGeminiClassifier.toClassifications(
                        listOf(
                            themeResponse(
                                text = "가".repeat(101),
                                categorizedPhotoIds = listOf("P001"),
                                sourcePhotoId = "P001",
                            ),
                        ),
                        aliases,
                    )

                Then("분석을 실패시키지 않고 저장 규칙에 맞게 잘라낸다") {
                    Sticker.isValidSummary(
                        classifications
                            .single()
                            .recap.text,
                    ) shouldBe true
                }
            }

            When("comments 자체가 없으면") {
                val classifications =
                    VertexAiGeminiClassifier.toClassifications(
                        listOf(
                            themeResponse(
                                categorizedPhotoIds = listOf("P001"),
                                sourcePhotoId = "P001",
                                comments = null,
                            ),
                        ),
                        aliases,
                    )

                Then("코멘트 없이 분류를 유지한다") {
                    classifications
                        .single()
                        .comments
                        .shouldBeEmpty()
                }
            }

            When("alias를 못 찾아 테마가 전부 탈락한 결과를 classifyAndRecap과 같은 순서로 검증하면") {
                val classifications =
                    VertexAiGeminiClassifier.toClassifications(
                        listOf(themeResponse(categorizedPhotoIds = listOf("PX98"), sourcePhotoId = "PX99")),
                        aliases,
                    )
                val exception =
                    shouldThrow<BusinessException> {
                        ThemeClassificationValidator.validate(classifications, photos.map { it.photoId }.toSet())
                    }

                Then("검증을 건너뛰지 않고 ANALYSIS-007로 분석을 실패시킨다") {
                    exception.errorCode.code shouldBe "ANALYSIS-007"
                    exception.message shouldContain "테마 개수는 1 개 이상"
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

                Then("ANALYSIS-007로 어떤 alias가 문제인지 알려준다") {
                    exception.errorCode.code shouldBe "ANALYSIS-007"
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
                Then("피사체 없음 대신 잘못된 응답 예외를 반환해 최초 분류로 복구할 수 있다") {
                    listOf(null, "  ").forEach { targetSubject ->
                        shouldThrow<BusinessException> {
                            VertexAiGeminiClassifier.toVerification(
                                GeminiSubjectVerificationResponse(true, targetSubject, "#123456"),
                            )
                        }.errorCode.code shouldBe "ANALYSIS-007"
                    }
                }
            }

            When("subjectPresent 값이 누락되면") {
                Then("피사체 없음으로 판정하지 않는다") {
                    shouldThrow<BusinessException> {
                        VertexAiGeminiClassifier.toVerification(GeminiSubjectVerificationResponse(null, "고양이", "#123456"))
                    }.errorCode.code shouldBe "ANALYSIS-007"
                }
            }
        }
    })

private fun Schema.missingDescriptionPaths(path: String = "root"): List<String> {
    val self = if (description().isPresent) emptyList() else listOf(path)
    val fromItems = items().map { it.missingDescriptionPaths("$path[]") }.orElse(emptyList())
    val fromProperties =
        properties()
            .map { properties -> properties.entries.flatMap { (name, schema) -> schema.missingDescriptionPaths("$path.$name") } }
            .orElse(emptyList())
    return self + fromItems + fromProperties
}

private fun Schema.containsEnum(): Boolean {
    if (enum_().isPresent) return true
    if (anyOf().map { schemas -> schemas.any { it.containsEnum() } }.orElse(false)) return true
    if (items().map { it.containsEnum() }.orElse(false)) return true
    if (properties().map { properties -> properties.values.any { it.containsEnum() } }.orElse(false)) return true
    return false
}

private fun themeResponse(
    theme: String = "테마",
    badge: String = "뱃지",
    text: String = "리캡",
    categorizedPhotoIds: List<String>,
    sourcePhotoId: String,
    keywordChips: List<String> = emptyList(),
    speechBubbles: List<GeminiSpeechBubbleResponse> = emptyList(),
    mainColor: String? = "#FF6B6B",
    comments: GeminiCommentsResponse? = GeminiCommentsResponse(speechBubbles, keywordChips),
) = GeminiThemeResponse(
    theme = theme,
    categorizedPhotoIds = categorizedPhotoIds,
    recap = GeminiRecapResponse(badge = badge, text = text),
    sticker =
        GeminiStickerResponse(
            targetSubject = "피사체",
            sourcePhotoId = sourcePhotoId,
            mainColor = mainColor,
        ),
    comments = comments,
)
