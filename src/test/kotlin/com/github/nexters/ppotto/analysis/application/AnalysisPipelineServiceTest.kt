package com.github.nexters.ppotto.analysis.application

import com.github.nexters.ppotto.analysis.domain.GeminiClassifier
import com.github.nexters.ppotto.analysis.domain.PhotoRef
import com.github.nexters.ppotto.analysis.domain.RecapContent
import com.github.nexters.ppotto.analysis.domain.StickerGenerator
import com.github.nexters.ppotto.analysis.domain.StickerRegenerationTarget
import com.github.nexters.ppotto.analysis.domain.StickerStorage
import com.github.nexters.ppotto.analysis.domain.StickerSubjectVerification
import com.github.nexters.ppotto.analysis.domain.ThemeClassification
import com.github.nexters.ppotto.analysis.domain.ThemeComment
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.util.UUID

class AnalysisPipelineServiceTest :
    BehaviorSpec({
        Given("분석 파이프라인이 여러 테마를 처리할 때") {
            val analysisId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
            val photos =
                listOf(
                    PhotoRef(
                        photoId = UUID.fromString("550e8400-e29b-41d4-a716-446655440001"),
                        gcsUri = "gs://bucket/1.jpg",
                        mimeType = "image/jpeg",
                    ),
                    PhotoRef(
                        photoId = UUID.fromString("550e8400-e29b-41d4-a716-446655440002"),
                        gcsUri = "gs://bucket/2.jpg",
                        mimeType = "image/jpeg",
                    ),
                    PhotoRef(
                        photoId = UUID.fromString("550e8400-e29b-41d4-a716-446655440003"),
                        gcsUri = "gs://bucket/3.jpg",
                        mimeType = "image/jpeg",
                    ),
                )
            val progress = mutableListOf<Int>()
            val service =
                AnalysisPipelineService(
                    geminiClassifier = FixedGeminiClassifier(photos),
                    stickerGenerator = FixedStickerGenerator(),
                    stickerStorage = EchoStickerStorage(),
                )

            When("run을 실행하면") {
                val result = service.run(analysisId, photos) { progress += it }

                Then("단계별 진행률을 콜백으로 전달한다") {
                    progress shouldContainExactly listOf(45, 60, 75, 90, 90)
                }

                Then("분석 아이디와 스티커 이미지 키를 결과에 담는다") {
                    result.analysisId shouldBe analysisId
                    result.themes.map { it.stickerImageKey } shouldContainExactly
                        listOf(
                            "stickers/550e8400-e29b-41d4-a716-446655440000/0-550e8400-e29b-41d4-a716-446655440001.png",
                            "stickers/550e8400-e29b-41d4-a716-446655440000/1-550e8400-e29b-41d4-a716-446655440002.png",
                            "stickers/550e8400-e29b-41d4-a716-446655440000/2-550e8400-e29b-41d4-a716-446655440003.png",
                        )
                }

                Then("분류 결과의 comments를 그대로 결과에 담는다") {
                    result.themes.map { it.comments } shouldContainExactly
                        photos.mapIndexed { index, _ ->
                            listOf(ThemeComment(content = "코멘트$index", posX = -96.0, posY = -150.0))
                        }
                }
            }
        }

        Given("스티커 대상 재확인이 targetSubject를 보정하거나 대상 없음을 판정할 때") {
            val analysisId = UUID.fromString("550e8400-e29b-41d4-a716-446655440010")
            val correctedPhoto =
                PhotoRef(
                    photoId = UUID.fromString("550e8400-e29b-41d4-a716-446655440011"),
                    gcsUri = "gs://bucket/11.jpg",
                    mimeType = "image/jpeg",
                )
            val missingPhoto =
                PhotoRef(
                    photoId = UUID.fromString("550e8400-e29b-41d4-a716-446655440012"),
                    gcsUri = "gs://bucket/12.jpg",
                    mimeType = "image/jpeg",
                )
            val photos = listOf(correctedPhoto, missingPhoto)
            val recordedTargetSubjects = mutableListOf<String>()
            val service =
                AnalysisPipelineService(
                    geminiClassifier =
                        VerifyingGeminiClassifier(
                            photos = photos,
                            correctedSubjectByPhotoId =
                                mapOf(correctedPhoto.photoId to StickerSubjectVerification("보정된 피사체", "#00FF00")),
                            notPresentPhotoIds = setOf(missingPhoto.photoId),
                        ),
                    stickerGenerator = RecordingStickerGenerator(recordedTargetSubjects),
                    stickerStorage = EchoStickerStorage(),
                )

            When("run을 실행하면") {
                val result = service.run(analysisId, photos)

                Then("재확인으로 보정된 테마는 보정된 targetSubject/mainColor로 스티커를 생성한다") {
                    val correctedTheme = result.themes.first { it.stickerSourcePhotoId == correctedPhoto.photoId }
                    correctedTheme.stickerTargetSubject shouldBe "보정된 피사체"
                    correctedTheme.stickerMainColor shouldBe "#00FF00"
                    correctedTheme.stickerImageKey.shouldNotBeNull()
                    recordedTargetSubjects shouldContainExactly listOf("보정된 피사체")
                }

                Then("재확인이 대상 없음으로 판정한 테마는 스티커 없이 나머지 정보는 유지된다") {
                    val missingTheme = result.themes.first { it.stickerSourcePhotoId == missingPhoto.photoId }
                    missingTheme.stickerImageKey.shouldBeNull()
                    missingTheme.theme.shouldNotBeNull()
                    missingTheme.badge.shouldNotBeNull()
                }
            }
        }
    })

private class FixedGeminiClassifier(
    private val photos: List<PhotoRef>,
) : GeminiClassifier {
    override fun classifyAndRecap(photos: List<PhotoRef>): List<ThemeClassification> =
        this.photos.mapIndexed { index, photo ->
            ThemeClassification(
                theme = "테마$index",
                categorizedPhotoIds = listOf(photo.photoId),
                recap = RecapContent(badge = "뱃지$index", text = "리캡$index"),
                stickerTargetSubject = "피사체$index",
                stickerSourcePhotoId = photo.photoId,
                stickerMainColor = "#FF6B6B",
                comments = listOf(ThemeComment(content = "코멘트$index", posX = -96.0, posY = -150.0)),
            )
        }

    override fun regenerateSticker(
        photos: List<PhotoRef>,
        previousSourcePhotoId: UUID,
    ): StickerRegenerationTarget =
        StickerRegenerationTarget(
            stickerTargetSubject = "재생성된 피사체",
            stickerSourcePhotoId = photos.first().photoId,
            stickerMainColor = "#FF6B6B",
        )

    override fun verifyStickerSubject(
        photo: PhotoRef,
        targetSubject: String,
    ): StickerSubjectVerification = StickerSubjectVerification(targetSubject = targetSubject, mainColor = "#FF6B6B")
}

private class VerifyingGeminiClassifier(
    private val photos: List<PhotoRef>,
    private val correctedSubjectByPhotoId: Map<UUID, StickerSubjectVerification>,
    private val notPresentPhotoIds: Set<UUID>,
) : GeminiClassifier {
    override fun classifyAndRecap(photos: List<PhotoRef>): List<ThemeClassification> =
        this.photos.mapIndexed { index, photo ->
            ThemeClassification(
                theme = "테마$index",
                categorizedPhotoIds = listOf(photo.photoId),
                recap = RecapContent(badge = "뱃지$index", text = "리캡$index"),
                stickerTargetSubject = "원래 피사체$index",
                stickerSourcePhotoId = photo.photoId,
                stickerMainColor = "#FF6B6B",
                comments = listOf(ThemeComment(content = "코멘트$index", posX = -96.0, posY = -150.0)),
            )
        }

    override fun regenerateSticker(
        photos: List<PhotoRef>,
        previousSourcePhotoId: UUID,
    ): StickerRegenerationTarget = throw UnsupportedOperationException("사용되지 않음")

    override fun verifyStickerSubject(
        photo: PhotoRef,
        targetSubject: String,
    ): StickerSubjectVerification? {
        if (photo.photoId in notPresentPhotoIds) return null
        return correctedSubjectByPhotoId[photo.photoId] ?: StickerSubjectVerification(targetSubject, "#FF6B6B")
    }
}

private class FixedStickerGenerator : StickerGenerator {
    override fun generate(
        sourceGcsUri: String,
        sourceMimeType: String,
        targetSubject: String,
    ): ByteArray = byteArrayOf(1)
}

private class RecordingStickerGenerator(
    private val recordedTargetSubjects: MutableList<String>,
) : StickerGenerator {
    override fun generate(
        sourceGcsUri: String,
        sourceMimeType: String,
        targetSubject: String,
    ): ByteArray {
        recordedTargetSubjects += targetSubject
        return byteArrayOf(1)
    }
}

private class EchoStickerStorage : StickerStorage {
    override fun upload(
        objectKey: String,
        bytes: ByteArray,
    ): String = objectKey
}
