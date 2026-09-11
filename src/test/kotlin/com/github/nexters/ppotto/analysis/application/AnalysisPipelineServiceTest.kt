package com.github.nexters.ppotto.analysis.application

import com.github.nexters.ppotto.analysis.domain.AnalysisErrorCode
import com.github.nexters.ppotto.analysis.domain.PhotoRef
import com.github.nexters.ppotto.analysis.domain.RecapContent
import com.github.nexters.ppotto.analysis.domain.StickerSubjectVerification
import com.github.nexters.ppotto.analysis.domain.ThemeClassification
import com.github.nexters.ppotto.analysis.domain.ThemeComment
import com.github.nexters.ppotto.analysis.support.FakeStickerGenerator
import com.github.nexters.ppotto.analysis.support.FakeStickerStorage
import com.github.nexters.ppotto.analysis.support.FakeThemeClassifier
import com.github.nexters.ppotto.global.error.BusinessException
import com.github.nexters.ppotto.global.identifier.AnalysisId
import com.github.nexters.ppotto.global.identifier.PhotoId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.web.client.RestClientException
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

private fun photoRef(
    suffix: String,
    burstGroupId: UUID? = null,
    isRepresentative: Boolean = true,
) = PhotoRef(
    photoId = PhotoId(UUID.fromString("550e8400-e29b-41d4-a716-4466554400$suffix")),
    sourceUri = "gs://bucket/$suffix.jpg",
    mimeType = "image/jpeg",
    burstGroupId = burstGroupId,
    isRepresentative = isRepresentative,
)

private fun themePerPhoto(photos: List<PhotoRef>): List<ThemeClassification> =
    photos.mapIndexed { index, photo ->
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

class AnalysisPipelineServiceTest :
    BehaviorSpec({
        Given("서로 다른 사진을 담은 테마 3개를 분류하는 파이프라인이") {
            val analysisId = AnalysisId(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"))
            val photos = listOf(photoRef("01"), photoRef("02"), photoRef("03"))
            val allThemesInFlight = CountDownLatch(photos.size)
            val themesThatSawEveryThemeInFlight = AtomicInteger(0)
            val stickerGenerator =
                FakeStickerGenerator().apply {
                    onGenerate = {
                        allThemesInFlight.countDown()
                        if (allThemesInFlight.await(5, TimeUnit.SECONDS)) themesThatSawEveryThemeInFlight.incrementAndGet()
                    }
                }
            val themeClassifier = FakeThemeClassifier().apply { onClassify = { themePerPhoto(photos) } }
            val service =
                AnalysisPipelineService(
                    themeClassifier = themeClassifier,
                    stickerGenerator = stickerGenerator,
                    stickerStorage = FakeStickerStorage(),
                )
            val progress = CopyOnWriteArrayList<Int>()

            When("파이프라인을 실행하면") {
                val result = service.run(PipelineRun(analysisId), photos) { progress += it }

                Then("테마 3개가 모두 서로가 스티커 생성 중인 것을 보고 병렬로 처리된다") {
                    themesThatSawEveryThemeInFlight.get() shouldBe photos.size
                }

                Then("진행률은 분류 완료 45에서 시작해 스티커 완료 90으로 끝난다") {
                    progress.first() shouldBe 45
                    progress.last() shouldBe 90
                }

                Then("진행률은 되돌아가지 않고 10에서 90 사이를 유지한다") {
                    progress
                        .zipWithNext { previous, next -> previous <= next }
                        .all { it }
                        .shouldBeTrue()
                    progress.all { it in 10..90 }.shouldBeTrue()
                }

                Then("분석 아이디와 테마 순번별 스티커 이미지 키를 결과에 담는다") {
                    result.analysisId shouldBe analysisId
                    result.themes.map { it.stickerImageKey } shouldContainExactly
                        photos.mapIndexed { index, photo -> "stickers/$analysisId/$index-${photo.photoId}.png" }
                }

                Then("분류 결과의 comments를 그대로 결과에 담는다") {
                    result.themes.map { it.comments } shouldContainExactly
                        photos.indices.map { listOf(ThemeComment(content = "코멘트$it", posX = -96.0, posY = -150.0)) }
                }
            }
        }

        Given("한 테마는 피사체를 보정하고 다른 테마는 피사체 없음으로 판정하는 재확인이") {
            val analysisId = AnalysisId(UUID.fromString("550e8400-e29b-41d4-a716-446655440010"))
            val correctedPhoto = photoRef("11")
            val missingPhoto = photoRef("12")
            val photos = listOf(correctedPhoto, missingPhoto)
            val stickerGenerator = FakeStickerGenerator()
            val themeClassifier =
                FakeThemeClassifier().apply {
                    onClassify = { themePerPhoto(photos) }
                    onVerify = { photo, _ ->
                        if (photo.photoId == missingPhoto.photoId) {
                            null
                        } else {
                            StickerSubjectVerification("보정된 피사체", "#00FF00")
                        }
                    }
                }
            val service =
                AnalysisPipelineService(
                    themeClassifier = themeClassifier,
                    stickerGenerator = stickerGenerator,
                    stickerStorage = FakeStickerStorage(),
                )

            When("파이프라인을 실행하면") {
                val result = service.run(PipelineRun(analysisId), photos)

                Then("보정된 테마는 보정된 피사체로 스티커를 만들고 보정된 mainColor를 쓴다") {
                    val correctedTheme = result.themes.first { it.stickerSourcePhotoId == correctedPhoto.photoId }
                    correctedTheme.stickerMainColor shouldBe "#00FF00"
                    correctedTheme.stickerImageKey.shouldNotBeNull()
                    stickerGenerator.requestedTargetSubjects shouldContainExactly listOf("보정된 피사체")
                }

                Then("피사체 없음으로 판정된 테마는 스티커 없이 테마 정보만 남는다") {
                    val missingTheme = result.themes.first { it.stickerSourcePhotoId == missingPhoto.photoId }
                    missingTheme.stickerImageKey.shouldBeNull()
                    missingTheme.failedCode shouldBe AnalysisErrorCode.NO_STICKER_SUBJECT
                    missingTheme.theme shouldBe "테마1"
                    missingTheme.badge shouldBe "뱃지1"
                }
            }
        }

        Given("스티커 대상 재확인 호출이 실패하는 파이프라인이") {
            val analysisId = AnalysisId(UUID.fromString("550e8400-e29b-41d4-a716-446655440020"))
            val photo = photoRef("21")
            val stickerGenerator = FakeStickerGenerator()
            val themeClassifier =
                FakeThemeClassifier().apply {
                    onClassify = {
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
                    }
                    onVerify = { _, _ -> throw IllegalStateException("검증 실패") }
                }
            val service =
                AnalysisPipelineService(
                    themeClassifier = themeClassifier,
                    stickerGenerator = stickerGenerator,
                    stickerStorage = FakeStickerStorage(),
                )

            When("파이프라인을 실행하면") {
                val result = service.run(PipelineRun(analysisId), listOf(photo))

                Then("분류 단계의 피사체와 색상으로 되돌아가 스티커 생성을 계속한다") {
                    val theme = result.themes.single()
                    theme.stickerMainColor shouldBe "#112233"
                    theme.stickerImageKey.shouldNotBeNull()
                    stickerGenerator.requestedTargetSubjects shouldContainExactly listOf("분류된 피사체")
                }
            }
        }

        Given("대표 사진 1장과 연사 형제 사진 2장이 한 그룹으로 묶인 파이프라인이") {
            val analysisId = AnalysisId(UUID.fromString("550e8400-e29b-41d4-a716-446655440040"))
            val burstGroupId = UUID.fromString("550e8400-e29b-41d4-a716-446655440049")
            val representativePhoto = photoRef("41", burstGroupId, isRepresentative = true)
            val siblingPhotos =
                listOf(
                    photoRef("42", burstGroupId, isRepresentative = false),
                    photoRef("43", burstGroupId, isRepresentative = false),
                )
            val photos = listOf(representativePhoto) + siblingPhotos
            val themeClassifier = FakeThemeClassifier()
            val service =
                AnalysisPipelineService(
                    themeClassifier = themeClassifier,
                    stickerGenerator = FakeStickerGenerator(),
                    stickerStorage = FakeStickerStorage(),
                )

            When("파이프라인을 실행하면") {
                val result = service.run(PipelineRun(analysisId), photos)

                Then("Gemini 분류 요청에는 대표 사진만 전달한다") {
                    themeClassifier.classifiedPhotoIds shouldContainExactly listOf(representativePhoto.photoId)
                }

                Then("대표 사진이 속한 테마에 연사 형제 사진이 모두 채워진다") {
                    result.themes
                        .single()
                        .categorizedPhotoIds
                        .toSet() shouldBe (listOf(representativePhoto) + siblingPhotos).map { it.photoId }.toSet()
                }
            }
        }

        Given("테마 3개 중 두 번째 테마의 배경 제거만 실패하는 파이프라인이") {
            val analysisId = AnalysisId(UUID.fromString("550e8400-e29b-41d4-a716-446655440050"))
            val photos = listOf(photoRef("51"), photoRef("52"), photoRef("53"))
            val stickerGenerator =
                FakeStickerGenerator().apply {
                    onGenerate = { targetSubject ->
                        if (targetSubject == "피사체1") throw RestClientException("Pixian 502")
                    }
                }
            val stickerStorage = FakeStickerStorage()
            val themeClassifier = FakeThemeClassifier().apply { onClassify = { themePerPhoto(photos) } }
            val service =
                AnalysisPipelineService(
                    themeClassifier = themeClassifier,
                    stickerGenerator = stickerGenerator,
                    stickerStorage = stickerStorage,
                )

            When("파이프라인을 실행하면") {
                val result = service.run(PipelineRun(analysisId), photos)

                Then("실패한 테마만 스티커 이미지 키가 null로 남는다") {
                    result.themes.map { it.stickerImageKey } shouldContainExactly
                        listOf(
                            "stickers/$analysisId/0-${photos[0].photoId}.png",
                            null,
                            "stickers/$analysisId/2-${photos[2].photoId}.png",
                        )
                }

                Then("실패한 테마도 분류 결과(테마명·뱃지·리캡)는 그대로 살아남는다") {
                    val failedTheme = result.themes[1]
                    failedTheme.theme shouldBe "테마1"
                    failedTheme.badge shouldBe "뱃지1"
                    failedTheme.text shouldBe "리캡1"
                    failedTheme.failedCode shouldBe AnalysisErrorCode.STICKER_GENERATION_FAILED
                }

                Then("실패한 테마의 오브젝트는 업로드하지 않는다") {
                    stickerStorage.uploaded.keys shouldContainExactlyInAnyOrder
                        listOf(
                            "stickers/$analysisId/0-${photos[0].photoId}.png",
                            "stickers/$analysisId/2-${photos[2].photoId}.png",
                        )
                }
            }
        }

        Given("스티커 업로드가 실패하는 파이프라인이") {
            val analysisId = AnalysisId(UUID.fromString("550e8400-e29b-41d4-a716-446655440060"))
            val photo = photoRef("61")
            val stickerStorage = FakeStickerStorage().apply { uploadFailure = IllegalStateException("업로드 실패") }
            val themeClassifier = FakeThemeClassifier().apply { onClassify = { themePerPhoto(listOf(photo)) } }
            val service =
                AnalysisPipelineService(
                    themeClassifier = themeClassifier,
                    stickerGenerator = FakeStickerGenerator(),
                    stickerStorage = stickerStorage,
                )

            When("파이프라인을 실행하면") {
                Then("완성된 스티커가 없으므로 전체 생성 실패 코드를 반환한다") {
                    shouldThrow<BusinessException> {
                        service.run(PipelineRun(analysisId), listOf(photo))
                    }.errorCode shouldBe AnalysisErrorCode.STICKER_GENERATION_FAILED
                }
            }
        }

        Given("모든 테마에서 피사체를 찾지 못한 파이프라인이") {
            val photos = listOf(photoRef("71"), photoRef("72"))
            val classifier =
                FakeThemeClassifier().apply {
                    onClassify = { themePerPhoto(photos) }
                    onVerify = { _, _ -> null }
                }
            val service = AnalysisPipelineService(classifier, FakeStickerGenerator(), FakeStickerStorage())

            When("파이프라인을 실행하면") {
                Then("생성 오류와 구분되는 피사체 없음 코드를 반환한다") {
                    shouldThrow<BusinessException> {
                        service.run(PipelineRun(AnalysisId(UUID.randomUUID())), photos)
                    }.errorCode shouldBe AnalysisErrorCode.NO_STICKER_SUBJECT
                }
            }
        }

        Given("피사체 없음과 생성 오류가 섞여 완성된 스티커가 없는 파이프라인이") {
            val photos = listOf(photoRef("81"), photoRef("82"))
            val classifier =
                FakeThemeClassifier().apply {
                    onClassify = { themePerPhoto(photos) }
                    onVerify = { photo, subject ->
                        if (photo == photos.first()) null else StickerSubjectVerification(subject, "#123456")
                    }
                }
            val generator = FakeStickerGenerator().apply { onGenerate = { error("배경 제거 실패") } }
            val service = AnalysisPipelineService(classifier, generator, FakeStickerStorage())

            When("파이프라인을 실행하면") {
                Then("피사체 없음 대신 전체 생성 실패 코드를 반환한다") {
                    shouldThrow<BusinessException> {
                        service.run(PipelineRun(AnalysisId(UUID.randomUUID())), photos)
                    }.errorCode shouldBe AnalysisErrorCode.STICKER_GENERATION_FAILED
                }
            }
        }

        Given("분류 결과가 비어 있는 파이프라인이") {
            val classifier = FakeThemeClassifier().apply { classifications = emptyList() }
            val service = AnalysisPipelineService(classifier, FakeStickerGenerator(), FakeStickerStorage())

            When("파이프라인을 실행하면") {
                Then("피사체 없음 대신 잘못된 분류 응답 코드를 반환한다") {
                    shouldThrow<BusinessException> {
                        service.run(PipelineRun(AnalysisId(UUID.randomUUID())), listOf(photoRef("91")))
                    }.errorCode shouldBe AnalysisErrorCode.INVALID_GEMINI_RESPONSE
                }
            }
        }

        Given("원본 사진이 입력에 없는 테마가 분류 결과에 섞인 파이프라인이") {
            val analysisId = AnalysisId(UUID.fromString("550e8400-e29b-41d4-a716-446655440030"))
            val validPhoto = photoRef("31")
            val missingPhotoId = PhotoId(UUID.fromString("550e8400-e29b-41d4-a716-446655440032"))
            val themeClassifier =
                FakeThemeClassifier().apply {
                    classifications =
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
                }
            val service =
                AnalysisPipelineService(
                    themeClassifier = themeClassifier,
                    stickerGenerator = FakeStickerGenerator(),
                    stickerStorage = FakeStickerStorage(),
                )

            When("파이프라인을 실행하면") {
                val result = service.run(PipelineRun(analysisId), listOf(validPhoto))

                Then("실패한 테마는 스티커 없이 남기고 성공한 테마 결과는 유지한다") {
                    result.themes.map { it.theme } shouldContainExactly listOf("정상 테마", "실패 테마")
                    result.themes.map { it.stickerImageKey } shouldContainExactly
                        listOf("stickers/$analysisId/0-${validPhoto.photoId}.png", null)
                }

                Then("실패한 테마도 분류가 고른 원본 사진 정보는 그대로 남긴다") {
                    result.themes[1].stickerSourcePhotoId shouldBe missingPhotoId
                    result.themes[1].stickerMainColor shouldBe "#445566"
                }
            }
        }
    })
