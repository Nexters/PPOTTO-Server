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
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

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
            val progress = CopyOnWriteArrayList<Int>()
            val intermediateProgressThreadIds = CopyOnWriteArrayList<Long>()
            val stickerGenerator = ConcurrentStickerGenerator()
            val service =
                AnalysisPipelineService(
                    geminiClassifier = FixedGeminiClassifier(photos),
                    stickerGenerator = stickerGenerator,
                    stickerStorage = EchoStickerStorage(),
                )

            When("run을 실행하면") {
                val result =
                    service.run(analysisId, photos) {
                        progress += it
                        if (it in 46..89) {
                            intermediateProgressThreadIds += Thread.currentThread().threadId()
                        }
                    }

                Then("진행률은 단조 증가하고 주요 체크포인트를 유지한다") {
                    progress.first() shouldBe 45
                    progress.last() shouldBe 90
                    progress
                        .zipWithNext { a, b -> a <= b }
                        .all { it }
                        .shouldBeTrue()
                    progress.all { it in 10..90 }.shouldBeTrue()
                }

                Then("중간 진행률은 단일 dispatcher thread에서 전달한다") {
                    intermediateProgressThreadIds.isNotEmpty().shouldBeTrue()
                    intermediateProgressThreadIds.distinct().size shouldBe 1
                }

                Then("테마별 스티커 생성은 병렬로 실행한다") {
                    stickerGenerator.maxActiveCount
                        .get()
                        .shouldBeGreaterThan(1)
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

        Given("스티커 대상 재확인이 실패할 때") {
            val analysisId = UUID.fromString("550e8400-e29b-41d4-a716-446655440020")
            val photo =
                PhotoRef(
                    photoId = UUID.fromString("550e8400-e29b-41d4-a716-446655440021"),
                    gcsUri = "gs://bucket/21.jpg",
                    mimeType = "image/jpeg",
                )
            val recordedTargetSubjects = mutableListOf<String>()
            val service =
                AnalysisPipelineService(
                    geminiClassifier = FailingVerificationGeminiClassifier(photo),
                    stickerGenerator = RecordingStickerGenerator(recordedTargetSubjects),
                    stickerStorage = EchoStickerStorage(),
                )

            When("run을 실행하면") {
                val result = service.run(analysisId, listOf(photo))

                Then("분류 결과의 피사체와 색상으로 fallback하여 스티커 생성을 계속한다") {
                    val theme = result.themes.single()
                    theme.stickerTargetSubject shouldBe "분류된 피사체"
                    theme.stickerMainColor shouldBe "#112233"
                    theme.stickerImageKey.shouldNotBeNull()
                    recordedTargetSubjects shouldContainExactly listOf("분류된 피사체")
                }
            }
        }

        Given("연사(버스트) 그룹 사진이 포함되어 있을 때") {
            val analysisId = UUID.fromString("550e8400-e29b-41d4-a716-446655440040")
            val burstGroupId = UUID.fromString("550e8400-e29b-41d4-a716-446655440049")
            val representativePhoto =
                PhotoRef(
                    photoId = UUID.fromString("550e8400-e29b-41d4-a716-446655440041"),
                    gcsUri = "gs://bucket/41.jpg",
                    mimeType = "image/jpeg",
                    burstGroupId = burstGroupId,
                    isRepresentative = true,
                )
            val siblingPhotos =
                listOf(
                    PhotoRef(
                        photoId = UUID.fromString("550e8400-e29b-41d4-a716-446655440042"),
                        gcsUri = "gs://bucket/42.jpg",
                        mimeType = "image/jpeg",
                        burstGroupId = burstGroupId,
                        isRepresentative = false,
                    ),
                    PhotoRef(
                        photoId = UUID.fromString("550e8400-e29b-41d4-a716-446655440043"),
                        gcsUri = "gs://bucket/43.jpg",
                        mimeType = "image/jpeg",
                        burstGroupId = burstGroupId,
                        isRepresentative = false,
                    ),
                )
            val photos = listOf(representativePhoto) + siblingPhotos
            val receivedPhotoIds = mutableListOf<UUID>()
            val service =
                AnalysisPipelineService(
                    geminiClassifier = RecordingClassifyGeminiClassifier(receivedPhotoIds),
                    stickerGenerator = RecordingStickerGenerator(mutableListOf()),
                    stickerStorage = EchoStickerStorage(),
                )

            When("run을 실행하면") {
                val result = service.run(analysisId, photos)

                Then("Gemini 분류 요청에는 대표사진만 전달한다") {
                    receivedPhotoIds shouldContainExactly listOf(representativePhoto.photoId)
                }

                Then("대표사진이 속한 테마의 categorizedPhotoIds에 연사 형제사진이 모두 포함된다") {
                    result.themes
                        .single()
                        .categorizedPhotoIds
                        .toSet() shouldBe
                        setOf(representativePhoto.photoId, siblingPhotos[0].photoId, siblingPhotos[1].photoId)
                }
            }
        }

        Given("일부 테마 처리 중 예상하지 못한 예외가 발생할 때") {
            val analysisId = UUID.fromString("550e8400-e29b-41d4-a716-446655440030")
            val validPhoto =
                PhotoRef(
                    photoId = UUID.fromString("550e8400-e29b-41d4-a716-446655440031"),
                    gcsUri = "gs://bucket/31.jpg",
                    mimeType = "image/jpeg",
                )
            val missingPhotoId = UUID.fromString("550e8400-e29b-41d4-a716-446655440032")
            val service =
                AnalysisPipelineService(
                    geminiClassifier = PartiallyInvalidThemeGeminiClassifier(validPhoto, missingPhotoId),
                    stickerGenerator = RecordingStickerGenerator(mutableListOf()),
                    stickerStorage = EchoStickerStorage(),
                )

            When("run을 실행하면") {
                val result = service.run(analysisId, listOf(validPhoto))

                Then("실패한 테마는 스티커 없이 남기고 성공한 테마 결과는 유지한다") {
                    result.themes.map { it.theme } shouldContainExactly listOf("정상 테마", "실패 테마")
                    result.themes.map { it.stickerImageKey } shouldContainExactly
                        listOf(
                            "stickers/550e8400-e29b-41d4-a716-446655440030/0-550e8400-e29b-41d4-a716-446655440031.png",
                            null,
                        )
                    result.themes[1].stickerSourcePhotoId shouldBe missingPhotoId
                    result.themes[1].stickerTargetSubject shouldBe "없는 피사체"
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

private class RecordingClassifyGeminiClassifier(
    private val receivedPhotoIds: MutableList<UUID>,
) : GeminiClassifier {
    override fun classifyAndRecap(photos: List<PhotoRef>): List<ThemeClassification> {
        receivedPhotoIds += photos.map { it.photoId }
        return listOf(
            ThemeClassification(
                theme = "테마",
                categorizedPhotoIds = photos.map { it.photoId },
                recap = RecapContent(badge = "뱃지", text = "리캡"),
                stickerTargetSubject = "피사체",
                stickerSourcePhotoId = photos.first().photoId,
                stickerMainColor = "#FF6B6B",
                comments = emptyList(),
            ),
        )
    }

    override fun regenerateSticker(
        photos: List<PhotoRef>,
        previousSourcePhotoId: UUID,
    ): StickerRegenerationTarget = throw UnsupportedOperationException("사용되지 않음")

    override fun verifyStickerSubject(
        photo: PhotoRef,
        targetSubject: String,
    ): StickerSubjectVerification = StickerSubjectVerification(targetSubject, "#FF6B6B")
}

private class FailingVerificationGeminiClassifier(
    private val photo: PhotoRef,
) : GeminiClassifier {
    override fun classifyAndRecap(photos: List<PhotoRef>): List<ThemeClassification> =
        listOf(
            ThemeClassification(
                theme = "테마",
                categorizedPhotoIds = listOf(photo.photoId),
                recap = RecapContent(badge = "뱃지", text = "리캡"),
                stickerTargetSubject = "분류된 피사체",
                stickerSourcePhotoId = photo.photoId,
                stickerMainColor = "#112233",
                comments = emptyList(),
            ),
        )

    override fun regenerateSticker(
        photos: List<PhotoRef>,
        previousSourcePhotoId: UUID,
    ): StickerRegenerationTarget = throw UnsupportedOperationException("사용되지 않음")

    override fun verifyStickerSubject(
        photo: PhotoRef,
        targetSubject: String,
    ): StickerSubjectVerification? = throw IllegalStateException("검증 실패")
}

private class PartiallyInvalidThemeGeminiClassifier(
    private val validPhoto: PhotoRef,
    private val missingPhotoId: UUID,
) : GeminiClassifier {
    override fun classifyAndRecap(photos: List<PhotoRef>): List<ThemeClassification> =
        listOf(
            ThemeClassification(
                theme = "정상 테마",
                categorizedPhotoIds = listOf(validPhoto.photoId),
                recap = RecapContent(badge = "정상", text = "정상 리캡"),
                stickerTargetSubject = "정상 피사체",
                stickerSourcePhotoId = validPhoto.photoId,
                stickerMainColor = "#112233",
                comments = emptyList(),
            ),
            ThemeClassification(
                theme = "실패 테마",
                categorizedPhotoIds = listOf(validPhoto.photoId),
                recap = RecapContent(badge = "실패", text = "실패 리캡"),
                stickerTargetSubject = "없는 피사체",
                stickerSourcePhotoId = missingPhotoId,
                stickerMainColor = "#445566",
                comments = emptyList(),
            ),
        )

    override fun regenerateSticker(
        photos: List<PhotoRef>,
        previousSourcePhotoId: UUID,
    ): StickerRegenerationTarget = throw UnsupportedOperationException("사용되지 않음")

    override fun verifyStickerSubject(
        photo: PhotoRef,
        targetSubject: String,
    ): StickerSubjectVerification = StickerSubjectVerification(targetSubject, "#112233")
}

private class ConcurrentStickerGenerator : StickerGenerator {
    val maxActiveCount = AtomicInteger(0)
    private val activeCount = AtomicInteger(0)

    override fun generate(
        sourceGcsUri: String,
        sourceMimeType: String,
        targetSubject: String,
    ): ByteArray {
        val active = activeCount.incrementAndGet()
        maxActiveCount.updateAndGet { current -> maxOf(current, active) }
        Thread.sleep(100)
        activeCount.decrementAndGet()
        return byteArrayOf(1)
    }
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
