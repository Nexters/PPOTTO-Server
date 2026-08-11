package com.github.nexters.ppotto.analysis.support

import com.github.nexters.ppotto.analysis.domain.GeminiClassifier
import com.github.nexters.ppotto.analysis.domain.PhotoRef
import com.github.nexters.ppotto.analysis.domain.RecapContent
import com.github.nexters.ppotto.analysis.domain.StickerRegenerationTarget
import com.github.nexters.ppotto.analysis.domain.StickerSubjectVerification
import com.github.nexters.ppotto.analysis.domain.ThemeClassification
import com.github.nexters.ppotto.analysis.domain.ThemeComment
import java.util.UUID

class FakeGeminiClassifier : GeminiClassifier {
    var failureToThrow: Throwable? = null
    var classifications: List<ThemeClassification>? = null
    var regenerationTarget: StickerRegenerationTarget? = null
    var verification: StickerSubjectVerification? = null

    override fun classifyAndRecap(photos: List<PhotoRef>): List<ThemeClassification> {
        failureToThrow?.let { throw it }
        classifications?.let { return it }
        return listOf(
            ThemeClassification(
                theme = "테스트테마",
                categorizedPhotoIds = photos.map { it.photoId },
                recap = RecapContent(badge = "테스트뱃지", text = "테스트 리캡 문구입니다."),
                stickerTargetSubject = "테스트 피사체",
                stickerSourcePhotoId = photos.first().photoId,
                stickerMainColor = "#FF6B6B",
                comments =
                    listOf(
                        ThemeComment(content = "테스트 말풍선", posX = -96.0, posY = -150.0),
                        ThemeComment(content = "테스트 키워드", posX = null, posY = null),
                    ),
            ),
        )
    }

    override fun regenerateSticker(
        photos: List<PhotoRef>,
        previousSourcePhotoId: UUID,
    ): StickerRegenerationTarget {
        failureToThrow?.let { throw it }
        regenerationTarget?.let { return it }
        return StickerRegenerationTarget(
            stickerTargetSubject = "재생성된 피사체",
            stickerSourcePhotoId = photos.first().photoId,
            stickerMainColor = "#FF6B6B",
        )
    }

    override fun verifyStickerSubject(
        photo: PhotoRef,
        targetSubject: String,
    ): StickerSubjectVerification? {
        failureToThrow?.let { throw it }
        return verification ?: StickerSubjectVerification(targetSubject = targetSubject, mainColor = "#FF6B6B")
    }
}
