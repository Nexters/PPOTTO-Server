package com.github.nexters.ppotto.analysis.application

import com.github.nexters.ppotto.analysis.domain.GeminiClassifier
import com.github.nexters.ppotto.analysis.domain.PhotoRef
import com.github.nexters.ppotto.analysis.domain.RecapContent
import com.github.nexters.ppotto.analysis.domain.StickerGenerator
import com.github.nexters.ppotto.analysis.domain.StickerRegenerationTarget
import com.github.nexters.ppotto.analysis.domain.StickerStorage
import com.github.nexters.ppotto.analysis.domain.ThemeClassification
import com.github.nexters.ppotto.analysis.domain.ThemeComment
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
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
}

private class FixedStickerGenerator : StickerGenerator {
    override fun generate(
        sourceGcsUri: String,
        sourceMimeType: String,
        targetSubject: String,
    ): ByteArray = byteArrayOf(1)
}

private class EchoStickerStorage : StickerStorage {
    override fun upload(
        objectKey: String,
        bytes: ByteArray,
    ): String = objectKey
}
